package pl.polishforests.worldgen.habitat;

/**
 * Progi i wartości startowe klasyfikatora siedlisk (docs/03-m2-biomy.md §2–§5, raport ekologii).
 * Wszystkie progi klasyfikacji są tutaj, z wyjątkiem wysokości pięter górskich: te ma tylko
 * {@link AltitudinalBelts} (jedno źródło progu 1150 m).
 *
 * <p>Jednostki: wysokości i głębokości w metrach modelu; odległości oznaczone „·k” mnoży się przez
 * {@code local} skali (REAL 1, GAMEPLAY 0,5); minima stref są w blokach (E11) i nie skalują się;
 * nachylenia w stopniach (w GAMEPLAY porównywane z nachyleniem w blokach, §3.2, uwaga dla S4).
 */
public final class Calibration {
	private Calibration() {
	}

	// ------------------------------------------------------------------ ogólne

	/** Najwęższy pas strefy, który staje się biomem (3 kwarty, Z4). */
	public static final double BIOME_BAND = 12.0;
	/** Fala szumu drgań granic stref (m·k), środek przedziału 30–60 m. */
	public static final double BORDER_WAVELENGTH = 45.0;
	/** Drganie szerokości stref: ±20%. */
	public static final double WIDTH_JITTER = 0.20;
	/** Drganie położenia w dnie: ±0,05 u. */
	public static final double U_JITTER = 0.05;
	/** Fala szumu płatów (nymfeidy, szuwar w płatach, luki w łęgu, zastoiska) w m·k. */
	public static final double PATCH_WAVELENGTH = 35.0;
	/** Fala szumu wariantów w obrębie biomu (buczyna niżowa, prześwity wrzosowisk) w m·k. */
	public static final double VARIANT_WAVELENGTH = 1_500.0;

	// ------------------------------------------------------------------ DGW (§3.3)

	/** Typowa głębokość zwierciadła pod wygładzonym terenem: SANDR, POBRZEŻE, RÓWNINA, WYSOCZYZNA, POGÓRZE, BESKIDY. */
	public static final double G_OUTWASH_PLAIN = 2.5;
	public static final double G_COASTLAND = 1.5;
	public static final double G_OLD_GLACIAL_PLAIN = 3.0;
	public static final double G_MORAINE_PLATEAU = 4.0;
	public static final double G_FOOTHILLS = 5.0;
	public static final double G_BESKIDS = 8.0;
	/** Spadek zwierciadła od wody: piasek, glina, flisz. */
	public static final double I_SAND = 0.003;
	public static final double I_TILL = 0.01;
	public static final double I_FLYSCH = 0.04;
	/** Lustro wody podnosi zwierciadło o tyle (m). */
	public static final double WATER_LEVEL_OFFSET = 0.3;
	/**
	 * Wpływ cieków i wód stojących na zwierciadło włącza się z wcięciem terenu w dolinę lub nieckę
	 * (rawSurface − H) od pierwszego do drugiego progu (m); w dnie zawsze. Bez tego DGW skakało na granicy
	 * zasięgu zapytania sieci rzecznej (d = +∞ dalej), gdzie teren nie jest już wcięty.
	 */
	public static final double INCISION_FROM = 0.5;
	public static final double INCISION_TO = 3.0;
	/** Kara dla zwierciadła od wody poza doliną (m): wyłącza człon L_w + i·r, zachowując ciągłość. */
	public static final double OUTSIDE_VALLEY_PENALTY = 50.0;
	/** Górna granica DGW (m). */
	public static final double DGW_MAX = 12.0;
	/** Woda zawieszona na glinie: wklęsłość terenu (m) i odpowiadające DGW. */
	public static final double PERCHED_1 = -1.5;
	public static final double PERCHED_1_DGW = 1.0;
	public static final double PERCHED_2 = -3.0;
	public static final double PERCHED_2_DGW = 0.3;
	/** Brzeg na mule jeziornym: DGW najwyżej tyle. */
	public static final double LAKE_MUD_DGW = 0.2;

	// ------------------------------------------------------------------ wilgotność (§3.3)

	public static final double DGW_DRY = 4.0;
	public static final double DGW_FRESH = 2.0;
	public static final double DGW_MOIST = 0.8;
	public static final double DGW_BOGGY = 0.5;
	/** Sucha: wydma co najmniej tyle (m) albo wypukłość powyżej {@link #DRY_CONVEXITY}. */
	public static final double DRY_DUNE = 4.0;
	public static final double DRY_CONVEXITY = 2.0;

	// ------------------------------------------------------------------ trofia (§3.3): udziały B, BM, LM, L w %

