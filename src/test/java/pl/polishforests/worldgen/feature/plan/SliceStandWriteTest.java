package pl.polishforests.worldgen.feature.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntBinaryOperator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.habitat.Species;
import pl.polishforests.worldgen.habitat.Zone;
import pl.polishforests.worldgen.landscape.Noise;
import pl.polishforests.worldgen.tree.AgeClass;
import pl.polishforests.worldgen.tree.CrownLayer;
import pl.polishforests.worldgen.tree.CrownTerritories;
import pl.polishforests.worldgen.tree.CrownTerritory;
import pl.polishforests.worldgen.tree.ForestStandMap;
import pl.polishforests.worldgen.tree.TreeRequest;
import pl.polishforests.worldgen.tree.TreeShapePlan;
import pl.polishforests.worldgen.tree.TreeVoxels;

/**
 * The procedural trees of the slice written into a world of blocks as the tree stand writes them (step V9 of
 * docs/04-pionowy-wycinek.md: the plan of each chunk, the crown territories with the vacancies of the decoration, the
 * planner with the stand map and the ground, and the write rules of {@code ProceduralTreeFeature.write}: wood on air or
 * leaves, leaves on air only, a block whose parent is missing left out), on rolling ground, with the chunks decorated in
 * two orders: the trees are written whole (every planned wood block stands), the two orders give the same blocks, and no
 * leaf is farther than 6 from its tree's wood through its tree's leaves.
 */
@Tag("slow")
class SliceStandWriteTest {
	private static final long SEED = 20260927L;
	private static final GeneratedTreePalette GENERATED = GeneratedTreePalette.load();
	private static final float[] RAMP = {1, 1, 1, 1};
	private static final int SIZE = 6;
	private static final byte WOOD = 1;
	private static final byte LEAF = 2;

	/** A world of blocks: kind and owner tree of each cell. */
	private static final class World {
		final Map<Long, int[]> cells = new HashMap<>();

		int[] at(int x, int y, int z) {
			return cells.get(key(x, y, z));
		}

		void set(int x, int y, int z, byte kind, int owner, int distance) {
			cells.put(key(x, y, z), new int[] {kind, owner, distance});
		}

		static long key(int x, int y, int z) {
			return ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | y & 0xFFF;
		}
	}

	private static int ground(Noise n, int x, int z) {
		return 64 + (int) Math.round(4 * n.at(x, z, 40));
	}

