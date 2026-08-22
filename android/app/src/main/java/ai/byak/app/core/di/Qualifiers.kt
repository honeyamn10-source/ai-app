package ai.byak.app.core.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BareOkHttp

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthenticatedOkHttp
