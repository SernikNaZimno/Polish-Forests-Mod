package pl.polishforests.gametest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** The kinds of the window differences of the determinism test (step L1): the floor away from objects is told apart. */
class WindowDiffTest {
	private static final int SIZE = 1;
	private static final int WINDOW = 8;
	private static final int BELOW = 3;
	/** State numbers: 0 air, 1 stone, 2 litter facing north, 3 litter facing east, 4 log, 5 ore. */
	private static final byte[] CLASSES = {WindowDiff.AIR, WindowDiff.OTHER, WindowDiff.FLOOR, WindowDiff.FLOOR, WindowDiff.OBJECT,
			WindowDiff.OTHER};

	private static int column(int x, int z) {
		return x * 16 + z;
	}

	/** Two equal windows of flat ground (stone below the ground at offset BELOW, air above) and the ground heights. */
	private static int[][] flat() {
		int[] a = new int[256 * WINDOW];
		for (int c = 0; c < 256; c++) {
			for (int k = 0; k < BELOW; k++) {
				a[c * WINDOW + k] = 1;
			}
		}
		int[] ground = new int[256];
		java.util.Arrays.fill(ground, 70);
		return new int[][] {a, a.clone(), ground};
	}

	private static WindowDiff.Result diff(int[][] w) {
		return WindowDiff.of(w[0], w[1], w[2], CLASSES, SIZE, WINDOW, BELOW, 100);
	}

	@Test
	void floorAwayFromObjectsIsFree() {
		int[][] w = flat();
		int i = column(8, 8) * WINDOW + BELOW;
		w[0][i] = 2;
		w[1][i] = 3;
		WindowDiff.Result r = diff(w);
		assertEquals(1, r.count(WindowDiff.Kind.FLOOR_FREE));
		assertEquals(1, r.blocks().size());
		assertEquals(70, r.blocks().get(0).y());
	}

	@Test
	void floorNextToAnObjectInEitherWorldIsContact() {
		int[][] w = flat();
		int i = column(8, 8) * WINDOW + BELOW;
		w[0][i] = 2;
		// A log two blocks away, one block higher, only in the second world.
		w[1][column(10, 8) * WINDOW + BELOW + 1] = 4;
		WindowDiff.Result r = diff(w);
		assertEquals(0, r.count(WindowDiff.Kind.FLOOR_FREE));
		assertEquals(1, r.count(WindowDiff.Kind.FLOOR_CONTACT));
		assertEquals(1, r.count(WindowDiff.Kind.OBJECT));
	}

	@Test
	void objectThreeBlocksAwayDoesNotExplain() {
		int[][] w = flat();
		w[0][column(8, 8) * WINDOW + BELOW] = 2;
		w[0][column(11, 8) * WINDOW + BELOW] = 4;
		w[1][column(11, 8) * WINDOW + BELOW] = 4;
		assertEquals(1, diff(w).count(WindowDiff.Kind.FLOOR_FREE));
	}

	@Test
	void contactFollowsTheGroundOfTheNeighborColumn() {
		int[][] w = flat();
		w[0][column(8, 8) * WINDOW + BELOW] = 2;
		// The neighbor column's ground is 3 blocks lower: its log at its offset BELOW + 1 lies at y 68, 2 below y 70.
		w[2][column(9, 8)] = 67;
		w[0][column(9, 8) * WINDOW + BELOW + 1] = 4;
		w[1][column(9, 8) * WINDOW + BELOW + 1] = 4;
		assertEquals(1, diff(w).count(WindowDiff.Kind.FLOOR_CONTACT));
		w[2][column(9, 8)] = 66;
		assertEquals(1, diff(w).count(WindowDiff.Kind.FLOOR_FREE));
	}

	@Test
	void groundAndUndergroundDifferencesAreKindsOfTheirOwnAndExplainTheFloorAbove() {
		int[][] w = flat();
		w[1][column(8, 8) * WINDOW] = 5;
		w[1][column(5, 5) * WINDOW + BELOW - 1] = 0;
		w[0][column(5, 6) * WINDOW + BELOW] = 3;
		WindowDiff.Result r = diff(w);
		assertEquals(1, r.count(WindowDiff.Kind.UNDERGROUND));
		assertEquals(1, r.count(WindowDiff.Kind.GROUND));
		assertEquals(1, r.count(WindowDiff.Kind.FLOOR_CONTACT));
		assertEquals(0, r.count(WindowDiff.Kind.FLOOR_FREE));
		assertEquals(2, r.blocks().size(), "the underground difference is not listed");
	}

	@Test
	void floorAtTheEdgeOfTheAreaIsContact() {
		int[][] w = flat();
		w[0][column(1, 8) * WINDOW + BELOW] = 2;
		w[0][column(8, 14) * WINDOW + BELOW] = 2;
		assertEquals(2, diff(w).count(WindowDiff.Kind.FLOOR_CONTACT));
	}

	@Test
	void columnCoordinatesOfAnAreaOfChunks() {
		// Chunk (1, 0) of a 2 × 2 area is the third chunk: column index 2 · 256 + x · 16 + z.
		assertEquals(16 + 3, WindowDiff.gx(2 * 256 + 3 * 16 + 5, 2));
		assertEquals(5, WindowDiff.gz(2 * 256 + 3 * 16 + 5, 2));
		assertEquals(16 + 5, WindowDiff.gz(256 + 3 * 16 + 5, 2));
	}
}
