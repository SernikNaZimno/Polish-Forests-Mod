package pl.polishforests.worldgen.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.habitat.Species;

/**
 * The root planner of step I1 ({@link RootPlan}): determinism, the parent chains and radii, the cover of soil over every
 * root and the level ground under every flare, the write window, and in stands of the tree planner (with the trunk
 * candidates of the M2 pattern on wavy ground) no shared cell between two trees and no flare touching another tree's
 * flare or trunk column; the habits of the species (flares of old broad-leaved trees, few on pine and birch, none on
 * young trees), the walls of the ground (exposed cells) and unknown columns.
 */
class RootPlanTest {
	private static final long SEED = 20260927L;
	private static final List<Species> SPECIES = List.of(Species.OAK, Species.BEECH, Species.LINDEN, Species.HORNBEAM,
			Species.NORWAY_MAPLE, Species.ASH, Species.SPRUCE, Species.SCOTS_PINE, Species.BIRCH);

	/** Wavy ground: about ±2 blocks with waves of 9-15 blocks, so the roots follow a slope. */
	static int wavy(int x, int z) {
		return 63 + (int) Math.round(1.6 * Math.sin(x / 9.0) + 1.2 * Math.cos(z / 15.0 + 0.3 * x / 11.0));
	}

	private static RootPlan.Request request(Species s, AgeClass age, int x, int z, TreeShapePlan.Ground g,
			TreeShapePlan.Trunks trunks, int base) {
		return new RootPlan.Request(s, CrownLayer.CANOPY, age, SEED, x, g.top(x, z) + 1, z, base, g, trunks);
	}

	@Test
	void sameRequestSamePlanInAnyOrderAndThread() throws Exception {
		TreeShapePlan.Ground g = RootPlanTest::wavy;
		List<RootPlan.Request> requests = new ArrayList<>();
		for (int i = 0; i < 40; i++) {
			Species s = SPECIES.get(i % SPECIES.size());
			requests.add(request(s, i % 3 == 0 ? AgeClass.MATURE : AgeClass.OLD_GROWTH, 37 * i - 400, 23 * i + 11, g, null, 3 + i % 5));
		}
		List<RootVoxels> first = new ArrayList<>();
		for (RootPlan.Request r : requests) {
			first.add(RootPlan.plan(r));
		}
		// Backwards, and on other threads.
		for (int i = requests.size() - 1; i >= 0; i--) {
			assertEquals(first.get(i), RootPlan.plan(requests.get(i)), "request " + i);
		}
		ExecutorService pool = Executors.newFixedThreadPool(4);
		try {
			List<Future<RootVoxels>> again = new ArrayList<>();
			for (RootPlan.Request r : requests) {
				again.add(pool.submit(() -> RootPlan.plan(r)));
			}
			for (int i = 0; i < requests.size(); i++) {
				assertEquals(first.get(i), again.get(i).get(), "request " + i + " on another thread");
			}
		} finally {
			pool.shutdown();
		}
	}

	/**
	 * Step O2: the share of rooted dirt (0.3 → 0.15) is the only change of the plan: flares and roots are the same, and the
	 * rooted dirt of the lower share is a subset of that of the higher one, about half of it.
	 */
	@Test
	void lowerDirtShareKeepsTheRestOfThePlan() {
		TreeShapePlan.Ground g = RootPlanTest::wavy;
		assertEquals(RootPlan.DIRT_SHARE, RootPlan.dirtShare(), "share of this run");
		assertEquals(RootPlan.LEGACY_DIRT_SHARE, RootPlan.dirtShare("0.3"));
		long legacyDirt = 0;
		long dirt = 0;
		for (Species s : SPECIES) {
			for (AgeClass age : AgeClass.values()) {
				for (int k = 0; k < 8; k++) {
					RootPlan.Request r = request(s, age, 83 * k - 250 + s.ordinal(), 47 * k + 5, g, null, 2 + k % 6);
					RootVoxels before = RootPlan.plan(r, RootPlan.LEGACY_DIRT_SHARE);
					RootVoxels now = RootPlan.plan(r);
					String what = s + " " + age + " " + k;
					assertEquals(before.flareCount(), now.flareCount(), what);
					for (int i = 0; i < now.flareCount(); i++) {
						assertEquals(List.of(before.flareX(i), before.flareY(i), before.flareZ(i), before.flareRadius(i),
								before.flareParent(i), before.flareParentIndex(i)), List.of(now.flareX(i), now.flareY(i),
								now.flareZ(i), now.flareRadius(i), now.flareParent(i), now.flareParentIndex(i)), what);
					}
					assertEquals(before.rootCount(), now.rootCount(), what);
					for (int i = 0; i < now.rootCount(); i++) {
						assertEquals(List.of(before.rootX(i), before.rootY(i), before.rootZ(i), before.rootRadius(i),
								before.rootParent(i), before.rootParentIndex(i), before.rootKind(i)), List.of(now.rootX(i),
								now.rootY(i), now.rootZ(i), now.rootRadius(i), now.rootParent(i), now.rootParentIndex(i),
								now.rootKind(i)), what);
					}
					Set<Long> old = new HashSet<>();
					for (int i = 0; i < before.dirtCount(); i++) {
						old.add(key(before.dirtX(i), before.dirtY(i), before.dirtZ(i)));
					}
					for (int i = 0; i < now.dirtCount(); i++) {
						assertTrue(old.contains(key(now.dirtX(i), now.dirtY(i), now.dirtZ(i))), what + ": new rooted dirt cell");
					}
					legacyDirt += before.dirtCount();
					dirt += now.dirtCount();
				}
			}
		}
		assertTrue(legacyDirt > 500, "rooted dirt planned before O2: " + legacyDirt);
		double ratio = (double) dirt / legacyDirt;
		assertTrue(ratio > 0.4 && ratio < 0.6, "rooted dirt after / before O2: " + ratio);
	}

