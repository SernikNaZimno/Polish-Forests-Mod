package pl.polishforests.worldgen.tree;

import java.util.concurrent.atomic.AtomicLongArray;

/**
 * The split surfaces of the world columns ({@link TreeShapePlan}, before the shift of the ground) shared by the
 * generation threads (step OP1 of docs/04-pionowy-wycinek.md). Before OP1 each thread kept its own memo of them (step
 * O1), so a column was computed again by every thread that planned a tree reaching it: with C2ME the chunks around a
 * tree are decorated on other threads, and the columns of a crown missed the memo of the thread about once per chunk of
 * the column (the split surfaces of a column read its stand's age class, a stand map query). The surfaces are a pure
 * function of the world seed, the stand map and the column, so a memoized value is the computed one.
 *
 * <p>One {@code long} per slot (opaque access, read and written whole): the slot is given by the low
 * {@value #WINDOW_BITS} bits of x and z, so no two columns of a window of 2^{@value #WINDOW_BITS} × 2^{@value #WINDOW_BITS}
 * columns share a slot; the entry holds the rest of x and z (the key), the two surfaces and a valid bit. A column far
 * outside the world border or a surface outside 0 … 8191 is not memoized (computed every time). The memo belongs to a
 * world's stand map ({@link ForestStandMap#splitMemo}), so it goes with its world: no mutable state is shared between
 * worlds, and maps made per request (uniform maps of previews, deadwood plans and tests) get none.
 */
final class SplitMemo {
	/** Bits of x and z that pick the slot (a window of 512 × 512 columns, 2 MB). */
	static final int WINDOW_BITS = 9;
	private static final int WINDOW_MASK = (1 << WINDOW_BITS) - 1;
	/** Bits of x and z (signed) above the window that an entry holds. */
	private static final int TAG_BITS = 18;
	private static final long TAG_MASK = (1L << TAG_BITS) - 1;
	private static final int VALUE_BITS = 13;
	private static final long VALUE_MASK = (1L << VALUE_BITS) - 1;
	private static final int UPPER_SHIFT = 1 + VALUE_BITS;
	private static final int TAG_X_SHIFT = 1 + 2 * VALUE_BITS;
	private static final int TAG_Z_SHIFT = TAG_X_SHIFT + TAG_BITS;
	/** The bits of an entry that hold its column. */
	private static final long KEY_MASK = TAG_MASK << TAG_X_SHIFT | TAG_MASK << TAG_Z_SHIFT;
	/** No memoized value ({@link #get}). */
	static final long MISS = -1;

	private final long worldSeed;
	private final AtomicLongArray slots = new AtomicLongArray(1 << 2 * WINDOW_BITS);

	SplitMemo(long worldSeed) {
		this.worldSeed = worldSeed;
	}

	/** The world seed the memoized surfaces belong to. */
	long worldSeed() {
		return worldSeed;
	}

	private static boolean fits(int c) {
		return c >= -(1 << (TAG_BITS + WINDOW_BITS - 1)) && c < 1 << (TAG_BITS + WINDOW_BITS - 1);
	}

	private static int slot(int x, int z) {
		return (x & WINDOW_MASK) << WINDOW_BITS | z & WINDOW_MASK;
	}

	private static long key(int x, int z) {
		return ((long) (x >> WINDOW_BITS) & TAG_MASK) << TAG_X_SHIFT | ((long) (z >> WINDOW_BITS) & TAG_MASK) << TAG_Z_SHIFT;
	}

	/** The memoized surfaces of column (x, z) packed as {@code upper << 32 | lower}, or {@link #MISS}. */
	long get(int x, int z) {
		if (!fits(x) || !fits(z)) {
			return MISS;
		}
		long e = slots.getOpaque(slot(x, z));
		if ((e & 1) == 0 || (e & KEY_MASK) != key(x, z)) {
			return MISS;
		}
		long lower = e >>> 1 & VALUE_MASK;
		long upper = e >>> UPPER_SHIFT & VALUE_MASK;
		return upper << 32 | lower;
	}

	/** Memoizes the surfaces of column (x, z), unless the column or a value does not fit. */
	void put(int x, int z, int upper, int lower) {
		if (!fits(x) || !fits(z) || upper < 0 || upper > VALUE_MASK || lower < 0 || lower > VALUE_MASK) {
			return;
		}
		slots.setOpaque(slot(x, z), key(x, z) | (long) upper << UPPER_SHIFT | (long) lower << 1 | 1);
	}
}
