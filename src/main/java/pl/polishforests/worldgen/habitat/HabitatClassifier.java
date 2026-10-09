package pl.polishforests.worldgen.habitat;

import pl.polishforests.worldgen.landscape.ColumnSample;
import pl.polishforests.worldgen.landscape.Landform;
import pl.polishforests.worldgen.landscape.LandscapeModel;
import pl.polishforests.worldgen.landscape.LandscapeScale;
import pl.polishforests.worldgen.landscape.LandscapeType;
import pl.polishforests.worldgen.landscape.Noise;
import pl.polishforests.worldgen.landscape.Substrate;

/**
 * Habitat classifier (Z1, docs/03-m2-biomy.md §3.4): a pure function of a column sample and its position,
 * returning a {@link Habitat} code. It is called by the biome source, {@code fill()}, the vegetation dispatchers,
 * the commands, the PNG preview and the tests, so biome, soil and vegetation cannot diverge.
 *
 * <p>The order is the priority: water ({@link OpenWater}), coast ({@link Coast}), waterside zones
 * ({@link WatersideZones}), mountain belts ({@link AltitudinalBelts}), zonal sites, and in the PRESENT_DAY mode the
 * forest mask ({@link ForestCover}). An earlier step may set a zone without choosing a biome (e.g. CLIFF_FACE
 * in the plateau biome or MONTANE_TALL_HERBS in a zonal forest); a later step then chooses the biome.
 *
 * <p>The position (x, z) is used only by the noises for zone boundary jitter, patches and variants, derived from the
 * world seed via {@code habitat.*}, so the result depends only on the seed, scale, mode, sample and position.
 * An instance is immutable and thread-safe.
 */
public final class HabitatClassifier {
	/** Vegetation mode: NATURAL "natural vegetation" (the default until M8, decision M2-B), PRESENT_DAY "present-day Poland". */
	public enum Mode {
		NATURAL, PRESENT_DAY
	}

	final LandscapeScale scale;
	final Mode mode;
	/** The scale's {@code local}: the "·k" distance multiplier. */
	final double k;
	/** The scale's channel width multiplier: W / chan is the width at 1:1 scale. */
	final double chan;
	final boolean gameplay;
	/** Zone boundary jitter (wavelength 30–60 m·k). */
	final Noise borders;
	/** Patches: floating-leaved plants, reedbed in patches, gaps in riparian forest, backswamps. */
	final Noise patches;
	/** Mountain belt transitions (wavelength 150 m·k). */
	final Noise belts;
	/** Variants within sites (lowland beech forest, heath openings), wavelength 1.5 km·k. */
	final Noise variants;
	/** Patches of the stunted spruces in the timberline ramp (step S8b, rule Z10). */
	final Noise timberline;
	final ForestCover forestCover;

	public HabitatClassifier(long seed, LandscapeScale scale, Mode mode) {
		this.scale = scale;
		this.mode = mode;
		this.k = scale.local();
		this.chan = scale.channel();
		this.gameplay = scale != LandscapeScale.REALISTIC;
		Noise root = new Noise(seed);
		this.borders = root.derive("habitat.granice");
		this.patches = root.derive("habitat.platy");
		this.belts = root.derive("habitat.pietra");
		this.variants = root.derive("habitat.warianty");
		// Step S8b: a new noise field with a new salt.
		this.timberline = root.derive("habitat.timberline.patches");
		// Step S8: the second octave of F is a new noise field with its own salt; round 1 of the S8 review: the woodlot
		// octave and the context noise, new fields with new salts.
		this.forestCover = new ForestCover(root.derive("habitat.lesistosc"), root.derive("habitat.forest_cover.fine"),
				root.derive("habitat.forest_cover.woodlots"), root.derive("habitat.forest_cover.context"), k);
	}

	public Mode mode() {
		return mode;
	}

	public LandscapeScale scale() {
		return scale;
	}

