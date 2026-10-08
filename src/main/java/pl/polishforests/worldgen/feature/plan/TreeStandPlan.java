package pl.polishforests.worldgen.feature.plan;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import pl.polishforests.worldgen.habitat.Habitat;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.landscape.Noise;

/**
 * Tree stand plan of one chunk (docs/03-m2-biomy.md §8.2): a pure function of the chunk habitats, the palette, the world
 * seed and the chunk position, testable without the game.
 *
 * <p>Candidate trunks form one pattern for the whole world, independent of chunk borders: a world-aligned grid of
 * {@value #CELL} × {@value #CELL} block cells has one candidate per cell at a random column of the cell (so every column
 * is equally likely), and a candidate is kept only if no kept candidate with a lower random mark stands closer than
 * {@value #MIN_SPACING} blocks (sequential inhibition in the order of the marks, evaluated locally and recursively, so a
 * chunk sees the same kept candidates near its border as its neighbor). The kept candidates have a known mean density of
 * {@value #DENSITY} per chunk; a kept candidate becomes a tree with probability n_b / {@value #DENSITY}, where n_b is the
 * number of trees per chunk of the biome of its column, so the stand has no grid, no lanes along chunk borders and at least
 * {@value #MIN_SPACING} blocks between trunks. Then a species from the weights of the column's biome, among the species
 * within their range (§9); a species out of range may give its weight to an alternative species ("beech or spruce",
 * §8.5). Columns under water get no tree (willows and alders in shallow water: step S7).
 *
 * <p>Basic version of step S5: palettes per biome only, without zones, gaps and the range ramp.
 */
public final class TreeStandPlan {
	/** Salt of the tree stand layer in the dispatcher seed and in the candidate pattern. */
	public static final long SALT = 0x7EE5_7A9DL;
	/** Size of the candidate cells in blocks (divides 16, so a chunk holds whole cells). */
	public static final int CELL = 2;
	/** Smallest distance between two trunks in blocks. */
	public static final int MIN_SPACING = 3;
	/**
	 * Mean number of kept candidates per chunk (measured over 100,000 chunks in {@code TreeStandPlanTest}); the largest
	 * possible number of trees per chunk.
	 */
	public static final double DENSITY = 16.65;

	private static final int CELLS = 16 / CELL;
	private static final int MIN_SPACING_SQ = MIN_SPACING * MIN_SPACING;

