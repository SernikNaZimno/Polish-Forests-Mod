package pl.polishforests.worldgen.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.habitat.Species;

/**
 * Invariants of the tree planner (step V6 of docs/04-pionowy-wycinek.md, §5): determinism, the parent chain to the root,
 * radii that never grow towards the tips, leaves within 6 steps of the wood, writes only in the 3 × 3 chunks around the
 * trunk, no disputes between the crowns of neighboring trees, the pine's bark zones, the layer split surfaces, no empty
 * leaf row in a stand, the branch shapes (risk R4) and the archetype silhouettes.
 */
class TreeShapePlanTest {
	private static final long SEED = 20260927L;

	/**
	 * Trees of every species, layer and age class at a few columns (also at chunk corners), without a territory: grown
	 * alone (even k) or in the stand form of a uniform stand (odd k).
	 */
	private static List<TreeRequest> lone(int perCell) {
		List<TreeRequest> out = new ArrayList<>();
		Random random = new Random(7);
		for (Species s : SpeciesShape.species()) {
			for (CrownLayer layer : CrownLayer.values()) {
				for (AgeClass age : AgeClass.values()) {
					for (int k = 0; k < perCell; k++) {
						int x = k == 0 ? 16 * 5 : k == 1 ? 16 * -3 + 15 : random.nextInt(4000) - 2000;
						int z = k == 0 ? 16 * -7 + 15 : k == 1 ? 16 * 9 : random.nextInt(4000) - 2000;
						out.add(new TreeRequest(s, layer, age, SEED, x, 64, z, null, null, k % 2 == 1
								? StandMap.uniform(age, true) : null));
					}
				}
			}
		}
		return out;
	}

	@Test
	void deterministicWhateverTheOrderAndTheWorkspace() {
		List<TreeRequest> requests = lone(6);
		List<TreeVoxels> first = new ArrayList<>();
		for (TreeRequest r : requests) {
			first.add(TreeShapePlan.plan(r));
		}
		// A fresh workspace, the requests in reverse order, each planned twice in a row.
		TreeShapePlan.Workspace w = TreeShapePlan.workspace();
		for (int i = requests.size() - 1; i >= 0; i--) {
			assertEquals(first.get(i), w.plan(requests.get(i)), "tree " + requests.get(i));
			assertEquals(first.get(i), w.plan(requests.get(i)), "tree planned again " + requests.get(i));
		}
		// Other threads, other workspaces.
		List<TreeVoxels> parallel = requests.parallelStream().map(TreeShapePlan::plan).toList();
		assertEquals(first, parallel);
		// The base height only moves the tree; another seed gives another tree.
		TreeRequest r = requests.get(3);
		TreeVoxels moved = TreeShapePlan.plan(new TreeRequest(r.species(), r.layer(), r.age(), SEED, r.x(), r.y() + 20, r.z(), null,
				null, r.stands()));
		assertEquals(first.get(3).woodCount(), moved.woodCount());
		assertEquals(first.get(3).leafCount(), moved.leafCount());
		assertNotEquals(first.get(3), TreeShapePlan.plan(new TreeRequest(r.species(), r.layer(), r.age(), SEED + 1, r.x(), r.y(),
				r.z(), null, null, r.stands())));
	}

	@Test
	void parentChainReachesTheRootAndRadiiNeverGrowTowardsTheTips() {
		for (TreeRequest r : lone(6)) {
			TreeVoxels v = TreeShapePlan.plan(r);
			chain(v, r.toString());
		}
		for (TreeVoxels v : stand(StandMap.uniform(AgeClass.OLD_GROWTH, true), true, 0, 0).trees()) {
			chain(v, v.toString());
		}
	}

	/** The root is block 0 at (0, 0, 0) on the ground, every other block's parent precedes it and is its neighbor. */
	static void chain(TreeVoxels v, String what) {
		assertTrue(v.woodCount() > 0, what);
		assertEquals(0, v.woodX(0), what);
		assertEquals(0, v.woodY(0), what);
		assertEquals(0, v.woodZ(0), what);
		assertEquals(TreeVoxels.DOWN, v.parent(0), what);
		assertEquals(-1, v.parentIndex(0), what);
		Set<Long> seen = new HashSet<>();
		for (int i = 0; i < v.woodCount(); i++) {
			assertTrue(seen.add(key(v.woodX(i), v.woodY(i), v.woodZ(i))), "two wood blocks at one place in " + what);
			int r = v.radius(i);
			assertTrue(r >= 1 && r <= TreeVoxels.FULL, "radius " + r + " in " + what);
			if (i == 0) {
				continue;
			}
			int p = v.parentIndex(i);
			assertTrue(p >= 0 && p < i, "parent index " + p + " of block " + i + " in " + what);
			int[] s = TreeVoxels.STEP[v.parent(i)];
			assertEquals(v.woodX(p), v.woodX(i) + s[0], what);
			assertEquals(v.woodY(p), v.woodY(i) + s[1], what);
			assertEquals(v.woodZ(p), v.woodZ(i) + s[2], what);
			assertTrue(r <= v.radius(p), "radius " + r + " above its parent's " + v.radius(p) + " in " + what);
		}
	}

