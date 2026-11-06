package pl.polishforests.worldgen.feature;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pl.polishforests.block.GroundPlant;
import pl.polishforests.season.GroundPhases;
import pl.polishforests.season.GroundPhases.Look;
import pl.polishforests.season.SeasonPhase;
import pl.polishforests.season.SubSeason;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.habitat.Species;
import pl.polishforests.worldgen.habitat.Zone;
import pl.polishforests.worldgen.norms.ForestNorms;
import pl.polishforests.worldgen.norms.ForestNorms.Forest;
import pl.polishforests.worldgen.norms.ForestNorms.Norm;
import pl.polishforests.worldgen.tree.AgeClass;
import pl.polishforests.worldgen.tree.CrownLayer;
import pl.polishforests.worldgen.tree.SpeciesShape;
import pl.polishforests.worldgen.tree.TreeRequest;
import pl.polishforests.worldgen.tree.TreeShapePlan;
import pl.polishforests.worldgen.tree.TreeVoxels;

/**
 * Step F5 of docs/04-pionowy-wycinek.md (the floodplain forests): the hop on the trunks (a pure function of the seed and
 * the tree, as the vines of step L1), the shapes of the white willow and the poplar, the seasons of the new herbs and the
 * card of the floodplain forests and the osier scrub.
 */
class FloodplainStepF5Test {
	private static final long SEED = 20260927L;

	@Test
	void hopStrandsArePureAndBounded() {
		int carrying = 0;
		int n = 4000;
		int[] sides = new int[TreeHop.MAX_STRAND + 1];
		for (int k = 0; k < n; k++) {
			int x = 37 * k - 70000;
			int z = 101 * k + 13;
			int[] a = TreeHop.strands(SEED, x, z, 0.35);
			int[] b = TreeHop.strands(SEED, x, z, 0.35);
			if (a == null) {
				assertNull(b);
				continue;
			}
			assertArrayEquals(a, b);
			carrying++;
			assertEquals(4, a.length);
			for (int s : a) {
				assertTrue(s == 0 || s >= 2 && s <= TreeHop.MAX_STRAND, "strand " + s);
				sides[s]++;
			}
			// A lower chance keeps a subset of the trees with the same strands (one draw per tree).
			int[] lower = TreeHop.strands(SEED, x, z, 0.2);
			if (lower != null) {
				assertArrayEquals(a, lower);
			}
		}
		assertEquals(0.35, carrying / (double) n, 0.03, "share of trees with hop");
		assertEquals(0.35, sides[0] / (4.0 * carrying), 0.04, "bare sides");
		assertTrue(sides[TreeHop.MAX_STRAND] > 0 && sides[2] > 0);
		for (int k = 0; k < 200; k++) {
			assertNull(TreeHop.strands(SEED, k, -k, 0));
		}
		assertNotNull(TreeHop.strands(SEED, 1, 2, 1.0));
	}

	@Test
	void hopGrowsOnlyInTheFloodplain() {
		assertEquals(0.4, TreeHop.treeChance(HabitatBiome.ASH_ALDER_FOREST, Zone.RIVERSIDE_WILLOWS), 1e-9);
		assertEquals(0.35, TreeHop.treeChance(HabitatBiome.WILLOW_POPLAR_FOREST, Zone.NONE), 1e-9);
		assertEquals(0.35, TreeHop.treeChance(HabitatBiome.WILLOW_SCRUB, Zone.NONE), 1e-9);
		assertEquals(0.22, TreeHop.treeChance(HabitatBiome.ELM_ASH_FOREST, Zone.NONE), 1e-9);
		for (HabitatBiome b : HabitatBiome.values()) {
			if (b != HabitatBiome.WILLOW_POPLAR_FOREST && b != HabitatBiome.WILLOW_SCRUB && b != HabitatBiome.ELM_ASH_FOREST) {
				assertEquals(0, TreeHop.treeChance(b, Zone.NONE), 1e-9, b.id());
			}
		}
	}

