# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep Retrofit
-keepattributes Signature
-keepattributes Exceptions

# Keep Gson
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class com.photoframer.data.api.** { *; }
