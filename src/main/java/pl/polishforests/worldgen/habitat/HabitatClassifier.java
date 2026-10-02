package pl.polishforests.worldgen.habitat;

import pl.polishforests.worldgen.landscape.ColumnSample;
import pl.polishforests.worldgen.landscape.Landform;
import pl.polishforests.worldgen.landscape.LandscapeModel;
import pl.polishforests.worldgen.landscape.LandscapeScale;
import pl.polishforests.worldgen.landscape.LandscapeType;
import pl.polishforests.worldgen.landscape.Noise;
import pl.polishforests.worldgen.landscape.Substrate;

/**
 * Klasyfikator siedlisk (Z1, docs/03-m2-biomy.md §3.4): czysta funkcja próbki kolumny i jej położenia,
 * zwracająca kod {@link Habitat}. Woła ją źródło biomów, {@code fill()}, dyspozytory roślinności,
 * komendy, podgląd PNG i testy, więc biom, gleba i roślinność nie mogą się rozjechać.
 *
 * <p>Kolejność oznacza priorytet: woda ({@link OpenWater}), wybrzeże ({@link Coast}), strefy nadwodne
 * ({@link WatersideZones}), piętra górskie ({@link AltitudinalBelts}), siedliska strefowe, a w trybie D maska
 * lasu ({@link ForestCover}). Strefę może ustawić wcześniejszy krok bez wyboru biomu (np. KLIF_SCIANA
 * w biomie wysoczyzny albo ZIOLOROSLA_GORSKIE w lesie strefowym); biom wybiera wtedy dalszy krok.
 *
 * <p>Położenie (x, z) służy tylko szumom drgań granic stref, płatów i wariantów, liczonym z ziarna
 * świata przez {@code habitat.*}, więc wynik zależy wyłącznie od ziarna, skali, trybu, próbki i położenia.
 * Instancja jest niezmienna i bezpieczna wątkowo.
 */
public final class HabitatClassifier {
	/** Tryb roślinności: N „roślinność naturalna” (domyślny do M8, decyzja M2-B), D „dzisiejsza Polska”. */
	public enum Mode {
		NATURAL, PRESENT_DAY
	}

