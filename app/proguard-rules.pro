# Reglas adaptadas de Komikku/Mihon.
# Las extensiones son APK aparte que se cargan en tiempo de ejecución y usan las clases de la app por su nombre:
# no se ofusca nada y se conservan las APIs que pueden llamar.
# OJO: -keep SIN allowoptimization. Con allowoptimization R8 marcaba como `final` métodos que la app no sobrescribe
# (p. ej. HttpSource.fetchRelatedMangaList) y las extensiones que sí los sobrescriben fallaban al cargar
# ("overrides final method").
-dontobfuscate
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable

-keep class eu.kanade.** { *; }
-keep class tachiyomi.** { *; }
-keep class mihon.** { *; }
-keep class exh.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keep class kotlinx.serialization.** { *; }
-keep class kotlinx.serialization.json.** { *; }
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-keep class org.jsoup.** { *; }
-keep class rx.** { *; }
-keep class app.cash.quickjs.** { *; }
-keep class uy.kohesive.injekt.** { *; }
-keep class androidx.preference.** { *; }
-keep class com.example.lector.ext.** { *; }

-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn com.google.re2j.**
-dontwarn javax.annotation.**
-dontwarn org.jetbrains.annotations.**
-dontwarn org.jspecify.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn edu.umd.cs.findbugs.annotations.**
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-keep interface eu.kanade.** { *; }
-keep interface okhttp3.** { *; }

# Código nativo (JNI): las librerías nativas buscan clases y métodos POR NOMBRE. Si R8 los quita o los cambia, la
# app se cae con SIGABRT al descomprimir una respuesta (zstd / brotli de OkHttp). Se conservan enteros.
-keepclasseswithmembernames,includedescriptorclasses class * { native <methods>; }
-keep class com.squareup.zstd.** { *; }
-keep class org.brotli.** { *; }
-keep class com.github.luben.zstd.** { *; }
-keep class app.cash.zipline.** { *; }
-dontwarn com.squareup.zstd.**
-dontwarn org.brotli.**

# Librerías que también usan las extensiones (TachiyomiSY/Komikku las incluyen).
-keep class nl.adaptivity.xmlutil.** { *; }
-dontwarn nl.adaptivity.xmlutil.**
-dontwarn javax.xml.stream.**
