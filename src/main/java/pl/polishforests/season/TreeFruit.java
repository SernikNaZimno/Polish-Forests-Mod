package pl.polishforests.season;

import pl.polishforests.worldgen.habitat.Species;

/**
 * The fruit of a tree on its leaves (step F6 of docs/04-pionowy-wycinek.md, "Projekt F6": the rowan's fruit clusters,
 * after the bilberry's flowers of step V13): pure Java, the phases the client shows at a color step of
 * {@link SeasonPhase}. Only the leaf blocks of the bearing positions ({@link #bearing}: about one in {@value #SHARE_PERCENT}
 * percent, the clusters at the shoot ends) carry fruit; a tree shifts its dates by its leaf phase as its color curve does
 * ({@link FoliageCurve#position}). The fruit is a look of the client only (no block state, no item): the rowan's berries
 * are not food in the mod (raw rowan berries are bitter and mildly poisonous).
 *
 * <p>The rowan (docs/research 08 §9, the mountain dates): the green fruit hidden among the leaves in July, orange from
 * mid-August ({@link Look#UNRIPE}), scarlet from early September ({@link Look#RIPE}) until the leaves fall in the middle
 * of October (the bare twigs of {@link FoliageCurve#leafless} then show; birds take most of the fruit by November).
 */
public final class TreeFruit {
	/** What the leaves of a tree show of its fruit. */
	public enum Look {
		NONE, UNRIPE, RIPE
	}

	/** The rowan's orange fruit from mid-August, color steps from 1 March. */
	static final double ROWAN_UNRIPE = 16.5;
	/** The rowan's ripe scarlet fruit from early September. */
	static final double ROWAN_RIPE = 18.8;
	/** The end of the fruit (after the leaf fall: the twigs show first). */
	static final double ROWAN_END = 23.5;
	/** Share of the leaf blocks of a rowan that carry a fruit cluster, percent. */
	static final int SHARE_PERCENT = 22;

	private TreeFruit() {
	}

	/** Whether a species shows fruit on its leaves. */
	public static boolean fruiting(Species species) {
		return species == Species.ROWAN;
	}

	/** Whether the leaf block at a position carries a fruit cluster (a pure hash of the position). */
	public static boolean bearing(int x, int y, int z) {
		long h = x * 0x9E3779B97F4A7C15L + y * 0xD1B54A32D192ED03L + z * 0xC2B2AE3D27D4EB4FL + 0xF2017L;
		h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
		h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
		h ^= h >>> 31;
		return Math.floorMod(h, 100) < SHARE_PERCENT;
	}

	/** The fruit of a leaf block of a species at a color step and leaf phase, at its position. */
	public static Look look(Species species, int step, int phase, int x, int y, int z) {
		if (!fruiting(species) || !bearing(x, y, z)) {
			return Look.NONE;
		}
		return lookAt(FoliageCurve.cyclic(FoliageCurve.position(step, phase)));
	}

	/** The rowan's fruit at a curve position (color steps from 1 March). */
	static Look lookAt(double at) {
		return at >= ROWAN_RIPE && at < ROWAN_END ? Look.RIPE : at >= ROWAN_UNRIPE && at < ROWAN_RIPE ? Look.UNRIPE : Look.NONE;
	}
}
