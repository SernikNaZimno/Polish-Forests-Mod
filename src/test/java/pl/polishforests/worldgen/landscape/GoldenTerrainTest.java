package pl.polishforests.worldgen.landscape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.chunk.VerticalScale;

/**
 * Golden terrain test (M2, step S0). Computes a hash of the {@link ColumnSample} fields that existed in M1
 * (surface to 1e-6 m, waterLevel, waterKind, type, substrate, coverDepth to 1e-6 m), plus the Y of the
 * ground and water blocks in both vertical mappings, and compares it with the golden file
 * {@code src/test/resources/golden_terrain_m1.txt}. New fields added to the sample are not part of the hash.
 *
 * <p>Sets: 2 scales × 2 seeds at region slider 1.0 and one set at slider 0.5. Each has a 32 × 32 grid
 * over an area of about 19 macroregions and 17 patches of 16 × 16 at difficult places (coast, lagoon,
 * beach, cliff, river mouth, rivers, oxbow lake, lakes, kettle pond, peatland, mountain stream, Beskids,
 * summit with the upper montane belt, Foothills), 5376 columns in total. Each patch has a target (e.g. beach:
 * BEACH_SAND columns and the BEACH landform), checked when writing and at every comparison. Patch centers
 * are searched for only when writing the file; when comparing they are taken from the file.
 *
 * <p>On a difference the test computes the same columns with the frozen copy of the M1 code ({@code landscape.m1})
 * and prints how much the values differ (max |Δsurface|, number of columns with a different enum). A difference
 * of the order of 1e-12 m after reordering operations then clearly stands out from a real terrain change.
 *
 * <p>New golden file: {@code ./gradlew test --tests '*GoldenTerrainTest*' -PwriteGolden}.
 * It may be overwritten only for an intended terrain change described in the documentation.
 */
class GoldenTerrainTest {
	static final String RESOURCE = "/golden_terrain_m1.txt";
	static final long SEED_A = 20260927L;
	static final long SEED_B = -7_316_550_294_015_845_337L;
	/** Column sets: 2 scales × 2 seeds at slider 1.0 and one at region slider 0.5. */
	static final List<SetKey> SETS = List.of(
			new SetKey(LandscapeScale.REALISTIC, SEED_A, 1.0),
			new SetKey(LandscapeScale.REALISTIC, SEED_B, 1.0),
			new SetKey(LandscapeScale.GAMEPLAY, SEED_A, 1.0),
			new SetKey(LandscapeScale.GAMEPLAY, SEED_B, 1.0),
			new SetKey(LandscapeScale.REALISTIC, SEED_A, 0.5));
	static final String[] FIELDS = {"surface", "waterLevel", "waterKind", "type", "substrate", "coverDepth", "blocks"};
	/** Minimum number of columns in a set (M2 plan requirement). */
	static final int MIN_COLUMNS = 4096;

	/** Scale, seed and region slider of one set. */
	record SetKey(LandscapeScale scale, long seed, double regionScale) {
		String key() {
			return scale.id() + " " + seed + " " + regionScale;
		}

		LandscapeModel model() {
			return new LandscapeModel(seed, scale, regionScale);
		}

		pl.polishforests.worldgen.landscape.m1.LandscapeModel modelM1() {
			return new pl.polishforests.worldgen.landscape.m1.LandscapeModel(seed, scale == LandscapeScale.REALISTIC
					? pl.polishforests.worldgen.landscape.m1.LandscapeScale.REALISTIC
					: pl.polishforests.worldgen.landscape.m1.LandscapeScale.GAMEPLAY, regionScale);
		}
	}

	/** Column patch: n × n points every {@code step} m around the center (x, z). */
	record Patch(String name, int n, long x, long z, int step) {
		long px(int i) {
			return x + (long) (i - n / 2) * step;
		}

		long pz(int j) {
			return z + (long) (j - n / 2) * step;
		}

		int count() {
			return n * n;
		}
	}

	/** A set from the golden file: patches and their hashes. */
	record GoldenSet(String key, List<Patch> patches, Map<String, String> hashes) {
	}