	@Test
	void chainsRadiiCoverAndWindow() {
		TreeShapePlan.Ground g = RootPlanTest::wavy;
		int planned = 0;
		for (Species s : SPECIES) {
			for (AgeClass age : AgeClass.values()) {
				for (int k = 0; k < 12; k++) {
					int x = 97 * k - 300 + s.ordinal();
					int z = 61 * k + 17;
					RootVoxels v = RootPlan.plan(request(s, age, x, z, g, null, 2 + k % 6));
					check(v, g, s + " " + age + " at " + x + ", " + z);
					planned += v.rootCount();
				}
			}
		}
		assertTrue(planned > 1000, "roots planned: " + planned);
	}

	/** The invariants of one plan. */
	static void check(RootVoxels v, TreeShapePlan.Ground g, String what) {
		int top0 = v.y() - 1;
		int cx = v.x() >> 4;
		int cz = v.z() >> 4;
		for (int i = 0; i < v.flareCount(); i++) {
			int x = v.flareX(i);
			int z = v.flareZ(i);
			assertEquals(0, v.flareY(i), what);
			assertTrue(Math.abs(x) <= RootPlan.FLARE_LENGTH + 1 && Math.abs(z) <= RootPlan.FLARE_LENGTH + 1, what);
			assertEquals(top0, g.top(v.x() + x, v.z() + z), what + ": flare over uneven ground");
			int p = v.flareParentIndex(i);
			assertTrue(p < i, what);
			int[] step = TreeVoxels.STEP[v.flareParent(i)];
			assertEquals(0, step[1], what + ": a flare's parent lies beside it");
			int px = p < 0 ? 0 : v.flareX(p);
			int pz = p < 0 ? 0 : v.flareZ(p);
			assertEquals(px, x + step[0], what);
			assertEquals(pz, z + step[2], what);
			assertTrue(v.flareRadius(i) >= 1 && v.flareRadius(i) <= RootPlan.MAX_FLARE_STAGE, what);
			// Review of I1: every block farther from the trunk than its parent (no elbow, no crossbar), straight on from the
			// parent's own direction, its stage one below the parent's; the last block of a flare has stage 1.
			assertTrue(x * x + z * z > px * px + pz * pz, what + ": a flare block not farther from the trunk than its parent");
			if (p >= 0) {
				assertEquals(v.flareParent(p), v.flareParent(i), what + ": a flare turns");
				assertEquals(i - 1, p, what + ": a flare's blocks not in a run");
				assertEquals(v.flareRadius(p) - 1, v.flareRadius(i), what + ": the stage does not fall by one outwards");
			}
			boolean last = i + 1 == v.flareCount() || v.flareParentIndex(i + 1) != i;
			if (last) {
				assertEquals(1, v.flareRadius(i), what + ": a flare does not run out into the ground");
			}
		}
		for (int i = 0; i < v.rootCount(); i++) {
			int x = v.rootX(i);
			int y = v.y() + v.rootY(i);
			int z = v.rootZ(i);
			assertTrue(x * x + z * z <= RootPlan.REACH * RootPlan.REACH, what + ": root outside the disc of the reach");
			assertTrue(Math.abs(((v.x() + x) >> 4) - cx) <= 1 && Math.abs(((v.z() + z) >> 4) - cz) <= 1,
					what + ": root outside the 3 x 3 chunks");
			int top = g.top(v.x() + x, v.z() + z);
			int p = v.rootParentIndex(i);
			assertTrue(p < i, what);
			boolean trunkColumn = x == 0 && z == 0;
			assertTrue(y <= top - (trunkColumn ? 1 : RootPlan.COVER), what + ": root under " + (top - y) + " blocks of ground");
			assertTrue(top - y <= RootPlan.MAX_DEPTH, what + ": root too deep");
			int[] step = TreeVoxels.STEP[v.rootParent(i)];
			if (p < 0) {
				assertEquals(TreeVoxels.UP, v.rootParent(i), what);
				assertEquals(-2, v.rootY(i), what);
				assertTrue(x == 0 && z == 0, what);
			} else {
				assertEquals(v.rootX(p), x + step[0], what + ": parent not a face neighbor");
				assertEquals(v.rootY(p), v.rootY(i) + step[1], what);
				assertEquals(v.rootZ(p), z + step[2], what);
				assertTrue(v.rootRadius(i) <= v.rootRadius(p), what + ": the radius grows towards the tips");
			}
		}
		Set<Long> cells = new HashSet<>();
		for (int i = 0; i < v.rootCount(); i++) {
			assertTrue(cells.add(key(v.rootX(i), v.rootY(i), v.rootZ(i))), what + ": two roots in one cell");
		}
		for (int i = 0; i < v.dirtCount(); i++) {
			int top = g.top(v.x() + v.dirtX(i), v.z() + v.dirtZ(i));
			int y = v.y() + v.dirtY(i);
			assertTrue(y <= top - RootPlan.COVER, what + ": rooted dirt near the surface");
			assertTrue(cells.add(key(v.dirtX(i), v.dirtY(i), v.dirtZ(i))), what + ": rooted dirt in a root cell");
		}
	}

