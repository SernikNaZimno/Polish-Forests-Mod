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

	/** Step F2: the card of the alder carr and the ash-alder forest (docs/04 "Projekt F2", N1, N3, N4, N7–N10). */
	@Test
	void alderCardOfStepF2() {
		for (Forest f : new Forest[] {Forest.ALDER_CARR_NATURAL, Forest.ALDER_CARR_MANAGED}) {
			assertRange(80, 100, ForestNorms.COMPOSITION + "black_alder", f, null, null);
			assertRange(60, 80, ForestNorms.CANOPY, f, null, null);
			assertRange(20, 50, ForestNorms.UNDERSTORY, f, null, null);
			assertRange(60, 90, ForestNorms.HERBS, f, null, null);
			assertTrue(ForestNorms.of(ForestNorms.TREES_PER_CHUNK, f, null, null).isPresent());
			assertTrue(ForestNorms.of(ForestNorms.MOSS, f, null, null).isPresent());
			assertTrue(ForestNorms.of(ForestNorms.TRANSECT_ORDER, f, null, null).isEmpty(), "the carr is off the transect");
		}
		for (Forest f : new Forest[] {Forest.ASH_ALDER_NATURAL, Forest.ASH_ALDER_MANAGED}) {
			assertRange(50, 90, ForestNorms.COMPOSITION + "black_alder", f, null, null);
			assertRange(10, 40, ForestNorms.COMPOSITION + "maple_ash", f, null, null);
			assertRange(70, 90, ForestNorms.CANOPY, f, null, null);
			assertRange(20, 50, ForestNorms.UNDERSTORY, f, null, null);
			assertRange(70, 100, ForestNorms.HERBS, f, null, null);
		}
		assertEquals(Forest.ALDER_CARR_NATURAL, Forest.of(pl.polishforests.worldgen.habitat.HabitatBiome.ALDER_CARR, true));
		assertEquals(Forest.ASH_ALDER_MANAGED, Forest.of(pl.polishforests.worldgen.habitat.HabitatBiome.ASH_ALDER_FOREST, false));
		assertTrue(ForestNorms.of(ForestNorms.DEADWOOD_PER_CHUNK, Forest.ALDER_CARR_NATURAL, null, null).isPresent());
		assertRange(1, 2, ForestNorms.STUMPS_PER_CHUNK, Forest.ASH_ALDER_MANAGED, null, null);
	}

	/**
	 * Step F4: the card of the four pine forests and the birch bog woodland (docs/04 "Projekt F4", N1, N3, N4, N7–N10): N9
	 * as the lichens of the dry pine forest and the sphagnum of the bog woodland, the birch bog woodland as the site variant
	 * of the boggy mixed coniferous site type.
	 */
	@Test
	void pineCardOfStepF4() {
		for (boolean natural : new boolean[] {true, false}) {
			Forest dry = Forest.of(pl.polishforests.worldgen.habitat.HabitatBiome.DRY_PINE_FOREST, natural);
			Forest moist = Forest.of(pl.polishforests.worldgen.habitat.HabitatBiome.MOIST_PINE_FOREST, natural);
			Forest bog = Forest.of(pl.polishforests.worldgen.habitat.HabitatBiome.BOG_WOODLAND, natural);
			Forest coastal = Forest.of(pl.polishforests.worldgen.habitat.HabitatBiome.COASTAL_PINE_FOREST, natural);
			assertEquals(natural, dry.natural());
			assertRange(90, 100, ForestNorms.COMPOSITION + "scots_pine", dry, null, null);
			assertRange(40, 60, ForestNorms.CANOPY, dry, null, null);
			assertRange(60, 90, ForestNorms.MOSS, dry, null, null);
			assertRange(70, 90, ForestNorms.COMPOSITION + "scots_pine", moist, null, null);
			assertRange(10, 30, ForestNorms.COMPOSITION + "birch", moist, null, null);
			assertRange(60, 90, ForestNorms.COMPOSITION + "scots_pine", bog, null, null);
			assertRange(30, 60, ForestNorms.CANOPY, bog, null, null);
			assertRange(80, 100, ForestNorms.MOSS, bog, null, null);
			Norm birch = ForestNorms.of(ForestNorms.COMPOSITION + "birch", bog, null, null, ForestNorms.Site.BIRCH_BOG).orElseThrow();
			assertEquals(60, birch.min(), 1e-9);
			assertEquals(90, birch.max(), 1e-9);
			Norm pine = ForestNorms.of(ForestNorms.COMPOSITION + "scots_pine", bog, null, null, ForestNorms.Site.BIRCH_BOG).orElseThrow();
			assertEquals(10, pine.min(), 1e-9);
			assertEquals(40, pine.max(), 1e-9);
			assertRange(70, 90, ForestNorms.COMPOSITION + "scots_pine", coastal, null, null);
			for (Forest f : new Forest[] {dry, moist, bog, coastal}) {
				for (String key : new String[] {ForestNorms.TREES_PER_CHUNK, ForestNorms.UNDERSTORY, ForestNorms.HERBS}) {
					assertTrue(ForestNorms.of(key, f, Aspect.SUMMER, null).isPresent(), key + " " + f);
				}
				assertTrue(ForestNorms.of(ForestNorms.TRANSECT_ORDER, f, null, null).isEmpty(), f + " is off the transect");
				if (!natural) {
					assertRange(1, 2, ForestNorms.STUMPS_PER_CHUNK, f, null, null);
				}
			}
		}
		assertRange(0.9, 0.9, ForestNorms.DEADWOOD_PER_CHUNK, Forest.DRY_PINE_NATURAL, null, null);
		assertRange(1.2, 1.2, ForestNorms.DEADWOOD_PER_CHUNK, Forest.BOG_WOODLAND_NATURAL, null, null);
		java.util.function.Function<pl.polishforests.worldgen.habitat.ForestSiteType, ForestNorms.Site> site = t -> ForestNorms.siteOf(
				pl.polishforests.worldgen.habitat.Habitat.pack(pl.polishforests.worldgen.habitat.HabitatBiome.BOG_WOODLAND,
						pl.polishforests.worldgen.habitat.Zone.NONE, t, pl.polishforests.worldgen.habitat.Association.TYPICAL,
						pl.polishforests.worldgen.habitat.LandCover.FOREST, 0, pl.polishforests.worldgen.habitat.Soil.forBiome(
								pl.polishforests.worldgen.habitat.HabitatBiome.BOG_WOODLAND, pl.polishforests.worldgen.habitat.Zone.NONE)));
		assertEquals(ForestNorms.Site.BIRCH_BOG, site.apply(pl.polishforests.worldgen.habitat.ForestSiteType.BOGGY_MIXED_CONIFEROUS));
		assertEquals(null, site.apply(pl.polishforests.worldgen.habitat.ForestSiteType.BOGGY_CONIFEROUS));
	}

	/**
	 * Step F3: the card of the upland fir forest and the Carpathian beech forest with its sycamore ravine forest (docs/04
	 * "Projekt F3", N1, N3, N4, N7–N10), the site variant of a habitat code.
	 */
	@Test
	void mountainCardOfStepF3() {
		for (Forest f : new Forest[] {Forest.UPLAND_FIR_NATURAL, Forest.UPLAND_FIR_MANAGED}) {
			assertRange(50, 90, ForestNorms.COMPOSITION + "fir", f, null, null);
			assertRange(70, 95, ForestNorms.CANOPY, f, null, null);
			assertRange(20, 50, ForestNorms.UNDERSTORY, f, null, null);
			assertRange(30, 70, ForestNorms.MOSS, f, null, null);
			assertTrue(ForestNorms.of(ForestNorms.TREES_PER_CHUNK, f, null, null).isPresent());
			assertTrue(ForestNorms.of(ForestNorms.HERBS, f, Aspect.SUMMER, null).isPresent());
			assertTrue(ForestNorms.of(ForestNorms.HERBS_SPRING, f, Aspect.SPRING, null).isPresent());
			assertTrue(ForestNorms.of(ForestNorms.TRANSECT_ORDER, f, null, null).isEmpty(), "the fir forest is off the transect");
		}
		for (Forest f : new Forest[] {Forest.MONTANE_BEECH_NATURAL, Forest.MONTANE_BEECH_MANAGED}) {
			assertRange(50, 90, ForestNorms.COMPOSITION + "beech", f, null, null);
			assertRange(10, 40, ForestNorms.COMPOSITION + "fir", f, null, null);
			assertRange(70, 95, ForestNorms.CANOPY, f, null, null);
			assertRange(5, 30, ForestNorms.UNDERSTORY, f, null, null);
			Norm sycamore = ForestNorms.of(ForestNorms.COMPOSITION + "maple_ash", f, null, null, ForestNorms.Site.FERTILE).orElseThrow();
			assertEquals(5, sycamore.min(), 1e-9);
			assertEquals(15, sycamore.max(), 1e-9);
			Norm ravine = ForestNorms.of(ForestNorms.COMPOSITION + "maple_ash", f, null, null, ForestNorms.Site.RAVINE).orElseThrow();
			assertEquals(55, ravine.min(), 1e-9);
			assertEquals(ForestNorms.Site.RAVINE, ForestNorms.of(ForestNorms.COMPOSITION + "beech", f, null, null, ForestNorms.Site.RAVINE)
					.orElseThrow().site());
			Norm spring = ForestNorms.of(ForestNorms.HERBS_SPRING, f, null, null, ForestNorms.Site.FERTILE).orElseThrow();
			assertEquals(30, spring.min(), 1e-9);
			assertEquals(80, spring.max(), 1e-9);
			Norm summer = ForestNorms.of(ForestNorms.HERBS, f, null, null, ForestNorms.Site.FERTILE).orElseThrow();
			assertEquals(10, summer.min(), 1e-9);
			assertEquals(50, summer.max(), 1e-9);
			// A plot of unknown form takes the union.
			assertRange(10, 90, ForestNorms.HERBS_SPRING, f, null, null);
		}
		assertRange(2, 2, ForestNorms.DEADWOOD_PER_CHUNK, Forest.UPLAND_FIR_NATURAL, null, null);
		assertRange(2.2, 2.2, ForestNorms.DEADWOOD_PER_CHUNK, Forest.MONTANE_BEECH_NATURAL, null, null);
		assertRange(1, 2, ForestNorms.STUMPS_PER_CHUNK, Forest.MONTANE_BEECH_MANAGED, null, null);
		assertEquals(Forest.UPLAND_FIR_NATURAL, Forest.of(pl.polishforests.worldgen.habitat.HabitatBiome.UPLAND_FIR_FOREST, true));
		assertEquals(Forest.MONTANE_BEECH_MANAGED, Forest.of(pl.polishforests.worldgen.habitat.HabitatBiome.MONTANE_BEECH_FOREST, false));
		// The site variant of a code: the association of the ravine before the site type.
		java.util.function.BiFunction<pl.polishforests.worldgen.habitat.ForestSiteType, pl.polishforests.worldgen.habitat.Association,
				ForestNorms.Site> site = (t, a) -> ForestNorms.siteOf(pl.polishforests.worldgen.habitat.Habitat.pack(
						pl.polishforests.worldgen.habitat.HabitatBiome.MONTANE_BEECH_FOREST, pl.polishforests.worldgen.habitat.Zone.NONE, t, a,
						pl.polishforests.worldgen.habitat.LandCover.FOREST, 0, pl.polishforests.worldgen.habitat.Soil.forBiome(
								pl.polishforests.worldgen.habitat.HabitatBiome.MONTANE_BEECH_FOREST, pl.polishforests.worldgen.habitat.Zone.NONE)));
		assertEquals(ForestNorms.Site.FERTILE, site.apply(pl.polishforests.worldgen.habitat.ForestSiteType.MOUNTAIN_BROADLEAVED,
				pl.polishforests.worldgen.habitat.Association.TYPICAL));
		assertEquals(ForestNorms.Site.ACID, site.apply(pl.polishforests.worldgen.habitat.ForestSiteType.MOUNTAIN_MIXED_BROADLEAVED,
				pl.polishforests.worldgen.habitat.Association.ACIDOPHILOUS));
		assertEquals(ForestNorms.Site.RAVINE, site.apply(pl.polishforests.worldgen.habitat.ForestSiteType.MOUNTAIN_BROADLEAVED,
				pl.polishforests.worldgen.habitat.Association.SYCAMORE_RAVINE_FOREST));
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