	/** Habitat code of the column at point (x, z) with its sample {@code s}. */
	public int classify(ColumnSample s, double x, double z) {
		Column c = new Column(this, s, x, z);
		int w = OpenWater.classify(c);
		if (w == Result.NONE) {
			w = Result.further(w, Coast.classify(c));
			if (Result.biome(w) == null) {
				w = Result.further(w, WatersideZones.classify(c));
			}
			if (Result.biome(w) == null) {
				w = Result.further(w, AltitudinalBelts.classify(c));
			}
			if (Result.biome(w) == null) {
				w = Result.further(w, zonal(c));
			}
		}
		HabitatBiome biome = Result.biome(w);
		Zone zone = Result.zone(w);
		Association association = Result.association(w);
		Fertility t = c.fertility();
		Moisture moisture = c.moisture();
		ForestSiteType siteType = biome.isForest() ? ForestSiteType.forBiome(biome, t, moisture) : ForestSiteType.NONE;
		if (mode == Mode.PRESENT_DAY && biome.isForest() && !forestCover.isForested(c, biome, siteType)) {
			HabitatBiome openLand = forestCover.nonForest(c, biome, siteType);
			biome = openLand;
		}
		int flags = biome.isForest() || siteType != ForestSiteType.NONE ? SpeciesRanges.flags(c, siteType) : 0;
		return Habitat.pack(biome, zone, siteType, association, LandCover.forBiome(biome), flags, Soil.forBiome(biome, zone));
	}

	/** Habitat of the column as a record (commands, preview, tests). */
	public Habitat habitat(ColumnSample s, double x, double z) {
		return Habitat.of(classify(s, x, z));
	}

	/** Depth to groundwater in the column (m), as in the classification (§3.3). */
	public double dgw(ColumnSample s, double x, double z) {
		return new Column(this, s, x, z).dgw();
	}

	/** Fertility of the column, as in the classification (§3.3). */
	public Fertility fertility(ColumnSample s, double x, double z) {
		return new Column(this, s, x, z).fertility();
	}

	// ------------------------------------------------------------------ zonal sites (§2.1, step 5)

	/** Zonal biome from fertility, moisture and landforms (§2.1) with the range rules (§9). */
	static int zonal(Column c) {
		Fertility t = c.fertility();
		Moisture w = c.moisture();
		double h = c.H;
		HabitatBiome b;
		Association z = Association.TYPICAL;
		if (w == Moisture.BOGGY) {
			// Bog woodland up to 400 m, alder carr up to 500 m; above that, moist sites.
			if (t == Fertility.OLIGOTROPHIC || t == Fertility.OLIGO_MESOTROPHIC) {
				b = h < Calibration.H_BOG_WOODLAND ? HabitatBiome.BOG_WOODLAND : HabitatBiome.MOIST_PINE_FOREST;
			} else {
				b = h < Calibration.H_ALDER_CARR ? HabitatBiome.ALDER_CARR : t == Fertility.EUTROPHIC ? HabitatBiome.OAK_HORNBEAM_FOREST : HabitatBiome.MIXED_FOREST;
			}
		} else if (t == Fertility.OLIGOTROPHIC) {
			b = switch (w) {
				case DRY -> h < Calibration.H_DRY_PINE ? HabitatBiome.DRY_PINE_FOREST : HabitatBiome.FRESH_PINE_FOREST;
				case FRESH -> h < Calibration.H_FRESH_PINE ? HabitatBiome.FRESH_PINE_FOREST : HabitatBiome.MIXED_PINE_FOREST;
				default -> HabitatBiome.MOIST_PINE_FOREST;
			};
			if (b == HabitatBiome.DRY_PINE_FOREST && c.classifier.mode == Mode.NATURAL
					&& c.patchQ(8, Calibration.HEATH_OPENING_WAVELENGTH) > 1 - Calibration.HEATH_SHARE_NATURAL) {
				// Openings on dunes (NATURAL mode, ≤ 5% of the outwash plain).
				b = HabitatBiome.HEATH;
			}
		} else if (t == Fertility.OLIGO_MESOTROPHIC) {
			if (w == Moisture.MOIST) {
				b = HabitatBiome.MOIST_PINE_FOREST;
			} else {
				b = firForest(c, t) ? HabitatBiome.UPLAND_FIR_FOREST : HabitatBiome.MIXED_PINE_FOREST;
			}
		} else {
			// LM and L: mixed forest or oak-hornbeam forest, lowland beech forest within the beech range, fir forest at P ≥ 0.5.
			if (firForest(c, t)) {
				b = HabitatBiome.UPLAND_FIR_FOREST;
			} else if (beechForest(c, w)) {
				b = HabitatBiome.LOWLAND_BEECH_FOREST;
				z = t == Fertility.MESOTROPHIC ? Association.ACIDOPHILOUS : Association.TYPICAL;
			} else {
				b = t == Fertility.EUTROPHIC ? HabitatBiome.OAK_HORNBEAM_FOREST : HabitatBiome.MIXED_FOREST;
			}
			if (b == HabitatBiome.OAK_HORNBEAM_FOREST && c.valleySlope()) {
				z = Association.SLOPE;
			}
		}
		return Result.of(b, Zone.NONE, z);
	}

