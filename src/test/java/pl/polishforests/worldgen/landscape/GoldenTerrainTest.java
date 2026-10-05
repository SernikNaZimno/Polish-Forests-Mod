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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.chunk.VerticalScale;
import pl.polishforests.worldgen.habitat.AltitudinalBelts;

/**
 * Golden terrain test (M2, step S0; terrain geometry fix, docs/m2/poprawka-geometrii.md). Computes a hash of the
 * {@link ColumnSample} fields that existed in M1 (surface to 1e-6 m, waterLevel, waterKind, type, substrate,
 * coverDepth to 1e-6 m), plus the Y of the ground and water blocks in both vertical mappings, and compares it with
 * a golden file. New fields added to the sample are not part of the hash.
 *
 * <p>Two golden files:
 * <ul>
 * <li>{@code golden_terrain_m1.txt}: the M1 terrain, never overwritten. Only {@link #frozenM1CopyMatchesGolden} reads
 * it, to check that the frozen copy of the M1 code ({@code landscape.m1}) is a faithful reference;</li>
 * <li>{@code golden_terrain_m2.txt}: the current model, read by {@link #terrainMatchesGolden} (with the M1 file as a
 * fallback when it is missing). It has the M1 patches plus control patches (step K0 of the geometry fix:
 * {@code outwash_plain_interior}, {@code moraine_plateau_interior}) and the summit of a large Beskid massif (step K2:
 * {@code great_massif}).</li>
 * </ul>
 *
 * <p>Sets: 2 scales × 2 seeds at region slider 1.0 and one set at slider 0.5. Each has a 32 × 32 grid
 * over an area of about 19 macroregions and 16 × 16 patches at difficult places (coast, lagoon,
 * beach, cliff, river mouth, rivers, oxbow lake, lakes, kettle pond, peatland, mountain stream, Beskids,
 * summit with the upper montane belt, Foothills) and in control interiors. Each patch has a target (e.g. beach:
 * BEACH_SAND columns and the BEACH landform), checked when writing and at every comparison. Patch centers
 * are searched for only when writing the file; when comparing they are taken from the file.
 *
 * <p>On a difference the test computes the same columns with the frozen copy of the M1 code ({@code landscape.m1})
 * and prints how much the values differ (max |Δsurface|, number of columns with a different enum). A difference
 * of the order of 1e-12 m after reordering operations then clearly stands out from a real terrain change.
 *
 * <p>Gradle properties (build.gradle):
 * <ul>
 * <li>{@code -PwriteGolden}: writes a new golden file ({@code -PgoldenFile=<file>}, by default the M2 file in
 * {@code src/test/resources}) instead of comparing. It may be overwritten only for an intended terrain change
 * described in the documentation;</li>
 * <li>{@code -PgoldenKeepCenters} (with {@code -PwriteGolden}): keeps the patch centers of the current golden file and
 * searches anew only for patches whose target is no longer met at the old center, and for new sites;</li>
 * <li>{@code -PgoldenAllow=<file>}: list of allowed changes, lines {@code <set> / <patch>} (patch hash may change) and
 * {@code <set> / <patch> target} (the patch may also lose its target), {@code <set> / coverage} (the set may lose a
 * water kind, type or substrate). {@code <set>} is a label such as {@code REAL A}, {@code GAMEPLAY B},
 * {@code REAL A 0.5}, a full set key or {@code *}. Allowed changes are printed as information;</li>
 * <li>{@code -PgoldenReport=<file>}: writes the changed patches and fields to a file in the same syntax.</li>
 * </ul>
 */
@Tag("slow")
class GoldenTerrainTest {
	/** Golden file of the frozen M1 copy ({@link #frozenM1CopyMatchesGolden}); never overwritten. */
	static final String RESOURCE_M1 = "/golden_terrain_m1.txt";
	/** Golden file of the current model ({@link #terrainMatchesGolden}); when missing, the M1 file is used. */
	static final String RESOURCE_CURRENT = "/golden_terrain_m2.txt";
	/** History written to the header of the M2 golden file. */
	static final List<String> HISTORY = List.of(
			"K0 (2026-10-03): the M1 patches unchanged (same centers and hashes as golden_terrain_m1.txt), new control",
			"    patches outwash_plain_interior and moraine_plateau_interior (centers with the largest margin, checked on",
			"    the prototype of the whole fix); terrain identical to M1",
			"K2 (2026-10-03): large Beskid massifs (M2-8); every earlier patch unchanged (same centers and hashes), new",
			"    patch great_massif at the summit of the massif nearest to (0, 0); after the review of K2 (dome also over",
			"    foothills cells, river geometry near the massifs) only the great_massif rows changed, REAL B searched anew");
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

