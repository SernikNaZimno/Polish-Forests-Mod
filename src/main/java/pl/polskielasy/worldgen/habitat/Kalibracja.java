package pl.polskielasy.worldgen.habitat;

/**
 * Progi i wartości startowe klasyfikatora siedlisk (docs/03-m2-biomy.md §2–§5, raport ekologii).
 * Wszystkie progi klasyfikacji są tutaj, z wyjątkiem wysokości pięter górskich: te ma tylko
 * {@link Pietra} (jedno źródło progu 1150 m).
 *
 * <p>Jednostki: wysokości i głębokości w metrach modelu; odległości oznaczone „·k” mnoży się przez
 * {@code local} skali (REAL 1, GAMEPLAY 0,5); minima stref są w blokach (E11) i nie skalują się;
 * nachylenia w stopniach (w GAMEPLAY porównywane z nachyleniem w blokach, §3.2, uwaga dla S4).
 */
public final class Kalibracja {
	private Kalibracja() {
	}

	// ------------------------------------------------------------------ ogólne

	/** Najwęższy pas strefy, który staje się biomem (3 kwarty, Z4). */
	public static final double PAS_BIOMU = 12.0;
	/** Fala szumu drgań granic stref (m·k), środek przedziału 30–60 m. */
	public static final double FALA_GRANIC = 45.0;
	/** Drganie szerokości stref: ±20%. */
	public static final double DRGANIE_SZEROKOSCI = 0.20;
	/** Drganie położenia w dnie: ±0,05 u. */
	public static final double DRGANIE_U = 0.05;
	/** Fala szumu płatów (nymfeidy, szuwar w płatach, luki w łęgu, zastoiska) w m·k. */
	public static final double FALA_PLATOW = 35.0;
	/** Fala szumu wariantów w obrębie biomu (buczyna niżowa, prześwity wrzosowisk) w m·k. */
	public static final double FALA_WARIANTOW = 1_500.0;

	// ------------------------------------------------------------------ DGW (§3.3)

	/** Typowa głębokość zwierciadła pod wygładzonym terenem: SANDR, POBRZEŻE, RÓWNINA, WYSOCZYZNA, POGÓRZE, BESKIDY. */
	public static final double G_SANDR = 2.5;
	public static final double G_POBRZEZE = 1.5;
	public static final double G_ROWNINA = 3.0;
	public static final double G_WYSOCZYZNA = 4.0;
	public static final double G_POGORZE = 5.0;
	public static final double G_BESKIDY = 8.0;
	/** Spadek zwierciadła od wody: piasek, glina, flisz. */
	public static final double I_PIASEK = 0.003;
	public static final double I_GLINA = 0.01;
	public static final double I_FLISZ = 0.04;
	/** Lustro wody podnosi zwierciadło o tyle (m). */
	public static final double LUSTRO_PLUS = 0.3;
	/**
	 * Wpływ cieków i wód stojących na zwierciadło włącza się z wcięciem terenu w dolinę lub nieckę
	 * (rawSurface − H) od pierwszego do drugiego progu (m); w dnie zawsze. Bez tego DGW skakało na granicy
	 * zasięgu zapytania sieci rzecznej (d = +∞ dalej), gdzie teren nie jest już wcięty.
	 */
	public static final double WCIECIE_OD = 0.5;
	public static final double WCIECIE_DO = 3.0;
	/** Kara dla zwierciadła od wody poza doliną (m): wyłącza człon L_w + i·r, zachowując ciągłość. */
	public static final double KARA_POZA_DOLINA = 50.0;
	/** Górna granica DGW (m). */
	public static final double DGW_MAX = 12.0;
	/** Woda zawieszona na glinie: wklęsłość terenu (m) i odpowiadające DGW. */
	public static final double ZAWIESZONA_1 = -1.5;
	public static final double ZAWIESZONA_1_DGW = 1.0;
	public static final double ZAWIESZONA_2 = -3.0;
	public static final double ZAWIESZONA_2_DGW = 0.3;
	/** Brzeg na mule jeziornym: DGW najwyżej tyle. */
	public static final double MUL_DGW = 0.2;

	// ------------------------------------------------------------------ wilgotność (§3.3)

	public static final double DGW_SUCHA = 4.0;
	public static final double DGW_SWIEZA = 2.0;
	public static final double DGW_WILGOTNA = 0.8;
	public static final double DGW_BAGIENNA = 0.5;
	/** Sucha: wydma co najmniej tyle (m) albo wypukłość powyżej {@link #WYP_SUCHA}. */
	public static final double WYDMA_SUCHA = 4.0;
	public static final double WYP_SUCHA = 2.0;

