package pl.polskielasy.worldgen.habitat;

import pl.polskielasy.worldgen.landscape.Landform;
import pl.polskielasy.worldgen.landscape.Substrate;

/**
 * Żyzność siedliska (§3.3): B bory, BM bory mieszane, LM lasy mieszane, L lasy. Bogactwo r = 1 − piask
 * (kwantyl, rozkład jednostajny) tniemy progami skumulowanymi udziałów zmieszanych wagami typów, więc
 * semantyka jest ta sama we wszystkich typach: mniej piasku to żyźniej, a udziały w każdym typie
 * wynoszą tyle, ile podaje {@link Kalibracja}.
 */
public enum Trofia {
	B, BM, LM, L;

	static Trofia oblicz(Klasyfikator.Kolumna c) {
		Substrate sub = c.podloze;
		// Wydmy śródlądowe i nadmorskie (na piasku): bory. Dalej pas nadmorski działa przez udziały POBRZEŻA
		// (70% B) ważone wPobrzeze, więc granica nie jest prostą linią wzdłuż brzegu.
		boolean piasek = sub == Substrate.SAND || sub == Substrate.BEACH_SAND;
		if (c.t.ma(Landform.WYDMY) || c.t.ma(Landform.WYDMY_NADMORSKIE) && piasek || sub == Substrate.BEACH_SAND) {
			return B;
		}
		// Mady w dnach dolin: L, poza dolinami w krainach piasków (tam dno ma trofię regionu).
		if (sub == Substrate.ALLUVIUM && c.wS + c.wPob < Kalibracja.MADY_NA_PIASKU) {
			return L;
		}
		double piask = c.t.piask();
		double r = Double.isNaN(piask) ? 0.5 : 1 - piask;
		double cum = 0;
		for (int i = 0; i < 3; i++) {
			cum += udzial(c, i) / 100.0;
			if (r < cum) {
				return values()[i];
			}
		}
		return L;
	}

	/**
	 * Udział klasy {@code i} (0 B … 3 L) w %, zmieszany wagami typów. Pas nadmorski na glinie (wysoki brzeg
	 * z klifem; na brzegu wydmowym podłoże siedliska to piasek) ma udziały wysoczyzny, a nie piasków pobrzeża.
	 */
	private static double udzial(Klasyfikator.Kolumna c, int i) {
		double[] pob = c.podloze == Substrate.GLACIAL_TILL ? Kalibracja.TROFIA_WYSOCZYZNA : Kalibracja.TROFIA_POBRZEZE;
		return c.wS * Kalibracja.TROFIA_SANDR[i] + c.wW * Kalibracja.TROFIA_WYSOCZYZNA[i]
				+ c.wR * Kalibracja.TROFIA_ROWNINA[i] + c.wPob * pob[i]
				+ c.wPg * Kalibracja.TROFIA_POGORZE[i] + c.wBs * Kalibracja.TROFIA_BESKIDY[i];
	}
}
