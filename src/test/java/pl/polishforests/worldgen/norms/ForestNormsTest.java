package pl.polishforests.worldgen.norms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import pl.polishforests.season.SubSeason;
import pl.polishforests.worldgen.norms.ForestNorms.Aspect;
import pl.polishforests.worldgen.norms.ForestNorms.Forest;
import pl.polishforests.worldgen.norms.ForestNorms.Norm;
import pl.polishforests.worldgen.norms.ForestNorms.Verdict;
import pl.polishforests.worldgen.tree.AgeClass;

/** The norms card (docs/04-pionowy-wycinek.md §6): its numbers, lookups and verdicts. */
class ForestNormsTest {
	@Test
	void everyNormHasAMetricASourceAndARange() {
		Set<String> seen = new HashSet<>();
		for (Norm n : ForestNorms.all()) {
			assertTrue(ForestNorms.METRICS.containsKey(n.metric()), n.metric());
			assertFalse(n.source().isBlank(), n.metric());
			assertTrue(n.min() <= n.max(), n.metric());
			assertTrue(n.tolerance() == ForestNorms.TOLERANCE || n.tolerance() == ForestNorms.TOLERANCE_WIDE
					|| n.metric().equals(ForestNorms.TRANSECT_ORDER), n.metric());
			String key = n.metric() + " " + n.forest() + " " + n.aspect() + " " + n.age() + " " + n.site();
			assertTrue(seen.add(key), "one norm per metric, forest, aspect, age and site: " + key);
		}
		for (String key : ForestNorms.COMPOSITION_KEYS) {
			assertTrue(ForestNorms.METRICS.containsKey(ForestNorms.COMPOSITION + key), key);
		}
	}

	@Test
	void numbersOfTheCard() {
		// A few rows of §6 by hand.
		assertRange(15, 15, ForestNorms.TREES_PER_CHUNK, Forest.OAK_HORNBEAM_NATURAL, null, null);
		assertRange(10, 12, ForestNorms.TREES_PER_CHUNK, Forest.FRESH_PINE_MANAGED, null, AgeClass.MATURE);
		assertRange(8, 16, ForestNorms.TREES_PER_CHUNK, Forest.FRESH_PINE_MANAGED, null, null);
		assertRange(80, 95, ForestNorms.CANOPY, Forest.OAK_HORNBEAM_NATURAL, null, null);
		assertRange(60, 95, ForestNorms.MOSS, Forest.FRESH_PINE_MANAGED, null, null);
		// Step V12: the spring and summer herb layer from one census (HERBS_SPRING and HERBS), whatever the season.
		assertRange(60, 100, ForestNorms.HERBS_SPRING, Forest.OAK_HORNBEAM_NATURAL, Aspect.SUMMER, null);
		assertRange(20, 60, ForestNorms.HERBS, Forest.OAK_HORNBEAM_NATURAL, Aspect.SUMMER, null);
		assertRange(50, 90, ForestNorms.HERBS_SPRING, Forest.OAK_HORNBEAM_MANAGED, null, null);
		assertRange(80, 95, ForestNorms.COMPOSITION + "scots_pine", Forest.FRESH_PINE_NATURAL, null, null);
		assertRange(2, 2, ForestNorms.DEADWOOD_PER_CHUNK, Forest.OAK_HORNBEAM_NATURAL, null, null);
		assertEquals(ForestNorms.TOLERANCE_WIDE, ForestNorms.of(ForestNorms.GAP_SHARE, Forest.OAK_HORNBEAM_NATURAL, null, null).orElseThrow()
				.tolerance());
	}

	@Test
	void beechForestCardOfStepF1() {
		for (Forest f : new Forest[] {Forest.LOWLAND_BEECH_NATURAL, Forest.LOWLAND_BEECH_MANAGED}) {
			assertRange(70, 100, ForestNorms.COMPOSITION + "beech", f, null, null);
			assertRange(80, 95, ForestNorms.CANOPY, f, null, null);
			assertRange(0, 15, ForestNorms.UNDERSTORY, f, null, null);
			assertRange(0, 30, ForestNorms.MOSS, f, null, null);
			Norm fertile = ForestNorms.of(ForestNorms.HERBS_SPRING, f, null, null, ForestNorms.Site.FERTILE).orElseThrow();
			assertEquals(20, fertile.min(), 1e-9);
			assertEquals(70, fertile.max(), 1e-9);
			Norm acid = ForestNorms.of(ForestNorms.HERBS_SPRING, f, null, null, ForestNorms.Site.ACID).orElseThrow();
			assertEquals(5, acid.min(), 1e-9);
			assertEquals(40, acid.max(), 1e-9);
			// A plot of unknown form takes the union.
			assertRange(5, 70, ForestNorms.HERBS_SPRING, f, null, null);
			assertTrue(ForestNorms.of(ForestNorms.TRANSECT_ORDER, f, null, null).isEmpty(), "the beech forest is off the transect");
		}
		assertEquals(Forest.LOWLAND_BEECH_MANAGED, Forest.of(pl.polishforests.worldgen.habitat.HabitatBiome.LOWLAND_BEECH_FOREST, false));
		assertEquals(Forest.OAK_HORNBEAM_NATURAL, Forest.of(pl.polishforests.worldgen.habitat.HabitatBiome.OAK_HORNBEAM_FOREST, true));
		assertRange(2, 2, ForestNorms.DEADWOOD_PER_CHUNK, Forest.LOWLAND_BEECH_NATURAL, null, null);
		assertRange(1, 2, ForestNorms.STUMPS_PER_CHUNK, Forest.LOWLAND_BEECH_MANAGED, null, null);
		assertRange(6, 12, ForestNorms.TREES_PER_CHUNK, Forest.LOWLAND_BEECH_NATURAL, null, AgeClass.OLD_GROWTH);
		assertRange(7, 15, ForestNorms.TREES_PER_CHUNK, Forest.LOWLAND_BEECH_MANAGED, null, null);
		assertRange(9, 12, ForestNorms.TREES_PER_CHUNK, Forest.LOWLAND_BEECH_MANAGED, null, AgeClass.MATURE);
	}

