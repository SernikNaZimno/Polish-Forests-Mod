package pl.polishforests.worldgen.habitat;

import pl.polishforests.worldgen.landscape.Landform;

/**
 * Vegetation belts of the Beskids and the Foothills (§5.1, ecology report §3, after Babia Gora). The only place
 * with belt elevations, including the nominal upper montane boundary of 1150 m, used by the terrain description
 * ({@code LandscapeModel.describe}), the M1 biome source and the commands.
 *
 * <pre>
 * dT                = 40·noise(λ 150k)                                    // m
 * aspectOffset      = slope ≥ 5° ? 50·cos(aspect − 180°) : 0              // S slope +50 m, N slope −50 m
 * lowerMontaneLimit = 550  + aspectOffset + dT
 * upperMontaneLimit = 1150 + aspectOffset + dT
 * timberline        = 1390 + aspectOffset − 60·[RIDGE and slope &lt; 15°] + dT
 * alpineThreshold   = 1650 + aspectOffset + dT
 * largeMassif       = summit within 3 km·mspace > 1470 m (E12), from the terrain.summit field
 * </pre>
 *
 * At gameplay scale the slope is converted to blocks ({@link HabitatClassifier.Column#slope}). The beech and spruce
 * ranges (§9) are not checked here: in the mountain belt (wFoothills + wBeskids > 0.5) P is close to 1, and both
 * species have their range at P ≥ 0.4–0.5, so montane beech and spruce forests lie within range by the geography
 * of the regional fields. The upland fir forest below the lower montane belt has the same conditions as the zonal
 * one ({@link HabitatClassifier#firForest}) and does not enter valley floors.
 */
public final class AltitudinalBelts {
	private AltitudinalBelts() {
	}

	/** Nominal belt elevations (m a.s.l.), before the aspect and noise correction. */
	public static final double LOWER_MONTANE = 550;
	public static final double UPPER_MONTANE = 1_150;
	public static final double TIMBERLINE = 1_390;
	public static final double ALPINE_THRESHOLD = 1_650;
	/**
	 * Highest summit within 3 km·mspace from which a massif has dwarf pine and alpine grassland (E12). The
	 * {@code terrain.summit} field is the maximum of the terrain without valleys from the {@code PeakField} grid, so the
	 * threshold is directly in meters of summit elevation.
	 */
	public static final double LARGE_MASSIF = 1_470;
	/** Threshold shift by aspect (m) and amplitude of the transition noise (m, wavelength 150 m·k). */
	public static final double ASPECT = 50;
	/** Aspect applies from this slope (°); below it the terrain is flat. */
	public static final double ASPECT_MIN_SLOPE = 5;
	public static final double TEMPERATURE_NOISE = 40;
	public static final double NOISE_WAVELENGTH = 150;
	/** Lowering of the timberline on wind-exposed ridges (m) at slopes below 15°. */
	public static final double WINDY_RIDGE = 60;
	/** Belt of stunted spruces below the timberline (m). */
	public static final double BORDER_BAND = 60;
	/** Largest possible lowering of a threshold (aspect and noise), used to skip the noise low down. */
	public static final double MAX_LOWERING = ASPECT + TEMPERATURE_NOISE;
	/**
	 * Lowest elevation at which a large massif changes anything (the timberline band on a ridge at the largest
	 * lowering). The model computes the {@code terrain.summit} field only from this elevation.
	 */
	public static final double SUMMIT_FROM = TIMBERLINE - MAX_LOWERING - WINDY_RIDGE - BORDER_BAND;
	/** Wind-exposed ridge: slope below (°). */
	static final double WINDY_RIDGE_SLOPE = 15;
	/** Above this elevation in the lower montane belt, N slopes have Abieti-Piceetum (m). */
	static final double ABIETI_N = 900;
	/** Inversion: valley floors above this elevation with a floor half-width > 30 m·k (m). */
	static final double ABIETI_FLOOR = 700;
	static final double ABIETI_FLOOR_K = 30;
	/** Foothills: Carpathian beech forest on N slopes above (m). */
	static final double FOOTHILLS_BEECH = 450;
	/** Oak-hornbeam forest in the foothills on L sites below (m). */
	static final double FOOTHILLS_OAK_HORNBEAM = 400;
	/** Sycamore ravine forest: slope above (°) on N–E slopes (azimuth 0–90°), in the lower part of the slope. */
	static final double SYCAMORE_RAVINE_SLOPE = 30;
	/** Tall herbs in gullies of the upper montane belt: flysch profile below. */
	static final double GULLY_RIDGE = 0.15;
	/** Sycamore ravine forest in the lower part of the slope: flysch profile below. */
	static final double SYCAMORE_RAVINE_RIDGE = 0.5;
	/** Sycamore ravine forest on slopes from N to E: aspect at most (°). */
	static final double SYCAMORE_RAVINE_ASPECT = 90;

