package ai.byak.app

import android.app.Application
import ai.byak.app.data.ApiClient

class ByakApplication : Application() {
    lateinit var api: ApiClient
    override fun onCreate() {
        super.onCreate()
        api = ApiClient(this)
    }
}
