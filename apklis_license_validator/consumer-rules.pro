# Rules bundled into the AAR and applied automatically to any host app's own
# R8/ProGuard pass when it depends on this library and minifies its release build.

# Kotlin string templates in this library compile to invokedynamic/StringConcatFactory
# calls that are desugared away at build time; R8 still flags the class as missing since
# it targets an android.jar below API 26. Harmless - never referenced at runtime.
-dontwarn java.lang.invoke.StringConcatFactory

# Public API - referenced by host apps via JitPack, must keep its names and signatures.
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

# WebSocketService is started only by class name via Intent (startForegroundService),
# so the host app's shrinker can't see that usage and would otherwise strip/rename it.
-keep class cu.uci.android.apklis_license_validator.WebSocketService { *; }

# Gson deserializes these via reflection (fromJson targets QrCode and
# VerifyLicenseResponse directly, both via field names/@SerializedName). Keeping only
# <fields> is not enough: a class with no call-graph usage the shrinker can see (its
# fields are only ever read through Gson's reflection) can still get stripped down to an
# empty shell, which Gson then reports as an "abstract class" it can't instantiate - hit
# for real in this library's own release AAR (see git history for the fix).
-keepattributes Signature,*Annotation*
-keep class cu.uci.android.apklis_license_validator.models.** { *; }
-keep class cu.uci.android.apklis_license_validator.models.PaymentResponse { *; }
-keep class cu.uci.android.apklis_license_validator.models.PaymentResponse$* { *; }
