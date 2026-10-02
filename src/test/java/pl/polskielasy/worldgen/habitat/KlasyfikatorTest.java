package pl.polskielasy.worldgen.habitat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import pl.polskielasy.worldgen.chunk.VerticalScale;
import pl.polskielasy.worldgen.landscape.ColumnSample;
import pl.polskielasy.worldgen.landscape.Landform;
import pl.polskielasy.worldgen.landscape.LandscapeModel;
import pl.polskielasy.worldgen.landscape.LandscapeScale;
import pl.polskielasy.worldgen.landscape.Noise;
import pl.polskielasy.worldgen.landscape.Substrate;
import pl.polskielasy.worldgen.landscape.WaterKind;

/**
 * Klasyfikator siedlisk (docs/03-m2-biomy.md §3.4, §12.1): determinizm, pakowanie kodu, przypadki
 * syntetyczne, osiągalność każdego biomu i każdej strefy, brak łęgu poza dnem doliny na 10⁶ próbkach
 * i koszt klasyfikacji ≤ 0,5 µs.
 */
class KlasyfikatorTest {
	static final long SEED = 20260927L;

	static Klasyfikator real(Klasyfikator.Tryb t) {
		return new Klasyfikator(SEED, LandscapeScale.REALISTIC, t);
	}

	@Test
	void pakowanieJestOdwracalne() {
		long h = 1;
		for (int i = 0; i < 20_000; i++) {
			h = Noise.mix(h + i);
			Biom b = Biom.values()[(int) Long.remainderUnsigned(h, Biom.values().length)];
			Strefa s = Strefa.values()[(int) Long.remainderUnsigned(h >>> 8, Strefa.values().length)];
			Stl stl = Stl.values()[(int) Long.remainderUnsigned(h >>> 16, Stl.values().length)];
			Zespol z = Zespol.values()[(int) Long.remainderUnsigned(h >>> 24, Zespol.values().length)];
			Pokrycie p = Pokrycie.values()[(int) Long.remainderUnsigned(h >>> 32, Pokrycie.values().length)];
			int f = (int) (h >>> 40) & 15;
			Gleba g = Gleba.values()[(int) Long.remainderUnsigned(h >>> 44, Gleba.values().length)];
			Siedlisko sd = new Siedlisko(b, s, stl, z, p, f, g);
			assertEquals(sd, Siedlisko.of(sd.kod()));
		}
		assertEquals(36, Biom.values().length);
	}

	@Test
	void identyfikatoryBiomowSaZamrozone() {
		String oczekiwane = "bor_suchy bor_swiezy bor_bazynowy bor_wilgotny bor_bagienny bor_mieszany las_mieszany grad "
				+ "buczyna_nizinna ols leg_jesionowo_olszowy leg_wierzbowo_topolowy leg_wiazowo_jesionowy jedlina_wyzynna "
				+ "buczyna_gorska swierczyna_gorska olszyna_gorska kosodrzewina torfowisko_wysokie torfowisko_niskie szuwar "
				+ "wikliny wrzosowisko laka_wilgotna laka_swieza pole plaza wydma_biala wydma_szara hala morze zalew rzeka "
				+ "potok jezioro jezioro_dystroficzne";
		StringBuilder sb = new StringBuilder();
		for (Biom b : Biom.values()) {
			sb.append(sb.isEmpty() ? "" : " ").append(b.id());
		}
		assertEquals(oczekiwane, sb.toString());
		long lesne = Biom.values().length - EnumSet.allOf(Biom.class).stream().filter(b -> !b.lesny()).count();
		assertEquals(18, lesne);
		assertEquals(6, EnumSet.allOf(Biom.class).stream().filter(Biom::wodny).count());
	}

