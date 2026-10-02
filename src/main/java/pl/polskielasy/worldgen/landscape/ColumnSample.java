package pl.polskielasy.worldgen.landscape;

import java.util.Set;

/**
 * Wynik próbkowania modelu krajobrazu w jednej kolumnie.
 *
 * <p>Sześć pierwszych pól to teren z M1 (złoty test liczy skrót tylko z nich). Rekordy {@link Teren},
 * {@link Wody} i {@link Region} niosą wielkości dla siedlisk (M2, docs/03-m2-biomy.md §3.1). Mają tylko
 * typy proste i enumy, bez tablic, więc {@code equals} porównuje je po wartościach. Wartość
 * {@link Double#NaN} znaczy „nie dotyczy” (np. {@code u} poza dnem doliny), a
 * {@link Double#POSITIVE_INFINITY} w odległościach znaczy „poza zasięgiem”.
 *
 * @param surface     wysokość powierzchni gruntu w metrach n.p.m. (także dna pod wodą)
 * @param waterLevel  poziom lustra wody w pełnych metrach n.p.m.; {@link Integer#MIN_VALUE}, gdy brak wody
 * @param waterKind   rodzaj wody
 * @param type        dominujący typ krajobrazu
 * @param substrate   utwór powierzchniowy
 * @param coverDepth  miąższość utworów czwartorzędowych lub zwietrzeliny nad skałą litą, w metrach
 * @param teren       rzeźba i formy terenu
 * @param wody        cieki, dna dolin i wody stojące w pobliżu
 * @param region      pola regionalne
 */
