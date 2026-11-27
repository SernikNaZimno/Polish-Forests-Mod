package pl.polishforests.worldgen.memo;

import java.util.concurrent.atomic.AtomicReferenceArray;
import org.jspecify.annotations.Nullable;

/**
 * A memo table of immutable entries shared by the generation threads without locks, in sets of
 * {@value MemoLongTable#WAYS} adjacent slots (step OP1 of docs/04-pionowy-wycinek.md; the object form of
 * {@link MemoLongTable}): a key's hash picks a set, a new entry goes to the first slot and the others move one slot on.
 * An entry must be immutable with final fields that hold its key, so a thread that reads an entry another thread has
 * just written sees it whole; an entry lost in a race is only computed again, and since the memoized values are pure
 * functions of their keys, a hit or a miss never changes a result. The caller reads the slots of a set with
 * {@link #slot} and compares the keys itself (no lambda on the hot path).
 *
 * @param <T> the type of the entries
 */
public final class MemoTable<T> {
	/** Slots of a set. */
	public static final int WAYS = MemoLongTable.WAYS;
	private final AtomicReferenceArray<T> slots;
	private final int shift;

	/** A table of 2^{@code bits} slots ({@code bits} ≥ 2). */
	public MemoTable(int bits) {
		if (bits < 2 || bits > 30) {
			throw new IllegalArgumentException("bits " + bits);
		}
		slots = new AtomicReferenceArray<>(1 << bits);
		shift = 64 - bits;
	}

	private int set(long hash) {
		return (int) (hash >>> shift) & -WAYS;
	}

	/** The entry in slot {@code way} (0 … {@value #WAYS} − 1) of the set of {@code hash}, or null. */
	public @Nullable T slot(long hash, int way) {
		return slots.getOpaque(set(hash) + way);
	}

	/**
	 * Puts an entry into the first slot of the set of {@code hash}; the entries before the first empty slot (or before the
	 * old copy of the same entry) move one slot on, the last one drops out when the set is full. Two threads that compute
	 * the same key put two equal objects, which may both stay in the set (harmless: either is the value).
	 */
	public void put(long hash, T entry) {
		int s = set(hash);
		T moved = entry;
		for (int w = 0; w < WAYS; w++) {
			T e = slots.getOpaque(s + w);
			slots.setOpaque(s + w, moved);
			// Step OP2 (DP-210): stop at the old copy of the new entry (the same object put again), as MemoLongTable.
			if (e == null || e == entry) {
				return;
			}
			moved = e;
		}
	}

	/** Empties every slot (a change of what the values depend on, before the table is shared). */
	public void clear() {
		for (int i = 0; i < slots.length(); i++) {
			slots.setOpaque(i, null);
		}
	}

	/** Number of slots. */
	public int size() {
		return slots.length();
	}
}