	/**
	 * M1 column fields independent of the {@link ColumnSample} version (enums by name), so that the same hash
	 * is computed from the current model and from the frozen M1 copy, even when the sample gets new fields.
	 */
	record Column(double surface, int waterLevel, String waterKind, String type, String substrate, double coverDepth) {
		/**
		 * Name of the M1 {@link LandscapeType} constant hashed in the golden file. The enum got English names,
		 * the terrain did not change, so the hash keeps the M1 names (rename to English, docs/rename).
		 */
		static String m1TypeName(LandscapeType t) {
			return switch (t) {
				case OUTWASH_PLAIN -> "SANDR";
				case MORAINE_PLATEAU -> "WYSOCZYZNA_MORENOWA";
				case OLD_GLACIAL_PLAIN -> "ROWNINA_STAROGLACJALNA";
				case FOOTHILLS -> "POGORZE";
				case BESKIDS -> "BESKIDY";
				case COASTLAND -> "POBRZEZE";
				case SEA -> "MORZE";
			};
		}

		static Column of(ColumnSample s) {
			return new Column(s.surface(), s.waterLevel(), s.waterKind().name(), m1TypeName(s.type()), s.substrate().name(),
					s.coverDepth());
		}

		static Column of(pl.polishforests.worldgen.landscape.m1.ColumnSample s) {
			return new Column(s.surface(), s.waterLevel(), s.waterKind().name(), s.type().name(), s.substrate().name(),
					s.coverDepth());
		}

		/** As {@link ColumnSample#hasWater()} in M1. */
		boolean hasWater() {
			return waterLevel != ColumnSample.NO_WATER && waterLevel > (int) Math.floor(surface);
		}

		/** Y of the ground and water blocks as in {@code PolandChunkGenerator} (without clamping to the dimension bounds). */
		int[] blocks() {
			return new int[] {VerticalScale.REAL.topBlockY(surface),
					hasWater() ? VerticalScale.REAL.topBlockY(waterLevel) : Integer.MIN_VALUE,
					VerticalScale.GAMEPLAY.topBlockY(surface),
					hasWater() ? VerticalScale.GAMEPLAY.topBlockY(waterLevel) : Integer.MIN_VALUE};
		}
	}

	@Test
	void terrainMatchesM1() throws IOException {
		if (Boolean.getBoolean("polishforests.golden.write")) {
			write();
			return;
		}
		Map<String, GoldenSet> golden = read();
		assertEquals(SETS.size(), golden.size(), "golden file has the wrong number of sets");
		List<String> errors = new ArrayList<>();
		for (SetKey k : SETS) {
			GoldenSet expected = golden.get(k.key());
			assertNotNull(expected, "missing set " + k.key() + " in the golden file");
			int points = expected.patches().stream().mapToInt(Patch::count).sum();
			assertTrue(points >= MIN_COLUMNS, "set " + k.key() + " has only " + points + " columns");
			LandscapeModel m = k.model();
			List<ColumnSample[]> samples = samples(m, expected.patches());
			List<Column[]> cols = columns(samples);
			Map<String, String> actual = hashes(expected.patches(), cols);
			Set<String> changed = new LinkedHashSet<>();
			for (Map.Entry<String, String> e : expected.hashes().entrySet()) {
				String got = actual.get(e.getKey());
				if (!e.getValue().equals(got)) {
					errors.add(k.key() + " / " + e.getKey() + ": expected " + e.getValue() + ", got " + got);
					if (e.getKey().startsWith("patch ")) {
						changed.add(e.getKey().substring("patch ".length()));
					}
				}
			}
			if (!changed.isEmpty()) {
				errors.addAll(diagnose(k, expected, cols, changed));
			}
			// Patch targets and coverage after the hashes, so that a large terrain change shows the changed patches first.
			errors.addAll(checkTargets(m, k.key(), expected.patches(), samples));
		}
		assertTrue(errors.isEmpty(), "Terrain differs from M1 (" + errors.size() + " issues):\n" + String.join("\n", errors));
	}

	/**
	 * The frozen copy of the M1 code gives the same hashes as the golden file. Ensures that the copy is a faithful
	 * reference for diagnosing differences and for the cost comparisons in {@code SampleCostTest}.
	 */
	@Test
	void frozenM1CopyMatchesGolden() throws IOException {
		if (Boolean.getBoolean("polishforests.golden.write")) {
			return;
		}
		Map<String, GoldenSet> golden = read();
		List<String> errors = new ArrayList<>();
		for (SetKey k : SETS) {
			GoldenSet expected = golden.get(k.key());
			assertNotNull(expected, "missing set " + k.key() + " in the golden file");
			Map<String, String> actual = hashes(expected.patches(), samplesM1(k.modelM1(), expected.patches()));
			for (Map.Entry<String, String> e : expected.hashes().entrySet()) {
				if (!e.getValue().equals(actual.get(e.getKey()))) {
					errors.add(k.key() + " / " + e.getKey());
				}
			}
		}
		assertTrue(errors.isEmpty(), "The M1 copy (landscape.m1) differs from the golden file: a different JVM or CPU, "
				+ "a modified copy, or a golden file generated from different terrain:\n" + String.join("\n", errors));
	}