public record ColumnSample(double surface, int waterLevel, WaterKind waterKind, LandscapeType type,
		Substrate substrate, double coverDepth, Teren teren, Wody wody, Region region) {
	public static final int NO_WATER = Integer.MIN_VALUE;

	public boolean hasWater() {
		return waterLevel != NO_WATER && waterLevel > surfaceMeters();
	}

	/** Wysokość górnej ściany najwyższego bloku gruntu, w pełnych metrach. */
	public int surfaceMeters() {
		return (int) Math.floor(surface);
	}

	/**
	 * Rzeźba w kolumnie.
	 *
	 * @param rawSurface   teren przed wcięciem dolin i jezior, po ukształtowaniu wybrzeża (m n.p.m.)
	 * @param coastD       odległość od linii brzegu morza (m), dodatnia na lądzie
	 * @param wSandr       waga typu SANDR z mieszania makroregionów; pięć wag typów regionów sumuje się do 1
	 * @param wWysoczyzna  waga typu WYSOCZYZNA_MORENOWA
	 * @param wRownina     waga typu ROWNINA_STAROGLACJALNA
	 * @param wPogorze     waga typu POGORZE
	 * @param wBeskidy     waga typu BESKIDY
	 * @param wPobrzeze    udział pasa nadmorskiego 0–1: 1 tam, gdzie próbka dostaje typ POBRZEZE, i maleje
	 *                     do 0 na skraju pasa wybrzeża (B + D + 2000k); typy regionów miesza się z wagą
	 *                     1 − wPobrzeze (makroregiony nie mają typu POBRZEZE, więc to pole nie pochodzi z Blend)
	 * @param formy        bity {@link Landform#bit()} form rozpoznawanych już w {@code sample}: WYDMY,
	 *                     WAL_MORENOWY, GRZBIET, DOLINA_GORSKA, PLAZA, WYDMY_NADMORSKIE, KLIF, ZRODLO
	 * @param wyp          lokalna wypukłość (m): składowe krótkofalowe rzeźby (falowanie i wydmy sandru,
	 *                     pagórki i wały wysoczyzny, drobna rzeźba równiny, żleby i szorstkość fliszu)
	 *                     ważone wagami makroregionów; dodatnia na garbach, ujemna w zagłębieniach
	 * @param wydma        wysokość wydmy nad sandrem (m), 0 poza polami wydm i bez komórki sandru w mieszaniu
	 * @param grzbiet      profil dolin podłużnych fliszu: 0 na osi doliny, ok. 1 na grzbiecie; NaN bez fliszu
	 * @param masyw        siła wyższego masywu Beskidów 0–1 (typ Babiej Góry), 0 poza Beskidami
	 * @param szczyt       wysokość grzbietów Beskidów w okolicy (m n.p.m.): dno pasma + rzeźba · kopuły, czyli
	 *                     teren przy profilach dolin równych 1, z nasyceniem jak teren; przybliża najwyższy
	 *                     szczyt w promieniu ok. 3 km (duży masyw w piętrach, E12); 0 bez komórki Beskidów
	 * @param klif         wysokość krawędzi klifu (m) w pasie formy KLIF, poza nim 0
	 * @param piask        piaszczystość utworu 0–1 (kwantyl szumu o fali 2 km·k, więc rozkład jednostajny);
	 *                     NaN w morzu i zalewie
	 * @param sBar         wygładzony teren bez dolin i jezior (m n.p.m.): średnia 3 × 3 węzłów siatki co 32 m·k
	 *                     z {@code landElevation} (okno ok. 96 m·k), interpolowana dwuliniowo
	 * @param nach         nachylenie terenu bez dolin z tej siatki (°), z różnic centralnych węzłów, w układzie
	 *                     modelu: w skali rozgrywki (odległości poziome ściśnięte, wysokości w metrach podobne)
	 *                     znacznie większe niż nachylenie w blokach; progi w stopniach porównywać z
	 *                     tan(nach) · d(bloki)/d(metry) z {@code VerticalScale} (docs/03-m2-biomy.md, stan po S3)
	 * @param eksp         ekspozycja (°): kierunek spadku stoku od północy (−Z) zgodnie z ruchem wskazówek
	 *                     zegara (90 wschód +X, 180 południe +Z, 270 zachód −X); NaN na terenie płaskim
	 */
	public record Teren(double rawSurface, double coastD, double wSandr, double wWysoczyzna, double wRownina,
			double wPogorze, double wBeskidy, double wPobrzeze, int formy, double wyp, double wydma, double grzbiet,
			double masyw, double szczyt, double klif, double piask, double sBar, double nach, double eksp) {
		/** Czy {@code sample} rozpoznał formę (tylko formy z opisu pola {@code formy}). */
		public boolean ma(Landform forma) {
			return (formy & forma.bit()) != 0;
		}

		/** Dopisuje do zbioru formy rozpoznane w {@code sample}. */
		public void dodajFormy(Set<Landform> zbior) {
			for (Landform f : Landform.Z_PROBKI) {
				if (ma(f)) {
					zbior.add(f);
				}
			}
		}
	}

	/**
	 * Wody w pobliżu kolumny. Pola cieku pochodzą z {@code RiverNetwork.query}: odległość, szerokość
	 * i lustro dotyczą najbliższego koryta, a dno doliny, rząd i spadek cieku, którego dolina dominuje.
	 *
	 * @param rzad            rząd cieku, którego dolina dominuje (0 = brak cieku w zasięgu)
	 * @param zrodlo          strefa źródłowa tego cieku
	 * @param odlKoryta       d: odległość (m) od brzegu najbliższego koryta, ≤ 0 w korycie, +∞ poza zasięgiem;
	 *                        ciągła, mierzona w układzie doliny (u wzdłuż, v w poprzek), w którym rysowane jest
	 *                        koryto, więc przy silnie wygiętej dolinie odbiega od odległości euklidesowej
	 *                        (Odstępstwo S2 w docs/03-m2-biomy.md). d ≤ 0 nie oznacza wody: przy źródle koryto
	 *                        jest węższe od W, a w suchej głowicy doliny go nie ma; strefę koryta wybiera się
	 *                        z {@code waterKind() == RIVER}
	 * @param szerKoryta      W: szerokość tego koryta (m); NaN bez cieku
	 * @param poziomKoryta    lustro tego koryta (m n.p.m., bez zaokrąglenia do bloku); NaN bez cieku
	 * @param wDnie           kolumna w dnie doliny
	 * @param u               położenie w dnie doliny: 0 przy korycie, 1 na skraju dna; NaN poza dnem
	 * @param polSzerDna      półszerokość dna doliny (m); NaN bez cieku
	 * @param spadek          spadek cieku w ‰, przeliczony na skalę 1:1; NaN bez cieku
	 * @param brzegWypukly    kolumna po wewnętrznej (wypukłej) stronie łuku meandra najbliższego koryta;
	 *                        liczone do d ≤ max(W; 15 m·k), przy wyraźnych meandrach (krętość odcinka
	 *                        od ok. 1,03) albo szerokim korycie (W / chan ≥ 6 m); wąskie potoki mają false
	 * @param s               odległość (m) od brzegu najbliższej wody stojącej: dodatnia na lądzie, ujemna
	 *                        w wodzie lub torfie oczka; +∞ poza pasem: jezioro bezodpływowe 150 m·k,
	 *                        jezioro rynnowe max(150 m·k; 70 m), oczko 45 m,
	 *                        starorzecze 40 m·k
	 * @param poziomBrzegu    lustro tej wody (m n.p.m.); {@link ColumnSample#NO_WATER} bez wody stojącej
	 * @param rodzajStojacej  rodzaj tej wody
	 * @param torfOmbro       oczko torfowe ombrotroficzne (brzeg misy dalej niż 300 m·k od koryta)
	 * @param idJeziora       skrót identyfikujący zbiornik (stały w całym zbiorniku), 0 bez wody stojącej
	 * @param promienStojacej promień oczka lub jeziora bezodpływowego, półszerokość jeziora rynnowego
	 *                        lub starorzecza w tym miejscu (m); NaN bez wody stojącej
	 */
	public record Wody(int rzad, boolean zrodlo, double odlKoryta, double szerKoryta, double poziomKoryta,
			boolean wDnie, double u, double polSzerDna, double spadek, boolean brzegWypukly, double s,
			int poziomBrzegu, RodzajStojacej rodzajStojacej, boolean torfOmbro, long idJeziora,
			double promienStojacej) {
		/** Brak cieków i wód stojących (morze i miejsca poza zasięgiem). */
		public static final Wody BRAK = new Wody(0, false, Double.POSITIVE_INFINITY, Double.NaN, Double.NaN, false,
				Double.NaN, Double.NaN, Double.NaN, false, Double.POSITIVE_INFINITY, NO_WATER, RodzajStojacej.BRAK,
				false, 0L, Double.NaN);
	}

	/** Rodzaj wody stojącej najbliższej kolumnie. */
	public enum RodzajStojacej {
		BRAK,
		/** Jezioro w rynnie polodowcowej. */
		JEZIORO_RYNNOWE,
		/** Jezioro w obniżeniu bezodpływowym sieci rzecznej. */
		JEZIORO_BEZODPLYWOWE,
		/** Oczko wytopiskowe z wodą. */
		OCZKO,
		/** Oczko wytopiskowe wypełnione torfem (bez wody). */
		OCZKO_TORFOWE,
		/** Starorzecze w dnie doliny. */
		STARORZECZE
	}

	/**
	 * Pola regionalne z siatki {@code RegionalField} (węzły co 16 km·zs, interpolacja dwuliniowa), także w morzu.
	 *
	 * @param oceanicznosc O: oceaniczność klimatu 0–1 (zachód i bliskość morza podnoszą)
	 * @param podgorskosc  P: podgórskość 0–1 (1 przy osi pasma górskiego, P ≥ 0,5 do ok. 150–250 km·zs od niej)
	 */
	public record Region(double oceanicznosc, double podgorskosc) {
	}
}
