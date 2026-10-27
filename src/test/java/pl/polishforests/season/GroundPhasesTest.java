package pl.polishforests.season;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pl.polishforests.block.GroundPlant;
import pl.polishforests.season.GroundPhases.Look;

/**
 * The phases of the herb layer (step V13, {@link GroundPhases}; plan §3 V13: liverleaf in EARLY_SPRING, wood anemone
 * EARLY to LATE_SPRING with the peak in MID_SPRING and gone from EARLY_SUMMER, bilberry flowering in LATE_SPRING and
 * fruiting in MID and LATE_SUMMER, bracken brown from MID_AUTUMN), over a carpet of 64 × 64 plants.
 */
class GroundPhasesTest {
	private static final int SIZE = 64;

	/** The share of a carpet of plants showing each look at a step. */
	private static Map<Look, Double> shares(GroundPlant plant, int step) {
		Map<Look, Double> out = new EnumMap<>(Look.class);
		for (Look l : Look.values()) {
			out.put(l, 0.0);
		}
		for (int x = 0; x < SIZE; x++) {
			for (int z = 0; z < SIZE; z++) {
				out.merge(GroundPhases.look(plant, step, x * 7 - 100, z * 13 + 5), 1.0 / (SIZE * SIZE), Double::sum);
			}
		}
		return out;
	}

	private static int step(SubSeason sub, int k) {
		return SeasonPhase.firstStep(sub) + k;
	}

	@Test
	void woodAnemoneFlowersInSpringAndWithdrawsInEarlySummer() {
		for (int k = 0; k < 3; k++) {
			assertEquals(1.0, shares(GroundPlant.WOOD_ANEMONE, step(SubSeason.MID_SPRING, k)).get(Look.FULL), 1e-9, "flowering in IV");
			assertEquals(1.0, shares(GroundPlant.WOOD_ANEMONE, step(SubSeason.MID_SUMMER, k)).get(Look.NONE), 1e-9, "gone in VII");
			assertEquals(1.0, shares(GroundPlant.WOOD_ANEMONE, step(SubSeason.EARLY_WINTER, k)).get(Look.NONE), 1e-9, "gone in XII");
		}
		// EARLY_SPRING: shoots and the first flowers at the end of March.
		assertTrue(shares(GroundPlant.WOOD_ANEMONE, step(SubSeason.EARLY_SPRING, 2)).get(Look.FULL) > 0.2);
		assertTrue(shares(GroundPlant.WOOD_ANEMONE, step(SubSeason.EARLY_SPRING, 0)).get(Look.FULL) == 0.0);
		// LATE_SPRING: the flowering ends, leaves only by the end of May.
		assertEquals(1.0, shares(GroundPlant.WOOD_ANEMONE, step(SubSeason.LATE_SPRING, 2)).get(Look.LEAVES), 1e-9);
		// EARLY_SUMMER: withdrawing over June.
		Map<Look, Double> june = shares(GroundPlant.WOOD_ANEMONE, step(SubSeason.EARLY_SUMMER, 0));
		assertTrue(june.get(Look.LEAVES) > 0.5 && june.get(Look.NONE) < 0.5, june.toString());
		assertEquals(1.0, shares(GroundPlant.WOOD_ANEMONE, step(SubSeason.EARLY_SUMMER, 2)).get(Look.NONE), 1e-9);
		// The plants of a carpet change at different dates (the jitter), not at once.
		Map<Look, Double> mid = shares(GroundPlant.WOOD_ANEMONE, step(SubSeason.EARLY_SUMMER, 1));
		assertTrue(mid.get(Look.LEAVES) > 0.1 && mid.get(Look.NONE) > 0.1, mid.toString());
	}

