package com.fnaf.nightshift.repair;

import com.fnaf.nightshift.registry.ModItems;
import net.minecraft.item.Item;
import net.minecraft.util.StringIdentifiable;

import java.util.function.Supplier;

/**
 * Tipos de instalaciones reparables. Cada una define su herramienta, el
 * minijuego, los repuestos que consume y cuánto ruido genera.
 */
public enum RepairType implements StringIdentifiable {
	GENERATOR("generator", MinigameType.HOLD, () -> ModItems.WRENCH, null, 0, 10.0, 28),
	FUSE_BOX("fuse_box", MinigameType.REPLACE_PART, () -> ModItems.SCREWDRIVER, () -> ModItems.FUSE, 0, 0, 10),
	WIRING("wiring", MinigameType.WIRES, () -> ModItems.PLIERS, () -> ModItems.CABLE, 1, 0, 8),
	ELECTRICAL_PANEL("electrical_panel", MinigameType.SEQUENCE, () -> ModItems.SCREWDRIVER, null, 0, 0, 10),
	LIGHTING("lighting", MinigameType.HOLD, () -> ModItems.PLIERS, () -> ModItems.FUSE, 1, 5.0, 8),
	AUTO_DOOR("auto_door", MinigameType.HOLD, () -> ModItems.WRENCH, null, 0, 6.0, 18),
	VENTILATION("ventilation", MinigameType.HOLD, () -> ModItems.WRENCH, null, 0, 7.0, 20),
	TERMINAL("terminal", MinigameType.CODE, null, null, 0, 0, 6),
	SECURITY("security", MinigameType.WIRES, () -> ModItems.SCREWDRIVER, () -> ModItems.ELECTRONIC_PARTS, 1, 0, 10),
	/** Cámara de seguridad: averiada muestra estática en el monitor. */
	CAMERA("camera", MinigameType.HOLD, () -> ModItems.SCREWDRIVER, null, 0, 4.0, 6);

	private final String id;
	private final MinigameType minigame;
	private final Supplier<Item> tool;
	private final Supplier<Item> part;
	private final int partCount;
	private final double holdSeconds;
	private final int noiseRadius;

	RepairType(String id, MinigameType minigame, Supplier<Item> tool, Supplier<Item> part, int partCount, double holdSeconds, int noiseRadius) {
		this.id = id;
		this.minigame = minigame;
		this.tool = tool;
		this.part = part;
		this.partCount = partCount;
		this.holdSeconds = holdSeconds;
		this.noiseRadius = noiseRadius;
	}

	public String id() {
		return id;
	}

	public MinigameType minigame() {
		return minigame;
	}

	/** Herramienta necesaria, o null si se repara a mano. */
	public Item tool() {
		return tool == null ? null : tool.get();
	}

	/** Repuesto consumido, o null. */
	public Item part() {
		return part == null ? null : part.get();
	}

	/** Cantidad fija de repuestos. En REPLACE_PART depende del número de piezas quemadas. */
	public int partCount() {
		return partCount;
	}

	public double holdSeconds() {
		return holdSeconds;
	}

	public int noiseRadius() {
		return noiseRadius;
	}

	public String translationKey() {
		return "repair.fnaf_night_shift." + id;
	}

	@Override
	public String asString() {
		return id;
	}
}