	// ------------------------------------------------------------------ trofia (§3.3): udziały B, BM, LM, L w %

	static final double[] TROFIA_SANDR = {65, 31, 4, 0};
	static final double[] TROFIA_WYSOCZYZNA = {0, 10, 30, 60};
	static final double[] TROFIA_ROWNINA = {15, 25, 30, 30};
	static final double[] TROFIA_POBRZEZE = {70, 20, 10, 0};
	static final double[] TROFIA_POGORZE = {0, 10, 30, 60};
	static final double[] TROFIA_BESKIDY = {0, 15, 30, 55};
	/**
	 * Dno doliny według terenu: grunt najwyżej tyle metrów nad lustrem najbliższego koryta (dno modelu leży
	 * 1,2–2,2 m nad lustrem) i nie dalej od koryta niż półszerokość dna (co najmniej {@link #DNO_MIN_K}·k).
	 * Flaga {@code wDnie} modelu pochodzi z doliny dominującej i bywa ucięta prostą linią przy zmianie doliny.
	 */
	public static final double DNO_H = 2.3;
	public static final double DNO_MIN_K = 80;
	/**
	 * Grunt niżej niż tyle metrów nad lustrem najbliższego koryta leży przy innym korycie niż to, nad którego
	 * dnem jest (dopływ schodzący bystrzem do dna większej doliny): rozstrzyga wtedy flaga {@code wDnie},
	 * a h = H − lustro − 1 nie jest określone.
	 */
	public static final double INNE_KORYTO_H = 1.15;
	/** Dno doliny modelu leży co najmniej tyle metrów nad lustrem własnego koryta (lustro dla DGW). */
	public static final double DNO_NAD_LUSTREM = 1.2;
	/** Udział piasków sandru i pobrzeża, powyżej którego mady w dnach dostają trofię regionu, a nie L. */
	public static final double MADY_NA_PIASKU = 0.5;

	// ------------------------------------------------------------------ biomy strefowe (§2.1)

	public static final double H_BOR_SUCHY = 350;
	public static final double H_BOR_SWIEZY = 500;
	public static final double H_BOR_BAGIENNY = 400;
	public static final double H_BUCZYNA_NIZINNA = 350;
	public static final double H_OLS = 500;
	public static final double H_LEG_OLJ = 600;
	public static final double H_LEG_WIERZBOWY = 300;
	/** Jedlina wyżynna przy P ≥ 0,5 w 250–650 m (niżej bór i las mieszany). */
	public static final double H_JEDLINA_OD = 250;
	public static final double H_JEDLINA_DO = 650;
	public static final double P_JEDLINA = 0.5;
	/** Buczyna niżowa: drenaż przy nachyleniu powyżej (°) lub wypukłości ≥ 0. */
	public static final double NACH_BUCZYNA = 3.0;
	/** Udział buczyny niżowej w zasięgu buka rośnie z O od tego progu do pełnego. */
	public static final double O_BUCZYNA_OD = 0.40;
	public static final double O_BUCZYNA_DO = 0.85;
	/** Najwyższy udział buczyny wśród siedlisk ją dopuszczających. */
	public static final double BUCZYNA_MAX = 0.85;
	/** Tryb N: prześwity wrzosowisk na wydmach (część boru suchego, ≤ 5% sandru), płaty o fali 150 m·k. */
	public static final double WRZOSOWISKO_N = 0.25;
	public static final double FALA_PRZESWITOW = 150.0;

	// ------------------------------------------------------------------ strefy nadwodne (§4)

