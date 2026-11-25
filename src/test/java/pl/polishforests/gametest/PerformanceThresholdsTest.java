package pl.polishforests.gametest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pl.polishforests.gametest.PerformanceThresholds.Metric;
import pl.polishforests.gametest.PerformanceThresholds.Result;
import pl.polishforests.gametest.PerformanceThresholds.Row;
import pl.polishforests.gametest.PerformanceThresholds.Scale;
import pl.polishforests.gametest.PerformanceThresholds.Verdict;

/**
 * Step C2 (CP-D.5): the thresholds in code give the verdicts of decision G.57 on the numbers of step F7
 * ({@code docs/wycinek/f7/ab_f7.md}): the forest norm without caves in 15 of 16 sites with the accepted realistic
 * {@code lowland_beech_1} (0.698), the guard with caves met everywhere once the mountain sites have their own threshold
 * ({@code upland_fir_1} realistic 0.392 and {@code montane_spruce_1} gameplay 0.478 pass it), the five FPS flights below
 * 0.8 all over the canopy of the forests of F1–F6 (report only), the stages within their budgets.
 */
class PerformanceThresholdsTest {
	private static List<Result> f7() throws IOException {
		List<String> lines = Files.readAllLines(Path.of("docs/wycinek/f7/ab_f7.md"), StandardCharsets.UTF_8);
		return PerformanceThresholds.judge(PerformanceThresholds.parseMarkdown(lines));
	}

	private static Result find(List<Result> results, Metric metric, Scale scale, String site, String view) {
		return results.stream().filter(r -> r.row().metric() == metric && r.row().scale() == scale && r.row().site().equals(site)
				&& java.util.Objects.equals(r.row().view(), view)).findFirst().orElseThrow(() -> new AssertionError(
						"no row " + metric + " " + scale + " " + site + " " + view));
	}

	@Test
	void theTablesOfStepF7AreReadWhole() throws IOException {
		Map<Metric, Integer> rows = new EnumMap<>(Metric.class);
		for (Result r : f7()) {
			rows.merge(r.row().metric(), 1, Integer::sum);
		}
		assertEquals(16, rows.get(Metric.CHUNKS_NO_CAVES));
		assertEquals(16, rows.get(Metric.CHUNKS_WITH_CAVES));
		assertEquals(32, rows.get(Metric.FPS));
		assertEquals(20, rows.get(Metric.FEATURES));
		assertEquals(20, rows.get(Metric.LIGHT));
		assertEquals(20, rows.get(Metric.WHOLE_CHUNK));
	}

