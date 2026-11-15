package pl.polishforests.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Step C2 (decision CP-D.5, G.58-c): the performance thresholds of the A/B measurements (PW-10, docs/00 G.34 with the
 * CP-D.5 note) in code, with a verdict for each row of an A/B table. A file of its own and plain Java only:
 * {@code ForestPerformance} must keep running on the M2 baseline build ({@code m2-baseline-r1}), which has none of the
 * mod's classes, and this class only judges numbers that the A/B drivers produce.
 *
 * <p>Thresholds (ratios to the M2 baseline; the numbers of the mountain guard are defaults to be confirmed by the user):
 * <ul>
 * <li>forest norm without caves ({@code -Pcaves=off}): chunks per second with C2ME ≥ 0.70 × M2 (target 0.75); the
 * realistic {@code lowland_beech_1} at 0.698 accepted by CP-D.5 (to be checked again in the collective run V17);</li>
 * <li>regression guard with caves: realistic ≥ 0.40, gameplay ≥ 0.55 × M2; at the mountain sites ({@code upland_fir_*},
 * {@code montane_spruce_*}) realistic ≥ 0.35, gameplay ≥ 0.45 × M2;</li>
 * <li>FPS with the optimization pack ≥ 0.8 × M2, except the flights over the canopy of the forests of steps F1–F6,
 * which are only reported;</li>
 * <li>stages without caves: FEATURES ≤ 2.0 × and ≤ 10 ms, LIGHT ≤ 1.75 × and ≤ 12 ms, the whole chunk ≤ 48 ms;</li>
 * <li>the Tatra sites (KT-6): description only, no threshold.</li>
 * </ul>
 */
public final class PerformanceThresholds {
	/** Forest norm without caves: chunks per second with C2ME, ratio to M2. */
	public static final double CHUNKS_NORM = 0.70;
	/** Target of the forest norm (reported, not required). */
	public static final double CHUNKS_TARGET = 0.75;
	/** The accepted ratio of the realistic {@code lowland_beech_1} without caves (CP-D.5; checked again in V17). */
	public static final double ACCEPTED_LOWLAND_BEECH_REAL = 0.698;
	/** Regression guard with caves (G.34). */
	public static final double GUARD_REAL = 0.40;
	public static final double GUARD_GAMEPLAY = 0.55;
	/** Mountain guard with caves (CP-D.5, defaults to be confirmed). */
	public static final double MOUNTAIN_GUARD_REAL = 0.35;
	public static final double MOUNTAIN_GUARD_GAMEPLAY = 0.45;
	/** FPS with the optimization pack. */
	public static final double FPS = 0.80;
	/** Stage budgets without caves (ratio and absolute milliseconds per chunk). */
	public static final double FEATURES_RATIO = 2.0;
	public static final double FEATURES_MS = 10.0;
	public static final double LIGHT_RATIO = 1.75;
	public static final double LIGHT_MS = 12.0;
	public static final double WHOLE_CHUNK_MS = 48.0;

	/** The sites of the forests of steps F1–F6 (their canopy flights are only reported). */
	public static final Set<String> F1_F6_FORESTS = Set.of("lowland_beech", "alder_carr", "upland_fir", "bog_woodland",
			"floodplain", "montane_spruce");
	/** The forests of the mountain guard. */
	public static final Set<String> MOUNTAIN_FORESTS = Set.of("upland_fir", "montane_spruce");

	/** What a row measures. */
	public enum Metric {
		/** Chunks per second with C2ME in a world without caves (the forest norm). */
		CHUNKS_NO_CAVES,
		/** Chunks per second with C2ME in a world with caves, against M2 without caves (the regression guard). */
		CHUNKS_WITH_CAVES,
		/** Frames per second with the optimization pack in a flight. */
		FPS,
		/** The FEATURES stage, ms per chunk, without caves. */
		FEATURES,
		/** The LIGHT stage, ms per chunk, without caves. */
		LIGHT,
		/** The whole chunk, ms per chunk, without caves. */
		WHOLE_CHUNK
	}

	/** The scale of a site. */
	public enum Scale {
		REALISTIC,
		GAMEPLAY
	}

