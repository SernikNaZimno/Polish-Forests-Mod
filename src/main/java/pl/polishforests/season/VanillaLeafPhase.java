package pl.polishforests.season;

/**
 * The phase (0-3, the jitter of the leaf-out and autumn dates as the {@code phase} of the species leaves) of a vanilla
 * leaf block in the Polska world by its column (step V13, decision V13-i; review round 1): the shrubs of the slice and the
 * M2 placeholder trees stand on vanilla leaves, which have no phase of their tree, so the phase comes from the position.
 *
 * <p>The phase is a low-frequency value noise ({@value #CELL}-block cells, smoothly interpolated, plus a weak octave of
 * {@value #DETAIL_CELL} blocks that frays the edges) cut into four phases at its quartiles, so the phases form patches of
 * tens of blocks, a crown of a few blocks mostly has one phase and switches at once (at a given color step only one edge
 * between phases shows, and about one crown 7 blocks wide in ten crosses it), and where two phases meet the edge is an
 * irregular curve, never a straight line along the block grid (rule Z10). The review of V13 found the earlier cells of
 * 4 x 4 columns splitting every larger crown into bare and leafy blocks along straight lines for about two color steps
 * in spring and autumn. Pure function of the column, the same on the client (models, falling leaves) and in the tests.
 */
public final class VanillaLeafPhase {
	/** Cell of the main octave, blocks. */
	static final int CELL = 64;
	/** Cell of the fraying octave, blocks. */
	static final int DETAIL_CELL = 11;
	/** Weight of the fraying octave against the main one. */
	static final double DETAIL = 0.15;
	/** The quartiles of the noise (equal shares of the four phases), measured once over a large area. */
	private static final double[] QUARTILES = quartiles();

	private VanillaLeafPhase() {
	}

	/** The phase (0-3) of a vanilla leaf block in column (x, z). */
	public static int phase(int x, int z) {
		double v = noise(x, z);
		int p = 0;
		while (p < 3 && v >= QUARTILES[p]) {
			p++;
		}
		return p;
	}

	/** The noise in [0, 1 + {@link #DETAIL}): two octaves of value noise. */
	static double noise(int x, int z) {
		return value(x, z, CELL, 0x51A7L) + DETAIL * value(x, z, DETAIL_CELL, 0xF2E1L);
	}

	private static double value(int x, int z, int cell, long salt) {
		int cx = Math.floorDiv(x, cell);
		int cz = Math.floorDiv(z, cell);
		double fx = smooth((x - cx * cell + 0.5) / cell);
		double fz = smooth((z - cz * cell + 0.5) / cell);
		double a = corner(cx, cz, salt);
		double b = corner(cx + 1, cz, salt);
		double c = corner(cx, cz + 1, salt);
		double d = corner(cx + 1, cz + 1, salt);
		return a + (b - a) * fx + (c - a) * fz + (a - b - c + d) * fx * fz;
	}

	private static double smooth(double t) {
		return t * t * (3 - 2 * t);
	}

	private static double corner(int cx, int cz, long salt) {
		long h = cx * 0x9E3779B97F4A7C15L ^ cz * 0xC2B2AE3D27D4EB4FL ^ salt;
		h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
		h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
		h ^= h >>> 31;
		return (h >>> 11) * 0x1.0p-53;
	}

	private static double[] quartiles() {
		int n = 0;
		double[] v = new double[160 * 160];
		for (int i = 0; i < 160; i++) {
			for (int j = 0; j < 160; j++) {
				v[n++] = noise(i * 13 - 1040, j * 13 - 1040);
			}
		}
		java.util.Arrays.sort(v);
		return new double[] {v[n / 4], v[n / 2], v[3 * n / 4]};
	}
}
