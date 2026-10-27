package pl.polishforests.season;

import pl.polishforests.block.GroundPlant;

/**
 * The phases of the herb layer in the year (step V13 of docs/04-pionowy-wycinek.md, decision PW-8 (a): drawn by the
 * client from the season, the block state does not change; ecology report §3 and §9). Pure Java, shared by the client
 * (the wrapped models and the tints), the block shapes on both sides (rule R12: a plant that has withdrawn underground has
 * no outline, so the player does not aim at nothing) and the tests.
 *
 * <p>Positions are in color steps from 1 March ({@link SeasonPhase}, about a decade each); every plant shifts its dates by
 * a jitter from its position ({@link #shift}: −1, −1/3, +1/3 or +1 step), so a carpet changes over about two decades,
 * not at once.
 * <ul>
 * <li><b>Wood anemone</b>: shoots from mid-March ({@value #ANEMONE_SHOOTS}), flowering from the end of March to the 1st
 * decade of May ({@value #ANEMONE_FLOWERS} … {@value #ANEMONE_FLOWERS_END}, the peak in MID_SPRING under the bare canopy),
 * then leaves only, yellowing, and gone from the 1st decade of June ({@value #ANEMONE_GONE}) until the next March;
 * <li><b>Liverleaf</b>: flowering from early March to mid-April ({@value #HEPATICA_FLOWERS} … {@value #HEPATICA_FLOWERS_END}),
 * before its new leaves; the leathery leaves stay the rest of the year (also under snow);
 * <li><b>Bilberry</b>: flowering in May ({@value #BILBERRY_FLOWERS} … {@value #BILBERRY_FLOWERS_END}, small pink bells);
 * fruit in MID_SUMMER and LATE_SUMMER ({@link #fruiting}; a server state, {@code BerrySeason}); its leaves turn red in
 * October and fall in November, the bare green-brown twigs stay ({@link #bilberryColor});
 * <li><b>Bracken</b>: fronds unfold at the end of May, turn yellow-brown from the end of September and dead brown in
 * MID_AUTUMN, and the dead fronds stay brown until the new ones ({@link #brackenColor});
 * <li><b>Lily of the valley</b> (the vanilla block, in the Polska world only; review round 1 of V13): leaves from the end
 * of April ({@value #LILY_SHOOTS}), the white bells in May ({@value #LILY_FLOWERS} … {@value #LILY_FLOWERS_END}), leaves
 * through the summer, yellow in September ({@link #lilyColor}) and gone from October ({@value #LILY_GONE}) to the end of
 * April: it dies back to its rhizome ({@link #lilyOfTheValley}).
 * </ul>
 */
public final class GroundPhases {
	/** What the client draws of a herb. */
	public enum Look {
		/** Nothing: the plant has withdrawn underground (rhizome, tuber). */
		NONE,
		/** Leaves only (no flowers). */
		LEAVES,
		/** The full model of the block (the flowering plant of the flower beds). */
		FULL,
		/** The full model with the flowers overlay (the bilberry in bloom). */
		FLOWERING
	}

	static final double ANEMONE_SHOOTS = 1.0;
	static final double ANEMONE_FLOWERS = 2.3;
	static final double ANEMONE_FLOWERS_END = 6.7;
	static final double ANEMONE_GONE = 9.7;
	static final double HEPATICA_FLOWERS = 0.6;
	static final double HEPATICA_FLOWERS_END = 4.4;
	static final double BILBERRY_FLOWERS = 5.8;
	static final double BILBERRY_FLOWERS_END = 8.6;
	static final double LILY_SHOOTS = 5.0;
	static final double LILY_FLOWERS = 6.2;
	static final double LILY_FLOWERS_END = 8.8;
	static final double LILY_GONE = 20.7;

	/** Keys of a tint curve: position, target color, weight of the target over the raw grass color. */
	private record Key(double at, int target, double weight) {
	}

	private static final int BILBERRY_WINTER = 0x6F6440;
	private static final Key[] BILBERRY = {new Key(3.6, BILBERRY_WINTER, 0.55), new Key(5.0, 0xA4C85A, 0.5),
			new Key(9.0, 0, 0.0), new Key(20.5, 0, 0.0), new Key(22.2, 0xB5452F, 0.75), new Key(24.0, 0x8E3E2E, 0.75),
			new Key(26.0, BILBERRY_WINTER, 0.55)};
	private static final int BRACKEN_DEAD = 0x7A5634;
	private static final Key[] BRACKEN = {new Key(6.0, BRACKEN_DEAD, 0.85), new Key(7.6, 0x8CB84A, 0.5), new Key(9.2, 0, 0.0),
			new Key(19.2, 0, 0.0), new Key(21.0, 0xB08A3C, 0.7), new Key(22.5, 0x8E5B2E, 0.85), new Key(24.5, BRACKEN_DEAD, 0.85)};

	private static final Key[] LILY = {new Key(LILY_SHOOTS, 0x86B861, 0.3), new Key(9.0, 0, 0.0), new Key(17.6, 0, 0.0),
			new Key(19.3, 0xC9B451, 0.7), new Key(LILY_GONE, 0xA9893F, 0.8)};

