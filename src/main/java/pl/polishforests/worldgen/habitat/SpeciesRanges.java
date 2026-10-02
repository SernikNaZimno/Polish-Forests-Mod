package pl.polishforests.worldgen.habitat;

/**
 * Zasięgi gatunków w świecie proceduralnym (§9, raport ekologii §5.1): progi na polach regionalnych
 * O (oceaniczność) i P (podgórskość) z {@code RegionalField}. Na poziomie biomu i flag siedliska progi
 * są twarde; rampa ekotonu (±0,05, szum 2 km·k) mnoży wagi gatunków dopiero w paletach drzewostanu (M3).
 * Między zasięgiem buka (O ≥ 0,4) a naturalnego świerka (O &lt; 0,3) przy P &lt; 0,4 leży pas bez buka
 * i świerka, jak na Mazowszu.
 */
public final class SpeciesRanges {
	private SpeciesRanges() {
	}

	/** Buk: O ≥ 0,40 lub P ≥ 0,40 (bez warunków wysokości i siedliska). */
	public static boolean beech(double o, double p) {
		return o >= Calibration.BEECH_O || p >= Calibration.BEECH_P;
	}

	/** Buk w siedlisku: zasięg, H do górnej granicy regla dolnego, nie na Bs, Bb, Ol ani Lł. */
	public static boolean beech(double o, double p, double h, double upperMontaneLimit, ForestSiteType siteType) {
		return beech(o, p) && h < upperMontaneLimit && siteType != ForestSiteType.DRY_CONIFEROUS && siteType != ForestSiteType.BOGGY_CONIFEROUS && siteType != ForestSiteType.ALDER_SWAMP && siteType != ForestSiteType.RIPARIAN;
	}

	/** Jodła: P ≥ 0,50, H ≤ 1250 m, nie na Bs, Bb, Ol ({@code stl} null: bez warunku siedliska). */
	public static boolean fir(double p, double h, ForestSiteType siteType) {
		return p >= Calibration.FIR_P && h <= Calibration.FIR_H
				&& (siteType == null || siteType != ForestSiteType.DRY_CONIFEROUS && siteType != ForestSiteType.BOGGY_CONIFEROUS && siteType != ForestSiteType.ALDER_SWAMP);
	}

	/** Świerk naturalny: O &lt; 0,30 lub P ≥ 0,50. */
	public static boolean spruce(double o, double p) {
		return o < Calibration.SPRUCE_O || p >= Calibration.SPRUCE_P;
	}

	/** Grab: H ≤ 600 m (stoki S ≤ 700 m), tylko siedliska L i LM ({@code stoki S}: cos ekspozycji > 0). */
	public static boolean hornbeam(double h, double aspect, Fertility t) {
		double max = aspect > 0 ? Calibration.HORNBEAM_H_S : Calibration.HORNBEAM_H;
		return h <= max && (t == Fertility.EUTROPHIC || t == Fertility.MESOTROPHIC);
	}

	/** Olsza szara: P ≥ 0,50 lub O &lt; 0,30. */
	public static boolean grayAlder(double o, double p) {
		return p >= Calibration.GRAY_ALDER_P || o < Calibration.GRAY_ALDER_O;
	}

	/** Dąb bezszypułkowy: O ≥ 0,35 lub P ≥ 0,40; H ≤ 600 m (użycie od M3). */
	public static boolean sessileOak(double o, double p, double h) {
		return (o >= Calibration.SESSILE_OAK_O || p >= Calibration.SESSILE_OAK_P) && h <= Calibration.SESSILE_OAK_H;
	}

	/** Modrzew: 0,50 ≤ P &lt; 0,80 i H 250–650 m (domieszka w jedlinie; użycie od M3). */
	public static boolean larch(double p, double h) {
		return p >= Calibration.LARCH_P_FROM && p < Calibration.LARCH_P_TO && h >= Calibration.LARCH_H_FROM
				&& h <= Calibration.LARCH_H_TO;
	}

	/** Bluszcz: O ≥ 0,45 lub P ≥ 0,50 (użycie od M4). */
	public static boolean ivy(double o, double p) {
		return o >= Calibration.IVY_O || p >= Calibration.IVY_P;
	}

	/** Cis: O ≥ 0,6 lub P ≥ 0,6 (udział ≤ 0,1%, użycie od M3). */
	public static boolean yew(double o, double p) {
		return o >= Calibration.YEW_O || p >= Calibration.YEW_P;
	}

	/** Flagi BUK, JODŁA, ŚWIERK i GRAB kodu siedliska dla kolumny i jej STL. */
	static int flags(HabitatClassifier.Column c, ForestSiteType siteType) {
		int f = 0;
		// Granica regla liczy szum tylko wysoko (poniżej ok. 1060 m nie może jej przekroczyć).
		double belt = c.H < AltitudinalBelts.UPPER_MONTANE - AltitudinalBelts.MAX_LOWERING ? Double.POSITIVE_INFINITY : AltitudinalBelts.upperMontaneLimit(c);
		if (beech(c.O, c.P, c.H, belt, siteType)) {
			f |= Species.FLAG_BEECH;
		}
		if (fir(c.P, c.H, siteType)) {
			f |= Species.FLAG_FIR;
		}
		if (spruce(c.O, c.P)) {
			f |= Species.FLAG_SPRUCE;
		}
		if (hornbeam(c.H, c.aspect(), c.fertility())) {
			f |= Species.FLAG_HORNBEAM;
		}
		return f;
	}
}
