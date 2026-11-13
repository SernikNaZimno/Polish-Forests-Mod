package pl.polishforests.worldgen.landscape;

/**
 * Horizontal scale of the landscape. The model always computes in meters; this class says how large
 * the individual orders of landforms are.
 *
 * @param regionSize      mean macroregion size in meters (with the slider at 100%)
 * @param meso            multiplier of medium landforms: river valleys, tunnel valleys, moraine belts, dune fields, base level
 * @param local           multiplier of local landforms: hummocks, kettle ponds, ridges, tunnel valley lakes
 * @param mountainSpacing multiplier of ridge and valley spacing in the mountains
 * @param channel         multiplier of river channel width
 */
public record LandscapeScale(String id, double regionSize, double meso, double local, double mountainSpacing,
		double channel) {
	/** Real sizes: macroregions about 64 km, landforms 1:1. */
	public static final LandscapeScale REALISTIC = new LandscapeScale("realistic", 64_000, 1.0, 1.0, 1.0, 1.0);

	/**
	 * Gameplay-friendly scale: landscapes about 1.4 km, i.e. about twice the size of typical vanilla
	 * biomes. Medium landforms are about 7 times smaller, local ones half the size, and heights are compressed
	 * by a separate vertical mapping so that slope gradients stay similar.
	 */
	public static final LandscapeScale GAMEPLAY = new LandscapeScale("gameplay", 1_400, 0.15, 0.5, 0.3, 0.2);

	/** Step TT5: the knee of the realistic vertical curve ({@code PolandDimension}): its middle, softness and compression. */
	static final double REAL_KNEE = 1_200.0;
	static final double REAL_KNEE_SOFTNESS = 80.0;
	static final double REAL_COMPRESSION = 1.7626;

	/**
	 * Step TT5: blocks per meter of height at {@code meters} a.s.l. in the realistic scale, the derivative of the curve of
	 * {@code PolandDimension.blocksForMeters} (the model does not depend on Minecraft, so the constants are repeated here;
	 * {@code LandscapeScaleTest} checks them against the dimension): 1 up to {@code REAL_KNEE − 4 · softness} (880 m), then
	 * {@code 1 − (1 − 1 / compression) · σ((m − knee) / softness)}, about 1 / 1.76 from about 1500 m.
	 */
	public static double realBlocksPerMeter(double meters) {
		if (meters <= REAL_KNEE - 4 * REAL_KNEE_SOFTNESS) {
			return 1.0;
		}
		double x = (meters - REAL_KNEE) / REAL_KNEE_SOFTNESS;
		return 1.0 - (1.0 - 1.0 / REAL_COMPRESSION) / (1.0 + Math.exp(-x));
	}

	/** Ratio of the region size to the real one; scales the zone fields (mountain ranges, glaciation). */
	public double zone() {
		return regionSize / REALISTIC.regionSize;
	}
}