	/** The verdict of a row. */
	public enum Verdict {
		/** Within the threshold. */
		PASS,
		/** Outside the threshold. */
		FAIL,
		/** Below the threshold, but at or above a value the user accepted (CP-D.5). */
		ACCEPTED,
		/** No threshold: only reported (the canopy flights over the forests of F1–F6). */
		REPORT,
		/** No threshold: only described (the Tatra sites, KT-6). */
		DESCRIBE
	}

	/**
	 * A row of an A/B table.
	 *
	 * @param metric what it measures
	 * @param scale  the scale of the site
	 * @param site   the site ({@code oak_hornbeam_1}, {@code upland_fir_1}, {@code tatra_high_1} …)
	 * @param view   for FPS the flight ({@code canopy} or {@code eye}), else null
	 * @param ratio  the ratio to M2 (for the stages the ratio of the milliseconds)
	 * @param ms     for the stages the milliseconds per chunk of the measured build, else NaN
	 */
	public record Row(Metric metric, Scale scale, String site, String view, double ratio, double ms) {
		public Row {
			site = site.toLowerCase(Locale.ROOT);
		}

		/** The forest of the site: its name without the trailing number ({@code upland_fir_1} → {@code upland_fir}). */
		public String forest() {
			return site.replaceFirst("_\\d+$", "");
		}

		public boolean canopy() {
			return "canopy".equals(view);
		}
	}

	/** A verdict with the threshold that decided it (NaN without one) and the rule in words. */
	public record Result(Row row, Verdict verdict, double threshold, String rule) {
		@Override
		public String toString() {
			return String.format(Locale.ROOT, "%s %s %s%s: %.3f -> %s (%s)", row.metric(), row.scale(), row.site(),
					row.view() == null ? "" : " " + row.view(), row.metric().ordinal() >= Metric.FEATURES.ordinal() ? row.ms()
							: row.ratio(), verdict, rule);
		}
	}

	private PerformanceThresholds() {
	}

	/** The verdict of one row. */
	public static Result judge(Row row) {
		if (row.site().startsWith("tatra")) {
			return new Result(row, Verdict.DESCRIBE, Double.NaN, "Tatra site (KT-6): description only");
		}
		boolean real = row.scale() == Scale.REALISTIC;
		return switch (row.metric()) {
			case CHUNKS_NO_CAVES -> {
				if (row.ratio() >= CHUNKS_NORM) {
					yield new Result(row, Verdict.PASS, CHUNKS_NORM, row.ratio() >= CHUNKS_TARGET ? "forest norm >= 0.70, target 0.75 met"
							: "forest norm >= 0.70 (target 0.75 not met)");
				}
				if (real && row.site().equals("lowland_beech_1") && row.ratio() >= ACCEPTED_LOWLAND_BEECH_REAL) {
					yield new Result(row, Verdict.ACCEPTED, CHUNKS_NORM, "0.698 accepted by CP-D.5, check again in V17");
				}
				yield new Result(row, Verdict.FAIL, CHUNKS_NORM, "forest norm >= 0.70");
			}
			case CHUNKS_WITH_CAVES -> {
				boolean mountain = MOUNTAIN_FORESTS.contains(row.forest());
				double t = mountain ? (real ? MOUNTAIN_GUARD_REAL : MOUNTAIN_GUARD_GAMEPLAY) : real ? GUARD_REAL : GUARD_GAMEPLAY;
				String rule = String.format(Locale.ROOT, "%s guard with caves >= %.2f", mountain ? "mountain" : "regression", t);
				yield new Result(row, row.ratio() >= t ? Verdict.PASS : Verdict.FAIL, t, rule);
			}
			case FPS -> {
				if (row.canopy() && F1_F6_FORESTS.contains(row.forest())) {
					yield new Result(row, Verdict.REPORT, Double.NaN, "canopy flight over a forest of F1-F6: report only");
				}
				yield new Result(row, row.ratio() >= FPS ? Verdict.PASS : Verdict.FAIL, FPS, "FPS with the pack >= 0.8");
			}
			case FEATURES -> stage(row, FEATURES_RATIO, FEATURES_MS, "FEATURES <= 2.0 x and <= 10 ms");
			case LIGHT -> stage(row, LIGHT_RATIO, LIGHT_MS, "LIGHT <= 1.75 x and <= 12 ms");
			case WHOLE_CHUNK -> new Result(row, row.ms() <= WHOLE_CHUNK_MS ? Verdict.PASS : Verdict.FAIL, WHOLE_CHUNK_MS,
					"whole chunk <= 48 ms");
		};
	}

