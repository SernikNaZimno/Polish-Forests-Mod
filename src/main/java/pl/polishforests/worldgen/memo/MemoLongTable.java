package pl.polishforests.worldgen.memo;

import java.util.concurrent.atomic.AtomicLongArray;

/**
 * A memo table of {@code long} entries shared by the generation threads without locks, in sets of {@value #WAYS}
 * adjacent slots (step OP1 of docs/04-pionowy-wycinek.md, set associative): a key's hash picks a set, an entry may sit
 * in any slot of it, and a new entry goes to the first slot while the others move one slot on (the last one drops out).
 * Before step OP1 the memos of the generation were direct-mapped, one slot per hash, and two keys of a working set that
 * shared a slot pushed each other out on every alternate access (the lattice of the wide blend was sampled about six
 * times per point over a loaded area, {@code GenerationMemoTest}); in a set, keys push each other out only when more
 * than {@value #WAYS} of them share it. The slots of a set lie in one cache line.
 *
 * <p>Bit 0 of an entry marks it valid; the caller packs its key and value into the other bits and finds an entry by the
 * key bits under a mask. Each slot holds one whole entry (opaque access, as the direct-mapped tables of steps O1 and Z1),
 * so a reader sees an entry another thread wrote whole or not at all; an entry lost in a race between two writers is
 * only computed again. The memoized values are pure functions of their keys, so a hit or a miss never changes a result.
 */
public final class MemoLongTable {
	/** Slots of a set. */
	public static final int WAYS = 4;
	private final AtomicLongArray slots;
	private final int shift;

	/** A table of 2^{@code bits} slots ({@code bits} ≥ 2). */
	public MemoLongTable(int bits) {
		if (bits < 2 || bits > 30) {
			throw new IllegalArgumentException("bits " + bits);
		}
		slots = new AtomicLongArray(1 << bits);
		shift = 64 - bits;
	}

	private int set(long hash) {
		return (int) (hash >>> shift) & -WAYS;
	}

	/**
	 * The valid entry of the set of {@code hash} whose bits under {@code mask} equal {@code key}, or 0 when no slot holds
	 * one. The mask must not cover bit 0 (the key has bit 0 clear).
	 */
	public long find(long hash, long key, long mask) {
		int s = set(hash);
		for (int w = 0; w < WAYS; w++) {
			long e = slots.getOpaque(s + w);
			if ((e & 1) != 0 && (e & mask) == key) {
				return e;
			}
		}
		return 0;
	}

	/**
	 * Puts a valid entry (bit 0 set) into the first slot of the set of {@code hash}; the entries before the first empty
	 * slot (or before the old copy of the same entry) move one slot on, the last one drops out when the set is full.
	 */
	public void put(long hash, long entry) {
		int s = set(hash);
		long fresh = entry | 1;
		long moved = fresh;
		for (int w = 0; w < WAYS; w++) {
			long e = slots.getOpaque(s + w);
			slots.setOpaque(s + w, moved);
			// Step OP2 (DP-210): stop at the old copy of the new entry (a second writer of the same key), so the set never
			// holds it twice; before, the copy was compared with the entry being moved and the set could end as
			// [new, a, b, new].
			if ((e & 1) == 0 || e == fresh) {
				return;
			}
			moved = e;
		}
	}

	/** Number of slots. */
	public int size() {
		return slots.length();
	}
}