	@Test
	void wynikJestDeterministyczny() {
		for (LandscapeScale sc : new LandscapeScale[] {LandscapeScale.REALISTIC, LandscapeScale.GAMEPLAY}) {
			LandscapeModel m = new LandscapeModel(SEED, sc, 1.0);
			Klasyfikator a = new Klasyfikator(SEED, sc, Klasyfikator.Tryb.N);
			Klasyfikator b = new Klasyfikator(SEED, sc, Klasyfikator.Tryb.N);
			double krok = sc == LandscapeScale.REALISTIC ? 397 : 23;
			int n = 160;
			int[] seryjnie = new int[n * n];
			for (int i = 0; i < n * n; i++) {
				double x = (i % n) * krok - 3_000;
				double z = (i / n) * krok + 1_000;
				seryjnie[i] = a.klasyfikuj(m.sample(x, z), x, z);
			}
			int[] rownolegle = new int[n * n];
			LandscapeModel m2 = new LandscapeModel(SEED, sc, 1.0);
			IntStream.range(0, n * n).parallel().forEach(i -> {
				double x = (i % n) * krok - 3_000;
				double z = (i / n) * krok + 1_000;
				rownolegle[i] = b.klasyfikuj(m2.sample(x, z), x, z);
			});
			for (int i = 0; i < n * n; i++) {
				assertEquals(seryjnie[i], rownolegle[i], "kolumna " + i + " w skali " + sc.id());
			}
		}
	}

	@Test
	void nachylenieWSkaliRozgrywkiZgadzaSieZOdwzorowaniemPionowym() {
		for (double m : new double[] {5, 50, 130, 400, 1_000, 1_600}) {
			double d = (VerticalScale.GAMEPLAY.blocksForMeters(m + 0.01) - VerticalScale.GAMEPLAY.blocksForMeters(m - 0.01)) / 0.02;
			assertEquals(d, Kalibracja.blokiNaMetrRozgrywki(m), 1e-4 * d, "m = " + m);
		}
	}

	// ------------------------------------------------------------------ przypadki syntetyczne

	/**
	 * Przypadek: próbka i oczekiwany biom (null: dowolny) i strefa (null: dowolna). Zależne od szumu (płaty,
	 * warianty) przesuwamy po punktach, aż wynik się pojawi; pozostałe muszą wyjść w pierwszym punkcie.
	 */
	record Przypadek(String opis, ColumnSample probka, Klasyfikator.Tryb tryb, Biom biom, Strefa strefa, boolean szukaj) {
	}

