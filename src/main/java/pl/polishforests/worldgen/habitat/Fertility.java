package pl.polishforests.worldgen.habitat;

import pl.polishforests.worldgen.landscape.Landform;
import pl.polishforests.worldgen.landscape.Substrate;

/**
 * Żyzność siedliska (§3.3): B bory, BM bory mieszane, LM lasy mieszane, L lasy. Bogactwo r = 1 − piask
 * (kwantyl, rozkład jednostajny) tniemy progami skumulowanymi udziałów zmieszanych wagami typów, więc
 * semantyka jest ta sama we wszystkich typach: mniej piasku to żyźniej, a udziały w każdym typie
 * wynoszą tyle, ile podaje {@link Calibration}.
 */
public enum Fertility {
	OLIGOTROPHIC, OLIGO_MESOTROPHIC, MESOTROPHIC, EUTROPHIC;

	static Fertility compute(HabitatClassifier.Column c) {
		Substrate sub = c.substrate;
		// Wydmy śródlądowe i nadmorskie (na piasku): bory. Dalej pas nadmorski działa przez udziały POBRZEŻA
		// (70% B) ważone wPobrzeze, więc granica nie jest prostą linią wzdłuż brzegu.
		boolean sandSubstrate = sub == Substrate.SAND || sub == Substrate.BEACH_SAND;
		if (c.t.has(Landform.INLAND_DUNES) || c.t.has(Landform.COASTAL_DUNES) && sandSubstrate || sub == Substrate.BEACH_SAND) {
			return OLIGOTROPHIC;
		}
		// Mady w dnach dolin: L, poza dolinami w krainach piasków (tam dno ma trofię regionu).
		if (sub == Substrate.ALLUVIUM && c.wOutwashPlain + c.wCoastland < Calibration.ALLUVIUM_ON_SAND) {
			return EUTROPHIC;
		}
		double sandiness = c.t.sandiness();
		double r = Double.isNaN(sandiness) ? 0.5 : 1 - sandiness;
		double cum = 0;
		for (int i = 0; i < 3; i++) {
			cum += share(c, i) / 100.0;
			if (r < cum) {
				return values()[i];
			}
		}
		return EUTROPHIC;
	}

	/**
	 * Udział klasy {@code i} (0 B … 3 L) w %, zmieszany wagami typów. Pas nadmorski na glinie (wysoki brzeg
	 * z klifem; na brzegu wydmowym podłoże siedliska to piasek) ma udziały wysoczyzny, a nie piasków pobrzeża.
	 */
	private static double share(HabitatClassifier.Column c, int i) {
		double[] coastland = c.substrate == Substrate.GLACIAL_TILL ? Calibration.FERTILITY_MORAINE_PLATEAU : Calibration.FERTILITY_COASTLAND;
		return c.wOutwashPlain * Calibration.FERTILITY_OUTWASH_PLAIN[i] + c.wMorainePlateau * Calibration.FERTILITY_MORAINE_PLATEAU[i]
				+ c.wOldGlacialPlain * Calibration.FERTILITY_OLD_GLACIAL_PLAIN[i] + c.wCoastland * coastland[i]
				+ c.wFoothills * Calibration.FERTILITY_FOOTHILLS[i] + c.wBeskids * Calibration.FERTILITY_BESKIDS[i];
	}
}
