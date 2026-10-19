package com.fnaf.nightshift.entity;

import net.minecraft.util.StringIdentifiable;

/**
 * Tipos de animatrónico. El orden define la noche en la que se incorporan.
 * ANY se usa sólo en marcadores de aparición genéricos.
 */
public enum AnimatronicKind implements StringIdentifiable {
	ANY("any", 0, 0x777777),
	BONNIE("bonnie", 1, 0x5B3F99),
	CHICA("chica", 2, 0xE8C33A),
	FOXY("foxy", 3, 0xA3402A),
	FREDDY("freddy", 4, 0x6B4226),
	GOLDEN_FREDDY("golden_freddy", 99, 0xC9A227);

	private final String id;
	private final int introNight;
	private final int color;

	AnimatronicKind(String id, int introNight, int color) {
		this.id = id;
		this.introNight = introNight;
		this.color = color;
	}

	public String id() {
		return id;
	}

	public int introNight() {
		return introNight;
	}

	public int color() {
		return color;
	}

	public boolean isRegular() {
		return this != ANY && this != GOLDEN_FREDDY;
	}

	@Override
	public String asString() {
		return id;
	}

	public static AnimatronicKind byId(String id) {
		for (AnimatronicKind k : values()) {
			if (k.id.equalsIgnoreCase(id)) return k;
		}
		return null;
	}
}
