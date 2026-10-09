package pl.polishforests.worldgen.habitat;

import pl.polishforests.worldgen.landscape.ColumnSample;
import pl.polishforests.worldgen.landscape.Landform;
import pl.polishforests.worldgen.landscape.Noise;
import pl.polishforests.worldgen.landscape.Substrate;

/**
 * Classifier step 3: waterside zones (§4, ecology report §2). First the rings of standing water
 * (lakes, kettle ponds, oxbow lakes, kettle bogs), then watercourses by class A, B or C. Zones are derived
 * from d, u, W, the order and the gradient (η is not used, because the valley floor is flat). Boundaries jitter
 * by ±20% of the width and ±0.05 u. Minimums in blocks (E11): reedbed 2, willow scrub 3, OlJ 6, alder carr 10.
 * Riparian forests only on the valley floor, in spring areas and in seeps at the foot of the valley side.
 */
final class WatersideZones {
	private WatersideZones() {
	}

	/** Watercourse class (§4): A large lowland river, B small lowland river, C mountain stream. */
	enum StreamClass {
		A, B, C
	}

	/**
	 * F2: the channel of the dominant valley takes over the zones of the floor only when it is wider than the nearest
	 * channel by more than this factor (otherwise the nearest channel is that channel, or one like it).
	 */
	private static final double FLOOR_CHANNEL_WIDER = 1.05;

	/** Class of the nearest watercourse: C in the mountains or at a gradient > 3‰, A at order 3 or W ≥ 30 m (1:1). */
	static StreamClass streamClass(HabitatClassifier.Column c) {
		return streamClass(c, c.wr(), c.w.channelGradient());
	}

	/**
	 * Class of a watercourse with a channel {@code wr} m wide at 1:1 scale and the given gradient in ‰ (F2: the
	 * channel of the dominant valley with its own gradient, {@code floorChannelGradient}; the soft maximum
	 * {@code channelGradient} mixes in a steep tributary near its mouth and would make the river class C there).
	 */
	static StreamClass streamClass(HabitatClassifier.Column c, double wr, double gradient) {
		ColumnSample.Waters w = c.w;
		if (c.wMountains > 0.5 || gradient > Calibration.CLASS_GRADIENT) {
			return StreamClass.C;
		}
		// The order and gradient come from the dominant valley, and W from the channel: a small tributary on the
		// floor of a large valley has order 3, so order 3 gives class A only with a wider channel.
		if (c.wLowland >= Calibration.CLASS_LOWLAND_WEIGHT
				&& (wr >= Calibration.CLASS_A_WR || w.streamOrder() == 3 && wr >= Calibration.CLASS_A_WR_ORDER3)) {
			return StreamClass.A;
		}
		return StreamClass.B;
	}

	static int classify(HabitatClassifier.Column c) {
		ColumnSample.Waters w = c.w;
		int r = HabitatClassifier.Result.NONE;
		boolean narrowOxbow = w.standingWaterKind() == ColumnSample.StandingWaterKind.OXBOW_LAKE
				&& !(w.standingWaterRadius() >= Calibration.LAKE_OXBOW_MIN && w.channelDist() > w.standingWaterRadius());
		if (w.standingWaterKind() != ColumnSample.StandingWaterKind.NONE && Double.isFinite(w.s()) && !narrowOxbow) {
			r = lakes(c);
			if (HabitatClassifier.Result.biome(r) != null) {
				return r;
			}
		}
		if (w.streamOrder() > 0 && Double.isFinite(w.channelDist())) {
			r = HabitatClassifier.Result.further(r, stream(c));
		}
		return r;
	}

	// ------------------------------------------------------------------ watercourses