	static final double[] FERTILITY_OUTWASH_PLAIN = {65, 31, 4, 0};
	static final double[] FERTILITY_MORAINE_PLATEAU = {0, 10, 30, 60};
	static final double[] FERTILITY_OLD_GLACIAL_PLAIN = {15, 25, 30, 30};
	static final double[] FERTILITY_COASTLAND = {70, 20, 10, 0};
	static final double[] FERTILITY_FOOTHILLS = {0, 10, 30, 60};
	static final double[] FERTILITY_BESKIDS = {0, 15, 30, 55};
	/**
	 * Dno doliny według terenu: grunt najwyżej tyle metrów nad lustrem najbliższego koryta (dno modelu leży
	 * 1,2–2,2 m nad lustrem) i nie dalej od koryta niż półszerokość dna (co najmniej {@link #FLOOR_MIN_K}·k).
	 * Flaga {@code wDnie} modelu pochodzi z doliny dominującej i bywa ucięta prostą linią przy zmianie doliny.
	 */
	public static final double FLOOR_H = 2.3;
	public static final double FLOOR_MIN_K = 80;
	/**
	 * Grunt niżej niż tyle metrów nad lustrem najbliższego koryta leży przy innym korycie niż to, nad którego
	 * dnem jest (dopływ schodzący bystrzem do dna większej doliny): rozstrzyga wtedy flaga {@code wDnie},
	 * a h = H − lustro − 1 nie jest określone.
	 */
	public static final double OTHER_CHANNEL_H = 1.15;
	/** Dno doliny modelu leży co najmniej tyle metrów nad lustrem własnego koryta (lustro dla DGW). */
	public static final double FLOOR_ABOVE_WATER_LEVEL = 1.2;
	/** Udział piasków sandru i pobrzeża, powyżej którego mady w dnach dostają trofię regionu, a nie L. */
	public static final double ALLUVIUM_ON_SAND = 0.5;

	// ------------------------------------------------------------------ biomy strefowe (§2.1)

	public static final double H_DRY_PINE = 350;
	public static final double H_FRESH_PINE = 500;
	public static final double H_BOG_WOODLAND = 400;
	public static final double H_LOWLAND_BEECH = 350;
	public static final double H_ALDER_CARR = 500;
	public static final double H_ASH_ALDER_RIPARIAN = 600;
	public static final double H_WILLOW_RIPARIAN = 300;
	/** Jedlina wyżynna przy P ≥ 0,5 w 250–650 m (niżej bór i las mieszany). */
	public static final double H_FIR_FOREST_FROM = 250;
	public static final double H_FIR_FOREST_TO = 650;
	public static final double P_FIR_FOREST = 0.5;
	/** Buczyna niżowa: drenaż przy nachyleniu powyżej (°) lub wypukłości ≥ 0. */
	public static final double SLOPE_BEECH = 3.0;
	/** Udział buczyny niżowej w zasięgu buka rośnie z O od tego progu do pełnego. */
	public static final double O_BEECH_FROM = 0.40;
	public static final double O_BEECH_TO = 0.85;
	/** Najwyższy udział buczyny wśród siedlisk ją dopuszczających. */
	public static final double BEECH_MAX = 0.85;
	/** Tryb N: prześwity wrzosowisk na wydmach (część boru suchego, ≤ 5% sandru), płaty o fali 150 m·k. */
	public static final double HEATH_SHARE_NATURAL = 0.25;
	public static final double HEATH_OPENING_WAVELENGTH = 150.0;

	// ------------------------------------------------------------------ strefy nadwodne (§4)