	static List<Przypadek> przypadki() {
		List<Przypadek> l = new ArrayList<>();
		Klasyfikator.Tryb n = Klasyfikator.Tryb.N;
		Klasyfikator.Tryb d = Klasyfikator.Tryb.D;
		// Wody.
		l.add(new Przypadek("morze", Probka.wybrzeze(-500, 0, Substrate.SAND).woda(WaterKind.SEA, 0, -12).build(), n,
				Biom.MORZE, Strefa.BRAK, false));
		Probka zalew = Probka.wybrzeze(800, 0, Substrate.LAKE_MUD).woda(WaterKind.SEA, 0, -3);
		l.add(new Przypadek("zalew", zalew.build(), n, Biom.ZALEW, Strefa.ELODEIDY, false));
		l.add(new Przypadek("szuwar zalewu", Probka.wybrzeze(800, 0, Substrate.LAKE_MUD).woda(WaterKind.SEA, 0, -1).build(),
				n, Biom.SZUWAR, Strefa.SZUWAR, false));
		l.add(new Przypadek("rzeka", Probka.wysoczyzna().h(95).ciek(3, 150, -20, 0.3).woda(WaterKind.RIVER, 98, 95).build(),
				n, Biom.RZEKA, Strefa.KORYTO, false));
		l.add(new Przypadek("potok", Probka.beskidy(600).ciek(1, 6, -1, 20).woda(WaterKind.RIVER, 600, 599.5).build(), n,
				Biom.POTOK, Strefa.KORYTO, false));
		l.add(new Przypadek("jezioro (głębia)", Probka.wysoczyzna().stojaca(ColumnSample.RodzajStojacej.JEZIORO_RYNNOWE, -80,
				110, 400, 5).woda(WaterKind.LAKE, 110, 100).build(), n, Biom.JEZIORO, Strefa.BRAK, false));
		l.add(new Przypadek("elodeidy", Probka.wysoczyzna().stojaca(ColumnSample.RodzajStojacej.JEZIORO_RYNNOWE, -40, 110, 400,
				5).woda(WaterKind.LAKE, 110, 106).build(), n, Biom.JEZIORO, Strefa.ELODEIDY, false));
		l.add(new Przypadek("nymfeidy starorzecza", Probka.wysoczyzna().stojaca(ColumnSample.RodzajStojacej.STARORZECZE, -30,
				110, 60, 5).woda(WaterKind.OXBOW, 110, 108).build(), n, Biom.JEZIORO, Strefa.NYMFEIDY, true));
		l.add(new Przypadek("szuwar jeziora (biom)", Probka.wysoczyzna().stojaca(ColumnSample.RodzajStojacej.JEZIORO_RYNNOWE,
				-20, 110, 400, 5).woda(WaterKind.LAKE, 110, 109).build(), n, Biom.SZUWAR, Strefa.SZUWAR, false));
		l.add(new Przypadek("szuwar oczka (strefa)", Probka.wysoczyzna().stojaca(ColumnSample.RodzajStojacej.OCZKO, -3, 110,
				25, 5).woda(WaterKind.KETTLE, 110, 109.5).build(), n, Biom.JEZIORO, Strefa.SZUWAR, false));
		for (long id = 1; id < 200; id++) {
			// Pierwszy zbiornik o skrócie dystroficznym na sandrze: pło przy brzegu.
			ColumnSample c = Probka.sandr().stojaca(ColumnSample.RodzajStojacej.OCZKO, -2, 140, 60, id)
					.woda(WaterKind.KETTLE, 140, 139).build();
			if (Siedlisko.biom(real(n).klasyfikuj(c, 0, 0)) == Biom.JEZIORO_DYSTROFICZNE) {
				l.add(new Przypadek("jezioro dystroficzne, pło", c, n, Biom.JEZIORO_DYSTROFICZNE, Strefa.PLO, false));
				break;
			}
		}
		for (long id = 1; id < 400; id++) {
			// Jezioro lobeliowe: pas olszy przy brzegu.
			ColumnSample woda = Probka.sandr().stojaca(ColumnSample.RodzajStojacej.JEZIORO_RYNNOWE, -30, 140, 300, id)
					.woda(WaterKind.LAKE, 140, 134).build();
			if (Siedlisko.zespol(real(n).klasyfikuj(woda, 0, 0)) == Zespol.LOBELIOWY) {
				ColumnSample lad = Probka.sandr().h(141.5).stojaca(ColumnSample.RodzajStojacej.JEZIORO_RYNNOWE, 2, 140, 300, id)
						.build();
				l.add(new Przypadek("jezioro lobeliowe, olsza przy brzegu", lad, n, null, Strefa.OLSZA_BRZEG, false));
				break;
			}
		}
		// Wybrzeże.
		l.add(new Przypadek("plaża mokra", Probka.wybrzeze(10, 1, Substrate.BEACH_SAND).build(), n, Biom.PLAZA, Strefa.BRAK,
				false));
		l.add(new Przypadek("kidzina", Probka.wybrzeze(40, 1.5, Substrate.BEACH_SAND).build(), n, Biom.PLAZA, Strefa.KIDZINA,
				false));
		l.add(new Przypadek("wydma inicjalna", Probka.wybrzeze(70, 5, Substrate.BEACH_SAND).build(), n, Biom.WYDMA_BIALA,
				Strefa.WYDMA_INICJALNA, false));
		l.add(new Przypadek("wydma szara", Probka.wybrzeze(320, 6, Substrate.SAND).build(), n, Biom.WYDMA_SZARA, Strefa.BRAK,
				false));
		l.add(new Przypadek("bór bażynowy", Probka.wybrzeze(1_000, 10, Substrate.SAND).build(), n, Biom.BOR_BAZYNOWY,
				Strefa.BRAK, false));
		l.add(new Przypadek("ściana klifu", Probka.wybrzeze(75, 20, Substrate.GLACIAL_TILL).forma(Landform.KLIF).build(), n,
				null, Strefa.KLIF_SCIANA, false));
		l.add(new Przypadek("korona klifu", Probka.wybrzeze(100, 20, Substrate.GLACIAL_TILL).build(), n, null,
				Strefa.KLIF_KORONA, false));
		Probka zaplecze = Probka.wybrzeze(400, 1.2, Substrate.SAND);
		l.add(new Przypadek("torfowisko niskie za mierzeją", zaplecze.build(), n, Biom.TORFOWISKO_NISKIE, Strefa.BRAK, false));
		// Siedliska strefowe.
		Probka bs = Probka.sandr().forma(Landform.WYDMY);
		bs.piask = 0.95;
		bs.sBar = 133;
		l.add(new Przypadek("bór suchy na wydmie", bs.build(), n, Biom.BOR_SUCHY, Strefa.BRAK, true));
		l.add(new Przypadek("prześwit wrzosowiska na wydmie", bs.build(), n, Biom.WRZOSOWISKO, Strefa.BRAK, true));
		Probka bsw = Probka.sandr();
		bsw.piask = 0.95;
		l.add(new Przypadek("bór świeży", bsw.build(), n, Biom.BOR_SWIEZY, Strefa.BRAK, false));
		Probka bw = Probka.sandr();
		bw.piask = 0.95;
		bw.sBar = 141.0;
		l.add(new Przypadek("bór wilgotny", bw.build(), n, Biom.BOR_WILGOTNY, Strefa.BRAK, false));
		Probka bb = Probka.sandr();
		bb.piask = 0.95;
		bb.sBar = 142.3;
		l.add(new Przypadek("bór bagienny", bb.build(), n, Biom.BOR_BAGIENNY, Strefa.BRAK, false));
		Probka bm = Probka.sandr();
		bm.piask = 0.2;
		l.add(new Przypadek("bór mieszany", bm.build(), n, Biom.BOR_MIESZANY, Strefa.BRAK, false));
		Probka lm = Probka.wysoczyzna();
		lm.piask = 0.75;
		lm.o = 0.35;
		l.add(new Przypadek("las mieszany", lm.build(), n, Biom.LAS_MIESZANY, Strefa.BRAK, false));
		Probka gr = Probka.wysoczyzna();
		gr.piask = 0.1;
		gr.o = 0.35;
		l.add(new Przypadek("grąd", gr.build(), n, Biom.GRAD, Strefa.BRAK, false));
		Probka bk = Probka.wysoczyzna();
		bk.piask = 0.1;
		bk.o = 0.95;
		bk.wyp = 1;
		l.add(new Przypadek("buczyna niżowa", bk.build(), n, Biom.BUCZYNA_NIZINNA, Strefa.BRAK, true));
		Probka ol = Probka.wysoczyzna();
		ol.piask = 0.1;
		ol.sBar = 124.2;
		l.add(new Przypadek("ols strefowy", ol.build(), n, Biom.OLS, Strefa.BRAK, false));
		Probka jd = Probka.wysoczyzna().h(400);
		jd.piask = 0.95;
		jd.p = 0.7;
		l.add(new Przypadek("jedlina wyżynna", jd.build(), n, Biom.JEDLINA_WYZYNNA, Strefa.BRAK, false));
		// Cieki (§4).
		Probka wik = Probka.wysoczyzna().h(91.7).ciek(3, 150, 20, 0.3).dno(0, 900);
		l.add(new Przypadek("wikliny przy dużej rzece", wik.build(), n, Biom.WIKLINY, Strefa.WIKLINA, false));
		Probka lacha = Probka.wysoczyzna().h(91.7).ciek(3, 150, 30, 0.3).dno(0, 900);
		lacha.brzegWypukly = true;
		l.add(new Przypadek("łacha", lacha.build(), n, Biom.WIKLINY, Strefa.LACHA, true));
		Probka okr = Probka.wysoczyzna().h(91.7).ciek(3, 150, 50, 0.3).dno(0, 900);
		l.add(new Przypadek("okrajek za wikliną", okr.build(), n, Biom.LEG_WIERZBOWO_TOPOLOWY, Strefa.OKRAJEK, true));
		Probka sal = Probka.wysoczyzna().h(91.7).ciek(3, 150, 150, 0.3).dno(0.1, 900);
		l.add(new Przypadek("łęg wierzbowy", sal.build(), n, Biom.LEG_WIERZBOWO_TOPOLOWY, Strefa.BRAK, true));
		Probka wiaz = Probka.wysoczyzna().h(91.7).ciek(3, 150, 800, 0.3).dno(0.5, 900);
		wiaz.wyp = 0.5;
		l.add(new Przypadek("łęg wiązowo-jesionowy", wiaz.build(), n, Biom.LEG_WIAZOWO_JESIONOWY, Strefa.BRAK, true));
		Probka zast = Probka.wysoczyzna().h(91.7);
		zast.poziomKoryta = 90.4;
		zast.ciek(3, 150, 800, 0.2).dno(0.85, 900);
		zast.wyp = -1;
		l.add(new Przypadek("zastoisko: torfowisko niskie", zast.build(), n, Biom.TORFOWISKO_NISKIE, Strefa.BRAK, true));
		l.add(new Przypadek("zastoisko: ols", zast.build(), n, Biom.OLS, Strefa.BRAK, true));
		Probka olj = Probka.wysoczyzna().ciek(1, 5, 8, 1).dno(0.1, 70);
		l.add(new Przypadek("łęg jesionowo-olszowy", olj.build(), n, Biom.LEG_JESIONOWO_OLSZOWY, Strefa.BRAK, true));
		Probka ziol = Probka.wysoczyzna().ciek(1, 5, 1, 1).dno(0, 70);
		l.add(new Przypadek("ziołorośla małej rzeki", ziol.build(), n, Biom.LEG_JESIONOWO_OLSZOWY, Strefa.ZIOLOROSLA, false));
		Probka wierzby = Probka.sandr().ciek(2, 20, 15, 1).dno(0, 100);
		wierzby.piask = 0.95;
		l.add(new Przypadek("wierzby małej rzeki na piasku", wierzby.build(), n, null, Strefa.WIERZBY, true));
		Probka zrodlo = Probka.wysoczyzna().ciek(1, 3, 10, 1).dno(0, 40).forma(Landform.ZRODLO);
		l.add(new Przypadek("źródlisko", zrodlo.build(), n, Biom.LEG_JESIONOWO_OLSZOWY, Strefa.ZRODLISKO, true));
		Probka szpaler = Probka.wysoczyzna().h(126).ciek(1, 5, 6, 1);
		szpaler.rawSurface = 126;
		l.add(new Przypadek("szpaler olszy (tryb D)", szpaler.build(), d, null, Strefa.SZPALER, true));
		Probka kam = Probka.beskidy(500).ciek(2, 8, 4, 8).dno(0, 40);
		kam.brzegWypukly = true;
		l.add(new Przypadek("kamieniec", kam.build(), n, null, Strefa.KAMIENIEC, false));
		Probka wikG = Probka.beskidy(500).ciek(2, 8, 5, 8).dno(0.1, 40);
		l.add(new Przypadek("wiklina górska", wikG.build(), n, null, Strefa.WIKLINA, false));
		Probka olsz = Probka.beskidy(600).ciek(2, 8, 12, 8).dno(0.2, 40);
		l.add(new Przypadek("olszyna górska", olsz.build(), n, Biom.OLSZYNA_GORSKA, Strefa.BRAK, true));
		Probka ziolG = Probka.beskidy(1_050).ciek(1, 3, 0.5, 30).dno(0, 20);
		l.add(new Przypadek("ziołorośla nadpotokowe", ziolG.build(), n, null, Strefa.ZIOLOROSLA_GORSKIE, false));
		// Wody stojące na lądzie.
		Probka lozo = Probka.wysoczyzna().h(111.5).stojaca(ColumnSample.RodzajStojacej.JEZIORO_RYNNOWE, 20, 110, 400, 5);
		lozo.nach = 1;
		l.add(new Przypadek("łozowisko przy jeziorze", lozo.build(), n, Biom.OLS, Strefa.LOZOWISKO, true));
		Probka szl = Probka.wysoczyzna().h(111.2).stojaca(ColumnSample.RodzajStojacej.JEZIORO_RYNNOWE, 3, 110, 400, 5);
		l.add(new Przypadek("szuwar lądowy", szl.build(), n, null, Strefa.SZUWAR_LADOWY, false));
		Probka tw = Probka.sandr().h(139.5).stojaca(ColumnSample.RodzajStojacej.OCZKO_TORFOWE, -30, 140, 120, 9);
		tw.substrate = Substrate.PEAT;
		tw.torfOmbro = true;
		l.add(new Przypadek("torfowisko wysokie", tw.build(), n, Biom.TORFOWISKO_WYSOKIE, Strefa.BRAK, false));
		// Góry (§5.1).
		Probka buk = Probka.beskidy(800);
		buk.piask = 0.1;
		buk.nach = 20;
		buk.eksp = 180;
		l.add(new Przypadek("buczyna karpacka", buk.build(), n, Biom.BUCZYNA_GORSKA, Strefa.BRAK, false));
		l.add(new Przypadek("świerczyna górska", Probka.beskidy(1_260).build(), n, Biom.SWIERCZYNA_GORSKA, Strefa.BRAK, false));
		Probka kos = Probka.beskidy(1_500);
		kos.szczyt = 1_700;
		l.add(new Przypadek("kosodrzewina", kos.build(), n, Biom.KOSODRZEWINA, Strefa.BRAK, false));
		Probka hala = Probka.beskidy(1_720);
		hala.szczyt = 1_720;
		l.add(new Przypadek("hala", hala.build(), n, Biom.HALA, Strefa.BRAK, false));
		Probka gl = Probka.beskidy(1_355);
		gl.szczyt = 1_700;
		l.add(new Przypadek("granica lasu", gl.build(), n, Biom.SWIERCZYNA_GORSKA, Strefa.GRANICA_LASU, true));
		// Tryb D: biomy nieleśne z maski lasu.
		Probka pole = Probka.wysoczyzna();
		pole.piask = 0.1;
		pole.o = 0.35;
		l.add(new Przypadek("pole (tryb D)", pole.build(), d, Biom.POLE, Strefa.BRAK, true));
		l.add(new Przypadek("łąka świeża (tryb D)", pole.build(), d, Biom.LAKA_SWIEZA, Strefa.BRAK, true));
		Probka laka = Probka.wysoczyzna().ciek(1, 5, 8, 1).dno(0.1, 70);
		l.add(new Przypadek("łąka wilgotna (tryb D)", laka.build(), d, Biom.LAKA_WILGOTNA, null, true));
		return l;
	}

