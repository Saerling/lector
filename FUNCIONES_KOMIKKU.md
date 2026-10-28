# Lector vs. Komikku — qué tenemos y qué falta

Fuente de la lista de Komikku: README oficial de https://github.com/komikku-app/komikku (sección *Features*) y su código.
Estado del nuestro (2026-10-20, PASO 15): **compila en GitHub Actions; no probado aún en un móvil**.

Leyenda: ✅ lo tenemos · 🟡 parcial · ❌ falta

## Funciones de Mihon/Tachiyomi (base de Komikku)
| Función | Estado | Notas |
|---|---|---|
| Leer online desde fuentes (extensiones) | ✅ | Listados, instalar, confiar, ajustes y filtros de cada fuente. |
| Leer descargado / archivos locales | ✅ | Capítulos descargados, .cbz/.zip y carpetas. |
| Visor configurable | ✅ | Paginado ← / →, vertical, webtoon, continuo; zonas de toque; zoom; recorte de bordes; páginas dobles; filtros de color; modo por serie. |
| Seguimiento | 🟡 | Solo AniList (con recomendaciones). Faltan MAL, Kitsu, MangaUpdates… |
| Categorías | ✅ | Con ocultas, editar, asignar al añadir. |
| Temas claro / oscuro | ✅ | Sistema, oscuro puro, oscuro, claro + acento. |
| Actualizar biblioteca programado | ✅ | WorkManager con intervalo, Wi‑Fi / cargando, notificaciones, errores. |
| Copias de seguridad | ✅ | Propias (.json, manuales y automáticas) y **compatibles con Mihon/Komikku/SY (.tachibk)** en los dos sentidos. Sin nube. |
| Continuar leyendo en la biblioteca | ✅ | Botón sobre la portada. |
| Historial / incógnito | ✅ | |

## Descargas
| Función | Estado | Notas |
|---|---|---|
| Cola persistente con pausa, reordenar, reintentar | ✅ | En primer plano con notificación. |
| Descargar siguientes N / no leídos / todos | ✅ | Ficha y selección múltiple. |
| Solo Wi‑Fi, simultáneas, borrar al leer, descargar adelante | ✅ | Ajustes → Descargas. |

## Funciones propias de Komikku / TachiyomiSY
| Función | Estado | Notas |
|---|---|---|
| Recomendaciones | ✅ | AniList + «Parecidos» de la fuente (getRelatedMangaList o por género). |
| Categorías ocultas / dinámicas (agrupar por fuente, estado) | ✅ | |
| Color según la portada | ✅ | |
| Selección múltiple en biblioteca / ficha / actualizaciones | ✅ | |
| Insignias (sin leer, descargas, idioma, local) | ✅ | |
| Actualizaciones agrupadas por manga | ✅ | |
| Búsquedas guardadas | ✅ | Texto y filtros; también como filas de Novedades. |
| Búsqueda global | ✅ | |
| Migración de fuente | ✅ | Desde la ficha, la biblioteca o Explorar → Migrar. No mueve descargas. |
| Mantener pulsado = añadir / quitar de la biblioteca | ✅ | Navegar fuente y búsqueda global. |
| Editar información y portada del manga | ✅ | |
| Ver / compartir / guardar portada | ✅ | |
| Autodesplazamiento | ✅ | |
| Precarga configurable | ✅ | |
| Pulsar etiqueta = buscar | ✅ | |
| Buscador de biblioteca con operadores | ✅ | |
| Bloqueo de la app, pantalla segura, DNS sobre HTTPS | ✅ | |
| Aviso de actualizaciones de extensiones | ✅ | |
| Feed multi‑fuente | ✅ | Novedades con fuentes y búsquedas guardadas. |
| Combinar varias entradas (merge) | ❌ | |
| Portada panorámica | ❌ | |
| Sincronización en la nube | ❌ | |
| Extensiones privadas | ❌ | |
| Grupos de traducción ocultos | ✅ | Por manga, en Filtros de capítulos (0.22). |
| Descargador | ✅ | Por páginas, reanudable, un capítulo por fuente, reintentos (0.21). |
| Abrir enlaces de la web en la app | ✅ | Compartir enlace → fuentes de esa web → ficha o búsqueda global (0.23). |
| Ahorro de datos | ✅ | wsrv.nl (WebP 1080 px), Avanzado → Red (0.24). |
| Vista previa de páginas | ✅ | 8 miniaturas del primer capítulo, bajo pedido (0.24). |