	@Test
	void theVerdictsOfStepF7AreThoseOfCheckpointD() throws IOException {
		List<Result> results = f7();
		for (Result r : results) {
			System.out.println(r);
		}
		// Forest norm without caves: 15 pass, the realistic lowland_beech_1 (0.698) accepted.
		Result beech = find(results, Metric.CHUNKS_NO_CAVES, Scale.REALISTIC, "lowland_beech_1", null);
		assertEquals(0.698, beech.row().ratio(), 1e-9);
		assertEquals(Verdict.ACCEPTED, beech.verdict());
		assertEquals(15, count(results, Metric.CHUNKS_NO_CAVES, Verdict.PASS));
		// The guard with caves: every site passes; the two mountain sites only by the mountain threshold.
		assertEquals(16, count(results, Metric.CHUNKS_WITH_CAVES, Verdict.PASS));
		Result fir = find(results, Metric.CHUNKS_WITH_CAVES, Scale.REALISTIC, "upland_fir_1", null);
		assertEquals(0.392, fir.row().ratio(), 1e-9);
		assertEquals(PerformanceThresholds.MOUNTAIN_GUARD_REAL, fir.threshold());
		Result spruce = find(results, Metric.CHUNKS_WITH_CAVES, Scale.GAMEPLAY, "montane_spruce_1", null);
		assertEquals(0.478, spruce.row().ratio(), 1e-9);
		assertEquals(PerformanceThresholds.MOUNTAIN_GUARD_GAMEPLAY, spruce.threshold());
		assertEquals(PerformanceThresholds.GUARD_GAMEPLAY, find(results, Metric.CHUNKS_WITH_CAVES, Scale.GAMEPLAY, "oak_hornbeam_1",
				null).threshold());
		// FPS: the canopy flights over the forests of F1-F6 (6 sites, 2 scales) are a report, everything else passes,
		// among them the canopy flights over the slice forests and every eye-level flight.
		assertEquals(12, count(results, Metric.FPS, Verdict.REPORT));
		assertEquals(20, count(results, Metric.FPS, Verdict.PASS));
		for (String[] low : new String[][] {{"REALISTIC", "lowland_beech_1"}, {"REALISTIC", "alder_carr_1"},
				{"REALISTIC", "upland_fir_1"}, {"GAMEPLAY", "lowland_beech_1"}, {"GAMEPLAY", "montane_spruce_1"}}) {
			Result r = find(results, Metric.FPS, Scale.valueOf(low[0]), low[1], "canopy");
			assertTrue(r.row().ratio() < PerformanceThresholds.FPS, r.toString());
			assertEquals(Verdict.REPORT, r.verdict());
		}
		// The stages without caves: every area within its budget.
		for (Metric m : new Metric[] {Metric.FEATURES, Metric.LIGHT, Metric.WHOLE_CHUNK}) {
			assertEquals(20, count(results, m, Verdict.PASS), m.name());
		}
		assertEquals(0, results.stream().filter(r -> r.verdict() == Verdict.FAIL).count());
	}

	@Test
	void theExceptionsAreNarrow() {
		// The acceptance holds for the realistic lowland_beech_1 at its measured value only.
		assertEquals(Verdict.FAIL, judge(Metric.CHUNKS_NO_CAVES, Scale.REALISTIC, "lowland_beech_1", null, 0.69));
		assertEquals(Verdict.FAIL, judge(Metric.CHUNKS_NO_CAVES, Scale.GAMEPLAY, "lowland_beech_1", null, 0.698));
		assertEquals(Verdict.FAIL, judge(Metric.CHUNKS_NO_CAVES, Scale.REALISTIC, "oak_hornbeam_1", null, 0.699));
		// The mountain guard is only for the fir and spruce forests.
		assertEquals(Verdict.FAIL, judge(Metric.CHUNKS_WITH_CAVES, Scale.REALISTIC, "fresh_pine_1", null, 0.392));
		assertEquals(Verdict.FAIL, judge(Metric.CHUNKS_WITH_CAVES, Scale.GAMEPLAY, "lowland_beech_1", null, 0.478));
		assertEquals(Verdict.FAIL, judge(Metric.CHUNKS_WITH_CAVES, Scale.REALISTIC, "upland_fir_2", null, 0.34));
		assertEquals(Verdict.PASS, judge(Metric.CHUNKS_WITH_CAVES, Scale.REALISTIC, "montane_spruce_2", null, 0.36));
		// The FPS report is only for the canopy flights over the forests of F1-F6.
		assertEquals(Verdict.FAIL, judge(Metric.FPS, Scale.REALISTIC, "lowland_beech_1", "eye", 0.79));
		assertEquals(Verdict.FAIL, judge(Metric.FPS, Scale.GAMEPLAY, "oak_hornbeam_1", "canopy", 0.79));
		assertEquals(Verdict.REPORT, judge(Metric.FPS, Scale.GAMEPLAY, "floodplain_1", "canopy", 0.5));
		// The Tatra sites (KT-6) are described only.
		assertEquals(Verdict.DESCRIBE, judge(Metric.CHUNKS_WITH_CAVES, Scale.REALISTIC, "tatra_high_1", null, 0.1));
		// The stages: ratio and absolute budget both.
		assertEquals(Verdict.FAIL, PerformanceThresholds.judge(new Row(Metric.FEATURES, Scale.REALISTIC, "x_1", null, 1.5, 10.5)).verdict());
		assertEquals(Verdict.FAIL, PerformanceThresholds.judge(new Row(Metric.LIGHT, Scale.REALISTIC, "x_1", null, 1.8, 9.0)).verdict());
		assertEquals(Verdict.FAIL, PerformanceThresholds.judge(new Row(Metric.WHOLE_CHUNK, Scale.REALISTIC, "x_1", null, 1.2, 48.5))
				.verdict());
	}

