package pl.polishforests.worldgen.habitat;

/**
 * Step TT3 (docs/04-pionowy-wycinek.md "Projekt TT3", docs/00 G.49 TT-f): a profile of the altitudinal belts of a mountain
 * range: the nominal belt elevations (m a.s.l., before the aspect and noise correction of {@link AltitudinalBelts}), the
 * widths of the mosaics on the borders and the rules of the range. {@link #BESKIDS} holds the constants of the Beskids
 * of before the step (Babia Gora, docs/03 §5.1), {@link #TATRA} the belts of the Tatras (research/05 §5, research/06 §5.1).
 * A column blends the two by the Tatra weight ({@link AltitudinalBelts#tatraShare}), so the belts change smoothly between
 * the Tatras and the Podhale (Z10); outside a Tatra complex the share is 0 and the Beskids profile applies unchanged.
 *
 * @param id                 name of the profile (commands, reports)
 * @param lowerMontane       lower limit of the lower montane belt (m)
 * @param upperMontane       lower limit of the upper montane belt (m)
 * @param timberline         timberline: the lower limit of the dwarf pine scrub (m)
 * @param alpineThreshold    lower limit of the alpine grassland (m)
 * @param subnivalThreshold  lower limit of the subnival belt of the rocks (m); {@link #NO_SUBNIVAL} without one
 * @param montaneRamp        half width of the mosaic on the border of the lower and the upper montane belt (m; 0: a
 *                           contour jittered by the aspect and the noise only, as in the Beskids)
 * @param subnivalRamp       half width of the mosaic on the border of the alpine and the subnival belt (m)
 * @param largeMassifOnly    whether the dwarf pine scrub and the alpine grassland need a large massif (E12)
 */
public record BeltProfile(String id, double lowerMontane, double upperMontane, double timberline, double alpineThreshold,
		double subnivalThreshold, double montaneRamp, double subnivalRamp, boolean largeMassifOnly) {
	/** A subnival threshold out of reach of every relief of the world (the Beskids reach about 1725 m). */
	public static final double NO_SUBNIVAL = 9_000;

	/** The belts of the Beskids (the constants of {@link AltitudinalBelts} before step TT3; the code is identical). */
	public static final BeltProfile BESKIDS = new BeltProfile("beskids", AltitudinalBelts.LOWER_MONTANE,
			AltitudinalBelts.UPPER_MONTANE, AltitudinalBelts.TIMBERLINE, AltitudinalBelts.ALPINE_THRESHOLD, NO_SUBNIVAL, 0, 0,
			true);
	/**
	 * The belts of the Tatras (TT-f): the lower montane belt from about 700 m (the foothills of the Podhale reach 650–700 m),
	 * the upper montane belt from 1225 m (the lower montane belt up to 1200–1250 m), the timberline at 1525 m (the upper
	 * montane belt up to 1500–1550 m), the alpine grassland from 1800 m and the subnival belt from 2275 m (the alpine
	 * grassland up to 2250–2300 m); mosaics of 30 m on the montane border and of 50 m on the subnival border, no large
	 * massif condition.
	 */
	public static final BeltProfile TATRA = new BeltProfile("tatra", 700, 1_225, 1_525, 1_800, 2_275, 30, 50, false);

	public BeltProfile {
		if (!(lowerMontane < upperMontane && upperMontane < timberline && timberline < alpineThreshold
				&& alpineThreshold < subnivalThreshold) || montaneRamp < 0 || subnivalRamp < 0) {
			throw new IllegalArgumentException("belts of the profile " + id + " not in order");
		}
	}

	/** The value of a threshold between the profile values {@code a} and {@code b} at share {@code s} of {@code b}. */
	static double blend(double a, double b, double s) {
		return s <= 0 ? a : s >= 1 ? b : a + (b - a) * s;
	}
}
