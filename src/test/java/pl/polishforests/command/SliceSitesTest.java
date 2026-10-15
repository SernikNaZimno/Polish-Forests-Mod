package pl.polishforests.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import pl.polishforests.worldgen.habitat.Association;
import pl.polishforests.worldgen.habitat.Habitat;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.habitat.HabitatClassifier;
import pl.polishforests.worldgen.landscape.LandscapeScale;

/**
 * Fixed places of the vertical slice (step V0, docs/04-pionowy-wycinek.md §3): two oak-hornbeam forests, two fresh pine
 * forests and a transect fresh pine forest → mixed pine-oak forest → mixed forest → oak-hornbeam forest in each scale, for
 * the seed of the in-game tests. The table lives in {@code src/test/resources/slice_sites.txt}; the in-game test
 * {@code -Pgametest=slice} and the {@code stages} measurement with {@code -Psites=slice} read the same file.
 *
 * <p>Each site is the center of a square of 100 × 100 blocks (the 1 ha plot of the norm card, step V4) whose columns have
 * the site's biome in both vegetation modes (in the PRESENT_DAY mode the square is forest), at least {@link #MIN_SHARE}
 * of a lattice of 5 blocks; the sites lie at least {@link #MIN_ORIGIN} blocks from the spawn area and {@link #MIN_APART}
 * from each other, so the 8 × 8 chunk areas of the stage measurement are new terrain, and the two sites of a biome
 * {@link #MIN_SAME_BIOME_APART} apart, so they are different stands. A transect is a straight line whose
 * columns pass the four forests in that order ({@link #transectProblem}). Columns of other biomes may interrupt it (water,
 * a bog in a hollow) up to {@link #MAX_OTHER} of the line and {@link #MAX_OTHER_RUN} blocks at a time. The sites and the
 * forests of a transect are the typical association of the biome on at least {@link #MIN_TYPICAL} of their columns (V0
 * review round 1: not the slope oak-hornbeam forest of a valley side).
 *
 * <p>The sites were found with {@link HabitatTargets#locate} (nearest place of the biome in the PRESENT_DAY mode from a
 * ring of origins) and a local search around each hit; {@code ./gradlew test --tests '*SliceSitesTest*' -PfindSliceSites}
 * searches again and writes the file. The usual run only checks the table.
 */
class SliceSitesTest {
	static final String RESOURCE = "/slice_sites.txt";
	static final String CAMERAS = "/slice_cameras.txt";
	/** Least share of the 100 × 100 square with the site's biome, in each mode. */
	static final double MIN_SHARE = 0.95;
	/**
	 * Least share of the 100 × 100 square with the site's biome in its typical form ({@link Association#TYPICAL}), in each
	 * mode (V0 review round 1): the reference plots show the typical association the norms of docs/04 §6 describe (e.g.
	 * Tilio-Carpinetum typicum), not a subassociation such as the slope oak-hornbeam forest of a valley side.
	 */
	static final double MIN_TYPICAL = 0.9;
	/** Least distance of a site (and of the middle of a transect) from (0, 0), blocks: outside the chunks of the world start. */
	static final double MIN_ORIGIN = 640;
	/** Least distance between two places of a scale, blocks: separate 8 × 8 measurement areas with their neighbors. */
	static final double MIN_APART = 320;
	/** Least distance between the two sites of one biome, blocks per scale (realistic, gameplay): two different stands. */
	static final double[] MIN_SAME_BIOME_APART = {1_500, 400};
	/** Largest share of a transect in other biomes. */
	static final double MAX_OTHER = 0.15;
	/** Longest stretch of a transect in other biomes, blocks per scale (realistic, gameplay). */
	static final int[] MAX_OTHER_RUN = {24, 12};
	/** Least length of each of the four forests along a transect, blocks per scale. */
	static final int[] MIN_RUN = {40, 16};
	/** Shortest forest of a transect that ends the search, blocks per scale (otherwise the best one of all windows). */
	static final int[] GOOD_RUN = {80, 30};
	/** Largest lengthening of a transect at each end, deeper into the end forests, blocks per scale. */
	static final int[] EXTEND = {120, 40};
	/** Largest transect length, blocks per scale. */
	static final int[] MAX_LENGTH = {2_400, 800};
	static final List<HabitatBiome> SEQUENCE = List.of(HabitatBiome.FRESH_PINE_FOREST, HabitatBiome.MIXED_PINE_FOREST,
			HabitatBiome.MIXED_FOREST, HabitatBiome.OAK_HORNBEAM_FOREST);

