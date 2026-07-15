# ── MyMusic ProGuard Rules ─────────────────────────────────────────

# Preserve line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Firebase ───────────────────────────────────────────────────────
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# ── Media3 / ExoPlayer ────────────────────────────────────────────
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ── Coil ──────────────────────────────────────────────────────────
-keep class coil.** { *; }
-dontwarn coil.**

# ── JAudioTagger ──────────────────────────────────────────────────
-keep class org.jaudiotagger.** { *; }
-dontwarn org.jaudiotagger.**
-dontwarn javax.sound.**
-dontwarn com.sun.**
-dontwarn java.awt.**
-dontwarn java.beans.**
-dontwarn javax.swing.**

# ── Gson / JSON (if used indirectly) ──────────────────────────────
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

# ── R8 full mode compatibility ────────────────────────────────────
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
