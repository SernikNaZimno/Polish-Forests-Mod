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
 *
 * <p><b>Round 2 of the S8 review.</b> The DGW changes by about 0.2 m per block on a valley side, so a blend continuous in
 * DGW was still a ramp of 5–10 m, and the edges lay on DGW contours parallel to the valleys; the floor margin, the
 * belts and the fertility boundaries still stepped. Now P_forest of dry land is one function of continuous coordinates
 * for every zonal and mountain biome ({@link #dryLand}): the richness r blended across the fertility thresholds, the
 * DGW blends widened by the rise of the ground over tens of meters and jittered along the valley, the mountains by
 * altitude only, a floor margin ramp, and the inland dunes by their height. The remaining steps are the gray alder strip
 * (P_forest 1 by design, §4.5), the floor margin on poor sites (pine forests reaching the floor) and the classifier's
 * own DGW jumps (docs/03-m2-biomy.md §3.4.1, "Runda 2 poprawek S8").
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

	/**
	 * P_forest of the column (§4.5, ecology report §6.3, calibrated in S8 with {@code BiomeSharesTest}).
	 *
	 * <p>Round 2 of the S8 review: on dry land P_forest no longer depends on the biome but on continuous coordinates
	 * ({@link #dryLand}): the richness r (blend across the fertility thresholds), the jittered DGW (blends across the
	 * moisture thresholds, widened by the valley side gradient), the mountain weight and the altitude (no step at the
	 * belt boundaries), the slope, the valley side and the height above the valley floor margin. Each transition is wide
	 * and jittered in space (tens of meters), so the forest edges follow F rather than contours parallel to the valleys.
	 * {@code siteType} is kept for the callers and the probes; P_forest no longer reads it.
	 */
	double forestProbability(HabitatClassifier.Column c, HabitatBiome biome, ForestSiteType siteType) {
		return switch (biome) {
			case DWARF_PINE_SCRUB -> 1;
			case COASTAL_PINE_FOREST -> floorMargin(c, Calibration.P_COASTAL_PINE, 0);
			// Off the valley floor (seeps, springs, stream belts on the sides) a riparian forest takes the P_forest of dry
			// land there, which at a low DGW is about that of the carr, so its boundary on a side is no step (round 2).
			case WILLOW_POPLAR_FOREST -> c.onValleyFloor() ? Calibration.P_WILLOW_POPLAR : dryLand(c);
			case ELM_ASH_FOREST -> c.onValleyFloor() ? Calibration.P_ELM_ASH : dryLand(c);
			case ASH_ALDER_FOREST -> c.onValleyFloor() ? Calibration.P_ASH_ALDER : dryLand(c);
			case ALDER_CARR -> alderCarr(c);
			case GRAY_ALDER_FOREST -> grayAlder(c);
			case MONTANE_BEECH_FOREST, UPLAND_FIR_FOREST, MONTANE_SPRUCE_FOREST -> c.onValleyFloor() ? Calibration.P_MOUNTAIN_FLOOR : dryLand(c);
			default -> dryLand(c);
		};
	}

	/**
	 * Horizontal scale of the transitions (round 2 of the S8 review): the rise (m) of the ground over {@code run} m
	 * (blocks, the same in both scales: in GAMEPLAY the sides are steeper per block, and widths in m·k left ramps of a few
	 * blocks). The coarse slope of the terrain grid ignores the valleys, so on a valley side the gradient is estimated
	 * from the height above the floor margin and the distance beyond the floor edge.
	 */
	private double rise(HabitatClassifier.Column c, double run) {
		double g = Math.tan(Math.toRadians(Math.min(c.t.slope(), 60)));
		if (c.w.streamOrder() > 0 && !Double.isNaN(c.w.softChannelLevel()) && Double.isFinite(c.w.channelDist()) && !c.onValleyFloor()) {
			double above = c.H - c.w.softChannelLevel() - Calibration.FLOOR_H;
			double half = Double.isNaN(c.w.floorHalfWidth()) ? 0 : c.w.floorHalfWidth();
			double beyond = Math.max(c.w.channelDist() - half, Calibration.P_SIDE_MIN_RUN_K * k);
			if (above > 0) {
				g = Math.max(g, above / beyond);
			}
		}
		return Math.min(g, Calibration.P_SIDE_MAX_GRADIENT) * run;
	}

	/** Spatial jitter of a transition: ±({@code base} + the rise over {@link Calibration#P_EDGE_SHIFT_K} m), noise layer. */
	private double shift(HabitatClassifier.Column c, double base, int layer) {
		return (base + rise(c, Calibration.P_EDGE_SHIFT_K)) * context(c, layer, Calibration.P_EDGE_SHIFT_WAVELENGTH);
	}

	/**
	 * P_forest of dry land: the zonal P_forest blended across the fertility and moisture thresholds, with the sands, the
	 * foothills and the uplands (P ≥ {@link Calibration#P_FIR_FOREST}), the mountains by altitude, the slope and valley side
	 * context and the floor margin ({@link #floorMargin}).
	 */
	private double dryLand(HabitatClassifier.Column c) {
		Fertility own = c.fertility();
		Fertility[] other = new Fertility[1];
		double wOther = Fertility.blend(c, Calibration.P_FERTILITY_BAND, other);
		double d = jitteredDgw(c);
		double p = zonal(c, own, d);
		double fertile = own == Fertility.MESOTROPHIC || own == Fertility.EUTROPHIC ? 1 : 0;
		if (wOther > 0) {
			p = Noise.lerp(wOther, p, zonal(c, other[0], d));
			fertile = Noise.lerp(wOther, fertile, other[0] == Fertility.MESOTROPHIC || other[0] == Fertility.EUTROPHIC ? 1 : 0);
		}
		double wm = Noise.smoothstep(Calibration.P_MOUNTAIN_W_FROM, Calibration.P_MOUNTAIN_W_TO, c.wMountains);
		if (wm > 0) {
			p = Noise.lerp(wm, p, mountain(c));
		}
		return floorMargin(c, withContext(c, p, fertile), fertile);
	}

	/**
	 * Zonal P_forest of the fertility {@code t} at the jittered DGW {@code d}: the fresh and the moist site type blend over
	 * DGW_FRESH ± the band (P_DGW_BAND plus the rise over {@link Calibration#P_EDGE_WIDTH_K} m); on fertile sites the
	 * moist side blends down to the alder carr ({@link #moistSide}). Sands get the factor outside the outwash plain, the
	 * foothills the farmland P_forest of their gentle slopes.
	 */
	private double zonal(HabitatClassifier.Column c, Fertility t, double d) {
		double fresh = table(ForestSiteType.zonal(t, c.moisture() == Moisture.DRY ? Moisture.DRY : Moisture.FRESH));
		double moist = table(ForestSiteType.zonal(t, Moisture.MOIST));
		double band = Calibration.P_DGW_BAND + rise(c, Calibration.P_EDGE_WIDTH_K);
		double w = Noise.smoothstep(Calibration.DGW_FRESH - band, Calibration.DGW_FRESH + band, d);
		boolean poor = t == Fertility.OLIGOTROPHIC || t == Fertility.OLIGO_MESOTROPHIC;
		double p = Noise.lerp(w, poor ? moist : moistSide(c, d, moist), fresh);
		if (poor) {
			// Sands: large forest complexes on the outwash plain, partly farmed sand patches elsewhere.
			p *= Noise.lerp(Math.clamp(c.wOutwashPlain, 0.0, 1.0), Calibration.P_SAND_OUTSIDE_OUTWASH, 1.0);
		}
		// Foothills: farmland on the gentle slopes and floors, forest on the steep slopes (context).
		return Noise.lerp(Math.clamp(c.wFoothills, 0.0, 1.0), p, Math.min(p, Calibration.P_FOOTHILLS));
	}

	/**
	 * P_forest on the moist side of a fertile zonal site (LM, L) at the jittered DGW {@code d}: from the alder carr at
	 * {@link Calibration#P_CARR_DGW_FROM} to the moist site {@code moist} at {@link Calibration#P_CARR_DGW_TO}, widened by
	 * the rise over {@link Calibration#P_EDGE_WIDTH_K} m. The alder carr of the same fertility uses the same function
	 * ({@link #alderCarr}), so P_forest is continuous across the boggy/moist boundary.
	 */
	private double moistSide(HabitatClassifier.Column c, double d, double moist) {
		double wide = rise(c, Calibration.P_EDGE_WIDTH_K);
		return Noise.lerp(Noise.smoothstep(Calibration.P_CARR_DGW_FROM - wide, Calibration.P_CARR_DGW_TO + wide, d), Calibration.P_ALDER_CARR, moist);
	}

	/** P_forest of a zonal site type in the lowland (BiomeSharesTest, step S8). */
	private static double table(ForestSiteType siteType) {
		return switch (siteType) {
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
	}

	/**
	 * P_forest in the mountains (round 2 of the S8 review): one function of the altitude for every forest of the mountain
	 * belt and the zonal forests between them, so the belt boundaries (montane beech, upland fir, montane spruce, the
	 * foothill oak-hornbeam forest) are no forest edges: from {@link Calibration#P_FOOTHILLS} below
	 * {@link Calibration#P_MOUNTAIN_H_FROM} m to {@link Calibration#P_MONTANE_BEECH} at {@link Calibration#P_MOUNTAIN_H_TO} m
	 * and {@link Calibration#P_MONTANE_SPRUCE} above the upper montane belt, the altitude jittered by
	 * ±{@link Calibration#P_MOUNTAIN_H_JITTER} m (context noise layer 13).
	 */
	private double mountain(HabitatClassifier.Column c) {
		double h = c.H + Calibration.P_MOUNTAIN_H_JITTER * context(c, 13, Calibration.P_EDGE_SHIFT_WAVELENGTH);
		double p = Noise.lerp(Noise.smoothstep(Calibration.P_MOUNTAIN_H_FROM, Calibration.P_MOUNTAIN_H_TO, h), Calibration.P_FOOTHILLS,
				Calibration.P_MONTANE_BEECH);
		return Noise.lerp(Noise.smoothstep(AltitudinalBelts.UPPER_MONTANE - 100, AltitudinalBelts.UPPER_MONTANE + 100, h), p,
				Calibration.P_MONTANE_SPRUCE);
	}

	/**
	 * DGW of the column ({@link Moisture#dgwForBlend}, without the steps of the perched water caps) jittered for the
	 * blends of P_forest (context noise layer 1): ±{@link Calibration#P_DGW_JITTER} m
	 * plus, since round 2 of the S8 review, the rise over {@link Calibration#P_EDGE_SHIFT_K} m, with the noise of
	 * {@link Calibration#P_EDGE_SHIFT_WAVELENGTH} m·k, so on a valley side the moisture transitions move by tens of m·k
	 * along the valley instead of lying on one DGW contour.
	 */
	private double jitteredDgw(HabitatClassifier.Column c) {
		return Moisture.dgwForBlend(c) + shift(c, Calibration.P_DGW_JITTER, 1);
	}

	/**
	 * Alder carr: {@link Calibration#P_ALDER_CARR} on peat; on mineral ground the P_forest of dry land
	 * ({@link #dryLand}, whose moist side at a low DGW is the alder carr, {@link #moistSide}), so a carr at the foot of a
	 * valley side is no forest edge of its own (rounds 1 and 2 of the S8 review).
	 */
	private double alderCarr(HabitatClassifier.Column c) {
		if (c.substrate == Substrate.PEAT) {
			return Calibration.P_ALDER_CARR;
		}
		return dryLand(c);
	}

	/**
	 * Context of a dry-land site: steep slopes, valley sides, end moraines and inland dunes stay forested more often
	 * (forests "on the poorest soils and where nothing else pays", docs/research/06 §1.3): P_forest moves towards
	 * {@link Calibration#P_STEEP} by the steepness 0–1. The valley side (round 1 of the S8 review) grows with the incision
	 * below the pre-valley terrain from {@link Calibration#P_VALLEY_INCISION_FROM} to {@link Calibration#P_VALLEY_INCISION_TO}
	 * m (since round 2 widened by the rise over {@link Calibration#P_EDGE_WIDTH_K} m and jittered by the rise over
	 * {@link Calibration#P_EDGE_SHIFT_K} m), on fertile sites only ({@code fertile}, blended across the fertility
	 * threshold), and its strength changes along the valley with the context noise of
	 * {@link Calibration#P_VALLEY_SIDE_BREAK_WAVELENGTH} m·k (a smoothstep of its quantile around
	 * 1 − {@link Calibration#P_VALLEY_SIDE_STRETCHES} ± {@link Calibration#P_VALLEY_SIDE_BREAK_BLEND}), so forested valley
	 * sides are not continuous belts of constant width on both sides of every valley.
	 */
	private double withContext(HabitatClassifier.Column c, double p, double fertile) {
		boolean gameplay = c.classifier.gameplay;
		double steep = Noise.smoothstep(gameplay ? Calibration.P_SLOPE_FROM_GAMEPLAY : Calibration.P_SLOPE_FROM,
				gameplay ? Calibration.P_SLOPE_TO_GAMEPLAY : Calibration.P_SLOPE_TO, c.slope);
		if (c.t.has(Landform.END_MORAINE)) {
			steep = Math.max(steep, Calibration.P_MORAINE_DUNE);
		}
		// Inland dunes by the dune height (round 2 of the S8 review: the INLAND_DUNES flag stepped at 4 m).
		double dunes = Noise.smoothstep(Calibration.P_DUNE_FROM, Calibration.P_DUNE_TO, c.t.duneHeight())
				* Noise.smoothstep(0.35, 0.65, c.wOutwashPlain);
		steep = Math.max(steep, dunes * Calibration.P_MORAINE_DUNE);
		if (fertile > 0 && c.w.streamOrder() > 0 && !c.onValleyFloor() && !c.s.hasWater()) {
			double wide = rise(c, Calibration.P_EDGE_WIDTH_K);
			double incision = c.t.rawSurface() - c.H + shift(c, Calibration.P_VALLEY_INCISION_JITTER, 2);
			double side = Noise.smoothstep(Calibration.P_VALLEY_INCISION_FROM - wide, Calibration.P_VALLEY_INCISION_TO + wide, incision);
			if (side > 0) {
				double q = LandscapeModel.noiseQuantile(context(c, 3, Calibration.P_VALLEY_SIDE_BREAK_WAVELENGTH));
				double stretch = Noise.smoothstep(1 - Calibration.P_VALLEY_SIDE_STRETCHES - Calibration.P_VALLEY_SIDE_BREAK_BLEND,
						1 - Calibration.P_VALLEY_SIDE_STRETCHES + Calibration.P_VALLEY_SIDE_BREAK_BLEND, q);
				steep = Math.max(steep, fertile * side * stretch * (gameplay ? Calibration.P_VALLEY_SIDE_GAMEPLAY : Calibration.P_VALLEY_SIDE));
			}
		}
		return p < Calibration.P_STEEP ? p + (Calibration.P_STEEP - p) * steep : p;
	}

	/**
	 * Floor margin (round 2 of the S8 review): above the floor of a valley with a watercourse, dry land P_forest rises from
	 * {@link Calibration#P_FLOOR_MARGIN} on fertile sites (about the P_forest of the riparian forests and carrs of the
	 * floor: meadows and pastures on the lower slopes) and {@link Calibration#P_FLOOR_MARGIN_POOR} on poor ones (the
	 * pine forests of the sands reach down to the floor; {@code fertile} blended across the fertility threshold) to its own
	 * value over {@link Calibration#P_FLOOR_MARGIN_H} m plus the rise over {@link Calibration#P_FLOOR_MARGIN_WIDTH_K} m
	 * above FLOOR_H, moved up the side by up to {@link Calibration#P_FLOOR_MARGIN_JITTER} m plus the rise over
	 * {@link Calibration#P_FLOOR_MARGIN_SHIFT_K} m (context noise layer 14), so the edge of
	 * the floor meadows is no step of P_forest along the floor margin. Only in a valley: the effect grows with the incision
	 * below the pre-valley terrain from {@link Calibration#P_FLOOR_MARGIN_INCISION_FROM} to
	 * {@link Calibration#P_FLOOR_MARGIN_INCISION_TO} m, so flat land low above a channel and the edge of the range of the
	 * river network query (where the stream order drops to 0) get no margin.
	 */
	private double floorMargin(HabitatClassifier.Column c, double p, double fertile) {
		double target = Noise.lerp(fertile, Calibration.P_FLOOR_MARGIN_POOR, Calibration.P_FLOOR_MARGIN);
		if (c.w.streamOrder() <= 0 || Double.isNaN(c.w.softChannelLevel()) || p <= target) {
			return p;
		}
		double valley = Noise.smoothstep(Calibration.P_FLOOR_MARGIN_INCISION_FROM, Calibration.P_FLOOR_MARGIN_INCISION_TO,
				c.t.rawSurface() - c.H);
		if (valley <= 0) {
			return p;
		}
		// The shift only moves the margin up the side (0 to its amplitude), so the ramp always starts at the floor.
		double up = (Calibration.P_FLOOR_MARGIN_JITTER + rise(c, Calibration.P_FLOOR_MARGIN_SHIFT_K))
				* 0.5 * (1 + context(c, 14, Calibration.P_EDGE_SHIFT_WAVELENGTH));
		double above = c.H - c.w.softChannelLevel() - Calibration.FLOOR_H - up;
		double w = Noise.smoothstep(0, Calibration.P_FLOOR_MARGIN_H + rise(c, Calibration.P_FLOOR_MARGIN_WIDTH_K), above);
		return Noise.lerp(valley * (1 - w), p, target);
	}

	/**
	 * Gray alder forest (§4.5): the strip by the stream (5–20 m·k, 2 W, with the zone jitter) always stays; beyond it
	 * P_forest falls to {@link Calibration#P_GRAY_ALDER} over {@link Calibration#D_GRAY_ALDER_RAMP} strip widths (round 2 of
	 * the S8 review: the strip edge varies with F instead of being a line parallel to the stream), and towards meadows on
	 * floors wider than 80 m·k below 900 m (smoothly with the floor width and the altitude).
	 */
	private static double grayAlder(HabitatClassifier.Column c) {
		double w = Double.isNaN(c.w.channelWidth()) ? 0 : c.w.channelWidth();
		double strip = Math.clamp(Calibration.D_GRAY_ALDER_STRIP_W * w, Calibration.D_GRAY_ALDER_STRIP_MIN_K * c.k,
				Calibration.D_GRAY_ALDER_STRIP_MAX_K * c.k) * c.jitter();
		double dist = Math.max(0, c.w.channelDist());
		if (dist <= strip) {
			return 1;
		}
		double half = Double.isNaN(c.w.floorHalfWidth()) ? 0 : c.w.floorHalfWidth();
		double meadow = Noise.smoothstep(0.35, 0.65, half / (Calibration.D_STREAM_MEADOW_FLOOR_K * c.k))
				* (1 - Noise.smoothstep(Calibration.D_STREAM_MEADOW_H - 50, Calibration.D_STREAM_MEADOW_H + 50, c.H));
		double beyond = Noise.lerp(meadow, Calibration.P_GRAY_ALDER, 0);
		return Noise.lerp(Noise.smoothstep(strip, strip * (1 + Calibration.D_GRAY_ALDER_RAMP), dist), 1, beyond);
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
