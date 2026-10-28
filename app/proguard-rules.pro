# JNA und die UniFFI-Bindings werden per Reflexion bzw. über native Symbole angesprochen
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-keep class de.crunchymate.core.ffi.** { *; }
-dontwarn java.awt.**
