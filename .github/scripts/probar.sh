#!/bin/bash
# Prueba en el emulador: instala la app y unas extensiones, abre el catálogo de cada una y guarda los fallos.
set -x
adb install -r app/build/outputs/apk/debug/app-debug.apk
: > fallos.txt
probar() {
  local url="$1" pkg="$2"
  curl -sL -o ext.apk "$url" && adb install -r ext.apk
  adb shell am force-stop com.example.lector
  adb logcat -c
  adb shell am start -n com.example.lector/.MainActivity --es destino "probar:$pkg"
  sleep 75
  adb exec-out screencap -p > "captura-$pkg.png"
  adb logcat -d > "logcat-$pkg.txt"
  {
    echo "===== $pkg ====="
    grep "LectorPrueba" "logcat-$pkg.txt" | tail -3
    grep -A45 "FATAL EXCEPTION" "logcat-$pkg.txt" | head -60
    grep -A25 "Fatal signal" "logcat-$pkg.txt" | head -30
    adb shell pidof com.example.lector >/dev/null && echo "La app sigue abierta" || echo "LA APP NO ESTÁ ABIERTA"
  } >> fallos.txt
}
probar "https://github.com/keiyoushi/extensions/releases/download/d0c0864/tachiyomi-es.cerberusseries-v1.6.1.apk" eu.kanade.tachiyomi.extension.es.cerberusseries
probar "https://github.com/keiyoushi/extensions/releases/download/bdcf84f/tachiyomi-es.anzmanga-v1.6.0.apk" eu.kanade.tachiyomi.extension.es.anzmanga
cat fallos.txt
exit 0