	/** Nominal upper montane belt (no correction): terrain description, commands, M1 biome source. */
	public static boolean isUpperMontane(double meters) {
		return meters >= UPPER_MONTANE;
	}

	/** Threshold shift: aspect and noise (m). */
	static double correction(HabitatClassifier.Column c) {
		double dT = TEMPERATURE_NOISE * c.classifier.belts.at(c.x, c.z, NOISE_WAVELENGTH * c.k);
		return ASPECT * c.aspect() + dT;
	}

	/** Upper limit of the lower montane belt in a column (m), with aspect and noise. */
	static double upperMontaneLimit(HabitatClassifier.Column c) {
		return UPPER_MONTANE + correction(c);
	}

	/** Large massif: a summit within 3 km·mspace above {@link #LARGE_MASSIF} (E12). */
	static boolean largeMassif(HabitatClassifier.Column c) {
		return c.t.summit() > LARGE_MASSIF;
	}

	/** Classifier step 4: belts in the mountain belt (wFoothills + wBeskids > 0.5); {@code Result.NONE} outside it and low down. */
	static int classify(HabitatClassifier.Column c) {
		if (c.wMountains <= 0.5) {
			return HabitatClassifier.Result.NONE;
		}
		double h = c.H;
		double corr = correction(c);
		boolean large = largeMassif(c);
		boolean onRidge = c.t.has(Landform.RIDGE);
		if (large && h >= ALPINE_THRESHOLD + corr) {
			return HabitatClassifier.Result.of(HabitatBiome.ALPINE_GRASSLAND, Zone.NONE, Association.TYPICAL);
		}
		double limit = TIMBERLINE + corr - (onRidge && c.slope < WINDY_RIDGE_SLOPE ? WINDY_RIDGE : 0);
		if (large && h >= limit) {
			return HabitatClassifier.Result.of(HabitatBiome.DWARF_PINE_SCRUB, Zone.NONE, Association.TYPICAL);
		}
		if (h >= UPPER_MONTANE + corr) {
			Zone s = large && h >= limit - BORDER_BAND ? Zone.TIMBERLINE : Zone.NONE;
			double p0 = c.t.ridgeProfile();
			Association z = !Double.isNaN(p0) && p0 < GULLY_RIDGE ? Association.GULLY_TALL_HERBS : Association.TYPICAL;
			return HabitatClassifier.Result.of(HabitatBiome.MONTANE_SPRUCE_FOREST, s, z);
		}
		Fertility t = c.fertility();
		boolean poor = t == Fertility.OLIGOTROPHIC || t == Fertility.OLIGO_MESOTROPHIC;
		double aspectOffset = c.aspect();
		if (h >= LOWER_MONTANE + corr) {
			boolean abieti = onRidge && poor || aspectOffset < 0 && h > ABIETI_N
					|| c.onValleyFloor() && h > ABIETI_FLOOR && c.w.floorHalfWidth() > ABIETI_FLOOR_K * c.k;
			if (abieti) {
				return HabitatClassifier.Result.of(HabitatBiome.MONTANE_SPRUCE_FOREST, Zone.NONE, Association.ABIETI_PICEETUM);
			}
			return HabitatClassifier.Result.of(HabitatBiome.MONTANE_BEECH_FOREST, Zone.NONE, beechAssociation(c, poor));
		}
		if (c.wFoothills > c.wBeskids && aspectOffset < 0 && h > FOOTHILLS_BEECH) {
			return HabitatClassifier.Result.of(HabitatBiome.MONTANE_BEECH_FOREST, Zone.NONE, beechAssociation(c, poor));
		}
		if (t == Fertility.EUTROPHIC && h < FOOTHILLS_OAK_HORNBEAM) {
			return HabitatClassifier.Result.of(HabitatBiome.OAK_HORNBEAM_FOREST, Zone.NONE, Association.TYPICAL);
		}
		if (HabitatClassifier.firForest(c, t) && c.moisture() != Moisture.BOGGY) {
			return HabitatClassifier.Result.of(HabitatBiome.UPLAND_FIR_FOREST, Zone.NONE, Association.TYPICAL);
		}
		return HabitatClassifier.Result.NONE;
	}

	/** Sycamore ravine forest on steep N–E slopes in the lower part of the slope, acidophilous form on poor sites. */
	private static Association beechAssociation(HabitatClassifier.Column c, boolean poor) {
		double e = c.t.aspect();
		double p0 = c.t.ridgeProfile();
		if (c.slope > SYCAMORE_RAVINE_SLOPE && !Double.isNaN(e) && e <= SYCAMORE_RAVINE_ASPECT && !Double.isNaN(p0)
				&& p0 < SYCAMORE_RAVINE_RIDGE) {
			return Association.SYCAMORE_RAVINE_FOREST;
		}
		return poor ? Association.ACIDOPHILOUS : Association.TYPICAL;
	}
}