	@Test
	void leavesLieWithinSixStepsOfTheWood() {
		for (TreeRequest r : lone(6)) {
			leaves(TreeShapePlan.plan(r), r.toString());
		}
	}

	/** An independent breadth-first search from the wood through the leaves gives every leaf its stored distance ≤ 6. */
	static void leaves(TreeVoxels v, String what) {
		Map<Long, Integer> leaf = new HashMap<>();
		Set<Long> wood = new HashSet<>();
		for (int i = 0; i < v.woodCount(); i++) {
			wood.add(key(v.woodX(i), v.woodY(i), v.woodZ(i)));
		}
		for (int i = 0; i < v.leafCount(); i++) {
			long k = key(v.leafX(i), v.leafY(i), v.leafZ(i));
			assertTrue(!wood.contains(k), "leaf in wood in " + what);
			assertTrue(leaf.put(k, i) == null, "two leaves at one place in " + what);
		}
		Map<Long, Integer> dist = new HashMap<>();
		ArrayDeque<Long> queue = new ArrayDeque<>();
		for (long k : wood) {
			dist.put(k, 0);
			queue.add(k);
		}
		while (!queue.isEmpty()) {
			long k = queue.poll();
			int d = dist.get(k) + 1;
			for (int[] s : TreeVoxels.STEP) {
				long n = key(x(k) + s[0], y(k) + s[1], z(k) + s[2]);
				if (leaf.containsKey(n) && !dist.containsKey(n)) {
					dist.put(n, d);
					queue.add(n);
				}
			}
		}
		for (Map.Entry<Long, Integer> e : leaf.entrySet()) {
			Integer d = dist.get(e.getKey());
			assertTrue(d != null && d <= TreeVoxels.MAX_DISTANCE, "leaf at distance " + d + " in " + what);
			assertEquals((int) d, v.leafDistance(e.getValue()), what);
		}
		// All the wood written: the planned distances.
		boolean[] written = new boolean[v.woodCount()];
		java.util.Arrays.fill(written, true);
		byte[] all = v.leafDistances(written);
		for (int i = 0; i < v.leafCount(); i++) {
			assertEquals(v.leafDistance(i), all[i], what);
		}
	}

	@Test
	void partialWritesGiveTheDistancesOfWhatExists() {
		Random random = new Random(11);
		int checked = 0;
		for (TreeRequest r : lone(3)) {
			TreeVoxels v = TreeShapePlan.plan(r);
			if (v.woodCount() < 4 || v.leafCount() < 4) {
				continue;
			}
			// One branch subtree missing (a block in its way stops it and everything beyond), and every fifth leaf not
			// placeable (terrain or another feature in its block).
			boolean[] written = new boolean[v.woodCount()];
			java.util.Arrays.fill(written, true);
			int cut = 1 + random.nextInt(v.woodCount() - 1);
			written[cut] = false;
			for (int i = cut + 1; i < v.woodCount(); i++) {
				written[i] = written[v.parentIndex(i)];
			}
			boolean[] placeable = new boolean[v.leafCount()];
			for (int i = 0; i < placeable.length; i++) {
				placeable[i] = random.nextInt(5) != 0;
			}
			byte[] got = v.leafDistances(written, placeable);
			// An independent breadth-first search from the written wood through the placeable leaves.
			Map<Long, Integer> leaf = new HashMap<>();
			for (int i = 0; i < v.leafCount(); i++) {
				if (placeable[i]) {
					leaf.put(key(v.leafX(i), v.leafY(i), v.leafZ(i)), i);
				}
			}
			Map<Long, Integer> dist = new HashMap<>();
			ArrayDeque<Long> queue = new ArrayDeque<>();
			for (int i = 0; i < v.woodCount(); i++) {
				if (written[i]) {
					long k = key(v.woodX(i), v.woodY(i), v.woodZ(i));
					dist.put(k, 0);
					queue.add(k);
				}
			}
			while (!queue.isEmpty()) {
				long k = queue.poll();
				int d = dist.get(k) + 1;
				if (d > TreeVoxels.MAX_DISTANCE) {
					continue;
				}
				for (int[] st : TreeVoxels.STEP) {
					long n = key(x(k) + st[0], y(k) + st[1], z(k) + st[2]);
					if (leaf.containsKey(n) && !dist.containsKey(n)) {
						dist.put(n, d);
						queue.add(n);
					}
				}
			}
			for (int i = 0; i < v.leafCount(); i++) {
				Integer d = placeable[i] ? dist.get(key(v.leafX(i), v.leafY(i), v.leafZ(i))) : null;
				assertEquals(d == null ? 0 : (int) d, got[i], "leaf " + i + " of " + r);
			}
			checked++;
		}
		assertTrue(checked > 50, "trees checked: " + checked);
	}

