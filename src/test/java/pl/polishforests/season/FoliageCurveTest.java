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
 * yellow then brown, green-yellow), 2-3 color steps per autumn sub-season, the phase shift, the conifers' winter colors
 * (V7 review round 1: the pine yellower, the spruce darker), the distinct summer colors of the species (CIE76 ΔE), the
 * light twig haze of the leafless crowns and the dry brown of marcescent leaves only.
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

	/** CIE L*a*b* (D65) of an sRGB color. */
	static double[] lab(int c) {
		double[] rgb = new double[3];
		for (int i = 0; i < 3; i++) {
			double v = ((c >> (16 - 8 * i)) & 255) / 255.0;
			rgb[i] = v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
		}
		double x = (0.4124 * rgb[0] + 0.3576 * rgb[1] + 0.1805 * rgb[2]) / 0.95047;
		double y = 0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2];
		double z = (0.0193 * rgb[0] + 0.1192 * rgb[1] + 0.9505 * rgb[2]) / 1.08883;
		double fy = f(y);
		return new double[] {116 * fy - 16, 500 * (f(x) - fy), 200 * (fy - f(z))};
	}

	private static double f(double t) {
		return t > 0.008856 ? Math.cbrt(t) : 7.787 * t + 16.0 / 116;
	}

	/** CIE76 color difference. */
	static double deltaE(int a, int b) {
		double[] p = lab(a);
		double[] q = lab(b);
		return Math.sqrt((p[0] - q[0]) * (p[0] - q[0]) + (p[1] - q[1]) * (p[1] - q[1]) + (p[2] - q[2]) * (p[2] - q[2]));
	}

	private static double chroma(int c) {
		double[] p = lab(c);
		return Math.hypot(p[1], p[2]);
	}

	private static double hue(int c) {
		double[] p = lab(c);
		return Math.toDegrees(Math.atan2(p[2], p[1]));
	}

	/** HSV saturation. */
	private static double saturation(int c) {
		int max = Math.max(r(c), Math.max(g(c), b(c)));
		int min = Math.min(r(c), Math.min(g(c), b(c)));
		return max == 0 ? 0 : (max - min) / (double) max;
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
		// Hornbeam yellow in the 3rd decade of X, brown in the 1st decade of XI (step V13: 4 days earlier).
		int hbYellow = FoliageCurve.colorAt(Species.HORNBEAM, 23.0, GRAD);
		int hbBrown = FoliageCurve.colorAt(Species.HORNBEAM, 24.2, GRAD);
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
	void conifersKeepTheirHueAndChangeInWinter() {
		for (Species s : List.of(Species.SCOTS_PINE, Species.SPRUCE)) {
			int summer = FoliageCurve.summer(s, GRAD);
			for (int step = SeasonPhase.firstStep(SubSeason.MID_SPRING); step < SeasonPhase.firstStep(SubSeason.LATE_AUTUMN); step++) {
				assertEquals(summer, FoliageCurve.color(s, step, 0, GRAD), s + " step " + step);
			}
		}
		int mid = SeasonPhase.firstStep(SubSeason.MID_WINTER);
		// Norway spruce stays dark green and darkens a little.
		int spruce = FoliageCurve.summer(Species.SPRUCE, GRAD);
		double k = (double) g(FoliageCurve.color(Species.SPRUCE, mid, 0, GRAD)) / g(spruce);
		assertTrue(k < 0.97 && k > 0.85, "spruce winter darkening " + k);
		// Scots pine: a gray-green with some chroma (not a frosted teal), a yellower olive in winter, not darker.
		int pine = FoliageCurve.summer(Species.SCOTS_PINE, GRAD);
		int pineWinter = FoliageCurve.color(Species.SCOTS_PINE, mid, 0, GRAD);
		assertTrue(chroma(pine) >= 24 && chroma(pine) <= 32 && hue(pine) < 140, "pine chroma " + chroma(pine) + " hue " + hue(pine));
		assertTrue(hue(pineWinter) < hue(pine) - 6, "pine winter hue " + hue(pineWinter) + " vs " + hue(pine));
		assertTrue(lab(pineWinter)[0] >= lab(pine)[0] - 1, "pine not darker in winter");
		assertTrue(deltaE(pine, spruce) >= 10, "pine and spruce distinct");
	}

	/**
	 * Winter (V7 review round 1): the leafless crowns of every broadleaf species show a light, low-chroma twig haze close
	 * to the summer lightness; only the marcescent leaves of oak, hornbeam and beech keep a dry, warm brown.
	 */
	@Test
	void leaflessCrownsAreLightAndOnlyMarcescentLeavesStayBrown() {
		double winter = SeasonPhase.firstStep(SubSeason.MID_WINTER) + 1.5;
		double march = SeasonPhase.firstStep(SubSeason.EARLY_SPRING) + 1.5;
		for (Species s : FoliageCurve.species()) {
			if (FoliageCurve.conifer(s)) {
				continue;
			}
			double summerL = lab(FoliageCurve.summer(s, GRAD))[0];
			for (double at : new double[] {winter, march}) {
				int haze = FoliageCurve.colorAt(s, at, GRAD);
				assertTrue(lab(haze)[0] >= summerL - 8, s.id() + " twig haze too dark: " + Integer.toHexString(haze));
				assertTrue(chroma(haze) <= 20, s.id() + " twig haze too colorful: " + Integer.toHexString(haze));
				int dry = FoliageCurve.colorAt(s, at, true, GRAD);
				if (FoliageCurve.marcescent(s)) {
					assertTrue(r(dry) - b(dry) >= 40 && r(dry) > g(dry) && lab(dry)[0] >= 52, s.id() + " dry brown " + Integer.toHexString(dry));
					assertTrue(deltaE(dry, haze) >= 12, s.id() + " dry leaves distinct from the twig haze");
				} else {
					assertEquals(haze, dry, s.id() + " has no marcescent leaves");
				}
			}
		}
		assertTrue(FoliageCurve.marcescent(Species.OAK) && FoliageCurve.marcescent(Species.HORNBEAM) && FoliageCurve.marcescent(Species.BEECH));
		assertTrue(!FoliageCurve.marcescent(Species.BIRCH) && !FoliageCurve.marcescent(Species.LINDEN)
				&& !FoliageCurve.marcescent(Species.NORWAY_MAPLE) && !FoliageCurve.marcescent(Species.ASH));
		// The 3rd decade of April: birch, maple and hornbeam light green, the later species still leafless.
		double april = SeasonPhase.firstStep(SubSeason.MID_SPRING) + 2.5;
		for (Species s : List.of(Species.BIRCH, Species.NORWAY_MAPLE, Species.HORNBEAM)) {
			int c = FoliageCurve.colorAt(s, april, GRAD);
			assertTrue(g(c) > r(c) + 40 && lab(c)[0] > 70, s.id() + " young leaves " + Integer.toHexString(c));
		}
		for (Species s : List.of(Species.LINDEN, Species.ASH, Species.OAK)) {
			assertTrue(chroma(FoliageCurve.colorAt(s, april, GRAD)) <= 20, s.id() + " still leafless");
		}
	}

	/** Birch, linden and maple hold their clear autumn color until the leaves fall instead of fading into khaki. */
	@Test
	void goldenAutumnHoldsUntilLeafFall() {
		int birch = FoliageCurve.colorAt(Species.BIRCH, 23.5, GRAD);              // 3rd decade of X
		assertTrue(saturation(birch) >= 0.5 && r(birch) > 190, "birch " + Integer.toHexString(birch));
		int linden = FoliageCurve.colorAt(Species.LINDEN, 23.0, GRAD);
		assertTrue(saturation(linden) >= 0.5, "linden " + Integer.toHexString(linden));
		int maple = FoliageCurve.colorAt(Species.NORWAY_MAPLE, 23.8, GRAD);
		assertTrue(r(maple) > 190 && r(maple) - g(maple) > 40, "maple " + Integer.toHexString(maple));
		// A birch of phase 3 (one step early) in the 2nd decade of X is still yellow.
		int early = FoliageCurve.color(Species.BIRCH, SeasonPhase.firstStep(SubSeason.MID_AUTUMN) + 1, 3, GRAD);
		assertTrue(saturation(early) >= 0.5, "early birch " + Integer.toHexString(early));
	}

	/**
	 * Every two species differ in summer by at least ΔE 6 (CIE76) over the oak-hornbeam forest color, the pairs that grow
	 * together (hornbeam and beech, linden and ash, linden and maple, ash and oak) included (V7 review round 1).
	 */
	@Test
	void summerColorsOfTheSpeciesAreDistinct() {
		List<Species> all = new ArrayList<>(FoliageCurve.species());
		StringBuilder report = new StringBuilder();
		for (int i = 0; i < all.size(); i++) {
			for (int j = i + 1; j < all.size(); j++) {
				int a = FoliageCurve.summer(all.get(i), GRAD);
				int c = FoliageCurve.summer(all.get(j), GRAD);
				double d = deltaE(a, c);
				report.append(String.format(Locale.ROOT, "%s/%s %.1f; ", all.get(i).id(), all.get(j).id(), d));
				assertTrue(d >= 6, all.get(i).id() + " " + Integer.toHexString(a) + " / " + all.get(j).id() + " "
						+ Integer.toHexString(c) + ": dE " + d);
			}
		}
		System.out.println("[foliage] summer dE: " + report);
		// Hornbeam is a dull green, not the most saturated of the species.
		assertTrue(chroma(FoliageCurve.summer(Species.HORNBEAM, GRAD)) < chroma(FoliageCurve.summer(Species.BEECH, GRAD)) - 8);
		// Spring: young leaves lighter than the summer leaves.
		int young = FoliageCurve.colorAt(Species.BIRCH, 5.5, GRAD);
		assertTrue(g(young) > g(FoliageCurve.summer(Species.BIRCH, GRAD)) + 15);
	}

	/**
	 * Step V13 (T-D5, CP-B): the bare twigs. Every broadleaf species is leafless in mid-winter and in leaf in summer; the
	 * marcescent leaves and the conifers never show twigs; in the 1st decade of May (the start of LATE_SPRING, the date
	 * of a new world) only some oaks and ashes are still bare, from the 2nd decade of May at most a late ash; the leaf
	 * fall follows the calendar (ash, linden, birch, maple, then hornbeam and beech, oak last, at the end of XI).
	 */
	@Test
	void leaflessWindowsFollowTheCalendar() {
		int winter = SeasonPhase.firstStep(SubSeason.MID_WINTER) + 1;
		int summer = SeasonPhase.firstStep(SubSeason.MID_SUMMER) + 1;
		for (Species s : FoliageCurve.species()) {
			for (int phase = 0; phase < FoliageCurve.PHASES; phase++) {
				boolean conifer = FoliageCurve.conifer(s);
				assertEquals(!conifer, FoliageCurve.leafless(s, winter, phase, false), s.id() + " in winter, phase " + phase);
				assertTrue(!FoliageCurve.leafless(s, summer, phase, false), s.id() + " in summer");
				for (int step = 0; step < SeasonPhase.STEPS; step++) {
					assertTrue(!FoliageCurve.leafless(s, step, phase, true), s.id() + ": marcescent leaves never show twigs");
				}
			}
		}
		int may = SeasonPhase.firstStep(SubSeason.LATE_SPRING);
		StringBuilder report = new StringBuilder();
		for (Species s : FoliageCurve.species()) {
			report.append(String.format(Locale.ROOT, "%s %d/%d; ", s.id(), bare(s, may), bare(s, may + 1)));
		}
		System.out.println("[foliage] bare phases of 4 in the 1st / 2nd decade of May: " + report);
		for (Species s : List.of(Species.BIRCH, Species.HORNBEAM, Species.NORWAY_MAPLE)) {
			assertEquals(0, bare(s, may), s.id() + " leafed out by May: " + report);
		}
		for (Species s : List.of(Species.BEECH, Species.LINDEN, Species.OAK)) {
			assertTrue(bare(s, may) <= 1, s.id() + ": a few late trees only: " + report);
			assertEquals(0, bare(s, may + 1), s.id() + " leafed out by mid-May: " + report);
		}
		assertTrue(bare(Species.ASH, may) <= 2 && bare(Species.ASH, may + 1) <= 1, report.toString());
		// The 3rd decade of April: birch and hornbeam in leaf, oak and ash bare.
		assertTrue(!FoliageCurve.leaflessAt(Species.BIRCH, 5.5) && !FoliageCurve.leaflessAt(Species.HORNBEAM, 5.5));
		assertTrue(FoliageCurve.leaflessAt(Species.OAK, 5.5) && FoliageCurve.leaflessAt(Species.ASH, 5.5));
		// Leaf fall in the order of the calendar.
		assertTrue(FoliageCurve.leafFall(Species.ASH) < FoliageCurve.leafFall(Species.LINDEN));
		assertTrue(FoliageCurve.leafFall(Species.LINDEN) < FoliageCurve.leafFall(Species.BIRCH));
		assertTrue(FoliageCurve.leafFall(Species.BIRCH) < FoliageCurve.leafFall(Species.NORWAY_MAPLE));
		assertTrue(FoliageCurve.leafFall(Species.NORWAY_MAPLE) < FoliageCurve.leafFall(Species.HORNBEAM));
		assertTrue(FoliageCurve.leafFall(Species.BEECH) < FoliageCurve.leafFall(Species.OAK));
		assertTrue(FoliageCurve.leafFall(Species.OAK) < SeasonPhase.firstStep(SubSeason.EARLY_WINTER), "oak bare by December");
		assertTrue(Double.isNaN(FoliageCurve.leafFall(Species.SCOTS_PINE)));
	}

	/**
	 * Step V13 (CP-B: "in mid-autumn most crowns are green"): in the middle of MID_AUTUMN (the 2nd decade of X, the peak of
	 * the autumn colors in the Polish lowlands) most crowns of every broadleaf species but the oak have turned, and in the
	 * 3rd decade of X the oak too; in the 1st decade of X the early species (birch, linden, maple) have turned.
	 */
	@Test
	void midAutumnCrownsHaveTurned() {
		int first = SeasonPhase.firstStep(SubSeason.MID_AUTUMN);
		StringBuilder report = new StringBuilder();
		for (Species s : FoliageCurve.species()) {
			if (!FoliageCurve.conifer(s)) {
				report.append(String.format(Locale.ROOT, "%s %d/%d/%d; ", s.id(), turned(s, first), turned(s, first + 1),
						turned(s, first + 2)));
			}
		}
		System.out.println("[foliage] turned or bare phases of 4 in the 1st / 2nd / 3rd decade of X: " + report);
		for (Species s : List.of(Species.BIRCH, Species.LINDEN, Species.NORWAY_MAPLE)) {
			assertTrue(turned(s, first) >= 2, s.id() + ": " + report);
		}
		for (Species s : List.of(Species.BIRCH, Species.LINDEN, Species.NORWAY_MAPLE, Species.BEECH, Species.HORNBEAM)) {
			assertTrue(turned(s, first + 1) >= 3, s.id() + ": " + report);
		}
		// The ash drops its leaves green-yellow over the 2nd and 3rd decades of X.
		assertTrue(turned(Species.ASH, first + 1) >= 2, report.toString());
		assertTrue(turned(Species.OAK, first + 2) >= 3, report.toString());
	}

	/**
	 * Step V13: the twig color of a species is low in chroma; step Z2 (CP-C.2: winter too dark): it is a lighter, grayer
	 * version of the winter color of the curve (the twig haze), at least 10 L* lighter, of no higher chroma and of about
	 * the same hue, and at least as light as the summer leaves.
	 */
	@Test
	void twigColorIsALighterTwigHaze() {
		double winter = SeasonPhase.firstStep(SubSeason.MID_WINTER) + 1.5;
		for (Species s : FoliageCurve.species()) {
			if (FoliageCurve.conifer(s)) {
				continue;
			}
			int twigs = FoliageCurve.twigs(s, GRAD);
			int haze = FoliageCurve.colorAt(s, winter, GRAD);
			String id = s.id() + " twigs " + Integer.toHexString(twigs) + ", haze " + Integer.toHexString(haze);
			assertTrue(chroma(twigs) <= 20 && chroma(twigs) <= chroma(haze) + 0.5, id);
			assertTrue(lab(twigs)[0] >= lab(haze)[0] + 10, id);
			assertTrue(lab(twigs)[0] >= lab(FoliageCurve.summer(s, GRAD))[0], id);
			double dh = Math.abs(hue(twigs) - hue(haze));
			assertTrue(Math.min(dh, 360 - dh) <= 25 || chroma(haze) < 8, id);
		}
	}

	/** The phases of 4 of a species that show bare twigs at a step. */
	private static int bare(Species s, int step) {
		int n = 0;
		for (int phase = 0; phase < FoliageCurve.PHASES; phase++) {
			n += FoliageCurve.leafless(s, step, phase, false) ? 1 : 0;
		}
		return n;
	}

	/** The phases of 4 of a species whose crown has turned from the summer color or is bare at a step. */
	private static int turned(Species s, int step) {
		int n = 0;
		int summer = FoliageCurve.summer(s, GRAD);
		for (int phase = 0; phase < FoliageCurve.PHASES; phase++) {
			n += FoliageCurve.leafless(s, step, phase, false) || dist(FoliageCurve.color(s, step, phase, GRAD), summer) >= TURNED ? 1 : 0;
		}
		return n;
	}

	@Test
	void stepsOfTheYear() {
		assertEquals(0, SeasonPhase.stepAt(0.0));
		assertEquals(SeasonPhase.STEPS - 1, SeasonPhase.stepAt(Math.nextDown(1.0)));
		assertEquals(SubSeason.MID_AUTUMN, SeasonPhase.subSeason(SeasonPhase.firstStep(SubSeason.MID_AUTUMN) + 2));
		assertEquals(3, SeasonPhase.STEPS_PER_SUB_SEASON);
	}
}
