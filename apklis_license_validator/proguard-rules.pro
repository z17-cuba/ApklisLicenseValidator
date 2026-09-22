# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Kotlin string templates compile to invokedynamic/StringConcatFactory calls that are
# desugared away at build time; R8 still flags the class as missing since it targets an
# android.jar below API 26. Harmless - the desugared bytecode never references it at runtime.
-dontwarn java.lang.invoke.StringConcatFactory

# Public API consumed by host apps via JitPack - keep names and signatures intact.
-keep public class cu.uci.android.apklis_license_validator.ApklisLicenseValidator {
    public *;
}
-keep public interface cu.uci.android.apklis_license_validator.ApklisLicenseValidator$LicenseCallback {
    *;
}
-keep public class cu.uci.android.apklis_license_validator.ApklisLicenseValidator$LicenseError {
    *;
}
-keep public class cu.uci.android.apklis_license_validator.ApklisLicenseValidator$ApklisLicenseUtils {
    public *;
}

# WebSocketService is only referenced by class name from an Intent (startForegroundService),
# never called directly - R8 can't see that usage and would otherwise strip/rename it.
-keep class cu.uci.android.apklis_license_validator.WebSocketService { *; }

# Gson deserializes these via reflection using field names/@SerializedName; keep them
# so minification doesn't rename fields and silently break JSON parsing.
-keepattributes Signature,*Annotation*
-keepclassmembers class cu.uci.android.apklis_license_validator.models.** {
    <fields>;
}
-keep class cu.uci.android.apklis_license_validator.models.PaymentResponse { *; }
-keep class cu.uci.android.apklis_license_validator.models.PaymentResponse$* { *; }