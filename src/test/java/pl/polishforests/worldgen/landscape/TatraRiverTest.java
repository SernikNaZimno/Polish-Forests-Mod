package pl.polishforests.worldgen.landscape;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * Step TT1 (docs/04-pionowy-wycinek.md, "Projekt TT1"): the rivers of the Tatra complexes for the seeds of
 * {@link TatraSurvey} at gameplay scale, on the final column sample (with the rivers, unlike the relief tests on the land
 * height):
 * <ul>
 * <li>no river over the main ridge: on every line across the range in the inner 80% of the massif the crest (the highest
 * point of the line) and its 30 m neighborhood lie outside every channel and have no water; the points well inside the
 * river core of the massif ({@link LandscapeModel#riverCoreStrength} at least {@value #INNER_CORE}, a 40 m grid) that lie in
 * a channel are only reported (the core is a field of the main and side ridges that also covers some valley floors near
 * the ends of a massif: in stage 2 of TT1 3 of 25 262 points, at 925–1184 m, none on a crest);</li>
 * <li>no water in a discontinuity: the largest height step 0.5 m apart of the surface with the rivers stays at most
 * {@value #MAX_STEP} per m over the whole complex (the old mountains with their rivers reach 8.8–9.7; the defects found in
 * stage 2 of TT1 were kettle pits of 80, tunnel lake pits of 16 and stream levees of 36 per m), and the water
 * surface of neighboring wet columns 0.5 m apart differs by at most {@value #MAX_WATER_STEP} m.</li>
 * </ul>
 */
class TatraRiverTest {
	private static final int MASSIFS = 2;
	/** Largest height step of the surface with the rivers (m per m). */
	private static final double MAX_STEP = 12;
	/**
	 * Core strength of a point well inside the river core ({@link TatraField#CORE} is its edge): paths do not cross the
	 * core, but the channel of a stream along its edge may touch it.
	 */
	private static final double INNER_CORE = 0.4;
	/** Largest step of the water surface between wet neighbors 0.5 m apart (m). */
	private static final int MAX_WATER_STEP = 2;

	private static boolean inChannel(ColumnSample s) {
		ColumnSample.Waters w = s.waters();
		return w.streamOrder() > 0 && w.channelDist() <= 0.5 * w.channelWidth();
	}

	@Test
	void noRiverOverTheMainRidge() {
		for (long seed : TatraSurvey.SEEDS) {
			LandscapeModel m = TatraSurvey.with(seed);
			double k = m.scale().mountainSpacing();
			for (LandscapeModel.Tatra t : TatraSurvey.nearest(seed, MASSIFS)) {
				String l = TatraSurvey.label(seed, t);
				double half = 0.8 * t.halfLength();
				int lines = (int) Math.floor(2 * half / 50);
				AtomicInteger wet = new AtomicInteger();
				IntStream.rangeClosed(0, lines).parallel().forEach(b -> {
					double v = -half + b * 50;
					double best = Double.NEGATIVE_INFINITY;
					double bu = 0;
					for (double u = -2_500 * k; u <= 2_500 * k; u += 10) {
						double h = m.sample(TatraSurvey.x(t, u, v), TatraSurvey.z(t, u, v)).surface();
						if (h > best) {
							best = h;
							bu = u;
						}
					}
					for (double du = -30; du <= 30; du += 2) {
						ColumnSample s = m.sample(TatraSurvey.x(t, bu + du, v), TatraSurvey.z(t, bu + du, v));
						if (s.hasWater() || inChannel(s)) {
							wet.incrementAndGet();
							return;
						}
					}
				});
				double uMin = -(TatraField.WS + 1_500) * k;
				double uMax = (TatraField.BASIN_CENTER + 1_000) * k;
				double vHalf = t.halfLength() + 1_500 * k;
				int nu = (int) ((uMax - uMin) / 40);
				int nv = (int) (2 * vHalf / 40);
				AtomicInteger core = new AtomicInteger();
				AtomicInteger coreInChannel = new AtomicInteger();
				IntStream.range(0, nu * nv).parallel().forEach(i -> {
					double u = uMin + (i % nu) * 40;
					double v = -vHalf + (i / nu) * 40;
					double x = TatraSurvey.x(t, u, v);
					double z = TatraSurvey.z(t, u, v);
					if (m.riverCoreStrength(x, z, false) >= INNER_CORE) {
						core.incrementAndGet();
						ColumnSample s = m.sample(x, z);
						if (inChannel(s)) {
							coreInChannel.incrementAndGet();
							System.out.printf(Locale.ROOT, "   river core point in a channel at (%.0f, %.0f): order %d, core strength %.3f, %.0f m, "
									+ "channel level %.0f m%n", x, z, s.waters().streamOrder(), m.riverCoreStrength(x, z, false), s.surface(),
									s.waters().channelLevel());
						}
					}
				});
				System.out.printf(Locale.ROOT, "%s: crest lines %d, wet crest lines %d; inner river core points %d, in a channel %d%n", l,
						lines + 1, wet.get(), core.get(), coreInChannel.get());
				assertTrue(core.get() > 1_000, l + ": no river core");
				assertTrue(wet.get() == 0, l + ": " + wet.get() + " crest lines with a river or water");
			}
		}
	}

	@Test
	void noWaterInADiscontinuity() {
		for (long seed : TatraSurvey.SEEDS) {
			LandscapeModel m = TatraSurvey.with(seed);
			LandscapeModel o = TatraSurvey.without(seed);
			for (LandscapeModel.Tatra t : TatraSurvey.nearest(seed, MASSIFS)) {
				String l = TatraSurvey.label(seed, t);
				double[] tatra = steps(m, t);
				double[] old = steps(o, t);
				System.out.printf(Locale.ROOT, "%s: steepest 0.5 m step of the surface with the rivers %.2f (old terrain %.2f); "
						+ "largest water step %.0f m (old terrain %.0f m), %.0f wet pairs%n", l, tatra[0], old[0], tatra[1], old[1],
						tatra[2]);
				assertTrue(tatra[0] <= MAX_STEP, l + ": a surface step of " + tatra[0] + " per m");
				assertTrue(tatra[1] <= MAX_WATER_STEP, l + ": a water step of " + tatra[1] + " m");
			}
		}
	}

	/**
	 * Over the complex to the end of its fades (as {@link TatraReliefTest#maxStep}): the largest surface step 0.5 m apart
	 * (per m), the largest water surface step between wet neighbors and the number of wet pairs.
	 */
	private static double[] steps(LandscapeModel m, LandscapeModel.Tatra t) {
		double k = m.scale().mountainSpacing();
		double d = 0.5;
		double uMin = -(TatraField.WS + TatraField.FADE_SOUTH1 + TatraField.EDGE) * k;
		double uMax = (TatraField.BASIN_CENTER + TatraField.BASIN / 2 + TatraField.FADE_NORTH + TatraField.EDGE) * k;
		double vHalf = t.halfLength() + (TatraField.FADE_END1 + TatraField.EDGE) * k;
		double[][] r = IntStream.range(0, 150_000).parallel().mapToObj(i -> {
			double fu = (i * 0.6180339887498949) % 1.0;
			double fv = (i * 0.7548776662466927) % 1.0;
			double x = TatraSurvey.x(t, uMin + fu * (uMax - uMin), -vHalf + fv * 2 * vHalf);
			double z = TatraSurvey.z(t, uMin + fu * (uMax - uMin), -vHalf + fv * 2 * vHalf);
			ColumnSample s = m.sample(x, z);
			ColumnSample sx = m.sample(x + d, z);
			ColumnSample sz = m.sample(x, z + d);
			// Step TT2: the sharp relief (pinnacles, gully walls) is steep on purpose (TatraReliefTest, TatraFormsTest).
			boolean sharp = m.tatraSharp(x, z) > 0 || m.tatraSharp(x + d, z) > 0 || m.tatraSharp(x, z + d) > 0;
			// And the shore walls of the tarns (a basin cut into a cirque floor under its headwall).
			for (ColumnSample c : new ColumnSample[] {s, sx, sz}) {
				sharp |= c.waters().standingWaterKind() == ColumnSample.StandingWaterKind.TARN && c.waters().s() < TatraWaters.BELT;
			}
			double step = sharp ? 0 : Math.max(Math.abs(sx.surface() - s.surface()), Math.abs(sz.surface() - s.surface())) / d;
			double water = 0;
			double pairs = 0;
			for (ColumnSample n : new ColumnSample[] {sx, sz}) {
				if (s.hasWater() && n.hasWater()) {
					water = Math.max(water, Math.abs(n.waterLevel() - s.waterLevel()));
					pairs++;
				}
			}
			return new double[] {step, water, pairs};
		}).toArray(double[][]::new);
		double[] out = new double[3];
		for (double[] a : r) {
			out[0] = Math.max(out[0], a[0]);
			out[1] = Math.max(out[1], a[1]);
			out[2] += a[2];
		}
		return out;
	}
}
