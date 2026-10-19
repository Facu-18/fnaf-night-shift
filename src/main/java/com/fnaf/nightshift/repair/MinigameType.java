package com.fnaf.nightshift.repair;

public enum MinigameType {
	/** Mantener pulsado clic derecho con la herramienta. */
	HOLD,
	/** Conectar cables del mismo color. */
	WIRES,
	/** Repetir una secuencia de interruptores. */
	SEQUENCE,
	/** Memorizar e introducir un código. */
	CODE,
	/** Sustituir piezas quemadas (consume repuestos). */
	REPLACE_PART;

	private static final MinigameType[] VALUES = values();

	public static MinigameType byOrdinal(int i) {
		return i >= 0 && i < VALUES.length ? VALUES[i] : HOLD;
	}
}
