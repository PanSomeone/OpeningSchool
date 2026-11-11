-keepattributes *Annotation*, InnerClasses

# kotlinx.serialization generated serializers are looked up reflectively.
-keep @kotlinx.serialization.Serializable class * { *; }
-keep class **$$serializer { *; }
-keepclassmembers class **$$serializer { *; }
-keep class kotlinx.serialization.** { *; }

# Jsoup resolves part of its selector/evaluator implementation dynamically.
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**

# Rhino JS runtime for in-app plugin modules
-keep class org.mozilla.javascript.** { *; }
-dontwarn org.mozilla.javascript.**
-dontwarn java.beans.**

# Native plugin SDK: third-party APKs compile against these exact names and are
# loaded with DexClassLoader from their own dex files.
-keep interface com.openschedule.plugin.sdk.** { *; }
-keep class com.openschedule.plugin.sdk.** { *; }

# Third-party native plugins are written in Kotlin and reference the public Kotlin
# stdlib from their own dex. Without this, R8 inlines/renames kotlin.* in release
# builds and every plugin fails with NoClassDefFoundError
# (kotlin/jvm/internal/Intrinsics).
-keep class kotlin.** { *; }
-dontwarn kotlin.**

# Host-side loader entry points used reflectively by plugins are kept above;
# plugin classes themselves live in their own APK and are never minified here.
