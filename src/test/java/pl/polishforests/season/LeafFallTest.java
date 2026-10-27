package pl.polishforests.season;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.habitat.Species;

/**
 * The falling leaf particles by the season (step V13, the user's request of 2026-10-26, {@link LeafFall}): broadleaf
 * trees drop no leaves from the leaf-out to the start of their autumn, more than vanilla in autumn, the most in late
 * autumn, none on bare twigs (rare dry leaves of the marcescent crowns); conifers rarely the whole year, the Scots pine a
 * little more in its needle fall.
 */
class LeafFallTest {
	private static final List<Species> BROADLEAF = List.of(Species.BIRCH, Species.LINDEN, Species.NORWAY_MAPLE, Species.ASH,
			Species.HORNBEAM, Species.BEECH, Species.OAK);

	@Test
	void noFallingLeavesFromLeafOutToAutumn() {
		for (Species s : BROADLEAF) {
			for (int step = SeasonPhase.firstStep(SubSeason.LATE_SPRING) + 2; step < SeasonPhase.firstStep(SubSeason.EARLY_AUTUMN) + 1;
					step++) {
				for (int phase = 0; phase < FoliageCurve.PHASES; phase++) {
					assertEquals(0.0F, LeafFall.chance(s, step, phase, false), s.id() + " step " + step + " phase " + phase);
					assertEquals(0.0F, LeafFall.chance(s, step, phase, true), s.id() + " marcescent, step " + step);
				}
			}
		}
	}

	@Test
	void moreThanVanillaInAutumnAndMostInLateAutumn() {
		double[] total = new double[SeasonPhase.STEPS];
		for (Species s : BROADLEAF) {
			float max = 0;
			for (int step = 0; step < SeasonPhase.STEPS; step++) {
				for (int phase = 0; phase < FoliageCurve.PHASES; phase++) {
					float c = LeafFall.chance(s, step, phase, false);
					assertTrue(c >= 0 && c <= LeafFall.AUTUMN_PEAK, s.id() + " " + c);
					max = Math.max(max, c);
					total[step] += c;
				}
			}
			assertTrue(max > 2 * LeafFall.VANILLA, s.id() + " peak " + max);
		}
		// The oak-hornbeam forest (hornbeam, oak and linden most, the others mixed in): the most falling leaves in XI.
		Map<Species, Double> grad = Map.of(Species.HORNBEAM, 0.4, Species.OAK, 0.25, Species.LINDEN, 0.15, Species.NORWAY_MAPLE, 0.07,
				Species.ASH, 0.05, Species.BIRCH, 0.04, Species.BEECH, 0.04);
		double[] forest = new double[SeasonPhase.STEPS];
		for (Map.Entry<Species, Double> e : grad.entrySet()) {
			for (int step = 0; step < SeasonPhase.STEPS; step++) {
				for (int phase = 0; phase < FoliageCurve.PHASES; phase++) {
					forest[step] += e.getValue() * LeafFall.chance(e.getKey(), step, phase, false);
				}
			}
		}
		int best = 0;
		StringBuilder report = new StringBuilder();
		for (int step = 0; step < SeasonPhase.STEPS; step++) {
			report.append(String.format(Locale.ROOT, "%d %.3f/%.4f; ", step, total[step], forest[step]));
			if (forest[step] > forest[best]) {
				best = step;
			}
		}
		System.out.println("[leaf fall] sum over the slice species / the oak-hornbeam forest, per step: " + report);
		assertEquals(SubSeason.LATE_AUTUMN, SeasonPhase.subSeason(best), "most falling leaves in XI, not at step " + best + ": " + report);
		// In the autumn of the leaves (X-XI) the forest drops more leaves than vanilla (0.01 for every leaf all year).
		double autumn = 0;
		for (int step = SeasonPhase.firstStep(SubSeason.MID_AUTUMN); step < SeasonPhase.firstStep(SubSeason.EARLY_WINTER); step++) {
			autumn += total[step];
		}
		assertTrue(autumn / (6 * BROADLEAF.size() * FoliageCurve.PHASES) > LeafFall.VANILLA, report.toString());
	}

	/** After the leaf fall of a tree (the crown shows its twigs) its last leaves fall less and less until it is bare. */
	@Test
	void lastLeavesUntilTheCrownIsBare() {
		for (Species s : BROADLEAF) {
			double fall = FoliageCurve.leafFall(s);
			double bare = FoliageCurve.bare(s);
			assertTrue(fall < bare, s.id());
			float early = LeafFall.chance(s, (int) Math.floor(fall + 0.1), 1, false);
			float atBare = LeafFall.chance(s, (int) Math.floor(bare) + 2, 1, false);
			assertTrue(early > 0, s.id() + " last leaves");
			assertEquals(0.0F, atBare, s.id() + " bare");
		}
	}

	@Test
	void winterOnlyRareDryLeavesOfTheMarcescentCrowns() {
		int winter = SeasonPhase.firstStep(SubSeason.MID_WINTER) + 1;
		for (Species s : BROADLEAF) {
			for (int phase = 0; phase < FoliageCurve.PHASES; phase++) {
				assertEquals(0.0F, LeafFall.chance(s, winter, phase, false), s.id());
				float dry = LeafFall.chance(s, winter, phase, true);
				assertEquals(FoliageCurve.marcescent(s) ? LeafFall.MARCESCENT_WINTER : 0.0F, dry, s.id());
				assertTrue(dry < LeafFall.VANILLA / 5);
			}
		}
	}

	@Test
	void conifersRarelyTheWholeYear() {
		for (int step = 0; step < SeasonPhase.STEPS; step++) {
			float pine = LeafFall.chance(Species.SCOTS_PINE, step, 0, false);
			float spruce = LeafFall.chance(Species.SPRUCE, step, 2, false);
			assertTrue(pine > 0 && pine < LeafFall.VANILLA / 2, "pine " + pine);
			assertEquals(LeafFall.NEEDLES, spruce);
			SubSeason sub = SeasonPhase.subSeason(step);
			assertEquals(sub == SubSeason.EARLY_AUTUMN || sub == SubSeason.MID_AUTUMN ? LeafFall.PINE_AUTUMN : LeafFall.NEEDLES, pine);
		}
	}
}
