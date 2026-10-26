package pl.polishforests.worldgen.tree;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.habitat.Species;
import pl.polishforests.worldgen.landscape.Noise;
import pl.polishforests.worldgen.norms.CensusGrid;
import pl.polishforests.worldgen.norms.ForestCensus;

/**
 * The dead wood planner (step V11 of docs/04-pionowy-wycinek.md, §5 {@code DeadwoodPlanTest}): valid wood (the parent
 * chain, radii), determinism, logs resting on the ground (at least {@link DeadwoodPlan#MIN_RESTING} of their length with at
 * most one block of air below) on rolling ground, nothing in the unknown columns (water, streets, outside the window),
 * each kind counted by the census as it should be, the managed share and the ways of dying of the species.
 */
class DeadwoodPlanTest {
	private static final long SEED = 20260927L;
	private static final int GROUND = 64;
	/** The write window of chunk (0, 0): its eight neighbors. */
	private static final int MIN = -16;
	private static final int MAX = 31;

	private static TreeShapePlan.Ground flat() {
		return (x, z) -> x < MIN || x > MAX || z < MIN || z > MAX ? TreeShapePlan.Ground.UNKNOWN : GROUND;
	}

	/** Rolling ground: ±amplitude blocks, a wavelength of about 14 blocks, unknown outside the window. */
	private static TreeShapePlan.Ground rolling(double amplitude) {
		Noise n = new Noise(SEED).derive("test.deadwood.ground");
		return (x, z) -> x < MIN || x > MAX || z < MIN || z > MAX ? TreeShapePlan.Ground.UNKNOWN
				: GROUND + (int) Math.round(amplitude * n.fbm(x, z, 14, 2, 0.5));
	}

	private static DeadwoodVoxels plan(Species s, DeadwoodPlan.Kind kind, AgeClass age, boolean natural, int x, int z,
			TreeShapePlan.Ground g) {
		return DeadwoodPlan.plan(new DeadwoodPlan.Request(s, kind, CrownLayer.CANOPY, age, natural, SEED, x, g.top(x, z) + 1, z, g));
	}

	@Test
	void everyObjectHasValidWoodInsideTheWindow() {
		Map<DeadwoodPlan.Kind, Integer> empty = new EnumMap<>(DeadwoodPlan.Kind.class);
		int plans = 0;
		for (Species s : SpeciesShape.species()) {
			for (DeadwoodPlan.Kind kind : DeadwoodPlan.Kind.values()) {
				for (AgeClass age : List.of(AgeClass.MATURE, AgeClass.OLD_GROWTH)) {
					for (int k = 0; k < 4; k++) {
						int x = 3 * k + 2;
						int z = 15 - 4 * k;
						DeadwoodVoxels v = plan(s, kind, age, true, x, z, flat());
						plans++;
						if (v.empty()) {
							empty.merge(kind, 1, Integer::sum);
							continue;
						}
						checkWood(v, flat());
					}
				}
			}
		}
		int perKind = plans / DeadwoodPlan.Kind.values().length;
		for (Map.Entry<DeadwoodPlan.Kind, Integer> e : empty.entrySet()) {
			assertTrue(e.getValue() <= perKind / 20, e.getKey() + ": " + e.getValue() + " empty plans of " + perKind);
		}
		// The pole stage has the dead wood of the previous generation (its own stems are about 1 px thin).
		assertEquals(AgeClass.MATURE, DeadwoodPlan.deadAge(AgeClass.POLE_STAGE, false));
		assertEquals(AgeClass.OLD_GROWTH, DeadwoodPlan.deadAge(AgeClass.MATURE, true));
	}

