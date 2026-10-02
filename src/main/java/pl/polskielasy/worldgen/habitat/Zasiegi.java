package pl.polskielasy.worldgen.habitat;

/**
 * Zasięgi gatunków w świecie proceduralnym (§9, raport ekologii §5.1): progi na polach regionalnych
 * O (oceaniczność) i P (podgórskość) z {@code RegionalField}. Na poziomie biomu i flag siedliska progi
 * są twarde; rampa ekotonu (±0,05, szum 2 km·k) mnoży wagi gatunków dopiero w paletach drzewostanu (M3).
 * Między zasięgiem buka (O ≥ 0,4) a naturalnego świerka (O &lt; 0,3) przy P &lt; 0,4 leży pas bez buka
 * i świerka, jak na Mazowszu.
 */
public final class Zasiegi {
	private Zasiegi() {
	}

	/** Buk: O ≥ 0,40 lub P ≥ 0,40 (bez warunków wysokości i siedliska). */
	public static boolean buk(double o, double p) {
		return o >= Kalibracja.BUK_O || p >= Kalibracja.BUK_P;
	}

	/** Buk w siedlisku: zasięg, H do górnej granicy regla dolnego, nie na Bs, Bb, Ol ani Lł. */
	public static boolean buk(double o, double p, double h, double regielGorny, Stl stl) {
		return buk(o, p) && h < regielGorny && stl != Stl.BS && stl != Stl.BB && stl != Stl.OL && stl != Stl.LL;
	}

	/** Jodła: P ≥ 0,50, H ≤ 1250 m, nie na Bs, Bb, Ol ({@code stl} null: bez warunku siedliska). */
	public static boolean jodla(double p, double h, Stl stl) {
		return p >= Kalibracja.JODLA_P && h <= Kalibracja.JODLA_H
				&& (stl == null || stl != Stl.BS && stl != Stl.BB && stl != Stl.OL);
	}

	/** Świerk naturalny: O &lt; 0,30 lub P ≥ 0,50. */
	public static boolean swierk(double o, double p) {
		return o < Kalibracja.SWIERK_O || p >= Kalibracja.SWIERK_P;
	}

	/** Grab: H ≤ 600 m (stoki S ≤ 700 m), tylko siedliska L i LM ({@code stoki S}: cos ekspozycji > 0). */
	public static boolean grab(double h, double ekspozycja, Trofia t) {
		double max = ekspozycja > 0 ? Kalibracja.GRAB_H_S : Kalibracja.GRAB_H;
		return h <= max && (t == Trofia.L || t == Trofia.LM);
	}

	/** Olsza szara: P ≥ 0,50 lub O &lt; 0,30. */
	public static boolean olszaSzara(double o, double p) {
		return p >= Kalibracja.OLSZA_SZARA_P || o < Kalibracja.OLSZA_SZARA_O;
	}

	/** Dąb bezszypułkowy: O ≥ 0,35 lub P ≥ 0,40; H ≤ 600 m (użycie od M3). */
	public static boolean dabBezszypulkowy(double o, double p, double h) {
		return (o >= Kalibracja.DAB_BEZSZYP_O || p >= Kalibracja.DAB_BEZSZYP_P) && h <= Kalibracja.DAB_BEZSZYP_H;
	}

	/** Modrzew: 0,50 ≤ P &lt; 0,80 i H 250–650 m (domieszka w jedlinie; użycie od M3). */
	public static boolean modrzew(double p, double h) {
		return p >= Kalibracja.MODRZEW_P_OD && p < Kalibracja.MODRZEW_P_DO && h >= Kalibracja.MODRZEW_H_OD
				&& h <= Kalibracja.MODRZEW_H_DO;
	}

	/** Bluszcz: O ≥ 0,45 lub P ≥ 0,50 (użycie od M4). */
	public static boolean bluszcz(double o, double p) {
		return o >= Kalibracja.BLUSZCZ_O || p >= Kalibracja.BLUSZCZ_P;
	}

	/** Cis: O ≥ 0,6 lub P ≥ 0,6 (udział ≤ 0,1%, użycie od M3). */
	public static boolean cis(double o, double p) {
		return o >= Kalibracja.CIS_O || p >= Kalibracja.CIS_P;
	}

	/** Flagi BUK, JODŁA, ŚWIERK i GRAB kodu siedliska dla kolumny i jej STL. */
	static int flagi(Klasyfikator.Kolumna c, Stl stl) {
		int f = 0;
		// Granica regla liczy szum tylko wysoko (poniżej ok. 1060 m nie może jej przekroczyć).
		double regiel = c.H < Pietra.REGIEL_GORNY - Pietra.MAKS_OBNIZENIE ? Double.POSITIVE_INFINITY : Pietra.regielGorny(c);
		if (buk(c.O, c.P, c.H, regiel, stl)) {
			f |= Gatunek.FLAGA_BUK;
		}
		if (jodla(c.P, c.H, stl)) {
			f |= Gatunek.FLAGA_JODLA;
		}
		if (swierk(c.O, c.P)) {
			f |= Gatunek.FLAGA_SWIERK;
		}
		if (grab(c.H, c.ekspozycja(), c.trofia())) {
			f |= Gatunek.FLAGA_GRAB;
		}
		return f;
	}
}