	/**
	 * Zones of a watercourse (F2). On the floor of a large river (class A) the zones follow the channel of the dominant
	 * valley ({@code floorChannelDist}), and a smaller, closer watercourse keeps only its own belt of ash-alder
	 * riparian forest (as in class B, at least 6 blocks). Before, every zone came from the nearest channel, so at a
	 * confluence the zones of the river were cut by the straight bisector between the two channels (wedges of the poplar
	 * riparian forest). Elsewhere (off that floor, two small watercourses, no wider channel) the nearest channel decides,
	 * and only by real water ({@link HabitatClassifier.Column#byWater}, step H): a dry channel stretch that the valley
	 * does not cut gets no zones. The prototype built a second sample and column for the dominant channel; here its
	 * fields are passed as arguments.
	 */
	private static int stream(HabitatClassifier.Column c) {
		ColumnSample.Waters w = c.w;
		boolean wet = c.byWater();
		if (w.inFloor() && !Double.isNaN(w.floorChannelWidth()) && !Double.isNaN(w.channelWidth())
				&& w.floorChannelWidth() > FLOOR_CHANNEL_WIDER * w.channelWidth() && Double.isFinite(w.floorChannelDist())) {
			double k = c.k;
			// Belt of the smaller watercourse: its ash-alder riparian forest belt from classB.
			double ashAlder = Math.min(Calibration.B_ASH_ALDER_MAX_K * k, Math.max(Calibration.B_ASH_ALDER_MIN_K * k, Calibration.B_ASH_ALDER_W * w.channelWidth()));
			if (c.wOutwashPlain > 0.5) {
				ashAlder = Math.clamp(ashAlder, Calibration.B_ASH_ALDER_OUTWASH_PLAIN_MIN_K * k, Calibration.B_ASH_ALDER_OUTWASH_PLAIN_MAX_K * k);
			}
			if ((!wet || Math.max(0, w.channelDist()) > width(Calibration.MIN_ASH_ALDER, ashAlder, c.jitter()))
					&& c.onValleyFloor(w.floorChannelDist(), w.floorChannelLevel())
					&& streamClass(c, c.wr(w.floorChannelWidth()), w.floorChannelGradient()) == StreamClass.A) {
				return classA(c, Math.max(0, w.floorChannelDist()), w.floorChannelWidth(), true);
			}
		}
		// Step H: no zones along a channel stretch without water (not cut by the valley, far below the ground).
		return wet ? nearestStream(c) : HabitatClassifier.Result.NONE;
	}

	/** Zones of the nearest watercourse. */
	private static int nearestStream(HabitatClassifier.Column c) {
		double d = Math.max(0, c.w.channelDist());
		double channelWidth = c.w.channelWidth();
		if (Double.isNaN(channelWidth)) {
			return HabitatClassifier.Result.NONE;
		}
		return switch (streamClass(c)) {
			case A -> classA(c, d, channelWidth, false);
			case B -> classB(c, d, channelWidth);
			case C -> classC(c, d, channelWidth);
		};
	}

	/** Belt width with jitter {@code f}, not less than the minimum in blocks (E11). */
	private static double width(double minimum, double base, double f) {
		return Math.max(minimum, base * f);
	}

	/** Biome of the belt by the bank, or null when the belt is narrower than a biome (then only a zone). */
	private static HabitatBiome band(double width, HabitatBiome biome) {
		return width >= Calibration.BIOME_BAND ? biome : null;
	}

