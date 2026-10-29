# HANDOFF — Lector (app Android estilo Komikku) · Pasos 3 a 15

> Este archivo es el "pasaporte" del proyecto. Se lo puedes pasar a cualquier IA junto con el zip.
> Al final hay un bloque **"Para la IA que continúe"** con las reglas para devolverlo (a Claude u otra).
> Idioma de trabajo: **español**. Fecha de este traspaso: 2026-10-20 (PASO 15, ver 2o).

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

## 2b. PASO 4 (compila y corre en el móvil; fuente AnzManga cargó y pidió datos) — Motor de fuentes (escrito el 2026-10-18, **SIN compilar ni probar**)
Estado de partida: el paso 3 ya compila y corre en el móvil (se arregló `LocalContext.current` dentro de `remember`).
Las pantallas decían "el motor de fuentes se conecta en el siguiente paso". Este paso lo conecta.

### Qué se hizo
Se **vendorizó** (copió de Komikku, Apache-2.0) el `source-api` y la red, con los mismos paquetes `eu.kanade.tachiyomi.*`
(las extensiones los referencian por nombre; **no cambies paquetes ni firmas**):
| Ruta (`app/src/main/java/...`) | Origen / cambios |
|---|---|
| `eu/kanade/tachiyomi/source/{Source,CatalogueSource,ConfigurableSource,SourceFactory,UnmeteredSource,PagePreviewSource,PreferenceScreen}.kt` | Komikku `source-api`. `logcat` → `android.util.Log`; `PreferenceScreen` pasa de expect/actual a `typealias` de `androidx.preference.PreferenceScreen`; quitadas anotaciones `@Serializable/@Transient` (no exigir el plugin de serialization) |
| `eu/kanade/tachiyomi/source/model/*` | modelos `SManga, SChapter, Page, Filter, FilterList, MangasPage…` (quitado `MetadataMangasPage`, de SY) |
| `eu/kanade/tachiyomi/source/online/{HttpSource,ParsedHttpSource}.kt` | `HttpSource` sin delegados EXH/SY: `network = Injekt.get<NetworkHelper>()` |
| `eu/kanade/tachiyomi/util/{JsoupExtensions,system/ChildFirstPathClassLoader}.kt` | tal cual |
| `eu/kanade/tachiyomi/network/*` | `AndroidCookieJar, HttpException, OkHttpExtensions, ProgressListener, ProgressResponseBody, Requests, interceptor/{UserAgent,UncaughtException,RateLimit,SpecificHostRateLimit}`. En `OkHttpExtensions`: atomics de Kotlin → `java.util.concurrent.atomic`; `context(Json)` → parámetro normal `json` **primero** (misma firma binaria `decodeFromJsonResponse(Json, DeserializationStrategy, Response)`) |
| `eu/kanade/tachiyomi/network/NetworkHelper.kt` | **Reescrito, reducido** (sin preferencias, sin DoH, sin logging; incluye CloudflareInterceptor) |
| `eu/kanade/tachiyomi/network/JavaScriptEngine.kt` | **Stub**: existe para que carguen las extensiones que lo nombran; evaluar JS lanza `UnsupportedOperationException` (falta QuickJS) |
| `tachiyomi/core/common/util/{QuerySanitizer,lang/RxCoroutineBridge}.kt`, `mihon/core/common/extensions/JsonObject.kt` | tal cual (shims que pide el source-api) |
| `com/example/lector/ext/MotorExt.kt` | **NUEVO.** Implementa `MotorFuentes`: registra en Injekt `Application`, `NetworkHelper`, `JavaScriptEngine`, `Json`; carga el APK de la extensión con `ChildFirstPathClassLoader` (lee `tachiyomi.extension.class`, soporta `SourceFactory`), cachea por `pkg:versionCode:firma`; **solo carga extensiones con `confiable == true`**; busca la fuente por `id`, si no por nombre+idioma; `populares/recientes/buscar` en `Dispatchers.IO`; `cabeceras()` = `HttpSource.headers` (para portadas) |
| `com/example/lector/ext/ExtManager.kt` | en `init`: `Motor.actual = MotorExt(app, this)` (dentro de `runCatching`) |

### ⚠️ DEPENDENCIAS NUEVAS (YA aplicadas en `app/build.gradle.kts` y `settings.gradle.kts` de este zip; el `build.gradle.kts` raíz y `gradle/libs.versions.toml` no existen/no se tocaron). Además se añadió `-Xskip-metadata-version-check` y `packaging excludes`
```kotlin
// settings.gradle(.kts) → dependencyResolutionManagement { repositories { ... maven("https://jitpack.io") } }
implementation("com.squareup.okhttp3:okhttp:5.3.2")          // OkHttp 5 (el código usa response.body sin "?")
implementation("com.squareup.okhttp3:okhttp-brotli:5.3.2")   // + okhttp-dnsoverhttps, logging-interceptor, okhttp-zstd (mismas 5.3.2): las extensiones los usan por nombre; sin brotli: "Failed resolution of: Lokhttp3/brotli/Brotli;"
implementation("io.reactivex:rxjava:1.3.8")                  // RxJava 1: lo exige el source-api
implementation("org.jsoup:jsoup:1.23.2")
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-okio:1.11.0")
implementation("androidx.preference:preference-ktx:1.2.1")
implementation("com.github.mihonapp:injekt:91edab2317")      // jitpack
// ya deberían estar: kotlinx-coroutines-android, androidx.core:core-ktx, compose runtime
```
Versiones copiadas del `gradle/*.versions.toml` de Komikku (que usa Kotlin 2.4.x). Si el proyecto usa un Kotlin más viejo
y `kotlinx-serialization-json:1.11.0` o `jsoup 1.23.2` no compilan, baja a versiones anteriores compatibles.
No hace falta el plugin `kotlin-serialization`. Si algún día se activa minify/R8 en release, hay que añadir reglas
`-keep class eu.kanade.tachiyomi.** { *; }` (las extensiones usan esas clases por nombre).

### Qué debería funcionar (NO verificado)
Instalar extensión → **Confiar** → su fuente sale en *Fuentes* → al abrirla: Populares / Recientes / búsqueda con
portadas (`NavegarFuente` en `ui/ExtPantallas.kt`, ya existía) y la pestaña *Novedades*.

### Qué NO existe / límites conocidos
- **Cloudflare**: `CloudflareInterceptor` + `WebViewInterceptor` restaurados (resuelven el desafío con un WebView oculto, 30 s). Sin ellos las extensiones fallaban con «CloudflareInterceptor must be present in default client». Sin probar contra un sitio protegido real.
- **QuickJS**: `JavaScriptEngine` es stub.
- **Ficha del manga, lista de capítulos, leer online, descargar → .cbz**: no existen (el motor solo lista y busca).
  `MotorFuentes` hay que ampliarlo (`detalle`, `capitulos`, `paginas`) usando `Source.getMangaUpdate(...)` y `getPageList(...)`.
- Filtros de fuente (`getFilterList`) no se muestran; la búsqueda usa `FilterList()` vacío.
- Ajustes por fuente (`ConfigurableSource`) sin pantalla.
- `index.pb` de Keiyoushi y `Fuente.id` del índice deben coincidir con `Source.id` real; si no, se cae a nombre+idioma.
- Pantalla *Migrar* sigue siendo un aviso.

---

## 2c. PASO 5 — Ficha del manga, capítulos, leer online y descargar .cbz (escrito 2026-10-18, **SIN compilar ni probar**)
Estado de partida: el paso 4 compila y corre; las fuentes (p. ej. AnzManga, CapibaraTraductor) listan Populares con portadas.
Los títulos no se abrían. Ahora sí.

| Archivo | Qué hace |
|---|---|
| `ext/Motor.kt` | + modelos `DetalleManga`, `CapituloFuente`, `FichaManga`; `MotorFuentes` gana `ficha()`, `cantidadPaginas()`, `imagenPagina()`, `descargarCbz()` |
| `ext/MotorExt.kt` | implementa lo anterior con `Source.getMangaUpdate(manga, [], true, true)`, `getPageList`, `HttpSource.getImageUrl/getImage` (cliente de la propia fuente, así valen cookies/Cloudflare/descifrado). Caché de páginas por `fuenteId|capítulo`. `descargarCbz` empaqueta `0001.jpg…` en un zip |
| `Data.kt` | + `Almacen.importarArchivo(File, titulo)` (copia un .cbz del disco a la biblioteca y lo registra) |
| `ui/FuenteManga.kt` | **NUEVO.** `FichaMangaScreen` (portada, autor, estado, descripción, géneros, lista de capítulos, botón «Empezar a leer», botón **CBZ** por capítulo → descarga e importa a la biblioteca) y `LeerOnlineScreen` (tira vertical tipo webtoon con carga perezosa por página, reintento al tocar, caché por bytes, botones Anterior/Siguiente) |
| `ui/Mas.kt` | `enum Ruta` + `Ficha`, `LeerOnline` (`padre()`: LeerOnline→Ficha→FuenteDetalle) |
| `ui/ExtPantallas.kt` | tocar una portada llama a `onManga(MangaFuente)` |
| `ui/LectorApp.kt` | estado `mangaSel`, `capsLectura`, `capIndice` (en `remember`, no sobreviven a muerte de proceso) y las dos ramas nuevas |