	/** Klasy cieków: waga nizin, szerokość w skali 1:1 (m), spadek (‰). */
	public static final double CLASS_LOWLAND_WEIGHT = 0.5;
	public static final double CLASS_A_WR = 30;
	/** Rząd 3 (dolina dominująca) daje klasę A dopiero przy korycie co najmniej tak szerokim (m, 1:1). */
	public static final double CLASS_A_WR_ORDER3 = 15;
	/** Szerokie dno dużej doliny (m·k): za łęgiem małego cieku reszta dna to łęg wiązowo-jesionowy. */
	public static final double WIDE_FLOOR_K = 250;
	/** Szerokie dno dużej doliny: dolina dominująca co najmniej tego rzędu. */
	public static final int WIDE_FLOOR_ORDER = 2;
	public static final double CLASS_GRADIENT = 3.0;
	/** Minima stref w blokach (E11). */
	public static final double MIN_REEDBED = 2;
	public static final double MIN_WILLOW_SCRUB = 3;
	public static final double MIN_ASH_ALDER = 6;
	public static final double MIN_ALDER_CARR = 10;
	public static final double MIN_HERB_FRINGE = 2;
	public static final double MIN_TALL_HERBS = 2;
	// Klasa A
	public static final double A_WILLOW_SCRUB_K = 8, A_WILLOW_SCRUB_W = 0.3;
	public static final double A_CONVEX_WILLOW_SCRUB_K = 15, A_CONVEX_WILLOW_SCRUB_W = 1.0;
	public static final double A_POINT_BAR_W = 0.5;
	/** Łacha w płatach: szum płatów (fala 35 m·k) powyżej progu (ok. 60% brzegu wypukłego). */
	public static final double A_POINT_BAR_PATCH = -0.2;
	public static final double A_HERB_FRINGE_K = 4, A_HERB_FRINGE_W = 0.05;
	public static final double A_D_WHITE_WILLOW_W = 1.7, A_D_WHITE_WILLOW_MIN = 30, A_D_WHITE_WILLOW_MAX = 300;
	public static final double A_D_POPLAR_W = 3.3, A_D_POPLAR_MIN = 80, A_D_POPLAR_MAX = 500;
	public static final double A_BACKSWAMP_U = 0.6;
	/**
	 * Zastoiska w niższych częściach dna: h = H − lustro − 1 poniżej progu (m). Dno modelu leży płasko
	 * 1,2–2,2 m nad lustrem z szumem o fali 90 m·k, więc h 0,2–1,2 m opisuje wyższe i niższe części dna
	 * (zastępczo dla η, §4). Plan brał {@code wyp}, ale ma ono składowe krótkofalowe (falowanie sandru)
	 * i dawało w dnie paski co kilka metrów (Z9: biom tylko z wejść o fali ≥ 64 m).
	 */
	public static final double A_BACKSWAMP_H = 0.6;
	/** Zastoiska w płatach: kwantyl szumu o fali {@link #BACKSWAMP_WAVELENGTH} m (bez k) poniżej udziału (ok. 25% dna). */
	public static final double BACKSWAMP_SHARE = 0.5;
	public static final double BACKSWAMP_WAVELENGTH = 120;
	public static final double A_BACKSWAMP_D_K = 60, A_BACKSWAMP_D_W = 2.0;
	public static final double A_PEAT_HALF_WIDTH_K = 300, A_PEAT_GRADIENT = 0.5;
	/**
	 * Udział torfowiska niskiego w zastoiskach szerokich den (reszta to ols); płaty o fali
	 * {@link #BACKSWAMP_WAVELENGTH} m bez k, bo wybierają biom (Z9).
	 */
	public static final double A_PEAT_SHARE = 0.4;
	/** Luki w łęgu (strefa OKRAJEK): szum płatów poniżej progu. */
	public static final double A_GAPS = -0.5;
	// Klasa B
	public static final double B_HERBS_K = 1.5, B_HERBS_W = 0.5;
	public static final double B_WILLOWS_WR = 15, B_WILLOWS_K = 20;
	public static final double B_ASH_ALDER_MAX_K = 80, B_ASH_ALDER_MIN_K = 15, B_ASH_ALDER_W = 4;
	public static final double B_ASH_ALDER_OUTWASH_PLAIN_MIN_K = 5, B_ASH_ALDER_OUTWASH_PLAIN_MAX_K = 25;
	public static final double B_ALDER_CARR_HALF_WIDTH_K = 60, B_ALDER_CARR_D_K = 20, B_ALDER_CARR_D_W = 4;
	public static final double B_ALDER_CARR_H = 0.45, B_ALDER_CARR_U = 0.5;
	public static final double B_WILLOW_CARR_K = 15;
	public static final double B_TREE_ROW_FROM_K = 3, B_TREE_ROW_TO_K = 10;
	// Klasa C
	public static final double C_GRAVEL_BAR_WR = 6, C_GRAVEL_BAR_H_FROM = 300, C_GRAVEL_BAR_H_TO = 1_000;
	public static final double C_WILLOW_SCRUB_WR = 4, C_WILLOW_SCRUB_H = 900;
	public static final double C_GRAY_ALDER_MAX_K = 60, C_GRAY_ALDER_MIN_K = 10, C_GRAY_ALDER_W = 3;
	public static final double C_GRAY_ALDER_H = 1_000, C_GRAY_ALDER_H_N = 900;
	public static final double C_WHOLE_FLOOR_K = 30, C_NARROW_FLOOR_K = 10;
	public static final double C_HERBS_W = 0.5, C_HERBS_MIN = 1;
	public static final double C_CARICI_H = 700, C_CARICI_P = 0.4;
	public static final double C_SPRING_FEN_H_FROM = 400, C_SPRING_FEN_H_TO = 1_100, C_SPRING_FEN_DGW = 0.3;
	/**
	 * Źródliska: forma ZRODLO obejmuje całe dno odcinka źródłowego, więc źródlisko to pas od koryta
	 * o szerokości 0–{@link #SPRING_AREA_K}·k zmiennej z szumem (promień 10–40k), średnio
	 * {@link #SPRING_AREA_SHARE} pełnego pasa.
	 */
	public static final double SPRING_AREA_K = 40;
	public static final double SPRING_AREA_SHARE = 0.3;
	/**
	 * Ols w szerokim dnie małej rzeki: płaty na tej części warunku (kwantyl szumu płatów o fali
	 * {@link #ALDER_CARR_PATCH_WAVELENGTH} m bez mnożnika k, żeby płaty miały co najmniej ok. 10 bloków także w GAMEPLAY).
	 */
	public static final double B_ALDER_CARR_SHARE = 0.45;
	public static final double ALDER_CARR_PATCH_WAVELENGTH = 120;
	/** Wysięk u podnóża zbocza doliny (łęg jesionowo-olszowy): DGW najwyżej tyle. */
	public static final double SEEP_DGW = 0.5;
	/**
	 * Wysięk tylko nisko nad ciekiem: najwyżej tyle metrów nad lustrem najbliższego koryta (dno leży 1,2–2,3 m
	 * nad nim), w terenie wciętym w dolinę (rawSurface − H ≥ {@link #INCISION_FROM}).
	 */
	public static final double SEEP_HL = 5;
	/**
	 * Źródliska w płatach o fali tyle metrów bez mnożnika k (płaty wybierają biom, Z9; przy fali 35 m·k
	 * w GAMEPLAY powstawały wysepki i strzępy łęgu węższe niż 6 bloków).
	 */
	public static final double SPRING_AREA_WAVELENGTH = 120;
	// Jeziora, oczka, starorzecza (§4.4)
	public static final double LAKE_DEPTH = 5, LAKE_DEPTH_OXBOW = 3;
	public static final double LAKE_SUBMERGED = 1.5;
	public static final double LAKE_FLOATING_LEAVED_FROM = 0.8, LAKE_FLOATING_LEAVED_TO = 3;
	/** Nymfeidy: udział lustra w starorzeczach i w zatokach dużych jezior (kwantyl szumu płatów). */
	public static final double LAKE_FLOATING_LEAVED_OXBOW = 0.75, LAKE_FLOATING_LEAVED_LAKE = 0.3;
	public static final double LAKE_REEDBED_Z = 1.5;
	/** Szuwar jako biom w wodzie: zbiornik o promieniu (półszerokości) co najmniej tyle (m·k). */
	public static final double LAKE_REEDBED_BIOME_RADIUS_K = 50;
	public static final double LAKE_SHORE_REEDBED_K = 10, LAKE_SHORE_REEDBED_H = 0.3;
	public static final double LAKE_WILLOW_CARR_K = 30, LAKE_WILLOW_CARR_H = 0.8;
	public static final double LAKE_ALDER_CARR_K = 150, LAKE_ALDER_CARR_KETTLE_K = 40, LAKE_ALDER_CARR_H = 1.0, LAKE_ALDER_CARR_SLOPE = 3.0;
	public static final double LAKE_OXBOW_REEDBED_K = 15, LAKE_OXBOW_WILLOW_CARR_K = 35, LAKE_OXBOW_ALDER_CARR_U = 0.5;
	/**
	 * Pierścień starorzecza tylko wokół starorzeczy o półszerokości co najmniej tyle (m): model podaje pierścień
	 * także przy wąskich ciekach, gdzie woda starorzecza nie powstaje (brzeg koryta ją wypiera), co dawało
	 * rząd jednakowych kresek łozowiska wzdłuż potoku.
	 */
	public static final double LAKE_OXBOW_MIN = 8;
	/** Jeziora na sandrze: skrót jeziora poniżej progu daje dystroficzne, poniżej drugiego oligotroficzne. */
	public static final double LAKE_DYSTROPHIC = 0.3, LAKE_OLIGOTROPHIC = 0.55;
	public static final double LAKE_FLOATING_MAT_K = 20;
	public static final double LAKE_SHORE_ALDER_K = 5;
	/** Szuwar jeziora lobeliowego w płatach na 20% brzegu. */
	public static final double LAKE_LOBELIA_REEDBED = 0.8;
	public static final double LAKE_DYSTROPHIC_PEAT_K = 15, LAKE_DYSTROPHIC_BOG_WOODLAND = 45;
	/** Oczko torfowe: promień granicy torfowiska wysokiego i boru bagiennego (m·k). */
	public static final double KETTLE_BOG_RADIUS_K = 75;
	/** Pierścień boru bagiennego wokół torfu ombro (Odstępstwo S2: pas oczka 45 m). */
	public static final double KETTLE_RING = 45;
	// Zalew (§4.4)
	public static final double LAGOON_Z = 1.5;
	public static final double LAGOON_PEAT_H = 0.4, LAGOON_ALDER_CARR_H = 1.0;

