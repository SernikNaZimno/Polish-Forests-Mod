package pl.polishforests.worldgen.habitat;

/**
 * Species ranges in the procedural world (§9, ecology report §5.1): thresholds on the regional fields
 * O (oceanicity) and P (mountain influence) from {@code RegionalField}. At the level of the biome and the habitat
 * flags the thresholds are hard; the ecotone ramp (±0.05, noise 2 km·k) scales species weights only in the tree
 * stand palettes (M3). Between the beech range (O ≥ 0.4) and the native spruce range (O &lt; 0.3), at P &lt; 0.4,
 * lies a belt without beech and spruce, as in Mazovia.
 */
public final class SpeciesRanges {
	private SpeciesRanges() {
	}

	/** Beech: O ≥ 0.40 or P ≥ 0.40 (no elevation or site conditions). */
	public static boolean beech(double o, double p) {
		return o >= Calibration.BEECH_O || p >= Calibration.BEECH_P;
	}

	/** Beech on a site: within range, H up to the upper limit of the lower montane belt, not on Bs, Bb, Ol or Lł. */
	public static boolean beech(double o, double p, double h, double upperMontaneLimit, ForestSiteType siteType) {
		return beech(o, p) && h < upperMontaneLimit && siteType != ForestSiteType.DRY_CONIFEROUS && siteType != ForestSiteType.BOGGY_CONIFEROUS && siteType != ForestSiteType.ALDER_SWAMP && siteType != ForestSiteType.RIPARIAN;
	}

	/** Fir: P ≥ 0.50, H ≤ 1250 m, not on Bs, Bb, Ol ({@code siteType} null: no site condition). */
	public static boolean fir(double p, double h, ForestSiteType siteType) {
		return p >= Calibration.FIR_P && h <= Calibration.FIR_H
				&& (siteType == null || siteType != ForestSiteType.DRY_CONIFEROUS && siteType != ForestSiteType.BOGGY_CONIFEROUS && siteType != ForestSiteType.ALDER_SWAMP);
	}

	/** Native spruce: O &lt; 0.30 or P ≥ 0.50. */
	public static boolean spruce(double o, double p) {
		return o < Calibration.SPRUCE_O || p >= Calibration.SPRUCE_P;
	}

	/** Hornbeam: H ≤ 600 m (≤ 700 m on S slopes), only on L and LM sites (S slope: {@code aspect}, the cosine of the aspect, > 0). */
	public static boolean hornbeam(double h, double aspect, Fertility t) {
		double max = aspect > 0 ? Calibration.HORNBEAM_H_S : Calibration.HORNBEAM_H;
		return h <= max && (t == Fertility.EUTROPHIC || t == Fertility.MESOTROPHIC);
	}

	/** Gray alder: P ≥ 0.50 or O &lt; 0.30. */
	public static boolean grayAlder(double o, double p) {
		return p >= Calibration.GRAY_ALDER_P || o < Calibration.GRAY_ALDER_O;
	}

	/** Sessile oak: O ≥ 0.35 or P ≥ 0.40; H ≤ 600 m (used from M3). */
	public static boolean sessileOak(double o, double p, double h) {
		return (o >= Calibration.SESSILE_OAK_O || p >= Calibration.SESSILE_OAK_P) && h <= Calibration.SESSILE_OAK_H;
	}

	/** Larch: 0.50 ≤ P &lt; 0.80 and H 250–650 m (admixture in fir forest; used from M3). */
	public static boolean larch(double p, double h) {
		return p >= Calibration.LARCH_P_FROM && p < Calibration.LARCH_P_TO && h >= Calibration.LARCH_H_FROM
				&& h <= Calibration.LARCH_H_TO;
	}

	/** Ivy: O ≥ 0.45 or P ≥ 0.50 (used from M4). */
	public static boolean ivy(double o, double p) {
		return o >= Calibration.IVY_O || p >= Calibration.IVY_P;
	}

	/** Yew: O ≥ 0.6 or P ≥ 0.6 (share ≤ 0.1%, used from M3). */
	public static boolean yew(double o, double p) {
		return o >= Calibration.YEW_O || p >= Calibration.YEW_P;
	}

	/** BEECH, FIR, SPRUCE and HORNBEAM flags of the habitat code for a column and its site type. */
	static int flags(HabitatClassifier.Column c, ForestSiteType siteType) {
		int f = 0;
		// The montane belt limit evaluates noise only up high (below about 1060 m it cannot be crossed).
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
