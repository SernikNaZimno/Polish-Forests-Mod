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
 * trunk, no disputes between the crowns of neighboring trees, the pine's bark zones, the layer bands and the archetype
 * silhouettes.
 */
class TreeShapePlanTest {
	private static final long SEED = 20260927L;

	/** Lone trees of every species, layer and age class at a few columns (also at chunk corners). */
	private static List<TreeRequest> lone(int perCell) {
		List<TreeRequest> out = new ArrayList<>();
		Random random = new Random(7);
		for (Species s : SpeciesShape.species()) {
			for (CrownLayer layer : CrownLayer.values()) {
				for (AgeClass age : AgeClass.values()) {
					for (int k = 0; k < perCell; k++) {
						int x = k == 0 ? 16 * 5 : k == 1 ? 16 * -3 + 15 : random.nextInt(4000) - 2000;
						int z = k == 0 ? 16 * -7 + 15 : k == 1 ? 16 * 9 : random.nextInt(4000) - 2000;
						out.add(new TreeRequest(s, layer, age, SEED, x, 64, z));
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
		TreeVoxels moved = TreeShapePlan.plan(new TreeRequest(r.species(), r.layer(), r.age(), SEED, r.x(), r.y() + 20, r.z()));
		assertEquals(first.get(3).woodCount(), moved.woodCount());
		assertEquals(first.get(3).leafCount(), moved.leafCount());
		assertNotEquals(first.get(3), TreeShapePlan.plan(new TreeRequest(r.species(), r.layer(), r.age(), SEED + 1, r.x(), r.y(),
				r.z())));
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
		// With some wood missing, the distances come from the wood that exists.
		boolean[] written = new boolean[v.woodCount()];
		java.util.Arrays.fill(written, true);
		byte[] all = v.leafDistances(written);
		for (int i = 0; i < v.leafCount(); i++) {
			assertEquals(v.leafDistance(i), all[i], what);
		}
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
					trees.add(TreeShapePlan.plan(new TreeRequest(sp, t.layer(), map.ageClass(t.x(), t.z()), SEED, t.x(), 64, t.z(),
							t, s::candidate)));
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

	@Test
	void leavesKeepToTheBandOfTheirLayer() {
		for (TreeRequest r : lone(4)) {
			TreeVoxels v = TreeShapePlan.plan(r);
			int[] band = TreeShapePlan.band(r.layer(), r.age());
			for (int i = 0; i < v.leafCount(); i++) {
				assertTrue(v.leafY(i) >= band[0] && v.leafY(i) <= band[1], "leaf at " + v.leafY(i) + " in " + r);
			}
			assertTrue(v.leafCount() > 0, "no leaves in " + r);
		}
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
						} else if (v.woodY(i) > v.woodY(upper)) {
							assertEquals(TreeVoxels.UPPER, v.bark(i), "mirror above the zone in " + r);
						}
					}
				}
			}
		}
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
			Silhouette e = silhouette(TreeShapePlan.plan(new TreeRequest(s, layer, age, SEED, 300 * k - 6000, 64, 17 * k)));
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
		StringBuilder report = new StringBuilder("[tree shape] species, age: height, lowest leaf share, crown width, "
				+ "low/high third width, trees with dry stubs of 40\n");
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
