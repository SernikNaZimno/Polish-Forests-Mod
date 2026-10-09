package pl.polishforests.worldgen.habitat;

import pl.polishforests.worldgen.landscape.Landform;
import pl.polishforests.worldgen.landscape.Noise;

/**
 * Forest mask of the PRESENT_DAY mode, "present-day Poland" (ecology report §6, docs/03-m2-biomy.md §3.4 step 6, §4.5,
 * calibrated in step S8). A column of a forest biome stays forest where the quantile of the smooth noise F is lower than
 * the column's P_forest, so at a constant P_forest the forest share equals P_forest. F has two octaves (wavelengths
 * {@link Calibration#F_WAVELENGTH} and {@link Calibration#F_FINE_WAVELENGTH} m·k) and a fine edge noise
 * ({@link Calibration#F_EDGE_WAVELENGTH} m·k, Z9); its quantile comes from a table of its own distribution
 * ({@link #quantile}), so it is uniform on [0, 1]. P_forest depends on the site (biome and STL), the slope and the context
 * (valley side, end moraine, inland dunes, the strip of the gray alder forest by a mountain stream, wide stream floors).
 * Outside forest the non-forest biome follows the site (§2.2, column D); the zone, the potential STL and the association
 * remain, so the terrain, the waterside zones and the potential habitat are the same in both modes. In the NATURAL mode
 * (the default until M8, decision M2-B) the classifier does not use the mask.
 */
final class ForestCover {
	private static final int CDF_BINS = 512;
	/** Distribution of {@link #raw} for a fixed seed; the shape does not depend on the seed. */
	private static final double[] CDF = cdf();

	private final Noise noise;
	private final Noise fine;
	private final double k;

	ForestCover(Noise noise, Noise fine, double k) {
		this.noise = noise;
		this.fine = fine;
		this.k = k;
	}

	/** F before the quantile table: the two octaves and the edge noise, in [−1, 1]. */
	private static double raw(Noise noise, Noise fine, double x, double z, double k) {
		double v = noise.at(x, z, Calibration.F_WAVELENGTH * k) + Calibration.F_FINE * fine.at(x, z, Calibration.F_FINE_WAVELENGTH * k)
				+ Calibration.F_EDGE * noise.at(x + 5_151, z - 919, Calibration.F_EDGE_WAVELENGTH * k);
		return v / (1 + Calibration.F_FINE + Calibration.F_EDGE);
	}

	/** Quantile of F at the point, uniform on [0, 1]. */
	double f(double x, double z) {
		return quantile(raw(noise, fine, x, z, k));
	}

	/** Quantile of a value of {@link #raw} from the table {@link #CDF}. */
	static double quantile(double v) {
		double f = Math.clamp((v + 1) * 0.5 * CDF_BINS, 0.0, CDF_BINS);
		int i = Math.min(CDF_BINS - 1, (int) f);
		return CDF[i] + (CDF[i + 1] - CDF[i]) * (f - i);
	}

	/**
	 * Table of the distribution of {@link #raw} from a grid of 512 × 512 points about 0.17 of the long wavelength apart
	 * (about 87 × 87 of its cells) for a fixed seed: the distribution depends only on the ratios of the wavelengths and
	 * weights, not on the world seed or the scale, so the table is always the same.
	 */
	private static double[] cdf() {
		Noise root = new Noise(0x7A57_F0E5L);
		Noise a = root.derive("cdf.a");
		Noise b = root.derive("cdf.b");
		long[] hist = new long[CDF_BINS];
		long total = 0;
		double step = 0.1713 * Calibration.F_WAVELENGTH;
		for (int i = 0; i < 512; i++) {
			for (int j = 0; j < 512; j++) {
				double v = raw(a, b, i * step + 37.3, j * step * 0.917 - 11.9, 1.0);
				int bin = (int) Math.clamp((long) Math.floor((v + 1) * 0.5 * CDF_BINS), 0, CDF_BINS - 1);
				hist[bin]++;
				total++;
			}
		}
		double[] cdf = new double[CDF_BINS + 1];
		long acc = 0;
		for (int i = 0; i < CDF_BINS; i++) {
			acc += hist[i];
			cdf[i + 1] = (double) acc / total;
		}
		return cdf;
	}

