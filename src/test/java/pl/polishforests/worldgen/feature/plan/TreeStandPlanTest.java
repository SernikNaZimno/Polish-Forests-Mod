package pl.polishforests.worldgen.feature.plan;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
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
 * Basic tree stand plan of step S5 (docs/03-m2-biomy.md §8.2): deterministic, the mean number of trees per chunk within
 * ±10% of the palette, candidates at least c/2 apart, species shares within ±5 percentage points of the weights, species
 * with a range flag only where the column has the flag, no trees under water.
 */
class TreeStandPlanTest {
	private static final int CHUNKS = 20_000;

	/** Palette: oak-hornbeam forest 9 trees (oak 60, beech 40 with the beech flag), raised bog 0.3 (one species). */
	private static TreeStandPlan.Palette palette() {
		int n = HabitatBiome.values().length;
		float[] perChunk = new float[n];
		int[][] trees = new int[n][0];
		int[][] weights = new int[n][0];
		int[][] flags = new int[n][0];
		int oak = HabitatBiome.OAK_HORNBEAM_FOREST.ordinal();
		perChunk[oak] = 9;
		trees[oak] = new int[] {0, 1};
		weights[oak] = new int[] {60, 40};
		flags[oak] = new int[] {0, Species.BEECH.flag()};
		int bog = HabitatBiome.RAISED_BOG.ordinal();
		perChunk[bog] = 0.3F;
		trees[bog] = new int[] {2};
		weights[bog] = new int[] {1};
		flags[bog] = new int[] {0};
		return new TreeStandPlan.Palette(perChunk, trees, weights, flags);
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
		long seed = TreeStandPlan.seed(20260927L, 12, -7, TreeStandPlan.SALT);
		assertArrayEquals(TreeStandPlan.of(codes, new boolean[256], palette(), seed),
				TreeStandPlan.of(codes, new boolean[256], palette(), seed));
		assertTrue(seed != TreeStandPlan.seed(20260927L, 13, -7, TreeStandPlan.SALT));
	}

	@Test
	void densitySpacingAndShares() {
		int[] codes = chunk(HabitatBiome.OAK_HORNBEAM_FOREST, Species.FLAG_BEECH);
		long trees = 0;
		long beech = 0;
		int minDistance = Integer.MAX_VALUE;
		for (int c = 0; c < CHUNKS; c++) {
			int[] plan = TreeStandPlan.of(codes, new boolean[256], palette(), TreeStandPlan.seed(1L, c, c * 7, TreeStandPlan.SALT));
			trees += plan.length;
			for (int i = 0; i < plan.length; i++) {
				if ((plan[i] & 0xFFFF) == 1) {
					beech++;
				}
				for (int j = i + 1; j < plan.length; j++) {
					int a = plan[i] >>> 16;
					int b = plan[j] >>> 16;
					minDistance = Math.min(minDistance, Math.max(Math.abs((a >> 4) - (b >> 4)), Math.abs((a & 15) - (b & 15))));
				}
			}
		}
		double mean = (double) trees / CHUNKS;
		System.out.printf(java.util.Locale.ROOT, "trees per chunk %.3f (palette 9), beech %.1f%%, min distance %d%n", mean,
				100.0 * beech / trees, minDistance);
		assertEquals(9.0, mean, 0.9);
		assertEquals(0.40, (double) beech / trees, 0.05);
		// n = 9: cells of 5 blocks, candidates within the inner 50% of their cell (at least c/2 apart).
		assertTrue(minDistance >= 5 / 2, "candidates closer than c/2: " + minDistance);
	}

	@Test
	void rangeFlagsAndWater() {
		int[] noBeech = chunk(HabitatBiome.OAK_HORNBEAM_FOREST, 0);
		boolean[] water = new boolean[256];
		long trees = 0;
		for (int c = 0; c < 2_000; c++) {
			int[] plan = TreeStandPlan.of(noBeech, water, palette(), TreeStandPlan.seed(2L, c, 0, TreeStandPlan.SALT));
			for (int p : plan) {
				assertEquals(0, p & 0xFFFF, "beech outside its range");
			}
			trees += plan.length;
		}
		// Without beech the oak takes its place: the density stays.
		assertEquals(9.0, trees / 2_000.0, 0.9);
		Arrays.fill(water, true);
		assertEquals(0, TreeStandPlan.of(noBeech, water, palette(), 3L).length);
		// A sparse biome: 0.3 trees per chunk.
		int[] bog = chunk(HabitatBiome.RAISED_BOG, 0);
		long bogTrees = 0;
		for (int c = 0; c < CHUNKS; c++) {
			bogTrees += TreeStandPlan.of(bog, new boolean[256], palette(), TreeStandPlan.seed(4L, c, 1, TreeStandPlan.SALT)).length;
		}
		assertEquals(0.3, (double) bogTrees / CHUNKS, 0.03);
		// A biome without a rule: no trees.
		assertEquals(0, TreeStandPlan.of(chunk(HabitatBiome.ARABLE_LAND, 0), new boolean[256], palette(), 5L).length);
	}
}
