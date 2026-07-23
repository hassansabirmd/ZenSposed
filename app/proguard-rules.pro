# Keep Xposed entry points (required if minify is enabled later)
-keep class com.hassan.zensposed.xposed.** { *; }
-keep class de.robv.android.xposed.** { *; }
-dontwarn de.robv.android.xposed.**

# Room / DataStore / libsu — safe defaults if R8 is turned on for release
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn com.topjohnwu.superuser.**