	/** Parents face-adjacent and earlier, radii 1-7, every block above the ground of a known column. */
	private static void checkWood(DeadwoodVoxels v, TreeShapePlan.Ground g) {
		TreeVoxels t = v.wood();
		int roots = 0;
		for (int i = 0; i < t.woodCount(); i++) {
			assertTrue(t.radius(i) >= 1 && t.radius(i) <= 7, "radius " + t.radius(i));
			int x = t.x() + t.woodX(i);
			int z = t.z() + t.woodZ(i);
			int top = g.top(x, z);
			assertTrue(top != TreeShapePlan.Ground.UNKNOWN, v.kind() + ": wood in an unknown column " + x + ", " + z);
			assertTrue(t.y() + t.woodY(i) > top, v.kind() + ": wood in the ground at " + x + ", " + z);
			int p = t.parentIndex(i);
			if (p < 0) {
				// One root; a broken tree has two pieces, the snag and its top lying beside it.
				roots++;
				assertTrue(i == 0 || v.kind() == DeadwoodPlan.Kind.BROKEN && roots == 2, v.kind() + ": a second root at " + i);
				continue;
			}
			assertTrue(p < i, "parent before child");
			int[] step = TreeVoxels.STEP[t.parent(i)];
			assertEquals(t.woodX(p), t.woodX(i) + step[0], "parent x");
			assertEquals(t.woodY(p), t.woodY(i) + step[1], "parent y");
			assertEquals(t.woodZ(p), t.woodZ(i) + step[2], "parent z");
		}
		for (int s : v.soil()) {
			assertTrue(g.top(t.x() + v.x(s), t.z() + v.z(s)) != TreeShapePlan.Ground.UNKNOWN, "soil in an unknown column");
		}
		for (int p : v.pit()) {
			assertTrue(g.top(t.x() + v.x(p), t.z() + v.z(p)) != TreeShapePlan.Ground.UNKNOWN, "pit in an unknown column");
		}
	}

	@Test
	void plansAreDeterministic() {
		for (DeadwoodPlan.Kind kind : DeadwoodPlan.Kind.values()) {
			DeadwoodVoxels a = plan(Species.OAK, kind, AgeClass.OLD_GROWTH, true, 5, 9, rolling(3));
			DeadwoodVoxels b = plan(Species.OAK, kind, AgeClass.OLD_GROWTH, true, 5, 9, rolling(3));
			assertEquals(a.wood(), b.wood(), kind.name());
			assertArrayEquals(a.soil(), b.soil());
			assertArrayEquals(a.pit(), b.pit());
			assertArrayEquals(a.cover(), b.cover());
			assertEquals(a.direction(), b.direction());
		}
	}

	@Test
	void logsRestOnRollingGround() {
		TreeShapePlan.Ground g = rolling(3);
		Noise pick = new Noise(SEED).derive("test.deadwood.positions");
		int objects = 0;
		int lyingBlocks = 0;
		int resting = 0;
		int worst = 0;
		for (DeadwoodPlan.Kind kind : new DeadwoodPlan.Kind[] {DeadwoodPlan.Kind.LOG, DeadwoodPlan.Kind.BROKEN,
				DeadwoodPlan.Kind.WINDTHROW}) {
			for (int k = 0; k < 150; k++) {
				int x = (int) (pick.unit(k, kind.ordinal(), 1) * 16);
				int z = (int) (pick.unit(k, kind.ordinal(), 2) * 16);
				Species s = new ArrayList<>(SpeciesShape.species()).get(k % SpeciesShape.species().size());
				DeadwoodVoxels v = plan(s, kind, k % 3 == 0 ? AgeClass.MATURE : AgeClass.OLD_GROWTH, k % 2 == 0, x, z, g);
				if (v.empty() || v.direction() < 0) {
					continue;
				}
				checkWood(v, g);
				TreeVoxels t = v.wood();
				int n = 0;
				int ok = 0;
				for (int i = 0; i < t.woodCount(); i++) {
					// The lying stem: the blocks at the base level along the fall direction.
					if (t.woodY(i) != 0 || t.parent(i) != DeadwoodPlan.opposite(v.direction())) {
						continue;
					}
					int air = t.y() - 1 - g.top(t.x() + t.woodX(i), t.z() + t.woodZ(i));
					assertTrue(air <= 2, kind + " over " + air + " blocks of air");
					n++;
					if (air <= 1) {
						ok++;
					}
				}
				if (n == 0) {
					continue;
				}
				objects++;
				lyingBlocks += n;
				resting += ok;
				assertTrue(ok >= DeadwoodPlan.MIN_RESTING * n - 1e-9, String.format(Locale.ROOT, "%s of %s at %d, %d: %d of %d blocks rest",
						kind, s, x, z, ok, n));
				worst = Math.max(worst, n - ok);
			}
		}
		assertTrue(objects > 350, "lying objects planned: " + objects);
		System.out.printf(Locale.ROOT, "[deadwood] rolling ground: %d lying objects, %d blocks, %.1f%% resting%n", objects, lyingBlocks,
				100.0 * resting / lyingBlocks);
	}

