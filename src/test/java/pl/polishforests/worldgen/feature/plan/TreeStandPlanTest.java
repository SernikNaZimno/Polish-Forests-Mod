package pl.polishforests.worldgen.feature.plan;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.habitat.Association;
import pl.polishforests.worldgen.habitat.ForestSiteType;
import pl.polishforests.worldgen.habitat.Habitat;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.habitat.LandCover;
import pl.polishforests.worldgen.habitat.Soil;
import pl.polishforests.worldgen.habitat.Species;
import pl.polishforests.worldgen.habitat.Zone;

/**
 * Tree stand plan (docs/03-m2-biomy.md §8.2): deterministic; one candidate pattern for the whole world, so trunks are
 * at least {@link TreeStandPlan#MIN_SPACING} blocks apart also across chunk borders and every local row of a chunk gets
 * trunks equally often (no lanes along chunk borders, no grid); the mean number of trees per chunk and the species
 * shares match the palette; species with a range flag only where the column has the flag, an alternative species takes
 * the weight out of range; no trees under water.
 */
class TreeStandPlanTest {
	private static final int CHUNKS = 20_000;
	private static final long SEED = 20260927L;

	/**
	 * Palette: oak-hornbeam forest 9 trees (oak 60, beech 40 with the beech flag and spruce as its alternative), raised bog
	 * 0.3 (one species), montane spruce forest 12 (the densest palette of §8.5).
	 */
	private static TreeStandPlan.Palette palette() {
		int n = HabitatBiome.values().length;
		float[] perChunk = new float[n];
		int[][] trees = new int[n][0];
		int[][] weights = new int[n][0];
		int[][] flags = new int[n][0];
		int[][] alternatives = new int[n][0];
		int[][] alternativeFlags = new int[n][0];
		int oak = HabitatBiome.OAK_HORNBEAM_FOREST.ordinal();
		perChunk[oak] = 9;
		trees[oak] = new int[] {0, 1};
		weights[oak] = new int[] {60, 40};
		flags[oak] = new int[] {0, Species.BEECH.flag()};
		alternatives[oak] = new int[] {-1, 3};
		alternativeFlags[oak] = new int[] {0, Species.SPRUCE.flag()};
		int bog = HabitatBiome.RAISED_BOG.ordinal();
		perChunk[bog] = 0.3F;
		trees[bog] = new int[] {2};
		weights[bog] = new int[] {1};
		flags[bog] = new int[] {0};
		alternatives[bog] = new int[] {-1};
		alternativeFlags[bog] = new int[] {0};
		int spruce = HabitatBiome.MONTANE_SPRUCE_FOREST.ordinal();
		perChunk[spruce] = 12;
		trees[spruce] = new int[] {3};
		weights[spruce] = new int[] {1};
		flags[spruce] = new int[] {0};
		alternatives[spruce] = new int[] {-1};
		alternativeFlags[spruce] = new int[] {0};
		return new TreeStandPlan.Palette(perChunk, trees, weights, flags, alternatives, alternativeFlags);
	}

	private static int[] chunk(HabitatBiome biome, int flags) {
		int[] codes = new int[256];
		Arrays.fill(codes, Habitat.pack(biome, Zone.NONE, ForestSiteType.NONE, Association.TYPICAL, LandCover.forBiome(biome),
				flags, Soil.forBiome(biome, Zone.NONE)));
		return codes;
	}

	@Test
	void deterministic() {
		int[] codes = chunk(HabitatBiome.OAK_HORNBEAM_FOREST, Species.FLAG_BEECH);
		assertArrayEquals(TreeStandPlan.of(codes, new boolean[256], palette(), SEED, 12, -7),
				TreeStandPlan.of(codes, new boolean[256], palette(), SEED, 12, -7));
		assertTrue(!Arrays.equals(TreeStandPlan.of(codes, new boolean[256], palette(), SEED, 12, -7),
				TreeStandPlan.of(codes, new boolean[256], palette(), SEED, 13, -7)));
	}

	/** The kept candidates have the documented density, the largest number of trees per chunk. */
	@Test
	void candidateDensity() {
		long kept = 0;
		int chunks = 100_000;
		for (int c = 0; c < chunks; c++) {
			kept += TreeStandPlan.keptCandidates(SEED + c / 1_000, c % 1_000 - 500, c * 7 % 911 - 400).length;
		}
		double density = (double) kept / chunks;
		System.out.printf(Locale.ROOT, "kept candidates per chunk %.3f (constant %.2f)%n", density, TreeStandPlan.DENSITY);
		assertEquals(TreeStandPlan.DENSITY, density, 0.005 * TreeStandPlan.DENSITY);
	}