Supuestos / límites (léelos):
- «Siguiente» = capítulo de índice menor, asumiendo que la fuente lista lo más nuevo primero. Si una fuente lo invierte, los botones salen al revés. «Empezar a leer» abre el último de la lista.
- No hay progreso de lectura online, ni biblioteca vinculada a la fuente, ni marcar leído, ni modo paginado/RTL en el lector online (solo tira vertical).
- Al volver de la ficha a la lista de la fuente, la lista se recarga desde la página 1.
- Descargar es capítulo a capítulo (no hay «descargar todo» ni cola).
- Solo fuentes `HttpSource` (las demás darán error).

---

## 2d. PASO 6 — Lector online estilo Komikku + Novedades gestionables (2026-10-18, **SIN compilar ni probar**)
Pedido del usuario (con capturas de Komikku): (1) al abrir un capítulo, barras con avance y demás; (2) en Novedades poder
abrir un título, añadir las fuentes que quiera y ordenarlas.

**Lector online (`ui/FuenteManga.kt → LeerOnlineScreen`)** — firma nueva: `(ext, fuenteId, titulo, capitulos, indiceInicial, onBack)`.
- Barra superior: atrás + título del manga + nombre del capítulo. Barra inferior: botón capítulo anterior · píldora con
  `página [Slider con marcas] total` · botón capítulo siguiente; debajo, acciones: Capítulos (diálogo para saltar), Navegador
  (ACTION_VIEW), Compartir, Recargar, Ajustes (modo de lectura).
- Un toque en el centro muestra/oculta barras; oculto, sale «n / total» abajo.
- Modos (persisten en `ExtManager.modoLector`): 0 tira vertical · 1 paginado → · 2 paginado ← (manga, `reverseLayout`). En paginado,
  tocar el tercio izquierdo/derecho pasa de página.
- Marca capítulo leído al llegar a la última página (`ExtManager.marcarLeido`); en la ficha los leídos salen atenuados.
- Iconos: solo set core de Material (no hay SkipPrevious/Bookmark/etc.), por eso las acciones llevan texto debajo.
- No hecho: zoom, marcador (bookmark), recortar bordes, rotación, brillo, recordar página dentro del capítulo.

**Novedades (`ui/Explorar.kt`)** — `ExplorarScreen` gana `onManga(FuenteUI, MangaFuente)`.
- Cabecera de Novedades: «+» abre el diálogo «Novedades» (buscador, casilla + icono + nombre + bandera, «Aceptar»); «≡» entra en
  modo ordenar (arrastrar el icono ≡ para mover, papelera para quitar, «X» para salir).
- Orden y selección en `ExtManager.novedades` (clave `novedades`; si nunca se tocó arranca con las fuentes ancladas).
- Cada fuente es un carrusel (recientes; si la fuente no los tiene, populares). Tocar una portada abre la ficha
  (`desdeNovedades=true` en `LectorApp`: al volver desde la ficha se regresa a Explorar, no a la lista de la fuente); la flecha abre la fuente.
- El arrastre es propio (pointerInput + detectDragGestures, filas de 72dp+12dp), sin librería. Sin probar.

---

## 2e. PASO 7 — Biblioteca con mangas online, categorías estilo Komikku, historial online y descarga cancelable (2026-10-18, **SIN compilar ni probar**)
Pedido del usuario (con capturas de Komikku): (1) añadir un manga/manhwa/cómic de una fuente a la biblioteca con el corazón
«En biblioteca» y poder **asignarle categorías** (diálogo con «Editar» → pantalla «Editar categorías»); (2) que lo que se lea online salga
en el **Historial**; (3) mejorar el **botón de descarga**; (4) volver a tocarlo mientras descarga **cancela**.

| Archivo | Cambio |
|---|---|
| `Data.kt` | `Comic` gana `fuenteId`, `urlManga`, `portadaUrl` (con valores por defecto) + `enLinea` + `etiquetaProgreso()`. Un manga en línea **no tiene .cbz**: `paginas` = capítulos totales, `ultimaPagina` = capítulos leídos. Nuevos en `Almacen`: `comicEnLinea / anadirEnLinea / quitarEnLinea / actualizarProgresoEnLinea`, historial online (`EntradaHistorial`, `historialLinea`, `registrarHistorialLinea`, `quitarHistorialLinea`; `limpiarHistorial` también lo vacía), `descargas() / registrarDescarga / olvidarDescarga`, `reordenarCategorias`. La copia de seguridad **omite** los mangas en línea. |
| `ui/Iconos.kt` | **NUEVO.** `Iconos` (Descargar, Detener, Ojo, OjoTachado, Arrastrar: ImageVector con trazados Material, sin `material-icons-extended`), `EstadoDescarga` (Libre / Descargando(fracción) / Listo) y `BotonDescarga` (círculo+flecha → anillo de progreso con ■ en el centro → círculo con ✓). |
| `ui/EditarCategorias.kt` | **NUEVO.** `EditarCategoriasScreen` (tarjetas con asa para **arrastrar y reordenar**, lápiz, ojo, papelera, botón «Añadir») y `DialogoAsignarCategorias` (casillas + Editar / Cancelar / Aceptar). Más → Categorías ahora abre esta pantalla (`CategoriasScreen` vieja de `Mas.kt` quedó sin usar). |
| `ui/FuenteManga.kt` | `GestorDescargas` (object, sobrevive a salir de la ficha: `progreso`, `iniciar`, `cancelar`). `FichaMangaScreen`: fila de acciones (corazón **En biblioteca** / **Categorías** / Navegador), al añadir pregunta categorías si existen, «Editar» abre la pantalla encima y al volver reabre el diálogo; botón de descarga nuevo por capítulo (tocar mientras descarga = cancelar; tocar el ✓ = quitar descarga); si venía del historial abre el capítulo guardado. `LeerOnlineScreen` gana `almacen`, `manga`, `paginaInicial`: anota historial (con 600 ms de espera) y actualiza capítulos leídos de la biblioteca. `onLeer` ahora es `(caps, índice, página)`. |
| `ext/MotorExt.kt` | `descargarCbz` llama a `ensureActive()` entre páginas (cancelación). |
| `Historial.kt` | `HistorialScreen` mezcla cómics locales + lecturas online; nuevos parámetros `alAbrirOnline` y `cabecerasDe` (con valores por defecto). Mantener pulsado quita la fila. |
| `ui/LectorApp.kt` | `abrirEnLinea(...)`: biblioteca/historial abren la ficha de un manga en línea (no `DetalleScreen`); `reanudar` y `paginaLectura`; relee la biblioteca al cerrar cualquier capa. |
| `ui/Data.kt`, `Vistas.kt`, `BibliotecaScreen.kt` (`estadoDe`, `progresoDe`), `ui/Mas.kt` (estadísticas) | Entienden `enLinea` (progreso = capítulos). |

Supuestos / límites:
- Un manga en línea solo se lee online (desde su ficha). Los capítulos descargados siguen siendo cómics .cbz independientes («Manga - Capítulo N»).
- «Siguiente/Anterior» y la lógica de «leído» siguen siendo las del paso 6.
- La biblioteca de mangas en línea no se guarda en la copia de seguridad.
- El historial online guarda un capítulo por manga (el último), hasta 300.
- Cancelar espera a que termine la página que se está bajando (el botón vuelve a «descargar» al instante).
- Sin probar: arrastre de categorías, diálogo con 3 botones, anillo de progreso, reanudar desde el historial.

---

## 2f. PASO 7b — Corrección: descargas sin duplicado + progreso por capítulo (2026-10-18, **SIN compilar ni probar**)
Problemas reportados: (1) al descargar un capítulo aparecía como cómic suelto («One Punch-Man - Capítulo 305_») duplicando al manga en Home/Historial/mini-lector;
(2) al leer un capítulo no se guardaba el avance; (3) en Historial los títulos salían negros sobre negro.
- **Descargas = archivo del capítulo, no cómic.** `Almacen.archivoDescarga / estaDescargado / guardarDescarga / borrarDescarga` (carpeta `filesDir/descargas`). `GestorDescargas` ya no llama a `importarArchivo`.
  Al arrancar, `migrarDescargasViejas()` convierte las descargas antiguas (cómics sueltos) en descargas del capítulo y las quita de la biblioteca.
- **Lectura sin conexión:** `LeerOnlineScreen` usa el .cbz descargado si existe (`paginasDeCap` / `bytesDePagina`), así leer una descarga también da historial y progreso del manga.
- **Progreso por capítulo (como Komikku):** `ExtManager.paginaDe / guardarPagina` (prefs `paginas_cap`); `marcarLeido` lo borra. El lector guarda la página (espera 600 ms) y marca leído en la última; la ficha muestra «Pág: N» en los capítulos empezados y al tocarlos (o «Reanudar») sigue por esa página.
- `Historial.kt`: color de texto explícito (`LectorColors.Text`) y nombre de capítulo sin «:» sobrante.

