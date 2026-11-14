package pl.polishforests.worldgen.feature.tatra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Step TT6: the boulders, the huts and the cave mouths of the Tatras ({@link BoulderPlan}, {@link HutPlan},
 * {@link MouthPlan}) on synthetic ground.
 */
class DetailPlansTest {
	private static long count(List<Placement> plan, Piece piece) {
		return plan.stream().filter(p -> p.piece() == piece).count();
	}

	@Test
	void aBoulderIsALumpOfRockSunkIntoTheGround() {
		for (int radius = 1; radius <= BoulderPlan.MAX_RADIUS; radius++) {
			List<Placement> plan = BoulderPlan.boulder(5, 10, 100, -4, radius, 100);
			long rock = count(plan, Piece.ROCK);
			// A sphere of radius r has about 4/3 pi r^3 cells; the lump is sunk one block and rounded.
			assertTrue(rock >= 5 && rock <= 4 * radius * radius * radius + 20, "radius " + radius + ": " + rock + " blocks");
			for (Placement p : plan) {
				if (p.piece() == Piece.ROCK) {
					assertTrue(p.y() >= 99 && p.y() <= 100 + 2 * radius, "y " + p.y());
					assertTrue(Math.abs(p.x() - 10) <= radius && Math.abs(p.z() + 4) <= radius);
				}
			}
			// With every top lichened, a lichen stands on each column's highest rock block and nowhere else.
			long lichens = count(plan, Piece.LICHEN);
			assertTrue(lichens >= 1);
			for (Placement p : plan) {
				if (p.piece() == Piece.LICHEN) {
					assertTrue(p.param() >= 2 && p.param() <= 4);
					assertTrue(plan.contains(new Placement(p.x(), p.y() - 1, p.z(), Piece.ROCK)));
					assertFalse(plan.contains(new Placement(p.x(), p.y(), p.z(), Piece.ROCK)));
				}
			}
			assertEquals(0, count(BoulderPlan.boulder(5, 10, 100, -4, radius, 0), Piece.LICHEN));
			assertEquals(plan, BoulderPlan.boulder(5, 10, 100, -4, radius, 100));
		}
	}

	@Test
	void aHutStandsOnFlatGround() {
		TerrainView t = new FlatTerrain((x, z) -> 1_500);
		HutPlan.Plan p = HutPlan.hut(t, 4, -2, 9);
		assertNotNull(p);
		assertEquals(0, p.range());
		List<Placement> plan = p.placements();
		assertEquals(HutPlan.LENGTH, count(plan, Piece.LOG), "the ridge beam");
		assertEquals(4 * HutPlan.LENGTH, count(plan, Piece.STAIRS), "the roof");
		assertEquals(2, count(plan, Piece.HAY));
		// The floor (5 x 6), the back wall (3 + 1) and the two posts of the front wall.
		assertEquals(HutPlan.WIDTH * HutPlan.LENGTH + 4 + 2, count(plan, Piece.PLANKS), "the floor and the walls");
		assertEquals(0, count(plan, Piece.SOURCE) + count(plan, Piece.FALL));
		// Nothing outside the footprint and its block of air above: 5 x 6 floor, 4 above it.
		int x0 = Integer.MAX_VALUE;
		int x1 = Integer.MIN_VALUE;
		for (Placement c : plan) {
			x0 = Math.min(x0, c.x());
			x1 = Math.max(x1, c.x());
			assertTrue(c.y() >= p.floor() && c.y() <= p.floor() + 4, "y " + c.y());
		}
		assertTrue(x1 - x0 <= HutPlan.LENGTH - 1);
		// The beam runs along the ridge and the stairs of the two sides face each other.
		for (Placement c : plan) {
			if (c.piece() == Piece.STAIRS) {
				int below = c.y() - p.floor();
				assertTrue(below == 1 || below == 2);
			}
		}
	}

	@Test
	void aHutNeedsFlatDryGround() {
		TerrainView slope = new FlatTerrain((x, z) -> 1_500 + x * 2);
		assertNull(HutPlan.hut(slope, 0, 0, 9));
		TerrainView bumps = new FlatTerrain((x, z) -> 1_500 + ((x * 7 + z * 13) & 3) * 2);
		assertNull(HutPlan.hut(bumps, 0, 0, 9));
		TerrainView wet = new FlatTerrain((x, z) -> 1_500, c -> false, c -> c[1] == 1_501);
		assertNull(HutPlan.hut(wet, 0, 0, 9));
		// A small step is filled under the floor.
		TerrainView step = new FlatTerrain((x, z) -> x > 3 ? 1_502 : 1_500);
		HutPlan.Plan p = HutPlan.hut(step, 0, 0, 9);
		assertNotNull(p);
		assertTrue(p.range() <= HutPlan.MAX_RANGE);
	}

	@Test
	void aMouthIsATunnelWithRockOverIt() {
		// A massif for x of at most 0 up to Y 100, a foot at Y 90 for x above 0: a face 10 blocks high looking east.
		TerrainView t = new FlatTerrain((x, z) -> x <= 0 ? 100 : 90);
		MouthPlan.Plan p = MouthPlan.mouth(t, -1, 0, 5);
		assertNotNull(p);
		assertEquals(1, p.facing(), "the mouth looks east");
		assertTrue(p.length() >= MouthPlan.MIN_TUNNEL && p.length() <= MouthPlan.MAX_TUNNEL);
		assertTrue(p.cut() >= 30, "blocks cut " + p.cut());
		for (Placement c : p.placements()) {
			assertEquals(Piece.AIR, c.piece());
			assertTrue(t.solid(c.x(), c.y(), c.z()), "only rock is cut");
			// Rock over every cut cell, down to the floor (two blocks of roof over the highest cut cell of its column).
			boolean highest = !p.placements().contains(new Placement(c.x(), c.y() + 1, c.z(), Piece.AIR));
			if (highest) {
				for (int r = 1; r <= MouthPlan.ROOF; r++) {
					assertTrue(t.solid(c.x(), c.y() + r, c.z()), "roof over " + c);
				}
			}
			assertTrue(c.y() >= 91, "the floor of the mouth is level with the ground in front of it");
		}
	}

	@Test
	void aMouthNeedsAFaceAndNoWater() {
		TerrainView low = new FlatTerrain((x, z) -> x <= 0 ? 93 : 90);
		assertNull(MouthPlan.mouth(low, -1, 0, 5), "a face of 3 blocks holds no mouth");
		TerrainView hollow = new FlatTerrain((x, z) -> x <= 0 ? 100 : 90, c -> c[0] <= -3 && c[0] >= -30 && c[1] >= 90 && c[1] <= 96,
				c -> false);
		// A cave all along the tunnel: the cells are air, nothing to cut, but the floor is missing.
		assertNull(MouthPlan.mouth(hollow, -1, 0, 5));
		TerrainView wet = new FlatTerrain((x, z) -> x <= 0 ? 100 : 90, c -> false, c -> c[0] < -5 && c[1] >= 91 && c[1] <= 93);
		MouthPlan.Plan p = MouthPlan.mouth(wet, -1, 0, 5);
		assertNull(p, "water in the way");
	}
}
