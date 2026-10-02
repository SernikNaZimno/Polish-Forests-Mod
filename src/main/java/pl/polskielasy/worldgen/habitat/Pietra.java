package pl.polskielasy.worldgen.habitat;

import pl.polskielasy.worldgen.landscape.Landform;

/**
 * Piętra roślinności Beskidów i Pogórza (§5.1, raport ekologii §3, wzorzec Babiej Góry). Jedyne miejsce
 * z wysokościami pięter, także z nominalną granicą regla górnego 1150 m, z której korzystają opis terenu
 * ({@code LandscapeModel.describe}), źródło biomów M1 i komendy.
 *
 * <pre>
 * dT   = 40·szum(λ 150k)                                   // m
 * eks  = nach ≥ 5° ? 50·cos(eksp − 180°) : 0               // stok S +50 m, stok N −50 m
 * regielDolny = 550  + eks + dT
 * regielGorny = 1150 + eks + dT
 * granicaLasu = 1390 + eks − 60·[GRZBIET i nach &lt; 15°] + dT
 * progHali    = 1650 + eks + dT
 * duzyMasyw   = szczyt w promieniu 3 km > 1470 m (E12), z pola teren.szczyt
 * </pre>
 *
 * Nachylenie w skali rozgrywki jest przeliczone na bloki ({@link Klasyfikator.Kolumna#nach}).
 */
public final class Pietra {
	private Pietra() {
	}

	/** Nominalne wysokości pięter (m n.p.m.), przed korektą ekspozycji i szumu. */
	public static final double REGIEL_DOLNY = 550;
	public static final double REGIEL_GORNY = 1_150;
	public static final double GRANICA_LASU = 1_390;
	public static final double PROG_HALI = 1_650;
	/** Najwyższy szczyt w promieniu ok. 3 km, od którego masyw ma kosodrzewinę i halę (E12). */
	public static final double DUZY_MASYW = 1_470;
	/**
	 * Próg na polu {@code teren.szczyt} (wysokość grzbietów okolicy przy profilach dolin równych 1), dobrany
	 * tak, by duży masyw był tylko tam, gdzie szczyt w promieniu 3 km przekracza {@link #DUZY_MASYW}: pole
	 * zawyża rzeczywisty szczyt o 36–175 m (67 kolumn powyżej 1200 m wokół najwyższego masywu ziarna testowego,
	 * docs/03-m2-biomy.md, Odstępstwo S4), więc próg to 1470 + 180 m.
	 */
	public static final double PROG_SZCZYTU = DUZY_MASYW + 180;
	/** Przesunięcie progu przez ekspozycję (m) i amplituda szumu przejść (m, fala 150 m·k). */
	public static final double EKSPOZYCJA = 50;
	public static final double SZUM = 40;
	public static final double FALA_SZUMU = 150;
	/** Obniżenie granicy lasu na grzbietach wystawionych na wiatr (m) przy nachyleniu poniżej 15°. */
	public static final double GRZBIET_WIATR = 60;
	/** Pas karłowych świerków pod granicą lasu (m). */
	public static final double PAS_GRANICY = 60;
	/** Największe możliwe obniżenie progu (ekspozycja i szum), do pomijania szumu nisko. */
	public static final double MAKS_OBNIZENIE = EKSPOZYCJA + SZUM;
	/** Wyżej niż tyle w reglu dolnym stoki N mają Abieti-Piceetum (m). */
	static final double ABIETI_N = 900;
	/** Inwersja: dna dolin powyżej tej wysokości przy półszerokości dna > 30 m·k (m). */
	static final double ABIETI_DNO = 700;
	static final double ABIETI_DNO_K = 30;
	/** Pogórze: buczyna karpacka na stokach N powyżej (m). */
	static final double POGORZE_BUCZYNA = 450;
	/** Grąd na pogórzu na siedliskach L poniżej (m). */
	static final double POGORZE_GRAD = 400;
	/** Jaworzyna: nachylenie ponad (°) na stokach N–E (azymut 0–90°), w dolnej części zbocza. */
	static final double JAWORZYNA_NACH = 30;
	/** Ziołorośla w żlebach regla górnego: profil fliszu poniżej. */
	static final double ZLEB_GRZBIET = 0.15;

	/** Nominalny regiel górny (bez korekty): opis terenu, komendy, źródło biomów M1. */
	public static boolean reglGorny(double metry) {
		return metry >= REGIEL_GORNY;
	}