	/** A place of the table: a site (one biome, a center) or a transect (start and end; biome null). */
	record Place(LandscapeScale scale, String modes, String name, HabitatBiome biome, int x, int z, int x2, int z2) {
		boolean transect() {
			return biome == null;
		}

		List<HabitatClassifier.Mode> modeList() {
			return switch (modes) {
				case "both" -> List.of(HabitatClassifier.Mode.NATURAL, HabitatClassifier.Mode.PRESENT_DAY);
				case "natural" -> List.of(HabitatClassifier.Mode.NATURAL);
				case "present_day" -> List.of(HabitatClassifier.Mode.PRESENT_DAY);
				default -> throw new IllegalArgumentException("modes " + modes);
			};
		}

		/** Middle of the place (the center of a site, the middle of a transect). */
		int[] middle() {
			return transect() ? new int[] {(x + x2) / 2, (z + z2) / 2} : new int[] {x, z};
		}

		String line() {
			return transect()
					? String.format(Locale.ROOT, "%s %s %s transect %d %d %d %d", scale.id(), modes, name, x, z, x2, z2)
					: String.format(Locale.ROOT, "%s %s %s %s %d %d", scale.id(), modes, name, biome.id(), x, z);
		}
	}

	static List<Place> read() throws IOException {
		List<Place> places = new ArrayList<>();
		try (InputStream in = SliceSitesTest.class.getResourceAsStream(RESOURCE)) {
			assertTrue(in != null, "missing " + RESOURCE);
			for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
				line = line.strip();
				if (line.isEmpty() || line.startsWith("#")) {
					continue;
				}
				String[] f = line.split("\\s+");
				LandscapeScale scale = f[0].equals("realistic") ? LandscapeScale.REALISTIC : LandscapeScale.GAMEPLAY;
				if (f[3].equals("transect")) {
					places.add(new Place(scale, f[1], f[2], null, Integer.parseInt(f[4]), Integer.parseInt(f[5]),
							Integer.parseInt(f[6]), Integer.parseInt(f[7])));
				} else {
					places.add(new Place(scale, f[1], f[2], HabitatBiome.byId(f[3]), Integer.parseInt(f[4]), Integer.parseInt(f[5]), 0, 0));
				}
			}
		}
		return places;
	}

	// ------------------------------------------------------------------ checks

	@Test
	void sitesHoldTheirForestInBothModes() throws IOException {
		List<String> problems = new ArrayList<>();
		for (Place p : read()) {
			for (HabitatClassifier.Mode mode : p.modeList()) {
				HabitatTargets.World w = PolishForestsCommandsTest.world(p.scale(), mode);
				String problem = p.transect() ? transectProblem(w, p.x(), p.z(), p.x2(), p.z2()) : siteProblem(w, p.biome(), p.x(), p.z());
				if (problem != null) {
					problems.add(p.line() + " (" + mode + "): " + problem);
				}
			}
		}
		assertTrue(problems.isEmpty(), String.join("\n", problems));
	}

	@Test
	void everyScaleAndModeHasAllPlaces() throws IOException {
		List<Place> places = read();
		for (LandscapeScale scale : List.of(LandscapeScale.REALISTIC, LandscapeScale.GAMEPLAY)) {
			for (HabitatClassifier.Mode mode : HabitatClassifier.Mode.values()) {
				List<Place> here = places.stream().filter(p -> p.scale() == scale && p.modeList().contains(mode)).toList();
				assertEquals(2, here.stream().filter(p -> p.biome() == HabitatBiome.OAK_HORNBEAM_FOREST).count(), scale.id() + " " + mode);
				assertEquals(2, here.stream().filter(p -> p.biome() == HabitatBiome.FRESH_PINE_FOREST).count(), scale.id() + " " + mode);
				assertTrue(here.stream().anyMatch(Place::transect), scale.id() + " " + mode + ": no transect");
				for (int i = 0; i < here.size(); i++) {
					int[] a = here.get(i).middle();
					assertTrue(Math.hypot(a[0], a[1]) >= MIN_ORIGIN, here.get(i).line() + ": too close to the spawn");
					for (int j = i + 1; j < here.size(); j++) {
						int[] b = here.get(j).middle();
						double apart = here.get(i).biome() != null && here.get(i).biome() == here.get(j).biome()
								? MIN_SAME_BIOME_APART[scale == LandscapeScale.REALISTIC ? 0 : 1] : MIN_APART;
						assertTrue(Math.hypot(a[0] - b[0], a[1] - b[1]) >= apart, here.get(i).line() + " and " + here.get(j).line());
					}
				}
			}
		}
	}

	/**
	 * The fixed cameras of the in-game test (V0 review round 1, {@code src/test/resources/slice_cameras.txt}, written by
	 * {@code -Pgametest=slice -PfindSliceCameras}) cover every place: a site has eye, canopy and 30m (eye and canopy near
	 * its center), a transect the eye views of its four forests, both views of its three borders, 30m and, in the gameplay
	 * scale, overview; no camera belongs to a place missing from the table.
	 */
	@Test
	void camerasCoverEveryPlace() throws IOException {
		Map<String, Map<String, int[]>> cameras = new TreeMap<>();
		try (InputStream in = SliceSitesTest.class.getResourceAsStream(CAMERAS)) {
			assertTrue(in != null, "missing " + CAMERAS);
			for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
				line = line.strip();
				if (line.isEmpty() || line.startsWith("#")) {
					continue;
				}
				String[] f = line.split("\\s+");
				assertEquals(8, f.length, line);
				cameras.computeIfAbsent(f[0] + " " + f[1], k -> new TreeMap<>()).put(f[2],
						new int[] {Integer.parseInt(f[3]), Integer.parseInt(f[4]), Integer.parseInt(f[5])});
			}
		}
		List<String> problems = new ArrayList<>();
		List<String> keys = new ArrayList<>();
		for (Place p : read()) {
			String key = p.scale().id() + " " + p.name();
			keys.add(key);
			Map<String, int[]> here = cameras.getOrDefault(key, Map.of());
			List<String> needed = new ArrayList<>();
			if (p.transect()) {
				for (int r = 0; r < SEQUENCE.size(); r++) {
					needed.add("eye_" + SEQUENCE.get(r).id());
					if (r > 0) {
						String edge = "edge_" + SEQUENCE.get(r - 1).id() + "_" + SEQUENCE.get(r).id();
						needed.add(edge + "_eye");
						needed.add(edge + "_side");
					}
				}
				needed.add("30m");
				if (p.scale() == LandscapeScale.GAMEPLAY) {
					needed.add("overview");
				}
			} else {
				needed.addAll(List.of("eye", "canopy", "30m"));
				for (String near : List.of("eye", "canopy")) {
					int[] c = here.get(near);
					if (c != null && Math.hypot(c[0] - p.x(), c[2] - p.z()) > 20) {
						problems.add(key + ": " + near + " " + Math.round(Math.hypot(c[0] - p.x(), c[2] - p.z())) + " blocks from the center");
					}
				}
			}
			for (String n : needed) {
				if (!here.containsKey(n)) {
					problems.add(key + ": no camera " + n);
				}
			}
		}
		for (String key : cameras.keySet()) {
			if (!keys.contains(key)) {
				problems.add(key + ": cameras of a place missing from the table");
			}
		}
		assertTrue(problems.isEmpty(), String.join("\n", problems));
	}

	/**
	 * Null when the 100 × 100 square around (x, z) has the biome in at least {@link #MIN_SHARE} of the lattice points and
	 * its typical form in at least {@link #MIN_TYPICAL}.
	 */
	static String siteProblem(HabitatTargets.World w, HabitatBiome biome, int x, int z) {
		double[] share = shares(w, biome, x, z);
		if (share[0] < MIN_SHARE) {
			return String.format(Locale.ROOT, "%s only %.1f%% of the square", biome.id(), 100 * share[0]);
		}
		return share[1] >= MIN_TYPICAL ? null
				: String.format(Locale.ROOT, "%s typical only %.1f%% of the square", biome.id(), 100 * share[1]);
	}

	/** Shares of the 21 × 21 lattice of the square around (x, z): {the biome, the biome in its typical form}. */
	static double[] shares(HabitatTargets.World w, HabitatBiome biome, int x, int z) {
		int[] codes = IntStream.range(0, 21 * 21).parallel().map(i -> w.code(x - 50 + 5 * (i / 21), z - 50 + 5 * (i % 21))).toArray();
		long hits = IntStream.of(codes).filter(c -> Habitat.biome(c) == biome).count();
		long typical = IntStream.of(codes).filter(c -> Habitat.biome(c) == biome && Habitat.association(c) == Association.TYPICAL).count();
		return new double[] {hits / (21.0 * 21.0), typical / (21.0 * 21.0)};
	}

	/** Biomes and associations of the square (or of the transect line): shares of the lattice (of the line), for the report. */
	static String associations(HabitatTargets.World w, Place p) {
		Map<String, Integer> count = new TreeMap<>();
		int n = p.transect() ? (int) Math.ceil(Math.hypot(p.x2() - p.x(), p.z2() - p.z())) + 1 : 21 * 21;
		for (int i = 0; i < n; i++) {
			int c = p.transect()
					? w.code(Math.floor(p.x() + (p.x2() - p.x()) * (double) i / (n - 1)) + 0.5,
							Math.floor(p.z() + (p.z2() - p.z()) * (double) i / (n - 1)) + 0.5)
					: w.code(p.x() - 50 + 5 * (i / 21), p.z() - 50 + 5 * (i % 21));
			count.merge(Habitat.biome(c).id() + "/" + Habitat.association(c).name().toLowerCase(Locale.ROOT), 1, Integer::sum);
		}
		return count.entrySet().stream().map(e -> String.format(Locale.ROOT, "%s %.1f%%", e.getKey(), 100.0 * e.getValue() / n))
				.collect(Collectors.joining(", "));
	}

	/** Biome and association shares of every place, for docs/04 ({@code -PsliceReport}; prints only). */
	@Test
	@EnabledIfSystemProperty(named = "polishforests.slice.report", matches = "true")
	void reportAssociations() throws IOException {
		for (Place p : read()) {
			for (HabitatClassifier.Mode mode : p.modeList()) {
				System.out.println(p.line() + " (" + mode + "): " + associations(PolishForestsCommandsTest.world(p.scale(), mode), p));
			}
		}
	}

	/**
	 * Null when the columns of the line from (x1, z1) to (x2, z2), one per block, pass the four forests of
	 * {@link #SEQUENCE} in order, each for at least {@link #MIN_RUN} blocks, with other biomes on at most
	 * {@link #MAX_OTHER} of the line and {@link #MAX_OTHER_RUN} blocks at a time, and the forests in their typical
	 * association on at least {@link #MIN_TYPICAL} of their columns; otherwise the reason.
	 */
	static String transectProblem(HabitatTargets.World w, int x1, int z1, int x2, int z2) {
		int n = (int) Math.ceil(Math.hypot(x2 - x1, z2 - z1));
		int[] codes = new int[n + 1];
		IntStream.rangeClosed(0, n).parallel().forEach(i -> codes[i] = w.code(
				Math.floor(x1 + (x2 - x1) * (double) i / n) + 0.5, Math.floor(z1 + (z2 - z1) * (double) i / n) + 0.5));
		HabitatBiome[] line = new HabitatBiome[n + 1];
		int forest = 0;
		int typical = 0;
		for (int i = 0; i <= n; i++) {
			line[i] = Habitat.biome(codes[i]);
			if (SEQUENCE.contains(line[i])) {
				forest++;
				typical += Habitat.association(codes[i]) == Association.TYPICAL ? 1 : 0;
			}
		}
		String problem = sequenceProblem(line, w.model().scale() == LandscapeScale.REALISTIC ? 0 : 1);
		if (problem == null && typical < MIN_TYPICAL * forest) {
			problem = String.format(Locale.ROOT, "the forests typical only on %.1f%% of their columns", 100.0 * typical / forest);
		}
		return problem;
	}

	static String sequenceProblem(HabitatBiome[] line, int s) {
		int rank = 0;
		int[] run = new int[SEQUENCE.size()];
		int other = 0;
		int otherRun = 0;
		for (HabitatBiome b : line) {
			int r = SEQUENCE.indexOf(b);
			if (r < 0) {
				other++;
				if (++otherRun > MAX_OTHER_RUN[s]) {
					return "more than " + MAX_OTHER_RUN[s] + " blocks of other biomes at a time";
				}
				continue;
			}
			otherRun = 0;
			if (r < rank) {
				return "back from " + SEQUENCE.get(rank).id() + " to " + b.id();
			}
			rank = r;
			run[r]++;
		}
		for (int r = 0; r < run.length; r++) {
			if (run[r] < MIN_RUN[s]) {
				return SEQUENCE.get(r).id() + " only " + run[r] + " blocks";
			}
		}
		if (other > MAX_OTHER * line.length) {
			return String.format(Locale.ROOT, "%.1f%% other biomes", 100.0 * other / line.length);
		}
		return null;
	}

	// ------------------------------------------------------------------ search (-PfindSliceSites)

	@Test
	@EnabledIfSystemProperty(named = "polishforests.slice.find", matches = "true")
	void findSites() throws IOException {
		Path out = Path.of(System.getProperty("polishforests.slice.file", "src/test/resources/slice_sites.txt"));
		List<String> lines = new ArrayList<>();
		lines.add("# Fixed places of the vertical slice (step V0, docs/04-pionowy-wycinek.md §3), seed 20260927. Written by");
		lines.add("# ./gradlew test --tests '*SliceSitesTest*' -PfindSliceSites, checked by SliceSitesTest, read by the in-game");
		lines.add("# test -Pgametest=slice and by -Pgametest=stages -Psites=slice. Columns: scale, modes (both, natural or");
		lines.add("# present_day), name, biome and center x z (a 100 x 100 block square of the biome), or 'transect' and the");
		lines.add("# start and end x z of a line through fresh pine -> mixed pine-oak -> mixed -> oak-hornbeam forest.");
		for (LandscapeScale scale : List.of(LandscapeScale.REALISTIC, LandscapeScale.GAMEPLAY)) {
			HabitatTargets.World natural = PolishForestsCommandsTest.world(scale, HabitatClassifier.Mode.NATURAL);
			HabitatTargets.World presentDay = PolishForestsCommandsTest.world(scale, HabitatClassifier.Mode.PRESENT_DAY);
			List<int[]> taken = new ArrayList<>();
			for (HabitatBiome biome : List.of(HabitatBiome.OAK_HORNBEAM_FOREST, HabitatBiome.FRESH_PINE_FOREST)) {
				String prefix = biome == HabitatBiome.OAK_HORNBEAM_FOREST ? "oak_hornbeam_" : "fresh_pine_";
				for (int k = 1; k <= 2; k++) {
					int[] site = findSite(natural, presentDay, biome, taken, k == 2 ? taken.getLast() : null);
					assertTrue(site != null, scale.id() + ": no site of " + biome.id());
					taken.add(site);
					lines.add(new Place(scale, "both", prefix + k, biome, site[0], site[1], 0, 0).line());
					System.out.printf(Locale.ROOT, "%s %s%d: (%d, %d), %.0f blocks from the origin%n", scale.id(), prefix, k, site[0],
							site[1], Math.hypot(site[0], site[1]));
				}
			}
			int[] both = findTransect(natural, presentDay, taken);
			if (both != null) {
				lines.add(new Place(scale, "both", "transect", null, both[0], both[1], both[2], both[3]).line());
			} else {
				int[] n = findTransect(natural, null, taken);
				assertTrue(n != null, scale.id() + ": no transect in the natural mode");
				lines.add(new Place(scale, "natural", "transect", null, n[0], n[1], n[2], n[3]).line());
				taken.add(new int[] {(n[0] + n[2]) / 2, (n[1] + n[3]) / 2});
				int[] d = findTransect(presentDay, null, taken);
				assertTrue(d != null, scale.id() + ": no transect in the present-day mode");
				lines.add(new Place(scale, "present_day", "transect_present_day", null, d[0], d[1], d[2], d[3]).line());
			}
		}
		// LF line ends on every system, as the file is kept in git.
		Files.writeString(out, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
		System.out.println("written " + out.toAbsolutePath());
	}

	/**
	 * A site of the biome: from a ring of origins at growing distance, the nearest place of the biome in the PRESENT_DAY mode
	 * ({@link HabitatTargets#locate}), then the nearest lattice point around it whose square holds the biome in both modes.
	 */
	private static int[] findSite(HabitatTargets.World natural, HabitatTargets.World presentDay, HabitatBiome biome, List<int[]> taken,
			int[] sameBiome) {
		boolean real = natural.model().scale() == LandscapeScale.REALISTIC;
		double step = real ? 1_500 : 400;
		for (int ring = 1; ring <= 12; ring++) {
			for (int k = 0; k < 8; k++) {
				double a = Math.PI / 4 * k + ring * 0.37;
				double ox = ring * step * Math.cos(a);
				double oz = ring * step * Math.sin(a);
				double[] hit = HabitatTargets.locate(presentDay, biome, ox, oz, () -> false);
				if (hit == null) {
					continue;
				}
				List<int[]> avoid = new ArrayList<>(taken);
				if (sameBiome != null) {
					// The second site of the biome: the first one counts as a disc of MIN_SAME_BIOME_APART (several points).
					double r = MIN_SAME_BIOME_APART[real ? 0 : 1] - MIN_APART;
					for (int i = 0; i < 24; i++) {
						for (double f : new double[] {0.35, 0.7, 1.0}) {
							avoid.add(new int[] {(int) Math.round(sameBiome[0] + f * r * Math.cos(Math.PI / 12 * i)),
									(int) Math.round(sameBiome[1] + f * r * Math.sin(Math.PI / 12 * i))});
						}
					}
				}
				int[] site = refine(natural, presentDay, biome, (int) Math.round(hit[0]), (int) Math.round(hit[1]), avoid, real ? 600 : 200);
				if (site != null && (sameBiome == null
						|| Math.hypot(site[0] - sameBiome[0], site[1] - sameBiome[1]) >= MIN_SAME_BIOME_APART[real ? 0 : 1])) {
					return site;
				}
			}
		}
		return null;
	}

	private static int[] refine(HabitatTargets.World natural, HabitatTargets.World presentDay, HabitatBiome biome, int hx, int hz,
			List<int[]> taken, int radius) {
		List<int[]> candidates = new ArrayList<>();
		for (int dx = -radius; dx <= radius; dx += 8) {
			for (int dz = -radius; dz <= radius; dz += 8) {
				int x = hx + dx;
				int z = hz + dz;
				if (Math.hypot(dx, dz) <= radius && free(x, z, taken)) {
					candidates.add(new int[] {x, z});
				}
			}
		}
		candidates.sort(Comparator.comparingDouble(c -> Math.hypot(c[0] - hx, c[1] - hz)));
		for (int[] c : candidates) {
			// Cheap test first: the center and the four corners.
			if (Habitat.biome(presentDay.code(c[0], c[1])) != biome || Habitat.biome(presentDay.code(c[0] - 50, c[1] - 50)) != biome
					|| Habitat.biome(presentDay.code(c[0] + 50, c[1] + 50)) != biome
					|| Habitat.biome(presentDay.code(c[0] - 50, c[1] + 50)) != biome
					|| Habitat.biome(presentDay.code(c[0] + 50, c[1] - 50)) != biome) {
				continue;
			}
			if (siteProblem(presentDay, biome, c[0], c[1]) == null && siteProblem(natural, biome, c[0], c[1]) == null) {
				return c;
			}
		}
		return null;
	}

	private static boolean free(int x, int z, List<int[]> taken) {
		if (Math.hypot(x, z) < MIN_ORIGIN) {
			return false;
		}
		for (int[] t : taken) {
			if (Math.hypot(x - t[0], z - t[1]) < MIN_APART) {
				return false;
			}
		}
		return true;
	}

	/**
	 * A transect near the nearest mixed forest places from a ring of origins: a map of the biomes of the second world
	 * (or the primary one) with one column per {@code cell} blocks in a window around each, then from each fresh pine cell
	 * straight lines in 48 directions, kept when the coarse line passes the sequence; the 400 shortest are checked block by
	 * block (in both worlds), lengthened at both ends ({@link #extend}) and rated by their shortest forest. The search
	 * stops at the first window with a transect whose shortest forest has {@link #GOOD_RUN} blocks, otherwise it takes
	 * the best of all windows. Returns {x1, z1, x2, z2} or null.
	 */
	private static int[] findTransect(HabitatTargets.World primary, HabitatTargets.World second, List<int[]> taken) {
		boolean real = primary.model().scale() == LandscapeScale.REALISTIC;
		int s = real ? 0 : 1;
		int cell = real ? 8 : 4;
		int half = real ? 3_000 : 1_000;
		double step = real ? 2_000 : 500;
		int[] best = null;
		int bestRun = 0;
		search:
		for (int ring = 0; ring <= 8; ring++) {
			for (int k = 0; k < (ring == 0 ? 1 : 8); k++) {
				double a = Math.PI / 4 * k + ring * 0.37;
				double[] hit = HabitatTargets.locate(second != null ? second : primary, HabitatBiome.MIXED_FOREST,
						ring * step * Math.cos(a), ring * step * Math.sin(a), () -> false);
				if (hit == null) {
					continue;
				}
				int cx = (int) Math.round(hit[0]);
				int cz = (int) Math.round(hit[1]);
				int size = 2 * half / cell + 1;
				byte[] map = new byte[size * size];
				HabitatTargets.World w = second != null ? second : primary;
				IntStream.range(0, size * size).parallel().forEach(i -> {
					HabitatBiome b = Habitat.biome(w.code(cx - half + cell * (i / size) + 0.5, cz - half + cell * (i % size) + 0.5));
					map[i] = (byte) SEQUENCE.indexOf(b);
				});
				List<int[]> lines = coarseLines(map, size, cell, s);
				// A full order (length, then the cells), so the parallel search always checks the same lines (V0 review round 1).
				lines.sort(Comparator.<int[]>comparingInt(l -> l[4]).thenComparingInt(l -> l[0]).thenComparingInt(l -> l[1])
						.thenComparingInt(l -> l[2]).thenComparingInt(l -> l[3]));
				for (int[] l : lines.subList(0, Math.min(lines.size(), 400))) {
					int[] t = {cx - half + cell * l[0], cz - half + cell * l[1], cx - half + cell * l[2], cz - half + cell * l[3]};
					if (!free((t[0] + t[2]) / 2, (t[1] + t[3]) / 2, taken) || transectProblem(primary, t[0], t[1], t[2], t[3]) != null
							|| second != null && transectProblem(second, t[0], t[1], t[2], t[3]) != null) {
						continue;
					}
					t = extend(primary, second, t, s);
					int least = leastRun(primary, t);
					if (second != null) {
						least = Math.min(least, leastRun(second, t));
					}
					if (best == null || least > bestRun) {
						best = t;
						bestRun = least;
					}
				}
				if (best != null && bestRun >= GOOD_RUN[s]) {
					break search;
				}
			}
		}
		if (best != null) {
			System.out.printf(Locale.ROOT, "%s transect (%d, %d) -> (%d, %d), %d blocks, shortest forest %d blocks%n",
					primary.model().scale().id(), best[0], best[1], best[2], best[3], Math.round(Math.hypot(best[2] - best[0], best[3] - best[1])),
					bestRun);
		}
		return best;
	}

	/**
	 * The transect lengthened at both ends, deeper into the fresh pine forest before its start and into the oak-hornbeam
	 * forest after its end, up to {@link #EXTEND} blocks, while the columns (of both worlds) keep the end forest.
	 */
	private static int[] extend(HabitatTargets.World primary, HabitatTargets.World second, int[] t, int s) {
		double len = Math.hypot(t[2] - t[0], t[3] - t[1]);
		double ux = (t[2] - t[0]) / len;
		double uz = (t[3] - t[1]) / len;
		int back = 0;
		while (back < EXTEND[s] && keeps(primary, second, t[0] - ux * (back + 1), t[1] - uz * (back + 1), SEQUENCE.getFirst())) {
			back++;
		}
		int ahead = 0;
		while (ahead < EXTEND[s] && keeps(primary, second, t[2] + ux * (ahead + 1), t[3] + uz * (ahead + 1), SEQUENCE.getLast())) {
			ahead++;
		}
		return new int[] {(int) Math.round(t[0] - ux * back), (int) Math.round(t[1] - uz * back), (int) Math.round(t[2] + ux * ahead),
				(int) Math.round(t[3] + uz * ahead)};
	}

	private static boolean keeps(HabitatTargets.World primary, HabitatTargets.World second, double x, double z, HabitatBiome biome) {
		x = Math.floor(x) + 0.5;
		z = Math.floor(z) + 0.5;
		return typical(primary.code(x, z), biome) && (second == null || typical(second.code(x, z), biome));
	}

	private static boolean typical(int code, HabitatBiome biome) {
		return Habitat.biome(code) == biome && Habitat.association(code) == Association.TYPICAL;
	}

	/** Shortest stretch (blocks) of the four forests along the transect. */
	private static int leastRun(HabitatTargets.World w, int[] t) {
		int n = (int) Math.ceil(Math.hypot(t[2] - t[0], t[3] - t[1]));
		int[] run = new int[SEQUENCE.size()];
		for (int i = 0; i <= n; i++) {
			int r = SEQUENCE.indexOf(Habitat.biome(w.code(Math.floor(t[0] + (t[2] - t[0]) * (double) i / n) + 0.5,
					Math.floor(t[1] + (t[3] - t[1]) * (double) i / n) + 0.5)));
			if (r >= 0) {
				run[r]++;
			}
		}
		return IntStream.of(run).min().orElse(0);
	}

	/** Lines {i1, j1, i2, j2, length in cells} of the coarse map that pass the sequence (start in fresh pine, end in oak-hornbeam). */
	private static List<int[]> coarseLines(byte[] map, int size, int cell, int s) {
		int maxSteps = MAX_LENGTH[s] / cell;
		int minRun = Math.max(2, MIN_RUN[s] / cell);
		int maxOtherRun = Math.max(1, MAX_OTHER_RUN[s] / cell);
		List<int[]> found = java.util.Collections.synchronizedList(new ArrayList<>());
		IntStream.range(0, size * size).parallel().forEach(start -> {
			if (map[start] != 0) {
				return;
			}
			int i0 = start / size;
			int j0 = start % size;
			for (int d = 0; d < 48; d++) {
				double a = 2 * Math.PI * d / 48;
				double di = Math.cos(a);
				double dj = Math.sin(a);
				int rank = 0;
				int[] run = new int[4];
				int otherRun = 0;
				int other = 0;
				for (int t = 1; t <= maxSteps; t++) {
					int i = (int) Math.round(i0 + di * t);
					int j = (int) Math.round(j0 + dj * t);
					if (i < 0 || j < 0 || i >= size || j >= size) {
						break;
					}
					int r = map[i * size + j];
					if (r < 0) {
						other++;
						if (++otherRun > maxOtherRun) {
							break;
						}
						continue;
					}
					otherRun = 0;
					if (r < rank) {
						break;
					}
					rank = r;
					run[r]++;
					if (r == 3 && run[3] >= minRun) {
						if (run[0] >= minRun && run[1] >= minRun && run[2] >= minRun && other <= MAX_OTHER * t) {
							found.add(new int[] {i0, j0, i, j, t});
						}
						break;
					}
				}
			}
		});
		return new ArrayList<>(found);
	}
}
