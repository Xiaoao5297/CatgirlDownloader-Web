# Add project specific ProGuard rules here.
# Keep Gson data models
-keep class com.catgirldownloader.android.data.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
