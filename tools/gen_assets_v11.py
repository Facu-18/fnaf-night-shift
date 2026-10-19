#!/usr/bin/env python3
"""
FNAF: NIGHT SHIFT 1.1 - recursos de los bloques nuevos (cámara de seguridad,
monitor de cámaras, panel de ventilación, casillero de herramientas) y
traducciones añadidas. Se ejecuta automáticamente desde gen_assets.py, pero
también puede ejecutarse solo:  python tools/gen_assets_v11.py
"""
import json
import math
import os
import random

import gen_assets as G

R = random.Random(11011)
M = G.m
T = G.Tex


def cam_body():
    t = T().noise((52, 54, 58), 5)
    t.frame(0, 0, 15, 15, (30, 31, 34))
    for x in range(2, 14):
        t.put(x, 4, (70, 72, 76))
    t.rect(3, 9, 8, 10, (200, 170, 40))
    t.bolt(1, 1)
    t.bolt(13, 13)
    return t


def cam_lens(broken):
    t = T().noise((20, 20, 22), 3)
    for x in range(16):
        for y in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 6.5:
                t.put(x, y, G.mix((30, 50, 70), (140, 190, 220), max(0, 1 - d / 4)) if not broken else (15, 15, 18))
            elif d < 7.5:
                t.put(x, y, (70, 72, 76))
    if broken:
        for (x, y) in ((5, 4), (6, 5), (7, 6), (8, 8), (9, 9), (7, 10), (10, 6), (11, 5)):
            t.put(x, y, (190, 190, 190))
    else:
        t.put(3, 3, (255, 40, 40))
        t.put(4, 3, (200, 30, 30))
        t.put(9, 5, (230, 240, 255))
    return t