	/** Klasy cieków: waga nizin, szerokość w skali 1:1 (m), spadek (‰). */
	public static final double KLASA_WN = 0.5;
	public static final double KLASA_A_WR = 30;
	/** Rząd 3 (dolina dominująca) daje klasę A dopiero przy korycie co najmniej tak szerokim (m, 1:1). */
	public static final double KLASA_A_WR_RZAD3 = 15;
	/** Szerokie dno dużej doliny (m·k): za łęgiem małego cieku reszta dna to łęg wiązowo-jesionowy. */
	public static final double SZEROKIE_DNO_K = 250;
	/** Szerokie dno dużej doliny: dolina dominująca co najmniej tego rzędu. */
	public static final int SZEROKIE_DNO_RZAD = 2;
	public static final double KLASA_SPADEK = 3.0;
	/** Minima stref w blokach (E11). */
	public static final double MIN_SZUWAR = 2;
	public static final double MIN_WIKLINA = 3;
	public static final double MIN_OLJ = 6;
	public static final double MIN_OLS = 10;
	public static final double MIN_OKRAJEK = 2;
	public static final double MIN_ZIOLOROSLA = 2;
	// Klasa A
	public static final double A_WIKLINA_K = 8, A_WIKLINA_W = 0.3;
	public static final double A_WIKLINA_WYPUKLY_K = 15, A_WIKLINA_WYPUKLY_W = 1.0;
	public static final double A_LACHA_W = 0.5;
	/** Łacha w płatach: szum płatów (fala 35 m·k) powyżej progu (ok. 60% brzegu wypukłego). */
	public static final double A_LACHA_PLAT = -0.2;
	public static final double A_OKRAJEK_K = 4, A_OKRAJEK_W = 0.05;
	public static final double A_DWB_W = 1.7, A_DWB_MIN = 30, A_DWB_MAX = 300;
	public static final double A_DTOP_W = 3.3, A_DTOP_MIN = 80, A_DTOP_MAX = 500;
	public static final double A_ZASTOISKO_U = 0.6;
	/**
	 * Zastoiska w niższych częściach dna: h = H − lustro − 1 poniżej progu (m). Dno modelu leży płasko
	 * 1,2–2,2 m nad lustrem z szumem o fali 90 m·k, więc h 0,2–1,2 m opisuje wyższe i niższe części dna
	 * (zastępczo dla η, §4). Plan brał {@code wyp}, ale ma ono składowe krótkofalowe (falowanie sandru)
	 * i dawało w dnie paski co kilka metrów (Z9: biom tylko z wejść o fali ≥ 64 m).
	 */
	public static final double A_ZASTOISKO_H = 0.6;
	/** Zastoiska w płatach: kwantyl szumu o fali {@link #FALA_ZASTOISK} m (bez k) poniżej udziału (ok. 25% dna). */
	public static final double ZASTOISKA_UDZIAL = 0.5;
	public static final double FALA_ZASTOISK = 120;
	public static final double A_ZASTOISKO_D_K = 60, A_ZASTOISKO_D_W = 2.0;
	public static final double A_TORF_POLSZER_K = 300, A_TORF_SPADEK = 0.5;
	/**
	 * Udział torfowiska niskiego w zastoiskach szerokich den (reszta to ols); płaty o fali
	 * {@link #FALA_ZASTOISK} m bez k, bo wybierają biom (Z9).
	 */
	public static final double A_TORF_UDZIAL = 0.4;
	/** Luki w łęgu (strefa OKRAJEK): szum płatów poniżej progu. */
	public static final double A_LUKI = -0.5;
	// Klasa B
	public static final double B_ZIOL_K = 1.5, B_ZIOL_W = 0.5;
	public static final double B_WIERZBY_WR = 15, B_WIERZBY_K = 20;
	public static final double B_OLJ_MAX_K = 80, B_OLJ_MIN_K = 15, B_OLJ_W = 4;
	public static final double B_OLJ_SANDR_MIN_K = 5, B_OLJ_SANDR_MAX_K = 25;
	public static final double B_OLS_POLSZER_K = 60, B_OLS_D_K = 20, B_OLS_D_W = 4;
	public static final double B_OLS_H = 0.45, B_OLS_U = 0.5;
	public static final double B_LOZOWISKO_K = 15;
	public static final double B_SZPALER_OD_K = 3, B_SZPALER_DO_K = 10;
	// Klasa C
	public static final double C_KAMIENIEC_WR = 6, C_KAMIENIEC_H_OD = 300, C_KAMIENIEC_H_DO = 1_000;
	public static final double C_WIKLINA_WR = 4, C_WIKLINA_H = 900;
	public static final double C_OLSZYNA_MAX_K = 60, C_OLSZYNA_MIN_K = 10, C_OLSZYNA_W = 3;
	public static final double C_OLSZYNA_H = 1_000, C_OLSZYNA_H_N = 900;
	public static final double C_CALE_DNO_K = 30, C_WASKIE_DNO_K = 10;
	public static final double C_ZIOL_W = 0.5, C_ZIOL_MIN = 1;
	public static final double C_CARICI_H = 700, C_CARICI_P = 0.4;
	public static final double C_MLAKA_H_OD = 400, C_MLAKA_H_DO = 1_100, C_MLAKA_DGW = 0.3;
	/**
	 * Źródliska: forma ZRODLO obejmuje całe dno odcinka źródłowego, więc źródlisko to pas od koryta
	 * o szerokości 0–{@link #ZRODLISKO_K}·k zmiennej z szumem (promień 10–40k), średnio
	 * {@link #ZRODLISKO_UDZIAL} pełnego pasa.
	 */
	public static final double ZRODLISKO_K = 40;
	public static final double ZRODLISKO_UDZIAL = 0.3;
	/**
	 * Ols w szerokim dnie małej rzeki: płaty na tej części warunku (kwantyl szumu płatów o fali
	 * {@link #FALA_PLATOW_OLSU} m bez mnożnika k, żeby płaty miały co najmniej ok. 10 bloków także w GAMEPLAY).
	 */
	public static final double B_OLS_UDZIAL = 0.45;
	public static final double FALA_PLATOW_OLSU = 120;
	/** Wysięk u podnóża zbocza doliny (łęg jesionowo-olszowy): DGW najwyżej tyle. */
	public static final double WYSIEK_DGW = 0.5;
	/**
	 * Wysięk tylko nisko nad ciekiem: najwyżej tyle metrów nad lustrem najbliższego koryta (dno leży 1,2–2,3 m
	 * nad nim), w terenie wciętym w dolinę (rawSurface − H ≥ {@link #WCIECIE_OD}).
	 */
	public static final double WYSIEK_HL = 5;
	/**
	 * Źródliska w płatach o fali tyle metrów bez mnożnika k (płaty wybierają biom, Z9; przy fali 35 m·k
	 * w GAMEPLAY powstawały wysepki i strzępy łęgu węższe niż 6 bloków).
	 */
	public static final double FALA_ZRODLISK = 120;
	// Jeziora, oczka, starorzecza (§4.4)
	public static final double J_GLEBIA = 5, J_GLEBIA_STARORZECZE = 3;
	public static final double J_ELODEIDY = 1.5;
	public static final double J_NYMFEIDY_OD = 0.8, J_NYMFEIDY_DO = 3;
	/** Nymfeidy: udział lustra w starorzeczach i w zatokach dużych jezior (kwantyl szumu płatów). */
	public static final double J_NYMFEIDY_STARORZECZE = 0.75, J_NYMFEIDY_JEZIORO = 0.3;
	public static final double J_SZUWAR_Z = 1.5;
	/** Szuwar jako biom w wodzie: zbiornik o promieniu (półszerokości) co najmniej tyle (m·k). */
	public static final double J_SZUWAR_BIOM_PROMIEN_K = 50;
	public static final double J_SZUWAR_LAD_K = 10, J_SZUWAR_LAD_H = 0.3;
	public static final double J_LOZOWISKO_K = 30, J_LOZOWISKO_H = 0.8;
	public static final double J_OLS_K = 150, J_OLS_OCZKO_K = 40, J_OLS_H = 1.0, J_OLS_NACH = 3.0;
	public static final double J_STARORZECZE_SZUWAR_K = 15, J_STARORZECZE_LOZ_K = 35, J_STARORZECZE_OLS_U = 0.5;
	/**
	 * Pierścień starorzecza tylko wokół starorzeczy o półszerokości co najmniej tyle (m): model podaje pierścień
	 * także przy wąskich ciekach, gdzie woda starorzecza nie powstaje (brzeg koryta ją wypiera), co dawało
	 * rząd jednakowych kresek łozowiska wzdłuż potoku.
	 */
	public static final double J_STARORZECZE_MIN = 8;
	/** Jeziora na sandrze: skrót jeziora poniżej progu daje dystroficzne, poniżej drugiego oligotroficzne. */
	public static final double J_DYSTROF = 0.3, J_OLIGO = 0.55;
	public static final double J_PLO_K = 20;
	public static final double J_OLSZA_BRZEG_K = 5;
	/** Szuwar jeziora lobeliowego w płatach na 20% brzegu. */
	public static final double J_LOBELIOWE_SZUWAR = 0.8;
	public static final double J_DYSTROF_TORF_K = 15, J_DYSTROF_BB = 45;
	/** Oczko torfowe: promień granicy torfowiska wysokiego i boru bagiennego (m·k). */
	public static final double OCZKO_TORF_PROMIEN_K = 75;
	/** Pierścień boru bagiennego wokół torfu ombro (Odstępstwo S2: pas oczka 45 m). */
	public static final double OCZKO_PIERSCIEN = 45;
	// Zalew (§4.4)
	public static final double ZALEW_Z = 1.5;
	public static final double ZALEW_TORF_H = 0.4, ZALEW_OLS_H = 1.0;

