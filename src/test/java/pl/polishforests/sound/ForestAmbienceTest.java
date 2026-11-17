package pl.polishforests.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;
import pl.polishforests.season.SubSeason;
import pl.polishforests.sound.AmbienceRules.ForestKind;
import pl.polishforests.sound.AmbienceRules.Phase;
import pl.polishforests.worldgen.habitat.HabitatBiome;

/**
 * Step FA4 (docs/04-pionowy-wycinek.md "Projekt FA4", PW-16): the forest ambience counted without a world. The same
 * cadence as {@link ForestAmbience} (an interval per part of the day, the silence draw, a second voice of the dawn chorus)
 * runs over many simulated days; the thresholds are the project's: dawn at least 1.5 times and dusk at least 1.3 times the
 * events per hour of the day, the night only nocturnal voices, January at most 0.6 times the events of May. The fauna
 * game test ({@code -Psites=sound}) counts the same on the server.
 */
class ForestAmbienceTest {
	/** Ticks of each part of the day (dawn 4:30-8:00, day 8:00-16:30, dusk 16:30-19:30, night 19:30-4:30). */
	private static final Map<Phase, Integer> LENGTH = Map.of(Phase.DAWN, 3500, Phase.DAY, 8500, Phase.DUSK, 3000, Phase.NIGHT, 9000);
	private static final int DAYS = 400;

	static final double DAWN_OVER_DAY = 1.5;
	static final double DUSK_OVER_DAY = 1.3;
	static final double WINTER_OVER_MAY = 0.6;

	/** Events (voices heard) per part of the day and voice, over {@link #DAYS} simulated days. */
	record Count(Map<Phase, Integer> events, Map<ForestVoice, Integer> voices, Map<Phase, Map<ForestVoice, Integer>> byPhase) {
		double perHour(Phase p) {
			return events.get(p) * 1000.0 / LENGTH.get(p) / DAYS;
		}

		int total() {
			return events.values().stream().mapToInt(Integer::intValue).sum();
		}
	}

	static Count simulate(HabitatBiome habitat, SubSeason sub, long seed) {
		RandomSource random = RandomSource.create(seed);
		Map<Phase, Integer> events = new EnumMap<>(Phase.class);
		Map<ForestVoice, Integer> voices = new EnumMap<>(ForestVoice.class);
		Map<Phase, Map<ForestVoice, Integer>> byPhase = new EnumMap<>(Phase.class);
		for (Phase p : Phase.values()) {
			events.put(p, 0);
			byPhase.put(p, new EnumMap<>(ForestVoice.class));
		}
		long end = DAYS * 24000L;
		for (long t = 0; t < end; ) {
			Phase phase = AmbienceRules.phase(t);
			ForestVoice voice = AmbienceRules.choose(habitat, sub, phase, random);
			int heard = 0;
			if (voice != null) {
				heard++;
				count(voices, byPhase.get(phase), voice);
				if (AmbienceRules.chorus(phase, sub)) {
					ForestVoice second = AmbienceRules.choose(habitat, sub, phase, random);
					if (second != null) {
						heard++;
						count(voices, byPhase.get(phase), second);
					}
				}
			}
			events.merge(phase, heard, Integer::sum);
			int interval = AmbienceRules.interval(phase, random);
			assertTrue(interval >= AmbienceRules.MIN_INTERVAL && interval <= AmbienceRules.MAX_INTERVAL, "interval " + interval);
			t += interval;
		}
		return new Count(events, voices, byPhase);
	}

	private static void count(Map<ForestVoice, Integer> voices, Map<ForestVoice, Integer> phase, ForestVoice v) {
		voices.merge(v, 1, Integer::sum);
		phase.merge(v, 1, Integer::sum);
	}

	@Test
	void dawnAndDuskRicherThanNoon() {
		for (HabitatBiome b : List.of(HabitatBiome.OAK_HORNBEAM_FOREST, HabitatBiome.FRESH_PINE_FOREST, HabitatBiome.ALDER_CARR)) {
			for (SubSeason s : List.of(SubSeason.MID_SPRING, SubSeason.LATE_SPRING, SubSeason.EARLY_SUMMER, SubSeason.MID_SUMMER)) {
				Count c = simulate(b, s, 42);
				double day = c.perHour(Phase.DAY);
				assertTrue(day > 0, b + " " + s + ": some voices by day");
				assertTrue(c.perHour(Phase.DAWN) >= DAWN_OVER_DAY * day, b + " " + s + ": dawn " + c.perHour(Phase.DAWN) + " against day " + day);
				assertTrue(c.perHour(Phase.DUSK) >= DUSK_OVER_DAY * day, b + " " + s + ": dusk " + c.perHour(Phase.DUSK) + " against day " + day);
			}
		}
	}

	@Test
	void nightOnlyNocturnalVoices() {
		for (HabitatBiome b : HabitatBiome.values()) {
			if (!b.isForest()) {
				continue;
			}
			for (SubSeason s : SubSeason.values()) {
				Count c = simulate(b, s, 7);
				Map<ForestVoice, Integer> night = c.byPhase().get(Phase.NIGHT);
				for (ForestVoice v : night.keySet()) {
					assertTrue(v.nocturnal(), b + " " + s + ": " + v + " at night");
				}
				assertTrue(night.getOrDefault(ForestVoice.TAWNY_OWL, 0) > 0, b + " " + s + ": the tawny owl at night");
			}
		}
		// The owl's hoot is a night sound; by day it is not drawn at all.
		assertEquals(0, ForestVoice.TAWNY_OWL.weight(Phase.DAY, ForestKind.BROADLEAF, false, SubSeason.MID_SPRING));
	}

