#!/bin/bash
# Prueba en el emulador: instala la app y unas extensiones, abre el catálogo de cada una y guarda los fallos.
set -x
APK="${1:-app/build/outputs/apk/debug/app-debug.apk}"
ETIQUETA="${2:-debug}"
adb uninstall com.example.lector || true
adb install -r "$APK"
adb shell pm grant com.example.lector android.permission.POST_NOTIFICATIONS || true
echo "########## APK $ETIQUETA ##########" >> fallos.txt
probar() {
  local url="$1" pkg="$2"
  curl -sL -o ext.apk "$url" && adb install -r ext.apk
  adb shell am force-stop com.example.lector
  adb logcat -c
  adb shell am start -n com.example.lector/.MainActivity --es destino "probar:$pkg"
  sleep 80
  adb exec-out screencap -p > "captura-$ETIQUETA-$pkg.png"
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml "ui-$pkg.xml" >/dev/null 2>&1
  adb logcat -d > "logcat-$ETIQUETA-$pkg.txt"
  {
    echo "===== $pkg ====="
    grep "LectorPrueba" "logcat-$ETIQUETA-$pkg.txt" | tail -3
    grep -A45 "FATAL EXCEPTION" "logcat-$ETIQUETA-$pkg.txt" | head -60
    grep -E "Fatal signal|Abort message|JNI DETECTED|java_vm_ext|art  *: |DEBUG *: (#0|  #0|backtrace|Cause|signal)| F [a-zA-Z]+ *:" "logcat-$ETIQUETA-$pkg.txt" | grep -v "MetricsFeature" | head -40
    adb shell pidof com.example.lector >/dev/null && echo "La app sigue abierta" || echo "LA APP NO ESTÁ ABIERTA"
    echo "Textos en pantalla: $(grep -o 'text="[^"]*"' "ui-$pkg.xml" 2>/dev/null | sed 's/text=//' | grep -v '""' | head -25 | tr '\n' ' ')"
  } >> fallos.txt
}
probar "https://github.com/keiyoushi/extensions/releases/download/d0c0864/tachiyomi-es.cerberusseries-v1.6.1.apk" eu.kanade.tachiyomi.extension.es.cerberusseries
probar "https://github.com/keiyoushi/extensions/releases/download/bdcf84f/tachiyomi-es.anzmanga-v1.6.0.apk" eu.kanade.tachiyomi.extension.es.anzmanga
probar "https://github.com/keiyoushi/extensions/releases/download/4c8cda7/tachiyomi-all.webtoons-v1.6.2.apk" eu.kanade.tachiyomi.extension.all.webtoons
probar "https://github.com/keiyoushi/extensions/releases/download/bdcf84f/tachiyomi-all.myreadingmanga-v1.6.0.apk" eu.kanade.tachiyomi.extension.all.myreadingmanga
probar "https://github.com/keiyoushi/extensions/releases/download/4217666-0/tachiyomi-all.imhentai-v1.6.25.apk" eu.kanade.tachiyomi.extension.all.imhentai
# ---- Copias de seguridad: restaurar una copia grande de Mihon (600 mangas, 48.000 capítulos) y volver a exportarla ----
probar_copias() {
  local dir=/sdcard/Android/data/com.example.lector/files
  adb shell am force-stop com.example.lector
  adb shell mkdir -p $dir
  adb push pruebas/tachibk/grande.tachibk $dir/grande.tachibk
  adb logcat -c
  adb shell am start -n com.example.lector/.MainActivity --es destino "probar-restaurar:$dir/grande.tachibk"
  sleep 4
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml ui-copia.xml >/dev/null 2>&1
  sleep 40
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml ui-copia-fin.xml >/dev/null 2>&1
  adb shell am start -n com.example.lector/.MainActivity --es destino "probar-copia"
  sleep 20
  adb logcat -d > "logcat-$ETIQUETA-copias.txt"
  {
    echo "===== Copias ($ETIQUETA) ====="
    grep "LectorPrueba" "logcat-$ETIQUETA-copias.txt" | tail -4
    grep -A30 "FATAL EXCEPTION" "logcat-$ETIQUETA-copias.txt" | head -40
    adb shell pidof com.example.lector >/dev/null && echo "La app sigue abierta" || echo "LA APP NO ESTÁ ABIERTA"
    echo "Durante: $(grep -o 'text="[^"]*"' ui-copia.xml 2>/dev/null | sed 's/text=//' | grep -v '""' | head -8 | tr '\n' ' ')"
    echo "Al final: $(grep -o 'text="[^"]*"' ui-copia-fin.xml 2>/dev/null | sed 's/text=//' | grep -v '""' | head -8 | tr '\n' ' ')"
  } >> fallos.txt
}
probar_copias
cat fallos.txt
exit 0
