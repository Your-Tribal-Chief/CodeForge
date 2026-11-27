# CodeForge IDE: R8 rules for release builds (isMinifyEnabled = true).
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*,SourceFile,LineNumberTable,Exceptions

# ---- Termux terminal-emulator / terminal-view -------------------------------------------
# libtermux.so binds JNI.createSubprocess/waitFor/close by name; keep the whole packages.
-keep class com.termux.terminal.** { *; }
-keep class com.termux.view.** { *; }
-keepclasseswithmembernames,includedescriptorclasses class * { native <methods>; }

# ---- sora-editor + TextMate (tm4e) -------------------------------------------------------
# tm4e builds grammar, theme and language-configuration models with Gson and reflection.
-keep class io.github.rosemoe.sora.** { *; }
-keep class org.eclipse.tm4e.** { *; }
-keep class org.joni.** { *; }
-keep class org.jcodings.** { *; }
-keep class org.snakeyaml.engine.** { *; }
-dontwarn org.eclipse.jdt.annotation.**
-dontwarn org.joni.**
-dontwarn org.jcodings.**
-dontwarn org.snakeyaml.engine.**
# sora-editor is compiled with Kotlin's old -Xjvm-default stubs; this companion class never exists at runtime.
-dontwarn kotlin.Cloneable$DefaultImpls

# ---- Gson --------------------------------------------------------------------------------
-keep class com.google.gson.** { *; }
-keepclassmembers,allowobfuscation class * { @com.google.gson.annotations.SerializedName <fields>; }
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken
-dontwarn sun.misc.**

# ---- kotlinx.serialization (not used yet; safe if added later) ----------------------------
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keepclassmembers class dev.codeforge.ide.** { *** Companion; }
-keepclasseswithmembers class dev.codeforge.ide.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class dev.codeforge.ide.**$$serializer { *; }
-dontwarn kotlinx.serialization.**

# ---- Apache Commons Compress: optional codecs that are never loaded -----------------------
-keep class org.apache.commons.compress.archivers.tar.** { *; }
-keep class org.apache.commons.compress.compressors.gzip.** { *; }
-dontwarn org.apache.commons.compress.**
-dontwarn org.tukaani.xz.**
-dontwarn com.github.luben.zstd.**
-dontwarn org.brotli.dec.**
-dontwarn org.objectweb.asm.**

# ---- App ---------------------------------------------------------------------------------
# Small; kept whole so stack traces stay readable and no reflective path is ever stripped.
-keep class dev.codeforge.ide.** { *; }
# Compose and AndroidX ship their own consumer rules; nothing app-specific is needed.
