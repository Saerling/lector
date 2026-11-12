# Shiori 0.72 frente a Komikku y Mihon — qué falta, qué copiar y qué optimizar

Método (2026-11-11): se sacaron todos los ajustes de las pantallas de Komikku (`presentation/more/settings/screen/*`, 27 pantallas)
y se buscaron uno a uno en el código de Shiori por palabras clave. **«Falta» significa que la búsqueda no encontró nada**; alguno
podría existir con otro nombre. No se compararon las pantallas a mano una por una. Las funciones ya hechas están en `FUNCIONES_KOMIKKU.md`.

Prioridad: 🔴 se nota a diario · 🟡 útil · ⚪ nicho.

## 1. Opciones que faltan, por menú

### Lector
| Opción de Komikku/Mihon | Prioridad | Nota |
|---|---|---|
| Zona inicial del zoom (izquierda/derecha/centro/automática) | 🟡 | |
| Ampliar solas las páginas apaisadas + desplazarlas («pan wide images») | 🟡 | |
| Girar las páginas anchas para que encajen | 🟡 | |
| Páginas dobles lado a lado (TachiyomiSY: «page layout») | 🟡 | Tablet Huawei: es donde más se nota. |
| GIF/WebP animados | 🟡 | Hoy se ven quietos. |
| Mostrar el modo de lectura y las zonas de toque al abrir | ⚪ | |
| Destello para pantallas de tinta electrónica | ⚪ | |
| Barra de avance vertical / para zurdos | ⚪ | |
| Guardar páginas en carpetas por título | ⚪ | |
| Sensibilidad para esconder el menú al desplazar, «tocar avanza una página» en tira | ⚪ | |
| Umbral de bitmap por hardware / decodificador antiguo | ⚪ | Solo si aparecen páginas en blanco. |

Ya están: saltar leídos (0.72), saltar duplicados, saltar filtrados (el lector recibe la lista filtrada), zonas de toque, recorte, dividir dobles, precarga, filtros de color, teclas de volumen, pantalla completa.

### Descargas
| Opción | Prioridad | Nota |
|---|---|---|
| Dividir imágenes altas al descargar | 🟡 | El lector ya corta en tiras al mostrar; esto lo deja hecho en disco. |
| Borrar al marcar como leído a mano | 🟡 | Hoy solo al terminar de leer. |
| Categorías excluidas (de «borrar al leer» y de «descargar nuevos») | 🟡 | |
| No descargar capítulos duplicados ya leídos | 🟡 | |
| Comprobar espacio libre antes de descargar | 🟡 | Mihon avisa con menos de 200 MB. |
| `ComicInfo.xml` dentro del .cbz | ⚪ | Para abrirlos bien en otros lectores. |
| Contraseña para los .cbz | ⚪ | |
| Reindexar descargas | ⚪ | |

Ya están: solo Wi‑Fi, fuentes a la vez, descargar adelante, descargar nuevos, borrar al leer (0.72: respeta marcadores).

### Biblioteca y revisión
| Opción | Prioridad | Nota |
|---|---|---|
| Categoría por defecto al añadir | 🔴 | Con 6000 entradas ahorra el diálogo cada vez. |
| Categorías incluidas/excluidas de la revisión | 🔴 | Hoy solo se puede revisar una categoría a mano. |
| Marcar como leído el capítulo nuevo si ya leíste ese número | 🟡 | Parte hecha en el lector (duplicados). |
| Orden distinto por categoría | 🟡 | |
| Refrescar portadas y datos al revisar (interruptor) | 🟡 | Hoy siempre se actualizan. |
| Restricciones «sin datos medidos» y «batería no baja» | ⚪ | Hay Wi‑Fi y cargando. |
| Número de sin leer en el icono de Actualizaciones | ⚪ | |
| Ordenar etiquetas | ⚪ | |

### Explorar / fuentes
| Opción | Prioridad | Nota |
|---|---|---|
| Fuente local (una carpeta del móvil como fuente con capítulos) | 🟡 | Hoy los .cbz son cómics sueltos. |
| Categorías de fuentes | ⚪ | Hay ancladas. |
| Esconder la pestaña Novedades / ponerla primera | ⚪ | |
| Instalador a elegir (Shizuku) | ⚪ | Hay sistema y privado. |

### Seguimiento
| Opción | Prioridad | Nota |
|---|---|---|
| Abrir el seguimiento al añadir a la biblioteca | 🟡 | |
| Subir el progreso también al marcar leído a mano | 🟡 | Hoy solo al leer. |
| Traer el progreso desde el servicio (sincronizar hacia la app) | 🟡 | |
| Puntuación, estado y fechas en la hoja de seguimiento | 🟡 | La búsqueda no encontró puntuación. |
| Shikimori, Bangumi | ⚪ | |
| Komga, Kavita, Suwayomi (seguimiento «mejorado») | ⚪ | |
| MangaDex (MDList, seguir, similares) | ⚪ | |

