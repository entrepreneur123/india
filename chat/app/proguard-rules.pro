# Prototype: no reflection-based libraries yet.

# Coil 3 finds its network fetcher (coil-network-okhttp, live flavor) through java.util.ServiceLoader
# (META-INF/services/coil3.util.FetcherServiceLoaderTarget). Keep the class that file names, so https
# images (signed Supabase URLs) keep loading after R8.
-keep class * implements coil3.util.FetcherServiceLoaderTarget { <init>(); *; }
-keepnames class coil3.util.FetcherServiceLoaderTarget
