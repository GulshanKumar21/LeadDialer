# ════════════════════════════════════════════════════════════
# ProGuard / R8 Rules — LeadDialer (Adyapan CRM)
# 🔒 SECURITY: -dontoptimize removed so R8 can shrink dead code
# ════════════════════════════════════════════════════════════
-keep class org.apache.poi.** { *; }
-keep class org.apache.poi.xssf.** { *; }
-keep class org.apache.poi.hssf.** { *; }
-keep class org.apache.poi.ss.** { *; }
-keep class org.apache.poi.openxml4j.** { *; }
-keep class org.apache.poi.ooxml.** { *; }
-keep class org.apache.poi.util.** { *; }

# WorkbookFactory SPI implementations (CRITICAL — loaded via reflection)
-keep class org.apache.poi.ss.usermodel.WorkbookFactory { *; }
-keep class org.apache.poi.xssf.usermodel.XSSFWorkbook { *; }
-keep class org.apache.poi.hssf.usermodel.HSSFWorkbook { *; }
-keep class org.apache.poi.xssf.usermodel.XSSFWorkbookFactory { *; }
-keep class org.apache.poi.hssf.usermodel.HSSFWorkbookFactory { *; }
-keep class * implements org.apache.poi.ss.usermodel.WorkbookProvider { *; }
-keep class org.apache.poi.ooxml.POIXMLDocumentPart { *; }
-keep class org.apache.poi.poifs.filesystem.** { *; }

-keep class org.apache.xmlbeans.** { *; }
-keep class org.openxmlformats.schemas.** { *; }
-keep class org.openxmlformats.schemas.spreadsheetml.** { *; }
-keep class org.openxmlformats.schemas.drawingml.** { *; }
-keep class com.microsoft.schemas.** { *; }
-keep class org.etsi.uri.** { *; }
-keep class org.w3.x2000.** { *; }
-keep class schemaorg_apache_xmlbeans.** { *; }
-dontwarn org.apache.poi.**
-dontwarn org.apache.xmlbeans.**
-dontwarn org.openxmlformats.**
-dontwarn com.microsoft.schemas.**
-dontwarn org.etsi.uri.**
-dontwarn org.w3.x2000.**
-dontwarn schemaorg_apache_xmlbeans.**

# Apache Commons (POI dependency)
-keep class org.apache.commons.** { *; }
-dontwarn org.apache.commons.**

# Google Guava
-keep class com.google.common.** { *; }
-dontwarn com.google.common.**
-dontwarn com.google.errorprone.**
-dontwarn com.google.j2objc.**
-dontwarn sun.misc.Unsafe

# Firebase
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Room Database
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-dontwarn androidx.room.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# 🔒 SECURITY FIX: Do NOT keep all app classes — let R8 obfuscate them.
# Only keep classes that are referenced via reflection or serialization:
# - Room: Entity/Dao loaded by generated code
# - Firebase data models (Firestore uses field names via reflection)
# - Serialized data classes that use @SerializedName or are passed to Firestore
-keep class com.adyapan.leaddialer.Lead { *; }
-keep class com.adyapan.leaddialer.Callrecord { *; }
-keep class com.adyapan.leaddialer.CallRecord { *; }
-keep class com.adyapan.leaddialer.Attendancerecord { *; }
-keep class com.adyapan.leaddialer.AttendanceRecord { *; }
-keep class com.adyapan.leaddialer.EmployeeSummary { *; }
-keep class com.adyapan.leaddialer.Contact { *; }
-keep class com.adyapan.leaddialer.CallCheckResult { *; }
-keep class com.adyapan.leaddialer.LeadDialerApp { *; }
-keep class com.adyapan.leaddialer.BuildConfig { *; }
# Keep Activity/Service/Receiver/Provider classes (registered in AndroidManifest)
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends androidx.fragment.app.Fragment

-keep class androidx.lifecycle.** { *; }
-dontwarn androidx.lifecycle.**
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

-keep class com.airbnb.lottie.** { *; }
-dontwarn com.airbnb.lottie.**

-keep class com.google.android.exoplayer2.** { *; }
-dontwarn com.google.android.exoplayer2.**

-dontwarn javax.xml.**
-dontwarn org.xml.sax.**
-dontwarn org.w3c.dom.**
-dontwarn java.awt.**
-dontwarn java.beans.**

# Apache POI — StAX / OOXML XML factory (Excel ke liye CRITICAL)
-keep class javax.xml.stream.** { *; }
-dontwarn javax.xml.stream.**
-keep class com.ctc.wstx.** { *; }
-dontwarn com.ctc.wstx.**
-keep class org.codehaus.stax2.** { *; }
-dontwarn org.codehaus.stax2.**

# XMLBeans — POI OOXML uses reflection on these
-keep class org.apache.xmlbeans.impl.** { *; }
-keep class schemasMicrosoftComOfficeExcel.** { *; }
-keep class schemasMicrosoftComOfficeOffice.** { *; }
-keep class schemasMicrosoftComOfficeWord.** { *; }
-keep class schemasMicrosoftComVml.** { *; }
-dontwarn schemasMicrosoftCom**

# Keep reflection attributes
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class com.bumptech.glide.** { *; }
-dontwarn com.bumptech.glide.**

# ── Apache POI ServiceLoader / SPI (CRITICAL for Excel parsing) ───────────────
# POI uses Java ServiceLoader to dynamically load implementations.
# Without these rules, SPI classes get stripped and Excel fails silently.
-keep class * implements java.util.Iterator { *; }
-keepclassmembers class * {
    public static ** provider();
}
-keep class * implements org.apache.poi.ss.usermodel.** { *; }
-keep class * implements org.apache.poi.xssf.usermodel.** { *; }
-keep class * implements org.apache.xmlbeans.XmlObject { *; }
-keepclassmembers class * implements org.apache.xmlbeans.XmlObject {
    public static org.apache.xmlbeans.SchemaType type;
}

# Preserve META-INF/services entries — required by POI SPI
-keepnames class * implements java.util.ServiceLoader
-keep class * implements org.apache.poi.ss.formula.** { *; }

# Java reflection used by POI internally
-keepclassmembers class * {
    @java.lang.reflect.** *;
}
-keepclasseswithmembernames class * {
    native <methods>;
}