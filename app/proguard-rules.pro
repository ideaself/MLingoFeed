# Add project specific ProGuard rules here.
-keep class com.mlingofeed.data.database.** { *; }
-keep class com.mlingofeed.data.api.** { *; }

# WorkManager instantiates workers by class name; keep names stable across updates.
-keep class com.mlingofeed.data.work.** { *; }