## 2g. Fusión con los cambios del usuario (2026-10-18)
El usuario devolvió el proyecto con un rediseño de la ficha (`FuenteManga.kt`: cabecera a pantalla completa teñida con el color de la portada, tarjetas «Último capítulo / Sinopsis», lista de capítulos con número y orden más nuevos/antiguos, botón leer/corazón/píldora, `TemaPortada` + `temaDe` en `Movimiento.kt`, `ImagenRemota(onCargada)` en `Explorar.kt`),
pero partiendo de la versión anterior al paso 7b. Se conservó su diseño y se re-aplicó encima el 7b: descargas por capítulo (`estaDescargado/guardarDescarga/borrarDescarga`), «Pág: N» y reanudar por página guardada (también en cabecera, barra superior y tarjeta «Último capítulo»), lectura de descargas sin conexión y guardado de página en el lector.
Se quitó `AccionFicha` (ya no se usa). Ojo: `TemaPortada` existe como clase en `ui/Movimiento.kt` y como función composable en `Theme.kt` (paquetes distintos, sin choque hoy; no importar ambos con `*`).
**Sigue sin compilarse**: primer paso al retomar = compilar y corregir.

## 2h. PASO 8 — Listados que no se dejaban añadir + pestaña Actualizaciones + comparación con Komikku (2026-10-18, **SIN compilar ni probar**)
- **Añadir listados (404):** la app pegaba `/index.min.json` detrás de lo escrito (p. ej. detrás de `…/repo.json`). Ahora `ExtRepos.candidatos()` acepta índice (`index.min.json/index.json/index.pb`), `repo.json`, carpeta, páginas de GitHub (`blob/raw/tree`), `usuario/repo` y enlaces `tachiyomi://add-repo?url=`; prueba varias direcciones y `ExtRepos.meta()` toma nombre/web de `repo.json`. `ExtRepos.carpeta()` evita duplicados. El diálogo ofrece atajos (Keiyoushi, Suwayomi, Yūzōnō, Cursed Yūzōnō, Kavita; el de Kavita sale de la captura del usuario, `Kareadita/tach-extension`; **Nyora**: no se encontró dirección oficial).
- **Pestaña Actualizaciones** (entre Biblioteca e Historial, `LectorTab.Updates`): `ui/Actualizaciones.kt` (`Actualizador` + `ActualizacionesScreen`). `Almacen.registrarCapitulos()` compara con los capítulos ya conocidos (clave `cc|fuente|manga`; la primera vez solo guarda la base). Se revisa al abrir la pestaña si pasaron 6 h, con el botón, y cada vez que se abre una ficha de la biblioteca. Quitar un manga de la biblioteca olvida su base y sus novedades.
- **No hay** revisión en segundo plano ni notificaciones (haría falta WorkManager).
- `FUNCIONES_KOMIKKU.md`: tabla de funciones de Komikku (README oficial) frente a las nuestras, con orden sugerido.
- Pendiente de verificar: el esquema de `repo.json` (`meta.name`, `meta.website`) y el «formato de índice más nuevo» que menciona el changelog de Komikku 1.14 (no está soportado: un índice que empiece por `{` se interpreta como protobuf y falla).

## 2i. PASO 9 — Optimización y revisión estática (2026-10-19, **SIN compilar ni probar**: el entorno no tenía Kotlin/Gradle ni red)
Se revisó el código leyéndolo (no ejecutándolo). Lo que se cambió y por qué:

**Rendimiento**
- `ui/Explorar.kt` → `Imagenes`/`ImagenRemota`: caché en memoria por **bytes** (antes 40 imágenes: las cuadrículas largas volvían a descargar portadas), **caché en disco** (`cacheDir/imagenes`, ~120 MB), una sola petición por URL, máx. 6 descargas a la vez, no decodifica respuestas de error. Una portada que ya está en memoria se pinta en el primer fotograma (sin parpadeo). `ImagenRemota` reintenta si cambian las cabeceras.
- `Data.kt` → `Almacen`: la biblioteca, las categorías y las asignaciones se leen del JSON **una vez** y viven en memoria (antes se reparseaban en cada consulta, incluso al recomponer). Lectura-modificación-escritura bajo un mismo cerrojo (`modificar { }`, `@Synchronized`) para no perder cambios entre el hilo de la interfaz y el actualizador/descargas. `estaDescargado` usa un conjunto en memoria (antes: SHA-256 + acceso a disco **por fila** de la ficha).
- `Cbz.paginas`: la lista de páginas de cada .cbz se guarda en una caché (antes cada página del lector online descargado reabría el zip y reordenaba todos los nombres).
- `ext/MotorExt.kt`: `cabeceras()` ya **no carga el APK de la extensión** si no estaba cargada (se hacía en el hilo principal desde `LectorApp`); nueva `cabecerasCargando()` (suspend) para pedirlas en segundo plano. Caché de cabeceras por fuente; caché de páginas acotada a 24 capítulos; `descargarCbz` baja 3 páginas a la vez.
- `ext/ExtManager.kt`: la lectura del JSON de extensiones disponibles, el listado de paquetes instalados y las firmas SHA-256 pasaron del hilo principal a segundo plano (arranque más rápido).
- `BibliotecaScreen.kt`: filtrar/ordenar la lista va en `remember` con sus dependencias (antes se rehacía en cada recomposición).
- `ui/FuenteManga.kt`: `sig`/`hayLeidos` memorizados; lista de capítulos con `key` estable.
- Animaciones: arranque de ~1,45 s a ~0,95 s; transiciones de pestañas y capas ~25 % más cortas; entrada escalonada de listas más corta.

**Errores corregidos**
- Lector online: las páginas muy altas (webtoon) se decodificaban como **un solo bitmap** que la GPU no dibuja (salían en blanco o cerraban la app). Ahora se cortan en tiras de 4096 px. El hueco de carga usa la proporción de la última página (antes 480 dp fijos → la tira «saltaba» al llegar la imagen).
- `LectorApp`: al restaurar la app tras morir el proceso, `ruta` volvía pero `mangaSel`/`capsLectura` no (pantalla «No se puede abrir el capítulo»). Ahora vuelve a la pestaña.
- `GestorDescargas` usaba `Dispatchers.Main.immediate`: un fallo síncrono podía dejar un capítulo «descargando» para siempre. Ahora `Dispatchers.Main`.

**Funciones nuevas**
- Cola de descargas (2 a la vez) y menú «Descargar» en la ficha: siguiente, siguientes 5/10, no leídos, todos.
- Copia de seguridad: incluye los mangas en línea (clave `en_linea`; compatible con copias antiguas).
- Lector online: precarga de las 3 páginas siguientes, pantalla encendida mientras lees.

**Sin probar / riesgos conocidos**
- Todo lo anterior. Primer paso al retomar: compilar. Puntos donde más probable es un error de compilación: `Imagenes` (Explorar.kt), `CachePaginas`/`decodificarTiras`/`PaginaOnline` y el menú de descargas (FuenteManga.kt), `modificar { }` (Data.kt), `descargarCbz` (MotorExt.kt).
- `press3D`/`pressableCon` usan `composed {}` en cada tarjeta de las cuadrículas (costoso): conviene migrarlos a `Modifier.Node` cuando se pueda probar.
- La caché de disco de imágenes se indexa solo por URL (no por cabeceras).

## 2j. PASO 10 — Funciones 14 y 15: ajustes de pantalla y de imagen también en el lector online (2026-10-19, **SIN compilar ni probar**)
El lector local ya tenía fondo, orientación, brillo, grises, invertir, filtro de color y recorte de bordes; el online (`LeerOnlineScreen`) no.
- `LectorScreen.kt`: `AjustesPantalla` y `construirFiltro` pasan de `private` a `internal` (misma lógica, ahora reutilizada).
- `AjustesLector.kt`: las secciones «Pantalla» e «Imagen» salen de `DialogoLector` a `AjustesPantallaImagen(almacen)`; nuevo `DialogoPantallaLector(almacen, alCerrar)` (solo esas secciones, sin las opciones de navegación del lector local). El lector local se comporta igual.
- `ui/FuenteManga.kt`: `LeerOnlineScreen` llama a `AjustesPantalla(almacen)` (orientación y brillo, se restauran al salir), pinta el fondo elegido, aplica el filtro de grises/invertido a cada `Image`, pone la capa de color encima de las páginas (debajo de las barras) y recorta bordes en los modos paginados (`produceState` en segundo plano con `Cbz.recortarBordes`; las páginas partidas en tiras no se recortan). El diálogo «Modo de lectura» gana el botón «Pantalla y color».
- Sin dependencias nuevas. Sin probar: orientación al girar con el lector online abierto, recorte en páginas reales, filtro invertido con fondo blanco.
- Sigue faltando en el lector online: zoom (pellizco/doble toque), autodesplazamiento, fondo inteligente.