def monitor_screen():
    t = T().noise((20, 24, 22), 3)
    t.frame(0, 0, 15, 15, (40, 42, 44))
    for x in range(2, 14):
        for y in range(2, 12):
            g = R.randint(20, 120)
            t.put(x, y, (g // 3, g, g // 3))
    for y in range(3, 12, 3):
        for x in range(2, 14):
            t.put(x, y, G.shade(t.get(x, y), 0.6))
    t.rect(2, 13, 4, 14, (220, 40, 40))
    t.rect(11, 13, 13, 14, (60, 200, 80))
    return t


def vent_panel():
    t = T().noise((92, 96, 100), 5)
    t.frame(0, 0, 15, 15, (50, 52, 56))
    for x in range(16):
        for y in range(16):
            d = math.hypot(x - 7.5, y - 6)
            if 1 < d < 4.5:
                a = math.atan2(y - 6, x - 7.5)
                t.put(x, y, (30, 32, 34) if int((a / math.pi * 3 + d * 0.2) % 2) == 0 else (150, 154, 158))
    t.rect(3, 12, 6, 13, (40, 200, 220))
    t.rect(9, 12, 12, 13, (220, 60, 40))
    t.put(7, 6, (220, 220, 220))
    return t


def locker_side():
    t = T().noise((96, 104, 98), 5)
    t.frame(0, 0, 15, 15, (60, 66, 62))
    for y in range(3, 14, 3):
        for x in range(2, 14):
            t.put(x, y, (70, 76, 72))
    t.spots((140, 80, 40), 2, 1.4, 0.5)
    return t


TOOL_ICONS = {
    "wrench": [(5, 11), (6, 10), (7, 9), (8, 8), (9, 7), (10, 6), (10, 5), (11, 5), (9, 5), (11, 7), (4, 12), (5, 12), (4, 11)],
    "screwdriver": [(5, 11), (6, 10), (6, 11), (5, 10), (7, 9), (8, 8), (9, 7), (10, 6), (11, 5)],
    "pliers": [(5, 11), (6, 10), (7, 11), (8, 10), (7, 9), (8, 8), (9, 7), (10, 6), (9, 8), (10, 7), (11, 6)],
}


def locker_front(tool):
    t = T().noise((104, 112, 106), 5)
    t.frame(0, 0, 15, 15, (55, 60, 57))
    t.frame(1, 1, 14, 14, (80, 88, 84))
    if tool is None:
        # puerta abierta: interior vacío y oscuro
        t.noise((22, 22, 24), 4, 2, 2, 14, 14)
        t.rect(2, 9, 13, 9, (60, 62, 64))
        t.rect(13, 3, 14, 12, (130, 136, 132))
    else:
        for y in (3, 4):
            for x in range(4, 12):
                t.put(x, y, (50, 54, 52) if x % 2 else (90, 96, 92))
        for (x, y) in TOOL_ICONS[tool]:
            t.put(x, y, (230, 230, 225))
        t.rect(12, 7, 13, 8, (40, 40, 40))
        t.put(12, 12, (60, 230, 80))
    t.spots((140, 80, 40), 2, 1.3, 0.45)
    return t


def write(path, data):
    G.write_json(path, data)


def gen():
    TB, MB, MI, BS = G.TB, G.MB, G.MI, G.BS

    # --- cámara de seguridad (instalación tipo CAMERA) ---
    cam_body().save(os.path.join(TB, "security_camera_body.png"))
    cam_lens(False).save(os.path.join(TB, "security_camera_lens.png"))
    cam_lens(True).save(os.path.join(TB, "security_camera_lens_broken.png"))
    for broken in (False, True):
        lens = "security_camera_lens_broken" if broken else "security_camera_lens"
        write(os.path.join(MB, "security_camera%s.json" % ("_broken" if broken else "")), {
            "parent": "minecraft:block/block",
            "textures": {"particle": M("security_camera_body"), "body": M("security_camera_body"), "lens": M(lens), "arm": M("metal_plating")},
            "elements": [
                {"from": [7, 12, 7], "to": [9, 16, 9], "faces": {f: {"texture": "#arm", "uv": [7, 0, 9, 4]} for f in ("north", "south", "east", "west")}},
                {"from": [5, 7, 4], "to": [11, 12, 13], "rotation": {"angle": -22.5, "axis": "x", "origin": [8, 12, 8]}, "faces": {
                    "north": {"texture": "#lens"}, "south": {"texture": "#body"}, "east": {"texture": "#body"},
                    "west": {"texture": "#body"}, "up": {"texture": "#body"}, "down": {"texture": "#body"}}},
                {"from": [4, 11, 3], "to": [12, 12, 13], "rotation": {"angle": -22.5, "axis": "x", "origin": [8, 12, 8]}, "faces": {
                    "north": {"texture": "#body", "uv": [4, 0, 12, 1]}, "up": {"texture": "#body"}, "down": {"texture": "#body"},
                    "east": {"texture": "#body", "uv": [3, 0, 13, 1]}, "west": {"texture": "#body", "uv": [3, 0, 13, 1]}}},
            ]})
    variants = {}
    for facing, y in G.FACING_Y.items():
        for broken in ("false", "true"):
            v = {"model": M("security_camera" + ("_broken" if broken == "true" else ""))}
            if y:
                v["y"] = y
            variants["broken=%s,facing=%s" % (broken, facing)] = v
    write(os.path.join(BS, "security_camera.json"), {"variants": variants})
    G.block_item("security_camera")

    # --- monitor de cámaras ---
    monitor_screen().save(os.path.join(TB, "camera_monitor_screen.png"))
    write(os.path.join(MB, "camera_monitor.json"), {
        "parent": "minecraft:block/block",
        "textures": {"particle": M("machine_side"), "screen": M("camera_monitor_screen"), "side": M("machine_side"), "top": M("machine_top")},
        "elements": [
            {"from": [2, 0, 4], "to": [14, 2, 14], "faces": {f: {"texture": "#side"} for f in ("north", "south", "east", "west", "up", "down")}},
            {"from": [6, 2, 8], "to": [10, 4, 12], "faces": {f: {"texture": "#side"} for f in ("north", "south", "east", "west", "up")}},
            {"from": [1, 4, 3], "to": [15, 14, 15], "faces": {
                "north": {"texture": "#screen"}, "south": {"texture": "#side"}, "east": {"texture": "#side"},
                "west": {"texture": "#side"}, "up": {"texture": "#top"}, "down": {"texture": "#side"}}},
        ]})
    G.wall_state("camera_monitor")
    G.block_item("camera_monitor")

    # --- panel de ventilación ---
    vent_panel().save(os.path.join(TB, "vent_control_panel.png"))
    write(os.path.join(MB, "vent_control_panel.json"), {
        "parent": "minecraft:block/block",
        "textures": {"particle": M("vent_control_panel"), "front": M("vent_control_panel"), "side": M("machine_side")},
        "elements": [{"from": [3, 3, 14], "to": [13, 13, 16], "faces": {
            "north": {"texture": "#front", "uv": [3, 3, 13, 13]}, "south": {"texture": "#side"},
            "east": {"texture": "#side", "uv": [0, 3, 2, 13]}, "west": {"texture": "#side", "uv": [0, 3, 2, 13]},
            "up": {"texture": "#side", "uv": [3, 0, 13, 2]}, "down": {"texture": "#side", "uv": [3, 0, 13, 2]}}}]})
    G.wall_state("vent_control_panel")
    G.block_item("vent_control_panel")

    # --- casillero de herramientas ---
    locker_side().save(os.path.join(TB, "tool_locker_side.png"))
    locker_front(None).save(os.path.join(TB, "tool_locker_front_empty.png"))
    for tool in TOOL_ICONS:
        locker_front(tool).save(os.path.join(TB, "tool_locker_front_%s.png" % tool))
    for name, front in [("tool_locker_empty", "tool_locker_front_empty")] + [("tool_locker_" + t, "tool_locker_front_" + t) for t in TOOL_ICONS]:
        write(os.path.join(MB, name + ".json"), {"parent": "minecraft:block/orientable",
                                                "textures": {"top": M("machine_top"), "front": M(front), "side": M("tool_locker_side")}})
    variants = {}
    for facing, y in G.FACING_Y.items():
        for tool in TOOL_ICONS:
            for stocked in ("false", "true"):
                v = {"model": M("tool_locker_" + tool if stocked == "true" else "tool_locker_empty")}
                if y:
                    v["y"] = y
                variants["facing=%s,stocked=%s,tool=%s" % (facing, stocked, tool)] = v
    write(os.path.join(BS, "tool_locker.json"), {"variants": variants})
    G.block_item("tool_locker", "tool_locker_wrench")


LANG = {
    "block.fnaf_night_shift.security_camera": "Cámara de seguridad",
    "block.fnaf_night_shift.camera_monitor": "Monitor de cámaras",
    "block.fnaf_night_shift.vent_control_panel": "Panel de ventilación",
    "block.fnaf_night_shift.tool_locker": "Casillero de herramientas",
    "repair.fnaf_night_shift.camera": "Cámara",
    "pack.fnaf_night_shift.fnaf_sounds": "FNAF Night Shift: sonidos de FNAF",
    "key.fnaf_night_shift.menu": "Menú de noches",
    "category.fnaf_night_shift": "FNAF: Night Shift",
    "direction.fnaf_night_shift.north": "NORTE",
    "direction.fnaf_night_shift.south": "SUR",
    "direction.fnaf_night_shift.east": "ESTE",
    "direction.fnaf_night_shift.west": "OESTE",
    "ai_state.fnaf_night_shift.approach": "Acercándose a la oficina",
    "ai_state.fnaf_night_shift.lurk": "Acechando en la puerta",
    "title.fnaf_night_shift.survive_until_6": "Sobreviví hasta las 6 AM",
    "hud.fnaf_night_shift.survive_until_6": "Sobreviví hasta las 6 AM",
    "hud.fnaf_night_shift.vent": "Ventilación: %s",
    "hud.fnaf_night_shift.vent_failed": "¡VENTILACIÓN CAÍDA! Reiniciala en la oficina",
    "hud.fnaf_night_shift.lurk_side": "⚠ Algo está en la puerta %s",
    "hud.fnaf_night_shift.lurk_warning": "⚠ Hay algo en una puerta de la oficina",
    "hud.fnaf_night_shift.door_jammed": "%s puerta(s) de la oficina atascadas",
    "hud.fnaf_night_shift.exhausted": "Sin aliento",
    "message.fnaf_night_shift.door_jammed": "La puerta está atascada: repará el control de puerta automática.",
    "message.fnaf_night_shift.door_forced": "¡Forzaron la puerta %s de la oficina!",
    "message.fnaf_night_shift.malfunction": "¡AVERÍA! %s a %s m — %s",
    "consequence.fnaf_night_shift.power": "la oficina se queda sin luz estable",
    "consequence.fnaf_night_shift.door": "una puerta de la oficina quedó atascada abierta",
    "consequence.fnaf_night_shift.vent": "la ventilación se degrada más rápido",
    "consequence.fnaf_night_shift.cameras": "interferencias en todas las cámaras",
    "consequence.fnaf_night_shift.lights": "las luces de la zona fallan",
    "consequence.fnaf_night_shift.camera": "esa cámara perdió la señal",
    "message.fnaf_night_shift.vent_warning": "Ventilación: reinicio necesario en %s s",
    "message.fnaf_night_shift.vent_failed": "¡La ventilación falló! El aire se vuelve irrespirable... reiniciala en el panel de la oficina.",
    "message.fnaf_night_shift.vent_reset": "%s reinició la ventilación",
    "message.fnaf_night_shift.vent_idle": "No hay ninguna noche en curso.",
    "message.fnaf_night_shift.no_cameras": "No hay cámaras instaladas. Usá «Actualizar complejo» en el menú (tecla N).",
    "message.fnaf_night_shift.locker_empty": "El casillero está vacío.",
    "message.fnaf_night_shift.locker_taken": "Tomaste: %s",
    "message.fnaf_night_shift.locker_tier": "Nivel de riesgo del casillero: %s",
    "message.fnaf_night_shift.no_lockers": "El mapa no tiene casilleros de herramientas: recibís el kit completo.",
    "message.fnaf_night_shift.upgrade_done": "Complejo actualizado: %s cambios (cámaras, casilleros, ventilación y puertas).",
    "message.fnaf_night_shift.upgrade_unknown": "El refugio no pertenece a un complejo generado por el mod: colocá cámaras, monitores y casilleros a mano.",
    "message.fnaf_night_shift.menu_denied": "No tenés permiso para hacer eso.",
    "message.fnaf_night_shift.foxy_running": "Se oyen pasos metálicos corriendo...",
    "screen.fnaf_night_shift.cameras": "Cámaras",
    "screen.fnaf_night_shift.no_signal": "SIN SEÑAL",
    "screen.fnaf_night_shift.no_signal_hint": "Cámara averiada: reparala con el destornillador",
    "screen.fnaf_night_shift.interference": "INTERFERENCIAS — equipo de seguridad averiado",
    "screen.fnaf_night_shift.camera_help": "A/D o flechas: cambiar · Arrastrar: girar · ESC: salir",
    "screen.fnaf_night_shift.you": "TÚ",
    "screen.fnaf_night_shift.menu": "Menú de noches",
    "screen.fnaf_night_shift.night_tooltip": "Jugar la noche %s",
    "screen.fnaf_night_shift.continue_night": "Continuar (noche %s)",
    "screen.fnaf_night_shift.new_game": "Nueva partida",
    "screen.fnaf_night_shift.new_game_confirm": "¿Borrar el progreso? Clic otra vez",
    "screen.fnaf_night_shift.restart": "Reiniciar noche",
    "screen.fnaf_night_shift.abandon": "Abandonar noche",
    "screen.fnaf_night_shift.difficulty": "Dificultad: %s",
    "screen.fnaf_night_shift.generate": "Generar complejo aquí",
    "screen.fnaf_night_shift.generate_tooltip": "Construye el complejo con esta posición como esquina noroeste (100 × 92 bloques). No reemplaza construcciones.",
    "screen.fnaf_night_shift.upgrade": "Actualizar complejo",
    "screen.fnaf_night_shift.close": "Cerrar",
    "screen.fnaf_night_shift.progress": "Noches completadas: %s/%s · Desbloqueada hasta la %s · Veces atrapado: %s",
    "screen.fnaf_night_shift.status_active": "Noche %s en curso",
    "screen.fnaf_night_shift.status_no_map": "No hay complejo: generalo o colocá un Núcleo del refugio",
    "screen.fnaf_night_shift.status_ready": "Complejo listo · %s instalaciones · %s cámaras · %s casilleros",
    "screen.fnaf_night_shift.no_permission": "Sólo el anfitrión o un administrador puede controlar las noches",
    "screen.fnaf_night_shift.choose_night": "Elegí una noche desbloqueada",
    "screen.fnaf_night_shift.next_night": "Siguiente noche",
    "screen.fnaf_night_shift.retry": "Reintentar",
    "screen.fnaf_night_shift.back_menu": "Menú",
    "subtitles.fnaf_night_shift.camera.switch": "Cambio de cámara",
    "subtitles.fnaf_night_shift.camera.static": "Estática",
    "subtitles.fnaf_night_shift.vent.warning": "Aviso de ventilación",
    "subtitles.fnaf_night_shift.vent.fail": "Ventilación averiada",
    "subtitles.fnaf_night_shift.vent.reset": "Ventilación reiniciada",
    "subtitles.fnaf_night_shift.player.heartbeat": "Latidos",
    "subtitles.fnaf_night_shift.player.breath": "Respiración agitada",
    "subtitles.fnaf_night_shift.door.bang": "Golpes en la puerta",
    "subtitles.fnaf_night_shift.entity.animatronic.lurk": "Algo respira tras la puerta",
    "subtitles.fnaf_night_shift.alert.malfunction": "Alarma de avería",
    "subtitles.fnaf_night_shift.ambient.static": "Interferencia",
    "subtitles.fnaf_night_shift.ambient.fnaf": "Ambiente",
    "subtitles.fnaf_night_shift.entity.foxy.jumpscare": "Grito",
    "subtitles.fnaf_night_shift.entity.golden_freddy.jumpscare": "Grito",
    "subtitles.fnaf_night_shift.block.locker.open": "Casillero",
}


def extend_lang():
    for lang in ("es_es", "es_ar", "es_mx", "es_cl", "es_uy", "es_ve", "es_ec", "en_us"):
        p = os.path.join(G.ASSETS, "lang", lang + ".json")
        data = json.load(open(p, encoding="utf-8"))
        data.update(LANG)
        # textos existentes cuyo significado cambió en la 1.1
        data["hud.fnaf_night_shift.in_shelter"] = "Oficina · recargando linterna · cerrá las puertas si algo se acerca"
        data["tutorial.fnaf_night_shift.1"] = "[Tutorial] Esta es la oficina. Cerrá las puertas de seguridad si algo se acerca: no es un lugar totalmente seguro."
        data["tutorial.fnaf_night_shift.2"] = "[Tutorial] Usá el monitor para mirar las cámaras y el panel de la pared para reiniciar la ventilación."
        data["tutorial.fnaf_night_shift.4"] = "[Tutorial] Sólo tenés la linterna: las herramientas están en casilleros por el complejo y los repuestos en las cajas."
        data["tutorial.fnaf_night_shift.5"] = "[Tutorial] Reparar hace ruido y correr gasta estamina. Aunque termines, hay que aguantar hasta las 6 AM."
        data["screen.fnaf_night_shift.next_hint"] = "Pulsá «Siguiente noche» o abrí el menú con N."
        data["screen.fnaf_night_shift.reason_caught"] = "Pulsá «Reintentar» o abrí el menú con N."
        data["screen.fnaf_night_shift.reason_time"] = "Quedaron objetivos pendientes a las 6 AM. Pulsá «Reintentar»."
        G.write_json(p, dict(sorted(data.items())))


if __name__ == "__main__":
    gen()
    extend_lang()
    import redesign_textures
    redesign_textures.main()
    print("recursos 1.1 generados")