	/**
	 * Upland fir forest (§2.1 no. 14): P ≥ 0.5, fir within range, fertility BM, LM or L (L only on N slopes),
	 * 250–650 m, off valley floors.
	 */
	static boolean firForest(Column c, Fertility t) {
		if (c.P < Calibration.P_FIR_FOREST || c.H < Calibration.H_FIR_FOREST_FROM || c.H > Calibration.H_FIR_FOREST_TO
				|| !SpeciesRanges.fir(c.P, c.H, null) || t == Fertility.OLIGOTROPHIC || c.onValleyFloor()) {
			return false;
		}
		return t != Fertility.EUTROPHIC || c.aspectN();
	}

	/**
	 * Lowland beech forest: beech within range, fresh and drained, H &lt; 350 m; the share grows with O. The fresh/moist
	 * boundary jitters by ±{@link Calibration#BEECH_DGW_JITTER} m of DGW (step H, round 1 of the review).
	 */
	static boolean beechForest(Column c, Moisture w) {
		if (w != Moisture.FRESH && w != Moisture.DRY && w != Moisture.MOIST || c.H >= Calibration.H_LOWLAND_BEECH
				|| !SpeciesRanges.beech(c.O, c.P)) {
			return false;
		}
		if (w != Moisture.DRY) {
			// On flat ground DGW follows the slowly changing type weights, so its isoline DGW_FRESH is a straight line.
			double d = c.dgw();
			boolean fresh = w == Moisture.FRESH;
			if (Math.abs(d - Calibration.DGW_FRESH) < Calibration.BEECH_DGW_JITTER) {
				fresh = d + Calibration.BEECH_DGW_JITTER * c.jitterNoise(19, Calibration.FERTILITY_JITTER_WAVELENGTH)
						> Calibration.DGW_FRESH;
			}
			if (!fresh) {
				return false;
			}
		}
		if (!(c.concavity() >= -Calibration.BEECH_CONCAVITY || c.slope > Calibration.SLOPE_BEECH)) {
			return false;
		}
		double share = Calibration.BEECH_MAX
				* Noise.smoothstep(Calibration.O_BEECH_FROM, Calibration.O_BEECH_TO, Math.max(c.O, c.P));
		// Step H (Z9): the variant noise has a 1.5 km·k wavelength, so its isolines are long arcs at the scale of a few
		// hundred meters; the threshold jitters like the fertility thresholds, by at most half of the shares on both
		// sides (a share of 0 stays 0, so no beech islets appear at the edge of its range).
		double q = c.variant(1);
		double a = Math.min(Calibration.FERTILITY_JITTER, 0.5 * Math.min(share, 1 - share));
		if (a > 0 && Math.abs(q - share) < a) {
			q += a * c.jitterNoise(15, Calibration.FERTILITY_JITTER_WAVELENGTH);
		}
		return q < share;
	}

