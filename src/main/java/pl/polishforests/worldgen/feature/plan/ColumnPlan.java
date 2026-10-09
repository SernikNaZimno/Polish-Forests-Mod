package pl.polishforests.worldgen.feature.plan;

import java.util.Arrays;
import java.util.List;
import pl.polishforests.worldgen.landscape.Noise;

/**
 * Column plan of a vegetation layer (docs/03-m2-biomy.md §8.2): deadwood, understory, waterside zones, ground layer and
 * aquatic plants walk the 256 columns of a chunk, and each column takes the first palette rule that matches its habitat
 * code ({@link HabitatMatch}), its medium (dry land, dry land with water beside it, the bottom or the surface of water
 * of a given depth) and its top ground block. The rule covers a share of its columns ({@code coverage}) with plants
 * chosen by weight. A pure function of the column data, the palette, the world seed and the layer salt, testable
 * without the game.
 *
 * <p>Both random values of a column (whether it is covered, which plant) are uniform in [0, 1) and depend only on the
 * world coordinates, so there are no seams at chunk borders. A rule with a patch size above 1 draws each value either
 * from the column (40%) or from its patch (60%), a cell of the patch size whose border is shifted by up to one block per
 * column; a mixture of two uniform values is uniform, so the mean coverage and the shares stay the palette's, while
 * plants grow in clumps and the ground layer has bare patches, as in a real forest floor.
 */
public final class ColumnPlan {
	/** Where a plant stands. */
	public enum Medium {
		/** On a dry column (no water above the top ground block). */
		LAND,
		/** On a dry column with water beside its top block (the bank shelf, §7.2). */
		SHORE,
		/** On the bottom of water of a depth within the rule's range. */
		WATER_BOTTOM,
		/** On the surface of water of a depth within the rule's range. */
		WATER_SURFACE
	}

	/** Kind of the top ground block, as far as plants are concerned. */
	public enum Ground {
		GRASS, DIRT, PODZOL, MUD, MOSS, SAND, GRAVEL, CLAY, OTHER
	}

	/** Share of the values of a patchy rule taken from the patch rather than from the column. */
	static final double PATCH_SHARE = 0.6;

	/**
	 * A palette rule resolved for planning.
	 *
	 * @param match    habitat condition
	 * @param medium   medium of the plants
	 * @param minDepth least water depth in blocks (water media only)
	 * @param maxDepth greatest water depth in blocks (water media only)
	 * @param grounds  mask of {@link Ground} ordinals the top block must be (0: any)
	 * @param coverage share of the matching columns that get a plant
	 * @param patch    patch size in blocks (1: no patches)
	 * @param plants   indices of the plants in the layer's plant list
	 * @param weights  weights of the plants
	 */
	public record Rule(HabitatMatch match, Medium medium, int minDepth, int maxDepth, int grounds, double coverage, int patch,
			int[] plants, int[] weights) {
		public Rule {
			if (plants.length != weights.length) {
				throw new IllegalArgumentException("plants and weights differ in length");
			}
		}

		boolean applies(int code, int depth, int ground, boolean shore) {
			boolean medium = switch (this.medium) {
				case LAND -> depth == 0;
				case SHORE -> depth == 0 && shore;
				case WATER_BOTTOM, WATER_SURFACE -> depth >= minDepth && depth <= maxDepth && depth > 0;
			};
			return medium && (grounds == 0 || (grounds >>> ground & 1) != 0) && match.matches(code);
		}
	}

	/** Rules in order: the first rule that applies to a column decides it. */
	public record Palette(List<Rule> rules) {
		public Palette {
			rules = List.copyOf(rules);
		}

		/** Index of the first rule that applies, or -1. */
		public int ruleFor(int code, int depth, int ground, boolean shore) {
			for (int r = 0; r < rules.size(); r++) {
				if (rules.get(r).applies(code, depth, ground, shore)) {
					return r;
				}
			}
			return -1;
		}
	}

