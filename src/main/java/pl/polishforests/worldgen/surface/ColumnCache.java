package pl.polishforests.worldgen.surface;

import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.atomic.LongAdder;
import pl.polishforests.worldgen.landscape.ColumnSample;
import pl.polishforests.worldgen.landscape.Noise;

/**
 * Lock-free cache of the column summaries that the surface plan of a chunk needs from its neighbors (the bank shelf
 * looks up to {@link BankShelf#RAMP} blocks beyond the chunk, review of S6, round 1), shared by all chunks of a world
 * seed. Every plan publishes the summaries of the columns within {@code RAMP} blocks of its border, so a later neighbor
 * reads them instead of sampling the model; a column that a plan has to sample outside its chunk is kept with its full
 * sample, which the generator takes when it fills that column's own chunk ({@link #takeSample}). Each column is then
 * sampled about once, whichever chunk comes first.
 *
 * <p>The values are pure functions of the column (the world seed is fixed per builder), so the plans do not depend on the
 * cache state or on the order of the chunks. As in {@code DirectCache} an entry sits in one of two slots given by its
 * hash; writes and reads through {@link AtomicReferenceArray} publish the immutable entries safely, and two threads
 * computing the same column store equal values.
 */
final class ColumnCache {
	/** Columns sampled outside their chunk for a plan (the model samples the shelf added). */
	static final LongAdder MISSES = new LongAdder();
	/** Summaries of columns outside the chunk read from the cache. */
	static final LongAdder HITS = new LongAdder();
	/** Samples taken back by the generator for the column's own chunk. */
	static final LongAdder REUSED = new LongAdder();

	private record Entry(long key, int summary, ColumnSample sample) {
	}

	private final AtomicReferenceArray<Entry> slots;
	private final int mask;

	/** @param size number of slots, a power of two */
	ColumnCache(int size) {
		if (size <= 0 || Integer.bitCount(size) != 1) {
			throw new IllegalArgumentException("cache size must be a power of two: " + size);
		}
		slots = new AtomicReferenceArray<>(size);
		mask = size - 1;
	}

	static long key(int x, int z) {
		return (long) x << 32 | (z & 0xFFFFFFFFL);
	}

	private Entry find(long key) {
		long h = Noise.mix(key * 0x9E3779B97F4A7C15L);
		Entry e1 = slots.get((int) h & mask);
		if (e1 != null && e1.key == key) {
			return e1;
		}
		Entry e2 = slots.get((int) (h >>> 32) & mask);
		return e2 != null && e2.key == key ? e2 : null;
	}

	private void put(Entry e) {
		long h = Noise.mix(e.key * 0x9E3779B97F4A7C15L);
		int i1 = (int) h & mask;
		int i2 = (int) (h >>> 32) & mask;
		Entry e1 = slots.get(i1);
		Entry e2 = slots.get(i2);
		int slot = e1 == null || e1.key == e.key ? i1 : e2 == null || e2.key == e.key ? i2
				: (h & 0x8000_0000L) == 0 ? i1 : i2;
		slots.set(slot, e);
	}

	/**
	 * Summary of the column (x, z) ({@link SurfaceBuilder#summary}): from the cache, or sampled with {@code sampler},
	 * stored with its sample and returned.
	 */
	int summary(int x, int z, SurfaceBuilder builder, SurfaceBuilder.Sampler sampler, int minY, int maxY) {
		long key = key(x, z);
		Entry e = find(key);
		if (e != null) {
			HITS.increment();
			return e.summary;
		}
		MISSES.increment();
		ColumnSample s = sampler.sample(x, z);
		int summary = builder.summary(s, minY, maxY);
		put(new Entry(key, summary, s));
		return summary;
	}

	/** Publishes the summary of a column of a chunk being built (without its sample). */
	void publish(int x, int z, int summary) {
		long key = key(x, z);
		Entry e = find(key);
		if (e == null) {
			put(new Entry(key, summary, null));
		}
	}

	/**
	 * Sample of the column (x, z) kept from a neighbor's plan, or null; the entry keeps only the summary afterwards (its
	 * chunk publishes it anyway).
	 */
	ColumnSample takeSample(int x, int z) {
		long key = key(x, z);
		Entry e = find(key);
		if (e == null || e.sample == null) {
			return null;
		}
		put(new Entry(key, e.summary, null));
		REUSED.increment();
		return e.sample;
	}
}