	@Test
	void writesStayInTheThreeByThreeChunksAroundTheTrunk() {
		for (TreeRequest r : lone(8)) {
			window(TreeShapePlan.plan(r), r.toString());
		}
	}

	static void window(TreeVoxels v, String what) {
		int cx = v.x() >> 4;
		int cz = v.z() >> 4;
		for (int i = 0; i < v.woodCount() + v.leafCount(); i++) {
			boolean w = i < v.woodCount();
			int j = w ? i : i - v.woodCount();
			int dx = w ? v.woodX(j) : v.leafX(j);
			int dy = w ? v.woodY(j) : v.leafY(j);
			int dz = w ? v.woodZ(j) : v.leafZ(j);
			assertTrue(dx * dx + dz * dz <= TreeShapePlan.REACH * TreeShapePlan.REACH, "block " + dx + ", " + dz + " in " + what);
			assertTrue(dy >= 0 && dy < TreeShapePlan.MAX_HEIGHT, "height " + dy + " in " + what);
			assertTrue(Math.abs(((v.x() + dx) >> 4) - cx) <= 1 && Math.abs(((v.z() + dz) >> 4) - cz) <= 1,
					"block outside the 3 x 3 chunks in " + what);
		}
	}

	/** The trees of a stand area: every kept candidate with its territory, species by layer. */
	record Stand(List<TreeVoxels> trees, List<CrownTerritory> territories, CrownTerritories.Session session) {
	}

	static Stand stand(StandMap map, boolean grad, int cx0, int cz0) {
		CrownTerritories territories = new CrownTerritories(SEED, map);
		CrownTerritories.Session s = territories.session();
		List<TreeVoxels> trees = new ArrayList<>();
		List<CrownTerritory> list = new ArrayList<>();
		for (int cx = cx0; cx < cx0 + 4; cx++) {
			for (int cz = cz0; cz < cz0 + 4; cz++) {
				for (CrownTerritory t : s.chunk(cx, cz, null)) {
					Species sp = TreePreview.standSpecies(grad, t.layer(), t.x(), t.z());
					TreeVoxels v = TreeShapePlan.plan(new TreeRequest(sp, t.layer(), map.ageClass(t.x(), t.z()), SEED, t.x(), 64, t.z(),
							t, s::candidate, map));
					if (v.woodCount() == 0) {
						// A suppressed canopy tree (V10): left out, as in the game.
						continue;
					}
					trees.add(v);
					list.add(t);
				}
			}
		}
		return new Stand(trees, list, s);
	}

	@Test
	void neighboringCrownsNeverShareABlock() {
		int checked = 0;
		for (StandMap map : List.of(StandMap.uniform(AgeClass.MATURE, false), StandMap.uniform(AgeClass.OLD_GROWTH, true),
				StandMap.uniform(AgeClass.POLE_STAGE, true), new PatchyStandMap(SEED, 0.5))) {
			for (boolean grad : new boolean[] {false, true}) {
				Stand st = stand(map, grad, grad ? 30 : -12, grad ? -40 : 75);
				// Within a layer: no column of two trees; every block in the own territory.
				Map<CrownLayer, Map<Long, Integer>> columns = new HashMap<>();
				// Across layers (level ground): no two leaves and no two woods in one block.
				Map<Long, Integer> woods = new HashMap<>();
				Map<Long, Integer> leaves = new HashMap<>();
				for (int t = 0; t < st.trees().size(); t++) {
					TreeVoxels v = st.trees().get(t);
					CrownTerritory ter = st.territories().get(t);
					Map<Long, Integer> layerColumns = columns.computeIfAbsent(ter.layer(), l -> new HashMap<>());
					Set<Long> own = new HashSet<>();
					for (int i = 0; i < v.woodCount() + v.leafCount(); i++) {
						boolean w = i < v.woodCount();
						int j = w ? i : i - v.woodCount();
						int x = v.x() + (w ? v.woodX(j) : v.leafX(j));
						int y = w ? v.woodY(j) : v.leafY(j);
						int z = v.z() + (w ? v.woodZ(j) : v.leafZ(j));
						assertTrue(ter.contains(x, z), "block outside the territory of " + v);
						long c = key(x, 0, z);
						if (own.add(c)) {
							Integer other = layerColumns.put(c, t);
							if (other != null) {
								fail("column " + x + ", " + z + " of " + v + " and " + st.trees().get(other) + " in " + ter.layer());
							}
						}
						// Wood replaces leaves in either order (only air and #replaceable_by_trees are written), so a block of
						// wood and a leaf of two trees is no dispute; two leaves or two woods would be.
						Integer other = (w ? woods : leaves).put(key(x, y, z), t);
						if (other != null) {
							fail((w ? "wood " : "leaf ") + x + ", " + y + ", " + z + " of " + v + " (" + ter.layer() + ") and "
									+ st.trees().get(other) + " (" + st.territories().get(other).layer() + ")");
						}
						if (w && ter.layer() != CrownLayer.CANOPY && (x != v.x() || z != v.z())) {
							// The wood of a lower layer keeps a column away from every other trunk.
							for (int ex = -1; ex <= 1; ex++) {
								for (int ez = -1; ez <= 1; ez++) {
									int tx = x + ex;
									int tz = z + ez;
									assertTrue(tx == v.x() && tz == v.z() || !st.session().candidate(tx, tz),
											"wood of " + v + " next to the trunk at " + tx + ", " + tz);
								}
							}
						}
					}
					checked++;
				}
			}
		}
		assertTrue(checked > 1000, "trees checked: " + checked);
	}

