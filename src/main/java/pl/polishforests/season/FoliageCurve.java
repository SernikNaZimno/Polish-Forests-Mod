package pl.polishforests.season;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import pl.polishforests.worldgen.habitat.Species;

/**
 * The foliage color curves of the species of the vertical slice (step V7 of docs/04-pionowy-wycinek.md, decision B6 (a)):
 * the color of a leaf at a color step of {@link SeasonPhase}, computed from the <b>raw</b> foliage color of the biome blend
 * (the biome color without the seasonal overlay of Serene Seasons, so nothing is seasoned twice). Pure Java: the client's
 * tint, the item colors of the data generator and the tests use the same numbers.
 *
 * <p>A curve of a broadleaf species is a cyclic list of keys (position in color steps from 1 March, a target color and the
 * weight of the target over the biome color); between two keys the two blended colors are interpolated linearly. The
 * dates follow report 08 §9 (Polish lowlands; a step is about ten days, a decade):
 * <ul>
 * <li>leaf-out (step V13: the Polish phenological network of 1991-2020): birch and hornbeam about 20 IV, maple 22 IV,
 * beech 28 IV, linden 30 IV, oak 2 V, ash 8 V; young leaves light yellow-green, fading into the summer color within
 * about 1.5 sub-seasons;
 * <li>summer: the biome color with a species cast of weight {@value #CAST_WEIGHT} (birch light and yellow, beech bright,
 * Norway maple a clear green, ash light yellow-green, linden blue-green, hornbeam a dull mid green, oak dark); every two
 * broadleaf species differ by at least ΔE 6 (CIE76) over the oak-hornbeam forest color, so the hue still helps to tell the
 * species apart in a mixed stand, though mainly the crown shape does;
 * <li>autumn: birch and linden yellow from the end of IX, full in the 1st-2nd decade of X, holding their clear yellow
 * until the leaves fall (birch bare at the end of X); Norway maple yellow to orange from the end of IX; ash drops its
 * leaves green-yellow early in X; hornbeam yellow from the 2nd decade of X, then brown in XI; beech golden from the 1st
 * decade of X, copper, then brown; oak late, yellow-brown in the 3rd decade of X, brown in XI, bare at the end of XI;
 * <li>winter and before leaf-out: the crown shows bare twigs (step V13, {@link #leafless}, tinted by {@link #twigs}),
 * and the curve itself ends in a light "twig haze" of each
 * species, low in chroma and close to the summer lightness (birch purple-brown, linden reddish, maple and oak gray-brown,
 * hornbeam and beech gray, ash pale gray), so a leafless forest reads neither dead nor burnt. Only the <b>marcescent</b>
 * leaves ({@link #marcescent}: oak, hornbeam and beech, written by the tree planner on young trees and the lower crowns,
 * see {@code ProceduralTreeFeature#marcescentShare}) keep a light, warm dry brown until leaf-out.
 * </ul>
 * Each tree shifts its curve by its phase ({@code phase} 0-3 of the leaf block, from the hash of its trunk): −1, −1/3,
 * +1/3 or +1 step, so a stand does not turn at once. Conifers keep their needle color through the growing season and
 * blend into a winter color (Dec-Feb, with a transition in Nov and Mar): the Scots pine, gray-green in summer, turns a
 * yellower olive in winter; the Norway spruce (as vanilla spruce) darkens by {@value #CONIFER_WINTER_DARKENING} in each
 * channel. Their phase does not shift anything.
 */
public final class FoliageCurve {
	/** The phases of the leaf blocks. */
	public static final int PHASES = 4;
	/** Shift of a tree's curve per phase, in color steps. */
	private static final double PHASE_STEP = 2.0 / 3.0;
	public static final double CONIFER_WINTER_DARKENING = 0.12;
	/** Weight of the summer cast of a broadleaf species over the biome color. */
	public static final double CAST_WEIGHT = 0.5;
	/** Weight of the winter color (twig haze or marcescent brown) over the biome color. */
	private static final double WINTER_WEIGHT = 0.9;
	/** The default foliage color of vanilla (item colors and the tint without a level). */
	public static final int DEFAULT_FOLIAGE = 0x48B518;

	/** A key of a curve: at {@code at} color steps from 1 March, the blend of {@code target} over the biome by {@code weight}. */
	record Key(double at, int target, double weight) {
	}

