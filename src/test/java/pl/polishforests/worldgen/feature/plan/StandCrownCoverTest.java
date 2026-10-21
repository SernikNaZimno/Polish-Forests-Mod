package pl.polishforests.worldgen.feature.plan;

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
 */
@Tag("slow")
class StandCrownCoverTest {
	private static final int CHUNKS = 6;
	private static final double MAX_PER_TREE = 0.5;
	private static final double MAX_MEAN = 0.25;
	private static final double MAX_LONE = 0.6;
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
			SimulatedStands.plant(s.biome(), s.natural(), s.age(), CHUNKS, RANGES, SimulatedStands.relief(5), (v, species, layer) -> {
				CrownCover c = CrownCover.of(v);
				t.add(c);
				all.add(c);
			});
			report.append(String.format(Locale.ROOT, "%n  %s %s %s: %d trees, %s", s.biome().id(), s.natural() ? "natural" : "managed",
					s.age(), t.trees(), t.report(CHUNKS * CHUNKS)));
			assertTrue(t.exposedPerTree() <= MAX_PER_TREE, s + ": " + t.report(CHUNKS * CHUNKS));
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
