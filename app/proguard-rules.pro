# Preserve readable line numbers for release diagnostics
-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod,Exceptions,*Annotation*

# Shizuku UID 2000 remote UserService & AIDL Binder interfaces
-keep class com.superglobalpeq.app.service.AudioLoopbackUserService { *; }
-keep class com.superglobalpeq.app.service.AudioLoopbackUserService$** { *; }
-keep interface com.superglobalpeq.app.service.IAudioLoopbackService { *; }
-keep class com.superglobalpeq.app.service.IAudioLoopbackService$** { *; }
-keep class com.superglobalpeq.app.service.AudioTelemetry { *; }
-keep class rikka.shizuku.** { *; }
-keep interface rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }
-keep interface moe.shizuku.** { *; }

# Keep Android OS Parcelable / Binder / native callbacks
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}
-keepclassmembers class * {
    native <methods>;
}

# Preserve enums used in SharedPreferences / JSON / APO persistence
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keep class com.superglobalpeq.app.model.** { *; }
-keep enum com.superglobalpeq.app.data.QuveThemeMode { *; }
-keep enum com.superglobalpeq.app.data.QuveThemePreset { *; }
-keep enum com.superglobalpeq.app.data.GlassRenderStyle { *; }
-keep enum com.superglobalpeq.app.data.PillMotionStyle { *; }

# Reflection targets invoked inside Shizuku UID 2000 loopback service
-dontwarn android.app.ActivityThread
-dontwarn android.media.audiopolicy.**
-dontwarn android.os.ServiceManager

# Keep Quick Settings TileService entries & tile picker package
-keep class com.superglobalpeq.app.tile.** { *; }
