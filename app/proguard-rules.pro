# Proguard rules for Parfume Creator

# LiteRT / TensorFlow Lite
-keep class com.google.ai.edge.litert.** { *; }
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**

# PDFBox
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**

# Room
-keep class androidx.room.RoomDatabase { *; }

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.parfumecreator.data.local.** { *; }
-keep class com.parfumecreator.domain.model.** { *; }
