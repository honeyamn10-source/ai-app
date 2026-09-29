package ai.byak.app

import android.app.Application
import ai.byak.app.billing.BillingManager
import ai.byak.app.data.ApiClient
import ai.byak.app.data.ByakApi
import ai.byak.app.data.Session
import ai.byak.app.data.SessionStore
import ai.byak.app.data.SettingsStore
import ai.byak.app.data.local.Gateway
import ai.byak.app.data.local.LocalApi
import ai.byak.app.data.local.LocalModel
import ai.byak.app.data.local.LocalStore

class ByakApplication : Application() {
    lateinit var sessionStore: SessionStore
    lateinit var settings: SettingsStore
    /** Talks to a self-hosted BYAK server (optional). */
    lateinit var api: ApiClient
    val billing: BillingManager by lazy { BillingManager(this) }
    /** Runs BYAK entirely on this phone (default). */
    val local: LocalApi by lazy { LocalApi(LocalStore(this), billing, sessionStore, gateway = Gateway(localModel)) }
    /** The downloadable offline model (Qwen3 0.6B). */
    val localModel: LocalModel by lazy { LocalModel(this) }

    override fun onCreate() {
        super.onCreate()
        sessionStore = SessionStore(this)
        settings = SettingsStore(this, BuildConfig.API_BASE_URL)
        api = ApiClient(settings, sessionStore)
    }

    fun apiFor(session: Session): ByakApi = if (SessionStore.isOnDevice(session)) local else api
}
