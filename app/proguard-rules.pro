# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

-keep class com.example.R$string { *; }
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-keepclassmembers class com.example.domain.security.** { *; }

# Room entities, DAOs, and Database
-keep class * extends androidx.room.RoomDatabase
-keep class com.example.data.db.** { *; }

# Moshi reflection & KotlinJsonAdapterFactory for CloudSyncPayload
-keep class com.squareup.moshi.** { *; }
-keep class com.example.domain.service.CloudSyncPayload { *; }
-keepclassmembers class com.example.domain.service.CloudSyncPayload { *; }
-keep class kotlin.Metadata { *; }

# Navigation, UI Models, ZXing QR & CameraX
-keep class com.example.ui.** { *; }
-keep class com.google.zxing.** { *; }
-keep class androidx.camera.** { *; }

# Strip debug/verbose Log calls in R8 release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}