	private static void assertRange(double min, double max, String metric, Forest forest, Aspect aspect, AgeClass age) {
		Norm n = ForestNorms.of(metric, forest, aspect, age).orElseThrow(() -> new AssertionError("no norm " + metric + " " + forest));
		assertEquals(min, n.min(), 1e-9, metric);
		assertEquals(max, n.max(), 1e-9, metric);
	}

	@Test
	void lookupsByAspectAndAge() {
		// A row without an aspect holds all year (step V12: the herb layer rows of both aspects come from one census).
		assertTrue(ForestNorms.of(ForestNorms.HERBS, Forest.OAK_HORNBEAM_NATURAL, Aspect.OTHER, null).isPresent());
		assertTrue(ForestNorms.of(ForestNorms.HERBS_SPRING, Forest.OAK_HORNBEAM_NATURAL, null, null).isPresent());
		assertTrue(ForestNorms.of(ForestNorms.HERBS, Forest.FRESH_PINE_MANAGED, Aspect.OTHER, null).isPresent());
		assertTrue(ForestNorms.of(ForestNorms.HERBS_SPRING, Forest.FRESH_PINE_MANAGED, null, null).isEmpty(), "no spring row in the pine forest");
		// Unknown age class: the general row.
		assertRange(8, 16, ForestNorms.TREES_PER_CHUNK, Forest.FRESH_PINE_MANAGED, Aspect.SUMMER, null);
		assertRange(15, 16, ForestNorms.TREES_PER_CHUNK, Forest.FRESH_PINE_MANAGED, Aspect.SUMMER, AgeClass.POLE_STAGE);
		assertTrue(ForestNorms.of(ForestNorms.TOP_HEIGHT, Forest.FRESH_PINE_MANAGED, null, null).isEmpty(), "diagnostics have no norm");
		assertEquals(Aspect.SPRING, Aspect.of(SubSeason.MID_SPRING));
		assertEquals(Aspect.SUMMER, Aspect.of(SubSeason.LATE_SUMMER));
		assertEquals(Aspect.OTHER, Aspect.of(SubSeason.EARLY_AUTUMN));
	}

	@Test
	void verdicts() {
		Norm canopy = ForestNorms.of(ForestNorms.CANOPY, Forest.OAK_HORNBEAM_NATURAL, null, null).orElseThrow();
		assertEquals(Verdict.IN, ForestNorms.verdict(80, canopy));
		assertEquals(Verdict.IN, ForestNorms.verdict(95, canopy));
		assertEquals(Verdict.TOLERANCE, ForestNorms.verdict(64, canopy), "80 − 20%");
		assertEquals(Verdict.OUT, ForestNorms.verdict(63.9, canopy));
		assertEquals(Verdict.TOLERANCE, ForestNorms.verdict(114, canopy), "95 + 20%");
		assertEquals(Verdict.NONE, ForestNorms.verdict(Double.NaN, canopy));
		Norm logs = ForestNorms.of(ForestNorms.LOGS_PER_CHUNK, Forest.FRESH_PINE_MANAGED, null, null).orElseThrow();
		assertEquals(Verdict.TOLERANCE, ForestNorms.verdict(0.13, logs), "0.1 + 30%");
		assertEquals(Verdict.OUT, ForestNorms.verdict(0.14, logs));
		Norm ratio = ForestNorms.of(ForestNorms.REGENERATION_RATIO, Forest.OAK_HORNBEAM_NATURAL, null, null).orElseThrow();
		assertEquals(Verdict.IN, ForestNorms.verdict(Double.POSITIVE_INFINITY, ratio));
		Norm gaps = ForestNorms.of(ForestNorms.GAP_SHARE, Forest.FRESH_PINE_MANAGED, null, null).orElseThrow();
		assertEquals(Verdict.IN, ForestNorms.verdict(0, gaps));
		assertEquals(Verdict.OUT, ForestNorms.verdict(0.5, gaps), "no gaps: any gap is out");
	}

	@Test
	void numbers() {
		assertEquals("15", ForestNorms.number(15));
		assertEquals("0.25", ForestNorms.number(0.25));
		assertEquals("2.56", ForestNorms.number(2.5600001));
		assertEquals("0", ForestNorms.number(-0.001));
		assertEquals("inf", ForestNorms.number(Double.POSITIVE_INFINITY));
		assertEquals("", ForestNorms.number(Double.NaN));
		Norm order = ForestNorms.of(ForestNorms.TRANSECT_ORDER, Forest.FRESH_PINE_NATURAL, null, null).orElseThrow();
		assertEquals("1", order.range());
		Norm ratio = ForestNorms.of(ForestNorms.REGENERATION_RATIO, Forest.OAK_HORNBEAM_NATURAL, null, null).orElseThrow();
		assertEquals("≥ 4", ratio.range());
	}
}
