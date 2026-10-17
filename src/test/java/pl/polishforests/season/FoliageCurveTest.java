package pl.polishforests.season;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.habitat.Species;
import pl.polishforests.worldgen.tree.SpeciesShape;

/**
 * The species foliage curves of step V7 (docs/04-pionowy-wycinek.md, report 08 §9): every slice species has one, the
 * species turn in the order of the Polish calendar (birch and linden first, oak last), the colors of the curves (yellow,
 * yellow then brown, green-yellow), 2-3 color steps per autumn sub-season, the phase shift, the conifers' constant hue
 * with a slight winter darkening and the distinct summer colors of the species.
 */
class FoliageCurveTest {
	/** The raw foliage color of the oak-hornbeam forest (src/main/generated). */
	private static final int GRAD = 0x59AE30;
	/** Largest channel difference counted as "turned" from the summer color. */
	private static final int TURNED = 40;

	private static int dist(int a, int b) {
		return Math.max(Math.abs(((a >> 16) & 255) - ((b >> 16) & 255)), Math.max(Math.abs(((a >> 8) & 255) - ((b >> 8) & 255)),
				Math.abs((a & 255) - (b & 255))));
	}

	private static int r(int c) {
		return (c >> 16) & 255;
	}

	private static int g(int c) {
		return (c >> 8) & 255;
	}

	private static int b(int c) {
		return c & 255;
	}

	/** The first autumn color step (from EARLY_AUTUMN on) at which the mean-phase color is far from the summer color. */
	private static int turn(Species s) {
		int summer = FoliageCurve.summer(s, GRAD);
		for (int step = SeasonPhase.firstStep(SubSeason.EARLY_AUTUMN); step < SeasonPhase.STEPS; step++) {
			if (dist(FoliageCurve.colorAt(s, step + 0.5, GRAD), summer) >= TURNED) {
				return step;
			}
		}
		return SeasonPhase.STEPS;
	}

	@Test
	void everySliceSpeciesHasACurve() {
		assertEquals(SpeciesShape.species(), FoliageCurve.species());
		for (Species s : FoliageCurve.species()) {
			for (int step = 0; step < SeasonPhase.STEPS; step++) {
				for (int p = 0; p < FoliageCurve.PHASES; p++) {
					int c = FoliageCurve.color(s, step, p, GRAD);
					assertEquals(c & 0xFFFFFF, c, s + " step " + step);
				}
			}
		}
	}

	@Test
	void speciesTurnInTheOrderOfTheCalendar() {
		StringBuilder report = new StringBuilder();
		for (Species s : FoliageCurve.species()) {
			if (!FoliageCurve.conifer(s)) {
				report.append(String.format(Locale.ROOT, "%s %d; ", s.id(), turn(s)));
			}
		}
		System.out.println("[foliage] first turned step (21 = 1st decade of X): " + report);
		// Birch and linden from the end of IX: turned by the 1st decade of X.
		assertTrue(turn(Species.BIRCH) <= 21, report.toString());
		assertTrue(turn(Species.LINDEN) <= 21, report.toString());
		assertTrue(turn(Species.NORWAY_MAPLE) <= 22, report.toString());
		// Hornbeam and beech in the 1st-2nd decade of X, oak late (end of X).
		assertTrue(turn(Species.HORNBEAM) >= turn(Species.BIRCH) + 1, report.toString());
		assertTrue(turn(Species.BEECH) >= turn(Species.BIRCH), report.toString());
		assertTrue(turn(Species.OAK) >= turn(Species.BIRCH) + 2, report.toString());
		assertTrue(turn(Species.OAK) >= turn(Species.HORNBEAM) + 1, report.toString());
		assertTrue(turn(Species.OAK) <= 24, report.toString());
		// In the summer (MID_SUMMER) nothing has turned.
		for (Species s : FoliageCurve.species()) {
			int c = FoliageCurve.colorAt(s, SeasonPhase.firstStep(SubSeason.MID_SUMMER) + 0.5, GRAD);
			assertTrue(dist(c, FoliageCurve.summer(s, GRAD)) < 12, s.id());
		}
	}