	/** On a hash mismatch: how much the changed patches differ from the frozen M1 copy. */
	private static List<String> diagnose(SetKey k, GoldenSet expected, List<Column[]> actual, Set<String> changed) {
		List<String> out = new ArrayList<>();
		List<Column[]> ref = samplesM1(k.modelM1(), expected.patches());
		if (!hashes(expected.patches(), ref).equals(expected.hashes())) {
			out.add(k.key() + ": WARNING, the frozen M1 copy does not match the golden file either, so the difference "
					+ "comes from the JVM or CPU (or from the golden file), not from a change in the model code");
		}
		for (int q = 0; q < expected.patches().size(); q++) {
			Patch p = expected.patches().get(q);
			if (!changed.contains(p.name())) {
				continue;
			}
			double dSurface = 0;
			double dCover = 0;
			int[] diff = new int[5];
			for (int i = 0; i < p.count(); i++) {
				Column a = actual.get(q)[i];
				Column b = ref.get(q)[i];
				dSurface = Math.max(dSurface, Math.abs(a.surface() - b.surface()));
				dCover = Math.max(dCover, Math.abs(a.coverDepth() - b.coverDepth()));
				diff[0] += a.waterLevel() != b.waterLevel() ? 1 : 0;
				diff[1] += a.waterKind().equals(b.waterKind()) ? 0 : 1;
				diff[2] += a.type().equals(b.type()) ? 0 : 1;
				diff[3] += a.substrate().equals(b.substrate()) ? 0 : 1;
				diff[4] += java.util.Arrays.equals(a.blocks(), b.blocks()) ? 0 : 1;
			}
			boolean numeric = dSurface < 1e-6 && dCover < 1e-6 && java.util.Arrays.stream(diff).sum() == 0;
			out.add(String.format(Locale.ROOT, "%s / %s versus the M1 copy: max|Δsurface| = %.3g m, max|ΔcoverDepth| = %.3g m,"
					+ " differing columns (of %d): waterLevel %d, waterKind %d, type %d, substrate %d, blocks %d%s", k.key(), p.name(),
					dSurface, dCover, p.count(), diff[0], diff[1], diff[2], diff[3], diff[4],
					numeric ? " -> numerical difference only (< 1e-6 m), the hash rounding hit a boundary" : ""));
		}
		return out;
	}

	/** Patch targets and coverage of the whole set; returns a list of issues. */
	static List<String> checkTargets(LandscapeModel m, String key, List<Patch> patches, List<ColumnSample[]> samples) {
		List<String> out = new ArrayList<>();
		Set<String> kinds = new LinkedHashSet<>();
		Set<String> types = new LinkedHashSet<>();
		Set<String> substrates = new LinkedHashSet<>();
		for (int q = 0; q < patches.size(); q++) {
			Patch p = patches.get(q);
			ColumnSample[] a = samples.get(q);
			for (ColumnSample s : a) {
				kinds.add(s.waterKind().name());
				types.add(s.type().name());
				substrates.add(s.substrate().name());
			}
			if (p.name().equals("grid")) {
				continue;
			}
			Site site = site(p.name());
			if (site == null) {
				out.add(key + ": unknown patch " + p.name());
			} else if (!site.target().test(m, p, a)) {
				out.add(key + " / " + p.name() + ": patch does not contain its target: " + site.description());
			}
		}
		for (WaterKind k : WaterKind.values()) {
			if (!kinds.contains(k.name())) {
				out.add(key + ": missing water " + k + " in the column set " + kinds);
			}
		}
		for (LandscapeType t : LandscapeType.values()) {
			if (!types.contains(t.name())) {
				out.add(key + ": missing type " + t + " in the column set " + types);
			}
		}
		for (Substrate s : Substrate.values()) {
			if (!substrates.contains(s.name())) {
				out.add(key + ": missing substrate " + s + " in the column set " + substrates);
			}
		}
		return out;
	}