	private static Result stage(Row row, double ratio, double ms, String rule) {
		return new Result(row, row.ratio() <= ratio && row.ms() <= ms ? Verdict.PASS : Verdict.FAIL, ratio, rule);
	}

	/** The verdicts of all rows. */
	public static List<Result> judge(List<Row> rows) {
		List<Result> results = new ArrayList<>(rows.size());
		for (Row r : rows) {
			results.add(judge(r));
		}
		return results;
	}

	// ------------------------------------------------------------------ A/B tables of docs/wycinek

	/**
	 * The rows of an A/B report in the Markdown form of {@code docs/wycinek/<step>/ab_<step>.md}: the section heading names
	 * the metric (chunks per second without caves, the guard with caves, FEATURES, LIGHT, whole chunk, FPS), a row starts
	 * with the scale and the site ({@code REAL upland_fir_1}, {@code GAMEPLAY D transect}, for FPS with {@code okap}
	 * (canopy) or {@code oko} (eye)), the ratio is the 4th column for chunks per second and FPS and the 5th for the stages,
	 * whose 4th column holds the milliseconds of the measured build. Decimal commas, bold marks and the summary rows are
	 * handled; sections without a threshold (the cost of steps F) are skipped.
	 */
	public static List<Row> parseMarkdown(List<String> lines) {
		List<Row> rows = new ArrayList<>();
		Metric metric = null;
		for (String line : lines) {
			if (line.startsWith("#")) {
				metric = metricOf(line.toLowerCase(Locale.ROOT));
				continue;
			}
			if (metric == null || !line.startsWith("|")) {
				continue;
			}
			String[] cells = line.split("\\|", -1);
			if (cells.length < 6) {
				continue;
			}
			String[] name = clean(cells[1]).split("\\s+");
			Scale scale = name[0].equals("REAL") ? Scale.REALISTIC : name[0].equals("GAMEPLAY") ? Scale.GAMEPLAY : null;
			if (scale == null || name.length < 2) {
				continue;
			}
			int at = 1;
			if (name.length > 2 && name[1].equals("D")) {
				at = 2;
			}
			String site = name[at];
			String view = null;
			if (metric == Metric.FPS && name.length > at + 1) {
				view = name[at + 1].equals("okap") ? "canopy" : name[at + 1].equals("oko") ? "eye" : name[at + 1];
			}
			if (at == 2) {
				site = site + "_d";
			}
			boolean stage = metric == Metric.FEATURES || metric == Metric.LIGHT || metric == Metric.WHOLE_CHUNK;
			double ratio = number(cells[stage ? 5 : 4]);
			double ms = stage ? number(cells[4]) : Double.NaN;
			rows.add(new Row(metric, scale, site, view, ratio, ms));
		}
		return rows;
	}

	private static Metric metricOf(String heading) {
		if (heading.contains("koszt kroków")) {
			return null;
		}
		if (heading.contains("fps")) {
			return Metric.FPS;
		}
		if (heading.contains("strażnik") || heading.contains("z jaskiniami")) {
			return Metric.CHUNKS_WITH_CAVES;
		}
		if (heading.contains("chunki na sekundę")) {
			return Metric.CHUNKS_NO_CAVES;
		}
		if (heading.contains("features")) {
			return Metric.FEATURES;
		}
		if (heading.contains("light")) {
			return Metric.LIGHT;
		}
		if (heading.contains("cały chunk")) {
			return Metric.WHOLE_CHUNK;
		}
		return null;
	}

	private static String clean(String cell) {
		return cell.replace("*", "").trim();
	}

	private static double number(String cell) {
		return Double.parseDouble(clean(cell).replace(',', '.'));
	}
}
