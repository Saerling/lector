# HANDOFF — Lector (app Android estilo Komikku) · Pasos 3 a 7: Explorar, Extensiones, Motor, Ficha/Lector online, Biblioteca online

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

## 3. PRÓXIMOS PASOS (en este orden; el punto 1 y parte del 2 ya están hechos arriba, ver 2b)
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
