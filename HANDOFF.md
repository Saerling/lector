# HANDOFF — Lector (app Android estilo Komikku) · Paso 3: Explorar + Extensiones

> Este archivo es el "pasaporte" del proyecto. Se lo puedes pasar a cualquier IA junto con el zip.
> Al final hay un bloque **"Para la IA que continúe"** con las reglas para devolverlo (a Claude u otra).
> Idioma de trabajo: **español**. Fecha de este traspaso: 2026-10-18.

---

## 1. Qué es el proyecto
App Android **"Lector"** (paquete `com.example.lector`), Kotlin + Jetpack Compose + Material 3.
Es un lector de cómics **local** (.cbz y carpetas de imágenes) con diseño inspirado en **Komikku**
(fork de Mihon/Tachiyomi): fondo negro, barra inferior flotante en píldora, acento configurable.

### Pasos hechos antes (resumen de lo que ya existía)
- **Paso 1 (`lector-codigo-completo.zip`)**: toda la app base.
  - `Data.kt` → clase `Almacen` (SharedPreferences "lector"): biblioteca, portadas, categorías (con "ocultas"),
    ajustes del lector, paleta de color, AniList (seguimiento), copias de seguridad, importar .cbz / carpeta.
  - `BibliotecaScreen.kt`, `HistorialScreen` (Historial.kt), `LectorScreen.kt`, `Cbz.kt`, `Vistas.kt`, `Anilist.kt`,
    `AjustesLector.kt`, `Theme.kt`, `MainActivity.kt`.
- **Paso 2 (`lector-paso2-ajustes-detalle.zip`)**: rediseño en `ui/`: `LectorApp.kt` (función `Inicio`, pestañas + capas
  animadas), `Mas.kt` (pestaña Más + `enum Ruta`), `AjustesPantallas.kt`, `Detalle.kt`, `Components.kt`, `Filas.kt`
  (`PaginaAjustes`, `FilaAjuste`, `FilaSwitch`, `ChipSel`, `TituloSeccion`...), `HomeScreen.kt`, `Pantallas.kt`.
- **Un APK ya compilado** (`lector-apk.zip` → `app-debug.apk`) salió del paso 2.
- Los zips **solo traen `app/src/main/...`**: NO incluyen `build.gradle`, `settings.gradle`, etc. El proyecto Gradle
  completo vive en el entorno donde se compiló el APK. Nada de este paso necesita dependencias nuevas.

### Convenciones del código (respétalas)
- Nombres y textos de UI en **español**. Estilo: `LText`, `LectorColors`, `LectorType`, `PillButton`, `pressable`.
- Navegación: no usa Navigation-Compose. `Inicio` (LectorApp.kt) mantiene `ruta: Ruta?` y `detalleId`, y pinta la
  capa encima con `AnimatedContent`. Para una pantalla nueva: añadir valor a `enum Ruta` (Mas.kt), `padre()` si
  cuelga de otra, y rama en el `when (Ruta.valueOf(clave))` de LectorApp.kt.
- **Solo iconos del set "core"** (`Icons.Rounded.Add/Search/Star/Settings/Delete/...`). No se sabe si
  `material-icons-extended` está en el proyecto; no usar `Download`, `Visibility`, `PushPin`, `Explore`...
- Sin librerías de imágenes: `ImagenRemota` (ui/Explorar.kt) descarga con `HttpURLConnection` + `LruCache`.
- Sin librerías de red/JSON nuevas: `HttpURLConnection` + `org.json`.

---

## 2. Qué se hizo en el PASO 3 (este traspaso)
Objetivo del usuario: la **funcionalidad de extensiones tal como en Komikku** (capturas en `referencias/`):
pestaña **Explorar** con subpestañas *Fuentes · Novedades · Extensiones · Migrar*, lista de extensiones instaladas con
engranaje, y **"Listados de extensiones"** con diálogo *"Añadir listado de extensiones"*.