	/** Przesunięcie progów: ekspozycja i szum (m). */
	static double korekta(Klasyfikator.Kolumna c) {
		double dT = SZUM * c.kl.pietra.at(c.x, c.z, FALA_SZUMU * c.k);
		return EKSPOZYCJA * c.ekspozycja() + dT;
	}

	/** Górna granica regla dolnego w kolumnie (m), z ekspozycją i szumem. */
	static double regielGorny(Klasyfikator.Kolumna c) {
		return REGIEL_GORNY + korekta(c);
	}

	/** Duży masyw: szczyt w promieniu ok. 3 km ponad 1470 m (pole szczytu ponad {@link #PROG_SZCZYTU}). */
	static boolean duzyMasyw(Klasyfikator.Kolumna c) {
		return c.t.szczyt() >= PROG_SZCZYTU;
	}

	/** Krok 4 klasyfikatora: piętra w pasie górskim (wP + wB > 0,5); {@code Wynik.BRAK} poza nim i nisko. */
	static int klasyfikuj(Klasyfikator.Kolumna c) {
		if (c.wG <= 0.5) {
			return Klasyfikator.Wynik.BRAK;
		}
		double h = c.H;
		double kor = korekta(c);
		boolean duzy = duzyMasyw(c);
		boolean grzbiet = c.t.ma(Landform.GRZBIET);
		if (duzy && h >= PROG_HALI + kor) {
			return Klasyfikator.Wynik.of(Biom.HALA, Strefa.BRAK, Zespol.TYPOWY);
		}
		double granica = GRANICA_LASU + kor - (grzbiet && c.nach < 15 ? GRZBIET_WIATR : 0);
		if (duzy && h >= granica) {
			return Klasyfikator.Wynik.of(Biom.KOSODRZEWINA, Strefa.BRAK, Zespol.TYPOWY);
		}
		if (h >= REGIEL_GORNY + kor) {
			Strefa s = duzy && h >= granica - PAS_GRANICY ? Strefa.GRANICA_LASU : Strefa.BRAK;
			double p0 = c.t.grzbiet();
			Zespol z = !Double.isNaN(p0) && p0 < ZLEB_GRZBIET ? Zespol.ZIOLOROSLA_ZLEBOWE : Zespol.TYPOWY;
			return Klasyfikator.Wynik.of(Biom.SWIERCZYNA_GORSKA, s, z);
		}
		Trofia t = c.trofia();
		boolean uboga = t == Trofia.B || t == Trofia.BM;
		double eks = c.ekspozycja();
		if (h >= REGIEL_DOLNY + kor) {
			boolean abieti = grzbiet && uboga || eks < 0 && h > ABIETI_N
					|| c.dno() && h > ABIETI_DNO && c.w.polSzerDna() > ABIETI_DNO_K * c.k;
			if (abieti) {
				return Klasyfikator.Wynik.of(Biom.SWIERCZYNA_GORSKA, Strefa.BRAK, Zespol.ABIETI_PICEETUM);
			}
			return Klasyfikator.Wynik.of(Biom.BUCZYNA_GORSKA, Strefa.BRAK, zespolBuczyny(c, uboga));
		}
		if (c.wPg > c.wBs && eks < 0 && h > POGORZE_BUCZYNA) {
			return Klasyfikator.Wynik.of(Biom.BUCZYNA_GORSKA, Strefa.BRAK, zespolBuczyny(c, uboga));
		}
		if (t == Trofia.L && h < POGORZE_GRAD) {
			return Klasyfikator.Wynik.of(Biom.GRAD, Strefa.BRAK, Zespol.TYPOWY);
		}
		if (c.P >= Kalibracja.P_JEDLINA && Zasiegi.jodla(c.P, h, null) && c.wilgotnosc() != Wilgotnosc.BAGIENNA) {
			return Klasyfikator.Wynik.of(Biom.JEDLINA_WYZYNNA, Strefa.BRAK, Zespol.TYPOWY);
		}
		return Klasyfikator.Wynik.BRAK;
	}

	/** Jaworzyna na stromych stokach N–E w dolnej części zbocza, odmiana kwaśna na ubogich siedliskach. */
	private static Zespol zespolBuczyny(Klasyfikator.Kolumna c, boolean uboga) {
		double e = c.t.eksp();
		double p0 = c.t.grzbiet();
		if (c.nach > JAWORZYNA_NACH && !Double.isNaN(e) && e <= 90 && !Double.isNaN(p0) && p0 < 0.5) {
			return Zespol.JAWORZYNA;
		}
		return uboga ? Zespol.KWASNY : Zespol.TYPOWY;
	}
}
