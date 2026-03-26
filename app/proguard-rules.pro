# Add project specific ProGuard rules here.

# Keep Kotlin metadata
-keep class kotlin.Metadata { *; }

# Keep coroutines
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Keep serialization
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepnames class kotlinx.serialization.serializers.SerializersKt {
    *** serializableTypeInfo(...);
}
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# TensorFlow Lite
-keep class org.tensorflow.lite.** { *; }
-keepclassmembers class org.tensorflow.lite.** { *; }

# ONNX Runtime
-keep class ai.onnxruntime.** { *; }
-keepclassmembers class ai.onnxruntime.** { *; }

# Vosk
-keep class org.vosk.** { *; }
-keepclassmembers class org.vosk.** { *; }

# Huawei HMS
-keep class com.huawei.** { *; }
-keepclassmembers class com.huawei.** { *; }
-dontwarn com.huawei.**

# Keep accessibility service
-keep class com.binti.dilink.dilink.DiLinkAccessibilityService { *; }

# Keep all parcelable classes
-keepclassmembers class * implements android.os.Parcelable {
    static ** CREATOR;
}

# Keep enum classes
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ====================================
# Binti-specific ProGuard rules
# ====================================

# Keep model asset paths and model manager
-keep class com.binti.dilink.utils.ModelManager { *; }
-keepclassmembers class com.binti.dilink.utils.ModelManager { *; }

# Keep DiLink package names for AccessibilityService
-keep class com.byd.** { *; }
-dontwarn com.byd.**

# Keep serialization annotations for intent mapping
-keepattributes Signature, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Keep ONNX/TFLite native method signatures
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep OkHttp and Okio for model downloads
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Keep Gson serialization
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