	/** Pierwszy punkt (z 4000 na spirali), w którym przypadek daje oczekiwany wynik; null, gdy żaden. */
	static double[] znajdz(Przypadek p) {
		Klasyfikator k = real(p.tryb());
		int proby = p.szukaj() ? 4_000 : 1;
		for (int i = 0; i < proby; i++) {
			double x = i * 37.3;
			double z = i * 53.9 - 7_000;
			int kod = k.klasyfikuj(p.probka(), x, z);
			if ((p.biom() == null || Siedlisko.biom(kod) == p.biom()) && (p.strefa() == null || Siedlisko.strefa(kod) == p.strefa())) {
				return new double[] {x, z};
			}
		}
		return null;
	}

	@Test
	void przypadkiSyntetyczne() {
		List<String> bledy = new ArrayList<>();
		Klasyfikator k = real(Klasyfikator.Tryb.N);
		for (Przypadek p : przypadki()) {
			if (znajdz(p) == null) {
				int kod = new Klasyfikator(SEED, LandscapeScale.REALISTIC, p.tryb()).klasyfikuj(p.probka(), 0, -7_000);
				bledy.add(p.opis() + ": oczekiwano " + p.biom() + "/" + p.strefa() + ", jest " + Siedlisko.of(kod));
			}
		}
		assertTrue(bledy.isEmpty(), String.join("\n", bledy));
		assertTrue(k.tryb() == Klasyfikator.Tryb.N);
	}

