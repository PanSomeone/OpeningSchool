package com.openschedule.plugin.hotwater

import android.content.Context
import com.openschedule.plugin.hotwater.ApiConstants.HEADER_APPLICATION_TYPE
import com.openschedule.plugin.hotwater.ApiConstants.HEADER_AUTHORIZATION
import com.openschedule.plugin.hotwater.ApiConstants.HEADER_VERSION_CODE
import com.openschedule.plugin.hotwater.ApiConstants.USER_AGENT
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.Response as RetrofitResponse
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.UUID
import java.util.concurrent.TimeUnit

internal class HotWaterSession(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("openschedule_hotwater", Context.MODE_PRIVATE)

    var token: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    var uid: String?
        get() = prefs.getString(KEY_UID, null)
        set(value) = prefs.edit().putString(KEY_UID, value).apply()

    var accountName: String?
        get() = prefs.getString(KEY_ACCOUNT_NAME, null)
        set(value) = prefs.edit().putString(KEY_ACCOUNT_NAME, value).apply()

    /** Which device the button controls, remembered across launches. */
    var selectedDeviceId: String?
        get() = prefs.getString(KEY_SELECTED_DEVICE, null)
        set(value) = prefs.edit().putString(KEY_SELECTED_DEVICE, value).apply()

    /**
     * Stable per install. Never the official app's compiled-in `cid`: the
     * meaning of that value is unconfirmed, so this client supplies its own.
     */
    val clientId: String
        get() = prefs.getString(KEY_CLIENT_ID, null)
            ?: UUID.randomUUID().toString().replace("-", "").also {
                prefs.edit().putString(KEY_CLIENT_ID, it).apply()
            }

    fun clear() {
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_UID)
            .remove(KEY_ACCOUNT_NAME)
            .remove(KEY_SELECTED_DEVICE)
            .apply()
    }

    private companion object {
        const val KEY_TOKEN = "token"
        const val KEY_UID = "uid"
        const val KEY_ACCOUNT_NAME = "account_name"
        const val KEY_SELECTED_DEVICE = "selected_device"
        const val KEY_CLIENT_ID = "client_id"
    }
}

internal val hotWaterJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
    encodeDefaults = true
}

private class ClientHeaderInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val endpoint = original.url.encodedPath
            .substringAfter("/api/v1/", original.url.encodedPath)
            .trimStart('/')
        val applicationType = original.header(HEADER_APPLICATION_TYPE)
            ?: ApiConstants.applicationTypeFor(endpoint)

        return chain.proceed(
            original.newBuilder()
                .header("user-agent", USER_AGENT)
                .header(HEADER_VERSION_CODE, ApiConstants.APP_VERSION_CODE)
                .header(HEADER_APPLICATION_TYPE, applicationType)
                .build(),
        )
    }
}

private class AuthInterceptor(private val tokenProvider: () -> String?) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header(HEADER_AUTHORIZATION) != null) return chain.proceed(request)
        val token = tokenProvider()?.takeIf { it.isNotBlank() } ?: return chain.proceed(request)
        return chain.proceed(
            request.newBuilder().header(HEADER_AUTHORIZATION, token).build(),
        )
    }
}

internal interface HotWaterApi {

    @GET(ApiConstants.PATH_CAPTCHA)
    suspend fun captcha(
        @Query("s") key: String,
        @Query("r") timestamp: Long,
    ): RetrofitResponse<ResponseBody>

    @POST(ApiConstants.PATH_SMS_CODE)
    suspend fun sendSmsCode(@Body body: SmsCodeRequest): RetrofitResponse<Envelope<JsonElement>>

    @POST(ApiConstants.PATH_LOGIN)
    suspend fun login(@Body body: LoginRequest): RetrofitResponse<Envelope<LoginData>>

    @GET(ApiConstants.PATH_MASTER)
    suspend fun master(): RetrofitResponse<Envelope<MasterData>>

    @GET(ApiConstants.PATH_DEVICE_START)
    suspend fun startDevice(
        @Query(ApiConstants.PARAM_DID) did: String,
        @Query(ApiConstants.PARAM_UPGRADE) upgrade: Boolean = true,
        @Query(ApiConstants.PARAM_PTYPE) ptype: Int = ApiConstants.PTYPE_WALLET,
        @Query(ApiConstants.PARAM_ARGS) args: String = ApiConstants.ARGS_EMPTY,
        @Query(ApiConstants.PARAM_RCP) rcp: Boolean = false,
        @Query(ApiConstants.PARAM_CNT) cnt: Int = 1,
    ): RetrofitResponse<Envelope<JsonElement>>

    @GET(ApiConstants.PATH_DEVICE_END)
    suspend fun stopDevice(
        @Query(ApiConstants.PARAM_DID) did: String,
        @Query(ApiConstants.PARAM_RCP) rcp: Boolean = false,
    ): RetrofitResponse<Envelope<JsonElement>>

    @GET(ApiConstants.PATH_QR_USE)
    suspend fun qrUse(@Query(ApiConstants.PARAM_ID) id: String): RetrofitResponse<Envelope<QrUseData>>

    @GET(ApiConstants.PATH_DEVICE_FAVO)
    suspend fun favouriteDevice(
        @Query(ApiConstants.PARAM_DID) did: String,
        @Query(ApiConstants.PARAM_REMOVE) remove: String = ApiConstants.FAVO_ADD,
    ): RetrofitResponse<Envelope<JsonElement>>
}

internal class HotWaterGateway(session: HotWaterSession) {

    private val client = OkHttpClient.Builder()
        .addInterceptor(ClientHeaderInterceptor())
        .addInterceptor(AuthInterceptor { session.token })
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val api: HotWaterApi = Retrofit.Builder()
        .baseUrl(ApiConstants.DEFAULT_BASE_URL)
        .client(client)
        .addConverterFactory(hotWaterJson.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(HotWaterApi::class.java)

    suspend fun captcha(key: String): RetrofitResponse<ResponseBody> =
        api.captcha(key = key, timestamp = System.currentTimeMillis())

    suspend fun sendSmsCode(body: SmsCodeRequest) = api.sendSmsCode(body)

    suspend fun login(body: LoginRequest) = api.login(body)

    suspend fun master() = api.master()

    suspend fun start(did: String) = api.startDevice(did = did)

    suspend fun stop(did: String) = api.stopDevice(did = did)

    suspend fun qrUse(id: String) = api.qrUse(id)

    suspend fun favourite(did: String) = api.favouriteDevice(did = did)
}
