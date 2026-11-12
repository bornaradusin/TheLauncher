# The rules from AOSP are located in proguard.flags file, we can just maintain TheLauncher related rules here.

# Optimization options.
-allowaccessmodification
-dontusemixedcaseclassnames
-allowaccessmodification
-keepattributes InnerClasses, *Annotation*, Signature, SourceFile, LineNumberTable

# Remove some Kotlin overhead
-processkotlinnullchecks remove

# Common rules.
-keep class android.window.** { *; }
-keep class android.view.** { *; }

-keepclassmembers class * implements android.os.Parcelable {
  public static final ** CREATOR;
}

# TheLauncher specific rules.
-keep,allowshrinking,allowoptimization class app.thelauncher.TheLauncherProto$* { *; }
-keep,allowshrinking,allowoptimization class app.thelauncher.TheLauncherApp { *; }
-keep,allowshrinking,allowoptimization class app.thelauncher.MainLauncher { *; }
-keep,allowshrinking,allowoptimization class app.thelauncher.compatlib.** { *; }

-keep,allowshrinking,allowoptimization class com.google.protobuf.Timestamp { *; }
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }

# We intentionally remove it to replace Smartspacer's widget popup with our own Launcher3 popup
-dontwarn com.skydoves.balloon.*

# This shouldn't concern us much
-dontwarn androidx.window.extensions.**
-dontwarn androidx.window.sidecar.**