	// ------------------------------------------------------------------ wybrzeże (§5.2, długości · k)

	public static final double PLAZA_B = 60, WYDMY_D = 220;
	public static final double KIDZINA_B = 0.35;
	public static final double WYDMA_INICJALNA_K = 20;
	public static final double WYDMA_SZARA_K = 170;
	public static final double BOR_WIATROWY_K = 420;
	public static final double BOR_BAZYNOWY_K = 2_000, BOR_BAZYNOWY_H = 40;
	/** Drganie granicy boru bażynowego: ±15% (szum wariantów). */
	public static final double BOR_BAZYNOWY_DRGANIE = 0.15;
	/**
	 * Brzeg wydmowy: pole {@code niskiBrzeg} (1 − smoothstep(6, 20, hl) z kształtu wybrzeża) co najmniej tyle,
	 * czyli teren przy morzu niższy niż ok. 15 m. Tam wydmy, mierzeja i zalew; wyżej klif. Garb wydmy modelu
	 * (6–20 m · low) przy low &lt; 0,5 chowa się pod terenem pasa (hl), więc brzeg niski poznajemy po hl,
	 * a nie po kształcie wydmy (docs/03-m2-biomy.md, Odstępstwo S4, poprawka wybrzeża).
	 */
	public static final double NISKI_BRZEG = 0.25;
	public static final double KLIF_H = 8, KLIF_KORONA_K = 20, KLIF_LAS_WIATROWY_K = 150;
	/** Plaża kamienista pod klifem: teren przed wcięciem wyższy niż tyle (m). */
	public static final double PLAZA_KAMIENISTA_RAW = 8;