	// ------------------------------------------------------------------ wybrzeże (§5.2, długości · k)

	public static final double BEACH_B = 60, DUNES_D = 220;
	public static final double STRANDLINE_B = 0.35;
	public static final double EMBRYO_DUNE_K = 20;
	public static final double GRAY_DUNE_K = 170;
	public static final double WINDSWEPT_PINE_K = 420;
	public static final double COASTAL_PINE_K = 2_000, COASTAL_PINE_H = 40;
	/** Drganie granicy boru bażynowego: ±15% (szum wariantów). */
	public static final double COASTAL_PINE_JITTER = 0.15;
	/**
	 * Brzeg wydmowy: pole {@code niskiBrzeg} (1 − smoothstep(6, 20, hl) z kształtu wybrzeża) co najmniej tyle,
	 * czyli teren przy morzu niższy niż ok. 15 m. Tam wydmy, mierzeja i zalew; wyżej klif. Garb wydmy modelu
	 * (6–20 m · low) przy low &lt; 0,5 chowa się pod terenem pasa (hl), więc brzeg niski poznajemy po hl,
	 * a nie po kształcie wydmy (docs/03-m2-biomy.md, Odstępstwo S4, poprawka wybrzeża).
	 */
	public static final double LOW_SHORE = 0.25;
	public static final double CLIFF_H = 8, CLIFF_TOP_K = 20, CLIFF_WINDSWEPT_FOREST_K = 150;
	/** Plaża kamienista pod klifem: teren przed wcięciem wyższy niż tyle (m). */
	public static final double SHINGLE_BEACH_RAW = 8;