	// ------------------------------------------------------------------ świat: osiągalność i łęgi tylko w dnie

	/** Skupiska 125 × 125 kolumn co 2 m·k w 64 punktach (ok. 10⁶ próbek na skalę). */
	static void przegladaj(LandscapeScale sc, Klasyfikator.Tryb tryb, Predicate<int[]> rob, Obserwator o) {
		LandscapeModel m = new LandscapeModel(SEED, sc, 1.0);
		Klasyfikator k = new Klasyfikator(SEED, sc, tryb);
		double obszar = sc == LandscapeScale.REALISTIC ? 300_000 : 20_000;
		double krok = 2 * sc.local();
		IntStream.range(0, 64).parallel().forEach(q -> {
			long h = Noise.mix(SEED + 977L * q);
			double cx = ((h >>> 11) * 0x1.0p-53 - 0.5) * obszar;
			double cz = ((Noise.mix(h) >>> 11) * 0x1.0p-53 - 0.5) * obszar;
			for (int j = 0; j < 125; j++) {
				for (int i = 0; i < 125; i++) {
					double x = cx + i * krok;
					double z = cz + j * krok;
					ColumnSample s = m.sample(x, z);
					o.kolumna(k, s, x, z, k.klasyfikuj(s, x, z));
				}
			}
		});
	}