		/** Short label used in allow lists and reports: {@code REAL A}, {@code GAMEPLAY B}, {@code REAL A 0.5}. */
		String label() {
			String s = (scale == LandscapeScale.REALISTIC ? "REAL" : "GAMEPLAY") + " "
					+ (seed == SEED_A ? "A" : seed == SEED_B ? "B" : Long.toString(seed));
			return regionScale == 1.0 ? s : s + " " + regionScale;
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

	/**
	 * The current model gives the hashes of the current golden file ({@link #RESOURCE_CURRENT}, or {@link #RESOURCE_M1}
	 * when it is missing), and every patch meets its target. Changes listed in {@code -PgoldenAllow} are reported
	 * as information only.
	 */
	@Test
	void terrainMatchesGolden() throws IOException {
		if (Boolean.getBoolean("polishforests.golden.write")) {
			write();
			return;
		}
		String resource = exists(RESOURCE_CURRENT) ? RESOURCE_CURRENT : RESOURCE_M1;
		Map<String, GoldenSet> golden = read(resource);
		Allow allow = Allow.load(System.getProperty("polishforests.golden.allow"));
		assertEquals(SETS.size(), golden.size(), "golden file " + resource + " has the wrong number of sets");
		List<String> errors = new ArrayList<>();
		List<String> allowed = new ArrayList<>();
		List<String> notes = new ArrayList<>();
		List<String> report = new ArrayList<>();
		for (SetKey k : SETS) {
			GoldenSet expected = golden.get(k.key());
			assertNotNull(expected, "missing set " + k.key() + " in the golden file " + resource);
			int points = expected.patches().stream().mapToInt(Patch::count).sum();
			assertTrue(points >= MIN_COLUMNS, "set " + k.key() + " has only " + points + " columns");
			LandscapeModel m = k.model();
			List<ColumnSample[]> samples = samples(m, expected.patches());
			List<Column[]> cols = columns(samples);
			Map<String, String> actual = hashes(expected.patches(), cols);
			Map<String, String> changed = new java.util.LinkedHashMap<>();
			List<String> changedFields = new ArrayList<>();
			for (Map.Entry<String, String> e : expected.hashes().entrySet()) {
				String got = actual.get(e.getKey());
				if (e.getValue().equals(got)) {
					continue;
				}
				String line = k.label() + " / " + e.getKey() + ": expected " + e.getValue() + ", got " + got;
				if (e.getKey().startsWith("patch ")) {
					String name = e.getKey().substring("patch ".length());
					changed.put(name, line);
					report.add(k.label() + " / " + name);
				} else {
					changedFields.add(e.getKey());
					report.add("# " + k.label() + " / " + e.getKey());
				}
			}
			if (changed.isEmpty() && !changedFields.isEmpty()) {
				// Field and total hashes are built from the patches, so they cannot change on their own.
				errors.add(k.label() + ": field hashes " + changedFields + " differ while all patch hashes match "
						+ "(an edited golden file?)");
			}
			Set<String> numericOnly = new LinkedHashSet<>();
			if (!changed.isEmpty()) {
				for (String d : diagnose(k, expected, cols, changed.keySet(), numericOnly)) {
					notes.add(d);
					report.add("# " + d);
				}
			}
			for (Map.Entry<String, String> e : changed.entrySet()) {
				String name = e.getKey();
				if (!allow.patch(k, name)) {
					errors.add(e.getValue());
				} else if (allow.numeric(k, name) && !numericOnly.contains(name)) {
					errors.add(e.getValue() + " (allowed only as a numerical change of the M1 state: below "
							+ NUMERIC_SURFACE + " m, no other block, water, type or substrate)");
				} else {
					allowed.add(e.getValue());
				}
			}
			// Patch targets and coverage after the hashes, so that a large terrain change shows the changed patches first.
			for (TargetIssue t : targetIssues(m, k.key(), expected.patches(), samples)) {
				report.add(k.label() + " / " + t.patch() + (t.patch().equals(TargetIssue.COVERAGE) ? "" : " target") + "   # "
						+ t.message());
				(allow.target(k, t.patch()) ? allowed : errors).add(t.message());
			}
		}
		writeReport(resource, allow, report);
		if (!allowed.isEmpty()) {
			System.out.println("Golden test: allowed changes (" + allow.source() + "):\n  " + String.join("\n  ", allowed));
		}
		if (!notes.isEmpty()) {
			System.out.println("Golden test: comparison with the frozen M1 copy:\n  " + String.join("\n  ", notes));
		}
		assertTrue(errors.isEmpty(), "Terrain differs from the golden file " + resource + " (" + errors.size()
				+ " issues outside the allow list " + allow.source() + "):\n" + String.join("\n", errors)
				+ (notes.isEmpty() ? "" : "\n" + String.join("\n", notes)));
	}

	/**
	 * The frozen copy of the M1 code gives the same hashes as the M1 golden file. Ensures that the copy is a faithful
	 * reference for diagnosing differences and for the cost comparisons in {@code SampleCostTest}. Always reads
	 * {@link #RESOURCE_M1}, whatever the current golden file is.
	 */
	@Test
	void frozenM1CopyMatchesGolden() throws IOException {
		if (Boolean.getBoolean("polishforests.golden.write")) {
			return;
		}
		Map<String, GoldenSet> golden = read(RESOURCE_M1);
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

	/**
	 * On a hash mismatch: how much the changed patches differ from the frozen M1 copy. The copy is compared only with
	 * the M1 golden file (patches with the same name, size, center and step), never with the current golden file:
	 * after an intended terrain change the current file differs from M1, which says nothing about the JVM.
	 */
	private static List<String> diagnose(SetKey k, GoldenSet expected, List<Column[]> actual, Set<String> changed,
			Set<String> numericOnly) throws IOException {
		List<String> out = new ArrayList<>();
		List<Column[]> ref = samplesM1(k.modelM1(), expected.patches());
		Map<String, String> refHashes = hashes(expected.patches(), ref);
		GoldenSet m1 = read(RESOURCE_M1).get(k.key());
		List<String> copyDiffers = new ArrayList<>();
		for (Patch p : expected.patches()) {
			if (m1 != null && m1.patches().contains(p)
					&& !m1.hashes().get("patch " + p.name()).equals(refHashes.get("patch " + p.name()))) {
				copyDiffers.add(p.name());
			}
		}
		if (!copyDiffers.isEmpty()) {
			out.add(k.label() + ": WARNING, the frozen M1 copy does not match the M1 golden file either " + copyDiffers
					+ ", so the difference comes from the JVM or CPU (or from the golden file), not from a change in the model code");
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
			boolean asM1 = dSurface < 1e-6 && dCover < 1e-6 && java.util.Arrays.stream(diff).sum() == 0;
			// Rounding is the explanation only when the current golden file expects the M1 hash of this patch (same
			// patch in the M1 file, same hash). Otherwise the patch is back at the M1 state, or the file expects a
			// different terrain, and the M1 copy says nothing about rounding.
			boolean expectsM1 = m1 != null && m1.patches().contains(p)
					&& m1.hashes().get("patch " + p.name()).equals(expected.hashes().get("patch " + p.name()));
			boolean numeric = expectsM1 && dSurface < NUMERIC_SURFACE && dCover < NUMERIC_SURFACE
					&& java.util.Arrays.stream(diff).sum() == 0;
			if (numeric) {
				numericOnly.add(p.name());
			}
			String verdict = !asM1
					? numeric ? " -> numerical change of the M1 state only (< " + NUMERIC_SURFACE + " m, the same blocks)" : ""
					: expectsM1 ? " -> numerical difference only (< 1e-6 m), the hash rounding hit a boundary"
					: " -> identical to the frozen M1 copy (to 1e-6 m), but the current golden file expects a different hash";
			out.add(String.format(Locale.ROOT, "%s / %s versus the M1 copy: max|Δsurface| = %.3g m, max|ΔcoverDepth| = %.3g m,"
					+ " differing columns (of %d): waterLevel %d, waterKind %d, type %d, substrate %d, blocks %d%s", k.label(), p.name(),
					dSurface, dCover, p.count(), diff[0], diff[1], diff[2], diff[3], diff[4], verdict));
		}
		return out;
	}

	/**
	 * Largest |Δsurface| and |ΔcoverDepth| (m) against the frozen M1 copy of a change allowed as numerical only
	 * ({@code numeric} in the allow list), with no column of another water level, water kind, type, substrate or block.
	 */
	static final double NUMERIC_SURFACE = 1e-3;

	/** A patch that does not meet its target, or ({@link #COVERAGE}) a set without some water kind, type or substrate. */
	record TargetIssue(String patch, String message) {
		static final String COVERAGE = "coverage";
	}

	/** Patch targets and coverage of the whole set; returns a list of issues. */
	static List<String> checkTargets(LandscapeModel m, String key, List<Patch> patches, List<ColumnSample[]> samples) {
		return targetIssues(m, key, patches, samples).stream().map(TargetIssue::message).toList();
	}

	/** Patch targets and coverage of the whole set, with the patch each issue belongs to. */
	static List<TargetIssue> targetIssues(LandscapeModel m, String key, List<Patch> patches, List<ColumnSample[]> samples) {
		List<TargetIssue> out = new ArrayList<>();
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
				out.add(new TargetIssue(p.name(), key + ": unknown patch " + p.name()));
			} else if (!site.target().test(m, p, a)) {
				out.add(new TargetIssue(p.name(), key + " / " + p.name() + ": patch does not contain its target: " + site.description()));
			}
		}
		for (WaterKind k : WaterKind.values()) {
			if (!kinds.contains(k.name())) {
				out.add(new TargetIssue(TargetIssue.COVERAGE, key + ": missing water " + k + " in the column set " + kinds));
			}
		}
		for (LandscapeType t : LandscapeType.values()) {
			if (!types.contains(t.name())) {
				out.add(new TargetIssue(TargetIssue.COVERAGE, key + ": missing type " + t + " in the column set " + types));
			}
		}
		for (Substrate s : Substrate.values()) {
			if (!substrates.contains(s.name())) {
				out.add(new TargetIssue(TargetIssue.COVERAGE, key + ": missing substrate " + s + " in the column set " + substrates));
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

	private static boolean exists(String resource) {
		return GoldenTerrainTest.class.getResource(resource) != null;
	}

	private static Map<String, GoldenSet> read(String resource) throws IOException {
		Map<String, GoldenSet> sets = new LinkedHashMap<>();
		try (InputStream in = GoldenTerrainTest.class.getResourceAsStream(resource)) {
			if (in == null) {
				fail("missing golden file " + resource + "; create it: ./gradlew test --tests '*GoldenTerrainTest*' -PwriteGolden");
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

	/** Writes the {@code -PgoldenReport} file: changed patches in the allow list syntax, fields and notes as comments. */
	private static void writeReport(String resource, Allow allow, List<String> lines) throws IOException {
		String target = System.getProperty("polishforests.golden.report");
		if (target == null || target.isBlank()) {
			return;
		}
		StringBuilder sb = new StringBuilder();
		sb.append("# GoldenTerrainTest report: changes against ").append(resource).append(", allow list ")
				.append(allow.source()).append('\n');
		sb.append("# <set> / <patch>: patch hash changed; <set> / <patch> target: patch lost its target;\n");
		sb.append("# <set> / coverage: the set lost a water kind, type or substrate. Fields and notes are comments.\n");
		if (lines.isEmpty()) {
			sb.append("# no changes\n");
		}
		for (String l : lines) {
			sb.append(l).append('\n');
		}
		Path path = Path.of(target);
		if (path.getParent() != null) {
			Files.createDirectories(path.getParent());
		}
		Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
		System.out.println("Golden test: report written to " + path);
	}

	/**
	 * List of allowed changes ({@code -PgoldenAllow}). Lines {@code <set> / <patch>} allow a changed patch hash,
	 * {@code <set> / <patch> target} also a lost target, {@code <set> / <patch> numeric} only a numerical change of a
	 * patch the golden file holds at its M1 hash (below {@link #NUMERIC_SURFACE} m against the frozen M1 copy, the same
	 * blocks, water, type and substrate in every column; for the patches that the design keeps unchanged, marked = in
	 * its table of patches), {@code <set> / coverage} a set without some water kind, type or substrate. {@code <set>} is
	 * a {@link SetKey#label()}, a {@link SetKey#key()} or {@code *}. Text after {@code #} is a comment, so a
	 * {@code -PgoldenReport} file can serve as an allow list. Unknown sets or patches fail the test, so that a typo does
	 * not silently allow nothing.
	 */
	record Allow(String source, Set<String> patches, Set<String> targets, Set<String> numerics) {
		static Allow load(String file) throws IOException {
			Set<String> patches = new LinkedHashSet<>();
			Set<String> targets = new LinkedHashSet<>();
			Set<String> numerics = new LinkedHashSet<>();
			if (file == null || file.isBlank()) {
				return new Allow("(none)", patches, targets, numerics);
			}
			Path path = Path.of(file);
			assertTrue(Files.isRegularFile(path), "missing allow list " + path);
			for (String raw : Files.readAllLines(path, StandardCharsets.UTF_8)) {
				int hash = raw.indexOf('#');
				String line = (hash >= 0 ? raw.substring(0, hash) : raw).strip();
				if (line.isEmpty()) {
					continue;
				}
				int slash = line.lastIndexOf(" / ");
				assertTrue(slash > 0, "allow list " + path + ": expected '<set> / <patch>', got: " + raw);
				String set = line.substring(0, slash).strip();
				String[] rest = line.substring(slash + 3).strip().split("\\s+");
				String patch = rest[0];
				boolean target = rest.length > 1 && rest[1].equals("target");
				boolean numeric = rest.length > 1 && rest[1].equals("numeric");
				assertTrue(rest.length == 1 || (target || numeric) && rest.length == 2, "allow list " + path
						+ ": unknown suffix in: " + raw);
				assertTrue(patch.equals("grid") || patch.equals(TargetIssue.COVERAGE) || site(patch) != null,
						"allow list " + path + ": unknown patch in: " + raw);
				List<String> labels = new ArrayList<>();
				for (SetKey k : SETS) {
					if (set.equals("*") || set.equalsIgnoreCase(k.label()) || set.equals(k.key())) {
						labels.add(k.label());
					}
				}
				assertTrue(!labels.isEmpty(), "allow list " + path + ": unknown set in: " + raw);
				for (String l : labels) {
					patches.add(l + " / " + patch);
					if (target || patch.equals(TargetIssue.COVERAGE)) {
						targets.add(l + " / " + patch);
					}
					if (numeric) {
						numerics.add(l + " / " + patch);
					}
				}
			}
			return new Allow(path.toString(), patches, targets, numerics);
		}

		boolean numeric(SetKey k, String name) {
			return numerics.contains(k.label() + " / " + name);
		}

		boolean patch(SetKey k, String name) {
			return patches.contains(k.label() + " / " + name);
		}

		boolean target(SetKey k, String name) {
			return targets.contains(k.label() + " / " + name);
		}
	}

	private static void write() throws IOException {
		String target = System.getProperty("polishforests.golden.file");
		assertNotNull(target, "missing property polishforests.golden.file (set by build.gradle)");
		boolean keepCenters = Boolean.getBoolean("polishforests.golden.keepCenters");
		Map<String, GoldenSet> current = Map.of();
		if (keepCenters) {
			String resource = exists(RESOURCE_CURRENT) ? RESOURCE_CURRENT : RESOURCE_M1;
			current = read(resource);
			System.out.println("Golden test: keeping the patch centers of " + resource);
		}
		StringBuilder sb = new StringBuilder();
		sb.append("# Golden terrain M2 (terrain geometry fix, docs/m2/poprawka-geometrii.md). SHA-256 hashes of M1\n");
		sb.append("# ColumnSample fields: surface (1e-6 m), waterLevel, waterKind, type, substrate, coverDepth (1e-6 m) and\n");
		sb.append("# blocks (Y of ground and water in both vertical mappings). Generated by GoldenTerrainTest. Overwrite only\n");
		sb.append("# for an intended terrain change: ./gradlew test --tests '*GoldenTerrainTest*' -PwriteGolden -PgoldenKeepCenters\n");
		sb.append("# The M1 terrain stays in golden_terrain_m1.txt (frozenM1CopyMatchesGolden).\n");
		sb.append("# History:\n");
		for (String h : HISTORY) {
			sb.append("#   ").append(h).append('\n');
		}
		sb.append("# set <scale> <seed> <region slider>\n");
		sb.append("# patch <name> <n> <center x> <center z> <step in m> <hash of the n × n patch>\n");
		List<String> errors = new ArrayList<>();
		for (SetKey k : SETS) {
			List<String> missing = new ArrayList<>();
			GoldenSet old = current.get(k.key());
			if (keepCenters && old == null) {
				System.out.println("Golden test: no set " + k.key() + " in the current file, searching all patches");
			}
			List<Patch> patches = choosePatches(k.model(), missing, old == null ? Map.of() : reusablePatches(k, old));
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

	/**
	 * Patches of the current golden file that can be kept when writing with {@code -PgoldenKeepCenters}: the grid when
	 * its size and step are unchanged, and every patch of a known site whose target is still met at the old center
	 * (checked with a fresh model). The others are searched for anew.
	 */
	static Map<String, Patch> reusablePatches(SetKey k, GoldenSet old) {
		LandscapeModel m = k.model();
		Map<String, Patch> out = new LinkedHashMap<>();
		for (Patch p : old.patches()) {
			if (p.name().equals("grid")) {
				if (p.equals(grid(m))) {
					out.put(p.name(), p);
				} else {
					System.out.println("Golden test: " + k.label() + " / grid has a different size or step, searching anew");
				}
				continue;
			}
			Site site = site(p.name());
			if (site == null) {
				System.out.println("Golden test: " + k.label() + " / " + p.name() + " is no longer a site, dropped");
			} else if (site.target().test(m, p, samples(m, List.of(p)).get(0))) {
				out.put(p.name(), p);
			} else {
				System.out.println("Golden test: " + k.label() + " / " + p.name() + " lost its target at the old center, "
						+ "searching anew");
			}
		}
		return out;
	}

	// ---------------------------------------------------------------- patch selection

	/**
	 * Where the spiral search for the patch center starts (COAST: the center of the "coast" patch; the type names:
	 * {@link LandscapePreview#findInterior} of that type; GREAT_MASSIF: the center of the large Beskid massif nearest to
	 * (0, 0), {@link LandscapeModel#nearestGreatMassif}).
	 */
	enum Start {
		ZERO, COAST, BESKIDS, FOOTHILLS, OUTWASH_PLAIN, MORAINE_PLATEAU, GREAT_MASSIF
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
			Pre pre, Predicate<ColumnSample> border, String description, Target target, Margin margin) {
		Site(String name, int stepReal, int stepGameplay, Start start, double searchReal, double searchGameplay, Pre pre,
				Predicate<ColumnSample> border, String description, Target target) {
			this(name, stepReal, stepGameplay, start, searchReal, searchGameplay, pre, border, description, target, null);
		}
	}

	/**
	 * Margin of a candidate patch that meets its target (larger is better). When a site has one, the search does not
	 * take the first candidate but the best of the first {@link #MARGIN_CANDIDATES} that meet the target.
	 */
	interface Margin {
		double of(LandscapeModel m, Patch p);
	}

	/** Number of candidates meeting the target that the search compares by {@link Margin}. */
	static final int MARGIN_CANDIDATES = 16;
	/** Least distance between the candidates compared by {@link Margin}, in m·k, so that they are not one cluster. */
	static final double MARGIN_SEPARATION = 600;

	private static final Predicate<ColumnSample> SEA_OPEN = s -> s.waterKind() == WaterKind.SEA
			&& s.type() == LandscapeType.SEA;
	private static final Predicate<ColumnSample> SEA = s -> s.waterKind() == WaterKind.SEA;
	private static final Predicate<ColumnSample> RIVER = s -> s.waterKind() == WaterKind.RIVER;

	/**
	 * Clearance of the interior control patches from valleys, standing water and the coastal belt, in m·k (k = local
	 * scale, the valleyScale of RiverNetwork): 300 m at realistic scale, 150 m at gameplay scale.
	 */
	static final double INTERIOR_CLEARANCE = 300;
	/** Rings of the interior target: at 1/3, 2/3 and 3/3 of the clearance beyond the patch corner. */
	static final int INTERIOR_RINGS = 3;
	/** Directions sampled on each ring of the interior target and of the margin scan. */
	static final int INTERIOR_DIRECTIONS = 36;
	static final String INTERIOR_DESCRIPTION = "no water, no standing water belt, not on a valley floor, not cut by valleys or "
			+ "lakes, at least " + INTERIOR_CLEARANCE + " m·k beyond a valley floor edge and beyond the coastal belt, also at "
			+ INTERIOR_RINGS + " × " + INTERIOR_DIRECTIONS + " points on rings up to " + INTERIOR_CLEARANCE
			+ " m·k around the patch";

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
					(m, p, a) -> count(a, s -> s.type() == LandscapeType.FOOTHILLS) >= 200),
			// Control patches of the terrain geometry fix (K0, docs/m2/poprawka-geometrii.md): interiors far from
			// valleys and standing water, which no step of the fix may change (except D3 for the moraine plateau).
			new Site("outwash_plain_interior", 4, 4, Start.OUTWASH_PLAIN, 500, 25,
					(m, x, z, s) -> interiorColumn(m, s, LandscapeType.OUTWASH_PLAIN), null,
					"outwash plain interior: type weight ≥ 0.95, " + INTERIOR_DESCRIPTION,
					(m, p, a) -> interior(m, p, a, LandscapeType.OUTWASH_PLAIN), GoldenTerrainTest::interiorMargin),
			new Site("moraine_plateau_interior", 4, 4, Start.MORAINE_PLATEAU, 500, 25,
					(m, x, z, s) -> interiorColumn(m, s, LandscapeType.MORAINE_PLATEAU), null,
					"moraine plateau interior: type weight ≥ 0.95, " + INTERIOR_DESCRIPTION,
					(m, p, a) -> interior(m, p, a, LandscapeType.MORAINE_PLATEAU), GoldenTerrainTest::interiorMargin),
			// Large Beskid massif (M2-8, step K2 of the terrain geometry fix): the summit of the massif nearest to (0, 0).
			// The target is the large massif threshold of the altitudinal belts (E12), not a fixed summit height, so
			// that tuning S2 (lower massifs) changes only the hash of this patch.
			new Site("great_massif", 40, 12, Start.GREAT_MASSIF, 200, 20,
					(m, x, z, s) -> s.type() == LandscapeType.BESKIDS && s.surface() > AltitudinalBelts.LARGE_MASSIF, null,
					"summit (SUMMIT landform) of a large Beskid massif, BESKIDS above " + AltitudinalBelts.LARGE_MASSIF + " m",
					(m, p, a) -> hasForm(m, p, a, s -> s.type() == LandscapeType.BESKIDS
							&& s.surface() > AltitudinalBelts.LARGE_MASSIF, Landform.SUMMIT)));

	/**
	 * Column untouched by waters and by the coast: no water and no water level, no standing water belt, not on a valley
	 * floor, not cut (surface equal to the terrain before valleys and lakes), the nearest channel at least
	 * {@code clearance} m·k beyond the edge of the valley floor, and at least {@code clearance} m·k beyond the coastal
	 * belt ({@code shapeCoast}, 25 km·meso). The channel condition does not depend on the culling frame of the river
	 * segments ({@code streamOrder} does: it is 0 in the holes between the frames, which steps K4 move), because every
	 * frame reaches far beyond the floor edge plus the clearance. NaN and +∞ (no watercourse) pass.
	 */
	static boolean untouched(LandscapeModel m, ColumnSample s, double clearance) {
		double k = m.scale().local();
		ColumnSample.Waters w = s.waters();
		return s.waterLevel() == ColumnSample.NO_WATER
				&& w.standingWaterKind() == ColumnSample.StandingWaterKind.NONE && !w.inFloor()
				&& s.surface() == s.terrain().rawSurface()
				&& !(w.channelDist() - w.floorHalfWidth() < clearance * k)
				&& !(s.terrain().coastD() < 25_000 * m.scale().meso() + clearance * k);
	}

	/** Interior column: type weight ≥ 0.95 and {@link #untouched} with {@link #INTERIOR_CLEARANCE}. */
	static boolean interiorColumn(LandscapeModel m, ColumnSample s, LandscapeType type) {
		double w = switch (type) {
			case OUTWASH_PLAIN -> s.terrain().wOutwashPlain();
			case MORAINE_PLATEAU -> s.terrain().wMorainePlateau();
			default -> throw new IllegalArgumentException(type.name());
		};
		return w >= 0.95 && untouched(m, s, INTERIOR_CLEARANCE);
	}

	/** Distance of the patch corner from its center (m). */
	static double corner(Patch p) {
		return Math.hypot(p.n() / 2.0 * p.step(), p.n() / 2.0 * p.step());
	}

	/** Point q of {@link #INTERIOR_DIRECTIONS} on the ring of radius r around the patch center. */
	static ColumnSample ring(LandscapeModel m, Patch p, double r, int q) {
		double ang = q * 2 * Math.PI / INTERIOR_DIRECTIONS;
		return m.sample(p.x() + r * Math.cos(ang), p.z() + r * Math.sin(ang));
	}

	/**
	 * Target of an interior control patch: every column is an {@link #interiorColumn}, and the points on
	 * {@link #INTERIOR_RINGS} rings around the center (1/3, 2/3 and 3/3 of {@link #INTERIOR_CLEARANCE} m·k beyond the
	 * patch corner, {@link #INTERIOR_DIRECTIONS} directions) are {@link #untouched}. The rings sample the clearance;
	 * between the points a narrow valley head could still come closer.
	 */
	static boolean interior(LandscapeModel m, Patch p, ColumnSample[] a, LandscapeType type) {
		for (ColumnSample s : a) {
			if (!interiorColumn(m, s, type)) {
				return false;
			}
		}
		double k = m.scale().local();
		for (int i = 1; i <= INTERIOR_RINGS; i++) {
			double r = corner(p) + INTERIOR_CLEARANCE * k * i / INTERIOR_RINGS;
			for (int q = 0; q < INTERIOR_DIRECTIONS; q++) {
				if (!untouched(m, ring(m, p, r, q), INTERIOR_CLEARANCE)) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * Margin of an interior patch, used to choose among the candidates: the ring radius beyond the patch corner (in
	 * units of {@link #INTERIOR_CLEARANCE} m·k, at most 3) up to which every point is still {@link #untouched} with twice
	 * the clearance. Rings every 1/4 of the clearance, {@link #INTERIOR_DIRECTIONS} directions. A patch chosen this way
	 * keeps its target when steps K4–K6 move valley walls, floor edges and lake basins by up to about the clearance.
	 */
	static double interiorMargin(LandscapeModel m, Patch p) {
		double k = m.scale().local();
		for (int i = 1; i <= 12; i++) {
			double r = corner(p) + INTERIOR_CLEARANCE * k * i / 4.0;
			for (int q = 0; q < INTERIOR_DIRECTIONS; q++) {
				if (!untouched(m, ring(m, p, r, q), 2 * INTERIOR_CLEARANCE)) {
					return (i - 1) / 4.0;
				}
			}
		}
		return 3;
	}

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

	/** The 32 × 32 grid of a set. */
	static Patch grid(LandscapeModel m) {
		return new Patch("grid", 32, 0, 0, (int) Math.round(m.regionSize() * 0.6));
	}

	/**
	 * A 32 × 32 grid and 16 × 16 patches from {@link #SITES}, in that order; patches in {@code keep} are taken as they
	 * are, the others are searched for. Names of sites not found go to {@code missing}.
	 */
	static List<Patch> choosePatches(LandscapeModel m, List<String> missing, Map<String, Patch> keep) {
		List<Patch> out = new ArrayList<>();
		out.add(keep.getOrDefault("grid", grid(m)));
		Map<Start, double[]> starts = new LinkedHashMap<>();
		starts.put(Start.ZERO, new double[] {0, 0});
		for (Site site : SITES) {
			Patch kept = keep.get(site.name());
			Patch p;
			String time;
			if (kept != null) {
				p = kept;
				time = "kept";
			} else {
				double[] start = start(m, site.start(), starts);
				long t0 = System.nanoTime();
				p = start == null ? null : search(m, site, start, out);
				time = String.format(Locale.ROOT, "%.1f s", (System.nanoTime() - t0) / 1e9);
			}
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

	/** Start point of the spiral search, computed once per set (null: COAST without a coast patch). */
	private static double[] start(LandscapeModel m, Start start, Map<Start, double[]> starts) {
		if (!starts.containsKey(start) && start == Start.GREAT_MASSIF) {
			LandscapeModel.GreatMassif g = m.nearestGreatMassif(0, 0);
			starts.put(start, g == null ? null : new double[] {g.x(), g.z()});
		}
		if (!starts.containsKey(start)) {
			LandscapeType type = switch (start) {
				case BESKIDS -> LandscapeType.BESKIDS;
				case FOOTHILLS -> LandscapeType.FOOTHILLS;
				case OUTWASH_PLAIN -> LandscapeType.OUTWASH_PLAIN;
				case MORAINE_PLATEAU -> LandscapeType.MORAINE_PLATEAU;
				default -> null;
			};
			starts.put(start, type == null ? null : LandscapePreview.findInterior(m, type));
		}
		return starts.get(start);
	}

	/** Spiral search for the patch center (see {@link Site}). */
	static Patch search(LandscapeModel m, Site site, double[] start, List<Patch> chosen) {
		boolean real = m.scale() == LandscapeScale.REALISTIC;
		double avoid = real ? 1_000 : 100;
		double step = real ? site.searchReal() : site.searchGameplay();
		int patchStep = real ? site.stepReal() : site.stepGameplay();
		// Sites with a margin: the best of the first MARGIN_CANDIDATES candidates that meet the target.
		List<Patch> compared = new ArrayList<>();
		Patch best = null;
		double bestMargin = -1;
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
				if (candidate != null && site.margin() != null
						&& near(compared, candidate, MARGIN_SEPARATION * m.scale().local())) {
					candidate = null;
				}
				if (candidate != null) {
					Patch p = new Patch(site.name(), 16, candidate[0], candidate[1], patchStep);
					if (site.target().test(m, p, samples(m, List.of(p)).get(0))) {
						if (site.margin() == null) {
							return p;
						}
						compared.add(p);
						double margin = site.margin().of(m, p);
						if (best == null || margin > bestMargin) {
							best = p;
							bestMargin = margin;
						}
						if (compared.size() >= MARGIN_CANDIDATES) {
							return best;
						}
					}
				}
			}
		}
		return best;
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
