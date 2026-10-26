package pl.polishforests.worldgen.feature.plan;

import java.util.Arrays;
import java.util.List;
import pl.polishforests.worldgen.landscape.Noise;

/**
 * Column plan of a vegetation layer (docs/03-m2-biomy.md §8.2): deadwood, understory, waterside zones, ground layer and
 * aquatic plants walk the 256 columns of a chunk, and each column takes the first palette rule that matches its habitat
 * code ({@link HabitatMatch}), its medium (dry land, dry land with the model's water beside it, dry land with only a
 * puddle beside it, dry land at the seaward edge of its zone, the bottom or the surface of water of a given depth) and
 * its top ground block. The rule covers a share of its columns ({@code coverage}) with plants
 * chosen by weight. A pure function of the column data, the palette, the world seed and the layer salt, testable
 * without the game.
 *
 * <p>Both random values of a column (whether it is covered, which plant) are uniform in [0, 1) and depend only on the
 * world coordinates, so there are no seams at chunk borders. A rule with a patch size above 1 draws each value either
 * from the column (40%) or from its patch (60%), a cell of the patch size whose border is shifted by up to one block per
 * column; a mixture of two uniform values is uniform, so the mean coverage and the shares stay the palette's, while
 * plants grow in clumps and the ground layer has bare patches, as in a real forest floor.
 *
 * <p>The patches of a rule with {@link PatchShape#BLOBS} (step V12; the CP-B remark on the pine forest: flat patches of
 * litter and bare ground with rectangular edges) are the cells of a jittered grid of the patch size seen through a
 * smooth warp of about half a patch ({@link #blob}): rounded, irregular patches of mixed sizes without straight edges,
 * with the same mean coverage and shares (each column still takes one uniform value, its own or its patch's).
 */
public final class ColumnPlan {
	/** Where a plant stands (new constants only at the end: the order is part of the packed placements). */
	public enum Medium {
		/** On a dry column (no water above the top ground block). */
		LAND,
		/**
		 * On a dry column with water of the landscape model beside its top block (the bank shelf of rivers, lakes and the
		 * sea, §7.2; since the S7 review not a puddle of the micro-relief).
		 */
		SHORE,
		/** On the bottom of water of a depth within the rule's range. */
		WATER_BOTTOM,
		/** On the surface of water of a depth within the rule's range. */
		WATER_SURFACE,
		/** On a dry column with only puddles of the micro-relief beside its top block (§7.3). */
		PUDDLE_SHORE,
		/**
		 * On a dry column within 2 blocks of the wet beach or the sea: the seaward edge of the strandline, the line of the
		 * beach wrack (§5.2).
		 */
		SEAWARD_EDGE;

		private static final Medium[] VALUES = values();
	}

	/** Bits of {@link Columns#side}: the model's water beside the top block. */
	public static final int SIDE_WATER = 1;
	/** Bits of {@link Columns#side}: a puddle of the micro-relief beside the top block. */
	public static final int SIDE_PUDDLE = 2;
	/** Bits of {@link Columns#side}: the wet beach or the sea within 2 blocks. */
	public static final int SIDE_SEAWARD = 4;

	/** Kind of the top ground block, as far as plants are concerned. */
	public enum Ground {
		GRASS, DIRT, PODZOL, MUD, MOSS, SAND, GRAVEL, CLAY, OTHER
	}

	/** Share of the values of a patchy rule taken from the patch rather than from the column. */
	static final double PATCH_SHARE = 0.6;
	/** The same for the rounded patches ({@link PatchShape#BLOBS}): more coherent, the edges still frayed. */
	static final double BLOB_SHARE = 0.75;
	/** Amplitude of the warp of the rounded patches, in patch sizes. */
	static final double BLOB_WARP = 0.55;
	/** Salt of the warp of the rounded patches ({@code feature.ground.patches}, ZW-2). */
	static final long BLOB_SALT = 0x4F1B_6C3A_29D7_E085L;

