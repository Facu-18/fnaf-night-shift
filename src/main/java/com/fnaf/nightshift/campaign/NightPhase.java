package com.fnaf.nightshift.campaign;

public enum NightPhase {
	/** No hay ninguna noche en curso. */
	NONE,
	/** Noche en curso: hay reparaciones pendientes. */
	REPAIRING,
	/** Todas las reparaciones están hechas: hay que volver al refugio. */
	RETURN_TO_SHELTER,
	VICTORY,
	DEFEAT;

	private static final NightPhase[] VALUES = values();

	public static NightPhase byOrdinal(int i) {
		return i >= 0 && i < VALUES.length ? VALUES[i] : NONE;
	}

	public boolean isActive() {
		return this == REPAIRING || this == RETURN_TO_SHELTER;
	}
}