	/** Whether a column of a forest biome stays forested in the PRESENT_DAY mode. */
	boolean isForested(HabitatClassifier.Column c, HabitatBiome biome, ForestSiteType siteType) {
		double p = forestProbability(c, biome, siteType);
		return p >= 1 || p > 0 && f(c.x, c.z) < p;
	}

	/** P_forest of the column (§4.5, ecology report §6.3, calibrated in S8 with {@code BiomeSharesTest}). */
	static double forestProbability(HabitatClassifier.Column c, HabitatBiome biome, ForestSiteType siteType) {
		double p = switch (biome) {
			case DWARF_PINE_SCRUB -> 1;
			case COASTAL_PINE_FOREST -> Calibration.P_COASTAL_PINE;
			case WILLOW_POPLAR_FOREST -> Calibration.P_WILLOW_POPLAR;
			case ELM_ASH_FOREST -> Calibration.P_ELM_ASH;
			case ASH_ALDER_FOREST -> Calibration.P_ASH_ALDER;
			case ALDER_CARR -> Calibration.P_ALDER_CARR;
			case GRAY_ALDER_FOREST -> grayAlder(c);
			case MONTANE_SPRUCE_FOREST -> Calibration.P_MONTANE_SPRUCE;
			case MONTANE_BEECH_FOREST -> c.onValleyFloor() ? Calibration.P_MOUNTAIN_FLOOR : Calibration.P_MONTANE_BEECH;
			case UPLAND_FIR_FOREST -> c.onValleyFloor() ? Calibration.P_MOUNTAIN_FLOOR : Calibration.P_UPLAND_FIR;
			default -> site(c, siteType);
		};
		if (wet(biome) || p >= 1) {
			// Floors and carrs: meadows by the water, no slope context (a valley floor is flat).
			return p;
		}
		return context(c, p);
	}

	/** Riparian forests, alder carr and gray alder forest: their P_forest has no slope context. */
	private static boolean wet(HabitatBiome b) {
		return switch (b) {
			case WILLOW_POPLAR_FOREST, ELM_ASH_FOREST, ASH_ALDER_FOREST, ALDER_CARR, GRAY_ALDER_FOREST -> true;
			default -> false;
		};
	}

	/** Base P_forest of a zonal site by its STL (lowland sites, the foothills by the slope context). */
	private static double site(HabitatClassifier.Column c, ForestSiteType siteType) {
		double p = switch (siteType) {
			case DRY_CONIFEROUS -> Calibration.P_DRY_CONIFEROUS;
			case FRESH_CONIFEROUS -> Calibration.P_FRESH_CONIFEROUS;
			case MOIST_CONIFEROUS, MOIST_MIXED_CONIFEROUS -> Calibration.P_MOIST_CONIFEROUS;
			case BOGGY_CONIFEROUS, BOGGY_MIXED_CONIFEROUS -> Calibration.P_BOGGY_CONIFEROUS;
			case FRESH_MIXED_CONIFEROUS -> Calibration.P_FRESH_MIXED_CONIFEROUS;
			case FRESH_MIXED_BROADLEAVED -> Calibration.P_FRESH_MIXED_BROADLEAVED;
			case MOIST_MIXED_BROADLEAVED, BOGGY_MIXED_BROADLEAVED -> Calibration.P_MOIST_MIXED_BROADLEAVED;
			case FRESH_BROADLEAVED -> Calibration.P_FRESH_BROADLEAVED;
			case MOIST_BROADLEAVED -> Calibration.P_MOIST_BROADLEAVED;
			default -> Calibration.P_OTHER;
		};
		if (c.sandy()) {
			// Sands: large forest complexes on the outwash plain, partly farmed sand patches elsewhere.
			p *= Noise.lerp(Math.clamp(c.wOutwashPlain, 0.0, 1.0), Calibration.P_SAND_OUTSIDE_OUTWASH, 1.0);
		}
		// Foothills: farmland on the gentle slopes and floors, forest on the steep slopes (context).
		return Noise.lerp(Math.clamp(c.wFoothills, 0.0, 1.0), p, Math.min(p, Calibration.P_FOOTHILLS));
	}

