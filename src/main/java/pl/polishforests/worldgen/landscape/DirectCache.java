package pl.polishforests.worldgen.landscape;

import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Lock-free cache of immutable grid tiles ({@link CoarseTerrainField}, {@link RegionalField}) that never
 * clears everything at once (docs/03-m2-biomy.md §3.2).
 *
 * <p>The {@link AtomicReferenceArray} has a fixed number of slots. A tile may sit in one of two slots
 * given by the two halves of the hash of its coordinates. A new tile goes into a free one of them, and when both
 * are taken, into the one chosen by a hash bit. Two slots almost eliminate two tiles evicting each other
 * from the same slot (with 400 tiles in 4096 slots and one slot per tile, about 9% were evicted).
 *
 * <p>The value of a tile is a pure function of its coordinates, so the result does not depend on the cache state. Two threads
 * may compute the same tile at once; then one of them is stored and the other returns its own, equal copy.
 * Writes and reads through {@link AtomicReferenceArray} publish the tile safely, and the entry fields are final.
 *
 * @param <T> immutable tile
 */
final class DirectCache<T> {
	/** Computes the tile at coordinates (tx, tz). Must be a pure function of the coordinates. */
	@FunctionalInterface
	interface Builder<T> {
		T build(long tx, long tz);
	}

	private record Entry<T>(long tx, long tz, T value) {
	}

	private final AtomicReferenceArray<Entry<T>> slots;
	private final int mask;
	private final Builder<T> builder;

	/**
	 * @param size    number of slots, a power of two
	 * @param builder function computing a tile
	 */
	DirectCache(int size, Builder<T> builder) {
		if (size <= 0 || Integer.bitCount(size) != 1) {
			throw new IllegalArgumentException("cache size must be a power of two: " + size);
		}
		this.slots = new AtomicReferenceArray<>(size);
		this.mask = size - 1;
		this.builder = builder;
	}

	/** Tile (tx, tz): from the cache, or computed and stored. */
	T get(long tx, long tz) {
		long h = Noise.mix(tx * 0x9E3779B97F4A7C15L + tz);
		int i1 = (int) h & mask;
		Entry<T> e1 = slots.get(i1);
		if (e1 != null && e1.tx == tx && e1.tz == tz) {
			return e1.value;
		}
		int i2 = (int) (h >>> 32) & mask;
		Entry<T> e2 = slots.get(i2);
		if (e2 != null && e2.tx == tx && e2.tz == tz) {
			return e2.value;
		}
		T value = builder.build(tx, tz);
		int slot = e1 == null ? i1 : e2 == null ? i2 : (h & 0x8000_0000L) == 0 ? i1 : i2;
		slots.set(slot, new Entry<>(tx, tz, value));
		return value;
	}

	/** Tile (tx, tz) if it is in the cache, otherwise null (without computing it). */
	T peek(long tx, long tz) {
		long h = Noise.mix(tx * 0x9E3779B97F4A7C15L + tz);
		Entry<T> e1 = slots.get((int) h & mask);
		if (e1 != null && e1.tx == tx && e1.tz == tz) {
			return e1.value;
		}
		Entry<T> e2 = slots.get((int) (h >>> 32) & mask);
		if (e2 != null && e2.tx == tx && e2.tz == tz) {
			return e2.value;
		}
		return null;
	}

	/** Number of slots. */
	int size() {
		return mask + 1;
	}
}