	/**
	 * Canopy trees of every species and age class at an edge whose territory reaches open land (review of V9, round 1;
	 * here every column counts as open) are planned in bounded time, keep their structure (parents, radii, leaf
	 * distances, the window) and carry leaves low on the open side.
	 */
	@Test
	void edgeTreesNextToOpenLandHaveLowBranches() {
		TreeShapePlan.Open open = (x, z) -> true;
		int edges = 0;
		int low = 0;
		long t0 = System.nanoTime();
		StandMap map = new PatchyStandMap(SEED, 0.5);
		CrownTerritories.Session s = new CrownTerritories(SEED, map).session();
		{
			for (int cx = 0; cx < 8; cx++) {
				for (int cz = 0; cz < 8; cz++) {
					for (CrownTerritory t : s.chunk(cx, cz, null)) {
						if (t.layer() != CrownLayer.CANOPY || !t.edge()) {
							continue;
						}
						AgeClass age = map.ageClass(t.x(), t.z());
						for (Species sp : Species.values()) {
							if (!SpeciesShape.supported(sp)) {
								continue;
							}
							TreeRequest r = new TreeRequest(sp, CrownLayer.CANOPY, age, SEED, t.x(), 64, t.z(), t, s::candidate, map, null,
									open);
							TreeVoxels v = TreeShapePlan.plan(r);
							if (v.woodCount() == 0) {
								// A suppressed canopy tree (V10): not planted.
								continue;
							}
							String what = r.toString();
							chain(v, what);
							leaves(v, what);
							window(v, what);
							edges++;
							for (int i = 0; i < v.leafCount(); i++) {
								low += v.leafY(i) < 0.4 * v.height() ? 1 : 0;
							}
						}
					}
				}
			}
		}
		double seconds = (System.nanoTime() - t0) / 1e9;
		System.out.printf(Locale.ROOT, "[tree plan] %d edge trees next to open land, %d leaves below 0.4 of the height, %.1f s%n", edges,
				low, seconds);
		assertTrue(edges > 100, "edge trees: " + edges);
		assertTrue(low > edges, "low leaves of the edge trees: " + low);
		assertTrue(seconds < 60, "planning time " + seconds);
	}

	@Test
	void layersKeepToTheirSplitSurfaces() {
		for (TreeRequest r : lone(4)) {
			TreeVoxels v = TreeShapePlan.plan(r);
			assertTrue(v.leafCount() > 0, "no leaves in " + r);
			for (int i = 0; i < v.woodCount() + v.leafCount(); i++) {
				boolean w = i < v.woodCount();
				int j = w ? i : i - v.woodCount();
				int x = r.x() + (w ? v.woodX(j) : v.leafX(j));
				int y = w ? v.woodY(j) : v.leafY(j);
				int z = r.z() + (w ? v.woodZ(j) : v.leafZ(j));
				int upper = TreeShapePlan.split(SEED, CrownLayer.SUBCANOPY, r.ageAt(x, z), x, z);
				int lower = TreeShapePlan.split(SEED, CrownLayer.REGENERATION, r.ageAt(x, z), x, z);
				String at = (w ? "wood" : "leaf") + " at " + y + " (split " + lower + "/" + upper + ") in " + r;
				// The trunk and the dry stubs (within two columns of the trunk base: lean and stubs) pass the surface below their layer.
				boolean trunk = w && Math.max(Math.abs(x - r.x()), Math.abs(z - r.z())) <= 2;
				switch (r.layer()) {
					case CANOPY -> assertTrue(r.openGrown() || y >= upper || trunk, at);
					case SUBCANOPY -> assertTrue(y < upper && (y >= lower || trunk), at);
					case REGENERATION -> assertTrue(y < lower, at);
				}
			}
		}
	}