	// ------------------------------------------------------------------ tryb D (§2.2, kolumna D): biom nieleśny

	/** Kwantyl wariantu poniżej progu: łęg → łąka wilgotna (dalej pole), ols → łąka wilgotna (dalej torfowisko). */
	public static final double D_RIPARIAN_MEADOW = 0.8;
	public static final double D_ALDER_CARR_MEADOW = 0.7;
	/** Bory świeże i wilgotne: pole (świeże) lub łąka wilgotna (wilgotne) poniżej progu, dalej wrzosowisko lub pole. */
	public static final double D_PINE_ARABLE = 0.6;
	/** Pozostałe siedliska na płaskim (nachylenie poniżej {@link #D_ARABLE_SLOPE}°): pole poniżej progu, dalej łąka świeża. */
	public static final double D_ARABLE = 0.85;
	public static final double D_ARABLE_SLOPE = 5;

	// ------------------------------------------------------------------ zasięgi (§9)

	public static final double BEECH_O = 0.40, BEECH_P = 0.40;
	public static final double FIR_P = 0.50, FIR_H = 1_250;
	public static final double SPRUCE_O = 0.30, SPRUCE_P = 0.50;
	public static final double HORNBEAM_H = 600, HORNBEAM_H_S = 700;
	public static final double GRAY_ALDER_P = 0.50, GRAY_ALDER_O = 0.30;
	public static final double SESSILE_OAK_O = 0.35, SESSILE_OAK_P = 0.40, SESSILE_OAK_H = 600;
	public static final double LARCH_P_FROM = 0.50, LARCH_P_TO = 0.80, LARCH_H_FROM = 250, LARCH_H_TO = 650;
	public static final double IVY_O = 0.45, IVY_P = 0.50;
	public static final double YEW_O = 0.6, YEW_P = 0.6;

	// ------------------------------------------------------------------ nachylenie w skali rozgrywki

	/**
	 * Pochodna odwzorowania pionowego skali rozgrywki (bloki na metr) przy wysokości {@code metry}:
	 * bloki = 1,89 · m^0,742 ({@code VerticalScale.GAMEPLAY}; zgodność pilnuje {@code KlasyfikatorTest}).
	 * Nachylenie w blokach to tan(nach) · ten czynnik (§3.2, uwaga dla S4).
	 */
	public static double gameplayBlocksPerMeter(double meters) {
		double m = Math.max(1.0, meters);
		return 1.89 * 0.742 * Math.pow(m, 0.742 - 1.0);
	}
}
