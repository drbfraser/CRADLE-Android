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

# https://stackoverflow.com/a/39388953
# -dontobfuscate
# -optimizations !code/allocation/variable

# Preserve the line number information for debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

-printusage usage.txt

#-keep class com.cradleVSA.neptune.** { *; }
#-keepclassmembers class com.cradleVSA.neptune.** { *; }

# BlurKit and RenderScript causing crashes on Android 4.4.4
# Caused by calling some native method
#   E/AndroidRuntime( 6589): FATAL EXCEPTION: main
#   E/AndroidRuntime( 6589): Process: com.cradleVSA.neptune, PID: 6589
#   E/AndroidRuntime( 6589): java.lang.NoSuchMethodError: no static or non-static method "Landroidx/renderscript/RenderScript;.nDeviceDestroy(J)V"
#   ...
-keepclasseswithmembernames,includedescriptorclasses class androidx.renderscript.RenderScript {
  native <methods>;
}
-keep class androidx.renderscript.** { *; }
-keepclasseswithmembernames,includedescriptorclasses class android.support.v8.renderscript.RenderScript {
  native <methods>;
}
-keep class android.support.v8.renderscript.** { *; }

-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata { *; }
-keep @kotlin.Metadata class *
-keepclasseswithmembers @kotlin.Metadata class * { *; }
