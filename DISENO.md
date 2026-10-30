# Shiori 2 — sistema de diseño (mezcla Mochy + Material 3 Expressive)

Objetivo: que CADA pantalla se vea claramente nueva, moderna, fluida y orgánica. Cambios visibles, no retoques.
La lógica (datos, callbacks, navegación) NO se toca: solo la capa visual de cada pantalla.

## Piezas compartidas (ya existen, úsalas)
- `ui/Expresivo.kt`: `Muelles` (espacial 0.8/380, rápido 0.6/800, lento 0.8/200, efectos 1/1600), `BotonExpresivo`
  (RELLENO/TONAL/CONTORNO/TEXTO; esquinas que se cuadran al pulsar), `GrupoBotones` (botones conectados),
  `IndicadorCarga`, `DeslizadorExpresivo`.
- `ui/Unificado.kt`: `Mov` (respeta MovimientoReducido), `CapaEncima`, `DialogoAlerta`, `MenuShiori`, `CasillaShiori`,
  `CampoTextoShiori`, `itemsAnimados`/`itemsIndexedAnimados` (animateItem), `gestoAtras`.
- `ui/Estilo.kt`: `SuaveShape(r)` (squircle), `pressable`, `pressableCon(escala)`, `resaltable` (resalte redondeado),
  `press3D` (inclinación), `BonesHost/Bone/BonesRejilla` (esqueletos con ola), `Odometro`, `AvisosHost`.
- `ui/Movimiento.kt`: `Modifier.entrada(i)` (aparición escalonada 520 ms / 60 ms).
- `ui/Theme.kt`: `LectorColors`, `LectorType`. Acento: `MaterialTheme.colorScheme.primary`.
- `ui/Expresivo.kt` → `PantallaPlegable` (nuevo, ver abajo) para cabeceras.

## Reglas visuales
1. **Jerarquía tipográfica fuerte**: títulos de pantalla `LectorType.Display` (grande, negrita), títulos de sección
   20–22 sp Bold, cuerpo `LectorType.Body`, etiquetas `LectorType.Label` en `LectorColors.Muted`.
2. **Superficies por capas**: fondo `LectorColors.Bg`; tarjetas `LectorColors.Card` con `SuaveShape(24–28.dp)`;
   elementos sobre tarjeta `LectorColors.CardHigh`/`PillFill`. Sin bordes duros; si hace falta, `LectorColors.Linea` 1 dp.
3. **Acento con intención**: el elemento principal de cada pantalla (botón Leer, pestaña activa, progreso, insignias de
   no leídos) usa el acento lleno (`primary` / `onPrimary`); lo secundario, tonal (`primary.copy(alpha=0.16–0.22f)`).
4. **Formas expresivas**: lo pulsable cambia de forma al pulsar (BotonExpresivo, GrupoBotones) o se aplasta
   (`pressable`) / inclina (`press3D` en portadas). Nada de ondas (ripple) de Material.
5. **Movimiento**: aparición escalonada (`entrada(i)`) en listas y secciones la primera vez; `itemsAnimados` en listas
   que cambian; transiciones de contenido con `AnimatedContent` + `Muelles`; estados (seleccionado, expandido) con
   muelles, nunca saltos. Todo debe respetar `Mov.reducido` (ya lo hacen las piezas compartidas).
6. **Portadas**: siempre `SuaveShape(14–18.dp)`, proporción 2:3, con `press3D()` y `origenTransicion()` si abren la
   ficha (la ficha crece desde la portada). Degradado inferior para texto encima. Insignias como píldoras de acento.
7. **Densidad**: márgenes laterales 20–24 dp, separación entre secciones 24–32 dp, filas 64–72 dp.
8. **Vacíos y carga**: esqueletos con ola (`BonesHost`) en vez de spinners para contenido; `IndicadorCarga` solo
   para acciones. Estados vacíos con icono grande en círculo tonal + texto + `BotonExpresivo`.

## Pantallas (qué debe verse nuevo)
- **Inicio**: carrusel héroe a ~55 % de alto con «Seguir leyendo» (portada grande desenfocada de fondo + portada nítida +
  título grande + botón Leer de acento), auto-avance suave y paginado con indicador de puntos que se estira; debajo,
  secciones en filas horizontales con título 22 sp y «Ver todo».
- **Biblioteca**: pestañas de categorías como píldoras con indicador de acento que se desliza; tarjetas con esquinas
  grandes, insignia de no leídos en píldora de acento, barra de progreso fina en la base de la portada, título 2 líneas.
- **Actualizaciones / Historial**: filas tipo tarjeta agrupadas por día (cabecera de día grande y pegajosa si se puede),
  portada 56 dp, texto con jerarquía, acción a la derecha en botón tonal redondo; aparición escalonada.
- **Ficha**: cabecera inmersiva con la portada desenfocada a sangre (RenderEffect en API 31+, degradado al fondo),
  portada nítida grande con sombra, título 30 sp+, metadatos en píldoras, fila de acciones con BotonExpresivo RELLENO
  «Leer/Continuar» grande + botones tonales; capítulos como filas limpias con indicador de no leído (punto/acento) y
  progreso.
- **Explorar**: fuentes como tarjetas con icono grande y acciones tonales; catálogo en cuadrícula con portadas con
  degradado y título encima opcional; esqueletos al cargar.
- **Más / Ajustes**: cabecera con el logo y nombre; apartados como burbujas (ya hecho en Ajustes); filas con icono en
  círculo tonal.
- **Lector**: barras flotantes de cristal en píldora, slider Expressive (hecho), botones redondos tonales.
