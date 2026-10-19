package com.fnaf.nightshift.campaign;

import com.fnaf.nightshift.config.ModConfig;
import com.fnaf.nightshift.entity.AnimatronicKind;

import java.util.ArrayList;
import java.util.List;

/**
 * Definición de cada una de las siete noches: duración, reparaciones,
 * animatrónicos activos y agresividad base.
 */
public record NightDefinition(int night, int durationTicks, int repairs, double aggression, List<AnimatronicKind> roster,
							  boolean tutorial, boolean goldenFreddy, boolean powerOutages,
							  int ventIntervalTicks, int maxExtraBreakdowns, int extraResponseTicks, double darkness,
							  double toolRisk, int doorStrength) {

	public static final int MAX_NIGHT = 7;

	public static NightDefinition of(int night, Difficulty difficulty) {
		ModConfig c = ModConfig.get();
		night = Math.max(1, Math.min(MAX_NIGHT, night));
		List<AnimatronicKind> roster = new ArrayList<>();
		for (AnimatronicKind k : AnimatronicKind.values()) {
			if (k.isRegular() && k.introNight() <= night) roster.add(k);
		}

		double[] baseAggression = {0.55, 0.7, 0.85, 1.0, 1.2, 1.4, 1.0};
		int seconds;
		int repairs;
		double aggression;
		boolean golden;
		boolean outages;

		if (night == 7) {
			seconds = c.night7DurationSeconds;
			repairs = c.night7Repairs;
			aggression = c.night7Aggression;
			for (int i = 0; i < c.night7ExtraBonnie; i++) roster.add(AnimatronicKind.BONNIE);
			for (int i = 0; i < c.night7ExtraChica; i++) roster.add(AnimatronicKind.CHICA);
			for (int i = 0; i < c.night7ExtraFoxy; i++) roster.add(AnimatronicKind.FOXY);
			for (int i = 0; i < c.night7ExtraFreddy; i++) roster.add(AnimatronicKind.FREDDY);
			golden = c.goldenFreddyEnabled && c.night7GoldenFreddy;
			outages = c.powerOutageEvents;
		} else {
			seconds = c.nightDurationSeconds + c.nightDurationIncreasePerNight * (night - 1);
			repairs = (int) Math.round(c.baseRepairsPerNight + c.extraRepairsPerNight * (night - 1));
			if (night >= 5) repairs += 1; // la noche 5 en adelante añade reparaciones extra
			aggression = baseAggression[night - 1];
			golden = c.goldenFreddyEnabled && night >= c.goldenFreddyMinNight;
			outages = c.powerOutageEvents && night >= c.powerOutageMinNight;
		}
		repairs = Math.min(repairs, c.maxRepairsPerNight);
		aggression *= c.aggressionMultiplier * difficulty.aggression;

		int[] ventSeconds = {240, 210, 180, 160, 140, 120, c.night7VentSeconds};
		int[] extras = {1, 1, 2, 2, 3, 4, c.night7ExtraBreakdowns};
		int[] response = {150, 140, 130, 120, 105, 90, 80};
		int vent = (int) (ventSeconds[night - 1] * c.ventIntervalMultiplier / Math.max(0.75, difficulty.aggression)) * 20;
		double darkness = c.maxDarkness * (0.25 + 0.75 * (night - 1) / 6.0);
		int door = Math.max(4, c.doorStrength - (night - 1) - (difficulty.ordinal() - 1));

		return new NightDefinition(night, seconds * 20, Math.max(1, repairs), aggression, roster, night == 1, golden, outages,
				Math.max(40 * 20, vent), c.extraBreakdowns ? extras[night - 1] : 0, response[night - 1] * 20, darkness,
				(night - 1) / 6.0, door);
	}

	/** Nivel de complejidad para minijuegos (longitud de secuencias, etc.). */
	public int puzzleLevel(Difficulty d) {
		return Math.max(1, (int) Math.round(night * d.repairComplexity));
	}
}