	/**
	 * Class A (§4.1): point bar, willow scrub, herb fringe, willow and poplar riparian forest, backswamps, elm-ash
	 * floodplain forest.
	 *
	 * @param floorChannel the channel is the channel of the dominant valley (F2), not the nearest one: no convex bank
	 *                     (that field belongs to the nearest channel), the column is on its floor ({@link #stream}
	 *                     checks it), and the height and floor position are measured from it
	 */
	private static int classA(HabitatClassifier.Column c, double d, double channelWidth, boolean floorChannel) {
		ColumnSample.Waters w = c.w;
		double k = c.k;
		double f = c.jitter();
		boolean convex = !floorChannel && w.convexBank();
		double willowBand = Math.max(Calibration.A_WILLOW_SCRUB_K * k, Calibration.A_WILLOW_SCRUB_W * channelWidth);
		if (convex) {
			willowBand = Math.max(willowBand, Math.max(Calibration.A_CONVEX_WILLOW_SCRUB_K * k, Calibration.A_CONVEX_WILLOW_SCRUB_W * channelWidth));
		}
		double willowScrub = width(Calibration.MIN_WILLOW_SCRUB, willowBand, f);
		Zone zone = Zone.NONE;
		if (convex && d < Calibration.A_POINT_BAR_W * channelWidth * f && c.patch(3) > Calibration.A_POINT_BAR_PATCH) {
			HabitatBiome b = band(Calibration.A_POINT_BAR_W * channelWidth, HabitatBiome.WILLOW_SCRUB);
			if (b != null) {
				return HabitatClassifier.Result.of(b, Zone.POINT_BAR, Association.TYPICAL);
			}
			zone = Zone.POINT_BAR;
		} else if (d <= willowScrub) {
			HabitatBiome b = band(willowBand, HabitatBiome.WILLOW_SCRUB);
			if (b != null) {
				return HabitatClassifier.Result.of(b, Zone.WILLOW_SCRUB, Association.TYPICAL);
			}
			zone = Zone.WILLOW_SCRUB;
		} else {
			double fringe = width(Calibration.MIN_HERB_FRINGE, Math.max(Calibration.A_HERB_FRINGE_K * k, Calibration.A_HERB_FRINGE_W * channelWidth), f);
			if (d <= willowScrub + fringe) {
				zone = Zone.HERB_FRINGE;
			}
		}
		if (!floorChannel && !c.onValleyFloor()) {
			return onSlope(c, zone);
		}
		if (zone == Zone.NONE && c.patch(4) < Calibration.A_GAPS) {
			// Gaps in the riparian forest: tall herbs of the herb fringe.
			zone = Zone.HERB_FRINGE;
		}
		double dWhiteWillow = Math.clamp(Calibration.A_D_WHITE_WILLOW_W * channelWidth, Calibration.A_D_WHITE_WILLOW_MIN * k, Calibration.A_D_WHITE_WILLOW_MAX * k) * f;
		double dPoplar = Math.clamp(Calibration.A_D_POPLAR_W * channelWidth, Calibration.A_D_POPLAR_MIN * k, Calibration.A_D_POPLAR_MAX * k) * f;
		boolean softwood = c.H < Calibration.H_WILLOW_RIPARIAN;
		if (d <= dWhiteWillow && softwood) {
			return HabitatClassifier.Result.of(HabitatBiome.WILLOW_POPLAR_FOREST, zone, Association.SALICETUM_ALBAE);
		}
		// Poplar riparian forest by d (deviation S4: no u < 0.35 condition, because u comes from the dominant valley
		// and jumps in a straight line where it changes, and no preference for humps, which interleaved it with the elm
		// floodplain forest in the D_top–1.15 D_top band; natural levees will come with the floor relief in M5).
		if (d <= dPoplar && softwood) {
			return HabitatClassifier.Result.of(HabitatBiome.WILLOW_POPLAR_FOREST, zone, Association.POPULETUM_ALBAE);
		}
		return restOfFloor(c, d, channelWidth, zone, floorChannel);
	}

	/**
	 * Rest of the floor of a large valley: backswamps (alder carr, fen) or elm-ash floodplain forest.
	 *
	 * @param floorChannel distances and heights from the channel of the dominant valley (F2), see {@link #classA}
	 */
	private static int restOfFloor(HabitatClassifier.Column c, double d, double channelWidth, Zone zone, boolean floorChannel) {
		ColumnSample.Waters w = c.w;
		double k = c.k;
		double fromBackswamp = Math.max(Calibration.A_BACKSWAMP_D_K * k, Calibration.A_BACKSWAMP_D_W * channelWidth);
		if ((floorChannel ? c.uJittered(w.floorChannelDist(), true) : c.uJittered()) > Calibration.A_BACKSWAMP_U
				&& (floorChannel ? c.heightAboveChannel(w.floorChannelLevel()) : c.heightAboveChannel()) < Calibration.A_BACKSWAMP_H
				&& d > fromBackswamp
				&& (1 - Calibration.A_BACKSWAMP_U) * w.floorHalfWidth() >= 2 * Calibration.MIN_ALDER_CARR
				&& c.patchQ(10, Calibration.BACKSWAMP_WAVELENGTH / k) < Calibration.BACKSWAMP_SHARE) {
			boolean peat = w.floorHalfWidth() > Calibration.A_PEAT_HALF_WIDTH_K * k && w.channelGradient() < Calibration.A_PEAT_GRADIENT
					&& c.patchQ(5, Calibration.BACKSWAMP_WAVELENGTH / k) < Calibration.A_PEAT_SHARE;
			Zone s = zone == Zone.HERB_FRINGE ? Zone.NONE : zone;
			return HabitatClassifier.Result.of(peat ? HabitatBiome.FEN : HabitatBiome.ALDER_CARR, s, Association.BACKSWAMP);
		}
		return HabitatClassifier.Result.of(HabitatBiome.ELM_ASH_FOREST, zone, Association.TYPICAL);
	}

