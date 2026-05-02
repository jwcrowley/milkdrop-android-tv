# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep JNI-called classes
-keep class com.example.milkdrop.ProjectMBridge { *; }

# Keep Leanback classes
-keep class androidx.leanback.** { *; }

# Keep native method declarations
-keepclasseswithmembernames class * { native <methods>; }

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
