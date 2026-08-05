# ── MyMusic ProGuard Rules ─────────────────────────────────────────

# Preserve line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── R8 optimizations ──────────────────────────────────────────────
-repackageclasses
-allowaccessmodification
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*

# ── Firebase Auth (uses reflection internally) ────────────────────
-keep class com.google.firebase.auth.** { *; }
-dontwarn com.google.firebase.auth.**

# ── Firebase Firestore (reflection for document mapping) ──────────
-keep class com.google.firebase.firestore.** { *; }
-dontwarn com.google.firebase.firestore.**

# ── Media3 / ExoPlayer ────────────────────────────────────────────
-keep class androidx.media3.** extends androidx.media3.common.Player { *; }
-keep class androidx.media3.session.** { *; }
-dontwarn androidx.media3.**

# ── JAudioTagger (Java library with unused desktop deps) ──────────
-keep class org.jaudiotagger.** { *; }
-dontwarn org.jaudiotagger.**
-dontwarn javax.sound.**
-dontwarn com.sun.**
-dontwarn java.awt.**
-dontwarn java.beans.**
-dontwarn javax.swing.**

# ── Gson / JSON serialization ─────────────────────────────────────
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# ── Data model classes (keep field names for Firestore/JSON) ──────
-keep class com.nandu.mymusic.model.** { *; }
-keepclassmembers class com.nandu.mymusic.model.** {
    <fields>;
}

# ── Enum safety ───────────────────────────────────────────────────
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ── Parcelable ────────────────────────────────────────────────────
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# ── Kotlin coroutines ─────────────────────────────────────────────
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# ── Remove logging in release ─────────────────────────────────────
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
