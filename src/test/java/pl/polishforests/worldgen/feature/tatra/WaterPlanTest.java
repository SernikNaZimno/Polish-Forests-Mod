package pl.polishforests.worldgen.feature.tatra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Step TT6: the waterfalls and the springs of {@link WaterPlan} and the flow model {@link FluidSim} they are checked with.
 */
class WaterPlanTest {
	/** A plateau at Y 100 for x of at most 0, a rock face down to Y 80 at x of at least 3, a slope between them. */
	private static int cliff(int x, int z) {
		return x <= 0 ? 100 : x >= 3 ? 80 : 100 - 7 * x;
	}

	@Test
	void aWaterfallFallsIntoAPoolAndStaysInItsCells() {
		TerrainView t = new FlatTerrain(WaterPlanTest::cliff);
		WaterPlan.Plan p = WaterPlan.waterfall(t, 0, 0, 77);
		assertNotNull(p, "a 20 block drop holds a waterfall");
		assertTrue(p.fall() >= WaterPlan.MIN_FALL && p.fall() <= WaterPlan.MAX_FALL, "fall " + p.fall());
		assertTrue(FluidSim.contained(t, p.placements()), "the water stays in its cells");
		long sources = p.placements().stream().filter(c -> c.piece() == Piece.SOURCE).count();
		long falling = p.placements().stream().filter(c -> c.piece() == Piece.FALL).count();
		long lips = p.placements().stream().filter(c -> c.piece() == Piece.FLOW_LIP).count();
		assertEquals(1, lips);
		assertTrue(falling >= p.fall() - 2, "the column reaches the pool: " + falling + " of " + p.fall());
		// One source in the notch of the lip, two deep over the pool of 3 x 2 columns.
		assertEquals(13, sources);
		// The source sits in a cell that was ground, lined on three sides.
		Placement source = p.placements().stream().filter(c -> c.piece() == Piece.SOURCE && c.y() == p.y()).findFirst().orElseThrow();
		assertTrue(t.solid(source.x(), source.y(), source.z()));
	}

	@Test
	void aSlopeOfFortyFiveDegreesGetsAWaterfallWithACutPool() {
		TerrainView t = new FlatTerrain((x, z) -> 100 - x);
		WaterPlan.Plan p = WaterPlan.waterfall(t, 0, 0, 3);
		assertNotNull(p);
		assertTrue(FluidSim.contained(t, p.placements()));
		assertTrue(p.fall() >= WaterPlan.MIN_FALL);
		// The pool lies under the natural ground around it: its source cells are deeper than the ground there.
		long deep = p.placements().stream().filter(c -> c.piece() == Piece.SOURCE && c.y() < t.top(c.x(), c.z()) - 1).count();
		assertTrue(deep > 0, "a cut pool");
	}

	@Test
	void aPoolOverACaveIsRejected() {
		TerrainView t = new FlatTerrain(WaterPlanTest::cliff, c -> c[0] >= 1 && c[1] <= 80, c -> false);
		// Everything under the foot is hollow: the pool would drain into the cave.
		assertNull(WaterPlan.waterfall(t, 0, 0, 77));
	}

	@Test
	void waterNextToOtherWaterIsRejected() {
		TerrainView t = new FlatTerrain(WaterPlanTest::cliff, c -> false, c -> c[0] >= 4 && c[1] == 81);
		WaterPlan.Plan p = WaterPlan.waterfall(t, 0, 0, 77);
		// A plan may avoid the water by its place, but never touch it.
		if (p != null) {
			assertTrue(FluidSim.contained(t, p.placements()));
		}
	}

	@Test
	void aSlopeUnderAMeterPerBlockHoldsNoWaterfall() {
		TerrainView t = new FlatTerrain((x, z) -> 100 - x / 3);
		assertNull(WaterPlan.waterfall(t, 0, 0, 5));
		TerrainView flat = new FlatTerrain((x, z) -> 100);
		assertNull(WaterPlan.waterfall(flat, 0, 0, 5));
	}

	@Test
	void waterOnBareGroundSpreadsSoThePoolIsNeeded() {
		// A falling column onto flat ground with no pool reaches far beyond its cells.
		List<Placement> plan = new ArrayList<>();
		plan.add(new Placement(0, 120, 0, Piece.SOURCE));
		for (int y = 101; y <= 119; y++) {
			plan.add(new Placement(0, y, 0, Piece.FALL));
		}
		TerrainView ground = new FlatTerrain((x, z) -> 100);
		assertTrue(FluidSim.stray(ground, plan) > 20, "stray " + FluidSim.stray(ground, plan));
		assertFalse(FluidSim.contained(ground, plan));
		// A source with a free cell under it only falls: it does not spread sideways in the air.
		List<Placement> hanging = List.of(new Placement(0, 120, 0, Piece.SOURCE));
		assertTrue(FluidSim.stray(ground, hanging) > 0);
	}

	@Test
	void aSourceOverSolidGroundReachesSevenBlocksAndNoFurther() {
		TerrainView ground = new FlatTerrain((x, z) -> 100);
		List<Placement> plan = List.of(new Placement(0, 101, 0, Piece.SOURCE));
		// Diamond of radius 7 (amounts 7 ... 1) minus the source itself: 4 * (1 + ... + 7) = 112 cells.
		assertEquals(112, FluidSim.stray(ground, plan));
	}

	@Test
	void aSpringIsAClosedPool() {
		TerrainView t = new FlatTerrain((x, z) -> 100 + (Math.abs(x) > 4 ? 1 : 0));
		WaterPlan.Plan p = WaterPlan.spring(t, 0, 0, 3);
		assertNotNull(p);
		assertTrue(FluidSim.contained(t, p.placements()));
		assertTrue(p.sources() >= 8 && p.sources() <= 18, "sources " + p.sources());
		assertEquals(0, p.fall());
		// Every water cell is a source and lies at or under the ground level of the pool.
		for (Placement c : p.placements()) {
			if (c.piece().water()) {
				assertEquals(Piece.SOURCE, c.piece());
				assertTrue(c.y() <= 100);
			}
		}
	}

	@Test
	void aSpringOverACaveIsRejected() {
		TerrainView t = new FlatTerrain((x, z) -> 100, c -> c[1] <= 98, c -> false);
		assertNull(WaterPlan.spring(t, 0, 0, 3));
	}

	@Test
	void plansAreDeterministic() {
		TerrainView t = new FlatTerrain(WaterPlanTest::cliff);
		assertEquals(WaterPlan.waterfall(t, 0, 0, 5).placements(), WaterPlan.waterfall(t, 0, 0, 5).placements());
	}
}
