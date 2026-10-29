package pl.polishforests.block;

import java.util.function.Predicate;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;
import pl.polishforests.worldgen.habitat.Species;
import pl.polishforests.worldgen.tree.AgeClass;
import pl.polishforests.worldgen.tree.SpeciesShape;
import pl.polishforests.worldgen.tree.TreeSize;

/**
 * The size of the tree a sapling of the mod grows into (step S1 of docs/04-pionowy-wycinek.md, decision CP-C.1 of the user:
 * "as in vanilla, a random size, most often medium"). Each size is a configured feature of the tree planner, an open-grown
 * canopy tree ({@link pl.polishforests.worldgen.feature.ProceduralTreeFeature}) of an age class and a {@link TreeSize}:
 * <ul>
 * <li>{@link #SMALL}: the pole stage, 13–19 blocks with the top leaves, a trunk of 1 px (the tree of the saplings since step V7, feature
 * {@code polishforests:sapling/<species>}, so a sapling of an old world grows as before when it draws this size);</li>
 * <li>{@link #LARGE}: a mature open-grown tree (not old growth) of the species' mature height, at most
 * {@value #LARGE_HEIGHT} blocks (so 20–28 blocks with the planner's spread of ±10% and the top leaves), the trunk and crown
 * scaled alike, the trunk at least {@value #LARGE_DBH} cm at breast height (feature {@code …/large});</li>
 * <li>{@link #MEDIUM}: a young mature tree halfway in height between the pole stage and the large tree (16–24 blocks), with a
 * trunk of at least {@value #MEDIUM_DBH} cm at breast height, so at least 2 px at the base (feature {@code …/medium}).</li>
 * </ul>
 * The factors of the medium and large trees depend on the species ({@link #factors}): the mature allometry of
 * {@link SpeciesShape} scaled to these heights.
 * The size is drawn when the sapling grows, from the random source of the growth ({@link #draw}); when the tree of the
 * drawn size has no room, the sapling tries the next smaller one ({@link #choose}), as vanilla's large trees fall back.
 * Weights and factors are defaults of step S1 (docs/00 G.28 S1-a … S1-c), to be confirmed at CP-D. Never stored: the order
 * of the constants may change.
 */
public enum SaplingSize {
	SMALL(AgeClass.POLE_STAGE, 25, ""),
	MEDIUM(AgeClass.MATURE, 55, "medium"),
	LARGE(AgeClass.MATURE, 20, "large");

	/** Largest height of a large tree before the planner's spread of ±10%, blocks. */
	public static final int LARGE_HEIGHT = 23;
	/** Least diameter at breast height of a medium tree before the planner's spread, cm (a trunk of 2 px at the base). */
	public static final int MEDIUM_DBH = 24;
	/**
	 * Least diameter at breast height of a large tree before the planner's spread, cm: an open-grown tree is thicker than
	 * one of a stand, and the slender birch and hornbeam would otherwise give hardly more logs than the medium tree.
	 */
	public static final int LARGE_DBH = 28;

	private static final SaplingSize[] VALUES = values();
	private static final int TOTAL;

	static {
		int t = 0;
		for (SaplingSize s : VALUES) {
			t += s.weight;
		}
		TOTAL = t;
	}

	private final AgeClass age;
	private final int weight;
	private final String suffix;

	SaplingSize(AgeClass age, int weight, String suffix) {
		this.age = age;
		this.weight = weight;
		this.suffix = suffix;
	}

	/** The age class of the planner's tree. */
	public AgeClass age() {
		return age;
	}

	/**
	 * The size factors of the planner's tree of a species (see the class comment), rounded to hundredths: the small tree
	 * {@link TreeSize#ONE}; the large one the mature height capped at {@value #LARGE_HEIGHT} blocks, trunk and crown by the
	 * same factor, the trunk at least {@value #LARGE_DBH} cm; the medium one half way in height between the pole stage and the large tree, the crown by the same
	 * factor, the trunk at least {@value #MEDIUM_DBH} cm.
	 */
	public TreeSize factors(Species species) {
		SpeciesShape shape = SpeciesShape.of(species);
		if (this == SMALL || shape == null) {
			return TreeSize.ONE;
		}
		double mature = shape.height().mature();
		double large = Math.min(mature, LARGE_HEIGHT);
		double height = this == LARGE ? large : (shape.height().pole() + large) / 2;
		float h = round(height / mature);
		double least = this == LARGE ? LARGE_DBH : MEDIUM_DBH;
		float dbh = round(Math.max(h * shape.dbh().mature(), least) / shape.dbh().mature());
		return new TreeSize(h, dbh, h);
	}

	private static float round(double f) {
		return Math.round(f * 100) / 100F;
	}

	/** The weight of the size in the draw (the weights add up to 100). */
	public int weight() {
		return weight;
	}

	/** The share of the size in the draw. */
	public double share() {
		return weight / (double) TOTAL;
	}

	/** The next smaller size, or {@code null} for the smallest. */
	public @Nullable SaplingSize smaller() {
		return ordinal() == 0 ? null : VALUES[ordinal() - 1];
	}

	/** The path of the configured feature of this size for a species id: {@code sapling/<id>} or {@code sapling/<id>/<size>}. */
	public String featurePath(String speciesId) {
		return suffix.isEmpty() ? "sapling/" + speciesId : "sapling/" + speciesId + "/" + suffix;
	}

	/** Draws a size by the weights, from the random source of the growth. */
	public static SaplingSize draw(RandomSource random) {
		int u = random.nextInt(TOTAL);
		for (SaplingSize s : VALUES) {
			u -= s.weight;
			if (u < 0) {
				return s;
			}
		}
		return VALUES[VALUES.length - 1];
	}

	/**
	 * The size that grows: the drawn size when its tree has room, else the next smaller one that has; {@code null} when
	 * none has (the sapling stays a sapling).
	 */
	public static @Nullable SaplingSize choose(SaplingSize drawn, Predicate<SaplingSize> room) {
		for (SaplingSize s = drawn; s != null; s = s.smaller()) {
			if (room.test(s)) {
				return s;
			}
		}
		return null;
	}
}
