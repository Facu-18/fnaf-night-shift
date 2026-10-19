# FNAF: NIGHT SHIFT

Mod de terror para **Minecraft Java 1.20.1 (Fabric)**. Sobreviví siete noches en un complejo
industrial abandonado —una vieja pizzería, depósitos, salas técnicas y túneles— vigilando las
cámaras desde una oficina vulnerable, saliendo a buscar herramientas y reparando instalaciones
mientras los animatrónicos te buscan por el mapa.

> Proyecto de fans sin fines comerciales. *Five Nights at Freddy's* y sus personajes pertenecen a
> Scott Cawthon. El código, los modelos, las texturas y los sonidos sintetizados del mod son
> originales. Los sonidos de FNAF opcionales (ver §10) son propiedad de ScottGames.

**Versión actual: 1.2.0** — ver [CHANGELOG.md](CHANGELOG.md).

---

## 1. Dependencias

| Componente | Versión | ¿Obligatorio? |
|---|---|---|
| Minecraft Java Edition | 1.20.1 | Sí |
| Fabric Loader | 0.15.0 o superior (probado con 0.16.14) | Sí |
| Fabric API | 0.92.6+1.20.1 o superior para 1.20.1 | Sí |
| GeckoLib (Fabric) | 4.4.9 para 1.20.1 | Sí |
| Java | 17 | Sí |
| Iris + Sodium | Iris 1.7.6 / Sodium 0.5.13 (1.20.1) | No (shaders, ver §11) |
| Essential | 1.5.1 Fabric 1.20.1 | No (ver §12) |

La 1.1 **no añade dependencias obligatorias**. SecurityCraft se evaluó para las cámaras, pero sólo
existe para Forge/NeoForge (no hay versión Fabric), así que el mod usa su propio sistema de cámaras.

Para **compilar** sólo hace falta un JDK 17: Gradle 8.8 y Fabric Loom 1.7 se descargan solos.

## 2. Compilar

```bash
# Windows (PowerShell)
.\gradlew.bat build
# Windows (CMD)
gradlew.bat build
# Linux / macOS
./gradlew build
```

