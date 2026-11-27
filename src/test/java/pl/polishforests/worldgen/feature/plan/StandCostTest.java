package pl.polishforests.worldgen.feature.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.chunk.PolandScale;

/**
 * Cost test of the tree stand (step OP2 of docs/04-pionowy-wycinek.md, the gate before the A/B in the game): the planning
 * part of the tree stand of the game ({@link StandCostHarness}) on a fixed set of chunks (a square of
 * {@code -PstandCostSide} chunks, default 9, around each of the eight sites of {@link StandCostHarness#SITES}, both scales,
 * in the order of the game), in one JVM after a warm-up, many runs. Each run plans every site twice with a fresh stand
 * map and fresh crown territories (cold memos, as in a new area; the wide blend of the world stays warm): once timing
 * only the whole tree stand per chunk and the coarse parts (the territories, the tree planner, the roots), once also
 * the parts inside them (the vacancies asked by the territories, {@code StandVacancies.compute}, and the open land of
 * the planner, {@code Ecotone.provableTrees}), whose clocks would slow down the whole. Reported per site and in all: the
 * median, the least and largest value and the interquartile range of the runs, in ms per chunk; the hash of the plans
 * (the same in every run).
 *
 * <p>Tagged {@code cost}: only {@code ./gradlew costTest --tests '*StandCostTest*'} on a quiet machine. The A/B of the
 * gate alternates the builds (base and step) in separate runs of this test.
 */
@Tag("cost")
class StandCostTest {
	private static final int SIDE = Integer.getInteger("polishforests.standCost.side", 9);
	private static final int RUNS = Math.max(3, Integer.getInteger("polishforests.cost.runs", 7));
	private static final int THREADS = Integer.getInteger("polishforests.standCost.threads", 1);
	private static final int WARMUP = 2;
	private static final String[] PARTS = {"stand plan", "territories", "  vacancies", "tree planner", "  open land", "roots"};

	/** The timings of one site over the runs. */
	private static final class Series {
		final List<Double> total = new ArrayList<>();
		final List<double[]> parts = new ArrayList<>();
		final List<Double> fineTotal = new ArrayList<>();
		long chunks;
		long trees;
		long hash;
		boolean hashed;
	}