	@Test
	void treesAreWrittenWholeAndTheSameInBothOrders() {
		double k = 1;
		Noise relief = new Noise(SEED).derive("test.relief");
		int ohf = EcotoneTest.code(HabitatBiome.OAK_HORNBEAM_FOREST, Zone.NONE);
		int fpf = EcotoneTest.code(HabitatBiome.FRESH_PINE_FOREST, Zone.NONE);
		Noise biomes = new Noise(SEED).derive("test.biomes");
		IntBinaryOperator world = (x, z) -> biomes.at(x, z, 120) > 0 ? ohf : fpf;
		ForestStandMap map = new ForestStandMap(SEED, k, 0.85);
		CrownTerritories crowns = new CrownTerritories(SEED, map);
		TreeStandPlan.Stand stand = new TreeStandPlan.Stand(RAMP, new Noise(SEED).derive("feature.tree_stand.gaps"), k, map, crowns);
		World[] worlds = new World[2];
		int[] planned = new int[2];
		int[] missing = new int[2];
		for (int order = 0; order < 2; order++) {
			World w = new World();
			worlds[order] = w;
			CrownTerritories.Session session = crowns.session();
			for (int i = 0; i < SIZE * SIZE; i++) {
				int n = order == 0 ? i : SIZE * SIZE - 1 - i;
				int a = order == 0 ? n / SIZE : n % SIZE;
				int b = order == 0 ? n % SIZE : n / SIZE;
				Ecotone.Region region = EcotoneTest.region(a, b, world, k);
				int[] codes = Ecotone.effective(region, Ecotone.Layer.TREES, SEED, Ecotone.TREES_SALT, 1);
				int[] plan = TreeStandPlan.of(codes, new int[256], GENERATED.palette, SEED, a, b, stand);
				StandVacancies v = new StandVacancies(a, b, plan, region, GENERATED.palette, stand, new StandVacancies.Neighbors() {
					@Override
					public int waterDepth(int x, int z) {
						return 0;
					}

					@Override
					public float[] ramp(int chunkX, int chunkZ) {
						return RAMP;
					}
				}, new TreeStandPlan.Candidates(SEED), SEED);
				for (int p : plan) {
					int t = TreeStandPlan.tree(p);
					if (!GENERATED.procedural(t)) {
						continue;
					}
					Species species = GENERATED.species.get(t);
					int c = TreeStandPlan.column(p);
					int x = (a << 4) + (c >> 4);
					int z = (b << 4) + (c & 15);
					CrownLayer layer = TreeStandPlan.layer(p);
					CrownTerritory territory = session.territory(x, z, v);
					AgeClass age = layer == CrownLayer.REGENERATION && map.canopyGap(x, z) ? AgeClass.POLE_STAGE : map.ageClass(x, z);
					int y = ground(relief, x, z) + 1;
					TreeVoxels vox = TreeShapePlan.plan(new TreeRequest(species, layer, age, SEED, x, y, z, territory, session::candidate, map,
							(gx, gz) -> ground(relief, gx, gz)));
					int[] result = write(w, vox, x * 31 + z);
					planned[order] += vox.woodCount();
					missing[order] += vox.woodCount() - result[0];
				}
			}
		}
		// Leaves farther than 6 from their own tree's wood through their tree's leaves (as LeavesBlock computes it, any wood).
		int decaying = 0;
		int leaves = 0;
		for (Map.Entry<Long, int[]> e : worlds[0].cells.entrySet()) {
			if (e.getValue()[0] == LEAF) {
				leaves++;
				decaying += e.getValue()[2] > 6 ? 1 : 0;
			}
		}
		int differ = 0;
		for (Map.Entry<Long, int[]> e : worlds[0].cells.entrySet()) {
			int[] other = worlds[1].cells.get(e.getKey());
			if (other == null || other[0] != e.getValue()[0] || other[1] != e.getValue()[1]) {
				differ++;
			}
		}
		System.out.printf(Locale.ROOT, "[slice write] %d planned wood blocks, %d left out (raster order), %d (reverse order); %d leaves, "
				+ "%d with a distance over 6; %d of %d blocks differ between the orders%n", planned[0], missing[0], missing[1], leaves,
				decaying, differ, worlds[0].cells.size());
		assertEquals(0, missing[0], "wood left out");
		assertEquals(0, missing[1], "wood left out");
		assertEquals(0, decaying);
		assertTrue(differ <= worlds[0].cells.size() / 1000, "blocks differing between the orders: " + differ);
	}

	/** Writes a tree as {@code ProceduralTreeFeature.write} does; returns {wood written, leaves written}. */
	private static int[] write(World w, TreeVoxels v, int owner) {
		int n = v.woodCount();
		boolean[] written = new boolean[n];
		int count = 0;
		for (int i = 0; i < n; i++) {
			int p = v.parentIndex(i);
			if (p >= 0 && !written[p]) {
				continue;
			}
			int[] at = w.at(v.x() + v.woodX(i), v.y() + v.woodY(i), v.z() + v.woodZ(i));
			if (at != null && at[0] == WOOD) {
				continue;
			}
			w.set(v.x() + v.woodX(i), v.y() + v.woodY(i), v.z() + v.woodZ(i), WOOD, owner, 0);
			written[i] = true;
			count++;
		}
		int leaves = v.leafCount();
		boolean[] placeable = new boolean[leaves];
		boolean all = count == n;
		for (int i = 0; i < leaves; i++) {
			placeable[i] = w.at(v.x() + v.leafX(i), v.y() + v.leafY(i), v.z() + v.leafZ(i)) == null;
			all &= placeable[i];
		}
		byte[] d = all ? null : v.leafDistances(written, placeable);
		int placed = 0;
		for (int i = 0; i < leaves; i++) {
			int dist = d == null ? v.leafDistance(i) : d[i];
			if (dist < 1 || !placeable[i]) {
				continue;
			}
			w.set(v.x() + v.leafX(i), v.y() + v.leafY(i), v.z() + v.leafZ(i), LEAF, owner, dist);
			placed++;
		}
		return new int[] {count, placed};
	}
}
