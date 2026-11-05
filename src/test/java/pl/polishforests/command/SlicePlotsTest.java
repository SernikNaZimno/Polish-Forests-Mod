package pl.polishforests.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.habitat.HabitatClassifier;
import pl.polishforests.worldgen.landscape.LandscapeScale;
import pl.polishforests.worldgen.tree.AgeClass;
import pl.polishforests.worldgen.tree.ForestStandMap;

/**
 * Sample plots of the norms card by stand (step V9 of docs/04-pionowy-wycinek.md §6): the card has columns for managed
 * and natural forests, and the stand map ({@link ForestStandMap}) decides which a place is, so the in-game census also
 * counts plots chosen in stands of each kind: per scale two natural and two managed oak-hornbeam forests, two natural
 * fresh pine forests and a managed fresh pine forest of each age class (its trunks per chunk have a norm by age class).
 * Step F1 (docs/04, "Kroki F"): each forest of the tree planner beyond the slice gets two natural and two managed plots
 * ({@link #F_FORESTS}, the lowland beech forest), searched around the nearest place of the forest from the first oak-hornbeam
 * and the first fresh pine site of the scale and appended after the plots of the slice, so these keep their places.
 * The table lives in {@code src/test/resources/slice_plots.txt}; {@code -Pgametest=slice} reads it.
 *
 * <p>A plot is the center of a 100 × 100 block square that holds its biome as a slice site does
 * ({@link SliceSitesTest#siteProblem} in both vegetation modes, the biome on at least {@value #MIN_BIOME} of the lattice) and lies in stands of its kind on at least
 * {@value #MIN_STAND} of a lattice of 5 blocks (a managed plot of a given age class: of that age class; a managed plot
 * wholly in managed stands), with the default managed share of 85%. {@code ./gradlew test --tests '*SlicePlotsTest*' -PfindSlicePlots} searches the plots again
 * around the slice sites and writes the file; the usual run checks the table.
 */
@Tag("slow")
class SlicePlotsTest {
	static final String RESOURCE = "/slice_plots.txt";
	/**
	 * Least share of the plot's lattice in stands of its kind (and age class, for a managed plot with one); a managed plot
	 * has no natural stand at all (its canopy gaps would count in a plot of a managed forest, which has none).
	 */
	static final double MIN_STAND = 0.85;
	/**
	 * Least share of the plot's lattice in its biome, in each vegetation mode: a sample plot lies inside the forest (the
	 * slice sites allow 5% of other habitats, whose edges would open the canopy of a plot).
	 */
	static final double MIN_BIOME = 0.98;
	/** Least distance between two plots, blocks. */
	static final int MIN_APART = 130;
	static final double MANAGED_SHARE = 0.85;
	/** The forests of the planner of the steps F with plots of their own (step F1: the lowland beech forest). */
	static final List<HabitatBiome> F_FORESTS = List.of(HabitatBiome.LOWLAND_BEECH_FOREST);

	/** A plot: scale, stand kind (natural or managed), age class (null: any), name, biome, center. */
	record Plot(LandscapeScale scale, boolean natural, AgeClass age, String name, HabitatBiome biome, int x, int z) {
		String line() {
			return String.format(Locale.ROOT, "%s %s %s %s %s %d %d", scale.id(), natural ? "natural" : "managed",
					age == null ? "any" : age.name().toLowerCase(Locale.ROOT), name, biome.id(), x, z);
		}
	}