	/** The white willow leans and forks low; the poplar is the tallest broad-leaved tree. */
	@Test
	void willowLeansAndPoplarIsTall() {
		for (Species s : SpeciesShape.species()) {
			if (s != Species.WHITE_WILLOW) {
				assertTrue(SpeciesShape.of(s).lean() < SpeciesShape.of(Species.WHITE_WILLOW).lean(), s.id());
				assertFalse(SpeciesShape.lowFork(s), s.id());
			}
			if (s != Species.POPLAR) {
				assertTrue(SpeciesShape.of(s).height().mature() + 2 <= SpeciesShape.of(Species.POPLAR).height().mature(), s.id());
			}
		}
		assertTrue(SpeciesShape.lowFork(Species.WHITE_WILLOW));
		assertEquals(0.25, SpeciesShape.stoolChance(Species.WHITE_WILLOW), 1e-9);
		double willowLean = meanCrownOffset(Species.WHITE_WILLOW);
		double oakLean = meanCrownOffset(Species.OAK);
		assertTrue(willowLean > oakLean + 0.5, "willow " + willowLean + " oak " + oakLean);
		double poplar = 0;
		int n = 60;
		for (int k = 0; k < n; k++) {
			TreeVoxels v = TreeShapePlan.plan(new TreeRequest(Species.POPLAR, CrownLayer.CANOPY, AgeClass.MATURE, SEED, 300 * k - 9000,
					64, 17 * k));
			poplar += v.height();
		}
		poplar /= n;
		assertTrue(poplar >= 26 && poplar <= 36, "mature poplar " + poplar);
	}

	/** Mean horizontal distance of the leaf centroid from the trunk base of lone mature canopy trees. */
	private static double meanCrownOffset(Species species) {
		double sum = 0;
		int n = 80;
		for (int k = 0; k < n; k++) {
			TreeVoxels v = TreeShapePlan.plan(new TreeRequest(species, CrownLayer.CANOPY, AgeClass.MATURE, SEED, 300 * k - 9000, 64,
					17 * k + 5));
			double x = 0;
			double z = 0;
			for (int i = 0; i < v.leafCount(); i++) {
				x += v.leafX(i);
				z += v.leafZ(i);
			}
			if (v.leafCount() > 0) {
				sum += Math.hypot(x / v.leafCount(), z / v.leafCount());
			}
		}
		return sum / n;
	}

	private static Map<Look, Double> shares(GroundPlant plant, int step) {
		Map<Look, Double> out = new EnumMap<>(Look.class);
		for (Look l : Look.values()) {
			out.put(l, 0.0);
		}
		int size = 40;
		for (int x = 0; x < size; x++) {
			for (int z = 0; z < size; z++) {
				out.merge(GroundPhases.look(plant, step, x * 7 - 100, z * 13 + 5), 1.0 / (size * size), Double::sum);
			}
		}
		return out;
	}

	private static int step(SubSeason sub, int k) {
		return SeasonPhase.firstStep(sub) + k;
	}

	@Test
	void herbsOfTheFloodplainOfStepF5() {
		// The lesser celandine flowers in April and is gone by June; the wild garlic flowers in May and is gone by July.
		assertEquals(1.0, shares(GroundPlant.LESSER_CELANDINE, step(SubSeason.MID_SPRING, 0)).get(Look.FULL), 1e-9, "celandine IV");
		assertEquals(1.0, shares(GroundPlant.LESSER_CELANDINE, step(SubSeason.EARLY_SUMMER, 1)).get(Look.NONE), 1e-9, "celandine VI");
		assertEquals(1.0, shares(GroundPlant.WILD_GARLIC, step(SubSeason.MID_SPRING, 0)).get(Look.LEAVES), 1e-9, "garlic IV");
		assertEquals(1.0, shares(GroundPlant.WILD_GARLIC, step(SubSeason.LATE_SPRING, 0)).get(Look.FULL), 1e-9, "garlic V");
		assertEquals(1.0, shares(GroundPlant.WILD_GARLIC, step(SubSeason.MID_SUMMER, 1)).get(Look.NONE), 1e-9, "garlic VII");
		// The reed canary grass: panicles in June, leaves in May, gone in winter.
		assertEquals(1.0, shares(GroundPlant.REED_CANARY_GRASS, step(SubSeason.EARLY_SUMMER, 1)).get(Look.FULL), 1e-9);
		assertEquals(1.0, shares(GroundPlant.REED_CANARY_GRASS, step(SubSeason.LATE_SPRING, 0)).get(Look.LEAVES), 1e-9);
		assertEquals(1.0, shares(GroundPlant.REED_CANARY_GRASS, step(SubSeason.MID_WINTER, 1)).get(Look.NONE), 1e-9);
		// The hop: cones in September, leaves in July, gone in winter (no model, no outline: withdrawn).
		assertEquals(1.0, shares(GroundPlant.HOP, step(SubSeason.EARLY_AUTUMN, 0)).get(Look.FULL), 1e-9, "hop cones");
		assertEquals(1.0, shares(GroundPlant.HOP, step(SubSeason.MID_SUMMER, 1)).get(Look.LEAVES), 1e-9, "hop VII");
		assertEquals(1.0, shares(GroundPlant.HOP, step(SubSeason.MID_WINTER, 1)).get(Look.NONE), 1e-9, "hop I");
		assertTrue(GroundPhases.withdrawn(GroundPlant.HOP, step(SubSeason.MID_WINTER, 1), 3, 4));
		assertSame(GroundPlant.Kind.CLIMBER, GroundPlant.HOP.kind());
	}