	// ------------------------------------------------------------------ partial result of the steps

	/**
	 * Partial result of a classification step in a single {@code int}: biome + 1 (0 = none, i.e. "continue"), zone
	 * and association. No objects, because the classification runs for every column.
	 */
	static final class Result {
		static final int NONE = 0;

		private Result() {
		}

		static int of(HabitatBiome b, Zone s, Association z) {
			return (b == null ? 0 : b.ordinal() + 1) | s.ordinal() << 8 | z.ordinal() << 16;
		}

		static int zone(Zone s) {
			return of(null, s, Association.TYPICAL);
		}

		static HabitatBiome biome(int w) {
			int b = w & 255;
			return b == 0 ? null : HabitatBiome.of(b - 1);
		}

		static Zone zone(int w) {
			return Zone.of(w >>> 8 & 255);
		}

		static Association association(int w) {
			return Association.of(w >>> 16 & 255);
		}

		/**
		 * Combines the result of the earlier steps with the result of the next one: the zone and association of the
		 * earlier step take precedence, the biome comes from the next one.
		 */
		static int further(int earlier, int next) {
			Zone s = zone(earlier) != Zone.NONE ? zone(earlier) : zone(next);
			Association z = association(earlier) != Association.TYPICAL ? association(earlier) : association(next);
			return of(biome(next), s, z);
		}
	}

	// ------------------------------------------------------------------ derived quantities of a column

	/**
	 * Derived quantities of a single column (§3.3), computed once and lazily. An object local to a single call.
	 */
	static final class Column {
		final HabitatClassifier classifier;
		final ColumnSample s;
		final ColumnSample.Terrain t;
		final ColumnSample.Waters w;
		final double x;
		final double z;
		/**
		 * Ground elevation (m a.s.l.) without the finer octave of the floor micro-relief ({@code Waters.floorFine}, step
		 * K8c, review round 1): every height above the water (h, DGW, the bank and seep belts) then follows the 90 m·k
		 * floor noise only (Z9: a biome only from inputs with a wavelength of at least 64 m).
		 */
		final double H;
		final double k;
		/** Type weights including the coastal belt (sum 1). */
		final double wOutwashPlain;
		final double wMorainePlateau;
		final double wOldGlacialPlain;
		final double wFoothills;
		final double wBeskids;
		final double wCoastland;
		/** Lowland weight and mountain weight (FOOTHILLS + BESKIDS). */
		final double wLowland;
		final double wMountains;
		final double O;
		final double P;
		/** Slope in degrees for comparisons with thresholds: in GAMEPLAY converted to blocks (§3.2). */
		final double slope;
		/** Substrate for habitats ({@link #substrate()}). */
		final Substrate substrate;
		private Fertility fertility;
		private double dgw = Double.NaN;
		private Moisture moisture;
		/** Valley floor by terrain (0 no, 1 yes, −1 not computed). */
		private int onValleyFloor = -1;
		private double jitter = Double.NaN;
		private double uJitter = Double.NaN;

		Column(HabitatClassifier classifier, ColumnSample s, double x, double z) {
			this.classifier = classifier;
			this.s = s;
			this.t = s.terrain();
			this.w = s.waters();
			this.x = x;
			this.z = z;
			this.H = s.surface() - w.floorFine();
			this.k = classifier.k;
			double coastland = Math.clamp(t.wCoastland(), 0.0, 1.0);
			double f = 1 - coastland;
			this.wCoastland = coastland;
			this.wOutwashPlain = f * t.wOutwashPlain();
			this.wMorainePlateau = f * t.wMorainePlateau();
			this.wOldGlacialPlain = f * t.wOldGlacialPlain();
			this.wFoothills = f * t.wFoothills();
			this.wBeskids = f * t.wBeskids();
			this.wLowland = wOutwashPlain + wMorainePlateau + wOldGlacialPlain + wCoastland;
			this.wMountains = wFoothills + wBeskids;
			this.O = s.region().oceanicity();
			this.P = s.region().mountainInfluence();
			double n = t.slope();
			if (classifier.gameplay && n > 0) {
				double tan = Math.tan(Math.toRadians(n)) * Calibration.gameplayBlocksPerMeter(t.sBar());
				n = Math.toDegrees(Math.atan(tan));
			}
			this.slope = n;
			this.substrate = substrate(s, t);
		}

