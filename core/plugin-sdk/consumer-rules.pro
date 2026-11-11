# Keep the plugin contract stable: third-party APKs are compiled against these
# exact names and are loaded reflectively, so R8 must not rename them.
-keep interface com.openschedule.plugin.sdk.** { *; }
-keep class com.openschedule.plugin.sdk.** { *; }
