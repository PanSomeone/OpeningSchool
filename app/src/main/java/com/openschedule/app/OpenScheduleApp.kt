package com.openschedule.app

import android.app.Application
import android.content.Context
import com.openschedule.adapter.zhengfang.ZhengfangImporter
import com.openschedule.core.data.AnnouncementStore
import com.openschedule.core.data.AppLog
import com.openschedule.core.data.ExternalAdapterCatalog
import com.openschedule.core.data.PluginModuleStore
import com.openschedule.core.data.SettingsStore
import com.openschedule.core.data.TimetableStore
import com.openschedule.core.model.TimetableImporter
import com.openschedule.feature.home.WeatherStore

class OpenScheduleApp : Application() {

    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        AppLog.install(this)
        graph = AppGraph(this)
    }
}

/**
 * Hand-rolled dependency graph, for the same reason the hot-water client used
 * one: there are few objects and a long lifetime, and wiring them by hand keeps
 * the path from a screen to a file readable in one sitting.
 */
class AppGraph(context: Context) {

    val timetableStore = TimetableStore(context)
    val settingsStore = SettingsStore(context)
    val weatherStore = WeatherStore(context)
    val announcementStore = AnnouncementStore(context)
    val moduleStore = PluginModuleStore(context)
    val externalAdapters = ExternalAdapterCatalog(context)

    /** Adapters compiled into this APK. Runtime ones come from [externalAdapters]. */
    val builtInImporters: List<TimetableImporter> = listOf(
        ZhengfangImporter(),
    )
}
