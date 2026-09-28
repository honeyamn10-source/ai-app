package ai.byak.app

import android.app.Application
import ai.byak.app.billing.BillingManager
import ai.byak.app.data.ApiClient
import ai.byak.app.data.SessionStore

class ByakApplication : Application() {
    lateinit var sessionStore: SessionStore
    lateinit var api: ApiClient
    val billing: BillingManager by lazy { BillingManager(this) }
    override fun onCreate() {
        super.onCreate()
        sessionStore = SessionStore(this)
        api = ApiClient(BuildConfig.API_BASE_URL, sessionStore)
    }
}
