# Add project-specific ProGuard rules here.

# --- kotlinx.serialization -------------------------------------------------
# Generated serializers are looked up reflectively via the Companion object, so
# both must survive shrinking for every @Serializable model in the app.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class io.rank5.app.**$$serializer { *; }
-keepclassmembers class io.rank5.app.** {
    *** Companion;
}
-keepclasseswithmembers class io.rank5.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Ktor / OkHttp -----------------------------------------------------------
# Engines are constructed explicitly (HttpClient(OkHttp)); only silence the
# optional-dependency warnings from the JVM-flavoured artifacts.
-dontwarn io.ktor.**
-dontwarn org.slf4j.**
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- Coroutines --------------------------------------------------------------
-dontwarn kotlinx.coroutines.debug.**
