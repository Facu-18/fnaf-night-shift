package com.fnaf.nightshift.ai;

/**
 * Estados de comportamiento de los animatrónicos. Se sincronizan al cliente
 * para que las animaciones correspondan siempre al estado real de la IA.
 */
public enum AiState {
	IDLE,
	PATROL,
	INVESTIGATE,
	SEARCH,
	DETECT,
	CHASE,
	ATTACK,
	REPOSITION,
	STUNNED,
	/** Se dirige a una puerta de la oficina. */
	APPROACH,
	/** Acecha junto a una puerta de la oficina, esperando para entrar. */
	LURK;

	private static final AiState[] VALUES = values();

	public static AiState byOrdinal(int i) {
		return i >= 0 && i < VALUES.length ? VALUES[i] : IDLE;
	}

	public String translationKey() {
		return "ai_state.fnaf_night_shift." + name().toLowerCase();
	}
}
