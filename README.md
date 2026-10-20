# Shiori

Lector de cómics y manga hecho con Kotlin y Jetpack Compose, con las funciones de
[Komikku](https://github.com/komikku-app/komikku) (fork de Mihon/Tachiyomi):

- **Extensiones** de Mihon/Komikku (listados, instalar, confiar, ajustes y filtros de cada fuente).
- **Explorar**: fuentes, novedades, extensiones, migración y **búsqueda global**.
- **Biblioteca**: categorías (también ocultas), agrupar por fuente o estado, filtros, orden, insignias,
  búsqueda con operadores y selección múltiple.
- **Visor**: paginado ← / →, vertical, webtoon y continuo, zonas de toque, zoom, recorte de bordes,
  páginas dobles, autodesplazamiento, teclas de volumen, filtros de color.
- **Actualizaciones en segundo plano** con notificaciones, **cola de descargas** persistente.
- **Historial**, modo incógnito, **seguimiento con AniList** y recomendaciones.
- **Copias de seguridad** manuales y automáticas.
- Temas claro / oscuro / oscuro puro / sistema, acento propio o según la portada.
- Bloqueo de la app, pantalla segura y DNS sobre HTTPS.
- Archivos locales: .cbz / .zip y carpetas de imágenes.

## Compilar
GitHub Actions compila el APK en cada push (`.github/workflows/build.yml`); descárgalo en
*Actions → último run → Artifacts*. En local: `gradle assembleDebug` (Gradle 8.9, JDK 17).

Más detalles para desarrolladores en `HANDOFF.md`; comparación con Komikku en `FUNCIONES_KOMIKKU.md`.

Fuente tipográfica: Inter, © The Inter Project Authors, licencia SIL Open Font License 1.1
(https://openfontlicense.org). Partes del motor de fuentes vienen de Mihon/Komikku (Apache-2.0).
