package pl.polishforests.worldgen.feature.plan;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.function.IntBinaryOperator;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.landscape.Noise;
import pl.polishforests.worldgen.tree.AgeClass;
import pl.polishforests.worldgen.tree.CrownTerritories;
import pl.polishforests.worldgen.tree.ForestStandMap;

/**
 * Step OP2 of docs/04-pionowy-wycinek.md: the memos and shortcuts of the vacancies give what the code before them gave.
 *
 * <ul>
 * <li>The meander of the region's columns from the corners computed once per region ({@code Ecotone.Region.warpAt})
 * equals {@code Ecotone.warpAt} in every column of the region and beyond it (the fallback), in both scales and for a
 * second world seed after the first.</li>
 * <li>The provable tree code from the memo of the region ({@link Ecotone#provableTrees}) equals the code computed without
 * it, asked in any order, with and without the wide blend, beyond the region too (and a new blend empties the memo).</li>
 * <li>A candidate's sure treelessness with the highest chances of a chunk computed once ({@link TreeStandPlan.Highest})
 * equals the answer of before, which went through the rules for every candidate: the generated palette, stands with and
 * without the gap noise and the crown territories, managed and natural stands of every age class with canopy gaps.</li>
 * </ul>
 */
class StandMemoOp2Test {
	private static final long SEED = 20260927L;
	private static final GeneratedTreePalette GENERATED = GeneratedTreePalette.load();

	@Test
	void meanderOfTheRegionAsComputedColumnByColumn() {
		for (double k : new double[] {1.0, 0.5}) {
			IntBinaryOperator w = EcotoneProvableTest.world(k);
			for (int cx = -3; cx < 3; cx++) {
				for (int cz = -3; cz < 3; cz++) {
					Ecotone.Region region = EcotoneTest.region(cx, cz, w, k);
					for (long seed : new long[] {SEED, SEED + 1}) {
						int[] got = new int[2];
						for (int x = -40; x < 56; x++) {
							for (int z = -40; z < 56; z++) {
								int wx = (cx << 4) + x;
								int wz = (cz << 4) + z;
								region.warpAt(seed, wx, wz, got);
								assertArrayEquals(Ecotone.warpAt(seed, k, wx, wz), got, "column " + wx + ", " + wz);
							}
						}
					}
				}
			}
		}
	}

	@Test
	void provableCodesFromTheMemoAsWithoutIt() {
		for (double k : new double[] {1.0, 0.5}) {
			IntBinaryOperator w = EcotoneProvableTest.world(k);
			ForestBlend blend = new ForestBlend(SEED, k, w::applyAsInt);
			for (int cx = -3; cx < 3; cx++) {
				for (int cz = -3; cz < 3; cz++) {
					for (boolean blended : new boolean[] {false, true}) {
						Ecotone.Region region = EcotoneTest.region(cx, cz, w, k);
						if (blended) {
							region.withBlend(blend);
						}
						// Every column twice, the second pass in reverse order (from the memo), and beyond the region.
						for (int pass = 0; pass < 2; pass++) {
							for (int n = 0; n < 56 * 56; n++) {
								int i = pass == 0 ? n : 56 * 56 - 1 - n;
								int x = i / 56 - 20;
								int z = i % 56 - 20;
								assertEquals(Ecotone.provableTreesOf(region, SEED, Ecotone.TREES_SALT, x, z),
										Ecotone.provableTrees(region, SEED, Ecotone.TREES_SALT, x, z), "column " + x + ", " + z);
							}
						}
						// Another salt does not read the memo of the first.
						assertEquals(Ecotone.provableTreesOf(region, SEED, Ecotone.PLANTS_SALT, 3, -5),
								Ecotone.provableTrees(region, SEED, Ecotone.PLANTS_SALT, 3, -5));
					}
					// The blend given after the memo was filled: the codes of the blend, not of the ramps.
					Ecotone.Region region = EcotoneTest.region(cx, cz, w, k);
					for (int i = 0; i < 256; i++) {
						Ecotone.provableTrees(region, SEED, Ecotone.TREES_SALT, i >> 4, i & 15);
					}
					region.withBlend(blend);
					for (int i = 0; i < 256; i++) {
						assertEquals(Ecotone.provableTreesOf(region, SEED, Ecotone.TREES_SALT, i >> 4, i & 15),
								Ecotone.provableTrees(region, SEED, Ecotone.TREES_SALT, i >> 4, i & 15), "blended column " + i);
					}
				}
			}
		}
	}