	@Test
	void winterQuieter() {
		for (HabitatBiome b : List.of(HabitatBiome.OAK_HORNBEAM_FOREST, HabitatBiome.FRESH_PINE_FOREST, HabitatBiome.MONTANE_SPRUCE_FOREST)) {
			int may = simulate(b, SubSeason.LATE_SPRING, 3).total();
			for (SubSeason s : List.of(SubSeason.EARLY_WINTER, SubSeason.MID_WINTER)) {
				int winter = simulate(b, s, 3).total();
				assertTrue(winter <= WINTER_OVER_MAY * may, b + " " + s + ": " + winter + " events against " + may + " in May");
				assertTrue(winter > 0, b + " " + s + ": not silent (tits, goldcrest, owl)");
			}
			// Only the voices of winter (PW-16): no chaffinch, blackbird, cuckoo or frog in January.
			Count jan = simulate(b, SubSeason.MID_WINTER, 5);
			for (ForestVoice v : List.of(ForestVoice.CHAFFINCH, ForestVoice.BLACKBIRD, ForestVoice.ROBIN, ForestVoice.CUCKOO, ForestVoice.COMMON_FROG)) {
				assertEquals(0, jan.voices().getOrDefault(v, 0), b + ": " + v + " in January");
			}
		}
	}

	@Test
	void voicesFollowTheForest() {
		// The frog only in wet forests and only in March and April.
		assertTrue(simulate(HabitatBiome.ALDER_CARR, SubSeason.EARLY_SPRING, 9).voices().getOrDefault(ForestVoice.COMMON_FROG, 0) > 0);
		assertEquals(0, simulate(HabitatBiome.ALDER_CARR, SubSeason.EARLY_SUMMER, 9).voices().getOrDefault(ForestVoice.COMMON_FROG, 0));
		assertEquals(0, simulate(HabitatBiome.DRY_PINE_FOREST, SubSeason.EARLY_SPRING, 9).voices().getOrDefault(ForestVoice.COMMON_FROG, 0));
		// The coal tit and the goldcrest belong to the conifers: more there than the great tit, the other way in a grąd.
		Count pine = simulate(HabitatBiome.FRESH_PINE_FOREST, SubSeason.LATE_SPRING, 11);
		Count grad = simulate(HabitatBiome.OAK_HORNBEAM_FOREST, SubSeason.LATE_SPRING, 11);
		assertTrue(pine.voices().get(ForestVoice.COAL_TIT) > pine.voices().get(ForestVoice.GREAT_TIT));
		assertTrue(grad.voices().get(ForestVoice.GREAT_TIT) > 3 * grad.voices().getOrDefault(ForestVoice.COAL_TIT, 0));
		// Every voice is heard somewhere in the year.
		for (ForestVoice v : ForestVoice.values()) {
			boolean heard = false;
			for (SubSeason s : SubSeason.values()) {
				for (Phase p : Phase.values()) {
					heard |= v.weight(p, ForestKind.BROADLEAF, true, s) > 0;
				}
			}
			assertTrue(heard, v + " is heard");
			assertTrue(v.range() >= 32 && v.range() <= 96, v + " range");
		}
	}

	@Test
	void notInOpenLand() {
		RandomSource random = RandomSource.create(1);
		for (HabitatBiome b : HabitatBiome.values()) {
			if (b.isForest()) {
				assertNotNull(AmbienceRules.kind(b), b.id());
			} else {
				assertNull(AmbienceRules.kind(b), b.id());
				assertNull(AmbienceRules.choose(b, SubSeason.LATE_SPRING, Phase.DAWN, random), b.id());
			}
		}
		assertNull(AmbienceRules.choose(null, SubSeason.LATE_SPRING, Phase.DAWN, random));
	}

	@Test
	void phasesOfTheDay() {
		assertEquals(Phase.DAWN, AmbienceRules.phase(23_000));
		assertEquals(Phase.DAWN, AmbienceRules.phase(500));
		assertEquals(Phase.DAY, AmbienceRules.phase(6_000));
		assertEquals(Phase.DUSK, AmbienceRules.phase(12_500));
		assertEquals(Phase.NIGHT, AmbienceRules.phase(18_000));
		assertEquals(Phase.DAWN, AmbienceRules.phase(24_000 * 5 + 500));
		int sum = 0;
		for (int t = 0; t < 24_000; t++) {
			sum += AmbienceRules.phase(t) == Phase.DAY ? 1 : 0;
		}
		assertEquals(LENGTH.get(Phase.DAY), sum);
	}

	@Test
	void enumsOnlyAppended() {
		// The census counters use the ordinals: new voices and phases only at the end.
		assertEquals(List.of("CHAFFINCH", "BLACKBIRD", "ROBIN", "GREAT_TIT", "COAL_TIT", "GOLDCREST", "CUCKOO", "WOODPECKER_DRUM",
				"WOODPECKER_CALL", "TAWNY_OWL", "JAY", "COMMON_FROG"), Stream.of(ForestVoice.values()).map(Enum::name).toList());
		assertEquals(List.of("DAWN", "DAY", "DUSK", "NIGHT"), Stream.of(Phase.values()).map(Enum::name).toList());
		assertEquals(List.of("BROADLEAF", "MIXED", "CONIFER"), Stream.of(ForestKind.values()).map(Enum::name).toList());
	}
}
