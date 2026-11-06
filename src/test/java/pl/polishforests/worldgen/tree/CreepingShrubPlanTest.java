package pl.polishforests.worldgen.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The creeping shrub of the dwarf mountain pine (step F6 of docs/04-pionowy-wycinek.md, "Projekt F6",
 * {@link CreepingShrubPlan}): a pure function of the request; low, wide stems on the ground with raised ends and a blanket
 * of needles; the stems follow the ground and stop at a wall or an unknown column, no leaf stands below the ground, and
 * every leaf is within the leaf reach of the wood.
 */
class CreepingShrubPlanTest {
	private static final long SEED = 0x5EED_F6L;

	private static CreepingShrubPlan.Request request(int k, float size, CreepingShrubPlan.Ground ground) {
		return new CreepingShrubPlan.Request(SEED, 37 * k - 900, 120, 53 * k + 400, size, ground);
	}

	@Test
	void deterministic() {
		for (int k = 0; k < 50; k++) {
			assertEquals(CreepingShrubPlan.plan(request(k, 1, CreepingShrubPlan.Ground.FLAT)),
					CreepingShrubPlan.plan(request(k, 1, CreepingShrubPlan.Ground.FLAT)), "shrub " + k);
		}
	}

	/** On flat ground a shrub is low (at most 4 blocks of wood and leaves) and wide (a few meters across), every leaf alive. */
	@Test
	void lowWideCushionOnFlatGround() {
		double width = 0;
		double leaves = 0;
		int n = 200;
		for (int k = 0; k < n; k++) {
			TreeVoxels v = CreepingShrubPlan.plan(request(k, 1, CreepingShrubPlan.Ground.FLAT));
			assertEquals(0, v.woodX(0));
			assertEquals(0, v.woodY(0));
			assertEquals(0, v.woodZ(0));
			assertEquals(CreepingShrubPlan.BASE_RADIUS, v.radius(0));
			assertTrue(v.woodCount() >= 1 + 2 * 2, "stems of shrub " + k + ": " + v.woodCount());
			int minX = 0;
			int maxX = 0;
			int minZ = 0;
			int maxZ = 0;
			for (int i = 0; i < v.woodCount(); i++) {
				assertTrue(v.woodY(i) >= 0 && v.woodY(i) <= 2, "wood height " + v.woodY(i));
				if (i > 0) {
					assertTrue(v.parentIndex(i) >= 0 && v.parentIndex(i) < i, "parent of wood " + i);
					assertTrue(v.radius(i) <= v.radius(v.parentIndex(i)), "radius grows along the stem");
				}
				minX = Math.min(minX, v.woodX(i));
				maxX = Math.max(maxX, v.woodX(i));
				minZ = Math.min(minZ, v.woodZ(i));
				maxZ = Math.max(maxZ, v.woodZ(i));
			}
			for (int i = 0; i < v.leafCount(); i++) {
				assertTrue(v.leafY(i) >= 0 && v.leafY(i) <= 4, "leaf height " + v.leafY(i));
				assertTrue(v.leafDistance(i) >= 1 && v.leafDistance(i) <= TreeVoxels.MAX_DISTANCE, "leaf distance");
			}
			assertTrue(v.height() <= 5, "height " + v.height());
			width += Math.max(maxX - minX, maxZ - minZ) + 1;
			leaves += v.leafCount();
		}
		width /= n;
		leaves /= n;
		assertTrue(width >= 5 && width <= 13, "mean width " + width);
		assertTrue(leaves >= 30 && leaves <= 150, "mean leaves " + leaves);
	}

	/** The saplings' sizes: a small shrub has fewer and shorter stems than a large one. */
	@Test
	void sizeScalesTheShrub() {
		double small = 0;
		double large = 0;
		for (int k = 0; k < 100; k++) {
			small += CreepingShrubPlan.plan(request(k, 0.5F, CreepingShrubPlan.Ground.FLAT)).woodCount();
			large += CreepingShrubPlan.plan(request(k, 1.2F, CreepingShrubPlan.Ground.FLAT)).woodCount();
		}
		assertTrue(small < 0.7 * large, small + " vs " + large);
	}

	/**
	 * On a slope the stems follow the ground (a step up of one block is climbed, a higher wall stops the stem), never go
	 * below the base, and neither wood nor leaves stand in an unknown column or below the ground.
	 */
	@Test
	void stemsFollowTheGroundAndStopAtWalls() {
		// Terraces rising to the east by one block every two columns, a wall of 5 to the north, the south unknown.
		CreepingShrubPlan.Ground ground = (dx, dz) -> dz > 2 ? CreepingShrubPlan.UNKNOWN : dz < -2 ? 5 : Math.max(0, Math.floorDiv(dx, 2));
		int climbed = 0;
		for (int k = 0; k < 200; k++) {
			TreeVoxels v = CreepingShrubPlan.plan(request(k, 1, ground));
			Set<Long> wood = new HashSet<>();
			for (int i = 0; i < v.woodCount(); i++) {
				int g = ground.free(v.woodX(i), v.woodZ(i));
				assertTrue(g != CreepingShrubPlan.UNKNOWN, "wood in an unknown column");
				assertTrue(v.woodY(i) >= g && v.woodY(i) >= 0, "wood below the ground");
				assertTrue(v.woodZ(i) >= -2, "wood through the wall");
				assertTrue(wood.add(((long) v.woodX(i) << 40) ^ ((long) v.woodY(i) << 20) ^ v.woodZ(i)), "wood twice in a cell");
				if (v.woodY(i) >= 2 && g >= 2) {
					climbed++;
				}
			}
			for (int i = 0; i < v.leafCount(); i++) {
				int g = ground.free(v.leafX(i), v.leafZ(i));
				assertTrue(g != CreepingShrubPlan.UNKNOWN, "leaf in an unknown column");
				assertTrue(v.leafY(i) >= g, "leaf below the ground");
			}
		}
		assertTrue(climbed > 50, "stems climbing the terraces: " + climbed);
	}
}
