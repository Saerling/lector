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
  # Desplazar hasta abajo varias veces (carga infinita) y comprobar que no sale ningún error.
  for k in 1 2 3 4 5 6; do adb shell input swipe 540 1800 540 500 250; sleep 3; done
  sleep 6
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml "ui2-$pkg.xml" >/dev/null 2>&1
  {
    if grep -qi "coroutine\|left the composition" "ui2-$pkg.xml"; then echo "ERROR TRAS DESPLAZAR: $(grep -oi 'text="[^"]*coroutine[^"]*"' "ui2-$pkg.xml" | head -1)"; else echo "Tras desplazar: sin errores"; fi
    echo "Tras desplazar se ven: $(grep -o 'text="[^"]*"' "ui2-$pkg.xml" 2>/dev/null | sed 's/text=//' | grep -v '""' | tail -6 | tr '\n' ' ')"
    adb shell pidof com.example.lector >/dev/null || echo "LA APP SE CERRÓ AL DESPLAZAR"
  } >> fallos.txt
}
# Listado de extensiones con "index_v2": null en su repo.json (antes fallaba con «no protocol: null»).
adb shell am force-stop com.example.lector; adb logcat -c
adb shell am start -n com.example.lector/.MainActivity --es destino "probar-repo:https://raw.githubusercontent.com/Nyora-Manga/nyora-mihon/main/repo.json"
sleep 25
{ echo "===== listado Nyora ====="; adb logcat -d | grep "LectorPrueba" | tail -5; } >> fallos.txt
probar "https://github.com/keiyoushi/extensions/releases/download/d0c0864/tachiyomi-es.cerberusseries-v1.6.1.apk" eu.kanade.tachiyomi.extension.es.cerberusseries
probar "https://github.com/keiyoushi/extensions/releases/download/bdcf84f/tachiyomi-es.anzmanga-v1.6.0.apk" eu.kanade.tachiyomi.extension.es.anzmanga
probar "https://github.com/keiyoushi/extensions/releases/download/4c8cda7/tachiyomi-all.webtoons-v1.6.2.apk" eu.kanade.tachiyomi.extension.all.webtoons
probar "https://github.com/keiyoushi/extensions/releases/download/bdcf84f/tachiyomi-all.myreadingmanga-v1.6.0.apk" eu.kanade.tachiyomi.extension.all.myreadingmanga
probar "https://github.com/keiyoushi/extensions/releases/download/4217666-0/tachiyomi-all.imhentai-v1.6.25.apk" eu.kanade.tachiyomi.extension.all.imhentai
# ---- «Fuente no disponible»: un manga de una fuente que no existe; debe buscar su título en las demás ----
adb shell am force-stop com.example.lector; adb logcat -c
adb shell am start -n com.example.lector/.MainActivity --es destino "probar-huerfano:Gacha infinita"
sleep 75
adb exec-out screencap -p > "captura-$ETIQUETA-huerfano.png"
adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml "ui-huerfano.xml" >/dev/null 2>&1
{
  echo "===== Fuente no disponible ====="
  echo "Textos: $(grep -o 'text="[^"]*"' ui-huerfano.xml 2>/dev/null | sed 's/text=//' | grep -v '""' | head -20 | tr '\n' ' ')"
  adb logcat -d | grep -A30 "FATAL EXCEPTION" | head -30
  adb shell pidof com.example.lector >/dev/null && echo "La app sigue abierta" || echo "LA APP NO ESTÁ ABIERTA"
} >> fallos.txt

# ---- Copias de seguridad: restaurar una copia grande de Mihon (600 mangas, 48.000 capítulos) y volver a exportarla ----
probar_copias() {
  adb shell am force-stop com.example.lector
  for f in grande enorme; do
    adb push pruebas/tachibk/$f.tachibk /data/local/tmp/$f.tachibk
    adb shell chmod 644 /data/local/tmp/$f.tachibk
    adb shell run-as com.example.lector cp /data/local/tmp/$f.tachibk files/$f.tachibk
  done
  for f in grande enorme; do
    adb logcat -c
    adb shell am start -n com.example.lector/.MainActivity --es destino "probar-restaurar:/data/user/0/com.example.lector/files/$f.tachibk"
    sleep 3
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml ui-copia-$f.xml >/dev/null 2>&1
    # Espera a que termine (como mucho 4 minutos).
    for i in $(seq 1 96); do adb logcat -d | grep -q "restaurar ms=\|LectorCopia" && break; sleep 5; done
    adb logcat -d > "logcat-$ETIQUETA-copia-$f.txt"
    {
      echo "===== Restaurar $f ($ETIQUETA) ====="
      grep -o "restaurar ms=.*" "logcat-$ETIQUETA-copia-$f.txt" | cut -c1-600 | tail -1
      grep -A8 "LectorCopia" "logcat-$ETIQUETA-copia-$f.txt" | head -10
      grep -B2 -A25 "FATAL EXCEPTION\|OutOfMemoryError" "logcat-$ETIQUETA-copia-$f.txt" | head -40
      adb shell pidof com.example.lector >/dev/null && echo "La app sigue abierta" || echo "LA APP NO ESTÁ ABIERTA"
      echo "Durante: $(grep -o 'text="[^"]*"' ui-copia-$f.xml 2>/dev/null | sed 's/text=//' | grep -v '""' | head -6 | tr '\n' ' ')"
      echo "Memoria tras restaurar:"; adb shell dumpsys meminfo com.example.lector | grep -E "Java Heap|TOTAL PSS|TOTAL:" | head -3
    } >> fallos.txt
  done
  # Volver a abrir la app con la biblioteca enorme y crear una copia de ella.
  adb shell am force-stop com.example.lector
  adb logcat -c
  adb shell am start -n com.example.lector/.MainActivity --es destino "probar-copia"
  for i in $(seq 1 36); do adb logcat -d | grep -q "crear ms=\|LectorCopia" && break; sleep 5; done
  sleep 3
  adb logcat -d > "logcat-$ETIQUETA-crear.txt"
  {
    echo "===== Reabrir y crear copia ($ETIQUETA) ====="
    grep -o "crear ms=.*" "logcat-$ETIQUETA-crear.txt" | cut -c1-400 | tail -1
    grep -A8 "LectorCopia" "logcat-$ETIQUETA-crear.txt" | head -10
    grep -B2 -A25 "FATAL EXCEPTION\|OutOfMemoryError" "logcat-$ETIQUETA-crear.txt" | head -40
    adb shell pidof com.example.lector >/dev/null && echo "La app sigue abierta" || echo "LA APP NO ESTÁ ABIERTA"
    echo "Memoria:"; adb shell dumpsys meminfo com.example.lector | grep -E "Java Heap|TOTAL PSS|TOTAL:" | head -3
  } >> fallos.txt
}
probar_copias

# ---- Actualización de la app: la última versión de GitHub se lee, se descarga y se instala ENCIMA (sin desinstalar) ----
adb shell am force-stop com.example.lector; adb logcat -c
adb shell am start -n com.example.lector/.MainActivity --es destino "probar-version"
for i in $(seq 1 24); do adb logcat -d | grep -q "LectorPrueba: version" && break; sleep 5; done
{
  echo "===== Actualización de la app ====="
  adb logcat -d | grep "LectorPrueba: version" | tail -1
  url=$(curl -s https://api.github.com/repos/Saerling/lector/releases/latest | python3 -c "import json,sys;d=json.load(sys.stdin);print(next((a['browser_download_url'] for a in d.get('assets',[]) if a['name'].endswith('.apk')),''))" 2>/dev/null)
  if [ -n "$url" ]; then
    curl -sL -o publicada.apk "$url"
    # -d: si la publicada tuviera un número menor que esta compilación de prueba. Lo que se comprueba es la firma.
    echo "Instalar la publicada encima de la instalada: $(adb install -r -d publicada.apk 2>&1 | tail -1)"
    adb shell dumpsys package com.example.lector | grep -m2 -E "versionCode|versionName"
  else
    echo "Aún no hay ninguna versión publicada"
  fi
} >> fallos.txt
cat fallos.txt
exit 0
