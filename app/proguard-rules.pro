# TeleRelay R8 rules.
# Retrofit + kotlinx-serialization build their reflection-free wire models at
# compile time, but keep the DTO classes and their generated serializers intact.

# --- kotlinx-serialization ---
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.telerelay.**$$serializer { *; }
-keepclassmembers class com.telerelay.** {
    *** Companion;
}
-keepclasseswithmembers class com.telerelay.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Retrofit ---
-keepattributes Signature, Exceptions
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# --- OkHttp / Okio: silence harmless warnings ---
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
