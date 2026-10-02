package pl.polishforests.worldgen.chunk;

import net.minecraft.world.level.LevelHeightAccessor;
import org.jspecify.annotations.Nullable;

/**
 * Vertical frame of the "Poland" world (decision A4, changed 2026-09-27).
 *
 * <p>Sea level is at the vanilla Y 63 and the world bottom at Y -64, so vanilla underground
 * structures and ores lie beneath the terrain as in a normal world. The engine does not allow blocks above
 * Y 2031, so elevation is mapped 1:1 up to about 900 m a.s.l. and smoothly compressed above that
 * (about 1.76 times above 1200 m). Rysy (2499 m) lands at Y 2000. The landscape model and the vegetation
 * belts always work in real meters.
 *
 * <p>The values must match data/polishforests/dimension_type/poland.json.
 */
public final class PolandDimension {
	public static final int MIN_Y = -64;
	/** Y from -64 to 2031, the engine maximum. */
	public static final int HEIGHT = 2096;
	/** Y of the top face of sea water, i.e. 0 m a.s.l. */
	public static final int SEA_LEVEL_Y = 63;
	/** Below this height stone turns into deepslate, as in vanilla. */
	public static final int DEEP_ROCK_Y = 0;

	/** Elevation at which mountain compression starts (middle of the smooth transition). */
	public static final double KNEE_METERS = 1_200.0;
	/** Width of the smooth transition in meters. */
	private static final double KNEE_SOFTNESS = 80.0;
	/** Compression factor above the knee, chosen so that Rysy lands at Y 2000. */
	public static final double COMPRESSION = 1.7626;
	/** Below this elevation the mapping is an exact identity. */
	private static final double IDENTITY_BELOW = KNEE_METERS - 4 * KNEE_SOFTNESS;
	private static final double SOFTPLUS_AT_IDENTITY_END = softplus(-4.0);
	private static final double SQUEEZE = 1.0 - 1.0 / COMPRESSION;

	/** Inverse table: elevation a.s.l. of the top face of the block for every world Y. */
	private static final double[] METERS_BY_Y = new double[HEIGHT + 1];

	static {
		for (int i = 0; i <= HEIGHT; i++) {
			METERS_BY_Y[i] = metersForBlocks(MIN_Y + i - SEA_LEVEL_Y);
		}
	}

	private PolandDimension() {
	}

	/**
	 * Whether the given level has the vertical frame of the "Poland" world in any scale (recognized by
	 * its height range). It used to recognize only the real scale.
	 */
	public static boolean isPoland(LevelHeightAccessor level) {
		return scaleOf(level) != null;
	}

	/**
	 * Vertical scale recognized by the level's height range: 2096 blocks is the real scale,
	 * 832 is the gameplay scale; null for other frames. This is a fallback; the dimension type
	 * ({@link PolandScale#byDimensionType}) is more reliable.
	 */
	public static @Nullable VerticalScale scaleOf(LevelHeightAccessor level) {
		for (PolandScale scale : PolandScale.values()) {
			VerticalScale v = scale.vertical();
			if (level.getMinY() == v.minY() && level.getHeight() == v.height()) {
				return v;
			}
		}
		return null;
	}

	public static int maxY() {
		return MIN_Y + HEIGHT - 1;
	}

	/** Number of blocks above sea level corresponding to an elevation of {@code meters} a.s.l. */
	public static double blocksForMeters(double meters) {
		if (meters <= IDENTITY_BELOW) {
			return meters;
		}
		double x = (meters - KNEE_METERS) / KNEE_SOFTNESS;
		return meters - SQUEEZE * KNEE_SOFTNESS * (softplus(x) - SOFTPLUS_AT_IDENTITY_END);
	}

	/** Inverse of {@link #blocksForMeters}: elevation a.s.l. for a number of blocks above the sea. */
	public static double metersForBlocks(double blocks) {
		if (blocks <= IDENTITY_BELOW) {
			return blocks;
		}
		double lo = blocks;
		double hi = blocks * COMPRESSION + 1;
		for (int i = 0; i < 60; i++) {
			double mid = 0.5 * (lo + hi);
			if (blocksForMeters(mid) < blocks) {
				lo = mid;
			} else {
				hi = mid;
			}
		}
		return 0.5 * (lo + hi);
	}

	/** Y of the topmost ground block for a surface at an elevation of {@code meters} a.s.l. */
	public static int topBlockY(double meters) {
		return (int) Math.floor(SEA_LEVEL_Y + blocksForMeters(meters)) - 1;
	}

	/** Elevation a.s.l. of the top face of the block at {@code y}. */
	public static double metersAboveSea(int y) {
		int i = y + 1 - MIN_Y;
		if (i >= 0 && i < METERS_BY_Y.length) {
			return METERS_BY_Y[i];
		}
		return metersForBlocks(y + 1 - SEA_LEVEL_Y);
	}

	private static double softplus(double x) {
		return x > 30 ? x : Math.log1p(Math.exp(x));
	}
}
