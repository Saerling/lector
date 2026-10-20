#!/bin/bash
# Prueba en el emulador: instala la app y unas extensiones, abre el catálogo de cada una y guarda los fallos.
set -x
APK="${1:-app/build/outputs/apk/debug/app-debug.apk}"
ETIQUETA="${2:-debug}"
adb uninstall com.example.lector || true
adb install -r "$APK"
echo "########## APK $ETIQUETA ##########" >> fallos.txt
probar() {
  local url="$1" pkg="$2"
  curl -sL -o ext.apk "$url" && adb install -r ext.apk
  adb shell am force-stop com.example.lector
  adb logcat -c
  adb shell am start -n com.example.lector/.MainActivity --es destino "probar:$pkg"
  sleep 75
  adb exec-out screencap -p > "captura-$ETIQUETA-$pkg.png"
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml "ui-$pkg.xml" >/dev/null 2>&1
  adb logcat -d > "logcat-$ETIQUETA-$pkg.txt"
  {
    echo "===== $pkg ====="
    grep "LectorPrueba" "logcat-$ETIQUETA-$pkg.txt" | tail -3
    grep -A45 "FATAL EXCEPTION" "logcat-$ETIQUETA-$pkg.txt" | head -60
    grep -A25 "Fatal signal" "logcat-$ETIQUETA-$pkg.txt" | head -30
    adb shell pidof com.example.lector >/dev/null && echo "La app sigue abierta" || echo "LA APP NO ESTÁ ABIERTA"
    echo "Textos en pantalla: $(grep -o 'text="[^"]*"' "ui-$pkg.xml" 2>/dev/null | sed 's/text=//' | grep -v '""' | head -25 | tr '\n' ' ')"
  } >> fallos.txt
}
probar "https://github.com/keiyoushi/extensions/releases/download/d0c0864/tachiyomi-es.cerberusseries-v1.6.1.apk" eu.kanade.tachiyomi.extension.es.cerberusseries
probar "https://github.com/keiyoushi/extensions/releases/download/bdcf84f/tachiyomi-es.anzmanga-v1.6.0.apk" eu.kanade.tachiyomi.extension.es.anzmanga
cat fallos.txt
exit 0