	@Test
	void nothingInUnknownColumns() {
		// A stream across the window (x 10-12 unknown) and the window's edge.
		TreeShapePlan.Ground base = flat();
		TreeShapePlan.Ground g = (x, z) -> x >= 10 && x <= 12 ? TreeShapePlan.Ground.UNKNOWN : base.top(x, z);
		for (DeadwoodPlan.Kind kind : DeadwoodPlan.Kind.values()) {
			for (int x = 0; x < 16; x++) {
				if (x >= 10 && x <= 12) {
					continue;
				}
				DeadwoodVoxels v = plan(Species.SPRUCE, kind, AgeClass.OLD_GROWTH, true, x, 8, g);
				checkWood(v, g);
				for (int c : v.cover()) {
					assertTrue(g.top(v.wood().x() + v.x(c), v.wood().z() + v.z(c)) != TreeShapePlan.Ground.UNKNOWN);
				}
			}
		}
	}

	@Test
	void theCensusCountsEachKind() {
		Map<DeadwoodPlan.Kind, List<ForestCensus.Kind>> expected = Map.of(DeadwoodPlan.Kind.LOG, List.of(ForestCensus.Kind.LOG),
				DeadwoodPlan.Kind.SNAG, List.of(ForestCensus.Kind.SNAG), DeadwoodPlan.Kind.BROKEN,
				List.of(ForestCensus.Kind.LOG, ForestCensus.Kind.SNAG), DeadwoodPlan.Kind.WINDTHROW, List.of(ForestCensus.Kind.LOG),
				DeadwoodPlan.Kind.STUMP, List.of(ForestCensus.Kind.STUMP));
		for (Species s : List.of(Species.OAK, Species.SCOTS_PINE, Species.SPRUCE, Species.BIRCH)) {
			for (DeadwoodPlan.Kind kind : DeadwoodPlan.Kind.values()) {
				for (AgeClass age : List.of(AgeClass.MATURE, AgeClass.OLD_GROWTH)) {
					CensusGrid grid = grid();
					DeadwoodVoxels v = plan(s, kind, age, kind != DeadwoodPlan.Kind.STUMP, 8, 8, flat());
					assertFalse(v.empty(), kind + " of " + s);
					DeadwoodGrid.write(grid, v);
					List<ForestCensus.Kind> kinds = new ArrayList<>(ForestCensus.of(grid).objects().stream().map(ForestCensus.WoodObject::kind)
							.sorted().toList());
					List<ForestCensus.Kind> want = new ArrayList<>(expected.get(kind));
					want.sort(null);
					assertEquals(want, kinds, kind + " of " + s + " " + age);
				}
			}
		}
	}

	@Test
	void aSnagTakesNoLeavesOfTheCrownsAroundIt() {
		CensusGrid grid = grid();
		DeadwoodVoxels v = plan(Species.OAK, DeadwoodPlan.Kind.SNAG, AgeClass.OLD_GROWTH, true, 8, 8, flat());
		// A living tree two blocks away whose crown wraps the snag from 3 blocks up.
		for (int dy = 1; dy <= 14; dy++) {
			grid.setWood(11, dy, 8, true, CensusGrid.trunk(4, Species.HORNBEAM, "polishforests:hornbeam_trunk", TreeVoxels.DOWN));
		}
		for (int x = 4; x <= 14; x++) {
			for (int z = 4; z <= 12; z++) {
				for (int dy = 4; dy <= 14; dy++) {
					if (grid.cell(x, dy, z) == CensusGrid.AIR && Math.abs(x - 11) + Math.abs(z - 8) <= 5) {
						grid.set(x, dy, z, CensusGrid.LEAVES);
					}
				}
			}
		}
		DeadwoodGrid.write(grid, v);
		List<ForestCensus.WoodObject> objects = ForestCensus.of(grid).objects();
		assertEquals(2, objects.size(), objects.toString());
		for (ForestCensus.WoodObject o : objects) {
			if (o.species() == Species.OAK) {
				assertEquals(ForestCensus.Kind.SNAG, o.kind());
			} else {
				assertEquals(ForestCensus.Kind.TREE, o.kind());
			}
		}
	}

