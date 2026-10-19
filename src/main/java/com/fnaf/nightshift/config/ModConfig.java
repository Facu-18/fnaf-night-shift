package com.fnaf.nightshift.config;

import com.fnaf.nightshift.FnafNightShift;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Configuración editable en config/fnaf_night_shift.json.
 * Se puede recargar en caliente con /fnaf config reload.
 */
public class ModConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("fnaf_night_shift.json");

	private static ModConfig instance = new ModConfig();

	// --- Noches ---
	/** Duración base de una noche (segundos de juego real). */
	public int nightDurationSeconds = 480;
	/** Segundos extra que se suman por cada noche posterior a la primera. */
	public int nightDurationIncreasePerNight = 30;
	/** Segundos de calma al empezar la noche antes de que aparezcan animatrónicos. */
	public int gracePeriodSeconds = 30;
	/** Dificultad por defecto para mundos nuevos: FACIL, NORMAL, DIFICIL, PESADILLA. */
	public String defaultDifficulty = "NORMAL";

	// --- Reparaciones ---
	public int baseRepairsPerNight = 3;
	/** Reparaciones adicionales que se suman en cada noche. */
	public double extraRepairsPerNight = 0.85;
	public int maxRepairsPerNight = 10;
	/** Multiplicador del tiempo necesario en reparaciones de mantener pulsado. */
	public double holdRepairTimeMultiplier = 1.0;

	// --- Enemigos ---
	/** Multiplicador global de agresividad (velocidad, detección, persistencia). */
	public double aggressionMultiplier = 1.0;
	public double detectionRange = 22.0;
	public boolean animatronicsInvulnerable = true;
	/** Si es true, el contacto produce un susto (atrapa al jugador). Si es false, causa daño normal. */
	public boolean jumpscareCatch = true;
	public float bonnieDamage = 6.0f;
	public float chicaDamage = 6.0f;
	public float foxyDamage = 8.0f;
	public float freddyDamage = 10.0f;
	public int maxAnimatronicsPerType = 3;

	// --- Linterna ---
	public int flashlightBatterySeconds = 180;
	/** Segundos de batería recuperados por cada segundo dentro del refugio. */
	public double flashlightRechargePerSecond = 6.0;

	// --- Refugio (oficina) y kit ---
	public int shelterRadius = 7;
	public boolean giveStarterKit = true;
	/** Si es true se entregan todas las herramientas al empezar (comportamiento antiguo). Por defecto sólo la linterna. */
	public boolean starterTools = false;
	/** Al empezar cada noche se retiran las herramientas y repuestos de la noche anterior. */
	public boolean resetInventoryEachNight = true;
	/** Si es true, cualquier jugador puede iniciar/reiniciar noches desde el menú (útil en cooperativo). */
	public boolean playersCanControlNights = true;

	// --- Oficina vulnerable ---
	/** Multiplicador de la presión de los animatrónicos sobre la oficina. */
	public double officePressureMultiplier = 1.0;
	/** Golpes necesarios para forzar una puerta cerrada en la noche 1 (disminuye con la noche). */
	public int doorStrength = 12;

	// --- Ventilación ---
	public boolean ventilationEnabled = true;
	/** Multiplicador del intervalo de mantenimiento de la ventilación. */
	public double ventIntervalMultiplier = 1.0;

	// --- Averías adicionales ---
	public boolean extraBreakdowns = true;

	// --- Estamina ---
	public boolean staminaEnabled = true;
	public float staminaMax = 100f;
	public float staminaDrainPerSecond = 10f;
	public float staminaRegenWalkingPerSecond = 6f;
	public float staminaRegenRestingPerSecond = 13f;
	/** Estamina necesaria para volver a correr después de agotarse. */
	public float staminaRecoverThreshold = 35f;

	// --- Atmósfera ---
	/** Filtro de terror del mod (post-procesado vanilla) durante las noches. Se desactiva solo con shaders de Iris. */
	public boolean horrorFilter = true;
	/** Alucinaciones (destellos y pasos fantasma) a partir de esta noche. 8 = desactivadas. */
	public int hallucinationsFromNight = 3;
	/** Las lámparas parpadean al paso de los animatrónicos. */
	public boolean animatronicLightInterference = true;
	/** Fracción máxima de luces averiadas/apagadas en la noche 7 (escala con la noche). */
	public double maxDarkness = 0.7;

	// --- Eventos secretos ---
	public boolean goldenFreddyEnabled = true;
	public int goldenFreddyMinNight = 5;
	/** Probabilidad (0-1) por minuto de que Golden Freddy se manifieste. */
	public double goldenFreddyChancePerMinute = 0.10;
	public boolean powerOutageEvents = true;
	public int powerOutageMinNight = 5;

	// --- Noche 7 (desafío personalizado) ---
	public int night7DurationSeconds = 720;
	public int night7Repairs = 10;
	public double night7Aggression = 1.6;
	public int night7ExtraBonnie = 0;
	public int night7ExtraChica = 0;
	public int night7ExtraFoxy = 1;
	public int night7ExtraFreddy = 1;
	public boolean night7GoldenFreddy = true;
	public int night7VentSeconds = 100;
	public int night7ExtraBreakdowns = 5;

	public static ModConfig get() {
		return instance;
	}

	public static void load() {
		try {
			if (Files.exists(PATH)) {
				try (Reader reader = Files.newBufferedReader(PATH, StandardCharsets.UTF_8)) {
					ModConfig loaded = GSON.fromJson(reader, ModConfig.class);
					if (loaded != null) {
						instance = loaded;
					}
				}
			}
			instance.sanitize();
			save();
		} catch (Exception e) {
			FnafNightShift.LOGGER.error("No se pudo leer la configuración, se usan valores por defecto", e);
			instance = new ModConfig();
		}
	}

	public static void save() {
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH, StandardCharsets.UTF_8)) {
				GSON.toJson(instance, writer);
			}
		} catch (IOException e) {
			FnafNightShift.LOGGER.error("No se pudo guardar la configuración", e);
		}
	}

	private void sanitize() {
		nightDurationSeconds = Math.max(60, nightDurationSeconds);
		nightDurationIncreasePerNight = Math.max(0, nightDurationIncreasePerNight);
		gracePeriodSeconds = Math.max(0, gracePeriodSeconds);
		baseRepairsPerNight = Math.max(1, baseRepairsPerNight);
		maxRepairsPerNight = Math.max(baseRepairsPerNight, maxRepairsPerNight);
		extraRepairsPerNight = Math.max(0, extraRepairsPerNight);
		holdRepairTimeMultiplier = Math.max(0.1, holdRepairTimeMultiplier);
		aggressionMultiplier = Math.max(0.1, Math.min(5.0, aggressionMultiplier));
		detectionRange = Math.max(4, Math.min(64, detectionRange));
		maxAnimatronicsPerType = Math.max(1, maxAnimatronicsPerType);
		flashlightBatterySeconds = Math.max(10, flashlightBatterySeconds);
		shelterRadius = Math.max(3, Math.min(32, shelterRadius));
		night7DurationSeconds = Math.max(60, night7DurationSeconds);
		night7Repairs = Math.max(1, night7Repairs);
		goldenFreddyChancePerMinute = Math.max(0, Math.min(1, goldenFreddyChancePerMinute));
	}
}