	/**
	 * Patch samples, row by row. Computed in parallel as in the generator (C2ME), so the test also ensures
	 * that the result depends neither on the call order nor on the state of the model caches.
	 */
	static List<ColumnSample[]> samples(LandscapeModel m, List<Patch> patches) {
		List<ColumnSample[]> out = new ArrayList<>();
		for (Patch p : patches) {
			ColumnSample[] a = new ColumnSample[p.count()];
			IntStream.range(0, a.length).parallel().forEach(k -> a[k] = m.sample(p.px(k % p.n()), p.pz(k / p.n())));
			out.add(a);
		}
		return out;
	}

	/** The same columns computed with the frozen copy of the M1 code. */
	static List<Column[]> samplesM1(pl.polishforests.worldgen.landscape.m1.LandscapeModel m, List<Patch> patches) {
		List<Column[]> out = new ArrayList<>();
		for (Patch p : patches) {
			Column[] a = new Column[p.count()];
			IntStream.range(0, a.length).parallel()
					.forEach(k -> a[k] = Column.of(m.sample(p.px(k % p.n()), p.pz(k / p.n()))));
			out.add(a);
		}
		return out;
	}

	static List<Column[]> columns(List<ColumnSample[]> samples) {
		List<Column[]> out = new ArrayList<>();
		for (ColumnSample[] a : samples) {
			Column[] k = new Column[a.length];
			for (int i = 0; i < a.length; i++) {
				k[i] = Column.of(a[i]);
			}
			out.add(k);
		}
		return out;
	}

	/** Hashes of the patches, the fields and the total for a column set; keys in a fixed order. */
	static Map<String, String> hashes(List<Patch> patches, List<Column[]> samples) {
		MessageDigest all = sha();
		MessageDigest[] fields = new MessageDigest[FIELDS.length];
		for (int f = 0; f < fields.length; f++) {
			fields[f] = sha();
		}
		Map<String, String> out = new LinkedHashMap<>();
		for (int q = 0; q < patches.size(); q++) {
			MessageDigest local = sha();
			for (Column s : samples.get(q)) {
				byte[][] parts = fieldBytes(s);
				for (int f = 0; f < parts.length; f++) {
					local.update(parts[f]);
					all.update(parts[f]);
					fields[f].update(parts[f]);
				}
			}
			out.put("patch " + patches.get(q).name(), hex(local));
		}
		for (int f = 0; f < fields.length; f++) {
			out.put("field " + FIELDS[f], hex(fields[f]));
		}
		out.put("total", hex(all));
		return out;
	}

	/** M1 sample fields as bytes; enum names instead of ordinals, so that adding values breaks nothing. */
	static byte[][] fieldBytes(Column s) {
		ByteBuffer blocks = ByteBuffer.allocate(16);
		for (int y : s.blocks()) {
			blocks.putInt(y);
		}
		return new byte[][] {
				longBytes(Math.round(s.surface() * 1e6)),
				longBytes(s.waterLevel()),
				(s.waterKind() + ";").getBytes(StandardCharsets.UTF_8),
				(s.type() + ";").getBytes(StandardCharsets.UTF_8),
				(s.substrate() + ";").getBytes(StandardCharsets.UTF_8),
				longBytes(Math.round(s.coverDepth() * 1e6)),
				blocks.array()};
	}

	private static byte[] longBytes(long v) {
		return ByteBuffer.allocate(8).putLong(v).array();
	}

