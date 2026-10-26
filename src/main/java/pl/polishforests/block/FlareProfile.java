package pl.polishforests.block;

/**
 * The profile of a root flare's fin (step I1, review round 1; {@link RootFlareBlock}), without game classes so the root
 * preview can draw it: a lying flare block of stage s runs from the height {@link #height}(s) at its parent's side down
 * to {@link #height}(s - 1) at the far side (stage 0: the ground), a fin of {@link #finWidth} on a foot of
 * {@link #footWidth} that reaches {@link #FOOT_SHARE} of the height. The spruce's flat surface roots are the foot alone,
 * low and about as wide as high. Heights and widths in px.
 */
public final class FlareProfile {
	/** Highest stage (the {@code radius} property of the block). */
	public static final int MAX_STAGE = TrunkBlock.MAX_RADIUS;
	/** Share of the fin's height that the foot reaches. */
	public static final double FOOT_SHARE = 0.4;

	private static final double[] RIDGE = {0, 5, 10, 13, 15, 16, 16, 16};
	private static final double[] FLAT = {0, 2, 3, 4, 5, 6, 6, 6};
	private static final double[] RIDGE_FIN = {0, 2, 2.5, 3, 3.5, 4, 4, 4};
	private static final double[] RIDGE_FOOT = {0, 4, 5, 6, 6.5, 7, 7, 7};
	private static final double[] FLAT_FOOT = {0, 3, 3.5, 4, 4.5, 5, 5, 5};

	private FlareProfile() {
	}

	/** Height of the top of a flare at the far side of a block of stage s (s 0: the ground), 0-16. */
	public static double height(boolean flat, int stage) {
		return (flat ? FLAT : RIDGE)[Math.clamp(stage, 0, MAX_STAGE)];
	}

	/** Height of the top of a flare of stage s at z px from the parent's side of the block (0-16), linear. */
	public static double heightAt(boolean flat, int stage, double z) {
		double h0 = height(flat, stage);
		double h1 = height(flat, stage - 1);
		return h0 + (h1 - h0) * z / 16;
	}

	/** Width of the fin of a block of stage s (a flat flare is its foot alone). */
	public static double finWidth(boolean flat, int stage) {
		return (flat ? FLAT_FOOT : RIDGE_FIN)[Math.clamp(stage, 1, MAX_STAGE)];
	}

	/** Width of the foot of a block of stage s. */
	public static double footWidth(boolean flat, int stage) {
		return (flat ? FLAT_FOOT : RIDGE_FOOT)[Math.clamp(stage, 1, MAX_STAGE)];
	}
}