	@Test
	void liverleafFlowersInEarlySpringAndKeepsItsLeaves() {
		assertTrue(shares(GroundPlant.HEPATICA, step(SubSeason.EARLY_SPRING, 1)).get(Look.FULL) > 0.7);
		assertEquals(1.0, shares(GroundPlant.HEPATICA, step(SubSeason.EARLY_SPRING, 2)).get(Look.FULL), 1e-9);
		assertTrue(shares(GroundPlant.HEPATICA, step(SubSeason.MID_SPRING, 2)).get(Look.LEAVES) > 0.7, "flowering over by mid-April");
		for (SubSeason sub : SubSeason.values()) {
			assertEquals(0.0, shares(GroundPlant.HEPATICA, step(sub, 1)).get(Look.NONE), 1e-9, sub.name());
		}
		assertEquals(1.0, shares(GroundPlant.HEPATICA, step(SubSeason.MID_SUMMER, 1)).get(Look.LEAVES), 1e-9);
		assertEquals(1.0, shares(GroundPlant.HEPATICA, step(SubSeason.MID_WINTER, 1)).get(Look.LEAVES), 1e-9);
	}

	@Test
	void bilberryFlowersInMayAndFruitsInSummer() {
		assertEquals(1.0, shares(GroundPlant.BILBERRY, step(SubSeason.LATE_SPRING, 1)).get(Look.FLOWERING), 1e-9);
		assertEquals(1.0, shares(GroundPlant.BILBERRY, step(SubSeason.MID_SUMMER, 1)).get(Look.FULL), 1e-9);
		assertEquals(1.0, shares(GroundPlant.BILBERRY, step(SubSeason.MID_SPRING, 0)).get(Look.FULL), 1e-9);
		for (SubSeason sub : SubSeason.values()) {
			assertEquals(sub == SubSeason.MID_SUMMER || sub == SubSeason.LATE_SUMMER, GroundPhases.fruiting(sub), sub.name());
			assertEquals(0.0, shares(GroundPlant.BILBERRY, step(sub, 1)).get(Look.NONE), 1e-9, sub.name());
		}
		int fertile = 0;
		for (int x = 0; x < 100; x++) {
			for (int z = 0; z < 100; z++) {
				fertile += GroundPhases.fertile(x, 70, z) ? 1 : 0;
			}
		}
		assertEquals(0.65, fertile / 10000.0, 0.03);
	}

	@Test
	void plantsWithoutPhasesAreAlwaysFull() {
		for (int step = 0; step < SeasonPhase.STEPS; step++) {
			assertEquals(Look.FULL, GroundPhases.look(GroundPlant.BRACKEN, step, 3, 4));
			assertEquals(Look.FULL, GroundPhases.look(GroundPlant.NEEDLE_LITTER, step, 3, 4));
		}
	}

	@Test
	void bilberryAndBrackenColors() {
		int raw = 0x79C05A;
		int summer = step(SubSeason.MID_SUMMER, 1);
		int october = step(SubSeason.MID_AUTUMN, 2);
		int winter = step(SubSeason.MID_WINTER, 1);
		for (int x = 0; x < 8; x++) {
			assertEquals(raw, GroundPhases.bilberryColor(summer, x, 2 * x, raw), "bilberry in summer is the grass color");
			assertEquals(raw, GroundPhases.brackenColor(summer, x, 2 * x, raw), "bracken in summer is the grass color");
			int red = GroundPhases.bilberryColor(october, x, 2 * x, raw);
			assertTrue(((red >> 16) & 255) > ((red >> 8) & 255) + 20, "bilberry red in X: " + Integer.toHexString(red));
			int brown = GroundPhases.brackenColor(october + 1, x, 2 * x, raw);
			assertTrue(((brown >> 16) & 255) > ((brown >> 8) & 255), "bracken brown at the end of X: " + Integer.toHexString(brown));
			int dead = GroundPhases.brackenColor(winter, x, 2 * x, raw);
			assertTrue(((dead >> 16) & 255) > ((dead >> 8) & 255), "dead bracken in winter: " + Integer.toHexString(dead));
		}
	}

	@Test
	void shiftsAreSpreadEvenly() {
		Map<Double, Integer> counts = new java.util.TreeMap<>();
		for (int x = 0; x < 100; x++) {
			for (int z = 0; z < 100; z++) {
				counts.merge(Math.round(GroundPhases.shift(x, z) * 3) / 3.0, 1, Integer::sum);
			}
		}
		assertEquals(4, counts.size(), counts.toString());
		for (int n : counts.values()) {
			assertEquals(2500, n, 250, counts.toString());
		}
	}
}
