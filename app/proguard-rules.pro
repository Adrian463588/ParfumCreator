# Proguard & R8 Hardening Rules for Parfume Creator (OWASP MASVS-RESILIENCE)

# Aggressive Obfuscation & Repackaging
-repackageclasses 'com.parfumecreator.internal'
-allowaccessmodification
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable,Signature,*Annotation*,InnerClasses,EnclosingMethod

# Strip verbose/debug/info/warn logging calls in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
}

# LiteRT / TensorFlow Lite
-keep class com.google.ai.edge.litert.** { *; }
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**

# PDFBox
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**

# Room
-keep class androidx.room.RoomDatabase { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**

# Gson & Data Transfer Models
-keep class com.parfumecreator.data.local.** { *; }
-keep class com.parfumecreator.domain.model.** { *; }

# Security & Crypto Models
-keep class com.parfumecreator.core.security.SecurityReport { *; }
-keep class com.parfumecreator.core.security.EncryptedPayload { *; }
-keep class com.parfumecreator.core.security.AndroidKeyStoreCrypto { *; }
-keep class com.parfumecreator.core.security.SecurityHardeningManager { *; }

# JNI & Native Security Bridge
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
-keep class com.parfumecreator.core.nativebridge.** { *; }

# Coroutines & Dispatchers
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