	/** Class B (§4.2): bank tall herbs, willows, ash-alder riparian forest, alder carr on wide floors, spring areas. */
	private static int classB(HabitatClassifier.Column c, double d, double channelWidth) {
		ColumnSample.Waters w = c.w;
		double k = c.k;
		double f = c.jitter();
		double herbs = width(Calibration.MIN_TALL_HERBS, Math.max(Calibration.B_HERBS_K * k, Calibration.B_HERBS_W * channelWidth), f);
		Zone zone = Zone.NONE;
		if (d <= herbs) {
			zone = Zone.TALL_HERBS;
		} else if (c.wr() >= Calibration.B_WILLOWS_WR && c.sandy() && d <= Calibration.B_WILLOWS_K * k * f) {
			zone = Zone.RIVERSIDE_WILLOWS;
		} else if (c.classifier.mode == HabitatClassifier.Mode.PRESENT_DAY && d >= Calibration.B_TREE_ROW_FROM_K * k
				&& d <= Calibration.B_TREE_ROW_TO_K * k * f) {
			zone = Zone.TREE_ROW;
		}
		if (c.isSpringArea() && c.H < Calibration.H_ASH_ALDER_RIPARIAN) {
			// The spring area keeps its zone in both modes (S8: the tree row does not replace it).
			return HabitatClassifier.Result.of(HabitatBiome.ASH_ALDER_FOREST, zone == Zone.NONE || zone == Zone.TREE_ROW
					? Zone.SPRING_AREA : zone, Association.SPRING_FED);
		}
		if (!c.onValleyFloor()) {
			// Narrow floor (or none): riparian forest by the bank in a belt of at least 6 blocks (E11), low above the watercourse.
			if (d <= Calibration.MIN_ASH_ALDER && c.H < Calibration.H_ASH_ALDER_RIPARIAN && !Double.isNaN(c.w.softChannelLevel())
					&& c.H - c.w.softChannelLevel() <= Calibration.SEEP_HL) {
				return HabitatClassifier.Result.of(HabitatBiome.ASH_ALDER_FOREST, zone, Association.TYPICAL);
			}
			return onSlope(c, zone);
		}
		double ashAlder = Math.min(Calibration.B_ASH_ALDER_MAX_K * k, Math.max(Calibration.B_ASH_ALDER_MIN_K * k, Calibration.B_ASH_ALDER_W * channelWidth));
		if (c.wOutwashPlain > 0.5) {
			ashAlder = Math.clamp(ashAlder, Calibration.B_ASH_ALDER_OUTWASH_PLAIN_MIN_K * k, Calibration.B_ASH_ALDER_OUTWASH_PLAIN_MAX_K * k);
		}
		ashAlder = width(Calibration.MIN_ASH_ALDER, ashAlder, f);
		if (d <= ashAlder && c.H < Calibration.H_ASH_ALDER_RIPARIAN) {
			return HabitatClassifier.Result.of(HabitatBiome.ASH_ALDER_FOREST, zone, Association.TYPICAL);
		}
		double fromAlderCarr = Math.max(Calibration.B_ALDER_CARR_D_K * k, Calibration.B_ALDER_CARR_D_W * channelWidth) * f;
		// Alder carr only on a floor with room for patches wider than 10 blocks beyond the riparian forest (E11): a belt of at least 2 × 10.
		if (w.floorHalfWidth() > Calibration.B_ALDER_CARR_HALF_WIDTH_K * k && w.floorHalfWidth() - fromAlderCarr >= 2 * Calibration.MIN_ALDER_CARR && d > fromAlderCarr
				&& c.H < Calibration.H_ALDER_CARR
				&& (c.substrate == Substrate.PEAT || (c.heightAboveChannel() < Calibration.B_ALDER_CARR_H
						|| c.uJittered() > Calibration.B_ALDER_CARR_U) && c.patchQ(6, Calibration.ALDER_CARR_PATCH_WAVELENGTH / k) < Calibration.B_ALDER_CARR_SHARE)) {
			Zone s = zone == Zone.NONE && d < fromAlderCarr + Calibration.B_WILLOW_CARR_K * k ? Zone.WILLOW_CARR : zone;
			return HabitatClassifier.Result.of(HabitatBiome.ALDER_CARR, s, Association.TYPICAL);
		}
		// Small watercourse on the wide floor of a large valley: beyond it, the rest of that floor.
		if (w.floorHalfWidth() > Calibration.WIDE_FLOOR_K * k && c.wLowland >= Calibration.CLASS_LOWLAND_WEIGHT
				&& w.streamOrder() >= Calibration.WIDE_FLOOR_ORDER) {
			return restOfFloor(c, d, channelWidth, zone, false);
		}
		// Floor margin: zonal site (till: low oak-hornbeam forest, LMw; sand: moist pine forest, then fresh).
		return HabitatClassifier.Result.zone(zone);
	}

