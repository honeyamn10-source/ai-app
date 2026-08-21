package ai.byak.app

import android.app.Application
import ai.byak.app.billing.BillingManager
import ai.byak.app.data.ApiClient

class ByakApplication : Application() {
    lateinit var api: ApiClient
    lateinit var billing: BillingManager
    override fun onCreate() {
        super.onCreate()
        api = ApiClient(this)
        billing = BillingManager(this)
    }
}
