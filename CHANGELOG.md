# Cambios

## 1.2.0 — Puertas, sonidos y más terror

### Corregido
- **Los animatrónicos no cruzaban las puertas abiertas de la oficina.** La hoja de la puerta abierta deja 0,81 bloques de
  paso y el cuerpo de 0,7 rozaba el marco. Ahora miden 0,6 de ancho y, si quedan frenados junto a una puerta abierta, se
  alinean con el centro del marco (≤ ½ bloque, sin teletransporte). Prueba automática nueva: Bonnie, Chica y Freddy entran
  por una puerta abierta en 44–56 ticks (~2,5 s, contando el pasillo).
- **Los sustos no sonaban:** el grito se reproducía en la posición del jugador y en el mismo instante se lo llevaba a la
  oficina, así que sonaba lejos. Ahora lo reproduce el cliente, sin posición, con el grito propio de cada animatrónico.
- **Sonidos inaudibles:** varios sonidos sintetizados eran casi todo graves que no reproducen parlantes ni auriculares
  comunes (latido 0 % audible, zumbido 0,2 %, pasos 21 %, golpes 19 %, carrera de Foxy 15 %). Se reprocesaron con armónicos
  audibles y volumen normalizado (latido 28 %, pasos 76–84 %, golpes 63 %, carrera 68 %), y se subieron los volúmenes de
  reproducción. Los pasos de los animatrónicos se oyen desde más lejos.

### Nuevo
- **Filtro de terror** integrado (sin Iris): niebla oscura por distancia, desaturación fría, viñeta, grano, aberración
  cromática, parpadeos y pulso rojo, con 3 intensidades según el peligro y visión ondulada si falla la ventilación.
  Se desactiva solo con un paquete de shaders de Iris. Configurable (`horrorFilter`).
- **Paquete de shaders de terror original** para Iris/OptiFine: `FNAF_NightShift_Horror.zip` (niebla negra densa, cielo
  negro, gradación, viñeta, grano, aberración y parpadeo; opciones en el menú de Iris).
- **Interferencia eléctrica:** las lámparas titilan al paso de un animatrónico que persigue, se acerca o acecha.
- **Alucinaciones** desde la noche 3: destellos subliminales de un animatrónico y pasos fantasma por detrás
  (`hallucinationsFromNight`).
- Configuración nueva: `horrorFilter`, `hallucinationsFromNight`, `animatronicLightInterference`.

## 1.1.0 — Oficina vulnerable, cámaras, ventilación y estamina

Actualización sobre la 1.0 (mismo proyecto, misma arquitectura, partidas compatibles).

### Nuevo
- **Menú de noches** (tecla N o clic derecho en el Núcleo): nueva partida, continuar, noches desbloqueadas, progreso,
  reiniciar/abandonar, dificultad, generar y actualizar el complejo. Botones de reintentar/siguiente/menú en victoria y derrota.
- **Cámaras de seguridad** propias (SecurityCraft no existe para Fabric): 14 cámaras, 3 monitores, minimapa, paneo,
  estática, señal perdida si la cámara está averiada e interferencias por averías de seguridad.
- **Oficina vulnerable**: estados de IA nuevos (acercarse, acechar), golpes, puertas forzadas y atascadas, entrada por puertas
  abiertas; personalidad por animatrónico.
- **Ventilación** con temporizador por noche, avisos, efectos reversibles (náuseas, oscuridad, visión borrosa) y reinicio.
- **Averías adicionales** con consecuencias reales y reglas que garantizan que la noche sea completable.
- **Casilleros de herramientas** y recursos por noche (sólo linterna al empezar, sin duplicados entre reinicios).
- **Estamina** individual, aprovechada por la IA.
- **Tensión**: oscuridad progresiva, latidos, interferencias, silencios, ambiente dosificado, avisos previos.
- **Sonidos de FNAF** proporcionados, en un paquete integrado desactivable (`-PnoFnafSounds` para excluirlo).
- Comandos de depuración: `/fnaf map upgrade`, `/fnaf vent`, `/fnaf breakdown`.

### Cambiado
- La victoria ocurre **a las 6:00 AM** con todos los objetivos completados (ya no al volver al refugio).
- El refugio pasa a ser la **oficina**: los jugadores dentro pueden ser atacados.
- Al empezar la noche sólo se entrega la linterna (`starterTools: true` restaura el kit completo).
- Hitbox de los animatrónicos: 0,7 de ancho (caben por puertas de 1 bloque abiertas). Modelos sin cambios.
- Ventilación: los efectos se aplican a los jugadores del mundo del complejo.

### Compatibilidad
- Campos nuevos de guardado opcionales; estados de IA añadidos al final; identificadores de objetos y bloques sin cambios.
- Mundos de la 1.0: el complejo se actualiza automáticamente al iniciar la primera noche (sólo coloca bloques en huecos libres
  y abre los dinteles de las 4 puertas de la oficina).

## 1.0.0
- Versión inicial: 7 noches, 5 animatrónicos GeckoLib, complejo generado, reparaciones y minijuegos, linterna, HUD, sonidos sintetizados.