	interface Obserwator {
		void kolumna(Klasyfikator k, ColumnSample s, double x, double z, int kod);
	}

	@Test
	void kazdyBiomIKazdaStrefaSaOsiagalne() {
		Set<Biom> biomy = java.util.concurrent.ConcurrentHashMap.newKeySet();
		Set<Strefa> strefy = java.util.concurrent.ConcurrentHashMap.newKeySet();
		for (LandscapeScale sc : new LandscapeScale[] {LandscapeScale.REALISTIC, LandscapeScale.GAMEPLAY}) {
			przegladaj(sc, Klasyfikator.Tryb.N, null, (k, s, x, z, kod) -> {
				biomy.add(Siedlisko.biom(kod));
				strefy.add(Siedlisko.strefa(kod));
			});
		}
		Set<Biom> synB = EnumSet.noneOf(Biom.class);
		Set<Strefa> synS = EnumSet.noneOf(Strefa.class);
		for (Przypadek p : przypadki()) {
			if (znajdz(p) != null) {
				if (p.biom() != null) {
					synB.add(p.biom());
				}
				if (p.strefa() != null) {
					synS.add(p.strefa());
				}
			}
		}
		Set<Biom> tylkoSynB = EnumSet.allOf(Biom.class);
		tylkoSynB.removeAll(biomy);
		Set<Strefa> tylkoSynS = EnumSet.allOf(Strefa.class);
		tylkoSynS.removeAll(strefy);
		System.out.println("Biomy tylko z próbek syntetycznych (rzadkie albo tryb D): " + tylkoSynB);
		System.out.println("Strefy tylko z próbek syntetycznych: " + tylkoSynS);
		Set<Biom> brakB = EnumSet.copyOf(tylkoSynB);
		brakB.removeAll(synB);
		Set<Strefa> brakS = EnumSet.copyOf(tylkoSynS);
		brakS.removeAll(synS);
		assertTrue(brakB.isEmpty(), "nieosiągalne biomy: " + brakB);
		assertTrue(brakS.isEmpty(), "nieosiągalne strefy: " + brakS);
		// W 64 skupiskach w świecie (bez próbek syntetycznych) musi być większość biomów; rzadkie to piętra
		// wysokie, tryb D i biomy wąskich pasów (zalew, wydma szara, zastoiska wielkich den).
		assertTrue(biomy.size() >= 25, "biomy w świecie: " + biomy.size() + " " + biomy);
	}

