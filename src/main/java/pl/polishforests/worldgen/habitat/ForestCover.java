package pl.polishforests.worldgen.habitat;

import pl.polishforests.worldgen.landscape.LandscapeModel;
import pl.polishforests.worldgen.landscape.Landform;
import pl.polishforests.worldgen.landscape.Noise;
import pl.polishforests.worldgen.landscape.Substrate;

/**
 * Forest mask of the PRESENT_DAY mode, "present-day Poland" (ecology report §6, docs/03-m2-biomy.md §3.4 step 6, §4.5,
 * calibrated in step S8). A column of a forest biome stays forest where the quantile of the smooth noise F is lower than
 * the column's P_forest, so at a constant P_forest the forest share equals P_forest. F has three octaves (wavelengths
 * {@link Calibration#F_WAVELENGTH}, {@link Calibration#F_FINE_WAVELENGTH} and, since round 1 of the S8 review, the
 * woodlots of {@link Calibration#F_WOODLOT_WAVELENGTH} m·k) and a fine edge noise ({@link Calibration#F_EDGE_WAVELENGTH}
 * m·k, Z9); its quantile comes from a table of its own distribution ({@link #quantile}), so it is uniform on [0, 1].
 * P_forest depends on the site (biome and STL), the slope and the context (valley side, end moraine, inland dunes, the
 * strip of the gray alder forest by a mountain stream, wide stream floors).
 *
 * <p><b>No edges along site boundaries (round 1 of the S8 review).</b> With P_forest stepping at the boundaries of the
 * site types and of binary context flags, and F nearly constant over a few hundred meters, most forest edges lay on those
 * boundaries: on the fresh/moist/boggy thresholds of DGW, which run along the valley sides as straight lines, so the
 * forest formed belts of constant width along the valleys. Now P_forest is continuous across the moisture thresholds
 * (a blend of the P_forest of both site types over a DGW band, the DGW jittered with the context noise), the valley side
 * context grows smoothly with the incision and is broken into stretches by a noise of
 * {@link Calibration#P_VALLEY_SIDE_BREAK_WAVELENGTH} m·k, and the woodlot octave gives F enough variation within a few
 * hundred meters that the edges follow F rather than the remaining site boundaries.
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
	private final Noise woodlots;
	/** Noise of the context and of the open land (round 1 of the S8 review): layers by coordinate offsets. */
	private final Noise context;
	private final double k;

	ForestCover(Noise noise, Noise fine, Noise woodlots, Noise context, double k) {
		this.noise = noise;
		this.fine = fine;
		this.woodlots = woodlots;
		this.context = context;
		this.k = k;
	}

	/** F before the quantile table: the three octaves and the edge noise, in [−1, 1]. */
	private static double raw(Noise noise, Noise fine, Noise woodlots, double x, double z, double k) {
		double v = noise.at(x, z, Calibration.F_WAVELENGTH * k) + Calibration.F_FINE * fine.at(x, z, Calibration.F_FINE_WAVELENGTH * k)
				+ Calibration.F_WOODLOT * woodlots.at(x, z, Calibration.F_WOODLOT_WAVELENGTH * k)
				+ Calibration.F_EDGE * noise.at(x + 5_151, z - 919, Calibration.F_EDGE_WAVELENGTH * k);
		return v / (1 + Calibration.F_FINE + Calibration.F_WOODLOT + Calibration.F_EDGE);
	}

	/** Quantile of F at the point, uniform on [0, 1]. */
	double f(double x, double z) {
		return quantile(raw(noise, fine, woodlots, x, z, k));
	}

	/** Context noise in [−1, 1] at the given wavelength (m·k); {@code layer} separates independent decisions. */
	private double context(HabitatClassifier.Column c, int layer, double wavelength) {
		return context.at(c.x + 1_499.0 * layer, c.z - 2_311.0 * layer, wavelength * k);
	}

	/**
	 * Whether the quantile {@code q} lies below the threshold {@code t}, jittered as the fertility thresholds of step H
	 * (Z9): near the threshold by at most {@link Calibration#FERTILITY_JITTER} and half of the shares on both sides, with
	 * the context noise of {@link Calibration#FERTILITY_JITTER_WAVELENGTH} m·k.
	 */
	private boolean below(HabitatClassifier.Column c, double q, double t, int layer) {
		double a = Math.min(Calibration.FERTILITY_JITTER, 0.5 * Math.min(t, 1 - t));
		if (a > 0 && Math.abs(q - t) < a) {
			t += a * context(c, layer, Calibration.FERTILITY_JITTER_WAVELENGTH);
		}
		return q < t;
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
		Noise w = root.derive("cdf.w");
		long[] hist = new long[CDF_BINS];
		long total = 0;
		double step = 0.1713 * Calibration.F_WAVELENGTH;
		for (int i = 0; i < 512; i++) {
			for (int j = 0; j < 512; j++) {
				double v = raw(a, b, w, i * step + 37.3, j * step * 0.917 - 11.9, 1.0);
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
	double forestProbability(HabitatClassifier.Column c, HabitatBiome biome, ForestSiteType siteType) {
		double p = switch (biome) {
			case DWARF_PINE_SCRUB -> 1;
			case COASTAL_PINE_FOREST -> Calibration.P_COASTAL_PINE;
			case WILLOW_POPLAR_FOREST -> Calibration.P_WILLOW_POPLAR;
			case ELM_ASH_FOREST -> Calibration.P_ELM_ASH;
			case ASH_ALDER_FOREST -> Calibration.P_ASH_ALDER;
			case ALDER_CARR -> alderCarr(c);
			case GRAY_ALDER_FOREST -> grayAlder(c);
			case MONTANE_SPRUCE_FOREST -> Calibration.P_MONTANE_SPRUCE;
			case MONTANE_BEECH_FOREST -> c.onValleyFloor() ? Calibration.P_MOUNTAIN_FLOOR : Calibration.P_MONTANE_BEECH;
			case UPLAND_FIR_FOREST -> c.onValleyFloor() ? Calibration.P_MOUNTAIN_FLOOR : Calibration.P_UPLAND_FIR;
			default -> site(c, biome, siteType);
		};
		if (wet(biome) || p >= 1) {
			// Floors and carrs: meadows by the water, no slope context (a valley floor is flat).
			return p;
		}
		return withContext(c, p);
	}

	/** Riparian forests, alder carr and gray alder forest: their P_forest has no slope context. */
	private static boolean wet(HabitatBiome b) {
		return switch (b) {
			case WILLOW_POPLAR_FOREST, ELM_ASH_FOREST, ASH_ALDER_FOREST, ALDER_CARR, GRAY_ALDER_FOREST -> true;
			default -> false;
		};
	}

	/**
	 * Base P_forest of a zonal site by its STL. Where the biome has a fresh and a moist site type on the column's
	 * fertility, P_forest blends between both over the DGW band DGW_FRESH ± {@link Calibration#P_DGW_BAND} (DGW jittered by
	 * ±{@link Calibration#P_DGW_JITTER} m with the context noise of {@link Calibration#FERTILITY_JITTER_WAVELENGTH} m·k), so
	 * the fresh/moist boundary is no forest edge (round 1 of the S8 review).
	 */
	private double site(HabitatClassifier.Column c, HabitatBiome biome, ForestSiteType siteType) {
		Fertility t = c.fertility();
		ForestSiteType fresh = ForestSiteType.forBiome(biome, t, Moisture.FRESH);
		ForestSiteType moist = ForestSiteType.forBiome(biome, t, Moisture.MOIST);
		if (fresh == moist || siteType != fresh && siteType != moist) {
			return site(c, siteType);
		}
		double d = jitteredDgw(c);
		double w = Noise.smoothstep(Calibration.DGW_FRESH - Calibration.P_DGW_BAND, Calibration.DGW_FRESH + Calibration.P_DGW_BAND, d);
		return Noise.lerp(w, moistSide(c, d, site(c, moist)), site(c, fresh));
	}

	/**
	 * P_forest on the moist side of a fertile zonal site (LM, L) at the jittered DGW {@code d}: from the alder carr at
	 * {@link Calibration#P_CARR_DGW_FROM} to the moist site {@code moist} at {@link Calibration#P_CARR_DGW_TO}. The alder
	 * carr of the same fertility uses the same function ({@link #alderCarr}), so P_forest is continuous across the
	 * boggy/moist boundary. Poor sites (B, BM) have a bog woodland with about the P_forest of the moist site instead.
	 */
	private static double moistSide(HabitatClassifier.Column c, double d, double moist) {
		if (c.sandy()) {
			return moist;
		}
		return Noise.lerp(Noise.smoothstep(Calibration.P_CARR_DGW_FROM, Calibration.P_CARR_DGW_TO, d), Calibration.P_ALDER_CARR, moist);
	}

	/** DGW of the column jittered for the blends of P_forest (context noise layer 1). */
	private double jitteredDgw(HabitatClassifier.Column c) {
		return c.dgw() + Calibration.P_DGW_JITTER * context(c, 1, Calibration.FERTILITY_JITTER_WAVELENGTH);
	}

	/**
	 * Alder carr: {@link Calibration#P_ALDER_CARR}; on mineral ground it blends towards the P_forest of the moist zonal
	 * site of the same fertility as {@link #moistSide} does, so a carr at the foot of a valley side is no forest edge of
	 * its own (round 1 of the S8 review).
	 */
	private double alderCarr(HabitatClassifier.Column c) {
		if (c.substrate == Substrate.PEAT) {
			return Calibration.P_ALDER_CARR;
		}
		return moistSide(c, jitteredDgw(c), site(c, ForestSiteType.zonal(c.fertility(), Moisture.MOIST)));
	}

	/** P_forest of a zonal site type (lowland sites, the foothills by the slope context). */
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
	 * {@link Calibration#P_STEEP} by the steepness 0–1. The valley side (round 1 of the S8 review) grows with the incision
	 * below the pre-valley terrain from {@link Calibration#P_VALLEY_INCISION_FROM} to {@link Calibration#P_VALLEY_INCISION_TO}
	 * m (jittered by ±{@link Calibration#P_VALLEY_INCISION_JITTER} m) instead of a flag at 2 m, and only on stretches
	 * chosen by the context noise of {@link Calibration#P_VALLEY_SIDE_BREAK_WAVELENGTH} m·k (about
	 * {@link Calibration#P_VALLEY_SIDE_STRETCHES} of the valley sides), so forested valley sides are not continuous belts
	 * on both sides of every valley.
	 */
	private double withContext(HabitatClassifier.Column c, double p) {
		boolean gameplay = c.classifier.gameplay;
		double steep = Noise.smoothstep(gameplay ? Calibration.P_SLOPE_FROM_GAMEPLAY : Calibration.P_SLOPE_FROM,
				gameplay ? Calibration.P_SLOPE_TO_GAMEPLAY : Calibration.P_SLOPE_TO, c.slope);
		if (c.t.has(Landform.END_MORAINE) || c.t.has(Landform.INLAND_DUNES)) {
			steep = Math.max(steep, Calibration.P_MORAINE_DUNE);
		}
		if (!c.sandy() && c.w.streamOrder() > 0 && !c.onValleyFloor() && !c.s.hasWater()) {
			double incision = c.t.rawSurface() - c.H
					+ Calibration.P_VALLEY_INCISION_JITTER * context(c, 2, Calibration.FERTILITY_JITTER_WAVELENGTH);
			double side = Noise.smoothstep(Calibration.P_VALLEY_INCISION_FROM, Calibration.P_VALLEY_INCISION_TO, incision);
			if (side > 0) {
				double q = LandscapeModel.noiseQuantile(context(c, 3, Calibration.P_VALLEY_SIDE_BREAK_WAVELENGTH));
				double stretch = Noise.smoothstep(1 - Calibration.P_VALLEY_SIDE_STRETCHES - Calibration.P_VALLEY_SIDE_BREAK_BLEND,
						1 - Calibration.P_VALLEY_SIDE_STRETCHES + Calibration.P_VALLEY_SIDE_BREAK_BLEND, q);
				steep = Math.max(steep, side * stretch * (gameplay ? Calibration.P_VALLEY_SIDE_GAMEPLAY : Calibration.P_VALLEY_SIDE));
			}
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

	/**
	 * Non-forest biome for an unforested site (§2.2, column D). The thresholds of the variant noise (1.5 km·k) jitter as
	 * the fertility thresholds of step H, so their isolines are no long arcs (round 1 of the S8 review). On the fertile
	 * sites the choice between arable land and hay meadow takes the parcel noise of {@link Calibration#D_PARCEL_WAVELENGTH}
	 * m·k (meadows in small parcels, not in ovals of a kilometer), and its slope threshold jitters by
	 * ±{@link Calibration#D_ARABLE_SLOPE_JITTER}° with a noise of {@link Calibration#D_ARABLE_SLOPE_JITTER_WAVELENGTH} m·k.
	 */
	HabitatBiome nonForest(HabitatClassifier.Column c, HabitatBiome biome, ForestSiteType siteType) {
		double q = c.variant(3);
		switch (biome) {
			case WILLOW_POPLAR_FOREST, ASH_ALDER_FOREST, GRAY_ALDER_FOREST:
				return HabitatBiome.WET_MEADOW;
			case ELM_ASH_FOREST:
				return below(c, q, Calibration.D_RIPARIAN_MEADOW, 4) ? HabitatBiome.WET_MEADOW : HabitatBiome.ARABLE_LAND;
			case ALDER_CARR:
				return below(c, q, Calibration.D_ALDER_CARR_MEADOW, 5) ? HabitatBiome.WET_MEADOW : HabitatBiome.FEN;
			case COASTAL_PINE_FOREST:
				return HabitatBiome.GRAY_DUNE;
			case MONTANE_BEECH_FOREST, MONTANE_SPRUCE_FOREST, UPLAND_FIR_FOREST:
				return HabitatBiome.HAY_MEADOW;
			default:
				break;
		}
		return switch (siteType) {
			case DRY_CONIFEROUS -> HabitatBiome.HEATH;
			case FRESH_CONIFEROUS -> below(c, q, Calibration.D_PINE_ARABLE, 6) ? HabitatBiome.ARABLE_LAND : HabitatBiome.HEATH;
			case MOIST_CONIFEROUS, MOIST_MIXED_CONIFEROUS -> below(c, q, Calibration.D_PINE_ARABLE, 6) ? HabitatBiome.WET_MEADOW
					: HabitatBiome.ARABLE_LAND;
			case BOGGY_CONIFEROUS, BOGGY_MIXED_CONIFEROUS -> HabitatBiome.RAISED_BOG;
			case MOIST_BROADLEAVED, MOIST_MIXED_BROADLEAVED, BOGGY_MIXED_BROADLEAVED, ALDER_SWAMP -> below(c, q, Calibration.D_MOIST_MEADOW, 10)
					? HabitatBiome.WET_MEADOW : HabitatBiome.ARABLE_LAND;
			default -> arable(c) ? HabitatBiome.ARABLE_LAND : HabitatBiome.HAY_MEADOW;
		};
	}

	/** Arable land rather than hay meadow on an unforested fertile site: the parcel noise, with fewer fields on slopes. */
	private boolean arable(HabitatClassifier.Column c) {
		double limit = (c.classifier.gameplay ? Calibration.D_ARABLE_SLOPE_GAMEPLAY : Calibration.D_ARABLE_SLOPE)
				+ Calibration.D_ARABLE_SLOPE_JITTER * context(c, 7, Calibration.D_ARABLE_SLOPE_JITTER_WAVELENGTH);
		return below(c, LandscapeModel.noiseQuantile(context(c, 8, Calibration.D_PARCEL_WAVELENGTH)),
				c.slope < limit ? Calibration.D_ARABLE : Calibration.D_ARABLE_STEEP, 9);
	}
}
