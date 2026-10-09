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
import pl.polishforests.worldgen.landscape.Noise;

/**
 * Tree stand plan (docs/03-m2-biomy.md §8.2, §8.5, §9, §12.1): deterministic; one candidate pattern for the whole world,
 * so trunks are at least {@link TreeStandPlan#MIN_SPACING} blocks apart also across chunk borders (and at least c/2 in
 * sparse stands) and every local row of a chunk gets trunks equally often (no lanes along chunk borders, no grid); the
 * mean number of trees per chunk is within ±10% of the palette and the species shares within ±5 points, also with the
 * gaps; the first matching rule applies (zones and associations before the biome); species with a range flag only where
 * the column has the flag, with the range ramp, an alternative species takes the rest of the weight; trees under water
 * only from entries that allow the depth.
 */
class TreeStandPlanTest {
	private static final int CHUNKS = 20_000;
	private static final long SEED = 20260927L;
	private static final int OAK = 0;
	private static final int BEECH = 1;
	private static final int PINE = 2;
	private static final int SPRUCE = 3;
	private static final int WILLOW = 4;
	private static final int POPLAR = 5;
	private static final int STUNTED = 6;

	private static HabitatMatch biome(HabitatBiome b) {
		return HabitatMatch.of(List.of(b), List.of(), List.of(), List.of(), List.of());
	}

	/**
	 * Palette: a treeless zone (willow scrub), the timberline with stunted spruces (4), the Populetum albae association
	 * of the willow-poplar forest (willow 40, poplar 60), the oak-hornbeam forest 9 (oak 60, beech 40 with the beech flag
	 * and spruce as its alternative), the willow-poplar forest 7 (willows 70 also in water 2 deep, poplars 30), raised bog
	 * 0.3, montane spruce forest 12 (the densest palette of §8.5), heath 0.5 and dwarf pine 1.
	 */
	private static TreeStandPlan.Palette palette() {
		List<TreeStandPlan.Rule> rules = new ArrayList<>();
		rules.add(TreeStandPlan.Rule.of(HabitatMatch.of(List.of(), List.of(Zone.WILLOW_SCRUB), List.of(), List.of(), List.of()),
				0, new int[0], new int[0], new int[0]));
		rules.add(TreeStandPlan.Rule.of(HabitatMatch.of(List.of(), List.of(Zone.TIMBERLINE), List.of(), List.of(), List.of()),
				4, new int[] {STUNTED}, new int[] {1}, new int[] {0}));
		rules.add(new TreeStandPlan.Rule(HabitatMatch.of(List.of(HabitatBiome.WILLOW_POPLAR_FOREST), List.of(), List.of(),
				List.of(Association.POPULETUM_ALBAE), List.of()), 7, new int[] {WILLOW, POPLAR}, new int[] {40, 60},
				new int[2], new int[] {-1, -1}, new int[2], new int[] {2, 0}));
		rules.add(new TreeStandPlan.Rule(biome(HabitatBiome.OAK_HORNBEAM_FOREST), 9, new int[] {OAK, BEECH}, new int[] {60, 40},
				new int[] {0, Species.BEECH.flag()}, new int[] {-1, SPRUCE}, new int[] {0, Species.SPRUCE.flag()}, new int[2]));
		rules.add(new TreeStandPlan.Rule(biome(HabitatBiome.WILLOW_POPLAR_FOREST), 7, new int[] {WILLOW, POPLAR},
				new int[] {70, 30}, new int[2], new int[] {-1, -1}, new int[2], new int[] {2, 0}));
		rules.add(TreeStandPlan.Rule.of(biome(HabitatBiome.RAISED_BOG), 0.3F, new int[] {PINE}, new int[] {1}, new int[1]));
		rules.add(TreeStandPlan.Rule.of(biome(HabitatBiome.MONTANE_SPRUCE_FOREST), 12, new int[] {SPRUCE}, new int[] {1},
				new int[1]));
		rules.add(TreeStandPlan.Rule.of(biome(HabitatBiome.HEATH), 0.5F, new int[] {PINE}, new int[] {1}, new int[1]));
		rules.add(TreeStandPlan.Rule.of(biome(HabitatBiome.DWARF_PINE_SCRUB), 1, new int[] {STUNTED}, new int[] {1},
				new int[1]));
		return new TreeStandPlan.Palette(rules);
	}

