package pl.polishforests.worldgen.feature.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/**
 * Step O2: the memo of the candidates' rank values ({@code TreeStandPlan.KeptTable}) gives the values of the code
 * without it, in any order and on any thread, also for cells that share a slot of the memo.
 */
class RankMemoTest {
	private static final long SEED = 20260927L;

	@Test
	void memoizedRankEqualsComputed() throws Exception {
		TreeStandPlan.Candidates candidates = new TreeStandPlan.Candidates(SEED);
		List<int[]> kept = new ArrayList<>();
		candidates.forEach(-600, -600, 600, 600, (x, z) -> kept.add(new int[] {x, z}));
		List<Double> expected = new ArrayList<>();
		for (int[] c : kept) {
			expected.add(candidates.rankUncached(c[0], c[1]));
		}
		ExecutorService pool = Executors.newFixedThreadPool(4);
		try {
			List<Future<?>> done = new ArrayList<>();
			for (int t = 0; t < 4; t++) {
				int from = t;
				done.add(pool.submit(() -> {
					for (int pass = 0; pass < 2; pass++) {
						for (int i = from; i < kept.size(); i += 4) {
							int k = pass == 0 ? i : kept.size() - 1 - i;
							assertEquals(expected.get(k), candidates.rank(kept.get(k)[0], kept.get(k)[1]), "candidate " + k);
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
		assertEquals(true, kept.size() > 50_000, "kept candidates: " + kept.size());
	}
}