	/**
	 * The leafless window of a broadleaf species (step V13, T-D5): its crown shows bare twigs from {@code leafFall} (the
	 * middle of the last segment of the curve, from the last autumn key to bare, when more than half of the leaves are
	 * down) until {@code budBurst} ({@value #BUD_BURST_LEAD} step before the leaf-out key, when the young leaves already
	 * outweigh the twig haze of the curve). Positions in color steps from 1 March, cyclic.
	 */
	private record Leafless(double leafFall, double budBurst, double autumn, double bare, int twigs) {
	}

	/** The needles of a conifer: the color of the growing season and the winter color. */
	private record Needles(int summer, int winter) {
	}

	private static final Map<Species, List<Key>> CURVES = new EnumMap<>(Species.class);
	/** The curves of the marcescent leaves (dry brown until leaf-out), for the species of {@link #marcescent}. */
	private static final Map<Species, List<Key>> MARCESCENT = new EnumMap<>(Species.class);
	private static final Map<Species, Needles> CONIFERS = new EnumMap<>(Species.class);
	private static final Map<Species, Leafless> LEAFLESS = new EnumMap<>(Species.class);
	/** How long before the leaf-out key the crown shows leaves again, in color steps. */
	static final double BUD_BURST_LEAD = 0.1;

	static {
		CONIFERS.put(Species.SCOTS_PINE, new Needles(0x7C996D, 0x8A9670));
		CONIFERS.put(Species.SPRUCE, new Needles(0x619961, darken(0x619961, CONIFER_WINTER_DARKENING)));
		//               leaf-out, young, summer cast, autumn keys (position, target, weight; weight 0 = summer), bare at,
		//               twig haze, marcescent brown (0: none)
		// Step V13 (CP-B: "in late spring a few oaks, hornbeams and beeches are still brown", "in mid-autumn most crowns are
		// green"): leaf-out after the Polish phenological network of 1991-2020 (birch and hornbeam about 20 IV, maple 22 IV,
		// beech 28 IV, linden 30 IV, oak 2 V, ash 8 V; V7 had the older dates of report 08, 5-8 days later), and the
		// autumn of hornbeam, beech and oak 3-5 days earlier with the leaf fall in the 2nd-3rd decade of XI.
		broadleaf(Species.BIRCH, 4.8, 0xBCDC62, 0xB8C049, keys(20.0, 0, 0, 22.0, 0xE2C23A, 0.85, 23.6, 0xE2C23A, 0.85), 24.2,
				0xB58B8F, 0);
		broadleaf(Species.LINDEN, 5.9, 0xB0D45C, 0x2AA28F, keys(20.0, 0, 0, 22.0, 0xD8B43C, 0.85, 23.1, 0xD8B43C, 0.85), 23.6,
				0xAA8377, 0);
		broadleaf(Species.NORWAY_MAPLE, 5.2, 0xB4D65A, 0x36B356,
				keys(20.6, 0, 0, 22.0, 0xE0B030, 0.8, 23.0, 0xE3902A, 0.9, 23.9, 0xE3902A, 0.9), 24.4, 0x9D8E80, 0);
		broadleaf(Species.ASH, 6.8, 0xB0D45C, 0x9FA453, keys(21.0, 0, 0, 22.3, 0xA8B84A, 0.55), 23.0, 0x9C9C91, 0);
		broadleaf(Species.HORNBEAM, 5.0, 0xB0D45C, 0x6D926E,
				keys(21.0, 0, 0, 22.6, 0xD2B03A, 0.8, 24.2, 0x946630, 0.9), 25.6, 0x959084, 0xA58660);
		broadleaf(Species.BEECH, 5.7, 0xA8D850, 0x7AB344,
				keys(20.8, 0, 0, 22.0, 0xD8A53A, 0.8, 23.1, 0xC0702C, 0.85, 24.4, 0x8E5A2C, 0.9), 25.6, 0xA1897E, 0xBE8C5F);
		broadleaf(Species.OAK, 6.1, 0xC2C86A, 0x60773C,
				keys(22.0, 0, 0, 23.6, 0xB89238, 0.75, 25.1, 0x8C6A30, 0.85), 26.6, 0x9A8577, 0xA77F56);
	}

	private FoliageCurve() {
	}

	/** Autumn keys: triples (position, target, weight); a weight 0 means the summer color (the species cast). */
	private static double[] keys(double... values) {
		return values;
	}