	/** The card of the floodplain forests and the osier scrub (docs/04 "Projekt F5"). */
	@Test
	void floodplainCardOfStepF5() {
		for (Forest f : new Forest[] {Forest.ELM_ASH_NATURAL, Forest.ELM_ASH_MANAGED}) {
			range(20, 50, ForestNorms.COMPOSITION + "oak", f, null);
			range(10, 40, ForestNorms.COMPOSITION + "elm", f, null);
			range(70, 90, ForestNorms.CANOPY, f, null);
			range(20, 60, ForestNorms.UNDERSTORY, f, null);
		}
		Forest wp = Forest.WILLOW_POPLAR_NATURAL;
		range(50, 80, ForestNorms.COMPOSITION + "white_willow", wp, null);
		range(10, 40, ForestNorms.COMPOSITION + "poplar", wp, null);
		range(30, 70, ForestNorms.COMPOSITION + "poplar", wp, ForestNorms.Site.POPLAR);
		range(10, 40, ForestNorms.COMPOSITION + "white_willow", wp, ForestNorms.Site.POPLAR);
		range(50, 70, ForestNorms.CANOPY, wp, null);
		// The managed stands as poplar plantings (the taller even-aged poplars hold the a1).
		Forest wpm = Forest.WILLOW_POPLAR_MANAGED;
		range(30, 95, ForestNorms.COMPOSITION + "poplar", wpm, null);
		range(50, 95, ForestNorms.COMPOSITION + "poplar", wpm, ForestNorms.Site.POPLAR);
		range(60, 95, ForestNorms.CANOPY, wpm, null);
		range(4, 14, ForestNorms.TREES_PER_CHUNK, wpm, null);
		for (Forest f : new Forest[] {wp, wpm}) {
			range(70, 100, ForestNorms.HERBS, f, null);
		}
		range(100, 220, ForestNorms.DEADWOOD_VOLUME, wp, null);
		range(90, 170, ForestNorms.DEADWOOD_VOLUME, Forest.ELM_ASH_NATURAL, null);
		range(80, 100, ForestNorms.UNDERSTORY, Forest.WILLOW_SCRUB, null);
		// The osier scrub is no forest: one column for both stands.
		assertSame(Forest.WILLOW_SCRUB, Forest.of(HabitatBiome.WILLOW_SCRUB, true));
		assertSame(Forest.WILLOW_SCRUB, Forest.of(HabitatBiome.WILLOW_SCRUB, false));
		assertSame(Forest.ELM_ASH_MANAGED, Forest.of(HabitatBiome.ELM_ASH_FOREST, false));
		assertNull(Forest.of(HabitatBiome.REEDBED, true));
		assertTrue(ForestNorms.COMPOSITION_KEYS.contains("white_willow") && ForestNorms.COMPOSITION_KEYS.contains("poplar"));
		assertEquals("other", ForestNorms.COMPOSITION_KEYS.get(ForestNorms.COMPOSITION_KEYS.size() - 1));
	}

	private static void range(double min, double max, String metric, Forest forest, ForestNorms.Site site) {
		Norm n = (site == null ? ForestNorms.of(metric, forest, null, null) : ForestNorms.of(metric, forest, null, null, site))
				.orElseThrow(() -> new AssertionError("no norm " + metric + " " + forest + " " + site));
		assertEquals(min, n.min(), 1e-9, metric + " " + forest);
		assertEquals(max, n.max(), 1e-9, metric + " " + forest);
	}
}
