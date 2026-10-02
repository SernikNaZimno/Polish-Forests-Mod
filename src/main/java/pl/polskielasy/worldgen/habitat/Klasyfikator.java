package pl.polskielasy.worldgen.habitat;

import pl.polskielasy.worldgen.landscape.ColumnSample;
import pl.polskielasy.worldgen.landscape.Landform;
import pl.polskielasy.worldgen.landscape.LandscapeModel;
import pl.polskielasy.worldgen.landscape.LandscapeScale;
import pl.polskielasy.worldgen.landscape.Noise;

/**
 * Klasyfikator siedlisk (Z1, docs/03-m2-biomy.md §3.4): czysta funkcja próbki kolumny i jej położenia,
 * zwracająca kod {@link Siedlisko}. Woła ją źródło biomów, {@code fill()}, dyspozytory roślinności,
 * komendy, podgląd PNG i testy, więc biom, gleba i roślinność nie mogą się rozjechać.
 *
 * <p>Kolejność oznacza priorytet: woda ({@link Woda}), wybrzeże ({@link Wybrzeze}), strefy nadwodne
 * ({@link StrefyNadwodne}), piętra górskie ({@link Pietra}), siedliska strefowe, a w trybie D maska
 * lasu ({@link Lesistosc}). Strefę może ustawić wcześniejszy krok bez wyboru biomu (np. KLIF_SCIANA
 * w biomie wysoczyzny albo ZIOLOROSLA_GORSKIE w lesie strefowym); biom wybiera wtedy dalszy krok.
 *
 * <p>Położenie (x, z) służy tylko szumom drgań granic stref, płatów i wariantów, liczonym z ziarna
 * świata przez {@code habitat.*}, więc wynik zależy wyłącznie od ziarna, skali, trybu, próbki i położenia.
 * Instancja jest niezmienna i bezpieczna wątkowo.
 */
public final class Klasyfikator {
	/** Tryb roślinności: N „roślinność naturalna” (domyślny do M8, decyzja M2-B), D „dzisiejsza Polska”. */
	public enum Tryb {
		N, D
	}

	final LandscapeScale scale;
	final Tryb tryb;
	/** {@code local} skali: mnożnik odległości „·k”. */
	final double k;
	/** Mnożnik szerokości koryt skali: W / chan to szerokość w skali 1:1. */
	final double chan;
	final boolean rozgrywka;
	/** Drgania granic stref (fala 30–60 m·k). */
	final Noise granice;
	/** Płaty: nymfeidy, szuwar w płatach, luki w łęgu, zastoiska. */
	final Noise platy;
	/** Przejścia pięter górskich (fala 150 m·k). */
	final Noise pietra;
	/** Warianty w obrębie siedlisk (buczyna niżowa, prześwity wrzosowisk), fala 1,5 km·k. */
	final Noise warianty;
	final Lesistosc lesistosc;

	public Klasyfikator(long seed, LandscapeScale scale, Tryb tryb) {
		this.scale = scale;
		this.tryb = tryb;
		this.k = scale.local();
		this.chan = scale.channel();
		this.rozgrywka = scale != LandscapeScale.REALISTIC;
		Noise root = new Noise(seed);
		this.granice = root.derive("habitat.granice");
		this.platy = root.derive("habitat.platy");
		this.pietra = root.derive("habitat.pietra");
		this.warianty = root.derive("habitat.warianty");
		this.lesistosc = new Lesistosc(root.derive("habitat.lesistosc"), k);
	}

	public Tryb tryb() {
		return tryb;
	}

	public LandscapeScale scale() {
		return scale;
	}