	final LandscapeScale scale;
	final Mode mode;
	/** {@code local} skali: mnożnik odległości „·k”. */
	final double k;
	/** Mnożnik szerokości koryt skali: W / chan to szerokość w skali 1:1. */
	final double chan;
	final boolean gameplay;
	/** Drgania granic stref (fala 30–60 m·k). */
	final Noise borders;
	/** Płaty: nymfeidy, szuwar w płatach, luki w łęgu, zastoiska. */
	final Noise patches;
	/** Przejścia pięter górskich (fala 150 m·k). */
	final Noise belts;
	/** Warianty w obrębie siedlisk (buczyna niżowa, prześwity wrzosowisk), fala 1,5 km·k. */
	final Noise variants;
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
		this.forestCover = new ForestCover(root.derive("habitat.lesistosc"), k);
	}

	public Mode mode() {
		return mode;
	}

	public LandscapeScale scale() {
		return scale;
	}

	/** Kod siedliska kolumny w punkcie (x, z) z jej próbką {@code s}. */
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

	/** Siedlisko kolumny jako rekord (komendy, podgląd, testy). */
	public Habitat habitat(ColumnSample s, double x, double z) {
		return Habitat.of(classify(s, x, z));
	}

	/** Głębokość wody gruntowej w kolumnie (m), jak w klasyfikacji (§3.3). */
	public double dgw(ColumnSample s, double x, double z) {
		return new Column(this, s, x, z).dgw();
	}

	/** Trofia kolumny, jak w klasyfikacji (§3.3). */
	public Fertility fertility(ColumnSample s, double x, double z) {
		return new Column(this, s, x, z).fertility();
	}

	// ------------------------------------------------------------------ siedliska strefowe (§2.1, krok 5)

	/** Biom strefowy z trofii, wilgotności i form (§2.1) z regułami zasięgu (§9). */
	static int zonal(Column c) {
		Fertility t = c.fertility();
		Moisture w = c.moisture();
		double h = c.H;
		HabitatBiome b;
		Association z = Association.TYPICAL;
		if (w == Moisture.BOGGY) {
			// Bór bagienny do 400 m, ols do 500 m; wyżej siedliska wilgotne.
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
				// Prześwity na wydmach (tryb N, ≤ 5% sandru).
				b = HabitatBiome.HEATH;
			}
		} else if (t == Fertility.OLIGO_MESOTROPHIC) {
			if (w == Moisture.MOIST) {
				b = HabitatBiome.MOIST_PINE_FOREST;
			} else {
				b = firForest(c, t) ? HabitatBiome.UPLAND_FIR_FOREST : HabitatBiome.MIXED_PINE_FOREST;
			}
		} else {
			// LM i L: las mieszany albo grąd, buczyna niżowa w zasięgu buka, jedlina przy P ≥ 0,5.
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
	 * Jedlina wyżynna (§2.1 nr 14): P ≥ 0,5, jodła w zasięgu, trofia BM, LM lub L (L tylko na stokach N),
	 * 250–650 m, poza dnami dolin.
	 */
	static boolean firForest(Column c, Fertility t) {
		if (c.P < Calibration.P_FIR_FOREST || c.H < Calibration.H_FIR_FOREST_FROM || c.H > Calibration.H_FIR_FOREST_TO
				|| !SpeciesRanges.fir(c.P, c.H, null) || t == Fertility.OLIGOTROPHIC || c.onValleyFloor()) {
			return false;
		}
		return t != Fertility.EUTROPHIC || c.aspectN();
	}

	/** Buczyna niżowa: buk w zasięgu, świeże i zdrenowane, H &lt; 350 m; udział rośnie z O. */
	static boolean beechForest(Column c, Moisture w) {
		if (w != Moisture.FRESH && w != Moisture.DRY || c.H >= Calibration.H_LOWLAND_BEECH
				|| !SpeciesRanges.beech(c.O, c.P)) {
			return false;
		}
		if (!(c.concavity() >= 0 || c.slope > Calibration.SLOPE_BEECH)) {
			return false;
		}
		double share = Calibration.BEECH_MAX
				* Noise.smoothstep(Calibration.O_BEECH_FROM, Calibration.O_BEECH_TO, Math.max(c.O, c.P));
		return c.variant(1) < share;
	}

	// ------------------------------------------------------------------ wynik częściowy kroków

	/**
	 * Wynik częściowy kroku klasyfikacji w jednym {@code int}: biom + 1 (0 = brak, czyli „dalej”), strefa
	 * i zespół. Bez obiektów, bo klasyfikacja idzie dla każdej kolumny.
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
		 * Łączy wynik wcześniejszych kroków z wynikiem kolejnego: strefa i zespół wcześniejszego kroku
		 * mają pierwszeństwo, biom bierze się z kolejnego.
		 */
		static int further(int earlier, int next) {
			Zone s = zone(earlier) != Zone.NONE ? zone(earlier) : zone(next);
			Association z = association(earlier) != Association.TYPICAL ? association(earlier) : association(next);
			return of(biome(next), s, z);
		}
	}

	// ------------------------------------------------------------------ wielkości pochodne kolumny

	/**
	 * Wielkości pochodne jednej kolumny (§3.3), liczone raz i leniwie. Obiekt lokalny dla jednego wywołania.
	 */
	static final class Column {
		final HabitatClassifier classifier;
		final ColumnSample s;
		final ColumnSample.Terrain t;
		final ColumnSample.Waters w;
		final double x;
		final double z;
		/** Wysokość gruntu (m n.p.m.). */
		final double H;
		final double k;
		/** Wagi typów z pasem nadmorskim (suma 1). */
		final double wOutwashPlain;
		final double wMorainePlateau;
		final double wOldGlacialPlain;
		final double wFoothills;
		final double wBeskids;
		final double wCoastland;
		/** Waga nizin i waga gór (POGÓRZE + BESKIDY). */
		final double wLowland;
		final double wMountains;
		final double O;
		final double P;
		/** Nachylenie w stopniach do porównań z progami: w GAMEPLAY przeliczone na bloki (§3.2). */
		final double slope;
		/** Podłoże dla siedlisk ({@link #substrate()}). */
		final Substrate substrate;
		private Fertility fertility;
		private double dgw = Double.NaN;
		private Moisture moisture;
		/** Dno doliny według terenu (0 nie, 1 tak, −1 nie liczone). */
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
			this.H = s.surface();
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
		 * Podłoże dla siedlisk: utwór z próbki, z wyjątkiem pasa nadmorskiego na niskim brzegu. Model daje
		 * glinę każdej kolumnie tego pasa wyższej niż 8 m, także wysokiej wydmie przedniej i wydmom za nią,
		 * więc na brzegu wydmowym ({@code niskiBrzeg} ≥ {@link Calibration#LOW_SHORE}) taka glina to piasek
		 * plaży i wydmy białej albo piasek wydmy szarej, jak przy niższej wydmie (granica z {@code golyPiasek}).
		 */
		private static Substrate substrate(ColumnSample s, ColumnSample.Terrain t) {
			Substrate sub = s.substrate();
			if (sub == Substrate.GLACIAL_TILL && s.type() == LandscapeType.COASTLAND && t.lowShore() >= Calibration.LOW_SHORE
					&& !Double.isNaN(t.bareSandWidth())) {
				return t.coastD() < t.bareSandWidth() ? Substrate.BEACH_SAND : Substrate.SAND;
			}
			return sub;
		}

		/** Brzeg morski z wydmami (niski), a nie z klifem. */
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

		/** Mnożnik szerokości stref 1 ± 0,2 (szum o fali 45 m·k). */
		double jitter() {
			if (Double.isNaN(jitter)) {
				jitter = 1 + Calibration.WIDTH_JITTER * classifier.borders.at(x, z, Calibration.BORDER_WAVELENGTH * k);
			}
			return jitter;
		}

		/**
		 * Dno doliny według terenu: grunt najwyżej {@link Calibration#FLOOR_H} nad lustrem najbliższego koryta, a do
		 * tego flaga {@code wDnie} modelu albo odległość od koryta w półszerokości dna. Flaga modelu pochodzi
		 * z doliny dominującej, więc bywa ucięta prostą linią i obejmuje grunt wysoko nad innym, bliższym
		 * korytem. Gdy grunt leży mniej niż 1,2 m nad lustrem najbliższego koryta (dopływ schodzący bystrzem
		 * do dna większej doliny), rozstrzyga sama flaga.
		 */
		boolean onValleyFloor() {
			if (onValleyFloor < 0) {
				boolean d = w.inFloor();
				if (w.streamOrder() > 0 && !s.hasWater() && !Double.isNaN(w.channelLevel())) {
					double hl = H - w.channelLevel();
					if (hl >= Calibration.OTHER_CHANNEL_H) {
						double range = Math.max(Calibration.FLOOR_MIN_K * k, Double.isNaN(w.floorHalfWidth()) ? 0 : w.floorHalfWidth());
						d = hl <= Calibration.FLOOR_H && (d || w.channelDist() <= range);
					}
				}
				onValleyFloor = d ? 1 : 0;
			}
			return onValleyFloor == 1;
		}

		/** Położenie w dnie 0–1: z modelu, a w dnie według terenu poza flagą {@code wDnie} z d / półszerokość. */
		double u() {
			if (w.inFloor() && !Double.isNaN(w.u())) {
				return w.u();
			}
			if (!onValleyFloor()) {
				return Double.NaN;
			}
			double half = w.floorHalfWidth();
			return Double.isNaN(half) || half <= 0 ? 0 : Math.clamp(Math.max(0, w.channelDist()) / half, 0.0, 1.0);
		}

		/** Położenie w dnie z drganiem ±0,05. */
		double uJittered() {
			if (Double.isNaN(uJitter)) {
				uJitter = u() + Calibration.U_JITTER * classifier.borders.at(x + 7_777, z - 3_333, Calibration.BORDER_WAVELENGTH * k);
			}
			return uJitter;
		}

		/** Kwantyl szumu płatów o zadanej fali (m·k), w [0, 1]. */
		double patchQ(int layer, double wavelength) {
			return LandscapeModel.noiseQuantile(classifier.patches.at(x + 1_013.0 * layer, z - 517.0 * layer, wavelength * k));
		}

		/** Szum płatów w [−1, 1] (fala 35 m·k); {@code warstwa} rozdziela niezależne decyzje. */
		double patch(int layer) {
			return classifier.patches.at(x + 1_013.0 * layer, z - 517.0 * layer, Calibration.PATCH_WAVELENGTH * k);
		}

		/** Kwantyl płatów w [0, 1] (rozkład jednostajny). */
		double patchQ(int layer) {
			return LandscapeModel.noiseQuantile(patch(layer));
		}

		/** Kwantyl szumu wariantów w [0, 1] (fala 1,5 km·k). */
		double variant(int layer) {
			return LandscapeModel.noiseQuantile(
					classifier.variants.at(x + 2_029.0 * layer, z + 911.0 * layer, Calibration.VARIANT_WAVELENGTH * k));
		}

		/** cos(ekspozycja − 180°): 1 stok S, −1 stok N; 0 na płaskim i poniżej 5°. */
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
		 * Wypukłość terenu w skali ok. 100 m·k: teren przed wcięciem dolin minus teren wygładzony (m). Zastępuje
		 * {@code teren.wyp} w regułach biomów, bo {@code wyp} ma składowe krótkofalowe (falowanie sandru, Z9).
		 */
		double concavity() {
			return t.rawSurface() - t.sBar();
		}

		/** Zbocze doliny: w zasięgu cieku, poza dnem, teren wcięty poniżej terenu przed doliną. */
		boolean valleySlope() {
			return w.streamOrder() > 0 && !onValleyFloor() && !s.hasWater() && H < t.rawSurface() - 2;
		}

		/**
		 * Wysięk u podnóża zbocza doliny: siedlisko bagienne (DGW ≤ 0,5, w pasie 0,5–0,8 według szumu) przy
		 * żyznym podłożu (LM, L) poniżej 600 m, poza dnem, ale w dolinie i nisko nad ciekiem: teren wcięty co
		 * najmniej {@link Calibration#INCISION_FROM} poniżej terenu przed doliną i najwyżej {@link Calibration#SEEP_HL}
		 * nad lustrem najbliższego koryta. Bez warunku odległości: granicę wysięku wyznacza wtedy granica siedliska
		 * bagiennego albo poziomica, a nie linia równoległa do koryta, przy której zostawały strzępy łęgu węższe
		 * niż 6 bloków. Bez warunku zbocza (wcięcie ≥ 2 m), bo wtedy skraj dna dostawał wąski pas olsu strefowego.
		 */
		boolean isSeep() {
			if (w.streamOrder() <= 0 || onValleyFloor() || s.hasWater() || H >= Calibration.H_ASH_ALDER_RIPARIAN || Double.isNaN(w.channelLevel())
					|| t.rawSurface() - H < Calibration.INCISION_FROM || H - w.channelLevel() > Calibration.SEEP_HL) {
				return false;
			}
			Fertility fert = fertility();
			return (fert == Fertility.MESOTROPHIC || fert == Fertility.EUTROPHIC) && moisture() == Moisture.BOGGY;
		}

		/** Szerokość koryta w skali 1:1 (m). */
		double wr() {
			return w.channelWidth() / classifier.chan;
		}

		/**
		 * Wysokość nad lustrem najbliższego koryta minus 1 m (h, §3.3). NaN bez cieku i wtedy, gdy grunt leży
		 * niżej niż 1,2 m nad tym lustrem: dno modelu leży co najmniej 1,2 m nad lustrem własnego koryta, więc
		 * najbliższe koryto jest wtedy inne (dopływ schodzący bystrzem do dna większej doliny).
		 */
		double heightAboveChannel() {
			double hl = H - w.channelLevel();
			return hl < Calibration.OTHER_CHANNEL_H ? Double.NaN : hl - 1;
		}

		/**
		 * Źródlisko: forma ZRODLO, pas od koryta o szerokości 0–40 m·k zmiennej z szumem płatów (średnio
		 * {@link Calibration#SPRING_AREA_SHARE} pasa). Pas zaczyna się przy korycie, więc łęg źródliska łączy się
		 * z łęgiem dna i nie rozpada się na strzępy węższe niż 6 bloków. Szum wybiera biom, więc ma falę
		 * {@link Calibration#SPRING_AREA_WAVELENGTH} m bez mnożnika k (Z9: biom tylko z wejść o fali ≥ 64 m).
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