## 2k. PASO 11 — Extensiones portadas de Komikku (2026-10-19, **SIN compilar la app entera**; el lector de listados sí se compiló y probó aparte)
Origen: `data/.../extension/service/ExtensionStoreService.kt` + modelos `NetworkExtensionStore`, `NetworkLegacyExtension(Repo)`; `app/.../extension/util/ExtensionLoader.kt`; `domain/.../TrustExtension.kt`; `ExtensionManager.updateExists`; `JavaScriptEngine`.
- **CORRECCIÓN IMPORTANTE (ext/ExtRepos.kt):** el esquema protobuf que había aquí estaba mal (decía «101 repetido = extensión»). El real: tienda {1 nombre, 2 etiqueta, 3 clave de firma, 4 contacto{1 web, 2 discord}, 101 lista{1 repetido extensión}, 102 url de lista aparte}; extensión {1 nombre, 2 paquete, 3 {1 apk, 2 icono}, 4 lib (texto), 5 versionCode, 6 versionName, 7 aviso (≥2 = NSFW), 8 fuentes{1 id, 2 nombre, 3 idioma, 4 web}}. Probado con un index.pb sintético (con y sin gzip). Se quitó el apaño que cambiaba Keiyoushi a index.min.json.
- `ExtRepos.leerTienda(url)` = `fetch` de Komikku: «[» índice clásico (lee repo.json; si trae `index_v2` salta al índice nuevo), «{» repo.json o tienda JSON, otro = protobuf; gzip automático. `ExtRepos.extensiones(tienda)` = `getExtensions`.
- `RepoExt` guarda ahora etiqueta, clave de firma, legacy, lista aparte y discord (persistido en prefs; los listados guardados se actualizan solos al refrescar).
- **ext/CargadorExt.kt (nuevo):** valida como Komikku: metadatos `tachiyomix.*`, versión de librería SOLO 1.4 y 1.6 (1.5 se rechaza, igual que Komikku), firmas SHA-256 de todas las del paquete, extensión sin firma = error. `ExtInstalada.errorCarga` dice el motivo. Carga real de las fuentes (id/nombre/idioma/baseUrl) en segundo plano; `ExtManager.erroresCarga` guarda los fallos de carga (la interfaz todavía NO los muestra).
- **Confianza (como TrustExtension):** una extensión firmada con la clave de un listado añadido es de confianza sin pulsar «Confiar»; las demás siguen necesitando «Confiar».
- **Actualizaciones:** solo cuenta el listado con la MISMA firma; hay actualización si sube versionCode o libVersion. `disponibles` sigue siendo una entrada por paquete (las pantallas no cambian); `disponiblesTodas` guarda una por paquete+firma.
- **JavaScriptEngine** real con QuickJS (`com.github.zhanghai.quickjs-java:quickjs-android:547f5b1597`, misma versión que Komikku). OJO: dependencia nueva que baja de JitPack; si el build falla por ella, revisar esto primero.
- NO portado todavía: extensiones privadas (APK dentro de la app), notificación de actualizaciones, pantalla de ajustes por fuente (ConfigurableSource / SourcePreferencesScreen), filtros de búsqueda de cada fuente, migración de fuente.

## 2l. PASO 12 — Ajustes propios de cada fuente (2026-10-19, **SIN compilar**)
Origen: Komikku `SourcePreferencesScreen`/`SourcePreferencesFragment` (usa un PreferenceFragmentCompat). Aquí NO hay FragmentActivity ni tema AppCompat, así que se hizo distinto: la extensión rellena el mismo `androidx.preference.PreferenceScreen` (`setupPreferenceScreen`) creado con `PreferenceManager(ctx)` (archivo `source_<id>`, el mismo que usa Komikku) y cada preferencia se dibuja en Compose.
- `ui/AjustesFuente.kt` (nuevo): `AjustesFuenteScreen(ext, pkg, fuente, titulo, onBack)`. Soporta TwoStatePreference (Switch/CheckBox), EditTextPreference, ListPreference, MultiSelectListPreference, categorías/subpantallas aplanadas y Preference con onClick. Los cambios pasan por `callChangeListener` y luego se guardan, como el original. Respeta `isVisible`/`isEnabled` (dependencias). NO soportado: SeekBarPreference y el tipo de teclado de EditTextPreference (contraseña, número…).
- Al salir tras cambiar algo: `ExtManager.recargarExtension(pkg)` descarta la extensión cargada (`CargadorExt.olvidar`) y las cabeceras/páginas guardadas (`MotorExt.olvidar`) para que la fuente se cree otra vez leyendo el dominio nuevo.
- `ExtManager.configurables(pkg)`: fuentes con ajustes (vacío hasta que la extensión termina de cargar).
- Puntos de entrada: detalle de la extensión («Ajustes de las fuentes») y engranaje en la cabecera de la fuente. El detalle de la extensión ahora enseña el motivo si no carga («Problema»: `errorCarga` / `ExtManager.erroresCarga`).
- Posible fallo de compilación: `PreferenceManager(Context)` está marcado @RestrictTo en androidx.preference (solo es aviso de lint, no error de compilación).
- Sigue sin hacer: filtros de búsqueda de cada fuente, migración de fuente, notificación de actualizaciones, extensiones privadas.

## 2m. PASO 13 — Filtros de búsqueda de cada fuente (2026-10-19, **SIN compilar**)
Origen: Komikku `SourceFilterDialog` (+ `BrowseSourceScreenModel`: filtros editados en sitio, reset = `getFilterList()` nuevo, búsqueda con la misma lista).
- `ext/Motor.kt`: `MotorFuentes.filtros(fuente): FilterList?` y `buscar(fuente, consulta, pagina, filtros)` (con implementación por defecto; el `buscar` de 3 argumentos sigue igual). `MotorExt` los implementa con `getFilterList()` / `getSearchManga(pagina, consulta, filtros)`.
- `ui/FiltrosFuente.kt` (nuevo): `PanelFiltros` a pantalla completa (Restablecer / Cancelar / Filtrar). Soporta Header, Separator, CheckBox, TriState (ignorar→incluir→excluir), Text, Select (menú), Sort (con ↑/↓), Group (plegable) y AutoComplete (como texto separado por comas). NO porta las búsquedas guardadas.
- `ui/ExtPantallas.kt` (`NavegarFuente`): chip «Filtros» (con ✓ si hay filtros aplicados) junto a Populares/Recientes; «Filtrar» lanza la búsqueda con la consulta escrita (puede estar vacía) y los filtros. Si la fuente no tiene filtros, el chip no aparece.
- Los filtros no son estado de Compose: `PanelFiltros` usa un contador (`tick`) que se pasa a cada fila para repintarla tras cada cambio.
- Sigue sin hacer: tocar un género en la ficha para buscarlo con filtro (Komikku `searchGenre`), migración de fuente, notificación de actualizaciones (no hay WorkManager ni notificaciones en la app), extensiones privadas.

## 2n. PASO 14 — Género tocable y migración de fuente (2026-10-19, **SIN compilar**)
Origen: Komikku `BrowseSourceScreenModel.searchGenre` y la migración de fuentes (versión simplificada).
- **Género tocable:** en la ficha, cada género es un botón. `FichaMangaScreen(alGenero)` → `LectorApp` guarda `generoBuscar`, apaga `desdeNovedades` y abre `Ruta.FuenteDetalle`; `FuenteDetalleScreen(generoBuscar, alConsumirGenero)` → `NavegarFuente(generoInicial)` pide filtros NUEVOS, `marcarGenero` (port de searchGenre: Group→TriState incluir / CheckBox, o Select por nombre) y busca con ellos; si la fuente no tiene ese filtro, busca el género como texto. OJO: `FuenteDetalleScreen` ahora tiene los parámetros nuevos ANTES de `onBack` (la lambda final sigue siendo `onBack`).
- **Migración:** botón «Migrar a otra fuente» en la ficha de un manga que está en la biblioteca. `ui/Migrar.kt` (`MigrarScreen`): elegir fuente → buscar por título → elegir resultado → confirmar. Es la MISMA entrada de la biblioteca (mismo id): se conservan categorías, portada guardada y orden. `Almacen.migrarEnLinea` cambia fuenteId/urlManga/portadaUrl/total/leídos y olvida novedades e historial de la fuente vieja; `ExtManager.migrarLeidos` pasa lo leído emparejando por NÚMERO de capítulo (si no se puede, marca los N más antiguos según el contador). Después registra los capítulos nuevos como base de novedades y vuelve atrás con un aviso.
- NO hace: mover descargas de la fuente vieja (quedan huérfanas en disco), copiar marcadores de capítulo, migrar varios mangas a la vez, ni la pantalla «Migrar» por fuente de Komikku. Si la fuente vieja ya no responde, `capsOrigen` es null y se usa el contador de leídos.
- Sigue sin hacer: notificación de actualizaciones de extensiones (no hay WorkManager), extensiones privadas.

## 2o. PASO 15 — «Todo lo de Komikku» + mejoras y optimización (2026-10-20, **COMPILA en GitHub Actions; NO probado en un móvil**)
Rama de GitHub: `claude/komikku` (no se tocó `main`). El workflow compila el APK debug (y el release optimizado).

### Verificación
- Revisión de tipos local con Kotlin 2.0.21 + plugin de Compose: 0 errores.
- **GitHub Actions: `assembleDebug` OK** (artefacto `lector-debug-apk`). Ver el estado del release en la pestaña Actions.
- Nada de esto se ha abierto en un teléfono todavía: **hay que probarlo**.

### Archivos NUEVOS
- `Preferencias.kt`: delegados `prefBool/prefInt/prefLong/prefFloat/prefString/prefEnum` (estado de Compose sobre
  SharedPreferences) y `enum Tri` (ignorar / incluir / excluir) para los filtros de tres estados.
- `Descargas.kt`: cola de descargas **persistente** en un trabajo de WorkManager en primer plano (notificación con progreso),
  simultáneas configurables, pausar / reanudar / cancelar / reintentar / mover al principio, solo con Wi‑Fi.
- `Trabajos.kt`: `RevisorBiblioteca` + `TrabajoActualizar` (busca capítulos nuevos en segundo plano, periódico o manual,
  con restricciones Wi‑Fi / cargando / saltar terminados, descarga automática de lo nuevo, errores guardados),
  `TrabajoExtensiones` (aviso de extensiones con versión nueva), `TrabajoCopia` (copia automática) y `Programador`.
- `Notificaciones.kt`: canales, ids y el extra `"destino"` que abre Actualizaciones / Descargas / Extensiones.
- `CopiaSeguridad.kt`: copia versión 2 (biblioteca, categorías, progreso, historial, ajustes, leídos, marcas, listados,
  fuentes ancladas, búsquedas guardadas). Se siguen restaurando las de versión 1.
- `Portadas.kt` (`rememberPortada`), `Teclas.kt` (teclas de volumen), `Ayudas.kt` (compartir / guardar imágenes…).
- `ui/Visor.kt`, `ui/VisorPaginas.kt`, `ui/VisorAjustes.kt`: **visor único** para cómics locales, descargas y online:
  paginado ← / →, vertical, webtoon y continuo; zonas de toque (L, Kindle, bordes, izq./der.) con inversión; teclas de
  volumen; autodesplazamiento; zoom con pellizco y doble toque; recorte de bordes; división de páginas dobles; relleno
  lateral; transiciones entre capítulos; precarga configurable; filtros de color y brillo; modo propio por serie;
  mantener pulsada una página = compartir / guardar / usar como portada.
- `ui/Biblioteca.kt`: pestañas por categoría o **agrupada por fuente / estado**, hoja Filtro / Ordenar / Mostrar
  (filtros de tres estados, orden por sin leer, total, último capítulo, última revisión, aleatorio…), insignias
  (sin leer, descargados, idioma, Local), botón continuar, **búsqueda con operadores** (`"frase"`, `-excluir`,
  `fuente:`, `autor:`, `genero:`), selección múltiple (categorías, leído / no leído, descargar, migrar, borrar),
  deslizar hacia abajo = buscar capítulos nuevos.
- `ui/Actualizaciones.kt` (rehecha): por día y agrupada por manga, selección múltiple, errores de revisión.
- `ui/Historial.kt` (paquete `ui`, sustituye al viejo): por día, buscador, quitar uno o todo, aviso de incógnito.
- `ui/ColaDescargas.kt`, `ui/BusquedaGlobal.kt` (buscar en todas las fuentes, solo ancladas, mantener pulsado = añadir),
  `ui/Comunes.kt` (cabecera de selección, `BarraAcciones`, filas de filtro / orden / radio).
- `app/proguard-rules.pro`, `res/drawable/ic_notificacion.xml`, `res/xml/archivos.xml` (FileProvider),
  `eu/kanade/tachiyomi/network/DohProviders.kt` (copiado de Komikku).

### Archivos MODIFICADOS (lo importante)
- `Data.kt`: `Almacen.obtener(ctx)` es **singleton** (lo usan también los trabajos de fondo); contador `version` para
  recomponer; `Comic` con autor / estado / géneros / descripción / edición propia / portada propia; índice de descargas;
  muchos ajustes nuevos (tema, visor, biblioteca, descargas, actualizaciones, seguridad, DoH, copias…); historial que
  respeta el modo incógnito.
- `ui/FuenteManga.kt` → `FichaMangaScreen` rehecha: filtros / orden / modo de mostrar de capítulos, selección múltiple
  (leído, no leído, anteriores leídos, marcar, descargar, borrar descarga), menú de descargas en lote, chips de
  Seguimiento / **Editar información** / Migrar / Compartir / **Buscar en otras fuentes**, **visor de portada**
  (compartir, guardar, cambiar), **recomendaciones de AniList**, datos actualizados al abrir. El lector online usa `Visor`.
- `ui/ExtPantallas.kt` (`NavegarFuente`): desplazamiento infinito, vista cuadrícula / lista, insignia «En biblioteca»,
  mantener pulsado = añadir / quitar, esconder lo que ya está en la biblioteca, **búsquedas guardadas** (solo el texto).
- `ui/Explorar.kt`: pestaña **Migrar** real (series agrupadas por fuente), entrada a la búsqueda global; `ImagenRemota`
  admite `escala` y `maxLado`; `Imagenes.bytes / tamanoDisco / vaciar`.
- `ui/LectorApp.kt`: rutas nuevas (`AjVisor, AjDescargas, AjExplorar, AjSeguridad, AjAvanzado, AcercaDe, Descargas,
  BusquedaGlobal, MigrarSerie`) y navegación desde notificaciones. Firmas nuevas de Biblioteca / Historial / Actualizaciones.
- `MainActivity.kt`: tema **Sistema / Oscuro puro / Oscuro / Claro** (`TemaActual` + `LectorColors` dinámicos, barras del
  sistema a juego), **bloqueo de la app** con el PIN / huella del sistema (`KeyguardManager`, tras N minutos fuera),
  **pantalla segura** (`FLAG_SECURE`), permiso de notificaciones (Android 13+), canales y trabajos programados.
- `ui/AjustesPantallas.kt`, `ui/Mas.kt`: Ajustes de Visor, Descargas, Explorar, Seguridad, Avanzado (DoH, cookies,
  cachés), Acerca de; estadísticas ampliadas; pestaña Más con incógnito y cola de descargas.
- `NetworkHelper.kt`: **DNS sobre HTTPS** (Cloudflare, Google, AdGuard, Quad9; se aplica al reiniciar).
- `ui/Estilo.kt`: modificadores de pulsación con `Modifier.Node` (menos recomposiciones).
- Borrados: `BibliotecaScreen.kt`, `Historial.kt` (raíz), `workflow-build.yml` (copia del workflow).

### Dependencias / manifest (CAMBIOS)
- `app/build.gradle.kts`: + `androidx.work:work-runtime-ktx:2.9.1`; `buildTypes.release` con R8 (`isMinifyEnabled`,
  `isShrinkResources`, **sin ofuscar**, firmado con la clave debug); versión 0.2 (code 2).
- Manifest: `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `WAKE_LOCK`,
  `ACCESS_NETWORK_STATE`; `FileProvider` (`${applicationId}.archivos`); `SystemForegroundService` de WorkManager con
  `foregroundServiceType="dataSync"`.
- Workflow: compila en cualquier rama; si falla, los errores salen en el resumen del run.

### Qué NO está (diferencias que quedan con Komikku)
- Trackers distintos de AniList (MyAnimeList, Kitsu, MangaUpdates…), sincronización en la nube, combinar entradas
  (merge), feed multi‑fuente, portada panorámica, extensiones privadas, búsquedas guardadas CON filtros.
- La migración sigue sin mover las descargas de la fuente vieja.

## 2p. PASO 16 — Arreglo de extensiones en el APK release + diseño de musicfy (2026-10-20)
**Arreglo:** en el APK *release*, R8 marcaba como `final` métodos de la API de fuentes que la app no sobrescribe
(p. ej. `HttpSource.fetchRelatedMangaList`) y las extensiones que sí los sobrescriben no cargaban
(«overrides final method»). `proguard-rules.pro` ahora usa `-keep class … { *; }` SIN `allowoptimization`
para `eu.kanade`, `tachiyomi`, `kotlin`, `okhttp3`, `okio`, `jsoup`, `rx`, `quickjs`, `injekt`, `androidx.preference`.

**Diseño de musicfy** (https://github.com/realidkroo/musicfy, GPL‑3.0): se reprodujo su lenguaje visual con
código PROPIO (no se copió código suyo):
- `ui/Cristal.kt` (nuevo): cristal esmerilado. `Modifier.raizCristal(estado)` graba la app en un `GraphicsLayer`;
  `FondoCristal` lo vuelve a dibujar desenfocado (RenderEffect, Android 12+) con tinte #131313 al 78 %, grano y
  línea de 2 px; `CristalProgresivo` para cabeceras. `CristalApp` = lo que hay bajo la barra. En Android 8–11 no
  hay desenfoque (tinte casi opaco). Una pieza de cristal NUNCA debe estar dentro de la raíz que dibuja.
- `ui/Theme.kt` / `Theme.kt`: grises de musicfy (#141416 campos, #0B0B0C hojas, #2C2C2E botones…), Inter
  SemiBold con interletrado −0.05 em, paleta por defecto **«Musicfy»** (gris claro monocromo; en tema claro, casi negro).
- `ui/Components.kt`: barra flotante de cristal de 64 dp con píldora que se estira al cambiar de pestaña, iconos
  contorno→relleno y rebote; mini lector de cristal con portada redonda y anillo de progreso.
- `ui/Estilo.kt`: avisos como las tarjetas de musicfy (caen con muelle, se cierran arrastrando hacia arriba).
- `ui/Filas.kt`: `PaginaAjustes` con título grande que se pliega junto al botón atrás (con desenfoque) sobre una
  cabecera de cristal; filas en tarjetas con icono en círculo claro; `Interruptor` y `Marca` propios; `ChipSel`
  con esquinas que rebotan; `TituloSeccion` en mayúsculas grises.
- `ui/Comunes.kt`: `HojaLector` (hojas #0B0B0C, asa gris) y `Hojas`: con una hoja abierta la app se aleja (MainActivity).
- `ui/LectorApp.kt`: pestañas que se deslizan hacia su lado; al abrir una ficha la página de debajo retrocede
  (escala 0.92, se apaga) y la ficha crece con muelle; al cerrar, curva de 560 ms.
- `ui/Explorar.kt`: `CampoBusqueda` como el de musicfy (48 dp, foco más claro, botón borrar).
- Sin probar en móvil. Pendiente si se quiere más parecido: cabeceras plegables con buscador en Explorar /
  Biblioteca (el `SearchGlassTopBar` de musicfy) y la transición «la portada crece hasta la página» (shared element).

## 2q. PASO 17 — Cierres al abrir catálogos (2026-10-20, **probado en emulador** con el APK release)
- Release: R8 quitaba clases con métodos nativos (zstd/brotli) → SIGABRT al recibir la respuesta. `proguard-rules.pro`
  conserva `native <methods>`, `com.squareup.zstd`, `org.brotli`.
- Las extensiones de Keiyoushi están compiladas contra **kotlinx-coroutines 1.11.0** (llaman a `runBlockingK`): con
  1.10.2 había `NoSuchMethodError`. Ahora las librerías coinciden con Komikku (coroutines 1.11.0, okio 3.17.0,
  serialization json/protobuf 1.11.0, xmlutil 0.91.3). **Regla: al actualizar Komikku, igualar estas versiones.**
- `Fallos.kt`: guarda el último cierre (traza) y lo enseña al abrir la app con botón «Copiar».
- `Pdf.kt`: importar PDF (cada página → JPEG dentro de un .cbz).
- `.github/workflows/probar.yml` + `.github/scripts/probar.sh`: en cada push a `claude/**` arranca un emulador
  (API 34), instala el APK release (compilado con `-Pprueba`, que lo hace depurable) y 5 extensiones, abre el
  catálogo con el intent `--es destino probar:<paquete>` (solo funciona en APK depurables) y publica en el resumen
  del run los títulos que se ven, si la app sigue abierta y las trazas de cierre.
- Versión 0.3 (code 3).

## 2r. PASO 18 — WebView en el catálogo + copias de Mihon/Komikku (2026-10-20)
- **WebView en el catálogo** (`ExtPantallas.kt` → `NavegarFuente`): chip «WebView» y, si falla la carga, botones
  «Abrir en WebView» / «Reintentar». Al volver se recarga. `WebViewScreen` ahora recibe las cabeceras de la fuente y
  usa su MISMO User-Agent (Cloudflare ata `cf_clearance` al navegador) y guarda las cookies al cerrar.
- **Copias .tachibk** (Mihon, Komikku, TachiyomiSY, J2K…): `Proto.kt` (protobuf mínimo propio, sin plugin de
  serialización), `TachibkFormato.kt` (formato puro, sin Android) y `Tachibk.kt` (biblioteca ↔ formato).
  Ajustes → Datos: «Restaurar copia» detecta solo si es .json de Lector o .tachibk; «Exportar para Mihon / Komikku».
  Pasa: mangas de fuentes, capítulos leídos/marcados/página, categorías (por «order»), historial, AniList, datos
  editados (Komikku 602/800-805) y, al importar, los listados de extensiones (campo 106). No pasan los .cbz locales.
  **Probado** en el ordenador con la librería de Mihon y sus modelos, en los dos sentidos: `pruebas/tachibk/`.
- Versión 0.4 (code 4).

## 2s. PASO 19 — Avance e informe de las copias + restauración rápida (2026-10-20, **probado en emulador**)
- `TareaCopia.kt`: crear/restaurar corren en segundo plano (sigue aunque cambies de pantalla), con avance en
  `estado`, notificación con barra (canal «copias», id 105) e `Informe` (bien / avisos / errores + duración).
  Al terminar: notificación con resumen (tocarla abre el informe, destino `copia`) y `terminadas++` (MainActivity
  relee la biblioteca).
- `ui/IslaCopia.kt`: píldora pequeña arriba con anillo y «líquido» de avance; tocarla despliega el paso actual; al
  terminar muestra el resultado 8 s y abre `InformeCopia` (hoja con Correcto / Avisos / Errores).
- Rapidez: antes la restauración de Mihon reescribía la biblioteca, los leídos y el historial POR CADA MANGA.
  Ahora todo se calcula en memoria y se guarda en lote: `Almacen.anadirVariosEnLinea`, `registrarConocidosVarios`,
  `anadirCategoriasVarios`, `anadirHistorialVarios`; `ExtManager.importarEstados`. Medido en el emulador con
  `pruebas/tachibk/grande.tachibk` (600 mangas, 48.000 capítulos): restaurar < 4 s; crear la .tachibk 2 s.
- Hooks de prueba (solo APK depurable): `--es destino probar-restaurar:<ruta>` y `--es destino probar-copia`.
- Versión 0.5 (code 5).

## 2t. PASO 20 — Bibliotecas enormes (6.000+ mangas) sin quedarse sin memoria (2026-10-20, **probado en emulador**)
Cierre por `OutOfMemoryError` en un móvil con >6.000 mangas. Cambios:
- `Persistencia.kt`: lo que crece con la biblioteca sale de SharedPreferences (Android lo carga entero y lo reescribe
  entero en cada cambio). Escrituras en un hilo de fondo, agrupadas, línea a línea, con .tmp + renombrar.
  - Capítulos leídos: `ConjuntoLargo` (huellas de 64 bits de «fuente|url», sin objetos por elemento) en
    `filesDir/estado_capitulos/leidos.bin`. Antes era un Set<String> que se COPIABA ENTERO en cada capítulo leído.
    `ExtManager.leidos` ahora es un Int (versión) para claves de remember; `esLeido()`, `totalLeidos`.
  - Marcados y páginas: `estado_capitulos/marcados.txt` y `paginas.txt`.
  - Capítulos conocidos: un archivo por manga en `filesDir/conocidos/` (`CapitulosConocidos`).
  - Biblioteca: `filesDir/biblioteca.json` (antes la clave «comics» de las preferencias).
  - Todo se migra solo la primera vez desde el formato anterior.
- `.tachibk`: restaurar lee la copia por partes desde el gzip (`Proto.recorrerFlujo` + `Proto.Cursor`, sin copias)
  y solo crea textos de los capítulos con progreso; exportar escribe manga a manga (`TachibkFormato.EscritorFlujo`).
- Copia .json: los leídos van como binario en base64 (`leidos_b64`); se siguen leyendo `leidos` y `leidos_h`.
- Medido en el emulador (APK release) con `pruebas/tachibk/enorme.tachibk` (6.500 mangas, 975.000 capítulos,
  145 MB sin comprimir): restaurar 25 s, Java heap ~100 MB; reabrir y exportar 29 s (622.600 capítulos con progreso).
- Versión 0.6 (code 6).

## 2u. PASO 21 — Catálogo y biblioteca (2026-10-20, versión 0.7)
- Error rojo «The coroutine scope left the composition» en el catálogo: la carga infinita era un `LaunchedEffect`
  con `cargando` como clave, que se reiniciaba y cancelaba su propia carga; la cancelación se enseñaba como error.
  Ahora es un `snapshotFlow` en un solo `LaunchedEffect(Unit)` y las `CancellationException` se relanzan (no son error).
- Catálogo: hoja Filtro (en la biblioteca, filtros de la fuente) / Ordenar (fuente o título, asc/desc) / Mostrar
  (cuadrícula cómoda, compacta, solo portadas, lista; columnas). Ajustes en `Almacen.explorar*` y `vistaExplorar`.
- Explorar → Extensiones: «Confiar en todas» (con confirmación).
- Biblioteca: recuerda la última pestaña (`Almacen.ultimaPestanaBiblioteca`, por la clave del grupo).

## 2v. PASO 22 — Listados, portadas, barra rápida, icono y arranque (2026-10-20, versión 0.8)
- «No se pudo leer el listado (no protocol: null)» con repo.json que traen `"index_v2": null` (p. ej. Nyora):
  `optString` de org.json devuelve el texto "null" para un null; ahora `ExtRepos` usa `txt()` (null = vacío).
  Prueba en el emulador: destino `probar-repo:<url>` (solo depurable) y paso en `probar.sh`.
- Portadas que no salían (MyReadingManga y otras con Cloudflare/cookies): `Imagenes` descarga primero con el
  cliente OkHttp de la propia fuente (`MotorFuentes.bytesImagen`: sus cabeceras, cookies e interceptores), luego
  con el cliente general de la app y por último con HttpURLConnection. Todas las `ImagenRemota` pasan `fuenteId`.
- `BarraDesplazamiento` (Comunes.kt): pulgar arrastrable para listas largas, con burbuja de sección (el
  `contentType` del primer elemento visible). Puesta en Explorar → Extensiones (secciones = idioma).
- Icono nuevo: agujero negro en blanco y negro (halo, arco curvado por encima y disco por delante) sobre fondo negro;
  el horizonte (r 17,6 de 108) es transparente. Monocromo a juego. `LogoApp(conFondo)` le pone el disco negro debajo.
- Arranque: el primer fotograma es igual que la pantalla de inicio del sistema (sin parpadeo); el agujero «inspira» y
  se abre: el icono crece acelerando y la app aparece por dentro del horizonte (máscara con BlendMode.Clear).

## 2w. PASO 23 — Fuente no disponible y actualizaciones de la app (2026-10-20, versión 0.9)
- «La fuente no está disponible» ya no es un aviso sin salida: `FuenteNoDisponible` (ui/Reencontrar.kt) dice por qué
  falta (extensión sin confianza / que no carga / no instalada pero en tus listados / desconocida, vía
  `ExtManager.extensionDeFuente`) con su botón (Confiar, Actualizar, Instalar) y busca el título en todas tus demás
  fuentes con filas como Novedades (`FilaResultados`/`rememberResultadosGlobales`, sacadas de BusquedaGlobal.kt).
  Los de título igual van primero con la insignia «Mismo título». Si el manga está en la biblioteca, tocar un
  resultado ofrece «Migrar aquí» (`migrarComic`, en Migrar.kt, ya sin necesitar la fuente vieja) o «Solo abrir».
  «Migrar» desde la biblioteca de una serie sin fuente abre esta pantalla.
- Firma fija: `app/firma/shiori.jks` (contraseña `shiori-lector`, alias `shiori`) para debug y release. Antes cada
  compilación de GitHub firmaba con una clave de depuración nueva y Android exigía desinstalar. La clave está en el
  repositorio a propósito (el repo es público: cualquiera podría firmar un APK «Shiori»; si se quiere evitar, pasarla
  a un secreto de GitHub). Las versiones 0.8 y anteriores llevan otra firma: hay que desinstalar UNA vez.
- `versionCode` = 1000 + número de ejecución de «Build APK» (sube solo); a mano, 9.
- build.yml publica cada compilación release de `claude/komikku` y `main` como versión de GitHub
  (`v<versionName>-<versionCode>`, APK adjunto, notas = mensaje del commit; guarda las 10 últimas).
- `NuevaVersion` (NuevaVersion.kt; ojo, `Actualizador` en Trabajos.kt es el de la biblioteca): al abrir la app
  (no en compilaciones depurables) mira `releases/latest`; si su número es mayor sale `DialogoActualizacion`
  (novedades, descarga con %, instala con PackageInstaller vía `ExtManager.abrirSesion`, explica los errores de
  firma). También cada 12 h con la revisión de extensiones (notificación una vez por versión) y en Ajustes → Acerca de.
- Pruebas en el emulador: `probar-huerfano:<título>`, `probar-version` y la instalación de la versión publicada
  encima de la instalada (`adb install -r`).

## 2x. PASO 24 — Recomendaciones (2026-10-21, versión 0.10)
- Tocar una recomendación (AniList) la busca en la fuente de la ficha abierta (`buscarEnFuente`, FuenteManga.kt:
  prueba inglés, romaji, sinónimos y original; prefiere el título exacto) y abre su ficha directamente. Si no está,
  un aviso con botón «Buscar en todas» (`Avisos.mostrar(texto, accion) { … }`, nuevo) lleva a la búsqueda global.
- Pila de fichas en LectorApp (`fichasAnteriores`): abrir otro manga desde una ficha (recomendación o «Fuente no
  disponible») guarda la actual; «atrás» (`volverDeFicha`) vuelve a ella. Se vacía al salir de la ficha. Cada ficha
  va en un `AnimatedContent` con clave fuente|url (antes el estado de la ficha anterior podía quedarse).
- Prueba: `probar:<pkg>|autorec` (`PruebaAuto`) abre sola la primera recomendación; la ficha anima sin parar y
  uiautomator no puede leerla, así que se comprueba con los registros «ficha abierta: …».

## 2y. PASO 25 — memo, fichas guardadas y deslizar para recargar (2026-10-22, versión 0.11)
- «Refresh the chapter list.»: las extensiones nuevas (tachiyomix 1.6; p. ej. todo lib-multisrc/madara) guardan datos
  en `SManga.memo` / `SChapter.memo` y los piden al abrir un capítulo. La app los tiraba. Ahora `MangaFuente.memo`,
  `CapituloFuente.memo` y `FichaManga.memo` (JSON en texto) van y vuelven en MotorExt (`sManga`/`sCapitulo`).
- `CacheFichas` (CacheFichas.kt): cada `motor.ficha()` se guarda en `files/fichas/<sha1>.json.gz` (JsonWriter/Reader
  en flujo, gzip rápido). La ficha se abre de ahí; deslizar hacia abajo (`PullToRefreshBox`) la pide a la fuente.
  Capítulos reconstruidos sin memo (Actualizaciones, cola de descargas) lo recuperan con `CacheFichas.conMemo`.
- Novedades (`NovedadesGuardadas`, Explorar.kt): una carga por sesión; deslizar para recargar; se olvidan al abrir
  la app (MainActivity.onCreate).
- .tachibk: memo del manga (112) y del capítulo (13) y orden (10), en los dos sentidos. Al restaurar se guarda la
  ficha de cada manga con TODOS sus capítulos (3 hilos en paralelo); al exportar, si hay ficha guardada, van todos
  sus capítulos. Emulador, 6.500 mangas: restaurar ~28 s, exportar ~36 s.
- Pruebas: `probar-url:<pkgs>` (direcciones de los listados) y `probar:<pkg>|capitulo` (populares → ficha →
  páginas del primer capítulo con el memo recuperado); BarManga (Madara) da 21 páginas.

## 2z. PASO 26 — Tacto y movimiento reducido (2026-10-24, versión 0.12)
Auditoría con las skills de diseño (design-engineering, ui-animation, apple-design/game-feel):
- `Tacto` (ui/Tacto.kt): vocabulario háptico central (tic, alternar, umbral, confirmar, rechazar) con efectos del
  sistema (respeta «Vibración al tocar»). Disparos: chips, interruptores, deslizar para recargar (ficha y
  Novedades), añadir/quitar de la biblioteca, migrar, y todo aviso de error.
- `MovimientoReducido`: con «Quitar animaciones» de Android se salta la animación de arranque, sin inclinación 3D,
  sin entradas escalonadas y esqueletos sin ola. Se relee en `onResume`.
- Avisos de error: sacudida de 280 ms (keyframes 80/160/220 ms, amplitud decreciente) + vibración de rechazo.

## 2aa. PASO 27 — Duplicados, búsqueda más rápida, mini lector (2026-10-24, versión 0.13)
- Mini lector solo en Inicio e Historial (tapaba la barra de selección de Actualizaciones y Más); entra/sale plegándose.
- Búsqueda en todas las fuentes (`rememberResultadosGlobales`): 12 a la vez, 12 s máx. por fuente, `CacheBusquedas` en
  memoria (10 min). Migrar: el paso 1 ya busca en todas las fuentes a la vez (filas como Novedades).
- `Duplicados` (ui/Duplicado.kt): al añadir un manga con el mismo título que uno de la biblioteca en otra fuente,
  ventanita animada (escala desde 0,94 + fundido; sale más rápido) con las dos portadas y «Ver el que tengo»,
  «Migrar aquí (con tu progreso)», «Añadir igualmente» y «Cancelar». Desde la ficha y al mantener pulsada una portada.

## 2ab. PASO 28 — «Living Flow Design» de ColorOS 17 (2026-10-24, versión 0.14)
Principios (oppo.com/es/coloros17 y su nota «Flow Everywhere»): luz como respuesta, capas translúcidas con luz,
movimiento continuo que funde y transforma, rebote elástico y ritmo que sigue la velocidad del gesto.
- Luz bajo el dedo en todo lo pulsable (`PulsacionNode`, Estilo.kt): brillo radial que nace al tocar, sigue al dedo y
  se apaga en 520 ms; recortado a la forma (píldora, portada 14 dp, fila).
- `Modifier.luzTactil()` (Components.kt) en la barra flotante de cristal.
- Indicador de la barra: si se cambia de pestaña en < 450 ms, muelles más rígidos (ritmo del gesto).
- Mini lector: brota de la barra (escala desde abajo + plegado) y se funde en ella al salir.

## 2ac. PASO 29 — Extensiones que desaparecían, carpeta de la app y Apariencia (2026-10-25, versión 0.15)
- Bug: `ExtManager.fuentes` hacía `distinctBy { id }` y las fuentes sin id del listado (clásicos, o antes de cargar
  la extensión) tenían id 0 → solo quedaba una. Ahora id 0 → hash estable de pkg|nombre|idioma.
- `CarpetaApp`: carpeta elegida (selector del sistema → ruta real; permiso «todos los archivos» en Android 11+) con
  `descargas/` y `copias/`. `Almacen.recargarCarpeta()`; al cambiar se mueven las descargas. Datos y almacenamiento.
- `Aspecto` (ui/Aspecto.kt, prefs «aspecto»): Superficie (Cristal / Liquid glass / Sólido, en `FondoCristal` y
  `lineaCristal`), Fondo (`FondoVivo`: aurora a 15 fps detrás de las pestañas, dentro de `raizCristal`), Animación de
  inicio (Agujero negro / Rápida / Ninguna) y Luz al tocar. Apariencia las enseña con previsualizaciones.
- Arranque: más ágil (40 + 200 + 560 ms) y la app emerge (escala 1,08 → 1) con `ArranqueRevelado`; «Ver animación».

## 2ad. PASO 30 — Extensiones 1.5 y diagnóstico (2026-10-25, versión 0.16)
- Bug: `CargadorExt.LIBS_SOPORTADAS` era `listOf(1.4, 1.6)` (valores exactos): las extensiones con librería 1.5 se
  marcaban «no soportadas» y no salían. Ahora rango 1.2..1.69 (como Komikku).
- Re-escaneo de extensiones en cada `onResume`.
- Ajustes → Avanzado → «Diagnóstico de extensiones»: `ExtManager.informe()` lista todo paquete que parezca extensión
  con su estado (no declara la característica, no soportada y por qué, sin confianza, error al cargar, nº de
  fuentes); se puede copiar para enviarlo.

## 2ae. PASO 31 — Estado, WebView en el lector, búsquedas en Novedades, parecidos y rendimiento (versión 0.17)
- Pestañas con `rememberSaveableStateHolder`: al cambiar no se pierde desplazamiento ni estado.
- Lector: «Auto» → «WebView» del capítulo actual (`AccionesVisor.cabecerasWeb`; al volver recarga). Barras del
  lector según `Aspecto.superficie`.
- Novedades: búsquedas guardadas como filas propias (`ExtManager.novedadesBusq`, elegibles en el diálogo) y chips
  de las guardadas en cada fuente (`BusquedaPendiente` → abre la extensión ya buscada).
- Ficha: «Parecidos en <fuente>» siempre (`MotorFuentes.relacionados` = `Source.getRelatedMangaList`, 15 s; si no
  hay, búsqueda por el primer género), además de las recomendaciones de AniList.
- Rendimiento: `instaladas`/`fuentes` con `derivedStateOf` (antes se recalculaban en cada lectura); `FondoVivo`
  con `withFrameMillis` (se para en segundo plano); `CacheBusquedas` 400 entradas.

## 2af. PASO 32 — Tema en lector e Inicio, leídos, biblioteca en Novedades, capítulo completo (versión 0.18)
- Inicio: el degradado del destacado se funde con `LectorColors.Bg` (antes negro fijo, horrible en claro).
- Capítulos leídos: fila al 42 % (animado) y visto en vez del número; no leídos con acento más fuerte.
- Novedades: lo que está en la biblioteca sale apagado con la insignia «En biblioteca».
- Lector: barras con `LectorColors` (claro/oscuro) y el estilo de superficie (`TintaBarra`, `SubBarra`).
- `CacheBytes` (VisorPaginas.kt): bytes de cada página en `cache/paginas/<sha1>/<n>` (400 MB, por antigüedad);
  al abrir un capítulo en línea se bajan TODAS sus páginas (3 a la vez); volver a una página ya no usa la red.

## 2ag. PASO 33 — Búsquedas con filtros, «Más reciente», instalación en cola, ajustes de fuente (versión 0.19)
- `FiltrosTexto` (FiltrosFuente.kt): FilterList ↔ JSON por posición; `BusquedaGuardada.filtros` se guarda ya. Guardar
  búsqueda (también solo filtros) con «Mostrar en Novedades»; Novedades y los chips la aplican con filtros
  (`BusquedaPendiente.guardada`).
- «Más reciente» (chip del catálogo y botón del panel de filtros): `ponerMasReciente` elige la opción de fecha /
  actualización del orden de la fuente; si no hay, modo Recientes.
- Extensiones: instalación en cola (`colaInstalacion`, Android confirma de una en una) y «Actualizar todas».
- «Páginas por carga» por fuente (hoja Vista del catálogo; `paginasPorCarga`).
- Ajustes de fuente: `DialogoShiori` + `OpcionPildora` (en lugar de AlertDialog) e `Interruptor`.

## 2ah. PASO 34 — «HTTP error 429» en descargas (versión 0.20)
- Cola: un solo capítulo a la vez por fuente (las simultáneas se reparten entre fuentes).
- `MotorExt.imagenPagina`: máx. 2 peticiones a la vez por fuente (`semaforo`) y `conReintentos` (429/503 → esperas
  2-4-8-16-30 s); grupos de 2 páginas en `descargarCbz`.

## 2ai. PASO 35 — Descargador al estilo Komikku (versión 0.21)
- `descargarCbz`: páginas a archivos en `<destino>.partes/` (3 a la vez); las ya hechas se saltan (reintento solo de
  lo que falta); se reutiliza `CacheBytes` del lector; `reintentar3` (2/4/8 s) + `conReintentos` (429/503); una
  página fallida no para el resto; al final se empaqueta el .cbz y se borra la carpeta.
- «Fuentes a la vez» (antes «Descargas a la vez»): un capítulo por fuente, como `parallelSourceLimit` de Komikku.

## 2aj. PASO 36 — Funciones de Komikku que faltaban (versiones 0.22–0.25)
- 0.22 grupos de traducción ocultos (`ExtManager.gruposOcultos`). 0.23 lectura continua (Visor: transición → capítulo
  siguiente/anterior solo) y «Abrir en Shiori» (SEND text/plain → `abrir-url:`; fuentes de ese dominio, si no búsqueda
  global). 0.24 vista previa de páginas (8 miniaturas, `miniatura()`) y ahorro de datos (wsrv.nl, pref «ahorro_datos»).
- 0.25 `Seguidores.kt`: MAL (OAuth PKCE con Client ID del usuario, vuelta lector://mal), Kitsu (password grant),
  MangaUpdates (sesión); `Seguidores.avanzar` al terminar capítulo. UI: Ajustes → Seguimiento (`CuentasSeguimiento`)
  y chip «MAL · Kitsu · MU» en la ficha (`DialogoServicios`). Fusión: `ExtManager.fusionar/unidas/principalDe`
  (pref «fusiones»); las unidas se ocultan en la biblioteca; chips «Fuentes combinadas» en la ficha.

## 3. PRÓXIMOS PASOS
**Actuales (tras el PASO 15):**
1. Instalar el APK de la rama `claude/komikku` y probar: visor (todos los modos), descargas en segundo plano,
   actualizaciones periódicas + notificaciones, copia / restauración, tema claro, bloqueo, búsqueda global, migrar.
2. Corregir lo que falle en el móvil. Luego fusionar `claude/komikku` en `main`.
3. Lo que falta de la lista «Qué NO está» del PASO 15.

**Históricos (de los primeros pasos, ya hechos):**
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
**Ahora:** el repo es un proyecto Gradle completo. Cada push a GitHub ejecuta `.github/workflows/build.yml`
(Gradle 8.9, JDK 17) y deja el APK en *Actions → el run → Artifacts* (`lector-debug-apk`, `lector-release-apk`).
En local: `gradle assembleDebug` (no hay wrapper; el workflow instala Gradle 8.9).

**Antes (histórico):** Proyecto Gradle (no incluido). Copiar `app/src/main/**` sobre el proyecto existente (los zips se pisan en este orden:
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

## 2ak. PASO 37 (0.26)
- Visor: lectura continua sin botón. Tira: pasa de capítulo en cuanto asoma la transición mientras desplazas (o al tope), hacia delante y hacia atrás. Paginado: la transición solo destella (120 ms) y salta. Quitado el botón «Siguiente/Anterior capítulo».
- Actualizaciones: tocar la portada abre la ficha del manga (alAbrirManga); el resto de la fila sigue abriendo el capítulo.
- ExtManager: la página guardada se conserva aunque el capítulo esté leído (paginaDe/guardarPagina/marcarLeido(s)/carga), así al reabrirlo sigue donde se dejó, también al final.

## 2al. PASO 38 (0.27)
- «Unable to resolve host raw.githubusercontent.com»: DnsRespaldo (sistema → DoH Cloudflare → DoH Google, IPs fijas) en ExtRepos (ahora OkHttp) y en NetworkHelper cuando no se eligió DoH. Si GitHub raw sigue fallando, espejo jsDelivr (cdn.jsdelivr.net/gh/u/r@rama/ruta) para listados y APKs.

## 2am. PASO 39 (0.28)
- Historial: tocar la portada de una entrada en línea abre la ficha del manga (alAbrirManga), como en Actualizaciones.

## 2an. PASO 40 (0.29)
- Migrar: paso 0 «Seleccionar fuentes» (Elegidas/Disponibles, buscar, ★ ancladas, ✓ todas, ✕ ninguna; se recuerda en prefs `migrar_fuentes`). Paso 1 ahora en LazyColumn (antes Column con scroll pintaba cientos de filas → cierre).
- Búsqueda global: 6 a la vez en un grupo propio de 8 hilos (IO.limitedParallelism), máx. 20 resultados por fuente y captura Throwable (errores de extensiones rotas cerraban la app).
- Ficha: menú ⋮ como Komikku: Actualizar, Editar categorías, Migrar, Compartir, Notas (prefs `notas`, se ven en la ficha), Unir, Editar información, Ver recomendaciones (desplaza a la sección), Abrir carpeta, Limpiar manga (descargas + leídos), Ajustes de la fuente.
