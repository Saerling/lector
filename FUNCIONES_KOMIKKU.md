# Lector vs. Komikku — qué tenemos y qué falta

Fuente de la lista de Komikku: README oficial de https://github.com/komikku-app/komikku (sección *Features*, consultada el 2026-10-18).
Fuente de lo nuestro: el código de este proyecto. **Nada de esto está compilado ni probado en un móvil todavía.**

Leyenda: ✅ lo tenemos · 🟡 parcial · ❌ falta

## Funciones de Mihon/Tachiyomi (base de Komikku)
| Función de Komikku | Estado | Notas |
|---|---|---|
| Leer online desde fuentes | ✅ | Explorar → ficha → lector (`FuenteManga.kt`). Ejecuta las extensiones de confianza. |
| Leer contenido descargado | ✅ | Descargas por capítulo (.cbz); el lector online las usa sin conexión. También .cbz/carpetas locales. |
| Lector configurable (visores, direcciones) | 🟡 | Online: tira vertical / paginado → / paginado ←, pantalla siempre encendida, precarga de 3 páginas y páginas muy altas cortadas en tiras. Local: ajustes de brillo, tinte, fondo, orientación. Faltan más ajustes para el lector online (zoom, brillo, recorte de bordes). |
| Seguimiento (trackers) | 🟡 | Solo **AniList**. Faltan MyAnimeList, Kitsu, MangaUpdates, Shikimori, Bangumi. |
| Categorías para organizar la biblioteca | ✅ | Con pantalla «Editar categorías» (arrastrar, renombrar, ocultar, borrar). |
| Temas claro / oscuro | 🟡 | Solo esquema oscuro (`darkColorScheme`). Hay color de acento y color según la portada. |
| Programar la actualización de la biblioteca | 🟡 | Pestaña **Actualizaciones**: revisa al abrirla (cada 6 h) o con el botón. No hay revisión en segundo plano ni notificaciones. |
| Copias de seguridad | 🟡 | Archivo .json manual. **Ahora la copia incluye los mangas en línea** (fuente, dirección, progreso y categorías; la portada se vuelve a bajar). Faltan copias automáticas/en la nube y el historial/capítulos leídos. |
| Botón «continuar leyendo» en la biblioteca | 🟡 | «Continuar leyendo» solo para cómics locales; en mangas online, «Reanudar» en la ficha. |

## Descargas (Mihon)
| Función | Estado | Notas |
|---|---|---|
| Cola de descargas | 🟡 | **Nuevo:** máximo 2 capítulos a la vez, el resto espera y se puede cancelar. Sin pausa ni persistencia al cerrar la app. |
| Descargar siguientes / no leídos / todos | ✅ | **Nuevo:** menú de descarga junto a «Capítulos» en la ficha. |
| Borrar al leer, descargar al leer | ❌ | |

## Funciones propias de Komikku
| Función | Estado | Notas |
|---|---|---|
| Sugerencias (recomendaciones de la fuente) | ❌ | |
| Categorías ocultas | ✅ | |
| Color automático según la portada | ✅ | Ficha online, detalle local y lector. |
| Tema propio con paletas | 🟡 | Solo color de acento. |
| Añadir varios a la biblioteca de golpe | ❌ | |
| Icono de fuente/idioma en la biblioteca | ❌ | |
| Feed (novedades de varias fuentes a la vez) | 🟡 | Pestaña «Novedades» de Explorar (una fuente). No hay feed multi‑fuente. |
| Entradas agrupadas en Actualizaciones | ❌ | Se agrupan por día, no por manga. |
| Notificación de actualización con portada | ❌ | |
| Sincronización en ambos sentidos con trackers | 🟡 | AniList (sube progreso). |
| Búsquedas guardadas | ❌ | |
| Portada panorámica | ❌ | |
| Combinar varias entradas | ❌ | |
| Migración de fuente (con selección por rango) | ❌ | La pestaña «Migrar» de Explorar es solo un aviso vacío. |
| Activar/desactivar listados con icono | ✅ | Ocultar/mostrar listado; icono por listado. |
| Pantalla de errores de actualización | ❌ | La revisión solo avisa «N no se pudieron revisar». |
| Pantalla «qué se va a actualizar» | ❌ | |
| Buscar fuentes + filtro NSFW | ✅ | Buscar fuentes/extensiones y filtro +18 en Explorar. |
| Pulsación larga para añadir/quitar de la biblioteca en cualquier sitio | ❌ | Solo desde la ficha (corazón). |
| Banner de progreso (biblioteca/copia) | 🟡 | Progreso de revisión en Actualizaciones; anillo en cada descarga. |

## Funciones de TachiyomiSY (Komikku las incluye)
| Función | Estado |
|---|---|
| Detección automática de webtoon | 🟡 solo en cómics locales (`Cbz.esWebtoon`) |
| Recomendaciones (MAL/AniList) | ❌ |
| Filtro «lewd», filtro de seguimiento | ❌ |
| Categorías propias para fuentes | 🟡 (fuentes fijadas) |
| Editar la info del manga | ❌ |
| Ver/compartir/guardar la portada | ❌ |
| Categorías dinámicas (agrupar biblioteca por fuente, estado…) | ❌ |
| Fondo inteligente en el lector | ❌ |
| Autodesplazamiento (autoscroll) | ❌ |
| Precarga de páginas / tamaño de caché configurables | 🟡 (precarga fija de 3 páginas; caché de imágenes en disco de ~120 MB fija; no configurables) |
| Importar fuentes/extensiones en lote | ❌ |
| Ajustes avanzados de fuentes (activar/desactivar todas) | 🟡 |
| Pulsar etiqueta = buscar | ❌ |
| Ordenar la biblioteca arrastrando | 🟡 (categorías sí; entradas no) |
| Buscador de biblioteca con operadores | 🟡 (búsqueda por texto simple) |

## Orden sugerido para acercarnos
1. Compilar y probar lo hecho (ver `HANDOFF.md`).
2. Descargas: cola con pausa, «descargar siguientes N», borrar al leer.
3. Pulsación larga = añadir/quitar de la biblioteca (Explorar, listas) y selección múltiple.
4. Migración de fuente (la pestaña ya existe, vacía).
5. Más seguimiento (MyAnimeList, Kitsu…) y sincronización en ambos sentidos.
6. Actualizaciones: segundo plano (WorkManager), notificaciones, agrupar por manga, pantalla de errores.
7. Sugerencias, búsquedas guardadas, feed multi‑fuente.
8. Copia de seguridad que incluya la biblioteca en línea.
