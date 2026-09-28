package ai.byak.app

import android.app.Application
import ai.byak.app.billing.BillingManager
import ai.byak.app.data.ApiClient
import ai.byak.app.data.SessionStore
import ai.byak.app.data.SettingsStore

class ByakApplication : Application() {
    lateinit var sessionStore: SessionStore
    lateinit var settings: SettingsStore
    lateinit var api: ApiClient
    val billing: BillingManager by lazy { BillingManager(this) }
    override fun onCreate() {
        super.onCreate()
        sessionStore = SessionStore(this)
        settings = SettingsStore(this, BuildConfig.API_BASE_URL)
        api = ApiClient(settings, sessionStore)
    }
}