	private record Cell(int x, int y, int z) {
	}

	private static long key(int x, int y, int z) {
		return ((long) x & 0xFFFFF) << 40 | ((long) y & 0xFFFFF) << 20 | (long) z & 0xFFFFF;
	}

	/**
	 * Stands of the tree planner (4 × 4 chunks, both stand maps' sets of species): roots of every tree on wavy ground with
	 * the trunk candidates; no cell shared by two trees, no flare within one block (also diagonally) of another tree's
	 * flare or of any other trunk candidate's column.
	 */
	@Test
	void treesOfAStandNeverShareOrTouch() {
		for (boolean grad : new boolean[] {true, false}) {
			StandMap map = new PatchyStandMap(SEED, 0.5);
			TreeShapePlanTest.Stand stand = TreeShapePlanTest.stand(map, grad, 0, 0);
			TreeShapePlan.Ground g = RootPlanTest::wavy;
			TreeShapePlan.Trunks trunks = stand.session().trunks();
			Map<Long, Integer> owner = new HashMap<>();
			Map<Cell, Integer> flareOwner = new HashMap<>();
			int flares = 0;
			int roots = 0;
			for (int t = 0; t < stand.trees().size(); t++) {
				TreeVoxels tree = stand.trees().get(t);
				CrownTerritory territory = stand.territories().get(t);
				Species s = TreePreview.standSpecies(grad, territory.layer(), tree.x(), tree.z());
				RootVoxels v = RootPlan.plan(new RootPlan.Request(s, territory.layer(), map.ageClass(tree.x(), tree.z()), SEED, tree.x(),
						g.top(tree.x(), tree.z()) + 1, tree.z(), tree.radius(0), g, trunks, stand.session().rootKinds()));
				check(v, g, s + " in the stand at " + tree.x() + ", " + tree.z());
				for (int i = 0; i < v.rootCount(); i++) {
					Integer other = owner.put(key(v.x() + v.rootX(i), v.y() + v.rootY(i), v.z() + v.rootZ(i)), t);
					assertTrue(other == null, "a root cell of two trees at " + (v.x() + v.rootX(i)) + ", " + (v.z() + v.rootZ(i)));
					roots++;
				}
				for (int i = 0; i < v.dirtCount(); i++) {
					Integer other = owner.put(key(v.x() + v.dirtX(i), v.y() + v.dirtY(i), v.z() + v.dirtZ(i)), t);
					assertTrue(other == null, "rooted dirt of two trees");
				}
				for (int i = 0; i < v.flareCount(); i++) {
					int fx = v.x() + v.flareX(i);
					int fz = v.z() + v.flareZ(i);
					flareOwner.put(new Cell(fx, v.y(), fz), t);
					flares++;
					// No other candidate column within one block.
					for (int ox = -1; ox <= 1; ox++) {
						for (int oz = -1; oz <= 1; oz++) {
							int nx = fx + ox;
							int nz = fz + oz;
							assertTrue(!trunks.trunk(nx, nz) || nx == tree.x() && nz == tree.z(),
									"flare of " + tree.x() + ", " + tree.z() + " next to the trunk candidate " + nx + ", " + nz);
						}
					}
				}
			}
			// Flares of two trees never within one block of each other.
			for (Map.Entry<Cell, Integer> e : flareOwner.entrySet()) {
				int x = e.getKey().x();
				int y = e.getKey().y();
				int z = e.getKey().z();
				for (int ox = -1; ox <= 1; ox++) {
					for (int oy = -1; oy <= 1; oy++) {
						for (int oz = -1; oz <= 1; oz++) {
							Integer o = flareOwner.get(new Cell(x + ox, y + oy, z + oz));
							assertTrue(o == null || o.equals(e.getValue()), "flares of two trees touch at " + x + ", " + z);
						}
					}
				}
			}
			// Few in the pine stand (pine flares are a short widening of the base of old trunks), many in the oak-hornbeam stand.
			assertTrue(flares > (grad ? 80 : 5), "flare blocks in the stand: " + flares);
			System.out.println("RootPlanTest stand " + (grad ? "oak-hornbeam" : "pine") + ": flare blocks " + flares + ", roots " + roots);
			assertTrue(roots > 1000, "roots in the stand: " + roots);
		}
	}

