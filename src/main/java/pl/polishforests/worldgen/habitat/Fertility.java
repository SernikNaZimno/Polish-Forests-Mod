package pl.polishforests.worldgen.habitat;

import pl.polishforests.worldgen.landscape.Landform;
import pl.polishforests.worldgen.landscape.Substrate;

/**
 * Site fertility (§3.3): B coniferous, BM mixed coniferous, LM mixed broadleaved, L broadleaved. Richness
 * r = 1 − sandiness (a quantile, uniformly distributed) is cut by cumulative thresholds of the shares blended
 * with the type weights, so the semantics are the same in every type: less sand means more fertile, and the
 * shares in each type are exactly as given in {@link Calibration}.
 */
public enum Fertility {
	OLIGOTROPHIC, OLIGO_MESOTROPHIC, MESOTROPHIC, EUTROPHIC;

	static Fertility compute(HabitatClassifier.Column c) {
		Substrate sub = c.substrate;
		// Inland and coastal dunes (on sand): coniferous. Beyond them the coastal belt works through the COASTLAND
		// shares (70% B) weighted by wCoastland, so the boundary is not a straight line along the shore.
		boolean sandSubstrate = sub == Substrate.SAND || sub == Substrate.BEACH_SAND;
		if (c.t.has(Landform.INLAND_DUNES) || c.t.has(Landform.COASTAL_DUNES) && sandSubstrate || sub == Substrate.BEACH_SAND) {
			return OLIGOTROPHIC;
		}
		// Alluvium on valley floors: L, except for valleys in sandy regions (there the floor gets the region's fertility).
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
	 * Share of class {@code i} (0 B … 3 L) in %, blended with the type weights. The coastal belt on till (a high
	 * shore with a cliff; on a dune shore the habitat substrate is sand) gets the moraine plateau shares, not the
	 * coastland sand shares.
	 */
	private static double share(HabitatClassifier.Column c, int i) {
		double[] coastland = c.substrate == Substrate.GLACIAL_TILL ? Calibration.FERTILITY_MORAINE_PLATEAU : Calibration.FERTILITY_COASTLAND;
		return c.wOutwashPlain * Calibration.FERTILITY_OUTWASH_PLAIN[i] + c.wMorainePlateau * Calibration.FERTILITY_MORAINE_PLATEAU[i]
				+ c.wOldGlacialPlain * Calibration.FERTILITY_OLD_GLACIAL_PLAIN[i] + c.wCoastland * coastland[i]
				+ c.wFoothills * Calibration.FERTILITY_FOOTHILLS[i] + c.wBeskids * Calibration.FERTILITY_BESKIDS[i];
	}
}
