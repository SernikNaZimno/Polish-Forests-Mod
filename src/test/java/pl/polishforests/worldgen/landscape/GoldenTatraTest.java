package pl.polishforests.worldgen.landscape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Step TT1 (KT-1): the golden file of the Tatra complexes, {@code golden_terrain_tatra.txt}. Two sets (gameplay scale,
 * seeds A and B of {@link GoldenTerrainTest}, the Tatras on), each with six patches in the nearest massif: a granite ridge
 * of the High Tatras, a cirque, the limestone regle, the Western Tatras, the Podhale basin and the transition to the
 * Beskids. The hashes cover the fields of the M2 golden file and the Tatra part and bedrock of every column; every patch
 * also meets its target ({@link #TARGETS}), so a later change that moves the complex is seen as a lost target, not only
 * as a changed hash.
 *
 * <p>{@code -PwriteGoldenTatra} searches the patches anew and writes the file (only on purpose, as {@code -PwriteGolden}
 * for the M2 file).
 *
 * <p>Step TT5: two more sets at realistic scale (seeds A and B, the Tatras on), with the same six targets; their lattice
 * steps and the search lattice grow with the forms ({@link TatraSurvey#step}), so a patch covers the same part of the
 * massif. The gameplay sets are written as before (the same lines).
 *
 * <p>Step TT7: a seventh patch on the foreland north of the Podhale basin (the Gorce-type flysch ridge, Beskid type); the
 * file was written anew once on purpose (the foreland changes the north side of the complex and its rivers).
 */
@Tag("slow")
class GoldenTatraTest {
	static final String RESOURCE = "/golden_terrain_tatra.txt";
	static final List<GoldenTerrainTest.SetKey> SETS = List.of(
			new GoldenTerrainTest.SetKey(LandscapeScale.GAMEPLAY, GoldenTerrainTest.SEED_A, 1.0),
			new GoldenTerrainTest.SetKey(LandscapeScale.GAMEPLAY, GoldenTerrainTest.SEED_B, 1.0),
			new GoldenTerrainTest.SetKey(LandscapeScale.REALISTIC, GoldenTerrainTest.SEED_A, 1.0),
			new GoldenTerrainTest.SetKey(LandscapeScale.REALISTIC, GoldenTerrainTest.SEED_B, 1.0));
	static final String[] FIELDS = {"surface", "waterLevel", "waterKind", "type", "substrate", "coverDepth", "blocks", "tatraPart",
			"rock"};
	/** Columns per patch side. */
	static final int N = 16;

	/** A patch target: its name, lattice step (m), a quick test of the patch center and the test of the whole patch. */
	record Target(String name, int step, Predicate<TatraField.Column> center, Predicate<ColumnSample[]> patch, String description) {
	}

	static double share(ColumnSample[] a, Predicate<ColumnSample> test) {
		return GoldenTerrainTest.count(a, test) / (double) a.length;
	}

	static final List<Target> TARGETS = List.of(
			new Target("granite_ridge", 8, c -> c.weight > 0.9 && c.rock == Substrate.GRANITE && c.height > 2_250,
					a -> share(a, s -> s.terrain().rock() == Substrate.GRANITE) >= 0.5
							&& java.util.Arrays.stream(a).mapToDouble(ColumnSample::surface).max().orElse(0) >= 2_200,
					"half the columns granite, a summit of at least 2200 m"),
			new Target("cirque", 6, c -> c.weight > 0.9 && (c.landforms & Landform.CIRQUE.bit()) != 0,
					a -> share(a, s -> s.terrain().has(Landform.CIRQUE)) >= 0.25, "a quarter of the columns in a cirque"),
			new Target("regle_limestone", 6, c -> c.weight > 0.9 && c.part == TatraPart.REGLE && c.rock == Substrate.LIMESTONE,
					a -> share(a, s -> s.terrain().tatraPart() == TatraPart.REGLE) >= 0.5
							&& share(a, s -> s.terrain().rock() == Substrate.LIMESTONE) >= 0.25,
					"half the columns in the regle, a quarter limestone"),
			new Target("western_tatra", 12, c -> c.weight > 0.9 && c.part == TatraPart.WESTERN_TATRA && c.height > 1_700,
					a -> share(a, s -> s.terrain().tatraPart() == TatraPart.WESTERN_TATRA) >= 0.7, "70% of the columns in the Western Tatras"),
			new Target("podhale", 12, c -> c.weight > 0.9 && c.part == TatraPart.PODHALE_BASIN,
					a -> share(a, s -> s.terrain().tatraPart() == TatraPart.PODHALE_BASIN && s.type() == LandscapeType.PODHALE) >= 0.7,
					"70% of the columns in the Podhale basin"),
			new Target("foreland", 40, c -> c.weight > 0.9 && c.part == TatraPart.FORELAND && c.beskids > 0.8,
					a -> share(a, s -> s.terrain().tatraPart() == TatraPart.FORELAND) >= 0.7
							&& share(a, s -> s.type() == LandscapeType.BESKIDS) >= 0.5,
					"70% of the columns on the foreland, half of them Beskid"),
			new Target("beskid_transition", 40, c -> c.weight > 0.35 && c.weight < 0.65,
					a -> share(a, s -> s.type().isTatra()) >= 0.15
							&& share(a, s -> s.type() == LandscapeType.BESKIDS || s.type() == LandscapeType.FOOTHILLS) >= 0.15,
					"at least 15% of the columns Tatra or Podhale and 15% Beskids or foothills"));

	static Target target(String name) {
		return TARGETS.stream().filter(t -> t.name().equals(name)).findFirst().orElseThrow();
	}

	static LandscapeModel model(GoldenTerrainTest.SetKey k) {
		return new LandscapeModel(k.seed(), k.scale(), k.regionScale(), true);
	}

	@Test
	void tatraTerrainMatchesGolden() throws IOException {
		String write = System.getProperty("polishforests.golden.tatra.write");
		if (write != null && !write.isBlank()) {
			write(Path.of(write));
			return;
		}
		Map<String, GoldenTerrainTest.GoldenSet> golden = GoldenTerrainTest.read(RESOURCE);
		assertEquals(SETS.size(), golden.size(), "golden file " + RESOURCE + " has the wrong number of sets");
		List<String> errors = new ArrayList<>();
		for (GoldenTerrainTest.SetKey k : SETS) {
			GoldenTerrainTest.GoldenSet expected = golden.get(k.key());
			assertNotNull(expected, "missing set " + k.key() + " in " + RESOURCE);
			assertEquals(TARGETS.size(), expected.patches().size(), k.label() + ": patches");
			LandscapeModel m = model(k);
			List<ColumnSample[]> samples = GoldenTerrainTest.samples(m, expected.patches());
			Map<String, String> actual = hashes(expected.patches(), samples);
			for (Map.Entry<String, String> e : expected.hashes().entrySet()) {
				if (!e.getValue().equals(actual.get(e.getKey()))) {
					errors.add(k.label() + " / " + e.getKey() + ": expected " + e.getValue() + ", got " + actual.get(e.getKey()));
				}
			}
			for (int q = 0; q < expected.patches().size(); q++) {
				GoldenTerrainTest.Patch p = expected.patches().get(q);
				Target t = target(p.name());
				if (!t.patch().test(samples.get(q))) {
					errors.add(k.label() + " / " + p.name() + ": lost its target (" + t.description() + ")");
				}
			}
		}
		assertTrue(errors.isEmpty(), "The Tatra terrain differs from " + RESOURCE + " (write it anew on purpose with "
				+ "-PwriteGoldenTatra):\n" + String.join("\n", errors));
	}

	/** Hashes of the patches, the fields and the total, as in {@link GoldenTerrainTest#hashes} with the Tatra fields. */
	static Map<String, String> hashes(List<GoldenTerrainTest.Patch> patches, List<ColumnSample[]> samples) {
		MessageDigest all = sha();
		MessageDigest[] fields = new MessageDigest[FIELDS.length];
		for (int f = 0; f < fields.length; f++) {
			fields[f] = sha();
		}
		Map<String, String> out = new LinkedHashMap<>();
		for (int q = 0; q < patches.size(); q++) {
			MessageDigest local = sha();
			for (ColumnSample s : samples.get(q)) {
				byte[][] base = GoldenTerrainTest.fieldBytes(GoldenTerrainTest.Column.of(s));
				byte[][] parts = java.util.Arrays.copyOf(base, FIELDS.length);
				parts[base.length] = (s.terrain().tatraPart().name() + ";").getBytes(StandardCharsets.UTF_8);
				parts[base.length + 1] = (String.valueOf(s.terrain().rock()) + ";").getBytes(StandardCharsets.UTF_8);
				for (int f = 0; f < parts.length; f++) {
					local.update(parts[f]);
					all.update(parts[f]);
					fields[f].update(parts[f]);
				}
			}
			out.put("patch " + patches.get(q).name(), HexFormat.of().formatHex(local.digest()));
		}
		for (int f = 0; f < fields.length; f++) {
			out.put("field " + FIELDS[f], HexFormat.of().formatHex(fields[f].digest()));
		}
		out.put("total", HexFormat.of().formatHex(all.digest()));
		return out;
	}

	private static MessageDigest sha() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new AssertionError(e);
		}
	}

	/** The patch of the target nearest the middle of the nearest massif, searched on a lattice of the complex. */
	static GoldenTerrainTest.Patch search(LandscapeModel m, LandscapeModel.Tatra t, Target target) {
		double k = m.scale().mountainSpacing();
		double uMin = -(TatraField.WS + TatraField.FADE_SOUTH1) * k;
		double uMax = (TatraField.BASIN_CENTER + TatraField.BASIN / 2 + TatraField.FORELAND + TatraField.FADE_NORTH) * k;
		double vHalf = t.halfLength() + TatraField.FADE_END1 * k;
		List<double[]> candidates = new ArrayList<>();
		double lattice = TatraSurvey.step(m.scale(), 90);
		int step = (int) Math.round(TatraSurvey.step(m.scale(), target.step()));
		for (double u = uMin; u <= uMax; u += lattice) {
			for (double v = -vHalf; v <= vHalf; v += lattice) {
				double x = TatraSurvey.x(t, u, v);
				double z = TatraSurvey.z(t, u, v);
				TatraField.Column c = m.tatraColumn(x, z);
				if (c != null && target.center().test(c)) {
					candidates.add(new double[] {x, z, Math.hypot(u, v)});
				}
			}
		}
		candidates.sort(Comparator.comparingDouble(c -> c[2]));
		for (double[] c : candidates) {
			GoldenTerrainTest.Patch p = new GoldenTerrainTest.Patch(target.name(), N, Math.round(c[0]), Math.round(c[1]), step);
			if (target.patch().test(GoldenTerrainTest.samples(m, List.of(p)).get(0))) {
				return p;
			}
		}
		throw new AssertionError("no patch for " + target.name() + " (" + target.description() + ") in the massif " + t);
	}

	private static void write(Path file) throws IOException {
		StringBuilder sb = new StringBuilder();
		sb.append("# Golden terrain of the Tatra complexes (steps TT1 and TT5, GoldenTatraTest): gameplay and realistic scale, the Tatras on.\n");
		sb.append("# patch <name> <n> <x> <z> <step> <hash>; fields: ").append(String.join(", ", FIELDS)).append('\n');
		sb.append("# Written with -PwriteGoldenTatra; the patches lie in the massif nearest the origin of each seed.\n");
		for (GoldenTerrainTest.SetKey k : SETS) {
			LandscapeModel m = model(k);
			double box = TatraSurvey.box(k.scale());
			List<LandscapeModel.Tatra> ts = new ArrayList<>(m.tatras(-box, -box, box, box));
			ts.sort(Comparator.comparingDouble(q -> Math.hypot(q.x(), q.z())));
			LandscapeModel.Tatra t = ts.get(0);
			List<GoldenTerrainTest.Patch> patches = new ArrayList<>();
			for (Target target : TARGETS) {
				patches.add(search(m, t, target));
			}
			Map<String, String> h = hashes(patches, GoldenTerrainTest.samples(m, patches));
			sb.append(String.format(Locale.ROOT, "%nset %s %d %s%n", k.scale().id(), k.seed(), k.regionScale()));
			for (GoldenTerrainTest.Patch p : patches) {
				sb.append(String.format(Locale.ROOT, "patch %s %d %d %d %d %s%n", p.name(), p.n(), p.x(), p.z(), p.step(),
						h.get("patch " + p.name())));
			}
			for (String f : FIELDS) {
				sb.append("field ").append(f).append(' ').append(h.get("field " + f)).append('\n');
			}
			sb.append("total ").append(h.get("total")).append('\n');
			System.out.println(k.label() + ": massif (" + Math.round(t.x()) + ", " + Math.round(t.z()) + "), patches " + patches);
		}
		Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
		System.out.println("GoldenTatraTest: wrote " + file);
	}
}