	private static CensusGrid grid() {
		CensusGrid g = new CensusGrid(MIN, MIN, MAX - MIN + 1, MAX - MIN + 1, 48);
		for (int x = MIN; x <= MAX; x++) {
			for (int z = MIN; z <= MAX; z++) {
				g.setGround(x, z, GROUND, "minecraft:grass_block");
			}
		}
		return g;
	}

	@Test
	void managedStandsKeepAnEighthOfTheDeadTrees() {
		int kept = 0;
		int n = 40_000;
		for (int i = 0; i < n; i++) {
			assertNotNull(DeadwoodPlan.deadTree(SEED, Species.OAK, true, i % 200, i / 200));
			if (DeadwoodPlan.deadTree(SEED, Species.OAK, false, i % 200, i / 200) != null) {
				kept++;
			}
		}
		assertEquals(DeadwoodPlan.MANAGED_KEEP, kept / (double) n, 0.01);
		StandMap natural = StandMap.uniform(AgeClass.MATURE, true);
		assertNull(DeadwoodPlan.request(SEED, Species.OAK, true, natural, 0, GROUND + 1, 0, flat()), "no stumps in a natural stand");
		assertEquals(DeadwoodPlan.Kind.STUMP, DeadwoodPlan.request(SEED, Species.OAK, true, StandMap.uniform(AgeClass.MATURE, false), 0,
				GROUND + 1, 0, flat()).kind());
	}

	@Test
	void speciesDieTheirOwnWay() {
		Map<Species, Map<DeadwoodPlan.Kind, Integer>> counts = new EnumMap<>(Species.class);
		for (Species s : List.of(Species.SPRUCE, Species.OAK, Species.HORNBEAM, Species.BIRCH)) {
			Map<DeadwoodPlan.Kind, Integer> c = new EnumMap<>(DeadwoodPlan.Kind.class);
			for (int i = 0; i < 20_000; i++) {
				c.merge(DeadwoodPlan.deadTree(SEED, s, true, i % 150, i / 150), 1, Integer::sum);
			}
			counts.put(s, c);
		}
		assertTrue(counts.get(Species.SPRUCE).get(DeadwoodPlan.Kind.WINDTHROW) > 3 * counts.get(Species.OAK).get(DeadwoodPlan.Kind.WINDTHROW),
				"spruces are thrown by the wind: " + counts);
		assertTrue(counts.get(Species.OAK).get(DeadwoodPlan.Kind.SNAG) > counts.get(Species.HORNBEAM).get(DeadwoodPlan.Kind.SNAG),
				"oaks stand long as snags: " + counts);
		assertTrue(counts.get(Species.BIRCH).get(DeadwoodPlan.Kind.BROKEN) > counts.get(Species.HORNBEAM).get(DeadwoodPlan.Kind.BROKEN),
				"birches break: " + counts);
	}

	@Test
	void aWindthrowHasItsRootPlateAndPit() {
		for (Species s : List.of(Species.SPRUCE, Species.OAK, Species.SCOTS_PINE)) {
			DeadwoodVoxels v = plan(s, DeadwoodPlan.Kind.WINDTHROW, AgeClass.OLD_GROWTH, true, 8, 8, flat());
			assertTrue(v.direction() >= 0, s + ": lying");
			assertTrue(v.soil().length >= 6, s + ": root plate of " + v.soil().length + " blocks");
			assertTrue(v.pit().length >= 3, s + ": pit of " + v.pit().length + " columns");
			int[] f = TreeVoxels.STEP[v.direction()];
			for (int c : v.soil()) {
				// The plate stands on edge in the plane of the base, across the fall direction.
				assertEquals(0, v.x(c) * f[0] + v.z(c) * f[2], s + ": soil off the plate's plane");
			}
			for (int p : v.pit()) {
				assertTrue(v.x(p) * f[0] + v.z(p) * f[2] < 0, s + ": the pit lies behind the plate");
			}
		}
	}
}
