# Release builds log nothing below WARN: verbose/debug/info calls are removed entirely, so no token, phone number or
# message text can reach Logcat from our code or from libraries that log through android.util.Log.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Coil 3 finds its network fetcher (coil-network-okhttp, live flavor) through java.util.ServiceLoader
# (META-INF/services/coil3.util.FetcherServiceLoaderTarget). Keep the class that file names, so https
# images (signed Supabase URLs) keep loading after R8.
-keep class * implements coil3.util.FetcherServiceLoaderTarget { <init>(); *; }
-keepnames class coil3.util.FetcherServiceLoaderTarget