	/** A broadleaf curve and, when {@code marcescent} is not 0, the curve of its marcescent leaves. */
	private static void broadleaf(Species s, double leafOut, int young, int cast, double[] autumn, double bare, int twigs,
			int marcescent) {
		CURVES.put(s, curve(s, leafOut, young, cast, autumn, bare, twigs));
		double lastKey = autumn[autumn.length - 3];
		LEAFLESS.put(s, new Leafless((lastKey + bare) / 2.0, leafOut - BUD_BURST_LEAD, autumn[0], bare, twigs));
		if (marcescent != 0) {
			MARCESCENT.put(s, curve(s, leafOut, young, cast, autumn, bare, marcescent));
		}
	}

	private static List<Key> curve(Species s, double leafOut, int young, int cast, double[] autumn, double bare, int winter) {
		List<Key> k = new java.util.ArrayList<>();
		k.add(new Key(leafOut - 0.4, winter, WINTER_WEIGHT));
		k.add(new Key(leafOut, young, 0.65));
		k.add(new Key(leafOut + 2.0, young, 0.35));
		k.add(new Key(leafOut + 4.5, cast, CAST_WEIGHT));
		for (int i = 0; i < autumn.length; i += 3) {
			boolean summer = autumn[i + 2] == 0;
			k.add(new Key(autumn[i], summer ? cast : (int) autumn[i + 1], summer ? CAST_WEIGHT : autumn[i + 2]));
		}
		k.add(new Key(bare, winter, WINTER_WEIGHT));
		for (int i = 1; i < k.size(); i++) {
			if (k.get(i).at() <= k.get(i - 1).at()) {
				throw new IllegalStateException("keys of " + s + " out of order at " + k.get(i).at());
			}
		}
		return List.copyOf(k);
	}

	/** The species with a curve (the leaf blocks of the slice). */
	public static Set<Species> species() {
		Set<Species> out = java.util.EnumSet.noneOf(Species.class);
		out.addAll(CURVES.keySet());
		out.addAll(CONIFERS.keySet());
		return out;
	}

	public static boolean conifer(Species s) {
		return CONIFERS.containsKey(s);
	}

	/** Whether the species can keep its dead leaves over the winter (oak, hornbeam, beech: it has a marcescent curve). */
	public static boolean marcescent(Species s) {
		return MARCESCENT.containsKey(s);
	}

	/**
	 * Whether the crown of a broadleaf tree shows bare twigs instead of leaves (step V13, T-D5): at a color step and phase,
	 * for leaves that are not marcescent ({@code dryLeaves} false), within the species' leafless window (from the leaf fall
	 * to the bud burst). Conifers and marcescent leaves (which keep their dry brown leaves until leaf-out) are never
	 * leafless.
	 */
	public static boolean leafless(Species species, int step, int phase, boolean dryLeaves) {
		return !dryLeaves && leaflessAt(species, position(step, phase));
	}

	/** Whether a broadleaf species is in its leafless window at a curve position (false for conifers). */
	public static boolean leaflessAt(Species species, double at) {
		Leafless w = LEAFLESS.get(species);
		if (w == null) {
			return false;
		}
		double x = cyclic(at);
		return w.leafFall() <= w.budBurst() ? x >= w.leafFall() && x < w.budBurst() : x >= w.leafFall() || x < w.budBurst();
	}

	/** The start of the leaf fall of a broadleaf species (curve position), or NaN for a conifer. */
	public static double leafFall(Species species) {
		Leafless w = LEAFLESS.get(species);
		return w == null ? Double.NaN : w.leafFall();
	}

	/** The bud burst of a broadleaf species (curve position, the end of the leafless window), or NaN for a conifer. */
	public static double budBurst(Species species) {
		Leafless w = LEAFLESS.get(species);
		return w == null ? Double.NaN : w.budBurst();
	}

	/** The end of the leaf fall of a broadleaf species (the bare key of its curve), or NaN for a conifer. */
	public static double bare(Species species) {
		Leafless w = LEAFLESS.get(species);
		return w == null ? Double.NaN : w.bare();
	}

	/** The first autumn key of a broadleaf species (the end of the summer color), or NaN for a conifer. */
	public static double autumnStart(Species species) {
		Leafless w = LEAFLESS.get(species);
		return w == null ? Double.NaN : w.autumn();
	}

	/**
	 * The color of the bare twigs of a broadleaf species over a raw biome foliage color (step V13): its twig haze with the
	 * winter weight, the tint of the twig model (tint index 1 of the leaf block); for a conifer its winter needles.
	 */
	public static int twigs(Species species, int raw) {
		Leafless w = LEAFLESS.get(species);
		if (w == null) {
			return colorAt(species, SeasonPhase.firstStep(SubSeason.MID_WINTER) + 1.5, raw);
		}
		return lerp(raw & 0xFFFFFF, w.twigs(), WINTER_WEIGHT);
	}

