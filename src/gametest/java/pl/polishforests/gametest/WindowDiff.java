package pl.polishforests.gametest;

import java.util.ArrayList;
import java.util.List;

/**
 * Differences of the decoration window between two generations of one area in different chunk orders, sorted by kind
 * (step L1 of docs/04-pionowy-wycinek.md, DP-107 and DP-115), without game classes so that it can be tested alone.
 *
 * <p>The window of a column holds {@code window} blocks from {@code below} blocks under its terrain ground (the top
 * terrain block is offset {@code below − 1}); a block is given as a state number, and {@code classes} gives the class of
 * each state number ({@link #AIR}, {@link #FLOOR}: a plant, a litter or a carpet of the forest floor, {@link #OBJECT}: wood,
 * leaves, a hanging vine, a trunk, a root flare or a root, {@link #OTHER}: anything else). A differing block is of one kind:
 * <ul>
 * <li>{@link Kind#UNDERGROUND}: below the top terrain block (vanilla ores, sculk and caves, roots in the soil);</li>
 * <li>{@link Kind#GROUND}: the top terrain block (the ground under a plant, a vanilla disk of sand or clay);</li>
 * <li>{@link Kind#OBJECT}: above the ground, an object or another block on one side (a tree, a log or a rock that
 * crosses a chunk border is placed by whichever chunk comes first, as in vanilla);</li>
 * <li>{@link Kind#FLOOR_CONTACT}: above the ground, only floor blocks and air, within {@link #CONTACT} blocks
 * (Chebyshev distance, in 3D) of an object in either generation or of another kind of difference: a plant whose place a
 * neighbor's object took, the moss collar of a trunk or the litter of a log, which depend on the order as the object
 * does; also every floor difference within {@link #CONTACT} blocks of the area's edge, whose objects outside the area
 * are not compared;</li>
 * <li>{@link Kind#FLOOR_FREE}: any other difference of the floor: the floor layers away from every object, which are a
 * pure function of the world seed, the position and the settings since step L1 and must be identical in any order.</li>
 * </ul>
 */
final class WindowDiff {
	static final byte AIR = 0;
	static final byte FLOOR = 1;
	static final byte OBJECT = 2;
	static final byte OTHER = 3;
	/** Distance (blocks, Chebyshev in 3D) within which an object or another difference explains a floor difference. */
	static final int CONTACT = 2;

	enum Kind {
		UNDERGROUND, GROUND, OBJECT, FLOOR_CONTACT, FLOOR_FREE
	}

	/** One differing block: area-local block coordinates (x, z from the area corner), world y, window offset, states. */
	record Block(int x, int y, int z, int k, Kind kind, int ours, int theirs) {
	}

	/** Counts by kind (index {@link Kind#ordinal}) and the first {@code limit} differing blocks above the ground. */
	record Result(int[] counts, List<Block> blocks) {
		int count(Kind kind) {
			return counts[kind.ordinal()];
		}
	}

	private WindowDiff() {
	}

	/**
	 * Compares two windows of an area of {@code size} × {@code size} chunks.
	 *
	 * @param a       window of generation A, index (column · window + offset), column = (chunk x · size + chunk z) · 256
	 *                + x · 16 + z
	 * @param b       window of generation B, same layout
	 * @param ground  terrain ground of each column (the first block above the terrain), the same in A and B
	 * @param classes class of each state number
	 * @param limit   most differing blocks above the ground kept in the result
	 */
	static Result of(int[] a, int[] b, int[] ground, byte[] classes, int size, int window, int below, int limit) {
		int side = size * 16;
		int columns = ground.length;
		// Positions that explain a floor difference nearby: an object on either side, or a difference of another kind.
		boolean[] source = new boolean[a.length];
		for (int i = 0; i < a.length; i++) {
			int k = i % window;
			byte ca = classes[a[i]];
			byte cb = classes[b[i]];
			source[i] = ca == OBJECT || cb == OBJECT
					|| a[i] != b[i] && (k < below || ca == OTHER || cb == OTHER);
		}
		int[] counts = new int[Kind.values().length];
		List<Block> blocks = new ArrayList<>();
		int[] columnAt = new int[side * side];
		for (int c = 0; c < columns; c++) {
			columnAt[gx(c, size) * side + gz(c, size)] = c;
		}
		for (int c = 0; c < columns; c++) {
			for (int k = 0; k < window; k++) {
				int i = c * window + k;
				if (a[i] == b[i]) {
					continue;
				}
				Kind kind;
				if (k < below - 1) {
					kind = Kind.UNDERGROUND;
				} else if (k == below - 1) {
					kind = Kind.GROUND;
				} else if (source[i]) {
					kind = Kind.OBJECT;
				} else {
					kind = near(source, ground, columnAt, c, ground[c] - below + k, size, window, below) ? Kind.FLOOR_CONTACT
							: Kind.FLOOR_FREE;
				}
				counts[kind.ordinal()]++;
				if (kind != Kind.UNDERGROUND && blocks.size() < limit) {
					blocks.add(new Block(gx(c, size), ground[c] - below + k, gz(c, size), k, kind, a[i], b[i]));
				}
			}
		}
		return new Result(counts, blocks);
	}

	/** Whether a source lies within {@link #CONTACT} blocks of world height y in column c. */
	private static boolean near(boolean[] source, int[] ground, int[] columnAt, int c, int y, int size, int window, int below) {
		int side = size * 16;
		int x = gx(c, size);
		int z = gz(c, size);
		if (x < CONTACT || z < CONTACT || x >= side - CONTACT || z >= side - CONTACT) {
			// The blocks of the chunks around the area are not compared: an object there may explain this difference.
			return true;
		}
		for (int dx = -CONTACT; dx <= CONTACT; dx++) {
			for (int dz = -CONTACT; dz <= CONTACT; dz++) {
				int nx = x + dx;
				int nz = z + dz;
				if (nx < 0 || nz < 0 || nx >= side || nz >= side) {
					continue;
				}
				int n = columnAt[nx * side + nz];
				int y0 = ground[n] - below;
				for (int dy = -CONTACT; dy <= CONTACT; dy++) {
					int k = y + dy - y0;
					if (k >= 0 && k < window && source[n * window + k]) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/** Area-local x of a column. */
	static int gx(int column, int size) {
		int chunk = column / 256;
		return chunk / size * 16 + column % 256 / 16;
	}

	/** Area-local z of a column. */
	static int gz(int column, int size) {
		int chunk = column / 256;
		return chunk % size * 16 + column % 16;
	}
}
