-keepattributes Signature
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault
-dontwarn org.conscrypt.**
-dontwarn io.ktor.**
-dontwarn org.slf4j.**

-keep class ai.byak.app.data.remote.** { *; }
-keep class ai.byak.app.billing.** { *; }
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class **$$serializer { *; }