	@Test
	void standCost() {
		String filter = System.getProperty("polishforests.standCost.sites", "");
		List<StandCostHarness.Site> sites = new ArrayList<>();
		for (StandCostHarness.Site s : StandCostHarness.SITES) {
			if (filter.isBlank() || Arrays.asList(filter.split(",")).contains(s.name())) {
				sites.add(s);
			}
		}
		Map<PolandScale, StandCostHarness.World> worlds = new EnumMap<>(PolandScale.class);
		long t0 = System.nanoTime();
		for (StandCostHarness.Site s : sites) {
			StandCostHarness.World w = worlds.computeIfAbsent(s.scale(), StandCostHarness.World::new);
			w.prepare(s, SIDE);
		}
		System.out.printf(Locale.ROOT, "[stand cost] habitats of %d sites ready in %.1f s%n", sites.size(), (System.nanoTime() - t0) / 1e9);
		Map<StandCostHarness.Site, Series> series = new LinkedHashMap<>();
		for (StandCostHarness.Site s : sites) {
			series.put(s, new Series());
		}
		List<Double> sums = new ArrayList<>();
		for (int run = -WARMUP; run < RUNS; run++) {
			double sumNanos = 0;
			long sumChunks = 0;
			for (StandCostHarness.Site s : sites) {
				StandCostHarness.World w = worlds.get(s.scale());
				Series out = series.get(s);
				StandCostHarness.Timers coarse = new StandCostHarness.Timers(false);
				long h = StandCostHarness.run(w, List.of(s), SIDE, StandCostHarness.StandSeed.POLAND, THREADS, coarse);
				StandCostHarness.Timers fine = new StandCostHarness.Timers(true);
				long h2 = StandCostHarness.run(w, List.of(s), SIDE, StandCostHarness.StandSeed.POLAND, THREADS, fine);
				assertEquals(h, h2, s + ": the plans of the timed parts differ");
				if (out.hashed) {
					assertEquals(out.hash, h, s + ": the plans differ between the runs");
				}
				out.hash = h;
				out.hashed = true;
				if (run < 0) {
					continue;
				}
				long chunks = coarse.chunks.sum();
				out.chunks = chunks;
				out.trees = coarse.trees.sum();
				out.total.add(coarse.total.sum() / 1e6 / chunks);
				out.fineTotal.add(fine.total.sum() / 1e6 / chunks);
				out.parts.add(new double[] {fine.standPlan.sum() / 1e6 / chunks, fine.territory.sum() / 1e6 / chunks,
						fine.vacancies.sum() / 1e6 / chunks, fine.shape.sum() / 1e6 / chunks, fine.open.sum() / 1e6 / chunks,
						fine.roots.sum() / 1e6 / chunks});
				sumNanos += coarse.total.sum();
				sumChunks += chunks;
			}
			if (run >= 0) {
				sums.add(sumNanos / 1e6 / sumChunks);
			}
		}
		StringBuilder report = new StringBuilder();
		report.append(String.format(Locale.ROOT, "Tree stand cost (step OP2): %d runs after %d warm-up runs, %d × %d chunks per site, %d thread(s)%n",
				RUNS, WARMUP, SIDE, SIDE, THREADS));
		report.append("ms per chunk: median [min – max] (IQR); parts from the runs with the inner clocks (median, share of their total)\n");
		for (Map.Entry<StandCostHarness.Site, Series> e : series.entrySet()) {
			StandCostHarness.Site s = e.getKey();
			Series v = e.getValue();
			report.append(String.format(Locale.ROOT, "%s %s%s: %d chunks, %.2f procedural trees per chunk, hash %dL%n", s.scale().getSerializedName(),
					s.name(), s.failed() ? " (below the norm in OP1)" : "", v.chunks, (double) v.trees / v.chunks, v.hash));
			report.append(String.format(Locale.ROOT, "  tree stand %s%n", stats(v.total)));
			double fineTotal = median(v.fineTotal);
			for (int p = 0; p < PARTS.length; p++) {
				List<Double> part = new ArrayList<>();
				for (double[] a : v.parts) {
					part.add(a[p]);
				}
				double m = median(part);
				report.append(String.format(Locale.ROOT, "  %-14s %.3f (%.1f%%)%n", PARTS[p], m, 100 * m / fineTotal));
			}
		}
		report.append(String.format(Locale.ROOT, "all sites: tree stand %s%n", stats(sums)));
		System.out.print(report);
		String file = System.getProperty("polishforests.standCost.report", "build/stand-cost/report.txt");
		try {
			Path path = Path.of(file);
			if (path.getParent() != null) {
				Files.createDirectories(path.getParent());
			}
			Files.writeString(path, report, StandardCharsets.UTF_8);
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static String stats(List<Double> values) {
		double[] v = values.stream().mapToDouble(Double::doubleValue).sorted().toArray();
		return String.format(Locale.ROOT, "%.3f [%.3f – %.3f] (IQR %.3f)", median(values), v[0], v[v.length - 1],
				quantile(v, 0.75) - quantile(v, 0.25));
	}

	private static double median(List<Double> values) {
		double[] v = values.stream().mapToDouble(Double::doubleValue).sorted().toArray();
		return quantile(v, 0.5);
	}

	private static double quantile(double[] sorted, double q) {
		double pos = q * (sorted.length - 1);
		int i = (int) Math.floor(pos);
		int j = Math.min(sorted.length - 1, i + 1);
		return sorted[i] + (sorted[j] - sorted[i]) * (pos - i);
	}
}
