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