	@Test
	void theVerdictSectionSumsUpEveryRow() throws IOException {
		List<Result> results = f7();
		String md = PerformanceThresholds.verdictMarkdown(results);
		// The summary: one line per metric with the counts of the verdicts (chunks without caves: 15 PASS, 1 ACCEPTED).
		assertTrue(md.contains("| CHUNKS_NO_CAVES | 15 | 0 | 1 | 0 | 0 |"), md);
		assertTrue(md.contains("| FPS | 20 | 0 | 0 | 12 | 0 |"), md);
		// Every row once, and the section is skipped when the file is parsed again (its heading names no metric).
		assertEquals(results.size(), md.lines().filter(l -> l.matches("\\| [A-Z_]+ \\| (REALISTIC|GAMEPLAY) \\|.*")).count());
		List<String> again = new java.util.ArrayList<>(Files.readAllLines(Path.of("docs/wycinek/f7/ab_f7.md"), StandardCharsets.UTF_8));
		again.addAll(md.lines().toList());
		assertEquals(results.size(), PerformanceThresholds.parseMarkdown(again).size());
	}

	/**
	 * Step FA10: the tables of the collective run ({@code docs/wycinek/v17/ab_v17.md}, made by {@code tools/dev/ab_tables.py})
	 * are complete, and their verdicts are written to {@code build/ab/verdicts_v17.md}; when the file already holds the
	 * verdict section, it must be the one the thresholds give. Skipped until the file exists (stage 2 of FA10).
	 */
	@Test
	void theCollectiveRunIsJudged() throws IOException {
		Path file = Path.of("docs/wycinek/v17/ab_v17.md");
		org.junit.jupiter.api.Assumptions.assumeTrue(Files.exists(file), "no " + file + " yet");
		List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
		List<Result> results = PerformanceThresholds.judge(PerformanceThresholds.parseMarkdown(lines));
		Map<Metric, Integer> rows = new EnumMap<>(Metric.class);
		for (Result r : results) {
			if (!r.row().site().startsWith("tatra")) {
				rows.merge(r.row().metric(), 1, Integer::sum);
			}
		}
		assertEquals(16, rows.get(Metric.CHUNKS_NO_CAVES));
		assertEquals(16, rows.get(Metric.CHUNKS_WITH_CAVES));
		assertEquals(32, rows.get(Metric.FPS));
		assertEquals(20, rows.get(Metric.FEATURES));
		assertEquals(20, rows.get(Metric.LIGHT));
		assertEquals(20, rows.get(Metric.WHOLE_CHUNK));
		String md = PerformanceThresholds.verdictMarkdown(results);
		Files.createDirectories(Path.of("build/ab"));
		Files.writeString(Path.of("build/ab/verdicts_v17.md"), md, StandardCharsets.UTF_8);
		for (Result r : results) {
			System.out.println(r);
		}
		String text = String.join("\n", lines);
		int at = text.indexOf("## Werdykty progów");
		if (at >= 0) {
			int end = text.indexOf("\n## ", at + 1);
			String section = (end < 0 ? text.substring(at) : text.substring(at, end)).strip();
			assertEquals(md.strip(), section, "the verdict section of " + file + " is not the one of the thresholds");
		}
	}

	private static Verdict judge(Metric metric, Scale scale, String site, String view, double ratio) {
		return PerformanceThresholds.judge(new Row(metric, scale, site, view, ratio, Double.NaN)).verdict();
	}

	private static long count(List<Result> results, Metric metric, Verdict verdict) {
		return results.stream().filter(r -> r.row().metric() == metric && r.verdict() == verdict).count();
	}
}