	@Test
	void splitSurfacesAreWavyAndOrdered() {
		for (AgeClass age : AgeClass.values()) {
			Set<Integer> uppers = new HashSet<>();
			for (int x = -200; x < 200; x += 7) {
				for (int z = -200; z < 200; z += 11) {
					int upper = TreeShapePlan.split(SEED, CrownLayer.SUBCANOPY, age, x, z);
					int lower = TreeShapePlan.split(SEED, CrownLayer.REGENERATION, age, x, z);
					assertTrue(lower >= 2 && upper >= lower + 3, age + " " + lower + "/" + upper);
					// Neighboring columns differ by at most a block: a gentle surface, no step.
					assertTrue(Math.abs(TreeShapePlan.split(SEED, CrownLayer.SUBCANOPY, age, x + 1, z) - upper) <= 1, age.toString());
					uppers.add(upper);
				}
			}
			// No flat surface: at least four heights over the area.
			assertTrue(uppers.size() >= 4, age + " upper split heights " + uppers);
		}
	}

	/**
	 * The side view of a stand (the middle row of 3 × 3 chunks, 48 columns, as {@code drzewostan_*.png}) has leaves in
	 * every block row from the lowest leaf (from the ground in the natural stand) to near the top: no stand-wide empty
	 * row between the layers (rule Z10, review of V6).
	 */
	@Test
	void standsHaveNoEmptyLeafRow() {
		for (boolean grad : new boolean[] {false, true}) {
			int cx0 = grad ? 27 : -44;
			int cz0 = grad ? -46 : 120;
			int[] rows = new int[TreeShapePlan.MAX_HEIGHT];
			boolean[][] seen = new boolean[TreeShapePlan.MAX_HEIGHT][48];
			for (TreePreview.Placed p : TreePreview.standTrees(grad, cx0, cz0)) {
				TreeVoxels v = p.voxels();
				for (int i = 0; i < v.leafCount(); i++) {
					int x = p.x() + v.leafX(i);
					int z = p.z() + v.leafZ(i);
					if (x >= 0 && x < 48 && z >= 16 && z < 32 && !seen[v.leafY(i)][x]) {
						seen[v.leafY(i)][x] = true;
						rows[v.leafY(i)]++;
					}
				}
			}
			int low = 0;
			while (rows[low] == 0) {
				low++;
			}
			int high = rows.length - 1;
			while (rows[high] == 0) {
				high--;
			}
			StringBuilder report = new StringBuilder("[tree shape] " + (grad ? "grad" : "bor") + " leaf columns of 48 by row:");
			for (int y = 0; y <= high; y++) {
				report.append(' ').append(rows[y]);
			}
			System.out.println(report);
			// The managed pine stand has little regeneration (a sparse layer by design): its rows from 5 on.
			for (int y = grad ? 1 : Math.max(low, 5); y <= high - 3; y++) {
				assertTrue(rows[y] >= 3, (grad ? "grad" : "bor") + " row " + y + " has " + rows[y] + " leaf columns");
			}
		}
	}

	/**
	 * Risk R4 (review of V6): thin branches make no hooks (a path from a tip towards the trunk that goes both ways along
	 * one horizontal axis), and right-angled elbows of thin wood within a branch, with both arms of two blocks or more, stay
	 * rare (at most one per tree, four for the crooked oak); the
	 * old oak's limbs are thick off the trunk column.
	 */
	@Test
	void branchesMakeNoHooksAndFewElbows() {
		StringBuilder report = new StringBuilder("[tree shape] per canopy tree in the stand form (40 trees): elbows, hooks, "
				+ "off-axis blocks of r >= 3\n");
		for (Species s : SpeciesShape.species()) {
			for (AgeClass age : List.of(AgeClass.MATURE, AgeClass.OLD_GROWTH)) {
				int elbows = 0;
				int hooks = 0;
				int thick = 0;
				int n = 40;
				for (int k = 0; k < n; k++) {
					TreeVoxels v = TreeShapePlan.plan(new TreeRequest(s, CrownLayer.CANOPY, age, SEED, 37 * k + 5, 64, 53 * k - 7, null,
							null, StandMap.uniform(age, true)));
					int[] children = new int[v.woodCount()];
					for (int i = 1; i < v.woodCount(); i++) {
						children[v.parentIndex(i)]++;
					}
					for (int i = 1; i < v.woodCount(); i++) {
						if ((v.woodX(i) != 0 || v.woodZ(i) != 0) && v.radius(i) >= 3) {
							thick++;
						}
						if (v.radius(i) <= 2 && elbow(v, i)) {
							elbows++;
						}
						if (children[i] == 0 && hook(v, i)) {
							hooks++;
						}
					}
				}
				report.append(String.format(Locale.ROOT, "  %-13s %-10s %5.1f %4.2f %5.1f%n", s.id(), age.name().toLowerCase(Locale.ROOT),
						elbows / (double) n, hooks / (double) n, thick / (double) n));
				assertTrue(hooks <= n / 2, s + " " + age + " hooks per tree " + hooks / (double) n);
				// The oak's limbs climb in runs with jogs (kinks fit the oak, review of V6), the others have smooth branches.
				assertTrue(elbows <= (s == Species.OAK ? 4 : 1) * n, s + " " + age + " elbows per tree " + elbows / (double) n);
				if (s == Species.OAK && age == AgeClass.OLD_GROWTH) {
					assertTrue(thick >= 4 * n, "old oak limbs: off-axis blocks of r >= 3 per tree " + thick / (double) n);
				}
			}
		}
		System.out.print(report);
	}

