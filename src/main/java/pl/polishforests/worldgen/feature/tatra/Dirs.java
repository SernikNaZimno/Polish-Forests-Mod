package pl.polishforests.worldgen.feature.tatra;

/** The four horizontal directions of the Tatra detail planners (step TT6): 0 north (-z), 1 east (+x), 2 south (+z), 3 west (-x). */
public final class Dirs {
	public static final int[] DX = {0, 1, 0, -1};
	public static final int[] DZ = {-1, 0, 1, 0};

	private Dirs() {
	}

	public static int opposite(int d) {
		return (d + 2) & 3;
	}

	/** The direction a quarter turn clockwise (north to east). */
	public static int right(int d) {
		return (d + 1) & 3;
	}
}
