package pl.polishforests.worldgen.vanilla;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Step C2, stage 2: the columns of the neighbor chunks that {@link BorderFreeze} may test without the vanilla biome zoom
 * leaving the 3 × 3 chunks of the generation region.
 */
class BorderFreezeTest {
	/** The chunk of the quart the zoom may pick for block x (the quart of x − 2 or the next one). */
	private static boolean zoomReadsOnly3x3(int cx, int cz, int x, int z) {
		for (int qx : new int[] {(x - 2) >> 2, ((x - 2) >> 2) + 1}) {
			for (int qz : new int[] {(z - 2) >> 2, ((z - 2) >> 2) + 1}) {
				if (Math.abs((qx >> 2) - cx) > 1 || Math.abs((qz >> 2) - cz) > 1) {
					return false;
				}
			}
		}
		return true;
	}

	@Test
	void zoomStaysInTheRegionAndCoversTheReachOfTheCrowns() {
		for (int[] c : new int[][] {{0, 0}, {-1, -1}, {147, -164}, {-216, 8}, {1000, -1000}}) {
			int cx = c[0];
			int cz = c[1];
			int inside = 0;
			for (int x = (cx - 1) * 16; x < (cx + 2) * 16; x++) {
				for (int z = (cz - 1) * 16; z < (cz + 2) * 16; z++) {
					boolean got = BorderFreeze.zoomInside(cx, cz, x, z);
					assertEquals(zoomReadsOnly3x3(cx, cz, x, z), got, "column (" + x + ", " + z + ") of chunk (" + cx + ", " + cz + ")");
					// Distance of the column from the decorated chunk, blocks (0 inside it).
					int dx = Math.max(Math.max(cx * 16 - x, x - (cx * 16 + 15)), 0);
					int dz = Math.max(Math.max(cz * 16 - z, z - (cz * 16 + 15)), 0);
					if (Math.max(dx, dz) <= 13) {
						assertTrue(got, "column (" + x + ", " + z + ") within 13 blocks of chunk (" + cx + ", " + cz + ")");
					}
					inside += got ? 1 : 0;
				}
			}
			// Of the 48 × 48 columns only the outer 2 on each side (15 and 16 blocks from the chunk) fall out.
			assertEquals(44 * 44, inside);
		}
	}
}
