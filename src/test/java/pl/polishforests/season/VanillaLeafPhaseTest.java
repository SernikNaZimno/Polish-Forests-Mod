package pl.polishforests.season;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The phase of the vanilla leaves in Polska ({@link VanillaLeafPhase}, review round 1 of V13): the four phases in about
 * equal shares, one phase over almost every crown of a placeholder tree or shrub, and edges between phases that do not
 * follow the block grid (the earlier 4 x 4 cells split crowns along straight lines).
 */
class VanillaLeafPhaseTest {
	@Test
	void phasesHaveEqualShares() {
		int[] n = new int[4];
		for (int x = 0; x < 400; x++) {
			for (int z = 0; z < 400; z++) {
				n[VanillaLeafPhase.phase(x * 5 + 3000, z * 5 - 7000)]++;
			}
		}
		for (int c : n) {
			assertEquals(0.25, c / 160000.0, 0.04, java.util.Arrays.toString(n));
		}
	}

	/**
	 * A crown of radius 3 (7 blocks wide) lies across the edge between the bare and the leafy phases of a color step (one
	 * of the three edges between neighbouring phases) at about one place in ten, and has one phase at most places.
	 */
	@Test
	void crownsKeepOnePhase() {
		int[] across = new int[3];
		int split = 0;
		int crowns = 0;
		for (int cx = 0; cx < 4000; cx += 17) {
			for (int cz = 0; cz < 4000; cz += 19) {
				crowns++;
				int lo = 3;
				int hi = 0;
				for (int dx = -3; dx <= 3; dx++) {
					for (int dz = -3; dz <= 3; dz++) {
						if (dx * dx + dz * dz <= 9) {
							int p = VanillaLeafPhase.phase(cx + dx, cz + dz);
							lo = Math.min(lo, p);
							hi = Math.max(hi, p);
						}
					}
				}
				split += lo != hi ? 1 : 0;
				for (int k = 1; k <= 3; k++) {
					across[k - 1] += lo < k && k <= hi ? 1 : 0;
				}
			}
		}
		for (int a : across) {
			assertTrue(a < 0.15 * crowns, java.util.Arrays.toString(across) + " of " + crowns + " crowns across an edge");
		}
		assertTrue(split < 0.4 * crowns, split + " of " + crowns + " crowns split");
	}

	/**
	 * The edges between phases are not straight lines along the grid: along a row the changes of phase fall at every
	 * position modulo 4 and modulo the cell, and a change in one row rarely sits in the same column in the next 4 rows.
	 */
	@Test
	void edgesAreIrregular() {
		int[] mod4 = new int[4];
		int[] modCell = new int[VanillaLeafPhase.CELL];
		int straight = 0;
		int changes = 0;
		for (int z = 0; z < 3000; z += 3) {
			for (int x = 0; x < 3000; x++) {
				if (VanillaLeafPhase.phase(x, z) != VanillaLeafPhase.phase(x + 1, z)) {
					changes++;
					mod4[Math.floorMod(x, 4)]++;
					modCell[Math.floorMod(x, VanillaLeafPhase.CELL)]++;
					boolean same = true;
					for (int dz = 1; dz <= 4 && same; dz++) {
						same = VanillaLeafPhase.phase(x, z + dz) != VanillaLeafPhase.phase(x + 1, z + dz);
					}
					straight += same ? 1 : 0;
				}
			}
		}
		for (int c : mod4) {
			assertEquals(0.25, (double) c / changes, 0.05, java.util.Arrays.toString(mod4));
		}
		for (int c : modCell) {
			assertTrue(c < 3.0 * changes / VanillaLeafPhase.CELL, java.util.Arrays.toString(modCell));
		}
		assertTrue(straight < 0.5 * changes, straight + " of " + changes + " edges straight over 5 rows");
	}
}
