# Add project specific ProGuard rules here.

# ====================================
# Kotlin metadata
# ====================================
-keep class kotlin.Metadata { *; }

# ====================================
# Coroutines
# ====================================
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ====================================
# Serialization (kotlinx-serialization)
# ====================================
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepnames class kotlinx.serialization.serializers.SerializersKt {
    *** serializableTypeInfo(...);
}
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# ====================================
# Vosk (offline ASR)
# ====================================
-keep class org.vosk.** { *; }
-keepclassmembers class org.vosk.** { *; }

# ====================================
# Huawei HMS Core
# ====================================
-keep class com.huawei.** { *; }
-keepclassmembers class com.huawei.** { *; }
-dontwarn com.huawei.**

# ====================================
# Hilt (DI) — Phase 2
# ====================================
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.HiltAndroidApp { *; }
-keepclassmembers class * { @dagger.hilt.android.lifecycle.HiltViewModel <init>(...); }
# Hilt-generated classes (Hilt_$ClassName)
-keep class **_HiltModules { *; }
-keep class **_HiltModules$** { *; }
-keep class * extends dagger.hilt.android.internal.lifecycle.HiltViewModelFactory$ViewModelComponentBuilderEntryPoint { *; }

# ====================================
# Room (database) — Phase 2
# ====================================
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keepclassmembers @androidx.room.Entity class * { <fields>; }
-keep class * extends androidx.room.Dao { *; }
-keepclassmembers class * {
    @androidx.room.* <methods>;
}

# ====================================
# Compose (Phase 2) — keep Compose runtime and lambda classes
# ====================================
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }

# ====================================
# Binti application classes
# ====================================
# Keep accessibility service
-keep class com.binti.dilink.dilink.DiLinkAccessibilityService { *; }

# Keep control/settings bridges and initializers (loaded via reflection
# and flavor-specific class names — R8 would strip these without keep rules)
-keep class com.binti.dilink.dilink.control.** { *; }
-keep class com.binti.dilink.dilink.settings.** { *; }
-keep class com.binti.dilink.telemetry.** { *; }
-keep class com.binti.dilink.dilink.telemetry.** { *; }

# Keep all Binti application classes from being stripped by R8
-keep class com.binti.dilink.** { *; }
-keepclassmembers class com.binti.dilink.** { *; }

# Keep :contract module (public API — must not be stripped)
-keep class com.binti.dilink.contract.** { *; }

# ====================================
# Parcelables & enums
# ====================================
-keepclassmembers class * implements android.os.Parcelable {
    static ** CREATOR;
}
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ====================================
# Binti-specific keeps
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

# Keep native method signatures (Vosk, sherpa-onnx via reflection)
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep Gson serialization
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