		/**
		 * Substrate for habitats: the deposit from the sample, except for the coastal belt on a low shore. The model
		 * gives till to every column of this belt higher than 8 m, including a high foredune and the dunes behind it,
		 * so on a dune shore ({@code lowShore} ≥ {@link Calibration#LOW_SHORE}) such till is the sand of the beach and
		 * white dune or the sand of the gray dune, as with a lower dune (boundary from {@code bareSandWidth}). The same
		 * holds for the alluvium of a valley floor in the dune belt outside the river mouth ({@link #duneOverFloor}).
		 */
		private Substrate substrate(ColumnSample s, ColumnSample.Terrain t) {
			Substrate sub = s.substrate();
			if (sub == Substrate.GLACIAL_TILL && s.type() == LandscapeType.COASTLAND && t.lowShore() >= Calibration.LOW_SHORE
					&& !Double.isNaN(t.bareSandWidth())) {
				return t.coastD() < t.bareSandWidth() ? Substrate.BEACH_SAND : Substrate.SAND;
			}
			if (sub == Substrate.ALLUVIUM && duneOverFloor()) {
				return t.coastD() < t.bareSandWidth() ? Substrate.BEACH_SAND : Substrate.SAND;
			}
			return sub;
		}

		/**
		 * Step H (D5): a column of the dune belt of a dune shore (beach, white and gray dunes: cD &lt; {@link #duneBeltEnd})
		 * outside the river mouth, i.e. farther than max({@link Calibration#MOUTH_K}·k, {@link Calibration#MOUTH_W}·W) from
		 * the nearest channel (±20%). There the dunes run across the valley floor: the flat hinterland of D5 lies at the
		 * level of the floors, so the floodplain forest of a river reached the beach. Behind the dune belt the floor keeps
		 * the waterside zones (round 1 of the review: a hinterland rule up to the crowberry pine forest drew straight
		 * cuts across the floors at its gate and at the height thresholds of the lagoon hinterland).
		 */
		boolean duneOverFloor() {
			// lowShore is NaN only without a sample of the coast; the belt end bounds cD, so the gate is the dune belt itself.
			if (!(t.lowShore() >= Calibration.LOW_SHORE)) {
				return false;
			}
			double cD = t.coastD();
			if (!(cD >= 0 && cD < beachWidth() + (Calibration.DUNES_D + Calibration.GRAY_DUNE_K * (1 + Calibration.GRAY_DUNE_JITTER)) * k)
					|| cD >= duneBeltEnd()) {
				return false;
			}
			if (w.streamOrder() <= 0 || !Double.isFinite(w.channelDist())) {
				return true;
			}
			double mouth = Math.max(Calibration.MOUTH_K * k, Calibration.MOUTH_W * (Double.isNaN(w.channelWidth()) ? 0 : w.channelWidth()));
			return w.channelDist() > mouth * (1 + Calibration.WIDTH_JITTER * jitterNoise(17, Calibration.FERTILITY_JITTER_WAVELENGTH));
		}

