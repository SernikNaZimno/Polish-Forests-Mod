package pl.polishforests.worldgen.tree;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.habitat.Species;

/**
 * Cost of the tree planner (step V6 of docs/04-pionowy-wycinek.md, "Ukończony, gdy": the median ≤ 0.2 ms per conifer and
 * ≤ 0.5 ms per broad-leaved tree, one thread). Per species: canopy trees of the mature stand and of the old growth (the
 * largest), {@value #TREES} lone trees at other columns per round, and the trees of a natural old stand of 4 × 4
 * chunks with their crown territories and the trunk columns (all layers, as the stand of step V9 will plan them; the
 * territories themselves are computed before the timing). The median of the rounds is reported and checked; the planner
 * alone, without writing blocks.
 *
 * <p>Tagged {@code cost}: only {@code ./gradlew costTest} (one JVM, alone, on a quiet machine).
 */
@Tag("cost")
class TreeShapePlanCostTest {
	private static final long SEED = 20260927L;
	private static final int ROUNDS = Math.max(3, Integer.getInteger("polishforests.cost.runs", 7));
	private static final int TREES = 400;
	/** Budgets of the plan (ms per tree, median). */
	private static final double CONIFER_MS = 0.2;
	private static final double BROADLEAF_MS = 0.5;
	private static final Set<Species> CONIFERS = Set.of(Species.SCOTS_PINE, Species.SPRUCE);

	private static volatile long sink;

	@Test
	void plannerCost() {
		StringBuilder report = new StringBuilder("[tree cost] median ms per tree (" + ROUNDS + " rounds of " + TREES + " trees)\n");
		List<String> over = new ArrayList<>();
		for (Species s : SpeciesShape.species()) {
			for (AgeClass age : List.of(AgeClass.MATURE, AgeClass.OLD_GROWTH)) {
				List<TreeRequest> requests = new ArrayList<>();
				for (int k = 0; k < TREES; k++) {
					requests.add(new TreeRequest(s, CrownLayer.CANOPY, age, SEED, 97 * k - 20_000, 64, 61 * k + 5_000));
				}
				double ms = median(requests);
				double budget = CONIFERS.contains(s) ? CONIFER_MS : BROADLEAF_MS;
				report.append(String.format(Locale.ROOT, "  %-13s %-10s %.4f ms (budget %.1f)%n", s.id(),
						age.name().toLowerCase(Locale.ROOT), ms, budget));
				if (ms > budget) {
					over.add(s.id() + " " + age);
				}
			}
		}
		for (boolean grad : new boolean[] {false, true}) {
			List<TreeRequest> requests = new ArrayList<>();
			CrownTerritories territories = new CrownTerritories(SEED, StandMap.uniform(AgeClass.OLD_GROWTH, true));
			CrownTerritories.Session session = territories.session();
			for (int cx = 0; cx < 4; cx++) {
				for (int cz = 0; cz < 4; cz++) {
					for (CrownTerritory t : session.chunk(cx + 40, cz - 30, null)) {
						Species sp = TreePreview.standSpecies(grad, t.layer(), t.x(), t.z());
						requests.add(new TreeRequest(sp, t.layer(), AgeClass.OLD_GROWTH, SEED, t.x(), 64, t.z(), t, session::candidate));
					}
				}
			}
			double ms = median(requests);
			report.append(String.format(Locale.ROOT, "  stand %-7s natural old growth, %d trees with territories: %.4f ms per tree%n",
					grad ? "grad" : "pine", requests.size(), ms));
		}
		System.out.print(report);
		assertTrue(over.isEmpty(), "over the budget: " + over);
	}

	/** Median over the rounds of the mean time per tree (ms), after a warm-up. */
	private static double median(List<TreeRequest> requests) {
		for (int w = 0; w < 3; w++) {
			for (TreeRequest r : requests) {
				sink += TreeShapePlan.plan(r).woodCount();
			}
		}
		double[] rounds = new double[ROUNDS];
		for (int k = 0; k < ROUNDS; k++) {
			long t0 = System.nanoTime();
			for (TreeRequest r : requests) {
				sink += TreeShapePlan.plan(r).leafCount();
			}
			rounds[k] = (System.nanoTime() - t0) / 1e6 / requests.size();
		}
		Arrays.sort(rounds);
		return rounds[ROUNDS / 2];
	}
}
