package com.fnaf.nightshift.registry;

import com.fnaf.nightshift.FnafNightShift;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;

/**
 * Todos los sonidos son originales, sintetizados por tools/gen_sounds.py.
 */
public final class ModSounds {
	public static final SoundEvent AMBIENT_HUM = of("ambient.hum");
	public static final SoundEvent AMBIENT_DISTANT = of("ambient.distant");
	public static final SoundEvent STEP_METAL = of("step.metal");
	public static final SoundEvent SERVO = of("mech.servo");
	public static final SoundEvent DOOR_SLAM = of("door.slam");
	public static final SoundEvent REPAIR_RATCHET = of("repair.ratchet");
	public static final SoundEvent REPAIR_SPARK = of("repair.spark");
	public static final SoundEvent REPAIR_SUCCESS = of("repair.success");
	public static final SoundEvent REPAIR_FAIL = of("repair.fail");
	public static final SoundEvent ALERT_DANGER = of("alert.danger");
	public static final SoundEvent NIGHT_START = of("alert.night_start");
	public static final SoundEvent SIX_AM = of("alert.six_am");
	public static final SoundEvent POWER_OUT = of("alert.power_out");
	public static final SoundEvent FLASHLIGHT_CLICK = of("flashlight.click");
	public static final SoundEvent FLASHLIGHT_DEAD = of("flashlight.dead");
	public static final SoundEvent BONNIE_IDLE = of("entity.bonnie.idle");
	public static final SoundEvent CHICA_IDLE = of("entity.chica.idle");
	public static final SoundEvent FOXY_RUN = of("entity.foxy.run");
	public static final SoundEvent FREDDY_LAUGH = of("entity.freddy.laugh");
	public static final SoundEvent GOLDEN_WHISPER = of("entity.golden_freddy.whisper");
	public static final SoundEvent ANIMATRONIC_HURT = of("entity.animatronic.hurt");
	public static final SoundEvent JUMPSCARE = of("entity.animatronic.jumpscare");
	public static final SoundEvent MUSIC_BOX = of("music.box");
	public static final SoundEvent CRATE_OPEN = of("block.crate.open");
	// --- Actualización 1.1 ---
	public static final SoundEvent CAMERA_SWITCH = of("camera.switch");
	public static final SoundEvent CAMERA_STATIC = of("camera.static");
	public static final SoundEvent VENT_WARNING = of("vent.warning");
	public static final SoundEvent VENT_FAIL = of("vent.fail");
	public static final SoundEvent VENT_RESET = of("vent.reset");
	public static final SoundEvent HEARTBEAT = of("player.heartbeat");
	public static final SoundEvent BREATH = of("player.breath");
	public static final SoundEvent DOOR_BANG = of("door.bang");
	public static final SoundEvent LURK = of("entity.animatronic.lurk");
	public static final SoundEvent MALFUNCTION = of("alert.malfunction");
	public static final SoundEvent AMBIENT_STATIC = of("ambient.static");
	public static final SoundEvent AMBIENT_FNAF = of("ambient.fnaf");
	public static final SoundEvent JUMPSCARE_FOXY = of("entity.foxy.jumpscare");
	public static final SoundEvent JUMPSCARE_GOLDEN = of("entity.golden_freddy.jumpscare");
	public static final SoundEvent LOCKER_OPEN = of("block.locker.open");

	private ModSounds() {
	}

	private static SoundEvent of(String path) {
		return SoundEvent.of(FnafNightShift.id(path));
	}

	public static void register() {
		for (SoundEvent e : new SoundEvent[]{AMBIENT_HUM, AMBIENT_DISTANT, STEP_METAL, SERVO, DOOR_SLAM, REPAIR_RATCHET,
				REPAIR_SPARK, REPAIR_SUCCESS, REPAIR_FAIL, ALERT_DANGER, NIGHT_START, SIX_AM, POWER_OUT, FLASHLIGHT_CLICK,
				FLASHLIGHT_DEAD, BONNIE_IDLE, CHICA_IDLE, FOXY_RUN, FREDDY_LAUGH, GOLDEN_WHISPER, ANIMATRONIC_HURT,
				JUMPSCARE, MUSIC_BOX, CRATE_OPEN, CAMERA_SWITCH, CAMERA_STATIC, VENT_WARNING, VENT_FAIL, VENT_RESET, HEARTBEAT,
				BREATH, DOOR_BANG, LURK, MALFUNCTION, AMBIENT_STATIC, AMBIENT_FNAF, JUMPSCARE_FOXY, JUMPSCARE_GOLDEN, LOCKER_OPEN}) {
			Registry.register(Registries.SOUND_EVENT, e.getId(), e);
		}
	}
}