		/**
		 * Landward end of the dune belt (beach, white and gray dunes): B + D + {@link Calibration#GRAY_DUNE_K}·k (B the
		 * {@link #beachWidth} of the column), the gray
		 * dune belt jittered by ±{@link Calibration#GRAY_DUNE_JITTER} with a noise of {@link Calibration#GRAY_DUNE_JITTER_WAVELENGTH} m·k
		 * (step H, round 1 of the review).
		 */
		double duneBeltEnd() {
			double gray = Calibration.GRAY_DUNE_K * (1 + Calibration.GRAY_DUNE_JITTER * jitterNoise(18, Calibration.GRAY_DUNE_JITTER_WAVELENGTH));
			return beachWidth() + (Calibration.DUNES_D + gray) * k;
		}

		/**
		 * Width of the sandy beach (m): from the sample ({@code terrain.beachWidth}, step K8b2: it varies along a low shore),
		 * {@link Calibration#BEACH_B}·k without it.
		 */
		double beachWidth() {
			double b = t.beachWidth();
			return Double.isNaN(b) ? Calibration.BEACH_B * k : b;
		}

		/** A sea shore with dunes (low), not with a cliff. */
		boolean isDuneShore() {
			return t.lowShore() >= Calibration.LOW_SHORE;
		}

		Fertility fertility() {
			if (fertility == null) {
				fertility = Fertility.compute(this);
			}
			return fertility;
		}

		double dgw() {
			if (Double.isNaN(dgw)) {
				dgw = Moisture.dgw(this);
			}
			return dgw;
		}

		Moisture moisture() {
			if (moisture == null) {
				moisture = Moisture.compute(this);
			}
			return moisture;
		}

		/** Zone width multiplier 1 ± 0.2 (noise with a wavelength of 45 m·k). */
		double jitter() {
			if (Double.isNaN(jitter)) {
				jitter = 1 + Calibration.WIDTH_JITTER * classifier.borders.at(x, z, Calibration.BORDER_WAVELENGTH * k);
			}
			return jitter;
		}

		/**
		 * Valley floor by terrain: the model's {@code inFloor} flag with the ground at most {@link Calibration#BANK_H}
		 * above the water, or off the flag the floor margin: ground at most {@link Calibration#FLOOR_H} above the water
		 * and within the floor half-width from the channel. The water level is the soft level of the nearby channels
		 * ({@code softChannelLevel}, step H, G3): the level of the nearest channel steps on the straight bisectors between
		 * the channels of a confluence, and the floor ended there in polygonal patches. On the flag the limit is
		 * BANK_H, because the floor of a mouth funnel lies on the level of the receiving valley, a little higher than the
		 * tributary (the S4 limit FLOOR_H cut it with spikes); above BANK_H the flag lies along a channel stretch without
		 * water ({@link #byWater}). When the ground lies less than 1.2 m above the water level (a tributary descending in
		 * a rapid to the floor of a larger valley), the flag alone decides.
		 */
		boolean onValleyFloor() {
			if (onValleyFloor < 0) {
				onValleyFloor = onValleyFloor(w.channelDist(), w.softChannelLevel()) ? 1 : 0;
			}
			return onValleyFloor == 1;
		}

		/**
		 * {@link #onValleyFloor()} measured from the given channel instead of the nearest one (F2: the channel of the
		 * dominant valley, {@code floorChannelDist} and {@code floorChannelLevel}); not cached.
		 */
		boolean onValleyFloor(double channelDist, double channelLevel) {
			boolean d = w.inFloor();
			if (w.streamOrder() > 0 && !s.hasWater() && !Double.isNaN(channelLevel)) {
				double hl = H - channelLevel;
				if (hl >= Calibration.OTHER_CHANNEL_H) {
					double range = Math.max(Calibration.FLOOR_MIN_K * k, Double.isNaN(w.floorHalfWidth()) ? 0 : w.floorHalfWidth());
					// Step H (G3): on the model floor up to BANK_H above the water (the floor of a mouth funnel lies on the
					// level of the receiving valley, a little higher than the tributary), off it the floor margin up to FLOOR_H.
					d = d ? hl <= Calibration.BANK_H : hl <= Calibration.FLOOR_H && channelDist <= range;
				}
			}
			return d;
		}