> En PowerShell hay que escribir `.\` delante (`.\gradlew.bat ...`); si no, aparece
> "El término 'gradlew.bat' no se reconoce...". En las tareas de abajo, anteponé `.\` igual.

El mod queda en `build/libs/fnaf-night-shift-1.2.0.jar`.

Para **publicar** el mod sin los sonidos de FNAF (con derechos de ScottGames):
`.\gradlew.bat build -PnoFnafSounds` — el juego usa entonces los sonidos sintetizados originales.

| Tarea | Para qué sirve |
|---|---|
| `runClient` | Abre Minecraft con el mod (entorno de desarrollo). |
| `runServer` | Servidor dedicado de desarrollo (pide aceptar el EULA en `run/eula.txt`). |
| `runGametest` | Ejecuta las pruebas automáticas en un servidor sin interfaz (7 pruebas). |
| `runDevShots` | Herramienta de desarrollo: abre `run/saves/FNAF_Test` y guarda capturas en `run/screenshots`. |
| `runDevShotsShaders` / `runDevShotsEssential` | Lo mismo en `run-shaders/` (Iris) o `run-essential/` (Essential). |

## 3. Instalar (jugar)

1. Instalá **Fabric Loader** para 1.20.1 (<https://fabricmc.net/use/>).
2. Copiá en la carpeta `mods` de tu `.minecraft`: `fnaf-night-shift-1.2.0.jar`, Fabric API 1.20.1 y GeckoLib Fabric 4.4.x 1.20.1.
3. Abrí el perfil de Fabric 1.20.1.

**Servidor:** los mismos tres `.jar` en la carpeta `mods` del servidor Fabric; cada jugador también necesita el mod.
**Partidas de la 1.0:** se pueden seguir usando; al empezar la primera noche el complejo se actualiza solo (§5).

## 4. Primeros pasos (sin comandos)

1. Creá un mundo con trucos activados (zona llana; en superplano subí ~10 bloques: hay túneles 8 bloques bajo el suelo).
2. Pulsá **N** (menú de noches) → **Generar complejo aquí**. Se construye con tu posición como esquina noroeste
   (100 × 92 bloques) y se **niega** a construir si hay bloques que no sean terreno natural.
3. Pasá a supervivencia. En el mismo menú: **Continuar** (o elegí una noche desbloqueada).

También se abre el menú haciendo clic derecho en el **Núcleo** de la oficina. Los comandos `/fnaf` siguen
existiendo como herramientas de depuración, pero no hacen falta para jugar.

## 5. Cómo se juega (1.1)

### Reglas de la noche
- **Objetivo:** tener todas las reparaciones obligatorias hechas **cuando suenen las 6:00 AM** y seguir con vida.
  Completar los objetivos antes **no termina la noche**: hay que aguantar hasta las 6.
- A las 6:00 con objetivos pendientes → **derrota**. Si te atrapan (o atrapan a todo el equipo) → **derrota**.
- Durante la noche aparecen **averías adicionales** (ver abajo), que se suman a los objetivos obligatorios.

### La oficina (antes "refugio")
- Es el centro de la partida: monitor de cámaras, panel de ventilación, recarga de la linterna.
- **No es totalmente segura.** Los animatrónicos acumulan "presión" sobre la oficina (más rápido si hay jugadores
  dentro y aún más si alguien está mirando las cámaras), recorren los pasillos hasta una de sus 4 puertas y acechan.
  - **Puerta abierta:** entran y atacan.
  - **Puerta cerrada:** la golpean. Si insisten lo suficiente (menos golpes en noches avanzadas) la **fuerzan**: queda
    abierta y **atascada** hasta reparar un *control de puerta automática*.
  - Si te encerrás a tiempo, se cansan y se van.
- Señales previas: respiración junto a la puerta, golpes, aviso "⚠ Algo está en la puerta OESTE" en la interfaz y
  lo que muestren las cámaras. Por el dintel abierto sobre cada puerta se ve (y te ven).
- Cada uno conserva su identidad: **Bonnie** llega por el **oeste**, **Chica** por el **este**, **Freddy** por la puerta más
  cercana y con más insistencia, **Foxy** no acecha: embiste y, si la puerta está cerrada, la golpea con fuerza y vuelve a su cala.
  Golden Freddy sigue siendo un evento secreto.

### Cámaras (sistema propio)
- Clic derecho en un **Monitor de cámaras** (2 en la oficina, 1 en la sala de seguridad).
- 14 cámaras: escenario, comedor, cala pirata, pasillos y accesos a la oficina, cocina, central eléctrica, depósito,
  almacén y túneles. Se ve el mundo real con los animatrónicos en su posición real.
- `A/D` o flechas: cambiar · arrastrar el ratón: girar · `ESC`: salir. Minimapa con tu posición.
- **Cámara averiada:** "SIN SEÑAL" (es una instalación reparable con destornillador). **Avería de seguridad:** interferencias en todas.
- Mientras mirás las cámaras tu cuerpo sigue en la oficina: los animatrónicos aprovechan esa distracción.

### Ventilación
- El panel de la oficina debe **reiniciarse** antes de que venza su temporizador (240 s en la noche 1 → 120 s en la 6;
  configurable). Indicador en la interfaz, avisos a los 30 y 10 s.
- Si falla: **náuseas** (distorsión), **oscuridad** (visibilidad reducida) y **visión borrosa** hasta que alguien la reinicie.
  Todo es temporal y reversible; no bloquea el control.
- Se integra con las instalaciones de ventilación existentes: si alguna está averiada el temporizador corre más rápido,
  y cada 3 fallos se avería una (nueva reparación obligatoria, si queda tiempo).

### Averías adicionales
Una instalación operativa se rompe, pasa a ser obligatoria (⚠ en la lista) y tiene una **consecuencia** hasta que se repara:

| Avería | Consecuencia |
|---|---|
| Generador / caja de fusibles / tablero | Las luces de la oficina fallan |
| Control de puerta automática | Una puerta de la oficina queda atascada abierta |
| Ventilación | El temporizador de ventilación se acorta y corre más rápido |
| Equipo de seguridad / terminal / cableado | Interferencias en todas las cámaras |
| Iluminación | Las lámparas de esa zona parpadean |
| Cámara | Esa cámara pierde la señal |

Nunca se programan si queda menos tiempo que la ventana de respuesta de la noche, como máximo 2 pendientes a la vez
y nunca dos veces la misma instalación en una noche: la noche siempre se puede completar.

### Herramientas y recursos
- Cada noche empezás **sólo con la linterna** (batería llena). Lo que sobró de la noche anterior se retira
  (inventario y objetos tirados en el complejo), así reiniciar no acumula ni duplica objetos.
- Las herramientas están en **casilleros** (10 en el mapa). En noches tempranas aparecen en zonas cercanas; en las
  avanzadas, en sectores peligrosos (central eléctrica, túneles, sala técnica A, escenario). Hay una copia de cada
  herramienta por jugador (máx. 3) y cada casillero entrega una sola vez por noche.
- Llave inglesa: generadores, puertas automáticas, ventilación · Destornillador: fusibles, tableros, seguridad,
  cámaras · Alicate: cableado, iluminación. Repuestos en las cajas de suministros.
- Si un mapa propio no tiene casilleros, se entrega el kit completo para que la noche sea completable.

### Estamina
- Correr la consume (≈10 s de carrera), caminar la recupera despacio y quedarse quieto, rápido.
- Agotado: no podés correr hasta recuperar un 35 %, respirás con fuerza (hace ruido) y los animatrónicos te detectan
  desde más lejos y te alcanzan algo más rápido. Cada jugador tiene la suya.

### Oscuridad y tensión
- Las lámparas titilan cuando pasa cerca un animatrónico que persigue, se acerca a la oficina o acecha: es un aviso.
- Desde la noche 3, alucinaciones: destellos de la cara de un animatrónico y pasos metálicos que se acercan por detrás.
- En cada noche una parte creciente de las lámparas de las zonas peligrosas se funde o falla (nunca las de la oficina).
- Latidos cuando un animatrónico está cerca (más rápidos si te persigue), interferencias, golpes lejanos, silencios
  inquietantes en el zumbido industrial y la pista de ambiente cada 2–4 minutos (no en bucle).
- Antes de algunos encuentros hay avisos: pasos corriendo antes de la embestida de Foxy, respiración en las puertas.

## 6. Comandos (`/fnaf`, nivel de permiso 2) — opcionales

| Comando | Acción |
|---|---|
| `/fnaf status` | Estado de la campaña (cualquier jugador). |
| `/fnaf night start [1-7]` · `stop` · `restart` · `set <1-7>` · `time <s>` | Control de noches. |
| `/fnaf difficulty <facil\|normal\|dificil\|pesadilla>` | Dificultad del mundo. |
| `/fnaf summon <animatrónico> [pos]` · `/fnaf despawn` | Pruebas de IA. |
| `/fnaf reset` · `/fnaf kit` | Progreso / kit completo. |
| `/fnaf event <golden_freddy\|blackout\|distant_noise>` | Eventos especiales. |
| `/fnaf repairs list` · `complete` | Instalaciones. |
| `/fnaf map check` · `generate` · `upgrade` | Mapa (upgrade = aplicar la actualización 1.1 al complejo existente). |
| `/fnaf vent <segundos>` | Ajusta el temporizador de ventilación (pruebas). |
| `/fnaf breakdown` | Fuerza una avería adicional (pruebas). |
| `/fnaf config reload` | Recarga la configuración. |

Teclas: **N** abre el menú de noches (configurable en *Opciones → Controles → FNAF: Night Shift*).

## 7. Configuración

`config/fnaf_night_shift.json` (`/fnaf config reload` para aplicar). Claves de la 1.0 sin cambios, más:

| Clave | Por defecto | Descripción |
|---|---|---|
| `starterTools` | false | true = se entregan todas las herramientas al empezar (comportamiento 1.0). |
| `resetInventoryEachNight` | true | Retirar herramientas y repuestos al empezar cada noche. |
| `playersCanControlNights` | true | Cualquier jugador puede iniciar/reiniciar desde el menú (dificultad y mapa: sólo anfitrión/admin). |
| `officePressureMultiplier` | 1.0 | Presión de los animatrónicos sobre la oficina. |
| `doorStrength` | 12 | Golpes para forzar una puerta en la noche 1 (baja 1 por noche, mínimo 4). |
| `ventilationEnabled` / `ventIntervalMultiplier` | true / 1.0 | Sistema de ventilación. |
| `extraBreakdowns` | true | Averías adicionales. |
| `staminaEnabled`, `staminaMax`, `staminaDrainPerSecond`, `staminaRegenWalkingPerSecond`, `staminaRegenRestingPerSecond`, `staminaRecoverThreshold` | true, 100, 10, 6, 13, 35 | Estamina. |
| `maxDarkness` | 0.7 | Fracción máxima de lámparas fundidas/averiadas (noche 7). |
| `horrorFilter` | true | Filtro de terror integrado durante las noches. |
| `hallucinationsFromNight` | 3 | Noche a partir de la cual hay alucinaciones (8 = nunca). |
| `animatronicLightInterference` | true | Las lámparas titilan al paso de los animatrónicos. |
| `night7VentSeconds` / `night7ExtraBreakdowns` | 100 / 5 | Noche 7 personalizable. |

(Claves de la 1.0: `nightDurationSeconds`, `gracePeriodSeconds`, `defaultDifficulty`, reparaciones, agresividad,
`detectionRange`, daños, linterna, `shelterRadius`, `giveStarterKit`, Golden Freddy, apagones y `night7*`.)

### Progresión por noche

| Noche | Animatrónicos | Ventilación | Averías extra (máx.) | Golpes para forzar | Oscuridad |
|---|---|---|---|---|---|
| 1 | Bonnie | 240 s | 1 | 12 | 17 % |
| 2 | + Chica | 210 s | 1 | 11 | 26 % |
| 3 | + Foxy | 180 s | 2 | 10 | 35 % |
| 4 | + Freddy | 160 s | 2 | 9 | 44 % |
| 5 | todos (1.2×) | 140 s | 3 | 8 | 52 % |
| 6 | todos (1.4×) | 120 s | 4 | 7 | 61 % |
| 7 | configurable | 100 s | 5 | 6 | 70 % |

(Dificultad Normal; la dificultad modifica intervalos, agresividad y resistencia de las puertas.)

## 8. Guía para probar las siete noches

Preparación: **N → Generar complejo aquí** → supervivencia → **N → Continuar**.
Atajos de prueba: `/fnaf night time 60`, `/fnaf repairs complete`, `/fnaf vent 5`, `/fnaf breakdown`.

| Noche | Qué comprobar |
|---|---|
| **1** | Empezás en la oficina sólo con la linterna. Tutorial. Buscá herramientas en los casilleros (depósito de herramientas, sala de seguridad). Mirá las cámaras en el monitor. Reiniciá la ventilación antes de que venza. Completá las reparaciones y **esperá hasta las 6 AM** dentro de la oficina con las puertas cerradas. |
| **2** | Chica se acerca por la puerta este; Bonnie por la oeste. Cerrá la puerta al oír respiración / ver el aviso. Dejá una abierta para ver cómo entran. |
| **3** | Foxy: vigilá la CAM 1C (cala pirata). Al oír pasos corriendo, cerrá la puerta o alumbralo con la linterna. |
| **4** | Freddy: insiste en las puertas; mirar las cámaras mucho tiempo aumenta la presión. Probá a quedarte sin estamina en una persecución. |
| **5** | Más averías adicionales y apagones; herramientas más alejadas. `/fnaf event golden_freddy` para el secreto. |
| **6** | Ventilación cada 2 minutos, puertas que ceden antes. Dejá pasar el tiempo con objetivos pendientes (`/fnaf night time 10`) → derrota. |
| **7** | Desafío configurable (`night7*`): caja de música, extras de Foxy y Freddy, averías y apagones frecuentes. |

Multijugador: repartíos las tareas (uno en cámaras, otro buscando herramientas). Si atrapan a un jugador, queda cegado en la
oficina y no puede reparar; la noche sólo se pierde cuando atrapan a todos.

## 9. Pruebas realizadas (1.1)

**Automáticas en un servidor real (`gradlew runGametest`): 7/7 superadas** (1.2: Bonnie, Chica y Freddy cruzan una puerta
abierta de la oficina en 44–56 ticks), incluidas las de 1.0 y 1.1:
- Mapa actualizado: 14 cámaras, 10 casilleros, monitores, panel de ventilación, dinteles de 3 bloques, 4 puertas de oficina.
- El menú respeta noches bloqueadas e inicia noches con la lógica existente.
- Empezar sólo con linterna; herramientas viejas retiradas; casilleros abastecidos (3 con 1 jugador, 6 con 2); un casillero no duplica.
- Ventilación: fallo → náuseas + oscuridad; reinicio → efectos retirados y temporizador restaurado.
- Estamina: correr agota, agotado no corre, descansar recupera; es independiente entre dos jugadores.
- Avería adicional con estado real, obligatoria y consecuencia revertida al repararla.
- Puerta cerrada forzada a golpes, abierta y atascada.
- **Bonnie camina por el pasillo hasta la puerta forzada, entra en la oficina y atrapa al jugador** → derrota.
- 6 AM: con todo reparado la noche continúa; al llegar las 6 → victoria; con pendientes → derrota.
- Multijugador (2 jugadores simulados): reparación compartida cuenta una vez, cámaras por jugador, atrapar a uno no termina la
  noche, atrapar a ambos sí.

**En el cliente real (capturas en `docs/screenshots/`):** menú de noches, vista de cámaras con Bonnie visible en la CAM 1A,
cámara sin señal, aviso de puerta acechada, entrada de Freddy por la puerta, fallo de ventilación, casilleros, estamina agotada,
susto de Foxy. Sin errores del mod en el registro; el paquete de sonidos de FNAF cargó.

**Partida guardada de la 1.0:** se compiló la 1.0 desde su ZIP, se generó un mundo y se abrió con la 1.1: carga sin errores y al
iniciar la primera noche el complejo se actualizó solo (32 cambios); cámaras y casilleros funcionan.

## 10. Sonidos

- Todos los eventos tienen un sonido **original sintetizado** (`tools/gen_sounds.py`), procesado para que se oiga también en
  parlantes y auriculares comunes (los graves puros no se reproducen en ellos).
- Los gritos de los sustos los reproduce el cliente sin posición, por el canal maestro: suenan siempre.
- **Sonidos de FNAF** (proporcionados para el proyecto): paquete integrado **activado por defecto** y desactivable en
  *Opciones → Paquetes de recursos* ("FNAF Night Shift: sonidos de FNAF"). Sustituye: ambiente (fragmento de 90 s de
  `ambience.mp3`), cambio de cámara (`cam_sound.mp3`), susto general (FNAF 1 `xscream`), susto de Foxy (FNAF 2 `xscream3`) y
  susto de Golden Freddy (UCN `fredbear_scream`). Origen: carpetas `PyNightsAtFredbears-main` y `fnaf-jumpscare-extension-main`
  (sus créditos indican que pertenecen a ScottGames). **No redistribuir**; para publicar: `-PnoFnafSounds`.
- Reimportar: `py tools/import_fnaf_sounds.py <PyNightsAtFredbears-main> <fnaf-jumpscare-extension-main>`.
- Sin archivo de FNAF disponible (quedan sintetizados, sustituibles con `.ogg` mono del mismo nombre): interferencias de cámara,
  ventilación (aviso, fallo, reinicio), latidos, respiración, golpes en puertas, acecho, alarma de avería, casilleros, pasos.

## 11. Terror visual y shaders

### Filtro de terror integrado (sin instalar nada)
Durante las noches el mod aplica su propio post-procesado: niebla oscura por distancia, colores fríos y apagados, sombras
más negras, viñeta, grano, aberración cromática y parpadeos. Tiene 3 intensidades (calma / algo cerca / persecución, con
pulso rojo) y una variante de visión ondulada cuando falla la ventilación. No se aplica al mirar las cámaras. Se desactiva
solo si hay un paquete de shaders de Iris activo, o con `horrorFilter: false`.

### Paquete de shaders de terror (Iris / OptiFine)
`FNAF_NightShift_Horror.zip` (en `dist/`, fuente en `shaderpack/`) es un paquete **original** hecho para el mod: cielo negro,
niebla negra densa (a ~20 bloques ya no se ve), gradación fría, viñeta, grano, aberración cromática y parpadeo eléctrico; los
ojos brillantes y las lámparas atraviesan la niebla. Instalación: copiar el `.zip` a `.minecraft/shaderpacks` y elegirlo en
*Opciones → Video → Paquetes de shaders* (requiere Iris + Sodium). Opciones: densidad de niebla, grano, viñeta, aberración y
parpadeo. Probado con Iris 1.7.6 + Sodium 0.5.13 (sin errores de compilación, capturas en `docs/screenshots/horror_shaders/`).
Es intencionadamente muy oscuro: usá la linterna o bajá la densidad de niebla.

### Otros paquetes de shaders

Probado con **Iris 1.7.6 + Sodium 0.5.13 y Complementary Reimagined r5.9.3** (`runDevShotsShaders`, capturas en
`docs/screenshots/shaders/`): modelos y texturas de los animatrónicos, ojos brillantes, animaciones, lámparas, interfaz de
cámaras, efectos de ventilación, susto y menú funcionan.
- La linterna usa luz de bloque real, que los shaders tratan como cualquier fuente de luz.
- Los efectos visuales del mod son superposiciones 2D y efectos de estado vanilla: no hay post-procesado propio que choque con shaders.
- **Limitación:** con shaders la vista de cámaras es más oscura (el aumento de brillo de las cámaras usa el gamma, que el
  shader ignora). Los ojos brillantes siguen delatando a los animatrónicos; alternativa: subir el brillo en la configuración
  del paquete de shaders o jugar las cámaras sin shaders.
- El registro muestra 3 errores "Unknown variable" del propio paquete Complementary (variables de Iris más nuevo), ajenos al mod.
- No se probaron otros paquetes de shaders ni OptiFine.

## 12. Multijugador y Essential

- El servidor es la autoridad: reloj, objetivos, averías, puertas, ventilación, IA, casilleros y victoria/derrota viven en el
  servidor y se sincronizan a cada cliente. Estamina y efectos visuales son por jugador.
- **Essential 1.5.1 (Fabric 1.20.1)** se probó cargado junto al mod: el juego arranca, abre el mundo y ejecuta todas las escenas
  sin errores del mod. **No se pudo probar** el alojamiento de partidas de Essential (requiere iniciar sesión con una cuenta de
  Microsoft real; el entorno de prueba usa una cuenta sin conexión). Como Essential aloja el mismo servidor integrado de
  Minecraft, no se espera ninguna incompatibilidad, pero no está verificado.
- No se probó con dos clientes reales conectados a la vez; la lógica multijugador se verificó en el servidor con dos jugadores simulados.

## 13. Limitaciones conocidas

- No hubo sesiones de juego humanas: el equilibrio (presión sobre la oficina, golpes por puerta, estamina, intervalos) puede requerir ajustes;
  todo es configurable.
- Los animatrónicos no trepan escaleras; los túneles los patrulla el que aparece allí.
- Se congelan si su chunk se descarga (lejos de todos los jugadores).
- El brillo extra de las cámaras no se aplica con shaders (ver §11).
- Mapas propios: hay que colocar cámaras, monitores, panel de ventilación y casilleros a mano (están en la pestaña creativa;
  Mayús + clic derecho en creativo cambia el nivel de riesgo de un casillero).

## 14. Estructura del proyecto

```
src/main/java/com/fnaf/nightshift/
  ai/        estados de IA (incl. APPROACH y LURK), máquina de estados, ruido
  block/     escenario, marcadores, cámara de seguridad, monitor, panel de ventilación, casillero
  campaign/  noches, estado persistente, eventos, oficina, averías, ventilación, estamina, cámaras,
             recursos, atmósfera, acciones del menú
  command/ config/ entity/ item/ network/ registry/ repair/
  world/     generador del complejo y actualización 1.1 (FacilityUpgrade)
src/client/java/.../client/  renderizado, HUD, tensión, pantallas (menú, cámaras, minijuegos, resultado)
src/main/resources/resourcepacks/fnaf_sounds/  paquete integrado de sonidos de FNAF (opcional)
src/gametest/   pruebas automáticas · src/devclient/  herramienta de capturas (no se incluyen en el .jar)
tools/          generadores de modelos, texturas, sonidos, vistas previas e importador de sonidos
docs/           vistas previas y capturas
```

Regenerar recursos: `py tools/gen_animatronics.py`, `py tools/gen_assets.py` (incluye los bloques 1.1),
`py tools/gen_sounds.py`, `py tools/preview_models.py`.