	/** Old broad-leaved trees have many flares, pine and birch few, mature trees fewer, young trees and thin trunks none. */
	@Test
	void habitsOfTheSpecies() {
		TreeShapePlan.Ground flat = (x, z) -> 63;
		Map<Species, double[]> stats = new EnumMap<>(Species.class);
		int n = 200;
		for (Species s : SPECIES) {
			double[] st = new double[6];
			for (int k = 0; k < n; k++) {
				RootVoxels old = RootPlan.plan(request(s, AgeClass.OLD_GROWTH, 31 * k, 17 * k, flat, null, 6));
				RootVoxels mature = RootPlan.plan(request(s, AgeClass.MATURE, 31 * k, 17 * k, flat, null, 6));
				RootVoxels pole = RootPlan.plan(request(s, AgeClass.POLE_STAGE, 31 * k, 17 * k, flat, null, 6));
				RootVoxels thin = RootPlan.plan(request(s, AgeClass.OLD_GROWTH, 31 * k, 17 * k, flat, null, 2));
				st[0] += old.flareCount();
				st[1] += mature.flareCount();
				st[2] += pole.flareCount() + thin.flareCount();
				st[3] += old.rootCount();
				int deepest = 0;
				for (int i = 0; i < old.rootCount(); i++) {
					deepest = Math.min(deepest, old.rootY(i));
				}
				st[4] += deepest;
				st[5] += pole.rootCount();
			}
			for (int i = 0; i < st.length; i++) {
				st[i] /= n;
			}
			stats.put(s, st);
		}
		StringBuilder report = new StringBuilder();
		stats.forEach((s, st) -> report.append(String.format(java.util.Locale.ROOT,
				"%n  %-13s flare blocks old %.1f mature %.1f young/thin %.1f, roots old %.0f (deepest dy %.1f), pole %.0f", s.id(), st[0],
				st[1], st[2], st[3], st[4], st[5])));
		System.out.println("RootPlanTest habits:" + report);
		for (Species s : SPECIES) {
			double[] st = stats.get(s);
			assertEquals(0.0, st[2], s + ": flares on a young tree or a thin trunk");
			assertTrue(st[1] < st[0], s + ": a mature tree has as many flare blocks as an old one");
			assertTrue(st[5] < st[3], s + ": a pole-stage tree has as many roots as an old one");
		}
		for (Species broad : List.of(Species.OAK, Species.BEECH, Species.LINDEN, Species.HORNBEAM)) {
			assertTrue(stats.get(broad)[0] >= 5, broad + ": flare blocks of an old tree " + stats.get(broad)[0]);
			for (Species weak : List.of(Species.SCOTS_PINE, Species.BIRCH)) {
				assertTrue(stats.get(broad)[0] > 3 * stats.get(weak)[0], broad + " against " + weak);
			}
		}
		// The spruce: shallow (its roots stay near the surface), flares as many as the broad-leaved trees.
		assertTrue(stats.get(Species.SPRUCE)[4] > stats.get(Species.OAK)[4], "spruce roots deeper than oak roots");
		assertTrue(stats.get(Species.SPRUCE)[0] >= 6, "spruce flare blocks " + stats.get(Species.SPRUCE)[0]);
		assertTrue(stats.get(Species.SCOTS_PINE)[4] < stats.get(Species.BIRCH)[4], "pine taproot not deeper than birch roots");
	}