	/**
	 * Whether block i turns between vertical and horizontal with both arms at least two blocks long, within a branch: a
	 * branch that leaves a vertical run of four blocks or more (a trunk, a leader, a limb) is a branch base, not an
	 * elbow.
	 */
	private static boolean elbow(TreeVoxels v, int i) {
		int p = v.parentIndex(i);
		if (p <= 0) {
			return false;
		}
		int d = v.parent(i);
		int dp = v.parent(p);
		if ((d <= TreeVoxels.UP) == (dp <= TreeVoxels.UP)) {
			return false;
		}
		int run = 1;
		for (int q = v.parentIndex(p); q > 0 && v.parent(q) == dp && run < 4; q = v.parentIndex(q)) {
			run++;
		}
		boolean up = run >= 2 && (run < 4 || dp > TreeVoxels.UP);
		boolean down = false;
		for (int c = i + 1; c < v.woodCount() && !down; c++) {
			down = v.parentIndex(c) == i && v.parent(c) == d;
		}
		return up && down;
	}

	/**
	 * Whether the thin path from tip i towards the trunk steps both ways along one horizontal axis, up to where it meets
	 * a vertical run of three blocks (the trunk or the leader, whose lean is no part of the branch).
	 */
	private static boolean hook(TreeVoxels v, int i) {
		boolean[] seen = new boolean[6];
		for (int q = i, steps = 0; q > 0 && steps < 8 && v.radius(q) <= 2; q = v.parentIndex(q), steps++) {
			int d = v.parent(q);
			if (verticalRun(v, q) >= 3) {
				break;
			}
			if (d >= TreeVoxels.NORTH) {
				if (seen[d ^ 1]) {
					return true;
				}
				seen[d] = true;
			}
		}
		return false;
	}

	@Test
	void pineBarkChangesThroughTheTransitionZoneAtHalfTheHeight() {
		Random random = new Random(3);
		for (AgeClass age : AgeClass.values()) {
			for (CrownLayer layer : List.of(CrownLayer.CANOPY, CrownLayer.SUBCANOPY)) {
				for (int k = 0; k < 40; k++) {
					TreeRequest r = new TreeRequest(Species.SCOTS_PINE, layer, age, SEED, random.nextInt(5000), 70,
							random.nextInt(5000));
					TreeVoxels v = TreeShapePlan.plan(r);
					int transition = -1;
					int upper = -1;
					for (int i = 0; i < v.woodCount(); i++) {
						if (v.bark(i) == TreeVoxels.TRANSITION) {
							assertEquals(-1, transition, "one transition block in " + r);
							transition = i;
						} else if (v.bark(i) == TreeVoxels.TRANSITION_UPPER) {
							assertEquals(-1, upper, "one upper transition block in " + r);
							upper = i;
						}
					}
					assertTrue(transition > 0 && upper > 0, "transition zone in " + r);
					// Lower bark, the two blocks of the zone, the mirror: one on another along the trunk.
					assertEquals(transition, v.parentIndex(upper), r.toString());
					assertEquals(TreeVoxels.LOWER, v.bark(v.parentIndex(transition)), r.toString());
					double share = (v.woodY(transition) + 1) / v.height();
					double mirror = new double[] {0.45, 0.5, 0.55}[age.ordinal()];
					// The zone starts on a whole block: one block of rounding is 1 / H of the height (small trees).
					assertTrue(Math.abs(share - mirror) < 0.05 + 1.0 / v.height(), String.format(Locale.ROOT, "mirror at %.2f of %s",
							share, r));
					for (int i = 0; i < v.woodCount(); i++) {
						if (v.woodY(i) < v.woodY(transition)) {
							assertEquals(TreeVoxels.LOWER, v.bark(i), "lower bark below the zone in " + r);
						} else if (v.woodY(i) > v.woodY(upper) && v.bark(i) != TreeVoxels.UPPER) {
							// Only a dry stub keeps the grey lower bark above the zone (review of V6).
							assertTrue(stub(v, i) && v.bark(i) == TreeVoxels.LOWER, "mirror above the zone in " + r);
						}
						if (stub(v, i) && v.woodY(i) < lowestLeaf(v) - 1) {
							assertEquals(TreeVoxels.LOWER, v.bark(i), "dry stub in " + r);
						}
					}
				}
			}
		}
	}

	/** Whether wood block i has the shape of a dry stub: one block of 1 px, horizontal off a trunk block, no children. */
	private static boolean stub(TreeVoxels v, int i) {
		if (i == 0 || v.radius(i) != 1 || v.parent(i) < TreeVoxels.NORTH) {
			return false;
		}
		for (int k = i + 1; k < v.woodCount(); k++) {
			if (v.parentIndex(k) == i) {
				return false;
			}
		}
		int p = v.parentIndex(i);
		// Its parent is a trunk block (the trunk climbs vertically).
		return p == 0 || v.parent(p) <= TreeVoxels.UP;
	}

