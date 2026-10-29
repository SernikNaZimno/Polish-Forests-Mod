package pl.polishforests.worldgen.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/**
 * The caches of the stand map (step O1: stands of columns; step O2: the sites of the grid cells around a column) give the
 * stands computed without them, in any order and on any thread, and the stands of an area are those of the code before
 * O2 (a hash of 2 × 40 000 columns recorded on commit 7c64a1d).
 */
class StandMapCacheTest {
	private static final long SEED = 20260927L;
	/** Hash of the stands of {@link #hash} before step O2 (commit 7c64a1d). */
	private static final long BEFORE_O2 = -6004385951774376970L;

	private static long hash(ForestStandMap map, int x0, int z0) {
		long h = 1125899906842597L;
		for (int x = x0; x < x0 + 200; x++) {
			for (int z = z0; z < z0 + 200; z++) {
				ForestStandMap.Stand s = map.stand(x, z);
				h = 31 * h + s.siteX();
				h = 31 * h + s.siteZ();
				h = 31 * h + s.age().ordinal();
				h = 31 * h + (s.natural() ? 1 : 0);
			}
		}
		return h;
	}

	@Test
	void standsAsBeforeO2() {
		long h = hash(new ForestStandMap(SEED, 1, 0.85), -3_000, 1_700) * 31 + hash(new ForestStandMap(SEED, 0.25, 0.85), 500, -900);
		System.out.println("[stand map] hash of the stands " + h);
		assertEquals(BEFORE_O2, h, "stands of the area differ from the code before O2");
	}

	@Test
	void cachedEqualsUncachedInAnyOrderAndThread() throws Exception {
		ForestStandMap map = new ForestStandMap(SEED, 1, 0.85);
		List<int[]> columns = new ArrayList<>();
		Random r = new Random(7);
		for (int i = 0; i < 20_000; i++) {
			columns.add(new int[] {r.nextInt(4_000) - 2_000, r.nextInt(4_000) - 2_000});
		}
		List<ForestStandMap.Stand> expected = new ArrayList<>();
		ForestStandMap fresh = new ForestStandMap(SEED, 1, 0.85);
		for (int[] c : columns) {
			expected.add(fresh.standUncached(c[0], c[1]));
		}
		List<Integer> order = new ArrayList<>();
		for (int i = 0; i < columns.size(); i++) {
			order.add(i);
		}
		Collections.shuffle(order, new Random(11));
		ExecutorService pool = Executors.newFixedThreadPool(4);
		try {
			List<Future<?>> done = new ArrayList<>();
			for (int t = 0; t < 4; t++) {
				int from = t;
				done.add(pool.submit(() -> {
					for (int k = from; k < order.size(); k += 4) {
						int i = order.get(k);
						assertEquals(expected.get(i), map.stand(columns.get(i)[0], columns.get(i)[1]), "column " + i);
						// The neighbors in a row: the sites of the cell are reused.
						assertEquals(fresh.standUncached(columns.get(i)[0] + 1, columns.get(i)[1]),
								map.stand(columns.get(i)[0] + 1, columns.get(i)[1]), "column " + i + " + 1");
					}
				}));
			}
			for (Future<?> f : done) {
				f.get();
			}
		} finally {
			pool.shutdown();
		}
	}
}
