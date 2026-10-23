package pl.polishforests.worldgen.feature.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.habitat.Species;
import pl.polishforests.worldgen.tree.AgeClass;
import pl.polishforests.worldgen.tree.CrownCover;
import pl.polishforests.worldgen.tree.CrownLayer;
import pl.polishforests.worldgen.tree.SpeciesShape;
import pl.polishforests.worldgen.tree.TreeRequest;
import pl.polishforests.worldgen.tree.TreeShapePlan;

/**
 * Bare wood in the crowns of the stands of the slice (review of V9, round 2): in simulated stands on rolling ground
 * ({@link SimulatedStands}, all species of the palettes), the wood of the crowns that is bare and open to the side
 * ({@link CrownCover}: at most one own leaf among its 26 neighbors) stays under {@value #MAX_PER_TREE} blocks per tree in
 * every stand and under {@value #MAX_MEAN} on average. Before round 2 of the review the old-growth oak-hornbeam forest had
 * 0.90 and the managed one 1.75 per tree (an arm of a "lamp post", a gray candelabra of limbs under the foliage, a bare
 * whorl of a pine between crowns that do not meet); after it 0.26 and 0.60, and with the leaf below each branch block
 * and the sleeve of the lower crown (round 2, after the frames) 0.08 and 0.12, the limits tightened from 0.8 / 0.4 / 1.6.
 * Since V10 also no canopy tree is a bare pole ({@link CrownCover#pole}: before, 3-7% of the canopy trees of 12 or more
 * blocks had fewer than 12 leaves, a thin trunk with a tuft seen from above the canopy); the planner leaves such trees out.
 */
@Tag("slow")
class StandCrownCoverTest {
	private static final int CHUNKS = 6;
	private static final double MAX_PER_TREE = 0.5;
	private static final double MAX_MEAN = 0.25;
	private static final double MAX_LONE = 0.6;
	/** Largest share of the canopy trees of a stand left out as suppressed (step V10): the trunk counts of N1 stay in tolerance. */
	private static final double MAX_SUPPRESSED = 0.12;
	private static final int RANGES = Species.FLAG_HORNBEAM | Species.FLAG_BEECH | Species.FLAG_SPRUCE;

	private record Stand(HabitatBiome biome, boolean natural, AgeClass age) {
	}

	@Test
	void crownsHaveLittleBareWoodOpenToTheSide() {
		Stand[] stands = {new Stand(HabitatBiome.OAK_HORNBEAM_FOREST, true, AgeClass.OLD_GROWTH),
				new Stand(HabitatBiome.OAK_HORNBEAM_FOREST, false, AgeClass.MATURE),
				new Stand(HabitatBiome.FRESH_PINE_FOREST, true, AgeClass.OLD_GROWTH),
				new Stand(HabitatBiome.FRESH_PINE_FOREST, false, AgeClass.MATURE)};
		CrownCover.Totals all = new CrownCover.Totals();
		StringBuilder report = new StringBuilder();
		for (Stand s : stands) {
			CrownCover.Totals t = new CrownCover.Totals();
			int[] poles = new int[2];
			long suppressed0 = SimulatedStands.SUPPRESSED.get();
			SimulatedStands.plant(s.biome(), s.natural(), s.age(), CHUNKS, RANGES, SimulatedStands.relief(5), (v, species, layer) -> {
				CrownCover c = CrownCover.of(v);
				t.add(c);
				all.add(c);
				if (layer == CrownLayer.CANOPY) {
					poles[0]++;
					if (CrownCover.pole(v)) {
						poles[1]++;
					}
				}
			});
			long suppressed = SimulatedStands.SUPPRESSED.get() - suppressed0;
			report.append(String.format(Locale.ROOT, "%n  %s %s %s: %d trees, %s; canopy %d, bare poles %d, suppressed %d (%.1f%%)",
					s.biome().id(), s.natural() ? "natural" : "managed", s.age(), t.trees(), t.report(CHUNKS * CHUNKS), poles[0], poles[1],
					suppressed, 100.0 * suppressed / Math.max(1, poles[0] + suppressed)));
			assertTrue(t.exposedPerTree() <= MAX_PER_TREE, s + ": " + t.report(CHUNKS * CHUNKS));
			// Carried from the review of V9, round 2: no canopy tree of the stand is a bare pole with a tuft.
			assertEquals(0, poles[1], s + ": canopy trees with a degenerate crown");
			assertTrue(suppressed <= MAX_SUPPRESSED * (poles[0] + suppressed), s + ": suppressed " + suppressed + " of " + (poles[0] + suppressed));
		}
		System.out.printf(Locale.ROOT, "[crown cover]%s%n  all: %.2f per tree%n", report, all.exposedPerTree());
		assertTrue(all.exposedPerTree() <= MAX_MEAN, "mean bare wood open to the side per tree: " + all.exposedPerTree());
	}

	/**
	 * The lone trees of every shaped species and age class (the board of {@code slice -Psites=trees}): under
	 * {@value #MAX_LONE} per tree. The wide open-grown crowns hide wood in their core farther than the side rays of
	 * {@link CrownCover} reach, so the limit is higher (round 2: 1.37).
	 */
	@Test
	void loneTreesHaveLittleBareWoodOpenToTheSide() {
		CrownCover.Totals t = new CrownCover.Totals();
		StringBuilder report = new StringBuilder();
		for (Species sp : SpeciesShape.species()) {
			for (AgeClass age : AgeClass.values()) {
				CrownCover c = CrownCover.of(TreeShapePlan.plan(new TreeRequest(sp, CrownLayer.CANOPY, age, SimulatedStands.SEED, 0, 64, 0)));
				t.add(c);
				report.append(String.format(Locale.ROOT, " %s/%s %d", sp.id(), age.ordinal(), c.exposed()));
			}
		}
		System.out.printf(Locale.ROOT, "[crown cover] lone trees: %.2f per tree:%s%n", t.exposedPerTree(), report);
		assertTrue(t.exposedPerTree() <= MAX_LONE, "lone trees: " + t.exposedPerTree());
	}
}