		/**
		 * By real water (step H): the ground lies at most {@link Calibration#BANK_H} above the soft water level of the
		 * nearby channels ({@code softChannelLevel}). False along a channel stretch that the valley does not cut, where
		 * the model has a channel without water far below the ground.
		 */
		boolean byWater() {
			double level = w.softChannelLevel();
			return s.hasWater() || Double.isNaN(level) || H - level <= Calibration.BANK_H;
		}

		/** Position on the floor 0–1: from the model, and on the terrain-based floor outside the {@code inFloor} flag from d / half-width. */
		double u() {
			if (w.inFloor() && !Double.isNaN(w.u())) {
				return w.u();
			}
			return u(w.channelDist(), onValleyFloor());
		}

		/** {@link #u()} measured from the given channel, with {@code onFloor} = {@link #onValleyFloor(double, double)} for it. */
		private double u(double channelDist, boolean onFloor) {
			if (w.inFloor() && !Double.isNaN(w.u())) {
				return w.u();
			}
			if (!onFloor) {
				return Double.NaN;
			}
			double half = w.floorHalfWidth();
			return Double.isNaN(half) || half <= 0 ? 0 : Math.clamp(Math.max(0, channelDist) / half, 0.0, 1.0);
		}

		/** Position on the floor with ±0.05 jitter. */
		double uJittered() {
			if (Double.isNaN(uJitter)) {
				uJitter = u() + Calibration.U_JITTER * classifier.borders.at(x + 7_777, z - 3_333, Calibration.BORDER_WAVELENGTH * k);
			}
			return uJitter;
		}

		/** {@link #uJittered()} measured from the given channel (F2); not cached. */
		double uJittered(double channelDist, boolean onFloor) {
			return u(channelDist, onFloor) + Calibration.U_JITTER * classifier.borders.at(x + 7_777, z - 3_333, Calibration.BORDER_WAVELENGTH * k);
		}

		/** Quantile of the patch noise at the given wavelength (m·k), in [0, 1]. */
		double patchQ(int layer, double wavelength) {
			return LandscapeModel.noiseQuantile(classifier.patches.at(x + 1_013.0 * layer, z - 517.0 * layer, wavelength * k));
		}

		/** Patch noise in [−1, 1] (wavelength 35 m·k); {@code layer} separates independent decisions. */
		double patch(int layer) {
			return classifier.patches.at(x + 1_013.0 * layer, z - 517.0 * layer, Calibration.PATCH_WAVELENGTH * k);
		}

		/** Patch noise in [−1, 1] at the given wavelength (m·k); {@code layer} separates independent decisions. */
		double jitterNoise(int layer, double wavelength) {
			return classifier.patches.at(x + 1_013.0 * layer, z - 517.0 * layer, wavelength * k);
		}

		/** Patch quantile in [0, 1] (uniform distribution). */
		double patchQ(int layer) {
			return LandscapeModel.noiseQuantile(patch(layer));
		}

		/** Quantile of the variant noise in [0, 1] (wavelength 1.5 km·k). */
		double variant(int layer) {
			return LandscapeModel.noiseQuantile(
					classifier.variants.at(x + 2_029.0 * layer, z + 911.0 * layer, Calibration.VARIANT_WAVELENGTH * k));
		}

		/** cos(aspect − 180°): 1 on an S slope, −1 on an N slope; 0 on flat ground and below 5°. */
		double aspect() {
			double e = t.aspect();
			if (Double.isNaN(e) || slope < AltitudinalBelts.ASPECT_MIN_SLOPE) {
				return 0;
			}
			return Math.cos(Math.toRadians(e - 180.0));
		}

		boolean aspectN() {
			return aspect() < 0;
		}

		/**
		 * Terrain convexity at a scale of about 100 m·k: terrain before valley incision minus the smoothed terrain (m).
		 * Replaces {@code terrain.convexity} in the biome rules, because {@code convexity} has short-wave components
		 * (outwash plain undulation, Z9).
		 */
		double concavity() {
			return t.rawSurface() - t.sBar();
		}

