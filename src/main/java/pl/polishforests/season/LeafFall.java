package pl.polishforests.season;

import pl.polishforests.worldgen.habitat.Species;

/**
 * The chance of a falling leaf particle per animation tick of a leaf block (step V13 of docs/04-pionowy-wycinek.md, the
 * user's request of 2026-10-26: in nature the leaves fall in autumn). Vanilla drops leaves the whole year with the chance
 * {@value #VANILLA} (oak, birch and most broadleaf leaves); in the "Polska" world the chance follows the species curve of
 * {@link FoliageCurve} instead (decision V13-g, to be confirmed at CP-C):
 * <ul>
 * <li>broadleaf species: none from the leaf-out until their autumn starts (the first autumn key of the curve); from then
 * on more than in vanilla, rising (slowly at first) from {@value #AUTUMN_FIRST} to {@value #AUTUMN_PEAK} at the leaf fall of the tree (when
 * half of its leaves are down and the crown shows its twigs, {@link FoliageCurve#leafFall}), then the last leaves until
 * the crown is bare ({@link FoliageCurve#bare}), so the most in late autumn, when the hornbeams and oaks of the
 * oak-hornbeam forest shed; none on bare twigs in winter; marcescent leaves (young oaks, hornbeams and beeches) drop a rare
 * dry leaf ({@value #MARCESCENT_WINTER}) all winter;
 * <li>conifers: rare the whole year, {@value #NEEDLES} (the Scots pine drops more, {@value #PINE_AUTUMN}, in September and
 * October, when its three-year-old needles turn yellow and fall).
 * </ul>
 * The particle keeps the color of the leaf (its tint: the curve, so a yellow birch drops yellow leaves). Pure Java, so the
 * client mixin and the tests use the same numbers.
 */
public final class LeafFall {
	/** Vanilla's chance of oak, birch, jungle, acacia, dark oak and mangrove leaves. */
	public static final float VANILLA = 0.01F;
	/** The chance at the start of a species' autumn. */
	public static final float AUTUMN_FIRST = 0.012F;
	/** The chance just before the leaf fall of a tree (the peak, four times vanilla). */
	public static final float AUTUMN_PEAK = 0.04F;
	/** Dry leaves of the marcescent crowns in winter. */
	public static final float MARCESCENT_WINTER = 0.0015F;
	/** Conifers outside the needle fall. */
	public static final float NEEDLES = 0.0015F;
	/** The Scots pine in its needle fall (EARLY_AUTUMN and MID_AUTUMN). */
	public static final float PINE_AUTUMN = 0.004F;

	private LeafFall() {
	}

	/**
	 * The chance of a falling leaf of a species' leaf block at a color step, for the phase of its tree and whether the leaf
	 * is marcescent.
	 */
	public static float chance(Species species, int step, int phase, boolean marcescent) {
		if (FoliageCurve.conifer(species)) {
			SubSeason sub = SeasonPhase.subSeason(step);
			return species == Species.SCOTS_PINE && (sub == SubSeason.EARLY_AUTUMN || sub == SubSeason.MID_AUTUMN) ? PINE_AUTUMN
					: NEEDLES;
		}
		double x = FoliageCurve.cyclic(FoliageCurve.position(step, phase));
		double autumn = FoliageCurve.autumnStart(species);
		double fall = FoliageCurve.leafFall(species);
		double bud = FoliageCurve.budBurst(species);
		if (x >= autumn && x < fall) {
			// Slow at first, fastest just before the leaf fall (the leaves loosen with the frosts of late X and XI).
			double f = (x - autumn) / (fall - autumn);
			return (float) (AUTUMN_FIRST + (AUTUMN_PEAK - AUTUMN_FIRST) * f * f);
		}
		double bare = FoliageCurve.bare(species);
		if (x >= fall && x < bare) {
			// The last leaves of a crown that already shows its twigs (the second half of the leaf fall): at the peak for half
			// of it, then fewer until the crown is bare.
			return (float) Math.max(MARCESCENT_WINTER, AUTUMN_PEAK * Math.min(1.0, 2.0 * (1.0 - (x - fall) / (bare - fall))));
		}
		boolean leafless = x >= fall || x < bud;
		return leafless && marcescent && FoliageCurve.marcescent(species) ? MARCESCENT_WINTER : 0.0F;
	}
}
