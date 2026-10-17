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
 * <li>leaf-out: birch about 20 IV, maple and hornbeam late IV, beech early V, linden and oak early to mid V, ash mid V;
 * young leaves light yellow-green, fading into the summer color within about 1.5 sub-seasons;
 * <li>summer: the biome color with a weak species cast (birch lighter and yellower, oak and ash darker, beech brighter),
 * so the species can be told apart in a mixed stand;
 * <li>autumn: birch and linden yellow from the end of IX, full in the 1st-2nd decade of X, bare at the end of X; Norway
 * maple yellow to orange from the end of IX; ash drops its leaves green-yellow early in X; hornbeam yellow from the
 * 1st-2nd decade of X, then brown in XI; beech golden, copper, then brown; oak late, yellow-brown at the end of X, brown
 * in XI, bare in XII;
 * <li>winter: a dull dormant color (until step V13 replaces the leafless crowns with twigs; oak, hornbeam and beech keep
 * a dry brown, their marcescent leaves).
 * </ul>
 * Each tree shifts its curve by its phase ({@code phase} 0-3 of the leaf block, from the hash of its trunk): −1, −1/3,
 * +1/3 or +1 step, so a stand does not turn at once. Conifers keep a constant hue (Scots pine gray-green, Norway spruce as
 * vanilla spruce) and only darken a little in winter (by up to {@value #CONIFER_WINTER_DARKENING} in each channel, Dec-Feb,
 * with a transition in Nov and Mar); their phase does not shift anything.
 */
public final class FoliageCurve {
	/** The phases of the leaf blocks. */
	public static final int PHASES = 4;
	/** Shift of a tree's curve per phase, in color steps. */
	private static final double PHASE_STEP = 2.0 / 3.0;
	public static final double CONIFER_WINTER_DARKENING = 0.12;
	/** The default foliage color of vanilla (item colors and the tint without a level). */
	public static final int DEFAULT_FOLIAGE = 0x48B518;

	/** A key of a curve: at {@code at} color steps from 1 March, the blend of {@code target} over the biome by {@code weight}. */
	record Key(double at, int target, double weight) {
	}

	private static final Map<Species, List<Key>> CURVES = new EnumMap<>(Species.class);
	private static final Map<Species, Integer> CONIFERS = new EnumMap<>(Species.class);

	private static final int DORMANT = 0x7D6A50;
	private static final int DRY_BROWN = 0x8A6A3E;

	static {
		CONIFERS.put(Species.SCOTS_PINE, 0x7F9C86);
		CONIFERS.put(Species.SPRUCE, 0x619961);
		//               leaf-out, young,    summer cast,         autumn keys …,                                  bare at, winter
		broadleaf(Species.BIRCH, 4.9, 0xB9D860, 0x9CC04E, 0.25, keys(20.0, 0, 0, 22.0, 0xE2C23A, 0.85), 24.2, DORMANT);
		broadleaf(Species.LINDEN, 6.3, 0xB0D45C, 0x30805C, 0.3, keys(20.0, 0, 0, 22.0, 0xD8B43C, 0.85), 23.6, DORMANT);
		broadleaf(Species.NORWAY_MAPLE, 5.5, 0xB4D65A, 0x4A9A40, 0.3,
				keys(20.6, 0, 0, 22.0, 0xE0B030, 0.8, 23.0, 0xE3902A, 0.9), 24.4, DORMANT);
		broadleaf(Species.ASH, 7.0, 0xB0D45C, 0x2F6A44, 0.3, keys(21.0, 0, 0, 22.3, 0xA8B84A, 0.55), 23.0, DORMANT);
		broadleaf(Species.HORNBEAM, 5.5, 0xB0D45C, 0x60BC20, 0.2,
				keys(21.3, 0, 0, 23.0, 0xD2B03A, 0.8, 24.6, 0x946630, 0.9), 26.0, DRY_BROWN);
		broadleaf(Species.BEECH, 6.0, 0xA8D850, 0x7AC030, 0.25,
				keys(21.0, 0, 0, 22.2, 0xD8A53A, 0.8, 23.3, 0xC0702C, 0.85, 24.6, 0x8E5A2C, 0.9), 26.0, DRY_BROWN);
		broadleaf(Species.OAK, 6.6, 0xC2C86A, 0x46702A, 0.3,
				keys(22.4, 0, 0, 24.0, 0xB89238, 0.75, 25.5, 0x8C6A30, 0.85), 27.0, DRY_BROWN);
	}

	private FoliageCurve() {
	}

	/** Autumn keys: triples (position, target, weight); a weight 0 means the summer color (the species cast). */
	private static double[] keys(double... values) {
		return values;
	}

	private static void broadleaf(Species s, double leafOut, int young, int cast, double castWeight, double[] autumn,
			double bare, int winter) {
		List<Key> k = new java.util.ArrayList<>();
		k.add(new Key(leafOut - 0.7, winter, 0.9));
		k.add(new Key(leafOut, young, 0.65));
		k.add(new Key(leafOut + 2.0, young, 0.35));
		k.add(new Key(leafOut + 4.5, cast, castWeight));
		for (int i = 0; i < autumn.length; i += 3) {
			boolean summer = autumn[i + 2] == 0;
			k.add(new Key(autumn[i], summer ? cast : (int) autumn[i + 1], summer ? castWeight : autumn[i + 2]));
		}
		k.add(new Key(bare, winter, 0.9));
		for (int i = 1; i < k.size(); i++) {
			if (k.get(i).at() <= k.get(i - 1).at()) {
				throw new IllegalStateException("keys of " + s + " out of order at " + k.get(i).at());
			}
		}
		CURVES.put(s, List.copyOf(k));
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

	/** The position of a tree's leaf on its curve at a color step: the middle of the step shifted by the phase. */
	public static double position(int step, int phase) {
		return Math.floorMod(step, SeasonPhase.STEPS) + 0.5 + (phase - (PHASES - 1) / 2.0) * PHASE_STEP;
	}

	/** The color (RGB, no alpha) of a species' leaf at a color step and phase over a raw biome foliage color. */
	public static int color(Species species, int step, int phase, int raw) {
		return colorAt(species, conifer(species) ? Math.floorMod(step, SeasonPhase.STEPS) + 0.5 : position(step, phase), raw);
	}

	/** The summer color of a species (the middle of MID_SUMMER, mean phase) over a raw biome color: the item color. */
	public static int summer(Species species, int raw) {
		return colorAt(species, SeasonPhase.firstStep(SubSeason.MID_SUMMER) + 1.5, raw);
	}

	/** The color at a curve position (color steps from 1 March, cyclic). */
	public static int colorAt(Species species, double at, int raw) {
		raw &= 0xFFFFFF;
		double x = ((at % SeasonPhase.STEPS) + SeasonPhase.STEPS) % SeasonPhase.STEPS;
		Integer needles = CONIFERS.get(species);
		if (needles != null) {
			return darken(needles, CONIFER_WINTER_DARKENING * winter(x));
		}
		List<Key> keys = CURVES.get(species);
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

	/** Share of the winter darkening of conifers at a curve position: 1 in Dec-Feb, ramps over Nov and Mar. */
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