	/** Class C (§4.3): gravel bar, montane willow scrub, gray alder forest, streamside tall herbs, spring fens. */
	private static int classC(HabitatClassifier.Column c, double d, double channelWidth) {
		ColumnSample.Waters w = c.w;
		double k = c.k;
		double f = c.jitter();
		double h = c.H;
		double wr = c.wr();
		Zone zone = Zone.NONE;
		if (w.convexBank() && wr >= Calibration.C_GRAVEL_BAR_WR && d <= channelWidth * f && h >= Calibration.C_GRAVEL_BAR_H_FROM
				&& h <= Calibration.C_GRAVEL_BAR_H_TO) {
			HabitatBiome b = band(channelWidth, HabitatBiome.WILLOW_SCRUB);
			if (b != null) {
				return HabitatClassifier.Result.of(b, Zone.GRAVEL_BAR, Association.TYPICAL);
			}
			zone = Zone.GRAVEL_BAR;
		} else if (wr >= Calibration.C_WILLOW_SCRUB_WR && d <= width(Calibration.MIN_WILLOW_SCRUB, channelWidth, f)
				&& h <= Calibration.C_WILLOW_SCRUB_H) {
			HabitatBiome b = band(Math.max(Calibration.MIN_WILLOW_SCRUB, channelWidth), HabitatBiome.WILLOW_SCRUB);
			if (b != null) {
				return HabitatClassifier.Result.of(b, Zone.WILLOW_SCRUB, Association.TYPICAL);
			}
			zone = Zone.WILLOW_SCRUB;
		}
		boolean alder = SpeciesRanges.grayAlder(c.O, c.P);
		boolean headwaters = c.isSpringArea();
		if ((headwaters || c.valleySlope() && c.dgw() <= Calibration.C_SPRING_FEN_DGW) && h >= Calibration.C_SPRING_FEN_H_FROM
				&& h <= Calibration.C_SPRING_FEN_H_TO && alder) {
			return HabitatClassifier.Result.of(HabitatBiome.GRAY_ALDER_FOREST, zone == Zone.NONE && headwaters ? Zone.SPRING_AREA : zone,
					Association.SPRING_FEN);
		}
		if (headwaters && h < Calibration.H_ASH_ALDER_RIPARIAN) {
			return HabitatClassifier.Result.of(HabitatBiome.ASH_ALDER_FOREST, zone == Zone.NONE ? Zone.SPRING_AREA : zone,
					Association.SPRING_FED);
		}
		if (!c.onValleyFloor()) {
			return onSlope(c, zone);
		}
		// Step H (Z9): the floor half-width changes slowly along the stream, so its thresholds crossed the floor in straight
		// lines; it jitters by ±20% with a noise of 150 m·k, and the riparian belt tapers instead of ending.
		double halfWidth = w.floorHalfWidth() * (1 + Calibration.WIDTH_JITTER * c.jitterNoise(16, Calibration.FERTILITY_JITTER_WAVELENGTH));
		double narrow = Calibration.C_NARROW_FLOOR_K * k;
		double wholeFloor = Calibration.C_WHOLE_FLOOR_K * k;
		if (h > Calibration.C_GRAY_ALDER_H || halfWidth < 0.75 * narrow) {
			// High up and in narrow V-shaped valleys: zonal forest down to the bank, tall herbs by the water.
			if (zone == Zone.NONE && d <= width(Calibration.C_HERBS_MIN, Calibration.C_HERBS_W * channelWidth, f)) {
				zone = Zone.MONTANE_TALL_HERBS;
			}
			return HabitatClassifier.Result.zone(zone);
		}
		double grayAlderBand = Math.min(Calibration.C_GRAY_ALDER_MAX_K * k, Math.max(Calibration.C_GRAY_ALDER_MIN_K * k,
				Calibration.C_GRAY_ALDER_W * channelWidth)) * f;
		// The whole floor on narrow floors (up to C_WHOLE_FLOOR_K·k, fading out up to 1.25 times that), the belt by the channel
		// on wider ones; around the narrow floor limit (0.75–1 of it) the belt grows from 0, so a riparian forest tapers out
		// along the stream instead of ending in a straight line across the floor.
		double whole = 1 - Noise.smoothstep(wholeFloor, 1.25 * wholeFloor, halfWidth);
		// The whole floor reaches as far as the floor margin (onValleyFloor) or a mouth funnel of the terrain floor.
		double floorReach = Math.max(Calibration.FLOOR_MIN_K * k, 2 * halfWidth);
		double band = (grayAlderBand + Math.max(0, floorReach - grayAlderBand) * whole) * Noise.smoothstep(0.75 * narrow, narrow, halfWidth);
		double hMax = c.aspectN() ? Calibration.C_GRAY_ALDER_H_N : Calibration.C_GRAY_ALDER_H;
		if (d <= band && h <= hMax) {
			if (w.streamOrder() == 1 && h < Calibration.C_CARICI_H && c.P >= Calibration.C_CARICI_P
					&& halfWidth < wholeFloor && c.dgw() <= Calibration.SEEP_DGW) {
				return HabitatClassifier.Result.of(HabitatBiome.ASH_ALDER_FOREST, zone, Association.CARICI_REMOTAE_FRAXINETUM);
			}
			return HabitatClassifier.Result.of(alder ? HabitatBiome.GRAY_ALDER_FOREST : HabitatBiome.ASH_ALDER_FOREST, zone, Association.TYPICAL);
		}
		return HabitatClassifier.Result.zone(zone);
	}