	@Test
	void legiTylkoWDnieZrodliskachIWysiekach() {
		AtomicLong legi = new AtomicLong();
		AtomicLong poza = new AtomicLong();
		AtomicLong wszystkie = new AtomicLong();
		List<String> przyklady = java.util.Collections.synchronizedList(new ArrayList<>());
		for (LandscapeScale sc : new LandscapeScale[] {LandscapeScale.REALISTIC, LandscapeScale.GAMEPLAY}) {
			przegladaj(sc, Klasyfikator.Tryb.N, null, (k, s, x, z, kod) -> {
				wszystkie.incrementAndGet();
				Biom b = Siedlisko.biom(kod);
				if (!Biom.LEGI.contains(b)) {
					return;
				}
				legi.incrementAndGet();
				Klasyfikator.Kolumna c = new Klasyfikator.Kolumna(k, s, x, z);
				boolean dozwolone = c.dno() || s.teren().ma(Landform.ZRODLO)
						|| b == Biom.LEG_JESIONOWO_OLSZOWY && c.wysiek();
				if (!dozwolone) {
					poza.incrementAndGet();
					if (przyklady.size() < 5) {
						przyklady.add(String.format(Locale.ROOT, "%s (%.0f, %.0f) %s: %s", sc.id(), x, z, b, s));
					}
				}
			});
		}
		System.out.printf(Locale.ROOT, "Łęgi: %d z %d próbek, poza dnem %d%n", legi.get(), wszystkie.get(), poza.get());
		assertTrue(wszystkie.get() >= 1_900_000, "za mało próbek");
		assertTrue(legi.get() > 1_000, "za mało łęgów: " + legi.get());
		assertEquals(0, poza.get(), "łęg poza dnem: " + przyklady);
	}