### Archivos NUEVOS
| Archivo | Contenido |
|---|---|
| `ext/ExtModelos.kt` | `ExtDisponible`, `ExtInstalada`, `FuenteInfo`, `FuenteUI`, `RepoExt` (sigla "@KEI"), `Progreso`, idiomas/banderas |
| `ext/ExtRepos.kt` | URL del listado (acepta `index.min.json`, `index.json`, `index.pb`, o base), descarga, lector **JSON** y lector **protobuf (gzip)** |
| `ext/ExtManager.kt` | Estado Compose + persistencia: listados, disponibles (con caché en disco), instaladas (PackageManager, característica `tachiyomi.extension`), **confianza por firma SHA-256**, instalar (PackageInstaller + receptor), desinstalar (`ACTION_DELETE`), filtros (+18, idiomas), fuentes ancladas/recientes |
| `ext/Motor.kt` | **Costura** `MotorFuentes` + `Motor.actual` (hoy `null`) |
| `ui/Explorar.kt` | `ExplorarScreen` (4 subpestañas), `IconoExt`, `Imagenes`/`ImagenRemota`, diálogos de confianza e idiomas |
| `ui/ExtPantallas.kt` | `ListadosExtScreen` (como la captura 3), `ExtDetalleScreen` (engranaje), `FuenteDetalleScreen` |

### Archivos MODIFICADOS
- `ui/Mas.kt`: `enum Ruta` + `ListadosExt, ExtDetalle, FuenteDetalle`.
- `ui/Components.kt`: pestaña "Buscar" → **"Explorar"** (la búsqueda por título sigue en Biblioteca).
- `ui/LectorApp.kt`: crea `ExtManager`, enruta `LectorTab.Search -> ExplorarScreen` y las 3 rutas nuevas.
- `AndroidManifest.xml`: permisos `REQUEST_INSTALL_PACKAGES`, `REQUEST_DELETE_PACKAGES`, `QUERY_ALL_PACKAGES`.
- `ui/Pantallas.kt` (`SearchScreen`) queda sin usar (se puede borrar).

### Qué funciona (en teoría) y qué NO
✅ Listados: añadir (valida descargando el índice), copiar URL, abrir web, ocultar/mostrar, quitar. Por defecto: Keiyoushi + Suwayomi.
✅ Extensiones: instaladas / actualizaciones pendientes / disponibles por idioma, buscar, filtro +18, idiomas, instalar,
   actualizar, desinstalar, "Confiar", detalle con engranaje.
✅ Fuentes: lista de las fuentes de extensiones instaladas **y de confianza**, ancladas, usadas recientemente, buscar.
❌ **NO se ejecuta aún el código de las extensiones.** Por eso: *Novedades* y *navegar una fuente* están "dormidos"
   (se activan al asignar `Motor.actual`), y *Migrar* es solo un aviso. Las fuentes se listan a partir del **índice del
   listado**, no cargando la extensión.
❌ Leer un capítulo online / ficha de manga / descargas / biblioteca vinculada a fuentes: no existe todavía.

### ⚠️ Estado de verificación (léelo antes de tocar nada)
1. **El código de este paso NO se ha compilado ni probado** (el entorno donde se escribió no tenía Gradle ni red).
   Solo se comprobó a mano el equilibrio de llaves. **Primer trabajo: compilar y arreglar errores.**
2. **Esquema de `index.pb` (Keiyoushi) INFERIDO**, no verificado: campo 101 repetido = extensión
   {1 nombre, 2 pkg, 3 {1 apkUrl, 2 iconoUrl}, 5 versionCode, 6 versionName, 8 fuentes{1 id, 2 nombre, 3 lang, 4 baseUrl}}.
   Vino de una descripción de terceros. `nsfw` no se lee del .pb (queda `false`). Verificar contra el `.proto`/código real
   (Komikku y Yurei ya lo parsean: ver su código) y corregir `ExtRepos.leerProtobuf`.
3. En 2026 **Keiyoushi publica `index.pb` (gzip+protobuf)**; su `index.min.json` quedó como "stub" (vacío). Si en el
   dispositivo el listado Keiyoushi sale vacío o con error, es esto.
4. Las extensiones actuales usan **extensions-lib 1.6** ("KeiSource", versión `1.6.x` como en las capturas), y Komikku v1.14
   añadió soporte. Mi memoria de la API `source-api` es de 1.4/1.5; **no se debe reescribir a mano**.
