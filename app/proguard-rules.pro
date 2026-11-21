# Coolapk Wear Proguard rules
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.mindrot.jbcrypt.**
-dontwarn com.google.zxing.**

-keepattributes *Annotation*
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