### Datos, seguridad y avanzado
| Opción | Prioridad | Nota |
|---|---|---|
| Sincronización entre dispositivos (Google Drive / SyncYomi / WebDAV) | 🟡 | Hay copias en WebDAV, no fusión automática. |
| Uso de almacenamiento por categoría/fuente | 🟡 | |
| Limpiar datos de mangas que ya no están en la biblioteca | 🟡 | Fichas, conocidos e historial se acumulan. |
| Vaciar la caché de capítulos al abrir | ⚪ | |
| Exportar la lista de la biblioteca (CSV) | ⚪ | |
| Esconder el contenido de las notificaciones | ⚪ | |
| Agente de usuario configurable | 🟡 | Ayuda con Cloudflare cuando Chrome cambia de versión. |
| Restablecer los ajustes de lector por serie | ⚪ | |
| Igualar títulos de la biblioteca con la fuente | ⚪ | |
| Compartir registros de fallos, registro detallado | ⚪ | Hay pantalla de fallos. |

Ya están: bloqueo, pantalla segura, incógnito, DoH, cookies, datos de WebView (0.72), revocar confianza (0.72), copias automáticas.

### Apariencia y app
| Opción | Prioridad | Nota |
|---|---|---|
| Idioma de la app | ⚪ | Solo español. |
| Formato de fecha y fechas relativas configurables | ⚪ | |
| Interfaz para tablet (dos paneles) | 🟡 | Por la tablet Huawei. |
| Widget de inicio con las actualizaciones | ⚪ | |
| Atajos de la app (mantener pulsado el icono) | ⚪ | |
| Imágenes en la descripción del manga | ⚪ | |
| Discord Rich Presence | ⚪ | |
| Integración E‑Hentai/ExHentai | ⚪ | |

## 2. Código que se puede copiar casi tal cual (Apache‑2.0 → GPL‑3.0, compatible)

| Archivo de Komikku/Mihon | Para qué | Esfuerzo |
|---|---|---|
| `core/common/.../util/system/ImageUtil.kt` (810 líneas) | Dividir imágenes altas, detectar apaisadas, tipo por cabecera, GIF/WebP animado, fondo automático | Medio: es Android puro, casi sin dependencias |
| `domain/.../chapter/service/MissingChapters.kt` (49 líneas) | Huecos entre capítulos; sustituye a `huecoCapitulos` del visor | Bajo |
| `core-metadata` (ComicInfo) | `ComicInfo.xml` en los .cbz | Bajo, pero usa kotlinx‑serialization XML |
| `data/track/shikimori`, `bangumi` | Dos servicios más | Medio: hay que adaptarlos a `Seguidores` |
| `core/common/.../util/storage/DiskUtil.kt` | Espacio libre, nombres de archivo válidos | Bajo |
| `presentation-widget` | Widget de actualizaciones | Alto: usa Glance y la base de datos de Mihon |
| `eu/kanade/tachiyomi/data/sync` | Sincronización | Alto: depende de su base de datos; habría que reescribir sobre las copias .tachibk |
| `ui/reader/viewer/ViewerNavigation` y navegaciones | Zonas de toque exactas | Ya hay equivalentes |

Hecho en 0.72: revisión agrupada por fuente (5 fuentes a la vez, cada una en serie), como LibraryUpdateJob.

Ya copiados: source‑api, red (Cloudflare, límites, DoH), ChapterRecognition, FetchInterval, SyncChaptersWithSource (lógica), ExtensionLoader (privadas), formato .tachibk.

## 3. Qué se puede optimizar

| Qué | Cómo lo hace Komikku | Ganancia |
|---|---|---|
| Decodificación de páginas | Librería nativa `tachiyomi image-decoder` + `SubsamplingScaleImageView`: decodifica por teselas y soporta AVIF/JXL/HEIF | Menos memoria en páginas grandes y formatos que hoy pueden fallar. Es el cambio más grande del lector |
| Portadas | Coil con caché de disco y tamaño exacto | Shiori usa caché propia (512 px); medir antes de cambiar |
| Biblioteca y estado | Base de datos SQLite (SQLDelight) con consultas | Shiori guarda JSON y archivos por manga; con 6500 mangas restaurar tarda ~29 s y crear copia ~40 s en el emulador. Migrar es un trabajo grande |
| Descargas | Caché de descargas en memoria con renovación | Shiori ya usa un conjunto en memoria |
| `press3D`/`pressableCon` con `composed {}` | — | Pendiente desde el paso 9: pasar a `Modifier.Node` |

## 4. Orden sugerido
1. Categoría por defecto + categorías incluidas/excluidas de la revisión.
3. Seguimiento: progreso al marcar a mano, puntuación/estado, traer progreso.
4. `ImageUtil`: dividir altas al descargar, GIF animado, páginas apaisadas.
5. Limpiar datos huérfanos y uso de almacenamiento.
6. Decodificador nativo por teselas.
7. Sincronización sobre WebDAV.