	/**
	 * The conifer's top is a column of needles, not a bare leader with one leaf floating above it (review of V6, round 2):
	 * for 100 canopy spruces per age class, grown alone and in the stand form, the top four leaf rows follow one another
	 * without an empty row. The pine's open crown is reported; only its leader's top block must have needles beside it.
	 */
	@Test
	void conifersTopsAreColumnsOfNeedles() {
		StringBuilder report = new StringBuilder("[tree shape] conifer tops, trees with an empty leaf row in the top four of 100"
				+ " (alone / stand form)\n");
		int spruceGaps = 0;
		for (Species s : List.of(Species.SPRUCE, Species.SCOTS_PINE)) {
			for (AgeClass age : AgeClass.values()) {
				int[] gaps = new int[2];
				for (int form = 0; form < 2; form++) {
					for (int k = 0; k < 100; k++) {
						TreeVoxels v = TreeShapePlan.plan(new TreeRequest(s, CrownLayer.CANOPY, age, SEED, 263 * k - 13000, 64,
								-181 * k + 50, null, null, form == 1 ? StandMap.uniform(age, true) : null));
						Set<Integer> rows = new HashSet<>();
						int top = Integer.MIN_VALUE;
						for (int i = 0; i < v.leafCount(); i++) {
							rows.add(v.leafY(i));
							top = Math.max(top, v.leafY(i));
						}
						boolean gap = false;
						for (int y = top - 3; y < top; y++) {
							gap |= !rows.contains(y);
						}
						if (gap) {
							gaps[form]++;
							if (s == Species.SCOTS_PINE) {
								// The pine's crown is open (bare rows between tufts are allowed), but its leader never ends
								// bare: the row below the top leaf has needles.
								assertTrue(rows.contains(top - 1), "bare leader top of a pine, " + age + ", k " + k);
							}
						}
					}
				}
				report.append(String.format(Locale.ROOT, "  %-13s %-10s %3d / %3d%n", s.id(), age.name().toLowerCase(Locale.ROOT),
						gaps[0], gaps[1]));
				if (s == Species.SPRUCE) {
					spruceGaps += gaps[0] + gaps[1];
				}
			}
		}
		System.out.print(report);
		assertEquals(0, spruceGaps, "spruces with an empty leaf row in their top");
	}

	private static int lowestLeaf(TreeVoxels v) {
		int lowest = Integer.MAX_VALUE;
		for (int k = 0; k < v.leafCount(); k++) {
			lowest = Math.min(lowest, v.leafY(k));
		}
		return lowest;
	}

	/** Per-tree measures of a silhouette. */
	private record Silhouette(double height, double lowestLeaf, double width, double lowWidth, double highWidth, int stubs) {
	}

	private static Silhouette silhouette(TreeVoxels v) {
		int top = 0;
		int low = Integer.MAX_VALUE;
		for (int i = 0; i < v.leafCount(); i++) {
			top = Math.max(top, v.leafY(i));
			low = Math.min(low, v.leafY(i));
		}
		double[] width = new double[3];
		for (int i = 0; i < v.leafCount(); i++) {
			double rho = Math.hypot(v.leafX(i), v.leafZ(i));
			int third = Math.min(2, (int) (3.0 * (v.leafY(i) - low) / Math.max(1, top - low + 1)));
			width[third] = Math.max(width[third], rho);
		}
		int stubs = 0;
		for (int i = 1; i < v.woodCount(); i++) {
			// A dry stub: a one-block branch of 1 px with a horizontal parent, below the crown, without children.
			if (v.radius(i) == 1 && v.parent(i) >= TreeVoxels.NORTH && v.woodY(i) < low - 1) {
				stubs++;
			}
		}
		return new Silhouette(v.height(), low, Math.max(width[0], Math.max(width[1], width[2])), width[0], width[2], stubs);
	}

	private static Silhouette mean(Species s, CrownLayer layer, AgeClass age) {
		double h = 0;
		double low = 0;
		double w = 0;
		double lw = 0;
		double hw = 0;
		int stubs = 0;
		int n = 40;
		for (int k = 0; k < n; k++) {
			Silhouette e = silhouette(TreeShapePlan.plan(new TreeRequest(s, layer, age, SEED, 300 * k - 6000, 64, 17 * k, null, null,
					StandMap.uniform(age, true))));
			h += e.height();
			low += e.lowestLeaf();
			w += e.width();
			lw += e.lowWidth();
			hw += e.highWidth();
			stubs += e.stubs() > 0 ? 1 : 0;
		}
		return new Silhouette(h / n, low / n, w / n, lw / n, hw / n, stubs);
	}

