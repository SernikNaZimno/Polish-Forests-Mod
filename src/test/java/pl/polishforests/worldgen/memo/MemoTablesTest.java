package pl.polishforests.worldgen.memo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.landscape.Noise;

/**
 * The set-associative memo tables of step OP1 ({@link MemoLongTable}, {@link MemoTable}): a found entry is always the
 * one put for its key (also with four threads writing and reading at once), a set keeps its last {@value MemoLongTable#WAYS}
 * keys, and keys of different sets do not push each other out.
 */
class MemoTablesTest {
	/** An entry of the long table: the key in bits 12-63, a value of the key in bits 1-11, the valid bit. */
	private static long entry(long key) {
		return key << 12 | (Noise.mix(key) & 0x7FF) << 1 | 1;
	}

	private static final long KEY_MASK = ~0xFFFL;

	@Test
	void aSetKeepsItsLastFourKeysAndForgetsTheOldest() {
		MemoLongTable t = new MemoLongTable(4);
		// Every key in the same set: the same hash, keys 1 … 6.
		for (long k = 1; k <= 6; k++) {
			t.put(0, entry(k));
		}
		for (long k = 1; k <= 6; k++) {
			long e = t.find(0, k << 12, KEY_MASK);
			if (k <= 2) {
				assertEquals(0, e, "key " + k + " dropped out of the full set");
			} else {
				assertEquals(entry(k), e, "key " + k + " kept");
			}
		}
		// Putting a key again that is still there does not drop another one.
		t.put(0, entry(6));
		assertEquals(entry(3), t.find(0, 3L << 12, KEY_MASK));
		// Another set is untouched.
		assertEquals(0, t.find(Long.MIN_VALUE, 3L << 12, KEY_MASK));
	}

	/**
	 * Step OP2 (DP-210): a key put again while it is not first in its set (a second writer of the same key) moves to the
	 * front without a second copy, so the set still holds four different keys: [c, b, a] + b → [b, c, a], then + d →
	 * [d, b, c, a] (before the fix [b, c, b, a] and [d, b, c, b], key a dropped out).
	 */
	@Test
	void aKeyPutAgainIsNotKeptTwice() {
		MemoLongTable t = new MemoLongTable(4);
		t.put(0, entry(1));
		t.put(0, entry(2));
		t.put(0, entry(3));
		t.put(0, entry(2));
		t.put(0, entry(4));
		for (long k = 1; k <= 4; k++) {
			assertEquals(entry(k), t.find(0, k << 12, KEY_MASK), "key " + k + " kept");
		}
		MemoTable<long[]> o = new MemoTable<>(4);
		long[] a = {1};
		long[] b = {2};
		long[] c = {3};
		long[] d = {4};
		for (long[] e : List.of(a, b, c, b, d)) {
			o.put(0, e);
		}
		List<long[]> expected = List.of(d, b, c, a);
		for (int w = 0; w < MemoTable.WAYS; w++) {
			assertSame(expected.get(w), o.slot(0, w), "slot " + w);
		}
	}

	@Test
	void objectTableKeepsItsLastFourEntriesPerSet() {
		MemoTable<long[]> t = new MemoTable<>(4);
		List<long[]> put = new ArrayList<>();
		for (int k = 0; k < 6; k++) {
			long[] e = {k};
			put.add(e);
			t.put(0, e);
		}
		for (int w = 0; w < MemoTable.WAYS; w++) {
			assertSame(put.get(5 - w), t.slot(0, w), "slot " + w + " holds the entries newest first");
		}
		t.clear();
		for (int w = 0; w < MemoTable.WAYS; w++) {
			assertNull(t.slot(0, w));
		}
	}

	@Test
	void foundEntriesAreTheOnesPutForTheirKeysInFourThreads() throws Exception {
		MemoLongTable longs = new MemoLongTable(12);
		MemoTable<long[]> objects = new MemoTable<>(12);
		AtomicLong hits = new AtomicLong();
		ExecutorService pool = Executors.newFixedThreadPool(4);
		try {
			List<Future<?>> done = new ArrayList<>();
			for (int t = 0; t < 4; t++) {
				int seed = t;
				done.add(pool.submit(() -> {
					Random r = new Random(seed);
					for (int i = 0; i < 400_000; i++) {
						// Keys from a working set of about three times the table, so sets overflow all the time.
						long key = r.nextInt(12_000);
						long hash = Noise.mix(key * 0x9E37_79B9_7F4A_7C15L);
						long e = longs.find(hash, key << 12, KEY_MASK);
						if (e != 0) {
							assertEquals(entry(key), e, "long entry of key " + key);
							hits.incrementAndGet();
						} else {
							longs.put(hash, entry(key));
						}
						long[] o = null;
						for (int w = 0; w < MemoTable.WAYS && o == null; w++) {
							long[] s = objects.slot(hash, w);
							if (s != null && s[0] == key) {
								o = s;
							}
						}
						if (o != null) {
							assertEquals(Noise.mix(key), o[1], "object entry of key " + key);
						} else {
							objects.put(hash, new long[] {key, Noise.mix(key)});
						}
					}
				}));
			}
			for (Future<?> f : done) {
				f.get();
			}
		} finally {
			pool.shutdown();
		}
		assertTrue(hits.get() > 0, "some keys were found again");
	}

	@Test
	void keysOfAWorkingSetOfAQuarterOfTheTableAreKeptWhereADirectMappedTableLosesSome() {
		MemoLongTable t = new MemoLongTable(16);
		int n = 1 << 14;
		int directHits = 0;
		long[] direct = new long[1 << 16];
		for (long k = 0; k < n; k++) {
			long hash = Noise.mix(k + 1_000_003L);
			t.put(hash, entry(k));
			direct[(int) (hash >>> 48)] = entry(k);
		}
		int kept = 0;
		for (long k = 0; k < n; k++) {
			long hash = Noise.mix(k + 1_000_003L);
			kept += t.find(hash, k << 12, KEY_MASK) != 0 ? 1 : 0;
			directHits += direct[(int) (hash >>> 48)] == entry(k) ? 1 : 0;
		}
		System.out.printf(java.util.Locale.ROOT, "[memo] %d keys in 2^16 slots: kept %d in sets of four, %d direct-mapped%n", n, kept, directHits);
		assertTrue(kept >= 0.995 * n, "sets of four keep nearly all keys: " + kept);
		assertTrue(directHits < 0.9 * n, "a direct-mapped table loses about a tenth: " + directHits);
	}
}