	/** Kod siedliska kolumny w punkcie (x, z) z jej próbką {@code s}. */
	public int klasyfikuj(ColumnSample s, double x, double z) {
		Kolumna c = new Kolumna(this, s, x, z);
		int w = Woda.klasyfikuj(c);
		if (w == Wynik.BRAK) {
			w = Wynik.dalej(w, Wybrzeze.klasyfikuj(c));
			if (Wynik.biom(w) == null) {
				w = Wynik.dalej(w, StrefyNadwodne.klasyfikuj(c));
			}
			if (Wynik.biom(w) == null) {
				w = Wynik.dalej(w, Pietra.klasyfikuj(c));
			}
			if (Wynik.biom(w) == null) {
				w = Wynik.dalej(w, strefowe(c));
			}
		}
		Biom biom = Wynik.biom(w);
		Strefa strefa = Wynik.strefa(w);
		Zespol zespol = Wynik.zespol(w);
		Trofia t = c.trofia();
		Wilgotnosc wil = c.wilgotnosc();
		Stl stl = biom.lesny() ? Stl.lasu(biom, t, wil) : Stl.BRAK;
		if (tryb == Tryb.D && biom.lesny() && !lesistosc.las(c, biom, stl)) {
			Biom otwarty = lesistosc.nielesny(c, biom, stl);
			biom = otwarty;
		}
		int flagi = biom.lesny() || stl != Stl.BRAK ? Zasiegi.flagi(c, stl) : 0;
		return Siedlisko.pack(biom, strefa, stl, zespol, Pokrycie.dla(biom), flagi, Gleba.dla(biom, strefa));
	}

	/** Siedlisko kolumny jako rekord (komendy, podgląd, testy). */
	public Siedlisko siedlisko(ColumnSample s, double x, double z) {
		return Siedlisko.of(klasyfikuj(s, x, z));
	}

	/** Głębokość wody gruntowej w kolumnie (m), jak w klasyfikacji (§3.3). */
	public double dgw(ColumnSample s, double x, double z) {
		return new Kolumna(this, s, x, z).dgw();
	}

	/** Trofia kolumny, jak w klasyfikacji (§3.3). */
	public Trofia trofia(ColumnSample s, double x, double z) {
		return new Kolumna(this, s, x, z).trofia();
	}

	// ------------------------------------------------------------------ siedliska strefowe (§2.1, krok 5)

	/** Biom strefowy z trofii, wilgotności i form (§2.1) z regułami zasięgu (§9). */
	static int strefowe(Kolumna c) {
		Trofia t = c.trofia();
		Wilgotnosc w = c.wilgotnosc();
		double h = c.H;
		Biom b;
		Zespol z = Zespol.TYPOWY;
		if (w == Wilgotnosc.BAGIENNA) {
			// Bór bagienny do 400 m, ols do 500 m; wyżej siedliska wilgotne.
			if (t == Trofia.B || t == Trofia.BM) {
				b = h < Kalibracja.H_BOR_BAGIENNY ? Biom.BOR_BAGIENNY : Biom.BOR_WILGOTNY;
			} else {
				b = h < Kalibracja.H_OLS ? Biom.OLS : t == Trofia.L ? Biom.GRAD : Biom.LAS_MIESZANY;
			}
		} else if (t == Trofia.B) {
			b = switch (w) {
				case SUCHA -> h < Kalibracja.H_BOR_SUCHY ? Biom.BOR_SUCHY : Biom.BOR_SWIEZY;
				case SWIEZA -> h < Kalibracja.H_BOR_SWIEZY ? Biom.BOR_SWIEZY : Biom.BOR_MIESZANY;
				default -> Biom.BOR_WILGOTNY;
			};
			if (b == Biom.BOR_SUCHY && c.kl.tryb == Tryb.N
					&& c.platQ(8, Kalibracja.FALA_PRZESWITOW) > 1 - Kalibracja.WRZOSOWISKO_N) {
				// Prześwity na wydmach (tryb N, ≤ 5% sandru).
				b = Biom.WRZOSOWISKO;
			}
		} else if (t == Trofia.BM) {
			if (w == Wilgotnosc.WILGOTNA) {
				b = Biom.BOR_WILGOTNY;
			} else {
				b = jedlina(c, t) ? Biom.JEDLINA_WYZYNNA : Biom.BOR_MIESZANY;
			}
		} else {
			// LM i L: las mieszany albo grąd, buczyna niżowa w zasięgu buka, jedlina przy P ≥ 0,5.
			if (jedlina(c, t)) {
				b = Biom.JEDLINA_WYZYNNA;
			} else if (buczyna(c, w)) {
				b = Biom.BUCZYNA_NIZINNA;
				z = t == Trofia.LM ? Zespol.KWASNY : Zespol.TYPOWY;
			} else {
				b = t == Trofia.L ? Biom.GRAD : Biom.LAS_MIESZANY;
			}
			if (b == Biom.GRAD && c.zboczeDoliny()) {
				z = Zespol.ZBOCZOWY;
			}
		}
		return Wynik.of(b, Strefa.BRAK, z);
	}