	@Test
	void autumnColorsOfTheSpecies() {
		int full = SeasonPhase.firstStep(SubSeason.MID_AUTUMN) + 1;           // 2nd decade of X
		int birch = FoliageCurve.colorAt(Species.BIRCH, full + 0.5, GRAD);
		assertTrue(r(birch) > 180 && g(birch) > 150 && b(birch) < 90, "birch yellow " + Integer.toHexString(birch));
		int linden = FoliageCurve.colorAt(Species.LINDEN, full + 0.5, GRAD);
		assertTrue(r(linden) > 170 && g(linden) > 140 && b(linden) < 90, "linden yellow " + Integer.toHexString(linden));
		int maple = FoliageCurve.colorAt(Species.NORWAY_MAPLE, full + 1.0, GRAD);
		assertTrue(r(maple) > 190 && r(maple) - g(maple) > 40, "maple orange " + Integer.toHexString(maple));
		// Ash drops its leaves green-yellow: green at least as strong as red.
		int ash = FoliageCurve.colorAt(Species.ASH, full + 0.8, GRAD);
		assertTrue(g(ash) >= r(ash) && r(ash) > 120, "ash green-yellow " + Integer.toHexString(ash));
		// Hornbeam yellow in the 3rd decade of X, brown in XI.
		int hbYellow = FoliageCurve.colorAt(Species.HORNBEAM, 23.0, GRAD);
		int hbBrown = FoliageCurve.colorAt(Species.HORNBEAM, 24.6, GRAD);
		assertTrue(r(hbYellow) > 170 && g(hbYellow) > 140, "hornbeam yellow " + Integer.toHexString(hbYellow));
		assertTrue(r(hbBrown) - g(hbBrown) >= 25 && g(hbBrown) < g(hbYellow) - 30, "hornbeam brown " + Integer.toHexString(hbBrown));
		// Oak late, yellow-brown at the end of X, brown in XI.
		int oak = FoliageCurve.colorAt(Species.OAK, 24.0, GRAD);
		assertTrue(r(oak) > g(oak) && r(oak) > 150, "oak yellow-brown " + Integer.toHexString(oak));
		// The autumn hues of birch, maple, hornbeam (brown), beech (copper) and ash are distinct in the 3rd decade of X.
		List<Integer> late = new ArrayList<>();
		for (Species s : List.of(Species.BIRCH, Species.NORWAY_MAPLE, Species.BEECH, Species.ASH, Species.OAK)) {
			late.add(FoliageCurve.colorAt(s, 23.0, GRAD));
		}
		for (int i = 0; i < late.size(); i++) {
			for (int j = i + 1; j < late.size(); j++) {
				assertTrue(dist(late.get(i), late.get(j)) >= 15, "autumn colors " + i + " " + j);
			}
		}
	}

	@Test
	void twoOrThreeColorStepsPerAutumnSubSeason() {
		for (Species s : FoliageCurve.species()) {
			if (FoliageCurve.conifer(s)) {
				continue;
			}
			Set<Integer> colors = new HashSet<>();
			int first = SeasonPhase.firstStep(SubSeason.MID_AUTUMN);
			for (int k = 0; k < SeasonPhase.STEPS_PER_SUB_SEASON; k++) {
				colors.add(FoliageCurve.color(s, first + k, 1, GRAD));
			}
			assertTrue(colors.size() >= 2, s.id() + " has " + colors.size() + " colors in MID_AUTUMN");
		}
	}

	@Test
	void phaseShiftsTheCurve() {
		int step = SeasonPhase.firstStep(SubSeason.EARLY_AUTUMN) + 2;     // 3rd decade of IX: birch starts to turn
		int late = FoliageCurve.color(Species.BIRCH, step, 0, GRAD);
		int early = FoliageCurve.color(Species.BIRCH, step, 3, GRAD);
		int summer = FoliageCurve.summer(Species.BIRCH, GRAD);
		assertTrue(dist(early, summer) > dist(late, summer) + 10, "phase 3 turns earlier than phase 0");
		assertEquals(-1.0 + step + 0.5, FoliageCurve.position(step, 0), 1e-9);
		assertEquals(1.0 + step + 0.5, FoliageCurve.position(step, 3), 1e-9);
		// Conifers ignore the phase and the biome.
		assertEquals(FoliageCurve.color(Species.SCOTS_PINE, step, 0, GRAD), FoliageCurve.color(Species.SCOTS_PINE, step, 3, 0x123456));
	}

	@Test
	void conifersKeepTheirHueAndDarkenInWinter() {
		for (Species s : List.of(Species.SCOTS_PINE, Species.SPRUCE)) {
			int summer = FoliageCurve.summer(s, GRAD);
			for (int step = SeasonPhase.firstStep(SubSeason.MID_SPRING); step < SeasonPhase.firstStep(SubSeason.LATE_AUTUMN); step++) {
				assertEquals(summer, FoliageCurve.color(s, step, 0, GRAD), s + " step " + step);
			}
			int winter = FoliageCurve.color(s, SeasonPhase.firstStep(SubSeason.MID_WINTER), 0, GRAD);
			double k = (double) g(winter) / g(summer);
			assertTrue(k < 0.97 && k > 0.85, s + " winter darkening " + k);
		}
	}

	@Test
	void summerColorsOfTheSpeciesDiffer() {
		List<Species> all = new ArrayList<>(FoliageCurve.species());
		for (int i = 0; i < all.size(); i++) {
			for (int j = i + 1; j < all.size(); j++) {
				int a = FoliageCurve.summer(all.get(i), GRAD);
				int c = FoliageCurve.summer(all.get(j), GRAD);
				assertTrue(dist(a, c) >= 4, all.get(i).id() + " " + Integer.toHexString(a) + " / " + all.get(j).id() + " "
						+ Integer.toHexString(c));
			}
		}
		// Spring: young leaves lighter than the summer leaves.
		int young = FoliageCurve.colorAt(Species.BIRCH, 5.5, GRAD);
		assertTrue(g(young) > g(FoliageCurve.summer(Species.BIRCH, GRAD)) + 15);
	}

	@Test
	void stepsOfTheYear() {
		assertEquals(0, SeasonPhase.stepAt(0.0));
		assertEquals(SeasonPhase.STEPS - 1, SeasonPhase.stepAt(Math.nextDown(1.0)));
		assertEquals(SubSeason.MID_AUTUMN, SeasonPhase.subSeason(SeasonPhase.firstStep(SubSeason.MID_AUTUMN) + 2));
		assertEquals(3, SeasonPhase.STEPS_PER_SUB_SEASON);
	}
}