	/**
	 * A wall of the ground: on a ledge 4 blocks above a lower terrace, the root cells at the wall turn into rooted dirt; no
	 * root in unknown columns (water).
	 */
	@Test
	void wallsAndUnknownColumns() {
		// Terrace: x >= 3 is 4 blocks lower; z >= 4 is water (unknown).
		TreeShapePlan.Ground terrace = (x, z) -> z >= 4 ? TreeShapePlan.Ground.UNKNOWN : x >= 3 ? 59 : 63;
		int exposed = 0;
		for (int k = 0; k < 40; k++) {
			Species s = SPECIES.get(k % SPECIES.size());
			RootVoxels v = RootPlan.plan(new RootPlan.Request(s, CrownLayer.CANOPY, AgeClass.OLD_GROWTH, SEED + k, 0, 64, 0, 6, terrace, null));
			check(v, terrace, s + " on the ledge, seed " + k);
			for (int i = 0; i < v.rootCount(); i++) {
				assertTrue(v.rootZ(i) < 4, "a root in an unknown column");
				int kind = v.rootKind(i);
				exposed += kind == RootVoxels.EXPOSED ? 1 : 0;
				if (kind == RootVoxels.ROOT) {
					// A root cell has soil on its four sides.
					for (int[] f : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
						int top = terrace.top(v.rootX(i) + f[0], v.rootZ(i) + f[1]);
						assertTrue(top != TreeShapePlan.Ground.UNKNOWN && v.y() + v.rootY(i) <= top, "an open side of a root cell");
					}
				}
			}
			for (int i = 0; i < v.flareCount(); i++) {
				assertTrue(v.flareX(i) < 3 && v.flareZ(i) < 4, "a flare off the ledge");
			}
		}
		assertTrue(exposed > 10, "exposed cells: " + exposed);
	}

	/** The lateral roots the windthrows turn up are the ones the living tree has (same values for the same tree). */
	@Test
	void lateralsMatchTheLivingTree() {
		double[][] a = RootPlan.laterals(Species.SPRUCE, CrownLayer.CANOPY, AgeClass.OLD_GROWTH, SEED, 10, 20, 5);
		double[][] b = RootPlan.laterals(Species.SPRUCE, CrownLayer.CANOPY, AgeClass.OLD_GROWTH, SEED, 10, 20, 5);
		assertEquals(a.length, b.length);
		for (int i = 0; i < a.length; i++) {
			assertTrue(java.util.Arrays.equals(a[i], b[i]));
			assertTrue(a[i][1] >= 2 && a[i][1] <= RootPlan.REACH, "length " + a[i][1]);
		}
		assertTrue(a.length >= 6, "spruce laterals " + a.length);
	}

	/**
	 * Review of I1: the lateral roots of a tree are no even fan (a pinwheel from above): the gaps between their azimuths
	 * differ within a tree.
	 */
	@Test
	void lateralsAreNoEvenFan() {
		int even = 0;
		int trees = 0;
		for (Species s : SPECIES) {
			for (int k = 0; k < 40; k++) {
				double[][] l = RootPlan.laterals(s, CrownLayer.CANOPY, AgeClass.OLD_GROWTH, SEED, 37 * k, -23 * k, 6);
				double[] az = new double[l.length];
				double minLen = Double.MAX_VALUE;
				double maxLen = 0;
				for (int i = 0; i < l.length; i++) {
					az[i] = Math.floorMod((long) Math.round(Math.toDegrees(l[i][0]) * 100), 36000L) / 100.0;
					minLen = Math.min(minLen, l[i][1]);
					maxLen = Math.max(maxLen, l[i][1]);
				}
				java.util.Arrays.sort(az);
				double minGap = 360;
				double maxGap = 0;
				for (int i = 0; i < az.length; i++) {
					double gap = i + 1 < az.length ? az[i + 1] - az[i] : az[0] + 360 - az[i];
					minGap = Math.min(minGap, gap);
					maxGap = Math.max(maxGap, gap);
				}
				trees++;
				if (maxGap - minGap < 15) {
					even++;
				}
			}
		}
		assertTrue(even < trees / 10, "trees with an even fan of laterals: " + even + " of " + trees);
	}
}