5. Instalación: `PackageInstaller` con confirmación del sistema. Requiere que el usuario active
   *"Instalar apps desconocidas"* para Lector (la app abre esos ajustes sola la primera vez).
6. Confianza: como Mihon, hay que pulsar **Confiar** tras instalar (se guarda `pkg:sha256(firma)`).

---

## 3. PRÓXIMOS PASOS (en este orden)
1. **Compilar** y corregir errores del paso 3. Probar en móvil: añadir listado → instalar una extensión → Confiar →
   ver sus fuentes en la pestaña Fuentes. Confirmar que `index.pb` de Keiyoushi se lee.
2. **Motor de fuentes (lo grande).** Camino recomendado: **vendorizar (copiar) de Komikku/Mihon** (licencia Apache-2.0)
   en lugar de reescribir:
   - módulo `source-api` (interfaces `Source`, `CatalogueSource`, `HttpSource`, `ParsedHttpSource`, modelos `SManga`,
     `SChapter`, `Page`, `Filter`…, y las del lib **1.6** / KeiSource);
   - `NetworkHelper`, `OkHttpExtensions`, interceptores (`RateLimit`, cloudflare);
   - el `ExtensionLoader` de la app (carga el APK con `ChildFirstPathClassLoader`, lee metadatos
     `tachiyomi.extension.class`, `.factory`, `.nsfw`, comprueba rango de versión de lib);
   - Injekt (usar la misma versión que `gradle/libs.versions.toml` de Komikku), OkHttp (misma versión),
     kotlinx-serialization, Jsoup, RxJava 1 si la lib lo exige, `androidx.preference`.
   Repo: https://github.com/komikku-app/komikku (el APK de referencia v1.14.1 iba con este traspaso).
   Luego implementar `MotorFuentes` y asignar `Motor.actual = ...` al arrancar (p. ej. en `ExtManager.init` o `MainActivity`).
   Solo cargar extensiones con `confiable == true`.
3. Ficha del manga + lista de capítulos + **leer online**, o más sencillo y encajando con la app:
   **descargar capítulo → empaquetar .cbz → `Almacen.importar(...)`** para usar el `LectorScreen` actual sin cambios.
4. *Migrar*, ajustes por fuente (`ConfigurableSource` → `androidx.preference`), "Novedades" con caché, etc.

---

## 4. Cómo se compila / prueba
Proyecto Gradle (no incluido). Copiar `app/src/main/**` sobre el proyecto existente (los zips se pisan en este orden:
`codigo-completo` → `paso2` → `paso3`). El paquete completo de este traspaso (`lector-paquete-para-otra-IA.zip`)
ya trae todo fusionado en `app/src/main/`.

---

## 5. Para la IA que continúe  ← COPIAR/PEGAR ESTO COMO INSTRUCCIÓN
```
Vas a continuar el proyecto "Lector" (Android, Kotlin, Compose). Lee HANDOFF.md completo antes de nada.
Reglas:
1. Responde en español y respeta las "Convenciones del código" (solo iconos core, textos en español,
   sin librerías nuevas salvo las que haga falta para el motor y que debes listar).
2. No finjas haber probado nada: distingue "compilado y probado" de "escrito sin probar".
3. Haz primero lo de "PRÓXIMOS PASOS" en orden. No rehagas lo que ya está salvo que esté roto.
4. AL TERMINAR (o al quedarte sin espacio) entrega SIEMPRE dos cosas, para que el usuario pueda
   volver con Claude o pasárselo a otra IA:
   a) un zip con TODO el código fusionado (app/src/main/** + HANDOFF.md + referencias/);
   b) el HANDOFF.md ACTUALIZADO con el mismo formato: añade una sección "PASO 4 (o siguiente)" con
      archivos nuevos/modificados, qué funciona, qué no, qué quedó sin probar y los próximos pasos,
      y conserva intacta la sección 5 para que la siguiente IA haga lo mismo.
5. Si cambias dependencias o manifest, anótalo explícitamente en el HANDOFF.
```

## 6. Contenido de `referencias/`
Capturas de Komikku v1.14.1 que definen el diseño objetivo: `…005824` (Extensiones instaladas), `…005829` (Novedades),
`…005833` (Añadir listado de extensiones), `…005846` (Fuentes con "Usado recientemente" y "Ancladas").
