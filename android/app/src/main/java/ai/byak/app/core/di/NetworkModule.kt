package ai.byak.app.core.di

import ai.byak.app.BuildConfig
import ai.byak.app.data.network.JwtAuthenticator
import ai.byak.app.data.network.JwtInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import io.ktor.http.URLProtocol
import io.ktor.http.headers
import io.ktor.serialization.kotlinx.json.json
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    @BareOkHttp
    fun provideBareOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MINUTES)
        .writeTimeout(60, TimeUnit.SECONDS)
        .pingInterval(25, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
        .build()

    @Provides
    @Singleton
    @AuthenticatedOkHttp
    fun provideAuthenticatedOkHttp(
        @BareOkHttp bare: OkHttpClient,
        interceptor: JwtInterceptor,
        authenticator: JwtAuthenticator,
    ): OkHttpClient = bare.newBuilder()
        .addInterceptor(interceptor)
        .authenticator(authenticator)
        .build()

    @Provides
    @Singleton
    fun provideHttpClient(
        @AuthenticatedOkHttp okHttp: OkHttpClient,
        json: Json,
    ): HttpClient = HttpClient(OkHttp) {
        engine { preconfigured = okHttp }
        expectSuccess = false
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            connectTimeoutMillis = 20_000
            requestTimeoutMillis = HttpTimeout.INFINITE_TIMEOUT_MS
            socketTimeoutMillis = HttpTimeout.INFINITE_TIMEOUT_MS
        }
        defaultRequest {
            headers {
                append(HttpHeaders.UserAgent, "BYAK-AI/${BuildConfig.VERSION_NAME} Android")
                append(HttpHeaders.Accept, "application/json, text/event-stream")
            }
        }
    }
}
