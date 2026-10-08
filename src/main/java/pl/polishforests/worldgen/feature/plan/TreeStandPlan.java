package pl.polishforests.worldgen.feature.plan;

import java.util.Arrays;
import java.util.SplittableRandom;
import pl.polishforests.worldgen.habitat.Habitat;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.landscape.Noise;

/**
 * Tree stand plan of one chunk (docs/03-m2-biomy.md §8.2): a pure function of the chunk habitats, the palette, the world
 * seed and the chunk position, testable without the game. Stratified sampling: a grid with cell size
 * c = 16 / ⌈√n⌉, where n is the largest number of trees per chunk among the biomes present in the chunk; one candidate per
 * cell, offset within the inner 50% of the cell (so neighboring candidates are at least about c/2 apart), accepted with
 * probability n_b / cells for the biome b of its column; then a species from the weights of the column's biome, among the
 * species within their range (§9). Columns under water get no tree (willows and alders in shallow water: step S7).
 *
 * <p>Basic version of step S5: palettes per biome only, without zones, gaps and the range ramp.
 */
public final class TreeStandPlan {
	/** Salt of the tree stand layer in the dispatcher seed. */
	public static final long SALT = 0x7EE5_7A9DL;

	/**
	 * Palette resolved for planning: for each biome ordinal the number of trees per chunk and the candidate trees
	 * (indices into the dispatcher's tree list), their weights and the range flag masks ({@code Species.flag()}, 0 for
	 * none).
	 */
	public record Palette(float[] treesPerChunk, int[][] trees, int[][] weights, int[][] flags) {
		public Palette {
			int n = HabitatBiome.values().length;
			if (treesPerChunk.length != n || trees.length != n || weights.length != n || flags.length != n) {
				throw new IllegalArgumentException("palette needs an entry for each of the " + n + " biomes");
			}
		}
	}

	private TreeStandPlan() {
	}

	/** Seed of a dispatcher layer in a chunk: (world seed, chunk, layer salt). */
	public static long seed(long worldSeed, int chunkX, int chunkZ, long salt) {
		return Noise.mix(worldSeed ^ Noise.mix(chunkX * 0x9E37_79B9_7F4A_7C15L + chunkZ * 0xC2B2_AE3D_27D4_EB4FL + salt));
	}

	/**
	 * Trees of the chunk, each packed as {@code column << 16 | tree}, where column is {@code x * 16 + z} and tree the
	 * index into the palette's tree list.
	 *
	 * @param codes habitat codes of the 256 columns
	 * @param water whether each column is under water
	 */
	public static int[] of(int[] codes, boolean[] water, Palette palette, long seed) {
		float max = 0;
		boolean[] seen = new boolean[HabitatBiome.values().length];
		for (int code : codes) {
			int b = Habitat.biome(code).ordinal();
			if (!seen[b]) {
				seen[b] = true;
				max = Math.max(max, palette.treesPerChunk()[b]);
			}
		}
		if (max <= 0) {
			return new int[0];
		}
		int cell = 16 / (int) Math.ceil(Math.sqrt(max));
		int perRow = 16 / cell;
		int cells = perRow * perRow;
		int inner = Math.max(1, cell / 2);
		SplittableRandom random = new SplittableRandom(seed);
		int[] out = new int[cells];
		int n = 0;
		for (int cx = 0; cx < perRow; cx++) {
			for (int cz = 0; cz < perRow; cz++) {
				int x = cx * cell + cell / 4 + random.nextInt(inner);
				int z = cz * cell + cell / 4 + random.nextInt(inner);
				double accept = random.nextDouble();
				double pick = random.nextDouble();
				int column = x << 4 | z;
				int code = codes[column];
				int b = Habitat.biome(code).ordinal();
				if (water[column] || accept >= palette.treesPerChunk()[b] / cells) {
					continue;
				}
				int tree = pickTree(palette, b, Habitat.flags(code), pick);
				if (tree >= 0) {
					out[n++] = column << 16 | tree;
				}
			}
		}
		return Arrays.copyOf(out, n);
	}

	/** Tree by weight among the species of biome {@code b} within their range, or -1. */
	private static int pickTree(Palette palette, int b, int rangeFlags, double pick) {
		int[] trees = palette.trees()[b];
		int[] weights = palette.weights()[b];
		int[] flags = palette.flags()[b];
		int total = 0;
		for (int i = 0; i < trees.length; i++) {
			if (flags[i] == 0 || (rangeFlags & flags[i]) != 0) {
				total += weights[i];
			}
		}
		if (total == 0) {
			return -1;
		}
		double target = pick * total;
		for (int i = 0; i < trees.length; i++) {
			if (flags[i] == 0 || (rangeFlags & flags[i]) != 0) {
				target -= weights[i];
				if (target < 0) {
					return trees[i];
				}
			}
		}
		return -1;
	}
}