	static List<Plot> read() throws IOException {
		List<Plot> plots = new ArrayList<>();
		try (InputStream in = SlicePlotsTest.class.getResourceAsStream(RESOURCE)) {
			assertTrue(in != null, "missing " + RESOURCE);
			for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
				line = line.strip();
				if (line.isEmpty() || line.startsWith("#")) {
					continue;
				}
				String[] f = line.split("\\s+");
				LandscapeScale scale = f[0].equals("realistic") ? LandscapeScale.REALISTIC : LandscapeScale.GAMEPLAY;
				AgeClass age = f[2].equals("any") ? null : AgeClass.valueOf(f[2].toUpperCase(Locale.ROOT));
				plots.add(new Plot(scale, f[1].equals("natural"), age, f[3], HabitatBiome.byId(f[4]), Integer.parseInt(f[5]),
						Integer.parseInt(f[6])));
			}
		}
		return plots;
	}

	/** Share of the 21 × 21 lattice of the square around (x, z) in stands of the kind (and age class). */
	static double standShare(ForestStandMap map, boolean natural, AgeClass age, int x, int z) {
		int hits = 0;
		for (int a = 0; a < 21; a++) {
			for (int b = 0; b < 21; b++) {
				ForestStandMap.Stand s = map.stand(x - 50 + 5 * a, z - 50 + 5 * b);
				hits += s.natural() == natural && (age == null || s.age() == age) ? 1 : 0;
			}
		}
		return hits / 441.0;
	}

	static String problem(Plot p) {
		ForestStandMap map = new ForestStandMap(PolishForestsCommandsTest.SEED, p.scale().local(), MANAGED_SHARE);
		double share = standShare(map, p.natural(), p.age(), p.x(), p.z());
		if (share < MIN_STAND || !p.natural() && standShare(map, false, null, p.x(), p.z()) < 1) {
			return String.format(Locale.ROOT, "only %.1f%% of the square in its stands", 100 * share);
		}
		for (HabitatClassifier.Mode mode : HabitatClassifier.Mode.values()) {
			HabitatTargets.World w = PolishForestsCommandsTest.world(p.scale(), mode);
			String problem = siteProblem(w, p.biome(), p.x(), p.z());
			if (problem != null) {
				return mode + ": " + problem;
			}
			double biome = SliceSitesTest.shares(w, p.biome(), p.x(), p.z())[0];
			if (biome < MIN_BIOME) {
				return String.format(Locale.ROOT, "%s: %s only %.1f%% of the square", mode, p.biome().id(), 100 * biome);
			}
		}
		return null;
	}

	/**
	 * {@link SliceSitesTest#siteProblem}, except for the forests of {@link #F_FORESTS}: their plots may lie in any form of
	 * the forest (the acid and the fertile beech forest have norms of their own, step F1), so only the biome share counts.
	 */
	static String siteProblem(HabitatTargets.World w, HabitatBiome biome, int x, int z) {
		if (!F_FORESTS.contains(biome)) {
			return SliceSitesTest.siteProblem(w, biome, x, z);
		}
		double share = SliceSitesTest.shares(w, biome, x, z)[0];
		return share >= SliceSitesTest.MIN_SHARE ? null : String.format(Locale.ROOT, "%s only %.1f%% of the square", biome.id(), 100 * share);
	}

	@Test
	void plotsHoldTheirForestAndStands() throws IOException {
		List<String> problems = new ArrayList<>();
		for (Plot p : read()) {
			String problem = problem(p);
			if (problem != null) {
				problems.add(p.line() + ": " + problem);
			}
		}
		assertTrue(problems.isEmpty(), String.join("\n", problems));
	}

	@Test
	void everyScaleHasThePlotsOfTheCard() throws IOException {
		List<Plot> plots = read();
		for (LandscapeScale scale : List.of(LandscapeScale.REALISTIC, LandscapeScale.GAMEPLAY)) {
			List<Plot> here = plots.stream().filter(p -> p.scale() == scale).toList();
			assertEquals(2, count(here, HabitatBiome.OAK_HORNBEAM_FOREST, true, null), scale.id());
			assertEquals(2, count(here, HabitatBiome.OAK_HORNBEAM_FOREST, false, null), scale.id());
			assertEquals(2, count(here, HabitatBiome.FRESH_PINE_FOREST, true, null), scale.id());
			for (AgeClass age : AgeClass.values()) {
				assertEquals(1, here.stream().filter(p -> p.biome() == HabitatBiome.FRESH_PINE_FOREST && !p.natural() && p.age() == age)
						.count(), scale.id() + " managed pine " + age);
			}
			assertEquals(1, count(here, HabitatBiome.OAK_HORNBEAM_FOREST, false, AgeClass.POLE_STAGE), scale.id());
			assertEquals(scale == LandscapeScale.REALISTIC ? 1 : 0, count(here, HabitatBiome.OAK_HORNBEAM_FOREST, true, AgeClass.POLE_STAGE),
					scale.id());
			for (HabitatBiome f : F_FORESTS) {
				assertEquals(2, count(here, f, true, null), scale.id() + " natural " + f.id());
				assertEquals(2, count(here, f, false, null), scale.id() + " managed " + f.id());
			}
			for (int i = 0; i < here.size(); i++) {
				for (int j = i + 1; j < here.size(); j++) {
					assertTrue(Math.hypot(here.get(i).x() - here.get(j).x(), here.get(i).z() - here.get(j).z()) >= MIN_APART,
							here.get(i).line() + " and " + here.get(j).line());
				}
			}
		}
	}

	/**
	 * Step F1 (KF-6): the place of the lowland beech forest in the flights of {@code ForestPerformance} (its constant
	 * {@code BEECH_SITE}, written as numbers for the M2 baseline) is the plot {@code lowland_beech_natural_1} of each scale.
	 */
	@Test
	void flightsOfThePerformanceTestUseTheBeechPlot() throws IOException {
		String source = Files.readString(Path.of("src/gametest/java/pl/polishforests/gametest/ForestPerformance.java"));
		StringBuilder expected = new StringBuilder("BEECH_SITE = {");
		for (LandscapeScale scale : List.of(LandscapeScale.REALISTIC, LandscapeScale.GAMEPLAY)) {
			Plot p = read().stream().filter(q -> q.scale() == scale && q.name().equals("lowland_beech_natural_1")).findFirst().orElseThrow();
			expected.append(scale == LandscapeScale.REALISTIC ? "" : ", ").append("{").append(p.x()).append(", ").append(p.z()).append("}");
		}
		expected.append("};");
		assertTrue(source.contains(expected), "ForestPerformance has no " + expected);
	}

	private static long count(List<Plot> plots, HabitatBiome biome, boolean natural, AgeClass age) {
		return plots.stream().filter(p -> p.biome() == biome && p.natural() == natural && p.age() == age).count();
	}

	// ------------------------------------------------------------------ the search

	@Test
	@EnabledIfSystemProperty(named = "polishforests.slice.plots.find", matches = "true")
	void findPlots() throws IOException {
		List<String> lines = new ArrayList<>();
		lines.add("# Sample plots of the norms card by stand (step V9, docs/04-pionowy-wycinek.md §6), seed 20260927, managed share");
		lines.add("# 85%. Written by ./gradlew test --tests '*SlicePlotsTest*' -PfindSlicePlots, checked by SlicePlotsTest, read by");
		lines.add("# the in-game test -Pgametest=slice. Columns: scale, stand kind, age class (any or the class of a plot of one");
		lines.add("# age class), name, biome and the center x z of a 100 x 100 block square of the biome in stands of that kind.");
		for (LandscapeScale scale : List.of(LandscapeScale.REALISTIC, LandscapeScale.GAMEPLAY)) {
			List<SliceSitesTest.Place> sites = SliceSitesTest.read().stream().filter(p -> p.scale() == scale && !p.transect()).toList();
			List<Plot> chosen = new ArrayList<>();
			ForestStandMap map = new ForestStandMap(PolishForestsCommandsTest.SEED, scale.local(), MANAGED_SHARE);
			HabitatTargets.World natural = PolishForestsCommandsTest.world(scale, HabitatClassifier.Mode.NATURAL);
			HabitatTargets.World presentDay = PolishForestsCommandsTest.world(scale, HabitatClassifier.Mode.PRESENT_DAY);
			for (HabitatBiome biome : List.of(HabitatBiome.OAK_HORNBEAM_FOREST, HabitatBiome.FRESH_PINE_FOREST)) {
				String prefix = biome == HabitatBiome.OAK_HORNBEAM_FOREST ? "oak_hornbeam" : "fresh_pine";
				List<int[]> origins = sites.stream().filter(p -> p.biome() == biome).map(p -> new int[] {p.x(), p.z()}).toList();
				List<Object[]> wanted = new ArrayList<>();
				wanted.add(new Object[] {true, null, prefix + "_natural_1"});
				wanted.add(new Object[] {true, null, prefix + "_natural_2"});
				if (biome == HabitatBiome.OAK_HORNBEAM_FOREST) {
					wanted.add(new Object[] {false, null, prefix + "_managed_1"});
					wanted.add(new Object[] {false, null, prefix + "_managed_2"});
				} else {
					for (AgeClass age : AgeClass.values()) {
						wanted.add(new Object[] {false, age, prefix + "_managed_" + age.name().toLowerCase(Locale.ROOT)});
					}
				}
				int k = 0;
				for (Object[] w : wanted) {
					int[] origin = origins.get(k++ % origins.size());
					int[] at = search(map, natural, presentDay, biome, (Boolean) w[0], (AgeClass) w[1], origin, chosen, scale);
					Plot p = new Plot(scale, (Boolean) w[0], (AgeClass) w[1], (String) w[2], biome, at[0], at[1]);
					chosen.add(p);
					lines.add(p.line());
					System.out.println("[plots] " + p.line());
				}
			}
			// The pole stage of the oak-hornbeam forest (review of V9, round 1), searched last so the plots above keep their
			// places: managed (norm of the a1 height of the pole stage) and natural (for the record: the card describes old
			// growth; only in the realistic scale: in the gameplay one the natural pole-stage stands, a seventh of the natural
			// ones, are too small for a plot of 1 ha within reach of the sites).
			List<int[]> origins = sites.stream().filter(p -> p.biome() == HabitatBiome.OAK_HORNBEAM_FOREST).map(p -> new int[] {p.x(), p.z()})
					.toList();
			int k = 0;
			for (boolean wantNatural : scale == LandscapeScale.REALISTIC ? new boolean[] {false, true} : new boolean[] {false}) {
				String name = "oak_hornbeam_" + (wantNatural ? "natural" : "managed") + "_pole_stage";
				int[] at = search(map, natural, presentDay, HabitatBiome.OAK_HORNBEAM_FOREST, wantNatural, AgeClass.POLE_STAGE,
						origins.get(k++ % origins.size()), chosen, scale);
				Plot p = new Plot(scale, wantNatural, AgeClass.POLE_STAGE, name, HabitatBiome.OAK_HORNBEAM_FOREST, at[0], at[1]);
				chosen.add(p);
				lines.add(p.line());
				System.out.println("[plots] " + p.line());
			}
			// Step F1: the forests of the planner beyond the slice, two natural and two managed plots each, around the places
			// of the forest nearest to a ring of origins (present-day mode, the rings of the slice sites' search), the first
			// place with a plot of the kind winning.
			for (HabitatBiome biome : F_FORESTS) {
				String prefix = biome.id().replace("_forest", "");
				List<int[]> from = origins(presentDay, biome, scale);
				for (boolean wantNatural : new boolean[] {true, false}) {
					for (int n = 1; n <= 2; n++) {
						String name = prefix + (wantNatural ? "_natural_" : "_managed_") + n;
						int[] at = searchAround(map, natural, presentDay, biome, wantNatural, from, chosen, scale);
						Plot p = new Plot(scale, wantNatural, null, name, biome, at[0], at[1]);
						chosen.add(p);
						lines.add(p.line());
						System.out.println("[plots] " + p.line());
					}
				}
			}
		}
		Path file = Path.of(System.getProperty("polishforests.slice.plots.file"));
		Files.writeString(file, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
	}

	/**
	 * The places of a forest nearest to a ring of origins ({@link HabitatTargets#locate} in the present-day mode, rings of
	 * 1,500 or 400 blocks as the slice sites' search, 30 or 12 rings), without repeats within 600 blocks (step F1).
	 */
	private static List<int[]> origins(HabitatTargets.World presentDay, HabitatBiome biome, LandscapeScale scale) {
		double step = scale == LandscapeScale.REALISTIC ? 1_500 : 400;
		List<int[]> out = new ArrayList<>();
		for (int ring = 0; ring <= (scale == LandscapeScale.REALISTIC ? 30 : 12); ring++) {
			for (int k = 0; k < (ring == 0 ? 1 : 8); k++) {
				double a = Math.PI / 4 * k + ring * 0.37;
				double[] hit = HabitatTargets.locate(presentDay, biome, ring * step * Math.cos(a), ring * step * Math.sin(a), () -> false);
				if (hit != null && out.stream().allMatch(o -> Math.hypot(o[0] - hit[0], o[1] - hit[1]) >= 600)) {
					out.add(new int[] {(int) Math.round(hit[0]), (int) Math.round(hit[1])});
				}
			}
		}
		return out;
	}

	/** The first plot of the kind found around the origins in turn (rings of up to 1,200 or 600 blocks; step F1). */
	private static int[] searchAround(ForestStandMap map, HabitatTargets.World natural, HabitatTargets.World presentDay,
			HabitatBiome biome, boolean wantNatural, List<int[]> origins, List<Plot> chosen, LandscapeScale scale) {
		int rings = scale == LandscapeScale.REALISTIC ? 60 : 30;
		for (int[] origin : origins) {
			int[] at = search(map, natural, presentDay, biome, wantNatural, null, origin, chosen, rings);
			if (at != null) {
				return at;
			}
		}
		throw new AssertionError("no plot of " + biome.id() + (wantNatural ? " natural" : " managed") + " around " + origins.size()
				+ " places of the forest");
	}

	/** The nearest plot center to the origin (on a grid of 20 blocks, in rings) that passes the checks. */
	private static int[] search(ForestStandMap map, HabitatTargets.World natural, HabitatTargets.World presentDay, HabitatBiome biome,
			boolean wantNatural, AgeClass age, int[] origin, List<Plot> chosen, LandscapeScale scale) {
		int[] at = search(map, natural, presentDay, biome, wantNatural, age, origin, chosen, scale == LandscapeScale.REALISTIC ? 300 : 120);
		if (at == null) {
			throw new AssertionError("no plot of " + biome.id() + (wantNatural ? " natural" : " managed " + age) + " near " + origin[0]
					+ ", " + origin[1]);
		}
		return at;
	}

	/** {@link #search} within the given rings; null when none passes. */
	private static int[] search(ForestStandMap map, HabitatTargets.World natural, HabitatTargets.World presentDay, HabitatBiome biome,
			boolean wantNatural, AgeClass age, int[] origin, List<Plot> chosen, int rings) {
		int step = 20;
		for (int r = 0; r <= rings; r++) {
			for (int a = -r; a <= r; a++) {
				for (int b = -r; b <= r; b++) {
					if (Math.max(Math.abs(a), Math.abs(b)) != r) {
						continue;
					}
					int x = origin[0] + a * step;
					int z = origin[1] + b * step;
					if (Math.hypot(x, z) < SliceSitesTest.MIN_ORIGIN) {
						continue;
					}
					boolean free = chosen.stream().allMatch(p -> Math.hypot(p.x() - x, p.z() - z) >= MIN_APART);
					if (!free || standShare(map, wantNatural, age, x, z) < MIN_STAND + 0.05
							|| !wantNatural && standShare(map, false, null, x, z) < 1) {
						continue;
					}
					if (siteProblem(natural, biome, x, z) == null && siteProblem(presentDay, biome, x, z) == null
							&& SliceSitesTest.shares(natural, biome, x, z)[0] >= MIN_BIOME
							&& SliceSitesTest.shares(presentDay, biome, x, z)[0] >= MIN_BIOME) {
						return new int[] {x, z};
					}
				}
			}
		}
		return null;
	}
}