	/** Jedlina wyżynna: P ≥ 0,5, jodła w zasięgu, trofia BM, LM lub L (L tylko na stokach N), 250–650 m. */
	static boolean jedlina(Kolumna c, Trofia t) {
		if (c.P < Kalibracja.P_JEDLINA || c.H < Kalibracja.H_JEDLINA_OD || c.H > Kalibracja.H_JEDLINA_DO
				|| !Zasiegi.jodla(c.P, c.H, null) || t == Trofia.B) {
			return false;
		}
		return t != Trofia.L || c.ekspozycjaN();
	}

	/** Buczyna niżowa: buk w zasięgu, świeże i zdrenowane, H &lt; 350 m; udział rośnie z O. */
	static boolean buczyna(Kolumna c, Wilgotnosc w) {
		if (w != Wilgotnosc.SWIEZA && w != Wilgotnosc.SUCHA || c.H >= Kalibracja.H_BUCZYNA_NIZINNA
				|| !Zasiegi.buk(c.O, c.P)) {
			return false;
		}
		if (!(c.wkl() >= 0 || c.nach > Kalibracja.NACH_BUCZYNA)) {
			return false;
		}
		double udzial = Kalibracja.BUCZYNA_MAX
				* Noise.smoothstep(Kalibracja.O_BUCZYNA_OD, Kalibracja.O_BUCZYNA_DO, Math.max(c.O, c.P));
		return c.wariant(1) < udzial;
	}

	// ------------------------------------------------------------------ wynik częściowy kroków

	/**
	 * Wynik częściowy kroku klasyfikacji w jednym {@code int}: biom + 1 (0 = brak, czyli „dalej”), strefa
	 * i zespół. Bez obiektów, bo klasyfikacja idzie dla każdej kolumny.
	 */
	static final class Wynik {
		static final int BRAK = 0;

		private Wynik() {
		}

		static int of(Biom b, Strefa s, Zespol z) {
			return (b == null ? 0 : b.ordinal() + 1) | s.ordinal() << 8 | z.ordinal() << 16;
		}

		static int strefa(Strefa s) {
			return of(null, s, Zespol.TYPOWY);
		}

		static Biom biom(int w) {
			int b = w & 255;
			return b == 0 ? null : Biom.of(b - 1);
		}

		static Strefa strefa(int w) {
			return Strefa.of(w >>> 8 & 255);
		}

		static Zespol zespol(int w) {
			return Zespol.of(w >>> 16 & 255);
		}

		/**
		 * Łączy wynik wcześniejszych kroków z wynikiem kolejnego: strefa i zespół wcześniejszego kroku
		 * mają pierwszeństwo, biom bierze się z kolejnego.
		 */
		static int dalej(int wczesniej, int kolejny) {
			Strefa s = strefa(wczesniej) != Strefa.BRAK ? strefa(wczesniej) : strefa(kolejny);
			Zespol z = zespol(wczesniej) != Zespol.TYPOWY ? zespol(wczesniej) : zespol(kolejny);
			return of(biom(kolejny), s, z);
		}
	}

	// ------------------------------------------------------------------ wielkości pochodne kolumny

	/**
	 * Wielkości pochodne jednej kolumny (§3.3), liczone raz i leniwie. Obiekt lokalny dla jednego wywołania.
	 */
	static final class Kolumna {
		final Klasyfikator kl;
		final ColumnSample s;
		final ColumnSample.Teren t;
		final ColumnSample.Wody w;
		final double x;
		final double z;
		/** Wysokość gruntu (m n.p.m.). */
		final double H;
		final double k;
		/** Wagi typów z pasem nadmorskim (suma 1). */
		final double wS;
		final double wW;
		final double wR;
		final double wPg;
		final double wBs;
		final double wPob;
		/** Waga nizin i waga gór (POGÓRZE + BESKIDY). */
		final double wN;
		final double wG;
		final double O;
		final double P;
		/** Nachylenie w stopniach do porównań z progami: w GAMEPLAY przeliczone na bloki (§3.2). */
		final double nach;
		private Trofia trofia;
		private double dgw = Double.NaN;
		private Wilgotnosc wilgotnosc;
		/** Dno doliny według terenu (0 nie, 1 tak, −1 nie liczone). */
		private int dno = -1;
		private double drganie = Double.NaN;
		private double drganieU = Double.NaN;

