package pl.polishforests.worldgen.habitat;

import pl.polishforests.worldgen.landscape.ColumnSample;
import pl.polishforests.worldgen.landscape.Landform;
import pl.polishforests.worldgen.landscape.Substrate;

/**
 * Krok 3 klasyfikatora: strefy nadwodne (§4, raport ekologii §2). Najpierw pierścienie wód stojących
 * (jeziora, oczka, starorzecza, oczka torfowe), potem cieki według klasy A, B albo C. Strefy wyznaczamy
 * z d, u, W, rzędu i spadku (η nie używamy, bo dno doliny jest płaskie). Granice drgają o ±20% szerokości
 * i ±0,05 u. Minima w blokach (E11): szuwar 2, wiklina 3, OlJ 6, ols 10. Łęgi tylko w dnie doliny,
 * w źródliskach i w wysiękach u podnóża zbocza.
 */
final class WatersideZones {
	private WatersideZones() {
	}

	/** Klasa cieku (§4): A duża rzeka nizinna, B mała rzeka nizinna, C potok górski. */
	enum StreamClass {
		A, B, C
	}

	/** Klasa najbliższego cieku: C w górach lub przy spadku > 3‰, A przy rzędzie 3 lub W ≥ 30 m (1:1). */
	static StreamClass streamClass(HabitatClassifier.Column c) {
		ColumnSample.Waters w = c.w;
		if (c.wMountains > 0.5 || w.channelGradient() > Calibration.CLASS_GRADIENT) {
			return StreamClass.C;
		}
		// Rząd i spadek pochodzą z doliny dominującej, a W z najbliższego koryta: mały dopływ w dnie dużej
		// doliny ma rząd 3, więc rząd 3 daje klasę A dopiero przy szerszym korycie.
		double wr = c.wr();
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

	// ------------------------------------------------------------------ cieki

	private static int stream(HabitatClassifier.Column c) {
		double d = Math.max(0, c.w.channelDist());
		double channelWidth = c.w.channelWidth();
		if (Double.isNaN(channelWidth)) {
			return HabitatClassifier.Result.NONE;
		}
		return switch (streamClass(c)) {
			case A -> classA(c, d, channelWidth);
			case B -> classB(c, d, channelWidth);
			case C -> classC(c, d, channelWidth);
		};
	}

	/** Szerokość pasa z drganiem {@code f}, nie mniejsza niż minimum w blokach (E11). */
	private static double width(double minimum, double base, double f) {
		return Math.max(minimum, base * f);
	}

	/** Biom pasa przy brzegu albo null, gdy pas jest węższy niż biom (wtedy tylko strefa). */
	private static HabitatBiome band(double width, HabitatBiome biome) {
		return width >= Calibration.BIOME_BAND ? biome : null;
	}

	/** Klasa A (§4.1): łacha, wiklina, okrajek, łęg wierzbowy, topolowy, zastoiska, łęg wiązowo-jesionowy. */
	private static int classA(HabitatClassifier.Column c, double d, double channelWidth) {
		ColumnSample.Waters w = c.w;
		double k = c.k;
		double f = c.jitter();
		boolean convex = w.convexBank();
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
		if (!c.onValleyFloor()) {
			return onSlope(c, zone);
		}
		if (zone == Zone.NONE && c.patch(4) < Calibration.A_GAPS) {
			// Luki w łęgu: ziołorośla okrajka.
			zone = Zone.HERB_FRINGE;
		}
		double dWhiteWillow = Math.clamp(Calibration.A_D_WHITE_WILLOW_W * channelWidth, Calibration.A_D_WHITE_WILLOW_MIN * k, Calibration.A_D_WHITE_WILLOW_MAX * k) * f;
		double dPoplar = Math.clamp(Calibration.A_D_POPLAR_W * channelWidth, Calibration.A_D_POPLAR_MIN * k, Calibration.A_D_POPLAR_MAX * k) * f;
		boolean softwood = c.H < Calibration.H_WILLOW_RIPARIAN;
		if (d <= dWhiteWillow && softwood) {
			return HabitatClassifier.Result.of(HabitatBiome.WILLOW_POPLAR_FOREST, zone, Association.SALICETUM_ALBAE);
		}
		// Łęg topolowy według d (Odstępstwo S4: bez warunku u < 0,35, bo u pochodzi z doliny dominującej
		// i skacze prostą linią przy jej zmianie, i bez preferencji garbów, która przeplatała go z łęgiem
		// wiązowym w pasie D_top–1,15 D_top; wały brzegowe przyjdą z rzeźbą dna w M5).
		if (d <= dPoplar && softwood) {
			return HabitatClassifier.Result.of(HabitatBiome.WILLOW_POPLAR_FOREST, zone, Association.POPULETUM_ALBAE);
		}
		return restOfFloor(c, d, channelWidth, zone);
	}

	/** Reszta dna dużej doliny: zastoiska (ols, torfowisko niskie) albo łęg wiązowo-jesionowy. */
	private static int restOfFloor(HabitatClassifier.Column c, double d, double channelWidth, Zone zone) {
		ColumnSample.Waters w = c.w;
		double k = c.k;
		double fromBackswamp = Math.max(Calibration.A_BACKSWAMP_D_K * k, Calibration.A_BACKSWAMP_D_W * channelWidth);
		if (c.uJittered() > Calibration.A_BACKSWAMP_U && c.heightAboveChannel() < Calibration.A_BACKSWAMP_H && d > fromBackswamp
				&& (1 - Calibration.A_BACKSWAMP_U) * w.floorHalfWidth() >= 2 * Calibration.MIN_ALDER_CARR
				&& c.patchQ(10, Calibration.BACKSWAMP_WAVELENGTH / k) < Calibration.BACKSWAMP_SHARE) {
			boolean peat = w.floorHalfWidth() > Calibration.A_PEAT_HALF_WIDTH_K * k && w.channelGradient() < Calibration.A_PEAT_GRADIENT
					&& c.patchQ(5, Calibration.BACKSWAMP_WAVELENGTH / k) < Calibration.A_PEAT_SHARE;
			Zone s = zone == Zone.HERB_FRINGE ? Zone.NONE : zone;
			return HabitatClassifier.Result.of(peat ? HabitatBiome.FEN : HabitatBiome.ALDER_CARR, s, Association.BACKSWAMP);
		}
		return HabitatClassifier.Result.of(HabitatBiome.ELM_ASH_FOREST, zone, Association.TYPICAL);
	}

	/** Klasa B (§4.2): ziołorośla brzegu, wierzby, łęg jesionowo-olszowy, ols w szerokich dnach, źródliska. */
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
			return HabitatClassifier.Result.of(HabitatBiome.ASH_ALDER_FOREST, zone == Zone.NONE ? Zone.SPRING_AREA : zone,
					Association.SPRING_FED);
		}
		if (!c.onValleyFloor()) {
			// Wąskie dno (albo jego brak): łęg przy brzegu w pasie co najmniej 6 bloków (E11), nisko nad ciekiem.
			if (d <= Calibration.MIN_ASH_ALDER && c.H < Calibration.H_ASH_ALDER_RIPARIAN && !Double.isNaN(c.w.channelLevel())
					&& c.H - c.w.channelLevel() <= Calibration.SEEP_HL) {
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
		// Ols tylko w dnie z miejscem na płaty szersze niż 10 bloków za łęgiem (E11): pas co najmniej 2 × 10.
		if (w.floorHalfWidth() > Calibration.B_ALDER_CARR_HALF_WIDTH_K * k && w.floorHalfWidth() - fromAlderCarr >= 2 * Calibration.MIN_ALDER_CARR && d > fromAlderCarr
				&& c.H < Calibration.H_ALDER_CARR
				&& (c.substrate == Substrate.PEAT || (c.heightAboveChannel() < Calibration.B_ALDER_CARR_H
						|| c.uJittered() > Calibration.B_ALDER_CARR_U) && c.patchQ(6, Calibration.ALDER_CARR_PATCH_WAVELENGTH / k) < Calibration.B_ALDER_CARR_SHARE)) {
			Zone s = zone == Zone.NONE && d < fromAlderCarr + Calibration.B_WILLOW_CARR_K * k ? Zone.WILLOW_CARR : zone;
			return HabitatClassifier.Result.of(HabitatBiome.ALDER_CARR, s, Association.TYPICAL);
		}
		// Mały ciek w szerokim dnie dużej doliny: dalej reszta jej dna.
		if (w.floorHalfWidth() > Calibration.WIDE_FLOOR_K * k && c.wLowland >= Calibration.CLASS_LOWLAND_WEIGHT
				&& w.streamOrder() >= Calibration.WIDE_FLOOR_ORDER) {
			return restOfFloor(c, d, channelWidth, zone);
		}
		// Skraj dna: siedlisko strefowe (glina: grąd niski, LMw; piasek: bór wilgotny, dalej świeży).
		return HabitatClassifier.Result.zone(zone);
	}

	/** Klasa C (§4.3): kamieniec, wiklina górska, olszyna górska, ziołorośla nadpotokowe, młaki. */
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
		double halfWidth = w.floorHalfWidth();
		if (h > Calibration.C_GRAY_ALDER_H || halfWidth < Calibration.C_NARROW_FLOOR_K * k) {
			// Wysoko i w wąskich dolinach V: las strefowy do brzegu, przy wodzie ziołorośla.
			if (zone == Zone.NONE && d <= width(Calibration.C_HERBS_MIN, Calibration.C_HERBS_W * channelWidth, f)) {
				zone = Zone.MONTANE_TALL_HERBS;
			}
			return HabitatClassifier.Result.zone(zone);
		}
		double grayAlderBand = Math.min(Calibration.C_GRAY_ALDER_MAX_K * k, Math.max(Calibration.C_GRAY_ALDER_MIN_K * k,
				Calibration.C_GRAY_ALDER_W * channelWidth)) * f;
		double hMax = c.aspectN() ? Calibration.C_GRAY_ALDER_H_N : Calibration.C_GRAY_ALDER_H;
		if ((d <= grayAlderBand || halfWidth < Calibration.C_WHOLE_FLOOR_K * k) && h <= hMax) {
			if (w.streamOrder() == 1 && h < Calibration.C_CARICI_H && c.P >= Calibration.C_CARICI_P
					&& halfWidth < Calibration.C_WHOLE_FLOOR_K * k && c.dgw() <= Calibration.SEEP_DGW) {
				return HabitatClassifier.Result.of(HabitatBiome.ASH_ALDER_FOREST, zone, Association.CARICI_REMOTAE_FRAXINETUM);
			}
			return HabitatClassifier.Result.of(alder ? HabitatBiome.GRAY_ALDER_FOREST : HabitatBiome.ASH_ALDER_FOREST, zone, Association.TYPICAL);
		}
		return HabitatClassifier.Result.zone(zone);
	}

	/** Poza dnem: wysięki u podnóża zbocza (DGW ≤ 0,5) to łęg jesionowo-olszowy, dalej strefowe. */
	private static int onSlope(HabitatClassifier.Column c, Zone zone) {
		if (c.isSeep()) {
			return HabitatClassifier.Result.of(HabitatBiome.ASH_ALDER_FOREST, zone, Association.TYPICAL);
		}
		return HabitatClassifier.Result.zone(zone);
	}

	// ------------------------------------------------------------------ wody stojące (§4.4)

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
				// Torf minerotroficzny: duże oczko na glinie to torfowisko niskie, inne ols.
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
			// Jezioro lobeliowe: bór dochodzi do wody z wąskim pasem olszy lub brzozy.
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
