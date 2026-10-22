package com.rep.app

import android.app.Application
import com.rep.app.data.ai.AiClient
import com.rep.app.data.db.AppDatabase
import com.rep.app.data.prompts.PromptStore
import com.rep.app.data.settings.SettingsStore
import com.rep.app.domain.SimulationEngine
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Ручной DI: всё создаётся один раз здесь. */
class AppContainer(app: Application) {
    val db = AppDatabase.create(app)
    val settings = SettingsStore(app)
    val prompts = PromptStore(app)
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        // Локальные модели могут думать долго.
        .readTimeout(180, TimeUnit.SECONDS)
        .build()
    val ai = AiClient(http, settings, db.tokenUsageDao())
    val engine = SimulationEngine(db, ai, prompts)
}

class RepApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
