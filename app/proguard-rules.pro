# Coolapk Wear Proguard rules
-keepattributes *Annotation*
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keepclassmembers class * {
    companion object;
}
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.mindrot.jbcrypt.**
