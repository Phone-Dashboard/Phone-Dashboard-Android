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
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

-dontobfuscate

-dontwarn okhttp3.internal.platform.ConscryptPlatform.**
-dontwarn com.google.auto.value.**
-dontwarn org.joda.convert.**

-keep class org.conscrypt.**
-keep class org.codehaus.mojo.**

-dontwarn org.conscrypt.**
-dontwarn org.codehaus.mojo.**

-dontwarn java.beans.**
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.openjsse.**
-dontwarn org.kxml2.io.**

-keep class com.google.android.gms.internal.places.**

-keep class com.google.android.material.** { *; }
-keep class com.audacious_software.passive_data_kit.** { *; }
-keep class com.audacious_software.phone_dashboard.** { *; }
-keep class org.xmlpull.v1.** { *; }
-keep class okhttp3.Headers { *; }