	/**
	 * Context of a dry-land site: steep slopes, valley sides, end moraines and inland dunes stay forested more often
	 * (forests "on the poorest soils and where nothing else pays", docs/research/06 §1.3): P_forest moves towards
	 * {@link Calibration#P_STEEP} by the steepness 0–1.
	 */
	private static double context(HabitatClassifier.Column c, double p) {
		boolean gameplay = c.classifier.gameplay;
		double steep = Noise.smoothstep(gameplay ? Calibration.P_SLOPE_FROM_GAMEPLAY : Calibration.P_SLOPE_FROM,
				gameplay ? Calibration.P_SLOPE_TO_GAMEPLAY : Calibration.P_SLOPE_TO, c.slope);
		if (c.t.has(Landform.END_MORAINE) || c.t.has(Landform.INLAND_DUNES)) {
			steep = Math.max(steep, Calibration.P_MORAINE_DUNE);
		}
		if (!c.sandy() && c.valleySlope()) {
			steep = Math.max(steep, gameplay ? Calibration.P_VALLEY_SIDE_GAMEPLAY : Calibration.P_VALLEY_SIDE);
		}
		return p < Calibration.P_STEEP ? p + (Calibration.P_STEEP - p) * steep : p;
	}

	/**
	 * Gray alder forest (§4.5): the strip by the stream (5–20 m·k, 2 W, with the zone jitter) always stays; beyond it
	 * meadows on floors wider than 80 m·k below 900 m, elsewhere {@link Calibration#P_GRAY_ALDER}.
	 */
	private static double grayAlder(HabitatClassifier.Column c) {
		double w = Double.isNaN(c.w.channelWidth()) ? 0 : c.w.channelWidth();
		double strip = Math.clamp(Calibration.D_GRAY_ALDER_STRIP_W * w, Calibration.D_GRAY_ALDER_STRIP_MIN_K * c.k,
				Calibration.D_GRAY_ALDER_STRIP_MAX_K * c.k) * c.jitter();
		if (Math.max(0, c.w.channelDist()) <= strip) {
			return 1;
		}
		boolean wide = c.w.floorHalfWidth() > 0.5 * Calibration.D_STREAM_MEADOW_FLOOR_K * c.k;
		return wide && c.H < Calibration.D_STREAM_MEADOW_H ? 0 : Calibration.P_GRAY_ALDER;
	}

	/** Non-forest biome for an unforested site (§2.2, column D). */
	HabitatBiome nonForest(HabitatClassifier.Column c, HabitatBiome biome, ForestSiteType siteType) {
		double q = c.variant(3);
		switch (biome) {
			case WILLOW_POPLAR_FOREST, ASH_ALDER_FOREST, GRAY_ALDER_FOREST:
				return HabitatBiome.WET_MEADOW;
			case ELM_ASH_FOREST:
				return q < Calibration.D_RIPARIAN_MEADOW ? HabitatBiome.WET_MEADOW : HabitatBiome.ARABLE_LAND;
			case ALDER_CARR:
				return q < Calibration.D_ALDER_CARR_MEADOW ? HabitatBiome.WET_MEADOW : HabitatBiome.FEN;
			case COASTAL_PINE_FOREST:
				return HabitatBiome.GRAY_DUNE;
			case MONTANE_BEECH_FOREST, MONTANE_SPRUCE_FOREST, UPLAND_FIR_FOREST:
				return HabitatBiome.HAY_MEADOW;
			default:
				break;
		}
		return switch (siteType) {
			case DRY_CONIFEROUS -> HabitatBiome.HEATH;
			case FRESH_CONIFEROUS -> q < Calibration.D_PINE_ARABLE ? HabitatBiome.ARABLE_LAND : HabitatBiome.HEATH;
			case MOIST_CONIFEROUS, MOIST_MIXED_CONIFEROUS -> q < Calibration.D_PINE_ARABLE ? HabitatBiome.WET_MEADOW : HabitatBiome.ARABLE_LAND;
			case BOGGY_CONIFEROUS, BOGGY_MIXED_CONIFEROUS -> HabitatBiome.RAISED_BOG;
			case MOIST_BROADLEAVED, MOIST_MIXED_BROADLEAVED, BOGGY_MIXED_BROADLEAVED, ALDER_SWAMP -> HabitatBiome.WET_MEADOW;
			default -> c.slope < Calibration.D_ARABLE_SLOPE ? (q < Calibration.D_ARABLE ? HabitatBiome.ARABLE_LAND : HabitatBiome.HAY_MEADOW)
					: HabitatBiome.HAY_MEADOW;
		};
	}
}