		Kolumna(Klasyfikator kl, ColumnSample s, double x, double z) {
			this.kl = kl;
			this.s = s;
			this.t = s.teren();
			this.w = s.wody();
			this.x = x;
			this.z = z;
			this.H = s.surface();
			this.k = kl.k;
			double pob = Math.clamp(t.wPobrzeze(), 0.0, 1.0);
			double f = 1 - pob;
			this.wPob = pob;
			this.wS = f * t.wSandr();
			this.wW = f * t.wWysoczyzna();
			this.wR = f * t.wRownina();
			this.wPg = f * t.wPogorze();
			this.wBs = f * t.wBeskidy();
			this.wN = wS + wW + wR + wPob;
			this.wG = wPg + wBs;
			this.O = s.region().oceanicznosc();
			this.P = s.region().podgorskosc();
			double n = t.nach();
			if (kl.rozgrywka && n > 0) {
				double tan = Math.tan(Math.toRadians(n)) * Kalibracja.blokiNaMetrRozgrywki(t.sBar());
				n = Math.toDegrees(Math.atan(tan));
			}
			this.nach = n;
		}

		Trofia trofia() {
			if (trofia == null) {
				trofia = Trofia.oblicz(this);
			}
			return trofia;
		}

		double dgw() {
			if (Double.isNaN(dgw)) {
				dgw = Wilgotnosc.dgw(this);
			}
			return dgw;
		}

		Wilgotnosc wilgotnosc() {
			if (wilgotnosc == null) {
				wilgotnosc = Wilgotnosc.klasa(this);
			}
			return wilgotnosc;
		}

		/** Mnożnik szerokości stref 1 ± 0,2 (szum o fali 45 m·k). */
		double drganie() {
			if (Double.isNaN(drganie)) {
				drganie = 1 + Kalibracja.DRGANIE_SZEROKOSCI * kl.granice.at(x, z, Kalibracja.FALA_GRANIC * k);
			}
			return drganie;
		}

		/**
		 * Dno doliny według terenu: grunt najwyżej {@link Kalibracja#DNO_H} nad lustrem najbliższego koryta, a do
		 * tego flaga {@code wDnie} modelu albo odległość od koryta w półszerokości dna. Flaga modelu pochodzi
		 * z doliny dominującej, więc bywa ucięta prostą linią i obejmuje grunt wysoko nad innym, bliższym
		 * korytem. Gdy grunt leży mniej niż 1,2 m nad lustrem najbliższego koryta (dopływ schodzący bystrzem
		 * do dna większej doliny), rozstrzyga sama flaga.
		 */
		boolean dno() {
			if (dno < 0) {
				boolean d = w.wDnie();
				if (w.rzad() > 0 && !s.hasWater() && !Double.isNaN(w.poziomKoryta())) {
					double hl = H - w.poziomKoryta();
					if (hl >= 1.15) {
						double zasieg = Math.max(Kalibracja.DNO_MIN_K * k, Double.isNaN(w.polSzerDna()) ? 0 : w.polSzerDna());
						d = hl <= Kalibracja.DNO_H && (d || w.odlKoryta() <= zasieg);
					}
				}
				dno = d ? 1 : 0;
			}
			return dno == 1;
		}

		/** Położenie w dnie 0–1: z modelu, a w dnie według terenu poza flagą {@code wDnie} z d / półszerokość. */
		double u() {
			if (w.wDnie() && !Double.isNaN(w.u())) {
				return w.u();
			}
			if (!dno()) {
				return Double.NaN;
			}
			double half = w.polSzerDna();
			return Double.isNaN(half) || half <= 0 ? 0 : Math.clamp(Math.max(0, w.odlKoryta()) / half, 0.0, 1.0);
		}

		/** Położenie w dnie z drganiem ±0,05. */
		double uDrgane() {
			if (Double.isNaN(drganieU)) {
				drganieU = u() + Kalibracja.DRGANIE_U * kl.granice.at(x + 7_777, z - 3_333, Kalibracja.FALA_GRANIC * k);
			}
			return drganieU;
		}

