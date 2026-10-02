package pl.polishforests.worldgen.landscape;

import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Pamięć podręczna niezmiennych kafli siatek ({@link CoarseTerrainField}, {@link RegionalField}) bez blokad
 * i bez czyszczenia całości (docs/03-m2-biomy.md §3.2).
 *
 * <p>Tablica {@link AtomicReferenceArray} ma stałą liczbę miejsc. Kafel może leżeć w jednym z dwóch miejsc
 * wyznaczonych dwiema połówkami skrótu jego współrzędnych. Nowy kafel trafia w wolne z nich, a gdy oba
 * są zajęte, w to, które wskazuje bit skrótu. Dwa miejsca prawie usuwają przepychanie się dwóch kafli
 * o tym samym miejscu (przy 400 kaflach w 4096 miejscach z jednym miejscem na kafel przepychało się ok. 9%).
 *
 * <p>Wartość kafla jest czystą funkcją jego współrzędnych, więc wynik nie zależy od stanu pamięci. Dwa wątki
 * mogą policzyć ten sam kafel naraz; zapisuje się wtedy jeden z nich, a drugi zwraca własną, równą kopię.
 * Zapis i odczyt przez {@link AtomicReferenceArray} publikują kafel bezpiecznie, a pola wpisu są finalne.
 *
 * @param <T> niezmienny kafel
 */
final class DirectCache<T> {
	/** Liczy kafel o współrzędnych (tx, tz). Musi być czystą funkcją współrzędnych. */
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
	 * @param size    liczba miejsc, potęga dwójki
	 * @param builder funkcja licząca kafel
	 */
	DirectCache(int size, Builder<T> builder) {
		if (size <= 0 || Integer.bitCount(size) != 1) {
			throw new IllegalArgumentException("rozmiar pamięci musi być potęgą dwójki: " + size);
		}
		this.slots = new AtomicReferenceArray<>(size);
		this.mask = size - 1;
		this.builder = builder;
	}

	/** Kafel (tx, tz): z pamięci albo policzony i zapisany. */
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

	/** Kafel (tx, tz), jeśli jest w pamięci, inaczej null (bez liczenia). */
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

	/** Liczba miejsc. */
	int size() {
		return mask + 1;
	}
}