	/** A curve position in [0, {@value SeasonPhase#STEPS}). */
	static double cyclic(double at) {
		return ((at % SeasonPhase.STEPS) + SeasonPhase.STEPS) % SeasonPhase.STEPS;
	}

	/** The position of a tree's leaf on its curve at a color step: the middle of the step shifted by the phase. */
	public static double position(int step, int phase) {
		return Math.floorMod(step, SeasonPhase.STEPS) + 0.5 + (phase - (PHASES - 1) / 2.0) * PHASE_STEP;
	}

	/** The color (RGB, no alpha) of a species' leaf at a color step and phase over a raw biome foliage color. */
	public static int color(Species species, int step, int phase, int raw) {
		return color(species, step, phase, false, raw);
	}

	/** As {@link #color(Species, int, int, int)}; {@code dryLeaves} picks the marcescent curve where the species has one. */
	public static int color(Species species, int step, int phase, boolean dryLeaves, int raw) {
		return colorAt(species, conifer(species) ? Math.floorMod(step, SeasonPhase.STEPS) + 0.5 : position(step, phase), dryLeaves,
				raw);
	}

	/** The summer color of a species (the middle of MID_SUMMER, mean phase) over a raw biome color: the item color. */
	public static int summer(Species species, int raw) {
		return colorAt(species, SeasonPhase.firstStep(SubSeason.MID_SUMMER) + 1.5, raw);
	}

	/** The color at a curve position (color steps from 1 March, cyclic). */
	public static int colorAt(Species species, double at, int raw) {
		return colorAt(species, at, false, raw);
	}

	/** The color at a curve position; {@code dryLeaves} picks the marcescent curve where the species has one. */
	public static int colorAt(Species species, double at, boolean dryLeaves, int raw) {
		raw &= 0xFFFFFF;
		double x = ((at % SeasonPhase.STEPS) + SeasonPhase.STEPS) % SeasonPhase.STEPS;
		Needles needles = CONIFERS.get(species);
		if (needles != null) {
			return lerp(needles.summer(), needles.winter(), winter(x));
		}
		List<Key> keys = dryLeaves && MARCESCENT.containsKey(species) ? MARCESCENT.get(species) : CURVES.get(species);
		if (keys == null) {
			throw new IllegalArgumentException("no foliage curve for " + species);
		}
		int n = keys.size();
		int next = 0;
		while (next < n && keys.get(next).at() <= x) {
			next++;
		}
		Key b = keys.get(next % n);
		Key a = keys.get((next - 1 + n) % n);
		double span = b.at() - a.at();
		double from = x - a.at();
		if (span <= 0) {
			span += SeasonPhase.STEPS;
		}
		if (from < 0) {
			from += SeasonPhase.STEPS;
		}
		double f = Math.clamp(from / span, 0.0, 1.0);
		return lerp(lerp(raw, a.target(), a.weight()), lerp(raw, b.target(), b.weight()), f);
	}

	/** Share of the winter color of conifers at a curve position: 1 in Dec-Feb, ramps over Nov and Mar. */
	static double winter(double x) {
		double dec = SeasonPhase.firstStep(SubSeason.EARLY_WINTER);
		double nov = SeasonPhase.firstStep(SubSeason.LATE_AUTUMN);
		if (x >= dec) {
			return 1.0;
		}
		if (x >= nov) {
			return (x - nov) / (dec - nov);
		}
		if (x < SeasonPhase.STEPS_PER_SUB_SEASON) {
			return 1.0 - x / SeasonPhase.STEPS_PER_SUB_SEASON;
		}
		return 0.0;
	}

	static int darken(int rgb, double by) {
		double k = 1.0 - by;
		int r = (int) Math.round(((rgb >> 16) & 255) * k);
		int g = (int) Math.round(((rgb >> 8) & 255) * k);
		int b = (int) Math.round((rgb & 255) * k);
		return (r << 16) | (g << 8) | b;
	}

	static int lerp(int a, int b, double f) {
		int r = (int) Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * f);
		int g = (int) Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * f);
		int bl = (int) Math.round((a & 255) + ((b & 255) - (a & 255)) * f);
		return (r << 16) | (g << 8) | bl;
	}
}
