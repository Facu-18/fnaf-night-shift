package com.fnaf.nightshift.block;

import net.minecraft.util.StringIdentifiable;

/**
 * Zona a la que pertenece un nodo de patrulla. Algunos animatrónicos
 * prefieren ciertas zonas (por ejemplo, Chica prefiere las zonas de servicio).
 */
public enum Zone implements StringIdentifiable {
	GENERAL("general"),
	PIZZERIA("pizzeria"),
	SERVICE("service"),
	TECHNICAL("technical"),
	TUNNEL("tunnel");

	private final String id;

	Zone(String id) {
		this.id = id;
	}

	@Override
	public String asString() {
		return id;
	}

	public Zone next() {
		return values()[(ordinal() + 1) % values().length];
	}
}