	@Test
	void klasyfikacjaTrwaNajwyzejPolMikrosekundy() {
		for (LandscapeScale sc : new LandscapeScale[] {LandscapeScale.REALISTIC, LandscapeScale.GAMEPLAY}) {
			LandscapeModel m = new LandscapeModel(SEED, sc, 1.0);
			Klasyfikator k = new Klasyfikator(SEED, sc, Klasyfikator.Tryb.N);
			int n = 100_000;
			ColumnSample[] s = new ColumnSample[n];
			double[] xs = new double[n];
			double[] zs = new double[n];
			// Pięć obszarów po 20 tys. kolumn (gęsto, jak w chunku): nizina, dolina, Beskidy, wybrzeże, środek.
			double[][] srodki = {{0, 0}, {-49_879, -3_478}, {154_834, 1_058_738}, {-357_357, -380_500}, {66_000, 21_000}};
			double skala = sc.local();
			for (int i = 0; i < n; i++) {
				double[] c = srodki[i / 20_000];
				int j = i % 20_000;
				xs[i] = c[0] * (sc == LandscapeScale.REALISTIC ? 1 : 0.02) + (j % 141) * 2 * skala;
				zs[i] = c[1] * (sc == LandscapeScale.REALISTIC ? 1 : 0.02) + (j / 141) * 2 * skala;
				s[i] = m.sample(xs[i], zs[i]);
			}
			long najlepszy = Long.MAX_VALUE;
			long suma = 0;
			for (int r = 0; r < 12; r++) {
				long t0 = System.nanoTime();
				for (int i = 0; i < n; i++) {
					suma += k.klasyfikuj(s[i], xs[i], zs[i]);
				}
				long t = System.nanoTime() - t0;
				if (r >= 4) {
					najlepszy = Math.min(najlepszy, t);
				}
			}
			double us = najlepszy / 1e3 / n;
			System.out.printf(Locale.ROOT, "Klasyfikacja (%s): %.3f µs na kolumnę (suma %d)%n", sc.id(), us, suma);
			assertTrue(us <= 0.5, "klasyfikacja " + us + " µs");
		}
	}

	@Test
	void szczytBezDuzegoMasywuToLasDoWierzcholka() {
		Probka bez = Probka.beskidy(1_500);
		bez.szczyt = 1_500;
		assertEquals(Biom.SWIERCZYNA_GORSKA, Siedlisko.biom(real(Klasyfikator.Tryb.N).klasyfikuj(bez.build(), 0, 0)));
		Probka wiatr = Probka.beskidy(1_700);
		wiatr.szczyt = 1_500;
		assertEquals(Biom.SWIERCZYNA_GORSKA, Siedlisko.biom(real(Klasyfikator.Tryb.N).klasyfikuj(wiatr.build(), 0, 0)));
	}
}