	// ------------------------------------------------------------------ tryb D (§2.2, kolumna D): biom nieleśny

	/** Kwantyl wariantu poniżej progu: łęg → łąka wilgotna (dalej pole), ols → łąka wilgotna (dalej torfowisko). */
	public static final double D_LEG_LAKA = 0.8;
	public static final double D_OLS_LAKA = 0.7;
	/** Bory świeże i wilgotne: pole (świeże) lub łąka wilgotna (wilgotne) poniżej progu, dalej wrzosowisko lub pole. */
	public static final double D_BOR_POLE = 0.6;
	/** Pozostałe siedliska na płaskim (nachylenie poniżej {@link #D_POLE_NACH}°): pole poniżej progu, dalej łąka świeża. */
	public static final double D_POLE = 0.85;
	public static final double D_POLE_NACH = 5;

	// ------------------------------------------------------------------ zasięgi (§9)

	public static final double BUK_O = 0.40, BUK_P = 0.40;
	public static final double JODLA_P = 0.50, JODLA_H = 1_250;
	public static final double SWIERK_O = 0.30, SWIERK_P = 0.50;
	public static final double GRAB_H = 600, GRAB_H_S = 700;
	public static final double OLSZA_SZARA_P = 0.50, OLSZA_SZARA_O = 0.30;
	public static final double DAB_BEZSZYP_O = 0.35, DAB_BEZSZYP_P = 0.40, DAB_BEZSZYP_H = 600;
	public static final double MODRZEW_P_OD = 0.50, MODRZEW_P_DO = 0.80, MODRZEW_H_OD = 250, MODRZEW_H_DO = 650;
	public static final double BLUSZCZ_O = 0.45, BLUSZCZ_P = 0.50;
	public static final double CIS_O = 0.6, CIS_P = 0.6;

	// ------------------------------------------------------------------ nachylenie w skali rozgrywki

	/**
	 * Pochodna odwzorowania pionowego skali rozgrywki (bloki na metr) przy wysokości {@code metry}:
	 * bloki = 1,89 · m^0,742 ({@code VerticalScale.GAMEPLAY}; zgodność pilnuje {@code KlasyfikatorTest}).
	 * Nachylenie w blokach to tan(nach) · ten czynnik (§3.2, uwaga dla S4).
	 */
	public static double blokiNaMetrRozgrywki(double metry) {
		double m = Math.max(1.0, metry);
		return 1.89 * 0.742 * Math.pow(m, 0.742 - 1.0);
	}
}
