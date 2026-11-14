package pl.polishforests.worldgen.feature.tatra;

/** Packing of world cells into longs for the planners of the Tatra details (step TT6): x and z within 2^21, Y within 2^19. */
public final class Cells {
	private static final int XZ_BIAS = 1 << 21;
	private static final int Y_BIAS = 1 << 19;

	private Cells() {
	}

	public static long pack(int x, int y, int z) {
		return (long) (x + XZ_BIAS) << 42 | (long) (z + XZ_BIAS) << 20 | (y + Y_BIAS);
	}

	public static int x(long key) {
		return (int) (key >>> 42) - XZ_BIAS;
	}

	public static int z(long key) {
		return (int) ((key >>> 20) & ((1L << 22) - 1)) - XZ_BIAS;
	}

	public static int y(long key) {
		return (int) (key & ((1 << 20) - 1)) - Y_BIAS;
	}
}