	@Test
	void archetypeSilhouettesDiffer() {
		Map<Species, Silhouette> mature = new HashMap<>();
		StringBuilder report = new StringBuilder("[tree shape] species, age (stand form): height, lowest leaf share, crown "
				+ "width, low/high third width, trees with dry stubs of 40\n");
		for (Species s : SpeciesShape.species()) {
			for (AgeClass age : AgeClass.values()) {
				Silhouette m = mean(s, CrownLayer.CANOPY, age);
				if (age == AgeClass.MATURE) {
					mature.put(s, m);
				}
				report.append(String.format(Locale.ROOT, "  %-13s %-10s H %5.1f  base %.2f  R %4.1f  low %4.1f  high %4.1f  stubs %d%n",
						s.id(), age.name().toLowerCase(Locale.ROOT), m.height(), m.lowestLeaf() / m.height(), m.width(),
						m.lowWidth(), m.highWidth(), m.stubs()));
			}
		}
		System.out.print(report);
		Silhouette pine = mature.get(Species.SCOTS_PINE);
		Silhouette spruce = mature.get(Species.SPRUCE);
		// Pine: crown in the upper part (25-35% of the height, the band of the canopy may push it higher), dry stubs below.
		assertTrue(pine.lowestLeaf() / pine.height() > 0.6, "pine crown base " + pine.lowestLeaf() / pine.height());
		assertTrue(pine.stubs() >= 20, "pines with stubs: " + pine.stubs());
		// Spruce: a cone, widest in its lower third, a longer crown than the pine.
		assertTrue(spruce.lowWidth() > spruce.highWidth() + 1, "spruce widths " + spruce.lowWidth() + " / " + spruce.highWidth());
		assertTrue(spruce.lowestLeaf() / spruce.height() < pine.lowestLeaf() / pine.height(), "spruce crown longer than pine");
		// Crown length (share of the height above the lowest leaf), which the split surface must not cut below the
		// species' minimum (review of V6: hornbeam dense and rounded, birch, linden and maple no plates on poles).
		Map<Species, Double> least = Map.of(Species.HORNBEAM, 0.4, Species.BIRCH, 0.3, Species.LINDEN, 0.4, Species.NORWAY_MAPLE,
				0.4, Species.BEECH, 0.4, Species.SPRUCE, 0.4);
		for (Map.Entry<Species, Double> e : least.entrySet()) {
			for (AgeClass age : List.of(AgeClass.MATURE, AgeClass.OLD_GROWTH)) {
				Silhouette m = mean(e.getKey(), CrownLayer.CANOPY, age);
				double crown = 1 - m.lowestLeaf() / m.height();
				assertTrue(crown >= e.getValue(), String.format(Locale.ROOT, "%s %s crown length %.2f", e.getKey().id(), age, crown));
			}
		}
		// Broad crowns: oak and linden wider than birch and the conifers.
		for (Species broad : List.of(Species.OAK, Species.LINDEN)) {
			for (Species narrow : List.of(Species.BIRCH, Species.SCOTS_PINE, Species.SPRUCE)) {
				assertTrue(mature.get(broad).width() > mature.get(narrow).width() + 1,
						broad.id() + " wider than " + narrow.id());
			}
		}
	}

	@Test
	void reportBlocksPerTree() {
		StringBuilder report = new StringBuilder("[tree shape] mean wood + leaves per tree (40 columns)\n");
		List<Species> species = new ArrayList<>(SpeciesShape.species());
		Collections.sort(species);
		for (Species s : species) {
			report.append(String.format(Locale.ROOT, "  %-13s", s.id()));
			for (CrownLayer layer : CrownLayer.values()) {
				for (AgeClass age : AgeClass.values()) {
					int wood = 0;
					int leaves = 0;
					for (int k = 0; k < 40; k++) {
						TreeVoxels v = TreeShapePlan.plan(new TreeRequest(s, layer, age, SEED, 211 * k, 64, -97 * k));
						wood += v.woodCount();
						leaves += v.leafCount();
					}
					report.append(String.format(Locale.ROOT, " %4d+%-4d", wood / 40, leaves / 40));
				}
			}
			report.append('\n');
		}
		System.out.print(report);
	}

	/** Length of the run of vertical steps from block q towards the root. */
	private static int verticalRun(TreeVoxels v, int q) {
		int run = 0;
		while (q > 0 && v.parent(q) <= TreeVoxels.UP && run < 3) {
			run++;
			q = v.parentIndex(q);
		}
		return run;
	}

	static long key(int x, int y, int z) {
		return ((long) (x + (1 << 20)) << 42) | ((long) (y + (1 << 20)) << 21) | (z + (1 << 20));
	}

	private static int x(long k) {
		return (int) (k >>> 42) - (1 << 20);
	}

	private static int y(long k) {
		return (int) (k >>> 21 & 0x1FFFFF) - (1 << 20);
	}

	private static int z(long k) {
		return (int) (k & 0x1FFFFF) - (1 << 20);
	}
}