	/** Off the floor: seeps at the foot of the valley side (DGW ≤ 0.5) are ash-alder riparian forest, otherwise zonal. */
	private static int onSlope(HabitatClassifier.Column c, Zone zone) {
		if (c.isSeep()) {
			return HabitatClassifier.Result.of(HabitatBiome.ASH_ALDER_FOREST, zone, Association.TYPICAL);
		}
		return HabitatClassifier.Result.zone(zone);
	}

	// ------------------------------------------------------------------ standing water (§4.4)

	private static int lakes(HabitatClassifier.Column c) {
		ColumnSample.Waters w = c.w;
		double k = c.k;
		double f = c.jitter();
		double s = w.s();
		double h = c.H - w.shoreLevel() - 1;
		Substrate sub = c.substrate;
		ColumnSample.StandingWaterKind kind = w.standingWaterKind();
		if (kind == ColumnSample.StandingWaterKind.KETTLE_BOG) {
			double radius = w.standingWaterRadius();
			boolean large = radius > Calibration.KETTLE_BOG_RADIUS_K * k;
			if (sub == Substrate.PEAT || s < 0) {
				if (w.ombrotrophicPeat()) {
					return HabitatClassifier.Result.of(large ? HabitatBiome.RAISED_BOG : HabitatBiome.BOG_WOODLAND, Zone.NONE,
							Association.TYPICAL);
				}
				// Minerotrophic peat: a large kettle on till is a fen, others are alder carr.
				boolean till = c.wMorainePlateau + c.wOldGlacialPlain > 0.5;
				return HabitatClassifier.Result.of(large && till ? HabitatBiome.FEN : HabitatBiome.ALDER_CARR, Zone.NONE, Association.TYPICAL);
			}
			if (w.ombrotrophicPeat() && s <= Calibration.KETTLE_RING) {
				return HabitatClassifier.Result.of(HabitatBiome.BOG_WOODLAND, Zone.NONE, Association.TYPICAL);
			}
			if (!w.ombrotrophicPeat() && s <= Calibration.LAKE_ALDER_CARR_KETTLE_K * k * f && h <= Calibration.LAKE_ALDER_CARR_H) {
				return HabitatClassifier.Result.of(HabitatBiome.ALDER_CARR, Zone.NONE, Association.TYPICAL);
			}
			return HabitatClassifier.Result.NONE;
		}
		s = Math.max(0, s);
		if (kind == ColumnSample.StandingWaterKind.OXBOW_LAKE) {
			double reedbed = Math.max(Calibration.MIN_REEDBED, Calibration.LAKE_OXBOW_REEDBED_K * k);
			if (s <= width(Calibration.MIN_REEDBED, Calibration.LAKE_OXBOW_REEDBED_K * k, f)) {
				HabitatBiome b = band(reedbed, HabitatBiome.REEDBED);
				return b != null ? HabitatClassifier.Result.of(b, Zone.SHORE_REEDBED, Association.OXBOW_LAKE)
						: HabitatClassifier.Result.zone(Zone.SHORE_REEDBED);
			}
			if (s <= Calibration.LAKE_OXBOW_WILLOW_CARR_K * k * f) {
				return HabitatClassifier.Result.of(HabitatBiome.ALDER_CARR, Zone.WILLOW_CARR, Association.OXBOW_LAKE);
			}
			if (c.onValleyFloor() && c.uJittered() > Calibration.LAKE_OXBOW_ALDER_CARR_U) {
				return HabitatClassifier.Result.of(HabitatBiome.ALDER_CARR, Zone.NONE, Association.OXBOW_LAKE);
			}
			return HabitatClassifier.Result.NONE;
		}
		OpenWater.TrophicState trophic = OpenWater.trophicState(c);
		if (trophic == OpenWater.TrophicState.DYSTROPHIC) {
			if (s <= Calibration.LAKE_DYSTROPHIC_PEAT_K * k * f) {
				return HabitatClassifier.Result.of(HabitatBiome.RAISED_BOG, Zone.NONE, Association.TYPICAL);
			}
			if (s <= Calibration.LAKE_DYSTROPHIC_BOG_WOODLAND) {
				return HabitatClassifier.Result.of(HabitatBiome.BOG_WOODLAND, Zone.NONE, Association.TYPICAL);
			}
			return HabitatClassifier.Result.NONE;
		}
		if (trophic == OpenWater.TrophicState.OLIGOTROPHIC) {
			// Lobelia lake: the pine forest reaches the water with a narrow belt of alder or birch.
			if (s <= Math.max(1, Calibration.LAKE_SHORE_ALDER_K * k) * f) {
				return HabitatClassifier.Result.zone(Zone.SHORE_ALDERS);
			}
			return HabitatClassifier.Result.NONE;
		}
		boolean suitableSubstrate = sub == Substrate.GLACIAL_TILL || sub == Substrate.PEAT || sub == Substrate.LAKE_MUD;
		Zone zone = Zone.NONE;
		if (s <= width(Calibration.MIN_REEDBED, Calibration.LAKE_SHORE_REEDBED_K * k, f) && h <= Calibration.LAKE_SHORE_REEDBED_H) {
			zone = Zone.SHORE_REEDBED;
		}
		if (s <= Calibration.LAKE_WILLOW_CARR_K * k * f && h <= Calibration.LAKE_WILLOW_CARR_H && suitableSubstrate) {
			return HabitatClassifier.Result.of(HabitatBiome.ALDER_CARR, zone == Zone.NONE ? Zone.WILLOW_CARR : zone, Association.TYPICAL);
		}
		double alderCarrMax = (kind == ColumnSample.StandingWaterKind.KETTLE_POND ? Calibration.LAKE_ALDER_CARR_KETTLE_K : Calibration.LAKE_ALDER_CARR_K) * k;
		if (s <= width(Calibration.MIN_ALDER_CARR, alderCarrMax, f) && h <= Calibration.LAKE_ALDER_CARR_H && c.slope < Calibration.LAKE_ALDER_CARR_SLOPE
				&& suitableSubstrate) {
			return HabitatClassifier.Result.of(HabitatBiome.ALDER_CARR, zone, Association.TYPICAL);
		}
		return HabitatClassifier.Result.zone(zone);
	}
}