	private GroundPhases() {
	}

	/** The jitter of a plant's dates from its position: −1, −1/3, +1/3 or +1 color step. */
	public static double shift(int x, int z) {
		long h = x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL;
		h = (h ^ (h >>> 31)) * 0xBF58476D1CE4E5B9L;
		h ^= h >>> 29;
		return ((int) (h >>> 62) - 1.5) * (2.0 / 3.0);
	}

	/** The position of a plant at a color step: the middle of the step shifted by its jitter, in [0, 36). */
	public static double position(int step, int x, int z) {
		return FoliageCurve.cyclic(Math.floorMod(step, SeasonPhase.STEPS) + 0.5 + shift(x, z));
	}

	/** What the client draws of a herb at a color step (the herbs without phases: {@link Look#FULL}). */
	public static Look look(GroundPlant plant, int step, int x, int z) {
		double at = position(step, x, z);
		return switch (plant) {
			case WOOD_ANEMONE -> at >= ANEMONE_FLOWERS && at < ANEMONE_FLOWERS_END ? Look.FULL
					: at >= ANEMONE_SHOOTS && at < ANEMONE_GONE ? Look.LEAVES : Look.NONE;
			case HEPATICA -> at >= HEPATICA_FLOWERS && at < HEPATICA_FLOWERS_END ? Look.FULL : Look.LEAVES;
			case BILBERRY -> at >= BILBERRY_FLOWERS && at < BILBERRY_FLOWERS_END ? Look.FLOWERING : Look.FULL;
			default -> Look.FULL;
		};
	}

	/**
	 * What the client draws of a lily of the valley at a color step (the vanilla block in the Polska world): the full
	 * vanilla model in flower, {@link Look#LEAVES} (the own leaves model, tinted by {@link #lilyColor}) or nothing.
	 */
	public static Look lilyOfTheValley(int step, int x, int z) {
		double at = position(step, x, z);
		return at >= LILY_FLOWERS && at < LILY_FLOWERS_END ? Look.FULL : at >= LILY_SHOOTS && at < LILY_GONE ? Look.LEAVES : Look.NONE;
	}

	/** The color of the lily of the valley leaves at a color step over the raw grass color of the biome. */
	public static int lilyColor(int step, int x, int z, int raw) {
		return color(LILY, position(step, x, z), raw);
	}

	/** Whether a herb has withdrawn underground at a color step: no model and no outline (R12). */
	public static boolean withdrawn(GroundPlant plant, int step, int x, int z) {
		return look(plant, step, x, z) == Look.NONE;
	}

	/** Whether the bilberry ripens its fruit in a sub-season (July and August). */
	public static boolean fruiting(SubSeason subSeason) {
		return subSeason == SubSeason.MID_SUMMER || subSeason == SubSeason.LATE_SUMMER;
	}

	/**
	 * Whether a bilberry plant bears fruit at all (about two in three: in deep shade and on young shoots the bilberry
	 * flowers little), from its position.
	 */
	public static boolean fertile(int x, int y, int z) {
		return unit(x, y, z, 0x5EEDL) < 0.65;
	}

	/** Whether a fertile plant has ripe fruit after the catch-up of a fruiting sub-season (most of them; a serial per sub-season). */
	public static boolean ripeAfterCatchUp(int x, int y, int z, long serial) {
		return unit(x, y, z, 0xBE44L + serial * 0x632BE59BD9B4E019L) < 0.85;
	}

	/** The color of the bilberry leaves at a color step over the raw grass color of the biome. */
	public static int bilberryColor(int step, int x, int z, int raw) {
		return color(BILBERRY, position(step, x, z), raw);
	}

	/** The color of the bracken fronds at a color step over the raw grass color of the biome. */
	public static int brackenColor(int step, int x, int z, int raw) {
		return color(BRACKEN, position(step, x, z), raw);
	}

	private static int color(Key[] keys, double x, int raw) {
		raw &= 0xFFFFFF;
		int n = keys.length;
		int next = 0;
		while (next < n && keys[next].at() <= x) {
			next++;
		}
		Key b = keys[next % n];
		Key a = keys[(next - 1 + n) % n];
		double span = b.at() - a.at();
		double from = x - a.at();
		if (span <= 0) {
			span += SeasonPhase.STEPS;
		}
		if (from < 0) {
			from += SeasonPhase.STEPS;
		}
		double f = Math.clamp(from / span, 0.0, 1.0);
		return FoliageCurve.lerp(FoliageCurve.lerp(raw, a.target(), a.weight()), FoliageCurve.lerp(raw, b.target(), b.weight()), f);
	}

	private static double unit(int x, int y, int z, long salt) {
		long h = x * 0x9E3779B97F4A7C15L + y * 0xD1B54A32D192ED03L + z * 0xC2B2AE3D27D4EB4FL + salt;
		h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
		h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
		h ^= h >>> 31;
		return (h >>> 11) * 0x1.0p-53;
	}
}
