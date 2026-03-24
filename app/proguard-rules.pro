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
