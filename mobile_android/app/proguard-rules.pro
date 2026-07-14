# The printer SDK contains obfuscated reflective entry points.
-keep class com.dothantech.** { *; }
-dontwarn com.dothantech.**

# ML Kit's bundled barcode scanner builds an internal component graph at
# runtime. R8 optimization of these classes can leave the scanner singleton
# without its provider on Android 12.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_barcode_bundled.** { *; }

# Gson persists these models by field name. Keep the small application package
# stable while still shrinking Compose, CameraX, WorkManager and other libraries.
-keep class studio.inventory.android.** { *; }
-keepattributes Signature,*Annotation*