	@Test
	void candidatesFromTheMasksOfTheTilesAsFromTheKeptStates() {
		java.util.Random random = new java.util.Random(27);
		TreeStandPlan.Candidates candidates = new TreeStandPlan.Candidates(SEED);
		// A small memo that is emptied on the way (more tiles than its limit are asked over the rounds).
		it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap masks = new it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap();
		long visited = 0;
		for (int round = 0; round < 3000; round++) {
			int minX = random.nextInt(4000) - 2000;
			int minZ = random.nextInt(4000) - 2000;
			int maxX = minX + random.nextInt(60) - 2;
			int maxZ = minZ + random.nextInt(60) - 2;
			java.util.List<Long> before = new java.util.ArrayList<>();
			java.util.List<Long> now = new java.util.ArrayList<>();
			candidates.forEach(minX, minZ, maxX, maxZ, (x, z) -> before.add((long) x << 32 | z & 0xFFFF_FFFFL));
			candidates.forEach(minX, minZ, maxX, maxZ, (x, z) -> now.add((long) x << 32 | z & 0xFFFF_FFFFL), masks);
			assertEquals(before, now, "area " + minX + ", " + minZ + " … " + maxX + ", " + maxZ);
			visited += before.size();
		}
		for (int tx = -20; tx < 20; tx++) {
			for (int tz = -20; tz < 20; tz++) {
				long mask = candidates.keptMask(tx, tz);
				for (int i = 0; i < TreeStandPlan.Candidates.TILE; i++) {
					for (int j = 0; j < TreeStandPlan.Candidates.TILE; j++) {
						boolean bit = (mask >>> (i * TreeStandPlan.Candidates.TILE + j) & 1) != 0;
						int gx = tx * TreeStandPlan.Candidates.TILE + i;
						int gz = tz * TreeStandPlan.Candidates.TILE + j;
						boolean[] any = {false};
						candidates.forEach(gx * TreeStandPlan.CELL, gz * TreeStandPlan.CELL, gx * TreeStandPlan.CELL + TreeStandPlan.CELL - 1,
								gz * TreeStandPlan.CELL + TreeStandPlan.CELL - 1, (x, z) -> any[0] = true);
						assertEquals(any[0], bit, "cell " + gx + ", " + gz);
					}
				}
			}
		}
		System.out.println("[op2] candidates from the tile masks as before: " + visited);
	}

	@Test
	void sureTreelessnessWithTheHighestChancesOfTheChunkAsBefore() {
		float[] ramp = {1, 1, 1, 1};
		Noise gaps = new Noise(SEED).derive("feature.tree_stand.gaps");
		TreeStandPlan.Candidates candidates = new TreeStandPlan.Candidates(SEED);
		ForestStandMap world = new ForestStandMap(SEED, 1, 0.85);
		long checked = 0;
		long treeless = 0;
		for (int variant = 0; variant < 4; variant++) {
			TreeStandPlan.Stand stand = switch (variant) {
				case 0 -> new TreeStandPlan.Stand(ramp, null, 1);
				case 1 -> new TreeStandPlan.Stand(ramp, gaps, 1);
				case 2 -> new TreeStandPlan.Stand(ramp, gaps, 1, world, new CrownTerritories(SEED, world));
				default -> {
					pl.polishforests.worldgen.tree.StandMap old = pl.polishforests.worldgen.tree.StandMap.uniform(AgeClass.OLD_GROWTH, true);
					yield new TreeStandPlan.Stand(ramp, null, 0.5, old, new CrownTerritories(SEED, old));
				}
			};
			TreeStandPlan.Highest highest = GENERATED.palette.highest(stand);
			for (int x = -300; x < 300; x++) {
				for (int z = -300; z < 300; z += 3) {
					if (!candidates.kept(x, z)) {
						continue;
					}
					boolean before = GENERATED.palette.surelyTreeless(candidates, x, z, stand);
					assertEquals(before, GENERATED.palette.surelyTreeless(candidates, x, z, stand, highest), "variant " + variant + " at " + x + ", " + z);
					checked++;
					treeless += before ? 1 : 0;
				}
			}
		}
		System.out.println("[op2] sure treelessness of " + checked + " candidates as before (" + treeless + " treeless)");
	}
}
