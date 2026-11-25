# Seamless ProGuard/R8 Rules — Security Hardened

# ===== SECURITY: Strip debug/verbose/info logs from release builds =====
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# ===== Cryptography (keep class names only for reflection) =====
-keep class com.seamless.android.protocol.** { *; }
-keepnames class com.seamless.android.crypto.** { *; }
-dontwarn org.bouncycastle.**
-keep class org.bouncycastle.** { *; }

# Keep SecureIdentityStateManager (used by reflection/EncryptedSharedPreferences)
-keep class com.seamless.android.identity.SecureIdentityStateManager {
    private android.content.SharedPreferences prefs;
    *;
}

# ===== Arti (Tor) =====
-keep class info.guardianproject.arti.** { *; }
-keep class org.torproject.jni.** { *; }
-keepnames class org.torproject.jni.**
-dontwarn info.guardianproject.arti.**
-dontwarn org.torproject.jni.**

# ===== Noise Protocol (native crypto — keep fully) =====
-keep class com.seamless.android.noise.southernstorm.** { *; }
-keepnames class com.seamless.android.noise.** { *; }

# ===== OkHttp (network) =====
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# ===== Gson (uses reflection on field names) =====
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# ===== Room database (entities need field names for schema) =====
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers @androidx.room.Entity class * { *; }

# ===== Data/Model classes (Parcelable + Gson serialization) =====
-keep class com.seamless.android.model.** { *; }
-keep class com.seamless.android.data.** { *; }

# ===== Bluetooth Mesh (delegates use interfaces by name) =====
-keepnames class com.seamless.android.mesh.** { *; }
-keepnames class com.seamless.android.service.** { *; }

# ===== Nostr (identity bridge uses reflection-like patterns) =====
-keepnames class com.seamless.android.nostr.** { *; }
-keepnames class com.seamless.android.identity.** { *; }
-keepnames class com.seamless.android.favorites.** { *; }

# ===== Nordic BLE =====
-keep class no.nordicsemi.android.** { *; }
-dontwarn no.nordicsemi.android.**

# ===== SQLCipher =====
-keep class net.zetetic.database.** { *; }
-dontwarn net.zetetic.database.**

# ===== Compose (framework — let R8 handle) =====
-dontwarn androidx.compose.**

# ===== Keep native methods =====
-keepclasseswithmembernames class * {
    native <methods>;
}

# ===== Prevent R8 from stripping interface methods =====
-keepclassmembers,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# ===== WebRTC (keep native JNI callbacks & package-private hardware encoder factory) =====
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**