		/** Valley side: within reach of a watercourse, off the floor, terrain incised below the pre-valley terrain. */
		boolean valleySlope() {
			return w.streamOrder() > 0 && !onValleyFloor() && !s.hasWater() && H < t.rawSurface() - 2;
		}

		/**
		 * Seep at the foot of a valley side: a boggy site (DGW ≤ 0.5, in the 0.5–0.8 band by noise) on fertile
		 * ground (LM, L) below 600 m, off the floor but in the valley and low above the watercourse: terrain incised at
		 * least {@link Calibration#INCISION_FROM} below the pre-valley terrain and at most {@link Calibration#SEEP_HL}
		 * above the water level of the nearest channel. No distance condition: the seep boundary is then set by the
		 * boggy site boundary or a contour line, not by a line parallel to the channel, along which shreds of riparian
		 * forest narrower than 6 blocks were left. No valley side condition (incision ≥ 2 m), because then the floor
		 * margin got a narrow strip of zonal alder carr.
		 */
		boolean isSeep() {
			if (w.streamOrder() <= 0 || onValleyFloor() || s.hasWater() || H >= Calibration.H_ASH_ALDER_RIPARIAN || Double.isNaN(w.softChannelLevel())
					|| t.rawSurface() - H < Calibration.INCISION_FROM || H - w.softChannelLevel() > Calibration.SEEP_HL) {
				return false;
			}
			Fertility fert = fertility();
			return (fert == Fertility.MESOTROPHIC || fert == Fertility.EUTROPHIC) && moisture() == Moisture.BOGGY;
		}

		/** Channel width at 1:1 scale (m). */
		double wr() {
			return wr(w.channelWidth());
		}

		/** Width of the given channel at 1:1 scale (m). */
		double wr(double channelWidth) {
			return channelWidth / classifier.chan;
		}

		/**
		 * Height above the water level of the nearest channel minus 1 m (h, §3.3). NaN without a watercourse and when
		 * the ground lies lower than 1.2 m above that water level: the model floor lies at least 1.2 m above the water
		 * level of its own channel, so the nearest channel is then a different one (a tributary descending in a rapid
		 * to the floor of a larger valley).
		 */
		double heightAboveChannel() {
			return heightAboveChannel(w.softChannelLevel());
		}

		/** {@link #heightAboveChannel()} above the water level of the given channel (F2). */
		double heightAboveChannel(double channelLevel) {
			double hl = H - channelLevel;
			return hl < Calibration.OTHER_CHANNEL_H ? Double.NaN : hl - 1;
		}

		/**
		 * Spring area: the HEADWATERS landform, a belt from the channel with a width of 0–40 m·k varying with the patch
		 * noise (on average {@link Calibration#SPRING_AREA_SHARE} of the belt). The belt starts at the channel, so the
		 * spring-area riparian forest joins the floor's riparian forest and does not break into shreds narrower than
		 * 6 blocks. The noise selects the biome, so it has a wavelength of {@link Calibration#SPRING_AREA_WAVELENGTH} m
		 * without the k multiplier (Z9: a biome only from inputs with a wavelength ≥ 64 m).
		 */
		boolean isSpringArea() {
			if (!t.has(Landform.HEADWATERS)) {
				return false;
			}
			double u = 2 * Calibration.SPRING_AREA_SHARE;
			double f = Math.clamp((patchQ(9, Calibration.SPRING_AREA_WAVELENGTH / k) - (1 - u)) / u, 0.0, 1.0);
			return f > 0 && w.channelDist() <= Calibration.SPRING_AREA_K * k * jitter() * f;
		}

		boolean sandy() {
			Fertility fert = fertility();
			return fert == Fertility.OLIGOTROPHIC || fert == Fertility.OLIGO_MESOTROPHIC;
		}
	}
}