	/**
	 * Palette resolved for planning: for each biome ordinal the number of trees per chunk and the candidate trees
	 * (indices into the dispatcher's tree list), their weights and the range flag masks ({@code Species.flag()}, 0 for
	 * none), and for each entry an alternative tree used when the entry's species is out of range (-1 for none) with its
	 * range flag mask.
	 */
	public record Palette(float[] treesPerChunk, int[][] trees, int[][] weights, int[][] flags, int[][] alternatives,
			int[][] alternativeFlags) {
		public Palette {
			int n = HabitatBiome.values().length;
			if (treesPerChunk.length != n || trees.length != n || weights.length != n || flags.length != n
					|| alternatives.length != n || alternativeFlags.length != n) {
				throw new IllegalArgumentException("palette needs an entry for each of the " + n + " biomes");
			}
		}

		/** A palette without alternative species. */
		public Palette(float[] treesPerChunk, int[][] trees, int[][] weights, int[][] flags) {
			this(treesPerChunk, trees, weights, flags, noAlternatives(trees), noAlternatives(trees));
		}

		private static int[][] noAlternatives(int[][] trees) {
			int[][] out = new int[trees.length][];
			for (int i = 0; i < trees.length; i++) {
				out[i] = new int[trees[i].length];
				Arrays.fill(out[i], -1);
			}
			return out;
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
	public static int[] of(int[] codes, boolean[] water, Palette palette, long worldSeed, int chunkX, int chunkZ) {
		boolean any = false;
		boolean[] seen = new boolean[HabitatBiome.values().length];
		for (int code : codes) {
			int b = Habitat.biome(code).ordinal();
			if (!seen[b]) {
				seen[b] = true;
				any |= palette.treesPerChunk()[b] > 0;
			}
		}
		if (!any) {
			return new int[0];
		}
		Pattern pattern = new Pattern(worldSeed);
		int[] out = new int[CELLS * CELLS];
		int n = 0;
		for (int i = 0; i < CELLS; i++) {
			for (int j = 0; j < CELLS; j++) {
				int gx = chunkX * CELLS + i;
				int gz = chunkZ * CELLS + j;
				if (!pattern.kept(gx, gz)) {
					continue;
				}
				long h = pattern.hash(gx, gz);
				int column = (i * CELL + offsetX(h)) << 4 | (j * CELL + offsetZ(h));
				int code = codes[column];
				int b = Habitat.biome(code).ordinal();
				long h2 = Noise.mix(h ^ 0x5DEE_CE66_DA11L);
				if (water[column] || unit(h2) >= palette.treesPerChunk()[b] / DENSITY) {
					continue;
				}
				int tree = pickTree(palette, b, Habitat.flags(code), unit(Noise.mix(h2)));
				if (tree >= 0) {
					out[n++] = column << 16 | tree;
				}
			}
		}
		return Arrays.copyOf(out, n);
	}

	/** Kept candidates of the chunk (local columns {@code x * 16 + z}), for tests: the pattern before biome thinning. */
	static int[] keptCandidates(long worldSeed, int chunkX, int chunkZ) {
		Pattern pattern = new Pattern(worldSeed);
		int[] out = new int[CELLS * CELLS];
		int n = 0;
		for (int i = 0; i < CELLS; i++) {
			for (int j = 0; j < CELLS; j++) {
				int gx = chunkX * CELLS + i;
				int gz = chunkZ * CELLS + j;
				if (pattern.kept(gx, gz)) {
					long h = pattern.hash(gx, gz);
					out[n++] = (i * CELL + offsetX(h)) << 4 | (j * CELL + offsetZ(h));
				}
			}
		}
		return Arrays.copyOf(out, n);
	}

	private static int offsetX(long h) {
		return (int) (h & (CELL - 1));
	}

	private static int offsetZ(long h) {
		return (int) ((h >>> 8) & (CELL - 1));
	}

	/** Uniform value in [0, 1) from the high 53 bits of a hash. */
	private static double unit(long h) {
		return (h >>> 11) * 0x1.0p-53;
	}

	/**
	 * The world's candidate pattern: candidate of cell (gx, gz) and its mark from a hash of (world seed, cell); a
	 * candidate is kept unless a kept candidate with a lower mark is closer than {@link #MIN_SPACING}. Kept states are
	 * memoized for one chunk.
	 */
	private static final class Pattern {
		private final long seed;
		private final Map<Long, Boolean> kept = new HashMap<>();

		Pattern(long worldSeed) {
			this.seed = worldSeed ^ SALT;
		}

		long hash(int gx, int gz) {
			return Noise.mix(seed ^ Noise.mix(gx * 0x9E37_79B9_7F4A_7C15L + gz * 0xC2B2_AE3D_27D4_EB4FL));
		}

		boolean kept(int gx, int gz) {
			long key = (long) gx << 32 | (gz & 0xFFFF_FFFFL);
			Boolean known = kept.get(key);
			if (known != null) {
				return known;
			}
			long h = hash(gx, gz);
			int x = gx * CELL + offsetX(h);
			int z = gz * CELL + offsetZ(h);
			boolean result = true;
			// With cells of 2 blocks and a spacing of 3, only the 8 neighboring cells can hold a closer candidate.
			for (int di = -1; di <= 1 && result; di++) {
				for (int dj = -1; dj <= 1; dj++) {
					if (di == 0 && dj == 0) {
						continue;
					}
					long hn = hash(gx + di, gz + dj);
					int dx = (gx + di) * CELL + offsetX(hn) - x;
					int dz = (gz + dj) * CELL + offsetZ(hn) - z;
					if (dx * dx + dz * dz < MIN_SPACING_SQ && lower(hn, gx + di, gz + dj, h, gx, gz)
							&& kept(gx + di, gz + dj)) {
						result = false;
						break;
					}
				}
			}
			kept.put(key, result);
			return result;
		}

		/** Whether mark a (cell a) is lower than mark b (cell b); ties broken by the cell, so the order is strict. */
		private static boolean lower(long a, int ax, int az, long b, int bx, int bz) {
			int c = Long.compareUnsigned(a, b);
			if (c != 0) {
				return c < 0;
			}
			return ax != bx ? ax < bx : az < bz;
		}
	}

	/** Tree by weight among the species of biome {@code b} within their range (or their alternatives), or -1. */
	private static int pickTree(Palette palette, int b, int rangeFlags, double pick) {
		int[] trees = palette.trees()[b];
		int[] weights = palette.weights()[b];
		int total = 0;
		for (int i = 0; i < trees.length; i++) {
			if (resolve(palette, b, i, rangeFlags) >= 0) {
				total += weights[i];
			}
		}
		if (total == 0) {
			return -1;
		}
		double target = pick * total;
		for (int i = 0; i < trees.length; i++) {
			int tree = resolve(palette, b, i, rangeFlags);
			if (tree >= 0) {
				target -= weights[i];
				if (target < 0) {
					return tree;
				}
			}
		}
		return -1;
	}

	/** Tree of entry {@code i}: its species if within range, else its alternative if within range, else -1. */
	private static int resolve(Palette palette, int b, int i, int rangeFlags) {
		int flag = palette.flags()[b][i];
		if (flag == 0 || (rangeFlags & flag) != 0) {
			return palette.trees()[b][i];
		}
		int alt = palette.alternatives()[b][i];
		int altFlag = palette.alternativeFlags()[b][i];
		return alt >= 0 && (altFlag == 0 || (rangeFlags & altFlag) != 0) ? alt : -1;
	}
}
