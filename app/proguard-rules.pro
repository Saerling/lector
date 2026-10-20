# Reglas adaptadas de Komikku/Mihon.
# Las extensiones son APK aparte que se cargan en tiempo de ejecución y usan las clases de la app por su nombre:
# no se ofusca nada y se conservan las APIs que pueden llamar.
-dontobfuscate
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable

-keep,allowoptimization class eu.kanade.** { public protected *; }
-keep,allowoptimization class tachiyomi.** { public protected *; }
-keep,allowoptimization class mihon.** { public protected *; }
-keep,allowoptimization class exh.** { public protected *; }
-keep,allowoptimization class kotlin.** { public protected *; }
-keep,allowoptimization class kotlinx.coroutines.** { public protected *; }
-keep,allowoptimization class kotlinx.serialization.** { public protected *; }
-keep,allowoptimization class kotlinx.serialization.json.** { public protected *; }
-keep,allowoptimization class okhttp3.** { public protected *; }
-keep,allowoptimization class okio.** { public protected *; }
-keep,allowoptimization class org.jsoup.** { public protected *; }
-keep,allowoptimization class rx.** { public protected *; }
-keep,allowoptimization class app.cash.quickjs.** { public protected *; }
-keep,allowoptimization class uy.kohesive.injekt.** { public protected *; }
-keep,allowoptimization class androidx.preference.** { public protected *; }
-keep class com.example.lector.ext.** { *; }

-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn com.google.re2j.**
-dontwarn javax.annotation.**
-dontwarn org.jetbrains.annotations.**
