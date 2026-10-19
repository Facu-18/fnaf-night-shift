package com.fnaf.nightshift.campaign;

public enum Difficulty {
	FACIL(0.75, 1.25, 0.8),
	NORMAL(1.0, 1.0, 1.0),
	DIFICIL(1.3, 0.85, 1.15),
	PESADILLA(1.7, 0.7, 1.3);

	/** Multiplicador de agresividad de los animatrónicos. */
	public final double aggression;
	/** Multiplicador de batería de la linterna. */
	public final double battery;
	/** Multiplicador de complejidad / duración de reparaciones. */
	public final double repairComplexity;

	Difficulty(double aggression, double battery, double repairComplexity) {
		this.aggression = aggression;
		this.battery = battery;
		this.repairComplexity = repairComplexity;
	}

	public static Difficulty parse(String s, Difficulty fallback) {
		if (s == null) return fallback;
		for (Difficulty d : values()) {
			if (d.name().equalsIgnoreCase(s)) return d;
		}
		return fallback;
	}

	public String translationKey() {
		return "difficulty.fnaf_night_shift." + name().toLowerCase();
	}
}