	private static int[] chunk(HabitatBiome biome, int flags) {
		return chunk(biome, Zone.NONE, Association.TYPICAL, flags);
	}

	private static int[] chunk(HabitatBiome biome, Zone zone, Association association, int flags) {
		int[] codes = new int[256];
		Arrays.fill(codes, Habitat.pack(biome, zone, ForestSiteType.NONE, association, LandCover.forBiome(biome), flags,
				Soil.forBiome(biome, zone)));
		return codes;
	}

	private static final int[] DRY = new int[256];
	private static final TreeStandPlan.Stand PLAIN = TreeStandPlan.Stand.PLAIN;

	private static int[] plan(int[] codes, long seed, int cx, int cz) {
		return TreeStandPlan.of(codes, DRY, palette(), seed, cx, cz, PLAIN);
	}

	@Test
	void deterministic() {
		int[] codes = chunk(HabitatBiome.OAK_HORNBEAM_FOREST, Species.FLAG_BEECH);
		TreeStandPlan.Stand stand = new TreeStandPlan.Stand(new float[] {0.5F, 1, 1, 1}, new Noise(SEED).derive("test"), 1);
		assertArrayEquals(TreeStandPlan.of(codes, DRY, palette(), SEED, 12, -7, stand),
				TreeStandPlan.of(codes, DRY, palette(), SEED, 12, -7, stand));
		assertTrue(!Arrays.equals(plan(codes, SEED, 12, -7), plan(codes, SEED, 13, -7)));
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
		List<int[]> trunks = trunks(codes, size, PLAIN);
		long[] rowX = new long[16];
		long[] rowZ = new long[16];
		for (int[] t : trunks) {
			rowX[t[0] & 15]++;
			rowZ[t[1] & 15]++;
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
		boolean[][] grid = grid(trunks, span);
		double borderSum = 0;
		int borderN = 0;
		double innerSum = 0;
		int innerN = 0;
		for (int[] t : trunks) {
			int nearest2 = nearest2(grid, t, 6);
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

	/** Trunks (world x, z) of a uniform area of size × size chunks from (0, 0). */
	private static List<int[]> trunks(int[] codes, int size, TreeStandPlan.Stand stand) {
		List<int[]> trunks = new ArrayList<>();
		for (int cx = 0; cx < size; cx++) {
			for (int cz = 0; cz < size; cz++) {
				for (int p : TreeStandPlan.of(codes, DRY, palette(), SEED, cx, cz, stand)) {
					int column = p >>> 16;
					trunks.add(new int[] {cx * 16 + (column >> 4), cz * 16 + (column & 15)});
				}
			}
		}
		return trunks;
	}

	private static boolean[][] grid(List<int[]> trunks, int span) {
		boolean[][] grid = new boolean[span][span];
		for (int[] t : trunks) {
			grid[t[0]][t[1]] = true;
		}
		return grid;
	}

	/** Squared distance to the nearest other trunk within the reach, or {@code Integer.MAX_VALUE}. */
	private static int nearest2(boolean[][] grid, int[] t, int reach) {
		int nearest2 = Integer.MAX_VALUE;
		for (int dx = -reach; dx <= reach; dx++) {
			for (int dz = -reach; dz <= reach; dz++) {
				int x = t[0] + dx;
				int z = t[1] + dz;
				if ((dx != 0 || dz != 0) && x >= 0 && z >= 0 && x < grid.length && z < grid.length && grid[x][z]) {
					nearest2 = Math.min(nearest2, dx * dx + dz * dz);
				}
			}
		}
		return nearest2;
	}

	/**
	 * Spacing of §12.1: trunks at least c/2 apart with c = 16 / ⌈√n⌉ (the mesh of a stratified sample of n trees per
	 * chunk), and the mean number of trees within ±10% of n also where sparse stands are thinned.
	 */
	@Test
	void spacingOfHalfTheMeshAndCounts() {
		record Case(HabitatBiome biome, Zone zone, double n, int size) {
		}
		for (Case c : List.of(new Case(HabitatBiome.MONTANE_SPRUCE_FOREST, Zone.NONE, 12, 40),
				new Case(HabitatBiome.OAK_HORNBEAM_FOREST, Zone.NONE, 9, 40),
				new Case(HabitatBiome.WILLOW_POPLAR_FOREST, Zone.NONE, 7, 40),
				new Case(HabitatBiome.MONTANE_SPRUCE_FOREST, Zone.TIMBERLINE, 4, 60),
				new Case(HabitatBiome.DWARF_PINE_SCRUB, Zone.NONE, 1, 100),
				new Case(HabitatBiome.HEATH, Zone.NONE, 0.5, 140),
				new Case(HabitatBiome.RAISED_BOG, Zone.NONE, 0.3, 180))) {
			int[] codes = chunk(c.biome(), c.zone(), Association.TYPICAL, 0);
			List<int[]> trunks = trunks(codes, c.size(), PLAIN);
			double mesh = 16.0 / Math.ceil(Math.sqrt(c.n()));
			int least = Integer.MAX_VALUE;
			boolean[][] grid = grid(trunks, c.size() * 16);
			for (int[] t : trunks) {
				least = Math.min(least, nearest2(grid, t, (int) Math.ceil(mesh)));
			}
			double perChunk = (double) trunks.size() / (c.size() * c.size());
			System.out.printf(Locale.ROOT, "%s %s: %.3f trees per chunk (palette %.1f), nearest trunk %.2f blocks, c/2 = %.2f%n",
					c.biome().id(), c.zone().id(), perChunk, c.n(), Math.sqrt(least), mesh / 2);
			assertEquals(c.n(), perChunk, 0.10 * c.n(), c.biome().id() + " trees per chunk");
			assertTrue(least >= mesh * mesh / 4 - 1e-9, c.biome().id() + ": trunks closer than c/2");
		}
	}

	@Test
	void densityAndShares() {
		int[] codes = chunk(HabitatBiome.OAK_HORNBEAM_FOREST, Species.FLAG_BEECH);
		long trees = 0;
		long beech = 0;
		for (int c = 0; c < CHUNKS; c++) {
			int[] plan = plan(codes, SEED, c % 200, c / 200);
			trees += plan.length;
			for (int p : plan) {
				if ((p & 0xFFFF) == BEECH) {
					beech++;
				}
			}
		}
		double mean = (double) trees / CHUNKS;
		System.out.printf(Locale.ROOT, "trees per chunk %.3f (palette 9), beech %.1f%%%n", mean, 100.0 * beech / trees);
		assertEquals(9.0, mean, 0.02 * 9);
		assertEquals(0.40, (double) beech / trees, 0.02);
	}

	/** Zone and association rules come first: no trees on willow scrub, Populetum albae with more poplars, stunted spruces. */
	@Test
	void zonesAndAssociations() {
		assertEquals(0, plan(chunk(HabitatBiome.WILLOW_POPLAR_FOREST, Zone.WILLOW_SCRUB, Association.TYPICAL, 0), SEED, 3, 4).length);
		long poplars = 0;
		long all = 0;
		for (int c = 0; c < 5_000; c++) {
			for (int p : plan(chunk(HabitatBiome.WILLOW_POPLAR_FOREST, Zone.NONE, Association.POPULETUM_ALBAE, 0), SEED, c, 3)) {
				poplars += (p & 0xFFFF) == POPLAR ? 1 : 0;
				all++;
			}
		}
		assertEquals(0.60, (double) poplars / all, 0.05);
		for (int p : plan(chunk(HabitatBiome.MONTANE_SPRUCE_FOREST, Zone.TIMBERLINE, Association.TYPICAL, 0), SEED, 1, 1)) {
			assertEquals(STUNTED, p & 0xFFFF);
		}
	}

	/**
	 * Gaps: the noise removes the trees from about {@link TreeStandPlan#GAP_SHARE} of the area, the mean per chunk stays
	 * the palette's within ±10%, the shares stay within ±5 points, and inside a gap there are no trees.
	 */
	@Test
	void gaps() {
		Noise gapNoise = new Noise(SEED).derive("feature.tree_stand.gaps");
		long inGap = 0;
		long cells = 0;
		for (int x = 0; x < 20_000; x += 7) {
			for (int z = 0; z < 20_000; z += 7) {
				cells++;
				inGap += gapNoise.at(x, z, TreeStandPlan.GAP_WAVELENGTH) > TreeStandPlan.GAP_THRESHOLD ? 1 : 0;
			}
		}
		double share = (double) inGap / cells;
		System.out.printf(Locale.ROOT, "gap share %.3f (constant %.3f)%n", share, TreeStandPlan.GAP_SHARE);
		assertEquals(TreeStandPlan.GAP_SHARE, share, 0.01);
		assertTrue(share >= 0.05 && share <= 0.10, "§8.2: gaps remove 5–10%");
		TreeStandPlan.Stand stand = new TreeStandPlan.Stand(new float[] {1, 1, 1, 1}, gapNoise, 1);
		int[] codes = chunk(HabitatBiome.OAK_HORNBEAM_FOREST, Species.FLAG_BEECH);
		long trees = 0;
		long beech = 0;
		int chunks = 0;
		for (int cx = 0; cx < 150; cx++) {
			for (int cz = 0; cz < 150; cz++) {
				chunks++;
				for (int p : TreeStandPlan.of(codes, DRY, palette(), SEED, cx, cz, stand)) {
					int column = p >>> 16;
					int x = cx * 16 + (column >> 4);
					int z = cz * 16 + (column & 15);
					assertTrue(gapNoise.at(x, z, TreeStandPlan.GAP_WAVELENGTH) <= TreeStandPlan.GAP_THRESHOLD, "tree in a gap");
					trees++;
					beech += (p & 0xFFFF) == BEECH ? 1 : 0;
				}
			}
		}
		double mean = (double) trees / chunks;
		System.out.printf(Locale.ROOT, "with gaps: %.3f trees per chunk (palette 9), beech %.1f%%%n", mean, 100.0 * beech / trees);
		assertEquals(9.0, mean, 0.10 * 9);
		assertEquals(0.40, (double) beech / trees, 0.05);
	}

	/** The range ramp scales the weight of a flagged species; its alternative takes the rest. */
	@Test
	void rangeRamp() {
		int[] both = chunk(HabitatBiome.OAK_HORNBEAM_FOREST, Species.FLAG_BEECH | Species.FLAG_SPRUCE);
		int[] beechOnly = chunk(HabitatBiome.OAK_HORNBEAM_FOREST, Species.FLAG_BEECH);
		long[] counts = new long[7];
		long[] countsBeechOnly = new long[7];
		TreeStandPlan.Stand half = new TreeStandPlan.Stand(new float[] {0.5F, 1, 1, 1}, null, 1);
		for (int c = 0; c < 10_000; c++) {
			for (int p : TreeStandPlan.of(both, DRY, palette(), SEED, c, 9, half)) {
				counts[p & 0xFFFF]++;
			}
			for (int p : TreeStandPlan.of(beechOnly, DRY, palette(), SEED, c, 9, half)) {
				countsBeechOnly[p & 0xFFFF]++;
			}
		}
		long all = Arrays.stream(counts).sum();
		// Beech 40 × 0.5 = 20, spruce takes the other 20, oak 60.
		assertEquals(0.20, (double) counts[BEECH] / all, 0.02);
		assertEquals(0.20, (double) counts[SPRUCE] / all, 0.02);
		long allBeechOnly = Arrays.stream(countsBeechOnly).sum();
		// Without spruce in range: beech 20 against oak 60, i.e. 25%.
		assertEquals(0.25, (double) countsBeechOnly[BEECH] / allBeechOnly, 0.02);
		assertEquals(0, countsBeechOnly[SPRUCE]);
		// SpeciesRamp: full weight a ramp width inside the range, nothing out of range, about half at the threshold
		// without shift.
		assertEquals(1.0, SpeciesRamp.factors(0.6, 0, 0)[0], 1e-6);
		assertEquals(0.5, SpeciesRamp.factors(0.45, 0, 0)[0], 1e-6);
		assertEquals(0.0, SpeciesRamp.factors(0.40, 0, 0)[0], 1e-6);
		assertEquals(1.0, SpeciesRamp.factors(0.1, 0, 0)[2], 1e-6);
		assertEquals(0.0, SpeciesRamp.factors(0.6, 0.55, -1)[1], 1e-6);
		assertEquals(1.0, SpeciesRamp.factors(0.6, 0, 0)[3], 1e-6);
	}

	@Test
	void rangeFlagsAlternativesAndWater() {
		// Neither beech nor spruce in range: the oak takes the whole weight, the density stays.
		int[] neither = chunk(HabitatBiome.OAK_HORNBEAM_FOREST, 0);
		long trees = 0;
		for (int c = 0; c < 2_000; c++) {
			int[] plan = plan(neither, 2L, c, 0);
			for (int p : plan) {
				assertEquals(OAK, p & 0xFFFF, "beech or spruce outside its range");
			}
			trees += plan.length;
		}
		assertEquals(9.0, trees / 2_000.0, 0.05 * 9);
		// Spruce in range, beech not: the spruce takes the beech's weight (40%).
		int[] spruceOnly = chunk(HabitatBiome.OAK_HORNBEAM_FOREST, Species.FLAG_SPRUCE);
		long all = 0;
		long spruce = 0;
		for (int c = 0; c < 5_000; c++) {
			for (int p : plan(spruceOnly, 3L, c, 1)) {
				assertTrue((p & 0xFFFF) != BEECH, "beech outside its range");
				spruce += (p & 0xFFFF) == SPRUCE ? 1 : 0;
				all++;
			}
		}
		assertEquals(0.40, (double) spruce / all, 0.03);
		// Under water one block deep: no oaks, but willows (up to 2 blocks) in the willow-poplar forest, not 3 deep.
		int[] water = new int[256];
		Arrays.fill(water, 1);
		assertEquals(0, TreeStandPlan.of(neither, water, palette(), 3L, 0, 0, PLAIN).length);
		int[] willows = chunk(HabitatBiome.WILLOW_POPLAR_FOREST, 0);
		long inWater = 0;
		for (int c = 0; c < 500; c++) {
			for (int p : TreeStandPlan.of(willows, water, palette(), 3L, c, 0, PLAIN)) {
				assertEquals(WILLOW, p & 0xFFFF, "only willows in water");
				inWater++;
			}
		}
		// In water only the willows can stand, so they take the whole stand of 7.
		assertEquals(7, inWater / 500.0, 0.10 * 7);
		Arrays.fill(water, 3);
		assertEquals(0, TreeStandPlan.of(willows, water, palette(), 3L, 0, 0, PLAIN).length);
		// A sparse biome: 0.3 trees per chunk.
		int[] bog = chunk(HabitatBiome.RAISED_BOG, 0);
		long bogTrees = 0;
		for (int c = 0; c < CHUNKS; c++) {
			bogTrees += plan(bog, 4L, c, 1).length;
		}
		assertEquals(0.3, (double) bogTrees / CHUNKS, 0.03);
		// A biome without a rule: no trees.
		assertEquals(0, plan(chunk(HabitatBiome.ARABLE_LAND, 0), 5L, 0, 0).length);
	}
}
