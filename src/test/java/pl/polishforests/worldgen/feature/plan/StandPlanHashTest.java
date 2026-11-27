package pl.polishforests.worldgen.feature.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.chunk.PolandScale;

/**
 * Step OP2 of docs/04-pionowy-wycinek.md (the tree stand planner optimized without a change of the result): the plans of
 * the tree stand of the game ({@link StandCostHarness}: territories, voxels and roots of every procedural tree) on the
 * habitats of the landscape model around the sites of the cost test, in both scales, equal the hashes recorded on the
 * base commit of the step (bf71f3c), planned in one thread and again in four threads (the chunks taken in the order of
 * the game from one queue, the memos shared by the threads). The stand map of the world type "Default + Polish
 * Forests" (a seed of its own, DP-208) is covered by the gameplay sites planned with that stand seed (the habitats are
 * the "Poland" ones: the vanilla terrain needs the game).
 */
class StandPlanHashTest {
	/** Side of the square of chunks around each site. */
	private static final int SIDE = 3;
	/** Hashes on bf71f3c: realistic, gameplay, gameplay with the stand seed of "Default + Polish Forests". */
	private static final long[] BEFORE_OP2 = {5221152611034046203L, 2150568708604832314L, -198598704043865790L};

	@Test
	void plansAsBeforeTheStepInOneAndFourThreads() {
		long[] hashes = new long[3];
		int i = 0;
		for (PolandScale scale : PolandScale.values()) {
			StandCostHarness.World w = new StandCostHarness.World(scale);
			for (StandCostHarness.Site s : StandCostHarness.SITES) {
				if (s.scale() == scale) {
					w.prepare(s, SIDE);
				}
			}
			for (StandCostHarness.StandSeed seed : StandCostHarness.StandSeed.values()) {
				if (scale == PolandScale.REALISTIC && seed == StandCostHarness.StandSeed.VANILLA_FORESTS) {
					continue;
				}
				StandCostHarness.Timers t = new StandCostHarness.Timers(false);
				long one = StandCostHarness.run(w, StandCostHarness.SITES, SIDE, seed, 1, t);
				long four = StandCostHarness.run(w, StandCostHarness.SITES, SIDE, seed, 4, new StandCostHarness.Timers(true));
				System.out.printf(Locale.ROOT, "[stand hash] %s %s: %d chunks, %d procedural trees, hash %dL%n", scale.getSerializedName(),
						seed, t.chunks.sum(), t.trees.sum(), one);
				assertEquals(one, four, scale + " " + seed + ": the plans in four threads differ from one thread");
				hashes[i++] = one;
			}
		}
		assertEquals(BEFORE_OP2[0], hashes[0], "realistic plans differ from the base commit of step OP2");
		assertEquals(BEFORE_OP2[1], hashes[1], "gameplay plans differ from the base commit of step OP2");
		assertEquals(BEFORE_OP2[2], hashes[2], "gameplay plans with the stand seed of Default + Polish Forests differ from the base");
	}
}