	/** Shape of the patches of a rule (new constants only at the end: the palettes name them). */
	public enum PatchShape {
		/** Square cells of the patch size, their borders shifted by up to a block per column (M2). */
		CELLS,
		/** Rounded, irregular patches (step V12). */
		BLOBS
	}

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
	 * @param edges    mask of {@link Ecotone.Edge} ordinals the column must have (0: any; step S8b)
	 * @param shape    shape of the patches (step V12)
	 */
	public record Rule(HabitatMatch match, Medium medium, int minDepth, int maxDepth, int grounds, double coverage, int patch,
			int[] plants, int[] weights, int edges, PatchShape shape) {
		public Rule {
			if (plants.length != weights.length) {
				throw new IllegalArgumentException("plants and weights differ in length");
			}
		}

		/** A rule with square patches. */
		public Rule(HabitatMatch match, Medium medium, int minDepth, int maxDepth, int grounds, double coverage, int patch,
				int[] plants, int[] weights, int edges) {
			this(match, medium, minDepth, maxDepth, grounds, coverage, patch, plants, weights, edges, PatchShape.CELLS);
		}

		/** A rule for any forest edge class. */
		public Rule(HabitatMatch match, Medium medium, int minDepth, int maxDepth, int grounds, double coverage, int patch,
				int[] plants, int[] weights) {
			this(match, medium, minDepth, maxDepth, grounds, coverage, patch, plants, weights, 0);
		}

		boolean applies(int code, int depth, int ground, int side, int edge) {
			if (edges != 0 && (edges >>> edge & 1) == 0) {
				return false;
			}
			boolean medium = switch (this.medium) {
				case LAND -> depth == 0;
				case SHORE -> depth == 0 && (side & SIDE_WATER) != 0;
				case PUDDLE_SHORE -> depth == 0 && (side & (SIDE_WATER | SIDE_PUDDLE)) == SIDE_PUDDLE;
				case SEAWARD_EDGE -> depth == 0 && (side & SIDE_SEAWARD) != 0;
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

		/** Index of the first rule that applies to a column without a forest edge, or -1. */
		public int ruleFor(int code, int depth, int ground, int side) {
			return ruleFor(code, depth, ground, side, 0);
		}

		/** Index of the first rule that applies, or -1. */
		public int ruleFor(int code, int depth, int ground, int side, int edge) {
			for (int r = 0; r < rules.size(); r++) {
				if (rules.get(r).applies(code, depth, ground, side, edge)) {
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
	 * @param side       what is beside a dry column ({@link #SIDE_WATER}, {@link #SIDE_PUDDLE}, {@link #SIDE_SEAWARD})
	 * @param edge       forest edge class of each column ({@link Ecotone.Edge} ordinals), or null for none (step S8b)
	 */
	public record Columns(int[] codes, int[] waterDepth, int[] ground, byte[] side, byte[] edge) {
		/** Columns without forest edges. */
		public Columns(int[] codes, int[] waterDepth, int[] ground, byte[] side) {
			this(codes, waterDepth, ground, side, null);
		}

		/** The same columns with other habitat codes and forest edge classes (the ecotones of a layer). */
		public Columns with(int[] codes, byte[] edge) {
			return new Columns(codes, waterDepth, ground, side, edge);
		}
	}

	private ColumnPlan() {
	}

	/** Medium of a packed placement. */
	public static Medium medium(int placement) {
		return Medium.VALUES[placement >>> 13 & 7];
	}

	/** Column ({@code x * 16 + z}) of a packed placement. */
	public static int column(int placement) {
		return placement >>> 16;
	}

	/** Plant index of a packed placement. */
	public static int plant(int placement) {
		return placement & 0x1FFF;
	}

	/**
	 * Placements of the chunk, each packed as {@code column << 16 | medium << 13 | plant}, in column order.
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
			int r = palette.ruleFor(columns.codes()[i], columns.waterDepth()[i], columns.ground()[i], columns.side()[i],
					columns.edge() == null ? 0 : columns.edge()[i]);
			if (r < 0) {
				continue;
			}
			Rule rule = palette.rules().get(r);
			int x = x0 + (i >> 4);
			int z = z0 + (i & 15);
			if (value(seed, x, z, 0x51A7L, rule.patch(), rule.shape()) >= rule.coverage()) {
				continue;
			}
			int plant = pick(rule, value(seed, x, z, 0x9E11L, rule.patch(), rule.shape()));
			if (plant >= 0) {
				out[n++] = i << 16 | rule.medium().ordinal() << 13 | plant;
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

	/** Uniform value in [0, 1) of the column at world (x, z) for patches of a shape. */
	static double value(long seed, int x, int z, long salt, int patch, PatchShape shape) {
		return shape == PatchShape.BLOBS && patch > 1 ? blob(seed, x, z, salt, patch) : value(seed, x, z, salt, patch);
	}

	/**
	 * Uniform value in [0, 1) of the column at world (x, z) with rounded patches: its own value, or with probability
	 * {@link #BLOB_SHARE} the value of its patch, the nearest point of a jittered grid of the patch size (one point per
	 * cell, anywhere in it) to the column moved by a smooth warp ({@link #smooth}, wavelength two patches, amplitude
	 * {@link #BLOB_WARP} patches). The patches are rounded polygons of mixed sizes whose borders bend; a column takes the
	 * uniform value of one point, so the mean coverage and the shares stay the palette's.
	 */
	static double blob(long seed, int x, int z, long salt, int patch) {
		long h = hash(seed, x, z, salt);
		long h2 = Noise.mix(h ^ 0x2545_F491_4F6C_DD1DL);
		if ((h2 & 0xFFFF) >= BLOB_SHARE * 0x10000) {
			return TreeStandPlan.unit(h);
		}
		long warpSeed = seed ^ BLOB_SALT ^ salt;
		double wave = 2.0 * patch;
		double wx = (x + 0.5) / patch + BLOB_WARP * (2 * smooth(warpSeed, x / wave, z / wave) - 1);
		double wz = (z + 0.5) / patch + BLOB_WARP * (2 * smooth(warpSeed ^ 0x6A09_E667_F3BC_C909L, x / wave, z / wave) - 1);
		int cx = (int) Math.floor(wx);
		int cz = (int) Math.floor(wz);
		double best = Double.MAX_VALUE;
		long bestHash = 0;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				long c = hash(seed, cx + dx, cz + dz, salt ^ 0x7A7C_4E5BL);
				double px = cx + dx + TreeStandPlan.unit(Noise.mix(c ^ 0x1L));
				double pz = cz + dz + TreeStandPlan.unit(Noise.mix(c ^ 0x2L));
				double d = (px - wx) * (px - wx) + (pz - wz) * (pz - wz);
				if (d < best) {
					best = d;
					bestHash = c;
				}
			}
		}
		return TreeStandPlan.unit(Noise.mix(bestHash ^ 0x3L));
	}

	/** Smooth value noise in [0, 1] at (u, v) in lattice units: smoothstep interpolation of hashed lattice values. */
	static double smooth(long seed, double u, double v) {
		int iu = (int) Math.floor(u);
		int iv = (int) Math.floor(v);
		double fu = u - iu;
		double fv = v - iv;
		fu = fu * fu * (3 - 2 * fu);
		fv = fv * fv * (3 - 2 * fv);
		double a = TreeStandPlan.unit(hash(seed, iu, iv, 0x3C6E_F372L));
		double b = TreeStandPlan.unit(hash(seed, iu + 1, iv, 0x3C6E_F372L));
		double c = TreeStandPlan.unit(hash(seed, iu, iv + 1, 0x3C6E_F372L));
		double d = TreeStandPlan.unit(hash(seed, iu + 1, iv + 1, 0x3C6E_F372L));
		return a + (b - a) * fu + (c - a) * fv + (a - b - c + d) * fu * fv;
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