	/**
	 * Data of the 256 columns of a chunk (index {@code x * 16 + z}).
	 *
	 * @param codes      habitat codes
	 * @param waterDepth water depth in blocks (0: dry)
	 * @param ground     {@link Ground} ordinal of the top ground block
	 * @param shore      whether a dry column has water beside its top block
	 */
	public record Columns(int[] codes, int[] waterDepth, int[] ground, boolean[] shore) {
	}

	private ColumnPlan() {
	}

	/** Medium of a packed placement. */
	public static Medium medium(int placement) {
		return Medium.values()[placement >>> 14 & 3];
	}

	/** Column ({@code x * 16 + z}) of a packed placement. */
	public static int column(int placement) {
		return placement >>> 16;
	}

	/** Plant index of a packed placement. */
	public static int plant(int placement) {
		return placement & 0x3FFF;
	}

	/**
	 * Placements of the chunk, each packed as {@code column << 16 | medium << 14 | plant}, in column order.
	 *
	 * @param salt salt of the layer (each layer draws independent values)
	 */
	public static int[] of(Columns columns, Palette palette, long worldSeed, int chunkX, int chunkZ, long salt) {
		int[] out = new int[256];
		int n = 0;
		long seed = Noise.mix(worldSeed ^ Noise.mix(salt));
		int x0 = chunkX << 4;
		int z0 = chunkZ << 4;
		for (int i = 0; i < 256; i++) {
			int r = palette.ruleFor(columns.codes()[i], columns.waterDepth()[i], columns.ground()[i], columns.shore()[i]);
			if (r < 0) {
				continue;
			}
			Rule rule = palette.rules().get(r);
			int x = x0 + (i >> 4);
			int z = z0 + (i & 15);
			if (value(seed, x, z, 0x51A7L, rule.patch()) >= rule.coverage()) {
				continue;
			}
			int plant = pick(rule, value(seed, x, z, 0x9E11L, rule.patch()));
			if (plant >= 0) {
				out[n++] = i << 16 | rule.medium().ordinal() << 14 | plant;
			}
		}
		return Arrays.copyOf(out, n);
	}

	/** Plant by weight, or -1 when the rule has none. */
	private static int pick(Rule rule, double v) {
		int total = 0;
		for (int w : rule.weights()) {
			total += w;
		}
		if (total <= 0) {
			return -1;
		}
		double target = v * total;
		for (int k = 0; k < rule.plants().length; k++) {
			target -= rule.weights()[k];
			if (target < 0) {
				return rule.plants()[k];
			}
		}
		return rule.plants()[rule.plants().length - 1];
	}

	/**
	 * Uniform value in [0, 1) of the column at world (x, z): its own value, or, for a patch size above 1, with
	 * probability {@link #PATCH_SHARE} the value of its patch.
	 */
	static double value(long seed, int x, int z, long salt, int patch) {
		long h = hash(seed, x, z, salt);
		double own = TreeStandPlan.unit(h);
		if (patch <= 1) {
			return own;
		}
		long h2 = Noise.mix(h ^ 0x2545_F491_4F6C_DD1DL);
		if ((h2 & 0xFFFF) >= PATCH_SHARE * 0x10000) {
			return own;
		}
		int jx = (int) ((h2 >>> 16 & 0xFF) % 3) - 1;
		int jz = (int) ((h2 >>> 24 & 0xFF) % 3) - 1;
		int cx = Math.floorDiv(x + jx, patch);
		int cz = Math.floorDiv(z + jz, patch);
		return TreeStandPlan.unit(hash(seed, cx, cz, salt ^ 0x7A7C_4E5BL));
	}

	private static long hash(long seed, int x, int z, long salt) {
		return Noise.mix(seed ^ Noise.mix(x * 0x9E37_79B9_7F4A_7C15L + z * 0xC2B2_AE3D_27D4_EB4FL + salt));
	}
}
