package pl.polishforests.worldgen.feature;

import java.util.Locale;
import pl.polishforests.worldgen.feature.plan.TreeStandPlan;

/**
 * Random sources of the decoration seeded per object position (step L1 of docs/04-pionowy-wycinek.md, DP-107 and
 * DP-115): the column layers ({@link PlantLayerFeature}) and the placeholder trees of the tree stand
 * ({@link TreeStandFeature}) draw each plant's and each tree's values from a source seeded from (world seed, world
 * column, salt), so the result of a chunk's decoration does not depend on which of its objects a neighbor chunk's
 * object had already displaced. Before L1 one source per chunk was drawn object after object, and an object skipped
 * because its place was taken (which depends on the order in which the chunk and its neighbors are decorated) shifted
 * the draws of every later object of the chunk.
 *
 * <p>The system property {@value #PROPERTY} {@code = chunk} ({@code -PdecorationRandom=chunk}) restores the chunk-wide
 * sequence of before L1 for the whole run (test switch, as {@code -ProotedDirtShare} of step O2): the determinism test
 * and the {@code roots} frames can then show the defect and its fix in one build.
 */
public final class DecorationRandom {
	/** System property of the test switch: {@code position} (default) or {@code chunk} (before step L1). */
	public static final String PROPERTY = "polishforests.decorationRandom";
	/** Whether this run draws with the chunk-wide sequence of before step L1. */
	public static final boolean CHUNK_SEQUENCE = chunkSequence(System.getProperty(PROPERTY));
	/**
	 * Salt of the random source of a plant of a column layer ({@code feature.plant.position_random}, a new salt of step L1;
	 * the plan's salts are unchanged), mixed with the layer's salt, so the layers of one column draw independently.
	 */
	public static final long PLANT_SALT = 0x2F8C_61D4_A95B_E317L;
	/** Salt of the random source of a placeholder tree ({@code feature.tree.position_random}, a new salt of step L1). */
	public static final long TREE_SALT = 0x61E3_9A0F_D47C_2B85L;
	/**
	 * Salt of the random source of the hanging vines of one leaf of a placeholder tree ({@code feature.tree.leaf_vine},
	 * a new salt of the second stage of step L1): the vanilla {@code leave_vine} decorator draws leaf after leaf from the
	 * tree's source, so a leaf that a neighbor chunk's object had displaced shifted the vines of the whole crown.
	 */
	public static final long VINE_SALT = 0x3B7E_C519_08F2_A6D3L;
	/**
	 * Salt of the hop on the trunks and shrubs of the floodplain forests ({@code feature.tree.hop}, a new salt of step F5,
	 * {@link TreeHop}, {@link HopDecorator}): the draws of a tree's strands and of a shrub block's sides.
	 */
	public static final long HOP_SALT = 0x7A4D_E2B1_96C0_358FL;
	/** Odd multiplier that spreads the height of a leaf over the salt (the golden ratio of 64 bits). */
	private static final long HEIGHT_MIX = 0x9E37_79B9_7F4A_7C15L;

	private DecorationRandom() {
	}

	static boolean chunkSequence(String property) {
		if (property == null || property.isBlank()) {
			return false;
		}
		return switch (property.trim().toLowerCase(Locale.ROOT)) {
			case "position" -> false;
			case "chunk" -> true;
			default -> throw new IllegalArgumentException(PROPERTY + " must be 'position' or 'chunk': " + property);
		};
	}

	/** Seed of the random source of the plant of a layer with the salt {@code layerSalt} at world column (x, z). */
	public static long plantSeed(long worldSeed, int x, int z, long layerSalt) {
		return seed(worldSeed, x, z, layerSalt ^ PLANT_SALT);
	}

	/** Seed of the random source of the vines of the leaf at world position (x, y, z) (a pure function). */
	public static long leafSeed(long worldSeed, int x, int y, int z) {
		return seed(worldSeed, x, z, VINE_SALT + y * HEIGHT_MIX);
	}

	/** Seed of the random source of the object at world column (x, z) with this salt (a pure function). */
	public static long seed(long worldSeed, int x, int z, long salt) {
		return TreeStandPlan.seed(worldSeed, x, z, salt);
	}
}
