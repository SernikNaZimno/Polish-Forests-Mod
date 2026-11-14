package pl.polishforests.worldgen.feature.tatra;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Step TT6 (review fixes): a plan is built only inside the chunk of its builder and never over a tree trunk ({@link Fit}).
 */
class PlanFitTest {
	/** A plateau at Y 100 for x of at most 0, a rock face down to Y 80 at x of at least 3, a slope between them. */
	private static int cliff(int x, int z) {
		return x <= 0 ? 100 : x >= 3 ? 80 : 100 - 7 * x;
	}

	private static boolean inChunk(int x, int z) {
		return x >= 0 && x < 16 && z >= 0 && z < 16;
	}

	@Test
	void everyCellOfAPlanLiesInTheChunkOfTheBuilder() {
		// The anchor near the west edge of the chunk: a plan that would cross the edge is left out, the others are kept.
		for (long seed = 0; seed < 6; seed++) {
			TerrainView t = new FlatTerrain((x, z) -> 1_500).writableOnly(PlanFitTest::inChunk);
			HutPlan.Plan hut = HutPlan.hut(t, 4, 5, seed);
			assertNotNull(hut, "a flat ground holds a hut inside the chunk");
			for (Placement c : hut.placements()) {
				assertTrue(inChunk(c.x(), c.z()), "hut cell " + c);
			}
			TerrainView slope = new FlatTerrain(PlanFitTest::cliff).writableOnly((x, z) -> x >= -2 && x < 14 && z >= -3 && z < 13);
			WaterPlan.Plan p = WaterPlan.waterfall(slope, 4, 5, seed);
			if (p != null) {
				for (Placement c : p.placements()) {
					assertTrue(c.x() >= -2 && c.x() < 14 && c.z() >= -3 && c.z() < 13, "waterfall cell " + c);
				}
			}
		}
		// Nothing may be written: no plan.
		assertNull(HutPlan.hut(new FlatTerrain((x, z) -> 1_500).writableOnly((x, z) -> false), 4, 5, 1));
		assertNull(WaterPlan.spring(new FlatTerrain((x, z) -> 1_500).writableOnly((x, z) -> false), 4, 5, 1));
	}

	@Test
	void aTrunkInAColumnOfThePlanRejectsTheCandidate() {
		// Trunks on the whole ground: no hut, no spring, no waterfall (every column has a log over the ground).
		TerrainView forest = new FlatTerrain((x, z) -> 1_500, c -> false, c -> false).withLogs(c -> c[1] > 1_500 && c[1] < 1_510);
		assertNull(HutPlan.hut(forest, 4, 5, 3));
		assertNull(WaterPlan.spring(forest, 4, 5, 3));
		TerrainView woodedCliff = new FlatTerrain(PlanFitTest::cliff).withLogs(c -> c[1] > cliff((int) c[0], (int) c[2]) && c[1] < 120);
		assertNull(WaterPlan.waterfall(woodedCliff, 0, 0, 77));
	}

	@Test
	void aSingleTrunkIsAvoided() {
		// One trunk at (6, 1501, 5): the hut and the spring go elsewhere and do not touch its column.
		TerrainView t = new FlatTerrain((x, z) -> 1_500).withLogs(c -> c[0] == 6 && c[2] == 5 && c[1] >= 1_501 && c[1] <= 1_508);
		HutPlan.Plan hut = HutPlan.hut(t, 6, 5, 3);
		assertNotNull(hut);
		WaterPlan.Plan spring = WaterPlan.spring(t, 6, 5, 3);
		assertNotNull(spring);
		for (List<Placement> plan : List.of(hut.placements(), spring.placements())) {
			for (Placement c : plan) {
				assertTrue(!(c.x() == 6 && c.z() == 5), "the column of the trunk is not planned: " + c);
			}
		}
	}

	@Test
	void aTrunkBesideThePlanIsNotInTheWay() {
		// The trunk next to the columns of a hut that stands elsewhere does not matter: the plan of a free ground fits.
		TerrainView far = new FlatTerrain((x, z) -> 1_500).withLogs(c -> c[0] == 1_000);
		assertNotNull(HutPlan.hut(far, 4, 5, 3));
	}
}
