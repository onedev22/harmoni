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

# Keep Coil classes
-keep class coil.** { *; }

# Keep Media3 classes
-keep class androidx.media3.** { *; }

# Keep DataStore classes
-keep class androidx.datastore.** { *; }

# Keep model classes used with JSON serialization
-keep class com.amurayada.music.data.model.** { *; }

# jaudiotagger - ignore missing Java AWT/ImageIO classes (not available on Android)
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.swing.**

# NewPipeExtractor and Rhino - ignore missing Java desktop classes
-dontwarn java.beans.**
-dontwarn javax.script.**
-dontwarn org.mozilla.javascript.**
-keep class org.schabi.newpipe.extractor.** { *; }
-keep interface org.schabi.newpipe.extractor.** { *; }

# Attributes essential for libraries using reflection/generics
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Keep custom utils to prevent accidental stripping
-keep class com.amurayada.music.utils.** { *; }

# Keep YouTube data, config, and enums (Fixes Filter Buttons)
-keep class com.amurayada.music.data.youtube.** { *; }

# Keep Repository implementations 
-keep class com.amurayada.music.data.repository.** { *; }

# Keep ViewModel states and Enums (Fixes UI State in Release)
-keep class com.amurayada.music.ui.viewmodel.** { *; }

# youtubedl-android
-keep class com.yausername.youtubedl_android.** { *; }
-keep class io.github.junkfood02.** { *; }

# Security Crypto (EncryptedSharedPreferences) - Critical for Release builds
-keep class androidx.security.crypto.** { *; }
-keep class com.google.crypto.tink.** { *; }
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.crypto.tink.**
-dontwarn com.google.api.client.**
-dontwarn org.joda.time.**
-dontwarn javax.annotation.**

# OkHttp (Network)
-keepattributes Signature
-keepattributes *Annotation*
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**