	private static MessageDigest sha() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new AssertionError(e);
		}
	}

	private static String hex(MessageDigest d) {
		return HexFormat.of().formatHex(d.digest());
	}

	// ---------------------------------------------------------------- reading and writing the golden file

	private static Map<String, GoldenSet> read() throws IOException {
		Map<String, GoldenSet> sets = new LinkedHashMap<>();
		try (InputStream in = GoldenTerrainTest.class.getResourceAsStream(RESOURCE)) {
			if (in == null) {
				fail("missing golden file " + RESOURCE + "; create it: ./gradlew test --tests '*GoldenTerrainTest*' -PwriteGolden");
			}
			BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
			GoldenSet current = null;
			String line;
			while ((line = r.readLine()) != null) {
				line = line.strip();
				if (line.isEmpty() || line.startsWith("#")) {
					continue;
				}
				String[] t = line.split("\\s+");
				switch (t[0]) {
					case "set" -> {
						// set <scale> <seed> [<region slider>]
						double regionScale = t.length > 3 ? Double.parseDouble(t[3]) : 1.0;
						current = new GoldenSet(t[1] + " " + Long.parseLong(t[2]) + " " + regionScale, new ArrayList<>(),
								new LinkedHashMap<>());
						sets.put(current.key(), current);
					}
					case "patch" -> {
						// patch <name> <n> <x> <z> <step> <hash>
						current.patches().add(new Patch(t[1], Integer.parseInt(t[2]), Long.parseLong(t[3]),
								Long.parseLong(t[4]), Integer.parseInt(t[5])));
						current.hashes().put("patch " + t[1], t[6]);
					}
					case "field" -> current.hashes().put("field " + t[1], t[2]);
					case "total" -> current.hashes().put("total", t[1]);
					default -> fail("unknown line in the golden file: " + line);
				}
			}
		}
		return sets;
	}

	private static void write() throws IOException {
		String target = System.getProperty("polishforests.golden.file");
		assertNotNull(target, "missing property polishforests.golden.file (set by build.gradle with -PwriteGolden)");
		StringBuilder sb = new StringBuilder();
		sb.append("# Golden terrain M1 (M2, step S0). SHA-256 hashes of M1 ColumnSample fields: surface (1e-6 m),\n");
		sb.append("# waterLevel, waterKind, type, substrate, coverDepth (1e-6 m) and blocks (Y of ground and water in both\n");
		sb.append("# vertical mappings). Generated by GoldenTerrainTest. Overwrite only for an intended terrain\n");
		sb.append("# change: ./gradlew test --tests '*GoldenTerrainTest*' -PwriteGolden\n");
		sb.append("# set <scale> <seed> <region slider>\n");
		sb.append("# patch <name> <n> <center x> <center z> <step in m> <hash of the n × n patch>\n");
		List<String> errors = new ArrayList<>();
		for (SetKey k : SETS) {
			List<String> missing = new ArrayList<>();
			List<Patch> patches = choosePatches(k.model(), missing);
			for (String name : missing) {
				errors.add(k.key() + ": site not found: " + name);
			}
			if (!missing.isEmpty()) {
				continue;
			}
			int points = patches.stream().mapToInt(Patch::count).sum();
			assertTrue(points >= MIN_COLUMNS);
			// Hashes and targets are computed by a fresh model, as in the comparison (without caches from the search).
			LandscapeModel fresh = k.model();
			List<ColumnSample[]> samples = samples(fresh, patches);
			errors.addAll(checkTargets(fresh, k.key(), patches, samples));
			Map<String, String> h = hashes(patches, columns(samples));
			sb.append('\n').append("set ").append(k.key()).append('\n');
			for (Patch p : patches) {
				sb.append(String.format(Locale.ROOT, "patch %s %d %d %d %d %s%n", p.name(), p.n(), p.x(), p.z(),
						p.step(), h.get("patch " + p.name())));
			}
			for (String f : FIELDS) {
				sb.append("field ").append(f).append(' ').append(h.get("field " + f)).append('\n');
			}
			sb.append("total ").append(h.get("total")).append('\n');
		}
		assertTrue(errors.isEmpty(), "Golden file not written:\n" + String.join("\n", errors));
		Path path = Path.of(target);
		Files.createDirectories(path.getParent());
		Files.writeString(path, sb.toString().replace("\r\n", "\n"), StandardCharsets.UTF_8);
		System.out.println("Saved the golden test file: " + path);
	}

	// ---------------------------------------------------------------- patch selection

	/** Where the spiral search for the patch center starts (COAST: the center of the "coast" patch). */
	enum Start {
		ZERO, COAST, BESKIDS, FOOTHILLS
	}

	/** Preliminary, cheap condition for a spiral point. */
	interface Pre {
		boolean test(LandscapeModel m, long x, long z, ColumnSample s);
	}

	/** Patch target: what the patch must contain. */
	interface Target {
		boolean test(LandscapeModel m, Patch p, ColumnSample[] a);
	}

	/**
	 * Patch site. The center is searched for on a spiral from the point {@code start} with the step {@code searchReal} or
	 * {@code searchGameplay}: a candidate is a point that meets {@code pre} or, when {@code border} is given,
	 * a point on the boundary of that condition (bisection between neighboring spiral points).
	 * A candidate wins when the patch around it meets {@code target}. Outside the mountains, candidates close
	 * to patches already chosen are skipped, so that e.g. the beach does not repeat the coast patch.
	 */
	record Site(String name, int stepReal, int stepGameplay, Start start, double searchReal, double searchGameplay,
			Pre pre, Predicate<ColumnSample> border, String description, Target target) {
	}

	private static final Predicate<ColumnSample> SEA_OPEN = s -> s.waterKind() == WaterKind.SEA
			&& s.type() == LandscapeType.SEA;
	private static final Predicate<ColumnSample> SEA = s -> s.waterKind() == WaterKind.SEA;
	private static final Predicate<ColumnSample> RIVER = s -> s.waterKind() == WaterKind.RIVER;

	static final List<Site> SITES = List.of(
			new Site("coast", 4, 4, Start.ZERO, 2_000, 100, null, SEA_OPEN,
					"open sea (SEA/SEA) and dry land, at least 16 columns of each",
					(m, p, a) -> count(a, SEA_OPEN) >= 16 && count(a, s -> !s.hasWater()) >= 16),
			new Site("lagoon", 4, 4, Start.ZERO, 500, 25,
					(m, x, z, s) -> s.waterKind() == WaterKind.SEA && s.type() == LandscapeType.COASTLAND, null,
					"lagoon (SEA/COASTLAND)",
					(m, p, a) -> count(a, s -> s.waterKind() == WaterKind.SEA && s.type() == LandscapeType.COASTLAND) > 0),
			new Site("beach", 6, 3, Start.ZERO, 1_700, 85, null, SEA,
					"beach: at least 8 BEACH_SAND columns and the BEACH landform",
					(m, p, a) -> count(a, s -> s.substrate() == Substrate.BEACH_SAND) >= 8
							&& hasForm(m, p, a, s -> s.substrate() == Substrate.BEACH_SAND, Landform.BEACH)),
			new Site("cliff", 10, 6, Start.ZERO, 2_300, 115, null, SEA, "cliff (CLIFF landform)",
					(m, p, a) -> hasForm(m, p, a, s -> s.type() == LandscapeType.COASTLAND && !s.hasWater()
							&& s.surface() > 8, Landform.CLIFF)),
			new Site("river_mouth", 4, 3, Start.COAST, 250, 15,
					(m, x, z, s) -> s.waterKind() == WaterKind.RIVER && m.coastDistance(x, z) < 3_000 * m.scale().meso(),
					null, "river mouth (RIVER with the RIVER_MOUTH landform)", (m, p, a) -> hasForm(m, p, a, RIVER, Landform.RIVER_MOUTH)),
			new Site("lowland_river", 3, 3, Start.ZERO, 500, 25,
					(m, x, z, s) -> s.waterKind() == WaterKind.RIVER && s.type().isLowland(), null, "river in the lowland",
					(m, p, a) -> count(a, s -> s.waterKind() == WaterKind.RIVER && s.type().isLowland()) > 0),
			new Site("large_river", 8, 3, Start.ZERO, 1_500, 60,
					(m, x, z, s) -> s.waterKind() == WaterKind.RIVER && s.type().isLowland(), null,
					"river of order ≥ 2 (RIVER landform) covering at least 1/8 of the patch",
					(m, p, a) -> count(a, RIVER) >= 32 && hasForm(m, p, a, RIVER, Landform.RIVER)),
			new Site("foothills_river", 3, 3, Start.FOOTHILLS, 250, 15,
					(m, x, z, s) -> s.waterKind() == WaterKind.RIVER && s.type() == LandscapeType.FOOTHILLS, null,
					"river of order ≥ 2 (RIVER landform) in the Foothills",
					(m, p, a) -> hasForm(m, p, a, s -> s.waterKind() == WaterKind.RIVER && s.type() == LandscapeType.FOOTHILLS,
							Landform.RIVER)),
			new Site("oxbow_lake", 3, 3, Start.ZERO, 500, 25, (m, x, z, s) -> s.waterKind() == WaterKind.OXBOW, null,
					"oxbow lake (OXBOW)", (m, p, a) -> count(a, s -> s.waterKind() == WaterKind.OXBOW) > 0),
			new Site("tunnel_valley_lake", 6, 6, Start.ZERO, 500, 25, (m, x, z, s) -> s.waterKind() == WaterKind.LAKE, null,
					"tunnel valley lake (LAKE)", (m, p, a) -> count(a, s -> s.waterKind() == WaterKind.LAKE) > 0),
			new Site("outwash_plain_lake", 6, 6, Start.ZERO, 2_500, 150,
					(m, x, z, s) -> s.type() == LandscapeType.OUTWASH_PLAIN
							&& (s.waterKind() == WaterKind.LAKE || s.waterKind() == WaterKind.KETTLE), null,
					"lake or kettle pond (LAKE, KETTLE) on the outwash plain",
					(m, p, a) -> count(a, s -> s.type() == LandscapeType.OUTWASH_PLAIN
							&& (s.waterKind() == WaterKind.LAKE || s.waterKind() == WaterKind.KETTLE)) > 0),
			new Site("kettle_pond", 4, 2, Start.ZERO, 500, 25,
					(m, x, z, s) -> s.waterKind() == WaterKind.KETTLE && s.type() == LandscapeType.MORAINE_PLATEAU, null,
					"kettle pond (KETTLE) on the moraine plateau",
					(m, p, a) -> count(a, s -> s.waterKind() == WaterKind.KETTLE) > 0),
			new Site("peatland", 4, 2, Start.ZERO, 250, 15, (m, x, z, s) -> s.substrate() == Substrate.PEAT, null,
					"peat (PEAT, KETTLE_BOG landform)", (m, p, a) -> hasForm(m, p, a, s -> s.substrate() == Substrate.PEAT,
							Landform.KETTLE_BOG)),
			new Site("mountain_stream", 2, 2, Start.BESKIDS, 20, 4,
					(m, x, z, s) -> s.waterKind() == WaterKind.RIVER && s.type() == LandscapeType.BESKIDS, null,
					"stream (STREAM landform) in the Beskids",
					(m, p, a) -> hasForm(m, p, a, s -> s.waterKind() == WaterKind.RIVER && s.type() == LandscapeType.BESKIDS,
							Landform.STREAM)),
			new Site("beskids", 60, 20, Start.BESKIDS, 5_000, 200, (m, x, z, s) -> true, null,
					"Beskids interior (at least 200 BESKIDS columns)",
					(m, p, a) -> count(a, s -> s.type() == LandscapeType.BESKIDS) >= 200),
			// At gameplay scale the Beskids rarely reach 1150 m (seed 20260927: at most about 1015 m within 8 km
			// of the interior), so there the patch only requires a summit above 900 m.
			new Site("summit", 20, 8, Start.BESKIDS, 200, 10,
					(m, x, z, s) -> s.type() == LandscapeType.BESKIDS && s.surface() >= highPeak(m), null,
					"summit (SUMMIT landform) above 900 m, at realistic scale above 1150 m with the UPPER_MONTANE landform",
					(m, p, a) -> hasForm(m, p, a, s -> s.type() == LandscapeType.BESKIDS && s.surface() >= highPeak(m),
							Landform.SUMMIT)
							&& (m.scale() != LandscapeScale.REALISTIC || hasForm(m, p, a, s -> s.type() == LandscapeType.BESKIDS
									&& s.surface() >= 1_150, Landform.UPPER_MONTANE))),
			new Site("foothills", 40, 12, Start.FOOTHILLS, 5_000, 200, (m, x, z, s) -> true, null,
					"Foothills interior (at least 200 FOOTHILLS columns)",
					(m, p, a) -> count(a, s -> s.type() == LandscapeType.FOOTHILLS) >= 200));

	static Site site(String name) {
		for (Site s : SITES) {
			if (s.name().equals(name)) {
				return s;
			}
		}
		return null;
	}

	/** Height threshold of the "summit" patch: the upper montane belt at realistic scale, 900 m at gameplay scale. */
	static double highPeak(LandscapeModel m) {
		return m.scale() == LandscapeScale.REALISTIC ? 1_150 : 900;
	}

	static int count(ColumnSample[] a, Predicate<ColumnSample> test) {
		int n = 0;
		for (ColumnSample s : a) {
			n += test.test(s) ? 1 : 0;
		}
		return n;
	}

	/** Whether the terrain description of any column that meets {@code test} contains the landform {@code form}. */
	static boolean hasForm(LandscapeModel m, Patch p, ColumnSample[] a, Predicate<ColumnSample> test, Landform form) {
		for (int k = 0; k < a.length; k++) {
			if (test.test(a[k]) && m.describe(p.px(k % p.n()), p.pz(k / p.n())).forms().contains(form)) {
				return true;
			}
		}
		return false;
	}

	/** A 32 × 32 grid and 16 × 16 patches from {@link #SITES}; names of sites not found go to {@code missing}. */
	static List<Patch> choosePatches(LandscapeModel m, List<String> missing) {
		List<Patch> out = new ArrayList<>();
		int gridStep = (int) Math.round(m.regionSize() * 0.6);
		out.add(new Patch("grid", 32, 0, 0, gridStep));
		Map<Start, double[]> starts = new LinkedHashMap<>();
		starts.put(Start.ZERO, new double[] {0, 0});
		starts.put(Start.BESKIDS, LandscapePreview.findInterior(m, LandscapeType.BESKIDS));
		starts.put(Start.FOOTHILLS, LandscapePreview.findInterior(m, LandscapeType.FOOTHILLS));
		for (Site site : SITES) {
			double[] start = starts.get(site.start());
			long t0 = System.nanoTime();
			Patch p = start == null ? null : search(m, site, start, out);
			String time = String.format(Locale.ROOT, "%.1f s", (System.nanoTime() - t0) / 1e9);
			if (p == null) {
				System.out.println("Golden test: site not found: " + site.name() + " (" + time + ")");
				missing.add(site.name());
			} else {
				System.out.println("Golden test: " + site.name() + " x=" + p.x() + " z=" + p.z() + " (" + time + ")");
				out.add(p);
				if (site.start() == Start.ZERO && site.name().equals("coast")) {
					starts.put(Start.COAST, new double[] {p.x(), p.z()});
				}
			}
		}
		return out;
	}

	/** Spiral search for the patch center (see {@link Site}). */
	static Patch search(LandscapeModel m, Site site, double[] start, List<Patch> chosen) {
		boolean real = m.scale() == LandscapeScale.REALISTIC;
		double avoid = real ? 1_000 : 100;
		double step = real ? site.searchReal() : site.searchGameplay();
		int patchStep = real ? site.stepReal() : site.stepGameplay();
		for (int r = 0; r < 800; r++) {
			int n = Math.max(12, r * 2);
			long px = 0;
			long pz = 0;
			boolean prev = false;
			for (int k = 0; k < n; k++) {
				double a = k * (2 * Math.PI / n) + r * 0.37;
				long x = Math.round(start[0] + Math.cos(a) * r * step);
				long z = Math.round(start[1] + Math.sin(a) * r * step);
				ColumnSample s = m.sample(x, z);
				long[] candidate = null;
				if (site.border() != null) {
					boolean in = site.border().test(s);
					if (k > 0 && in != prev) {
						candidate = in ? bisect(m, site.border(), x, z, px, pz) : bisect(m, site.border(), px, pz, x, z);
					}
					prev = in;
				} else if (site.pre().test(m, x, z, s)) {
					candidate = new long[] {x, z};
				}
				px = x;
				pz = z;
				if (candidate != null && (site.start() == Start.ZERO || site.start() == Start.COAST)
						&& near(chosen, candidate, avoid)) {
					candidate = null;
				}
				if (candidate != null) {
					Patch p = new Patch(site.name(), 16, candidate[0], candidate[1], patchStep);
					if (site.target().test(m, p, samples(m, List.of(p)).get(0))) {
						return p;
					}
				}
			}
		}
		return null;
	}

	private static boolean near(List<Patch> chosen, long[] c, double distance) {
		for (Patch p : chosen) {
			if (!p.name().equals("grid") && Math.hypot(p.x() - c[0], p.z() - c[1]) < distance) {
				return true;
			}
		}
		return false;
	}

	/** A point on the boundary of the condition between (ix, iz), where it holds, and (ox, oz), where it does not. */
	private static long[] bisect(LandscapeModel m, Predicate<ColumnSample> test, long ix, long iz, long ox, long oz) {
		double ax = ix;
		double az = iz;
		double bx = ox;
		double bz = oz;
		while (Math.hypot(bx - ax, bz - az) > 1) {
			double mx = (ax + bx) / 2;
			double mz = (az + bz) / 2;
			if (test.test(m.sample(Math.round(mx), Math.round(mz)))) {
				ax = mx;
				az = mz;
			} else {
				bx = mx;
				bz = mz;
			}
		}
		return new long[] {Math.round(ax), Math.round(az)};
	}
}
