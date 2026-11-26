package pl.polishforests.worldgen.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

/**
 * The split surfaces shared by the threads (step OP1, {@link SplitMemo}): a value read back is the one put for its column
 * (also for negative and far columns), the columns of a window of 512 × 512 never share a slot, a column 512 blocks away
 * replaces the entry, values that do not fit and columns far outside the world are not memoized, and each world stand map
 * has its own memo for its seed, dropped with a change of the park. That the planner gives the same trees with the memo
 * is {@code GenerationMemoTest}.
 */
class SplitMemoTest {
	private static ForestStandMap map() {
		return new ForestStandMap(1L, 1, 0.85);
	}

	@Test
	void valuesComeBackForTheirColumnsOnly() {
		SplitMemo m = new SplitMemo(1L);
		int[][] columns = {{0, 0}, {-1, -1}, {511, -512}, {-29_999_999, 29_999_999}, {12_345, -6_789}};
		for (int[] c : columns) {
			assertEquals(SplitMemo.MISS, m.get(c[0], c[1]));
			m.put(c[0], c[1], 40, 17);
			assertEquals(40L << 32 | 17, m.get(c[0], c[1]), "column " + c[0] + ", " + c[1]);
		}
		// The column 512 blocks away shares the slot: it replaces the entry, which is then missed.
		m.put(12_345 + 512, -6_789, 30, 9);
		assertEquals(SplitMemo.MISS, m.get(12_345, -6_789));
		assertEquals(30L << 32 | 9, m.get(12_345 + 512, -6_789));
		// Not memoized: values outside 0 … 8191, columns beyond 2^26.
		m.put(5, 5, 8192, 3);
		m.put(6, 6, 9, -1);
		m.put(1 << 26, 0, 9, 3);
		assertEquals(SplitMemo.MISS, m.get(5, 5));
		assertEquals(SplitMemo.MISS, m.get(6, 6));
		assertEquals(SplitMemo.MISS, m.get(1 << 26, 0));
	}

	@Test
	void aWindowOfColumnsNeverSharesASlot() {
		SplitMemo m = new SplitMemo(2L);
		int x0 = -700;
		int z0 = 300;
		for (int x = x0; x < x0 + 512; x++) {
			for (int z = z0; z < z0 + 512; z++) {
				m.put(x, z, (x & 1023) + 3, (z & 1023) + 5);
			}
		}
		for (int x = x0; x < x0 + 512; x++) {
			for (int z = z0; z < z0 + 512; z++) {
				assertEquals((long) ((x & 1023) + 3) << 32 | (z & 1023) + 5, m.get(x, z), "column " + x + ", " + z);
			}
		}
	}

	@Test
	void eachStandMapHasItsOwnMemoForItsSeed() {
		ForestStandMap a = map();
		ForestStandMap b = map();
		assertSame(a.splitMemo(3L), a.splitMemo(3L));
		assertNotSame(a.splitMemo(3L), b.splitMemo(3L));
		SplitMemo first = a.splitMemo(3L);
		first.put(1, 1, 20, 10);
		SplitMemo other = a.splitMemo(4L);
		assertNotSame(first, other);
		assertEquals(4L, other.worldSeed());
		assertEquals(SplitMemo.MISS, other.get(1, 1), "a new seed starts with an empty memo");
		other.put(1, 1, 20, 10);
		assertSame(a, a.withPark(null));
		assertEquals(SplitMemo.MISS, a.splitMemo(4L).get(1, 1), "a change of the park starts with an empty memo");
	}
}
