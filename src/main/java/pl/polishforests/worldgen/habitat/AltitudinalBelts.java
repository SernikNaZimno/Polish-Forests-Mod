package pl.polishforests.worldgen.habitat;

import pl.polishforests.worldgen.landscape.Landform;
import pl.polishforests.worldgen.landscape.Noise;
import pl.polishforests.worldgen.landscape.Substrate;

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
 * (round 1 of the S8b review: both borders are mosaics of patches 40 m and 50 m either side, §8.8)
 * largeMassif       = summit within 3 km·mspace > 1470 m (E12), from the terrain.summit field
 * </pre>
 *
 * At gameplay scale the slope is converted to blocks ({@link HabitatClassifier.Column#slope}). The beech and spruce
 * ranges (§9) are not checked here: in the mountain belt (wFoothills + wBeskids > 0.5) P is close to 1, and both
 * species have their range at P ≥ 0.4–0.5, so montane beech and spruce forests lie within range by the geography
 * of the regional fields. The upland fir forest below the lower montane belt has the same conditions as the zonal
 * one ({@link HabitatClassifier#firForest}) and does not enter valley floors.
 *
 * <p>Step TT3 (docs/04-pionowy-wycinek.md "Projekt TT3"): the belt elevations are a {@link BeltProfile}. The constants
 * above are {@link BeltProfile#BESKIDS}, whose codes are unchanged ({@code BeltProfileBeskidsTest}); a column of a
 * Tatra complex blends them with {@link BeltProfile#TATRA} by its Tatra share s = smoothstep(0.3, 0.9, wTatra): every
 * threshold is {@code blend(beskids, tatra, s)} with the same aspect and noise correction, the montane border gets a
 * mosaic of ±30·s m and the subnival border (only at s &gt; 0) one of ±50·s m, and from s ≥ 0.5 the large massif
 * condition no longer applies. On the Tatra rock the associations follow the rock: the lower montane belt is the
 * Carpathian beech forest with fir on the limestone and the fir-spruce forest (Abieti-Piceetum) on the granite and the
 * gneiss, the upper montane belt Polysticho-Piceetum on the limestone, the alpine grassland the granite or limestone
 * grassland, the snowbeds and the rock grasslands. The new noise fields read {@code habitat.tatra.mosaic}.
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
	/** Belt of stunted spruces below the timberline (m): its mean depth, the area of the ramp below. */
	public static final double BORDER_BAND = 60;
	/**
	 * Ramp of the stunted spruces below the timberline (m, step S8b, rule Z10): their patches cover a share of the
	 * columns that grows from 0 at this depth below the timberline to 1 at it (before S8b a belt of
	 * {@link #BORDER_BAND} with a sharp lower border, where the stand fell from 12 to 4 trees per chunk).
	 */
	public static final double TIMBERLINE_RAMP = 2 * BORDER_BAND;
	/**
	 * Single stunted spruces above the timberline (m, step S8b): in the dwarf pine scrub the patches of the stunted
	 * spruces cover half of the columns at the timberline and none at this height above it.
	 */
	public static final double TIMBERLINE_ABOVE = 40;
	/**
	 * Wavelength of the noise of the stunted spruce patches (m·k; round 1 of the S8b review: 40 m·k within the 30–60 m of
	 * Z9 instead of 12, a new field with a new salt).
	 */
	static final double TIMBERLINE_PATCHES = 40;
	/**
	 * Mosaic of the dwarf pine scrub and the spruce forest at the timberline (m, round 1 of the S8b review): the share of
	 * the dwarf pine rises from 0 at this depth below the timberline through 1/2 at it to 1 as far above it, in patches of
	 * the mosaic noise; the dwarf pine patches below the timberline have the stunted spruces of the zone {@code TIMBERLINE},
	 * the spruce patches above it are stunted spruce forest.
	 */
	public static final double KRUMMHOLZ_RAMP = 40;
	/** The same mosaic of the alpine grassland and the dwarf pine scrub around the alpine threshold (m). */
	public static final double ALPINE_RAMP = 50;
	/** Wavelength of the mosaic noise (m·k, Z9). */
	static final double MOSAIC_WAVELENGTH = 40;
	/** Largest possible lowering of a threshold (aspect and noise), used to skip the noise low down. */
	public static final double MAX_LOWERING = ASPECT + TEMPERATURE_NOISE;
	/**
	 * Lowest elevation at which a large massif changes anything (the timberline band on a ridge at the largest
	 * lowering). The model computes the {@code terrain.summit} field only from this elevation.
	 */
	public static final double SUMMIT_FROM = TIMBERLINE - MAX_LOWERING - WINDY_RIDGE - TIMBERLINE_RAMP;
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

	/**
	 * Step TT3: the Tatra weight of the column smoothed to the share of the {@link BeltProfile#TATRA} profile: 0 up to a
	 * weight of {@value #TATRA_FROM} (the whole world outside the Tatra complexes and the Podhale), 1 from
	 * {@value #TATRA_TO} (the massif), a smoothstep between them (the foot of the massif, Z10).
	 */
	static double tatraShare(HabitatClassifier.Column c) {
		double w = c.t.wTatra();
		return w <= TATRA_FROM ? 0 : Noise.smoothstep(TATRA_FROM, TATRA_TO, w);
	}

	/** Tatra weight from which the belts start to follow the Tatra profile, and the weight of the full profile. */
	static final double TATRA_FROM = 0.3;
	static final double TATRA_TO = 0.9;

	/**
	 * Classifier step 4: belts in the mountain belt (wFoothills + wBeskids > 0.5); {@code Result.NONE} outside it and low
	 * down. Step TT3: the thresholds blend the {@link BeltProfile#BESKIDS} and the {@link BeltProfile#TATRA} profile by
	 * {@link #tatraShare}; with a share of 0 every comparison is the one of before the step (the code is identical). On
	 * the Tatra rocks the associations follow the rock ({@link #tatraForest}, {@link #alpineAssociation}).
	 */
	static int classify(HabitatClassifier.Column c) {
		if (c.wMountains <= 0.5) {
			return HabitatClassifier.Result.NONE;
		}
		double h = c.H;
		double corr = correction(c);
		double s = tatraShare(c);
		BeltProfile tatra = BeltProfile.TATRA;
		Substrate rock = s > 0 ? c.t.rock() : null;
		boolean tatraRock = rock != null && rock.isTatraRock();
		boolean large = largeMassif(c) || s >= 0.5;
		boolean onRidge = c.t.has(Landform.RIDGE);
		if (s > 0) {
			double subnival = BeltProfile.blend(BeltProfile.NO_SUBNIVAL, tatra.subnivalThreshold(), s) + corr;
			double ramp = BeltProfile.blend(0, tatra.subnivalRamp(), s);
			if (above(c, h, subnival, ramp, 1)) {
				return HabitatClassifier.Result.of(HabitatBiome.SUBNIVAL_ROCKS, Zone.NONE, Association.TYPICAL);
			}
		}
		double alpine = BeltProfile.blend(ALPINE_THRESHOLD, tatra.alpineThreshold(), s) + corr;
		if (large && h >= alpine - ALPINE_RAMP && (h >= alpine + ALPINE_RAMP
				|| mosaic(c, true) < 0.5 + (h - alpine) / (2 * ALPINE_RAMP))) {
			return HabitatClassifier.Result.of(HabitatBiome.ALPINE_GRASSLAND, Zone.NONE,
					tatraRock ? alpineAssociation(c, rock) : Association.TYPICAL);
		}
		double limit = BeltProfile.blend(TIMBERLINE, tatra.timberline(), s) + corr
				- (onRidge && c.slope < WINDY_RIDGE_SLOPE ? WINDY_RIDGE : 0);
		if (large && h >= limit - KRUMMHOLZ_RAMP && (h >= limit + KRUMMHOLZ_RAMP
				|| mosaic(c, false) < 0.5 + (h - limit) / (2 * KRUMMHOLZ_RAMP))) {
			// Stunted spruces among the dwarf pines: in all of them at the lowest dwarf pine patches, in half of them at the
			// timberline, none at TIMBERLINE_ABOVE over it.
			Zone s0 = h < limit + TIMBERLINE_ABOVE && timberlinePatch(c) > 0.5 + 0.5 * (h - limit) / TIMBERLINE_ABOVE
					? Zone.TIMBERLINE : Zone.NONE;
			// Step TT4: the lower part of the dwarf pine on granite and gneiss has single stone pines.
			Association z = tatraRock && crystalline(rock) && h < limit + STONE_PINE_ABOVE ? Association.PINO_CEMBRAE_PICEETUM
					: Association.TYPICAL;
			return HabitatClassifier.Result.of(HabitatBiome.DWARF_PINE_SCRUB, s0, z);
		}
		double upper = BeltProfile.blend(UPPER_MONTANE, tatra.upperMontane(), s) + corr;
		if (above(c, h, upper, BeltProfile.blend(0, tatra.montaneRamp(), s), 2)) {
			Zone s0 = large && h >= limit - TIMBERLINE_RAMP && timberlinePatch(c) > (limit - h) / TIMBERLINE_RAMP
					? Zone.TIMBERLINE : Zone.NONE;
			Association z;
			if (tatraRock) {
				// Step TT3: the gullies have the tall herbs, the limestone the Polysticho-Piceetum. Step TT4: on granite and
				// gneiss the spruce-stone pine forest in the upper part of the belt, below it the larch on the sunny slopes.
				z = c.t.has(Landform.GULLY) ? Association.GULLY_TALL_HERBS
						: rock == Substrate.LIMESTONE || rock == Substrate.DOLOMITE ? Association.POLYSTICHO_PICEETUM
						: !crystalline(rock) ? Association.TYPICAL
						: stonePine(c, h, limit) ? Association.PINO_CEMBRAE_PICEETUM
						: larchSlope(c) ? Association.LARCH_SLOPE : Association.TYPICAL;
			} else {
				double p0 = c.t.ridgeProfile();
				z = !Double.isNaN(p0) && p0 < GULLY_RIDGE ? Association.GULLY_TALL_HERBS : Association.TYPICAL;
			}
			return HabitatClassifier.Result.of(HabitatBiome.MONTANE_SPRUCE_FOREST, s0, z);
		}
		Fertility t = c.fertility();
		boolean poor = t == Fertility.OLIGOTROPHIC || t == Fertility.OLIGO_MESOTROPHIC;
		double aspectOffset = c.aspect();
		if (h >= BeltProfile.blend(LOWER_MONTANE, tatra.lowerMontane(), s) + corr) {
			if (tatraRock) {
				// Step TT4: the fir-spruce forest of the granite and gneiss with larch on the sunny slopes.
				if (crystalline(rock) && larchSlope(c)) {
					return HabitatClassifier.Result.of(HabitatBiome.MONTANE_SPRUCE_FOREST, Zone.NONE, Association.LARCH_SLOPE);
				}
				return tatraForest(rock, poor);
			}
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

	/**
	 * Whether the column at elevation {@code h} lies in the belt above the border {@code limit} (m, corrected): with a
	 * ramp of 0 the contour itself (the Beskids), else a mosaic of the patches of the Tatra mosaic noise ({@code layer}
	 * separates the borders): no patch of the upper belt {@code ramp} under the border, half of the columns at it, all of
	 * them {@code ramp} above it (step TT3, Z10).
	 */
	static boolean above(HabitatClassifier.Column c, double h, double limit, double ramp, int layer) {
		if (ramp <= 0) {
			return h >= limit;
		}
		return h >= limit - ramp && (h >= limit + ramp || tatraMosaic(c, layer) < 0.5 + (h - limit) / (2 * ramp));
	}

	/**
	 * Step TT3: the lower montane belt of the Tatras by the rock (research/06 §5.1): on limestone and dolomite the
	 * Carpathian beech forest with fir (Dentario glandulosae-Fagetum; acidophilous on the poor sites), on granite and
	 * gneiss the fir-spruce forest (Abieti-Piceetum).
	 */
	static int tatraForest(Substrate rock, boolean poor) {
		if (rock == Substrate.LIMESTONE || rock == Substrate.DOLOMITE) {
			return HabitatClassifier.Result.of(HabitatBiome.MONTANE_BEECH_FOREST, Zone.NONE,
					poor ? Association.ACIDOPHILOUS : Association.TYPICAL);
		}
		return HabitatClassifier.Result.of(HabitatBiome.MONTANE_SPRUCE_FOREST, Zone.NONE, Association.ABIETI_PICEETUM);
	}

	/** Step TT4: depth (m) below the timberline from which the spruce-stone pine forest grows on granite and gneiss. */
	static final double STONE_PINE_BELOW = 125;
	/** Step TT4: half-width (m) of the mosaic of the lower border of the spruce-stone pine forest (Z10). */
	static final double STONE_PINE_RAMP = 40;
	/** Step TT4: height (m) over the timberline up to which the dwarf pine on granite and gneiss has single stone pines. */
	static final double STONE_PINE_ABOVE = 150;
	/** Step TT4: least {@code Column.aspect()} (the cosine of the aspect from south) of a larch slope: within 60° of south. */
	static final double LARCH_ASPECT = 0.5;
	/** Step TT4: share of the sunny slopes in larch patches, and the patch wavelength (m·k). */
	static final double LARCH_SHARE = 0.45;
	static final double LARCH_PATCHES = 120;

	/** Step TT4: the crystalline rocks of the Tatras, granite and gneiss (the stone pine and the larch series). */
	static boolean crystalline(Substrate rock) {
		return rock == Substrate.GRANITE || rock == Substrate.CRYSTALLINE;
	}

	/**
	 * Step TT4: whether a column of the upper montane belt at elevation {@code h} lies in the spruce-stone pine forest
	 * (research/06 §3.5: Pino cembrae-Piceetum on the granite of the High Tatras, about 1400-1550 m): from
	 * {@value #STONE_PINE_BELOW} m below the timberline {@code limit} (corrected) up, the border a mosaic of the Tatra noise
	 * (layer 5) {@value #STONE_PINE_RAMP} m to each side.
	 */
	static boolean stonePine(HabitatClassifier.Column c, double h, double limit) {
		double from = limit - STONE_PINE_BELOW;
		return h >= from - STONE_PINE_RAMP
				&& (h >= from + STONE_PINE_RAMP || tatraMosaic(c, 5) < 0.5 + (h - from) / (2 * STONE_PINE_RAMP));
	}

	/**
	 * Step TT4: whether a column of the Tatra regiel lies in a larch patch: a sunny slope (within 60° of south) and a patch
	 * of the Tatra noise (layer 6, {@value #LARCH_PATCHES} m·k) with the share {@value #LARCH_SHARE}.
	 */
	static boolean larchSlope(HabitatClassifier.Column c) {
		return c.aspect() > LARCH_ASPECT && tatraPatch(c, 6, LARCH_PATCHES) < LARCH_SHARE;
	}

	/** Step TT3: snowbeds in north-facing hollows (cirques, gullies, concave ground): aspect within this many degrees of north. */
	static final double SNOWBED_ASPECT = 60;
	/** Step TT3: concavity (m) below which a column is a hollow of a snowbed. */
	static final double SNOWBED_CONCAVITY = 4;
	/** Step TT3: share of the north-facing hollows in snowbed patches. */
	static final double SNOWBED_SHARE = 0.55;
	/** Step TT3: slope (°, in blocks in the gameplay scale) from which the alpine grassland is a rock grassland (± 5°). */
	static final double ROCK_GRASSLAND_SLOPE = 36;

	/**
	 * Step TT3: the alpine grassland of the Tatras by the site (research/05 §5, research/06 §5.1): snowbeds in the
	 * north-facing hollows ({@link Association#SNOWBED}), rock grasslands on the steep slopes and the pinnacles
	 * ({@link Association#ROCK_GRASSLAND}), elsewhere the grassland of the rock: the three-leaved rush and the two-rowed
	 * oreochloa on granite and gneiss ({@link Association#ALPINE_SILICATE}), the Tatra moor-grass, the variegated fescue,
	 * the edelweiss and the gentians on limestone and dolomite ({@link Association#ALPINE_CALCAREOUS}).
	 */
	static Association alpineAssociation(HabitatClassifier.Column c, Substrate rock) {
		double e = c.t.aspect();
		boolean north = !Double.isNaN(e) && (e < SNOWBED_ASPECT || e > 360 - SNOWBED_ASPECT);
		boolean hollow = (c.t.landformBits() & (Landform.CIRQUE.bit() | Landform.GULLY.bit())) != 0
				|| c.concavity() < -SNOWBED_CONCAVITY;
		if (north && hollow && tatraPatch(c, 3, 40) < SNOWBED_SHARE) {
			return Association.SNOWBED;
		}
		if (c.t.has(Landform.PINNACLE) || c.slope > ROCK_GRASSLAND_SLOPE + 5 * tatraNoise(c, 4, 60)) {
			return Association.ROCK_GRASSLAND;
		}
		return rock == Substrate.LIMESTONE || rock == Substrate.DOLOMITE ? Association.ALPINE_CALCAREOUS : Association.ALPINE_SILICATE;
	}

	/** Step TT3: uniform value 0–1 of the Tatra mosaic noise at the column (wavelength {@value #MOSAIC_WAVELENGTH} m·k). */
	static double tatraMosaic(HabitatClassifier.Column c, int layer) {
		return tatraPatch(c, layer, MOSAIC_WAVELENGTH);
	}

	/** Step TT3: quantile 0–1 of the Tatra noise ({@code habitat.tatra.mosaic}) at the wavelength (m·k), by layer. */
	static double tatraPatch(HabitatClassifier.Column c, int layer, double wavelength) {
		return pl.polishforests.worldgen.landscape.LandscapeModel.noiseQuantile(tatraNoise(c, layer, wavelength));
	}

	/** Step TT3: the Tatra noise in [−1, 1] at the wavelength (m·k), by layer. */
	static double tatraNoise(HabitatClassifier.Column c, int layer, double wavelength) {
		return c.classifier.tatra.at(c.x + 1_259.0 * layer, c.z - 2_087.0 * layer, wavelength * c.k);
	}

	/** Belts of a column for the commands (step TT3, {@link #belt}). */
	public enum Belt {
		/** Outside the mountain belt. */
		NONE,
		FOOTHILLS,
		LOWER_MONTANE,
		UPPER_MONTANE,
		SUBALPINE,
		ALPINE,
		SUBNIVAL;

		public String id() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/**
	 * Step TT3: the belt of the column by the thresholds of its profile with the aspect and noise correction (the middle
	 * of each mosaic; the dwarf pine and the alpine grassland of the Beskids only on a large massif), for {@code here}.
	 */
	static Belt belt(HabitatClassifier.Column c) {
		if (c.wMountains <= 0.5) {
			return Belt.NONE;
		}
		double h = c.H;
		double corr = correction(c);
		double s = tatraShare(c);
		BeltProfile b = BeltProfile.TATRA;
		boolean large = largeMassif(c) || s >= 0.5;
		if (h >= BeltProfile.blend(BeltProfile.NO_SUBNIVAL, b.subnivalThreshold(), s) + corr) {
			return Belt.SUBNIVAL;
		}
		if (large && h >= BeltProfile.blend(ALPINE_THRESHOLD, b.alpineThreshold(), s) + corr) {
			return Belt.ALPINE;
		}
		if (large && h >= BeltProfile.blend(TIMBERLINE, b.timberline(), s) + corr) {
			return Belt.SUBALPINE;
		}
		if (h >= BeltProfile.blend(UPPER_MONTANE, b.upperMontane(), s) + corr) {
			return Belt.UPPER_MONTANE;
		}
		return h >= BeltProfile.blend(LOWER_MONTANE, b.lowerMontane(), s) + corr ? Belt.LOWER_MONTANE : Belt.FOOTHILLS;
	}

	/** Step TT3: the profile of the column ({@code beskids}, {@code tatra}, or {@code blend} on the foot of the Tatras). */
	static String profile(HabitatClassifier.Column c) {
		double s = tatraShare(c);
		return s <= 0 ? BeltProfile.BESKIDS.id() : s >= 1 ? BeltProfile.TATRA.id() : "blend";
	}

	/**
	 * Uniform value 0–1 of the patch noise of the stunted spruces at the column: a column of the timberline ramp at depth d
	 * below the timberline is in a patch when the value exceeds d / {@link #TIMBERLINE_RAMP} (step S8b).
	 */
	static double timberlinePatch(HabitatClassifier.Column c) {
		return pl.polishforests.worldgen.landscape.LandscapeModel.noiseQuantile(
				c.classifier.timberline.at(c.x, c.z, TIMBERLINE_PATCHES * c.k));
	}

	/**
	 * Uniform value 0–1 of the mosaic noise at the column (round 1 of the S8b review): the upper belt where it is below
	 * the belt's share at the column's elevation; the alpine border reads the noise at shifted coordinates.
	 */
	static double mosaic(HabitatClassifier.Column c, boolean alpine) {
		double dx = alpine ? 911.0 : 0;
		double dz = alpine ? -1_733.0 : 0;
		return pl.polishforests.worldgen.landscape.LandscapeModel.noiseQuantile(
				c.classifier.timberlineMosaic.at(c.x + dx, c.z + dz, MOSAIC_WAVELENGTH * c.k));
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