	/**
	 * Over a contiguous area of 48 × 48 chunks of the densest palette: no two trunks closer than the minimum spacing,
	 * also across chunk borders; every local row x and z gets trunks equally often (each row within ±12% of 1/16), so
	 * there are no lanes along chunk borders; the nearest-neighbor distance of trunks next to a chunk border equals the
	 * one inside the chunk.
	 */
	@Test
	void noLanesAndSpacingAcrossChunkBorders() {
		int size = 48;
		int[] codes = chunk(HabitatBiome.MONTANE_SPRUCE_FOREST, 0);
		List<int[]> trunks = new ArrayList<>();
		long[] rowX = new long[16];
		long[] rowZ = new long[16];
		for (int cx = 0; cx < size; cx++) {
			for (int cz = 0; cz < size; cz++) {
				for (int p : TreeStandPlan.of(codes, new boolean[256], palette(), SEED, cx, cz)) {
					int column = p >>> 16;
					rowX[column >> 4]++;
					rowZ[column & 15]++;
					trunks.add(new int[] {cx * 16 + (column >> 4), cz * 16 + (column & 15)});
				}
			}
		}
		double perChunk = (double) trunks.size() / (size * size);
		System.out.printf(Locale.ROOT, "montane spruce: %.2f trees per chunk (palette 12), rows x %s, rows z %s%n", perChunk,
				Arrays.toString(rowX), Arrays.toString(rowZ));
		assertEquals(12.0, perChunk, 0.04 * 12);
		double expected = trunks.size() / 16.0;
		for (int r = 0; r < 16; r++) {
			assertEquals(expected, rowX[r], 0.12 * expected, "local row x = " + r);
			assertEquals(expected, rowZ[r], 0.12 * expected, "local row z = " + r);
		}
		int span = size * 16;
		int[][] grid = new int[span][span];
		for (int[] t : trunks) {
			grid[t[0]][t[1]] = 1;
		}
		double borderSum = 0;
		int borderN = 0;
		double innerSum = 0;
		int innerN = 0;
		for (int[] t : trunks) {
			int nearest2 = Integer.MAX_VALUE;
			for (int dx = -6; dx <= 6; dx++) {
				for (int dz = -6; dz <= 6; dz++) {
					int x = t[0] + dx;
					int z = t[1] + dz;
					if ((dx != 0 || dz != 0) && x >= 0 && z >= 0 && x < span && z < span && grid[x][z] == 1) {
						nearest2 = Math.min(nearest2, dx * dx + dz * dz);
					}
				}
			}
			assertTrue(nearest2 >= TreeStandPlan.MIN_SPACING * TreeStandPlan.MIN_SPACING,
					"trunks closer than the minimum spacing at " + t[0] + ", " + t[1]);
			if (t[0] < 16 || t[1] < 16 || t[0] >= span - 16 || t[1] >= span - 16 || nearest2 == Integer.MAX_VALUE) {
				continue;
			}
			int lx = t[0] & 15;
			int lz = t[1] & 15;
			if (lx == 0 || lx == 15 || lz == 0 || lz == 15) {
				borderSum += Math.sqrt(nearest2);
				borderN++;
			} else if (lx >= 3 && lx <= 12 && lz >= 3 && lz <= 12) {
				innerSum += Math.sqrt(nearest2);
				innerN++;
			}
		}
		double border = borderSum / borderN;
		double inner = innerSum / innerN;
		System.out.printf(Locale.ROOT, "nearest trunk: %.2f blocks at chunk borders, %.2f inside%n", border, inner);
		assertEquals(inner, border, 0.05 * inner);
	}

	@Test
	void densityAndShares() {
		int[] codes = chunk(HabitatBiome.OAK_HORNBEAM_FOREST, Species.FLAG_BEECH);
		long trees = 0;
		long beech = 0;
		for (int c = 0; c < CHUNKS; c++) {
			int[] plan = TreeStandPlan.of(codes, new boolean[256], palette(), SEED, c % 200, c / 200);
			trees += plan.length;
			for (int p : plan) {
				if ((p & 0xFFFF) == 1) {
					beech++;
				}
			}
		}
		double mean = (double) trees / CHUNKS;
		System.out.printf(Locale.ROOT, "trees per chunk %.3f (palette 9), beech %.1f%%%n", mean, 100.0 * beech / trees);
		assertEquals(9.0, mean, 0.02 * 9);
		assertEquals(0.40, (double) beech / trees, 0.02);
	}

	@Test
	void rangeFlagsAlternativesAndWater() {
		boolean[] water = new boolean[256];
		// Neither beech nor spruce in range: the oak takes the whole weight, the density stays.
		int[] neither = chunk(HabitatBiome.OAK_HORNBEAM_FOREST, 0);
		long trees = 0;
		for (int c = 0; c < 2_000; c++) {
			int[] plan = TreeStandPlan.of(neither, water, palette(), 2L, c, 0);
			for (int p : plan) {
				assertEquals(0, p & 0xFFFF, "beech or spruce outside its range");
			}
			trees += plan.length;
		}
		assertEquals(9.0, trees / 2_000.0, 0.05 * 9);
		// Spruce in range, beech not: the spruce takes the beech's weight (40%).
		int[] spruceOnly = chunk(HabitatBiome.OAK_HORNBEAM_FOREST, Species.FLAG_SPRUCE);
		long all = 0;
		long spruce = 0;
		for (int c = 0; c < 5_000; c++) {
			for (int p : TreeStandPlan.of(spruceOnly, water, palette(), 3L, c, 1)) {
				assertTrue((p & 0xFFFF) != 1, "beech outside its range");
				spruce += (p & 0xFFFF) == 3 ? 1 : 0;
				all++;
			}
		}
		assertEquals(0.40, (double) spruce / all, 0.03);
		Arrays.fill(water, true);
		assertEquals(0, TreeStandPlan.of(neither, water, palette(), 3L, 0, 0).length);
		// A sparse biome: 0.3 trees per chunk.
		int[] bog = chunk(HabitatBiome.RAISED_BOG, 0);
		long bogTrees = 0;
		for (int c = 0; c < CHUNKS; c++) {
			bogTrees += TreeStandPlan.of(bog, new boolean[256], palette(), 4L, c, 1).length;
		}
		assertEquals(0.3, (double) bogTrees / CHUNKS, 0.03);
		// A biome without a rule: no trees.
		assertEquals(0, TreeStandPlan.of(chunk(HabitatBiome.ARABLE_LAND, 0), new boolean[256], palette(), 5L, 0, 0).length);
	}
}
