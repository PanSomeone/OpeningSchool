package com.openschedule.adapter.zhengfang

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.math.BigInteger
import java.security.KeyFactory
import java.security.spec.RSAPublicKeySpec
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher

class ZhengfangException(message: String) : Exception(message)

data class ZhengfangLogin(
    val username: String,
    val password: String,
    val year: String,
    val term: String,
)

data class ZhengfangFetch(
    val timetable: ParsedTimetable,
    val warnings: List<String>,
)

/**
 * The new Zhengfang (正方) login and timetable calls.
 *
 * Flow: login page -> csrftoken -> RSA public key -> encrypted password ->
 * `login_slogin` -> `index_initMenu` -> timetable endpoint.
 *
 * The endpoint path varies between 2019-era builds, so two are tried and a
 * GET fallback follows the POST. Parsing lives in [ZhengfangParser].
 */
class ZhengfangClient(
    baseUrl: String,
    private val client: OkHttpClient = defaultClient(),
) {
    private val base = normalizeBase(baseUrl)
    private val cookieJar = MemoryCookieJar()

    private val http: OkHttpClient = client.newBuilder().cookieJar(cookieJar).build()

    fun loginAndFetch(login: ZhengfangLogin): ZhengfangFetch {
        val warnings = mutableListOf<String>()
        val root = discoverRoot()
        val csrf = fetchCsrf(root)
        val publicKey = fetchPublicKey(root)

        val encryptedPassword = if (publicKey != null) {
            encryptPassword(login.password, publicKey.first, publicKey.second)
        } else {
            if (root.startsWith("http://")) {
                warnings += "该教务系统没有提供密码加密，密码以明文发往学校地址。请确认地址无误。"
            }
            login.password
        }

        val loginBody = FormBody.Builder()
            .add("time", System.currentTimeMillis().toString())
            .add("csrftoken", csrf)
            .add("language", "zh_CN")
            .add("yhm", login.username)
            .add("mm", encryptedPassword)
            .build()

        val loginRequest = Request.Builder()
            .url(root + "xtgl/login_slogin.html")
            .header("User-Agent", USER_AGENT)
            .header("Referer", root + "xtgl/login_slogin.html")
            .post(loginBody)
            .build()

        val loginResponse = execute(loginRequest)
        if (loginResponse.code !in 200..399) {
            throw ZhengfangException("登录请求失败（HTTP ${loginResponse.code}）")
        }
        val loginText = loginResponse.body
        if (isLoginFailure(loginText, loginResponse.finalUrl)) {
            throw ZhengfangException(loginErrorText(loginText) ?: "账号或密码不正确")
        }

        runCatching { execute(get(root + "xtgl/index_initMenu.html")) }

        val (body, endpoint) = fetchTimetable(root, login)
        val parsed = ZhengfangParser.parseJson(body)
            ?: ZhengfangParser.parseHtml(body)
            ?: run {
                if (body.contains("login_slogin") || body.contains("csrftoken")) {
                    throw ZhengfangException("登录没有保持住，教务系统又要求登录了")
                }
                throw ZhengfangException("没有从 $endpoint 解析出课表，该学校的正方版本可能不同")
            }

        if (parsed.courses.isEmpty()) {
            throw ZhengfangException("教务系统返回了空课表，请确认学年和学期")
        }
        return ZhengfangFetch(parsed, warnings)
    }

    private fun discoverRoot(): String {
        val candidates = buildList {
            add(base)
            if (!base.trimEnd('/').endsWith("jwglxt")) add(base + "jwglxt/")
        }.distinct()

        var lastError: String? = null
        candidates.forEach { candidate ->
            val url = candidate + "xtgl/login_slogin.html"
            try {
                val result = execute(get(url))
                if (result.body.contains("csrftoken") || result.body.contains("login_slogin")) {
                    return candidate
                }
                lastError = "登录页没有找到"
            } catch (e: Exception) {
                lastError = e.message
            }
        }
        throw ZhengfangException("找不到教务系统登录页（$lastError）")
    }

    private fun fetchCsrf(root: String): String {
        val html = execute(get(root + "xtgl/login_slogin.html")).body
        val patterns = listOf(
            Regex("""name=["']csrftoken["'][^>]*value=["']([^"']+)["']"""),
            Regex("""value=["']([^"']+)["'][^>]*name=["']csrftoken["']"""),
            Regex("""id=["']csrftoken["'][^>]*value=["']([^"']+)["']"""),
        )
        patterns.forEach { pattern ->
            val value = pattern.find(html)?.groupValues?.getOrNull(1)
            if (!value.isNullOrBlank()) return value
        }
        throw ZhengfangException("登录页没有 csrftoken，可能不是新版正方系统")
    }

    private fun fetchPublicKey(root: String): Pair<String, String>? {
        val timestamp = System.currentTimeMillis()
        val body = runCatching {
            execute(get(root + "xtgl/login_getPublicKey.html?time=$timestamp&_=$timestamp")).body
        }.getOrNull() ?: return null

        val modulus = Regex(""""modulus"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1)
        val exponent = Regex(""""exponent"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1)
        if (modulus.isNullOrBlank() || exponent.isNullOrBlank()) return null
        return modulus to exponent
    }

    private fun encryptPassword(password: String, modulus: String, exponent: String): String {
        val modulusBytes = decodeKey(modulus)
        val exponentBytes = decodeKey(exponent)
        val spec = RSAPublicKeySpec(BigInteger(1, modulusBytes), BigInteger(1, exponentBytes))
        val key = KeyFactory.getInstance("RSA").generatePublic(spec)
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return Base64.getEncoder().encodeToString(cipher.doFinal(password.toByteArray(Charsets.UTF_8)))
    }

    private fun decodeKey(raw: String): ByteArray {
        val cleaned = raw.replace("\\s".toRegex(), "")
        return if (cleaned.matches(Regex("[0-9a-fA-F]+"))) {
            cleaned.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        } else {
            Base64.getMimeDecoder().decode(cleaned)
        }
    }

    private fun fetchTimetable(root: String, login: ZhengfangLogin): Pair<String, String> {
        val year = Regex("\\d{4}").find(login.year)?.value
            ?: throw ZhengfangException("学年应形如 2026 或 2026-2027")
        val term = when (login.term.trim()) {
            "2" -> "12"
            "3" -> "16"
            else -> "3"
        }
        val referer = root + "kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151"
        val endpoints = listOf(
            "kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N2151",
            "kbcx/xskbcx_cxXsKb.html?gnmkdm=N2151",
        )

        var last = ""
        endpoints.forEach { endpoint ->
            val url = root + endpoint
            val form = FormBody.Builder()
                .add("xnm", year)
                .add("xqm", term)
                .add("kzlx", "ck")
                .build()

            runCatching {
                execute(
                    Request.Builder()
                        .url(url)
                        .header("User-Agent", USER_AGENT)
                        .header("Referer", referer)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Accept", "application/json, text/javascript, */*; q=0.01")
                        .post(form)
                        .build(),
                ).body
            }.onSuccess { body ->
                if (body.contains("kbList") || body.trimStart().startsWith("{")) {
                    return body to url
                }
                last = body
            }.onFailure { last = it.message.orEmpty() }

            runCatching {
                execute(
                    Request.Builder()
                        .url("$url&xnm=$year&xqm=$term&kzlx=ck")
                        .header("User-Agent", USER_AGENT)
                        .header("Referer", referer)
                        .header("X-Requested-With", "XMLHttpRequest")
                        .get()
                        .build(),
                ).body
            }.onSuccess { body ->
                if (body.contains("kbList")) return body to url
                last = body
            }.onFailure { last = it.message.orEmpty() }
        }

        if (last.contains("login_slogin")) {
            throw ZhengfangException("登录状态失效，请在浏览器确认账号能正常进入教务系统")
        }
        throw ZhengfangException("教务系统没有返回可识别的课表数据")
    }

    private fun isLoginFailure(body: String, finalUrl: String): Boolean {
        val markers = listOf("密码错误", "用户名或密码", "用户不存在", "登录失败", "验证码")
        if (markers.any { body.contains(it) }) return true
        return finalUrl.contains("login_slogin") && body.contains("csrftoken")
    }

    private fun loginErrorText(body: String): String? {
        val text = runCatching {
            Jsoup.parse(body).select("#showErrorTip, .alert-danger, .error, #msg, .tips").text()
        }.getOrNull().orEmpty()
        return text.takeIf { it.isNotBlank() }?.take(120)
    }

    private fun get(url: String): Request = Request.Builder()
        .url(url)
        .header("User-Agent", USER_AGENT)
        .get()
        .build()

    private fun execute(request: Request): HttpResult {
        http.newCall(request).execute().use { response ->
            val body = response.body.string()
            return HttpResult(
                code = response.code,
                body = body,
                finalUrl = response.request.url.toString(),
            )
        }
    }

    private data class HttpResult(
        val code: Int,
        val body: String,
        val finalUrl: String,
    )

    private class MemoryCookieJar : CookieJar {
        private val cookies = mutableMapOf<String, MutableList<Cookie>>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val bucket = this.cookies.getOrPut(url.host) { mutableListOf() }
            cookies.forEach { incoming ->
                bucket.removeAll { it.name == incoming.name }
                bucket += incoming
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> =
            cookies[url.host].orEmpty().filter { it.matches(url) }
    }

    private companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(25, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()

        fun normalizeBase(raw: String): String {
            var value = raw.trim()
            if (value.isEmpty()) throw ZhengfangException("请填写教务系统地址")
            if (!value.startsWith("http://") && !value.startsWith("https://")) {
                value = "https://$value"
            }
            if (!value.endsWith("/")) value += "/"
            return value
        }
    }
}
