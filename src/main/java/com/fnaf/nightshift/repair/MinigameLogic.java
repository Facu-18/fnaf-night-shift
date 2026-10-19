package com.fnaf.nightshift.repair;

import java.util.Random;

/**
 * Generación determinista de los puzles a partir de una semilla. El cliente
 * la usa para dibujar el minijuego y el servidor para validar la respuesta,
 * así nunca se confía en que el cliente diga "lo resolví".
 */
public final class MinigameLogic {
	public static final int WIRE_COUNT = 4;
	public static final int SEQUENCE_BUTTONS = 6;
	public static final int CODE_LENGTH = 4;
	public static final int FUSE_SLOTS = 6;

	private MinigameLogic() {
	}

	/** perm[j] = color del terminal derecho j. Los cables izquierdos están en orden 0..n-1. */
	public static int[] wires(long seed) {
		Random r = new Random(seed ^ 0x57A1E5L);
		int[] perm = new int[WIRE_COUNT];
		for (int i = 0; i < WIRE_COUNT; i++) perm[i] = i;
		for (int i = WIRE_COUNT - 1; i > 0; i--) {
			int j = r.nextInt(i + 1);
			int t = perm[i];
			perm[i] = perm[j];
			perm[j] = t;
		}
		// evitar que salga ya resuelto
		boolean identity = true;
		for (int i = 0; i < WIRE_COUNT; i++) identity &= perm[i] == i;
		if (identity) {
			int t = perm[0];
			perm[0] = perm[1];
			perm[1] = t;
		}
		return perm;
	}

	public static int sequenceLength(int level) {
		return Math.min(9, 3 + level / 2);
	}

	public static int[] sequence(long seed, int level) {
		Random r = new Random(seed ^ 0x5E9L);
		int[] seq = new int[sequenceLength(level)];
		for (int i = 0; i < seq.length; i++) {
			int v;
			do {
				v = r.nextInt(SEQUENCE_BUTTONS);
			} while (i > 0 && v == seq[i - 1]);
			seq[i] = v;
		}
		return seq;
	}

	public static int[] code(long seed) {
		Random r = new Random(seed ^ 0xC0DEL);
		int[] code = new int[CODE_LENGTH];
		for (int i = 0; i < CODE_LENGTH; i++) code[i] = r.nextInt(10);
		return code;
	}

	/** Segundos durante los que se muestra el código antes de ocultarse. */
	public static int codeDisplayTicks(int level) {
		return Math.max(40, 100 - level * 8);
	}

	public static boolean[] burntFuses(long seed, int level) {
		Random r = new Random(seed ^ 0xF05EL);
		boolean[] burnt = new boolean[FUSE_SLOTS];
		int count = Math.min(FUSE_SLOTS - 1, 2 + level / 3);
		int placed = 0;
		while (placed < count) {
			int i = r.nextInt(FUSE_SLOTS);
			if (!burnt[i]) {
				burnt[i] = true;
				placed++;
			}
		}
		return burnt;
	}

	public static int burntCount(long seed, int level) {
		int n = 0;
		for (boolean b : burntFuses(seed, level)) if (b) n++;
		return n;
	}

	/**
	 * Valida la respuesta enviada por el cliente.
	 *
	 * @param answer WIRES: answer[i] = terminal derecho elegido para el cable i.
	 *               SEQUENCE / CODE: valores introducidos en orden.
	 *               REPLACE_PART: índices de fusibles sustituidos.
	 */
	public static boolean validate(MinigameType type, long seed, int level, int[] answer) {
		if (answer == null) return false;
		switch (type) {
			case WIRES -> {
				int[] perm = wires(seed);
				if (answer.length != WIRE_COUNT) return false;
				for (int i = 0; i < WIRE_COUNT; i++) {
					int j = answer[i];
					if (j < 0 || j >= WIRE_COUNT || perm[j] != i) return false;
				}
				return true;
			}
			case SEQUENCE -> {
				return java.util.Arrays.equals(sequence(seed, level), answer);
			}
			case CODE -> {
				return java.util.Arrays.equals(code(seed), answer);
			}
			case REPLACE_PART -> {
				boolean[] burnt = burntFuses(seed, level);
				boolean[] replaced = new boolean[FUSE_SLOTS];
				for (int i : answer) {
					if (i < 0 || i >= FUSE_SLOTS || !burnt[i] || replaced[i]) return false;
					replaced[i] = true;
				}
				for (int i = 0; i < FUSE_SLOTS; i++) if (burnt[i] != replaced[i]) return false;
				return true;
			}
			default -> {
				return false;
			}
		}
	}
}