		/** Kwantyl szumu płatów o zadanej fali (m·k), w [0, 1]. */
		double platQ(int warstwa, double fala) {
			return LandscapeModel.noiseQuantile(kl.platy.at(x + 1_013.0 * warstwa, z - 517.0 * warstwa, fala * k));
		}

		/** Szum płatów w [−1, 1] (fala 35 m·k); {@code warstwa} rozdziela niezależne decyzje. */
		double plat(int warstwa) {
			return kl.platy.at(x + 1_013.0 * warstwa, z - 517.0 * warstwa, Kalibracja.FALA_PLATOW * k);
		}

		/** Kwantyl płatów w [0, 1] (rozkład jednostajny). */
		double platQ(int warstwa) {
			return LandscapeModel.noiseQuantile(plat(warstwa));
		}

		/** Kwantyl szumu wariantów w [0, 1] (fala 1,5 km·k). */
		double wariant(int warstwa) {
			return LandscapeModel.noiseQuantile(
					kl.warianty.at(x + 2_029.0 * warstwa, z + 911.0 * warstwa, Kalibracja.FALA_WARIANTOW * k));
		}

		/** cos(ekspozycja − 180°): 1 stok S, −1 stok N; 0 na płaskim i poniżej 5°. */
		double ekspozycja() {
			double e = t.eksp();
			if (Double.isNaN(e) || nach < 5.0) {
				return 0;
			}
			return Math.cos(Math.toRadians(e - 180.0));
		}

		boolean ekspozycjaN() {
			return ekspozycja() < 0;
		}

		/**
		 * Wypukłość terenu w skali ok. 100 m·k: teren przed wcięciem dolin minus teren wygładzony (m). Zastępuje
		 * {@code teren.wyp} w regułach biomów, bo {@code wyp} ma składowe krótkofalowe (falowanie sandru, Z9).
		 */
		double wkl() {
			return t.rawSurface() - t.sBar();
		}

		/** Zbocze doliny: w zasięgu cieku, poza dnem, teren wcięty poniżej terenu przed doliną. */
		boolean zboczeDoliny() {
			return w.rzad() > 0 && !dno() && !s.hasWater() && H < t.rawSurface() - 2;
		}

		/**
		 * Wysięk u podnóża zbocza doliny: w zasięgu cieku, poza dnem, siedlisko bagienne (DGW ≤ 0,5, w pasie
		 * 0,5–0,8 według szumu) przy żyznym podłożu (LM, L) poniżej 600 m. Bez warunku wcięcia (zbocze), bo inaczej skraj dna dostawał wąski pas olsu strefowego.
		 */
		boolean wysiek() {
			Trofia tr = trofia();
			return w.rzad() > 0 && !dno() && !s.hasWater() && H < Kalibracja.H_LEG_OLJ && (tr == Trofia.LM || tr == Trofia.L)
					&& wilgotnosc() == Wilgotnosc.BAGIENNA;
		}

		/** Szerokość koryta w skali 1:1 (m). */
		double wr() {
			return w.szerKoryta() / kl.chan;
		}

		/**
		 * Wysokość nad lustrem najbliższego koryta minus 1 m (h, §3.3). NaN bez cieku i wtedy, gdy grunt leży
		 * niżej niż 1,2 m nad tym lustrem: dno modelu leży co najmniej 1,2 m nad lustrem własnego koryta, więc
		 * najbliższe koryto jest wtedy inne (dopływ schodzący bystrzem do dna większej doliny).
		 */
		double hKoryta() {
			double h = H - w.poziomKoryta() - 1;
			return h < 0.15 ? Double.NaN : h;
		}

		/** Źródlisko: forma ZRODLO, płatami do 40 m·k od koryta. */
		boolean zrodlisko() {
			return t.ma(Landform.ZRODLO)
					&& w.odlKoryta() <= Kalibracja.ZRODLISKO_K * k * drganie() && platQ(9) < Kalibracja.ZRODLISKO_UDZIAL;
		}

		boolean piaszczyste() {
			Trofia tr = trofia();
			return tr == Trofia.B || tr == Trofia.BM;
		}
	}
}
