# Add project specific ProGuard rules here.

# Kotlin
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep class kotlin.Metadata { *; }

# Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Firebase
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class breathy.com.**$$serializer { *; }
-keepclassmembers class breathy.com.** { *** Companion; }
-keepclasseswithmembers class breathy.com.** { kotlinx.serialization.KSerializer serializer(...); }

# Coil
-dontwarn coil.**

# AdMob
-keep public class com.google.android.gms.ads.** { public *; }
-dontwarn com.google.android.gms.ads.**
