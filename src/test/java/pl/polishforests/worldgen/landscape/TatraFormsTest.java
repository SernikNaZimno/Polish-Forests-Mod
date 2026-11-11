package pl.polishforests.worldgen.landscape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.chunk.VerticalScale;

/**
 * Step TT2 (docs/04-pionowy-wycinek.md, "Projekt TT2"): the tarns and the fine relief of the Tatra massifs of the seeds
 * of {@link TatraSurvey} at gameplay scale.
 * <ul>
 * <li>{@link #everyMassifHasItsTarns}: 15–40 tarns per massif, the largest of the Morskie Oko type (behind a moraine), at
 * least three with an outlet brook, at least one brook joining the river network or the next tarn.</li>
 * <li>{@link #tarnBasinsAreClosed}: a 1 m scan of the shore band of every tarn: every water column of a tarn has each of its
 * four neighbors either water of the same tarn at its level or dry ground at least at the level (no leak, no water at two
 * levels side by side); the ground under the water lies below the level (no hanging water). The brooks fall
 * monotonically and never below the water they enter.</li>
 * <li>{@link #tarnsLieOnTheGround} (round 1 of the review of TT2): over all tarns of the massifs, the water is at most
 * {@value #DEPTH_TOLERANCE} blocks deeper than the bed of the basin center (no basin over an incised river valley held by a
 * wall), and at the edge of the belt the surface steps to the neighbors outside it at most {@value #EDGE_STEP} block more
 * than the ground without the tarn does (the raise of the dam fades out, no cliff).</li>
 * <li>{@link #tatraSurfaceZones}: bare rock, scree and snow patches on the massif, the snow only on its sites, the
 * rendzina only on the limestone.</li>
 * <li>{@link #finesHaveTheirSlopes}: the slopes in blocks (gameplay vertical scale): the gully floors 30–45° (median), the
 * scree cones 30–36° (median), the pinnacles steeper than 45° (median); a scree cone at half of the gully mouths or
 * more.</li>
 * </ul>
 */
class TatraFormsTest {
	/** Massifs per seed. */
	static final int MASSIFS = 2;
	/** Largest water depth over the bed of the basin center (blocks) and largest extra step at the belt edge (blocks). */
	static final double DEPTH_TOLERANCE = 3;
	static final double EDGE_STEP = 1;

	@Test
	void everyMassifHasItsTarns() {
		StringBuilder report = new StringBuilder();
		List<String> failures = new ArrayList<>();
		for (long seed : TatraSurvey.SEEDS) {
			LandscapeModel m = TatraSurvey.with(seed);
			for (LandscapeModel.Tatra t : TatraSurvey.nearest(seed, MASSIFS)) {
				List<TatraWaters.Tarn> tarns = tarns(m, t);
				int outlets = 0;
				int joined = 0;
				int moraine = 0;
				TatraWaters.Tarn largest = null;
				for (TatraWaters.Tarn tn : tarns) {
					outlets += tn.outlet() ? 1 : 0;
					joined += tn.outlet() && tn.brook().joined() ? 1 : 0;
					moraine += tn.moraine() ? 1 : 0;
					if (largest == null || tn.aq() * tn.bu() > largest.aq() * largest.bu()) {
						largest = tn;
					}
				}
				String label = TatraSurvey.label(seed, t);
				report.append(String.format(Locale.ROOT, "%s: %d tarns (%d behind moraines), %d outlets (%d joined), largest %.0f×%.0f m%s%n",
						label, tarns.size(), moraine, outlets, joined, largest == null ? 0 : 2 * largest.aq(),
						largest == null ? 0 : 2 * largest.bu(), largest != null && largest.morskie() ? " (Morskie Oko)" : ""));
				check(failures, tarns.size() >= 15 && tarns.size() <= 40, label + ": " + tarns.size() + " tarns");
				check(failures, largest.moraine(), label + ": the largest tarn is not behind a moraine");
				// Most tarns have no outlet (their basin lies under a higher lip; TT2-d): a few brooks per massif, and some of
				// them reach the river network (the others end in a pool above a steep step, the water under the scree).
				check(failures, outlets >= 3, label + ": " + outlets + " outlets");
				check(failures, joined >= 1, label + ": " + joined + " of " + outlets + " brooks join");
			}
		}
		System.out.print(report);
		assertTrue(failures.isEmpty(), String.join("; ", failures));
	}

	@Test
	void tarnBasinsAreClosed() {
		StringBuilder report = new StringBuilder();
		for (long seed : TatraSurvey.SEEDS) {
			LandscapeModel m = TatraSurvey.with(seed);
			LandscapeModel.Tatra t = TatraSurvey.nearest(seed, 1).get(0);
			String label = TatraSurvey.label(seed, t);
			AtomicInteger water = new AtomicInteger();
			AtomicInteger leaks = new AtomicInteger();
			AtomicInteger hanging = new AtomicInteger();
			List<String> examples = new ArrayList<>();
			for (TatraWaters.Tarn tn : tarns(m, t)) {
				double reach = Math.max(tn.aq(), tn.bu()) * tn.ring() + 4;
				int n = (int) Math.ceil(2 * reach);
				TatraField field = m.tatraField();
				IntStream.range(0, n * n).parallel().forEach(k -> {
					double x = Math.floor(tn.x() - reach) + 0.5 + k % n;
					double z = Math.floor(tn.z() - reach) + 0.5 + k / n;
					TatraField.Column c = field.column(x, z);
					TatraWaters.Hit h = c == null ? null : m.tatraWaters().hit(c);
					// Only the shore band: inside it every column is water of the tarn or ground at least at its level.
					if (h == null || h.tarn != tn || h.r < 0.6) {
						return;
					}
					ColumnSample s = m.sample(x, z);
					if (s.waterKind() != WaterKind.TARN) {
						return;
					}
					water.incrementAndGet();
					if (s.surface() >= s.waterLevel()) {
						hanging.incrementAndGet();
					}
					for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
						ColumnSample o = m.sample(x + d[0], z + d[1]);
						boolean ok = o.waterKind() == WaterKind.TARN ? o.waterLevel() == s.waterLevel()
								: o.waterKind() == WaterKind.NONE && o.surface() >= s.waterLevel();
						if (!ok) {
							leaks.incrementAndGet();
							synchronized (examples) {
								if (examples.size() < 5) {
									examples.add(String.format(Locale.ROOT, "(%.1f, %.1f) level %d → %s %d ground %.2f", x, z,
											s.waterLevel(), o.waterKind(), o.waterLevel(), o.surface()));
								}
							}
						}
					}
				});
				TatraWaters.Brook b = tn.brook();
				if (b != null) {
					for (int i = 1; i < b.steps(); i++) {
						assertTrue(b.level()[i] <= b.level()[i - 1], label + ": the brook of tarn " + tn.id() + " rises");
					}
					assertTrue(b.level()[0] < tn.level(), label + ": the brook of tarn " + tn.id() + " starts at the tarn level");
				}
			}
			report.append(String.format(Locale.ROOT, "%s: %d shore water columns, %d leaks, %d hanging %s%n", label, water.get(),
					leaks.get(), hanging.get(), examples));
			assertTrue(water.get() > 0, label + ": no tarn water");
			assertEquals(0, leaks.get(), label + ": leaks " + examples);
			assertEquals(0, hanging.get(), label + ": hanging water");
		}
		System.out.print(report);
	}

	@Test
	void tarnsLieOnTheGround() {
		VerticalScale vs = VerticalScale.GAMEPLAY;
		StringBuilder report = new StringBuilder();
		List<String> failures = new ArrayList<>();
		for (long seed : TatraSurvey.SEEDS) {
			LandscapeModel m = TatraSurvey.with(seed);
			TatraField field = m.tatraField();
			TatraWaters waters = m.tatraWaters();
			for (LandscapeModel.Tatra t : TatraSurvey.nearest(seed, MASSIFS)) {
				String label = TatraSurvey.label(seed, t);
				List<TatraWaters.Tarn> tarns = tarns(m, t);
				double worstDepth = Double.NEGATIVE_INFINITY;
				double worstEdge = Double.NEGATIVE_INFINITY;
				AtomicInteger edgePairs = new AtomicInteger();
				String worst = "";
				for (TatraWaters.Tarn tn : tarns) {
					double reach = Math.max(tn.aq(), tn.bu()) * tn.ring() + TatraWaters.BELT + 2;
					int n = (int) Math.ceil(2 * reach);
					double bed = vs.blocksForMeters(tn.level() - tn.depth());
					// [0] the deepest water under the bed of the center, [1] the largest extra step at the belt edge (blocks).
					double[] r = {Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
					IntStream.range(0, n * n).parallel().forEach(k -> {
						double x = Math.floor(tn.x() - reach) + 0.5 + k % n;
						double z = Math.floor(tn.z() - reach) + 0.5 + k / n;
						TatraField.Column c = field.column(x, z);
						TatraWaters.Hit h = c == null ? null : waters.hit(c);
						if (h == null || h.tarn != tn) {
							return;
						}
						boolean basin = h.inBasin();
						boolean edge = h.beltDistance >= TatraWaters.BELT - 3 && h.brookLevel == ColumnSample.NO_WATER;
						if (!basin && !edge || basin && (k / n) % 2 + (k % n) % 2 > 0) {
							// The basin on a 2 m grid, the edge of the belt every meter.
							return;
						}
						ColumnSample s = m.sample(x, z);
						double deeper = Double.NEGATIVE_INFINITY;
						if (basin && s.waterKind() == WaterKind.TARN) {
							deeper = bed - vs.blocksForMeters(s.surface());
						}
						double step = Double.NEGATIVE_INFINITY;
						if (edge) {
							double g = waters.ground(x, z);
							for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
								TatraField.Column oc = field.column(x + d[0], z + d[1]);
								TatraWaters.Hit oh = oc == null ? null : waters.hit(oc);
								if (oh != null && (oh.tarn != null || oh.brookLevel != ColumnSample.NO_WATER)) {
									continue;
								}
								ColumnSample o = m.sample(x + d[0], z + d[1]);
								double go = waters.ground(x + d[0], z + d[1]);
								step = Math.max(step, Math.abs(vs.blocksForMeters(s.surface()) - vs.blocksForMeters(o.surface()))
										- Math.abs(vs.blocksForMeters(g) - vs.blocksForMeters(go)));
								edgePairs.incrementAndGet();
							}
						}
						synchronized (r) {
							r[0] = Math.max(r[0], deeper);
							r[1] = Math.max(r[1], step);
						}
					});
					if (r[0] > worstDepth) {
						worstDepth = r[0];
						worst = String.format(Locale.ROOT, "tarn (%.0f, %.0f) level %d", tn.x(), tn.z(), tn.level());
					}
					worstEdge = Math.max(worstEdge, r[1]);
					check(failures, r[0] <= DEPTH_TOLERANCE, String.format(Locale.ROOT, "%s: tarn (%.0f, %.0f) level %d is %.1f blocks "
							+ "deeper than its bed", label, tn.x(), tn.z(), tn.level(), r[0]));
					check(failures, r[1] <= EDGE_STEP, String.format(Locale.ROOT, "%s: tarn (%.0f, %.0f) level %d has a step of %.1f "
							+ "blocks at the edge of its belt", label, tn.x(), tn.z(), tn.level(), r[1]));
				}
				report.append(String.format(Locale.ROOT, "%s: %d tarns, deepest water %.1f blocks under the bed of the center (%s), "
						+ "largest extra step at the belt edge %.2f blocks over %d pairs%n", label, tarns.size(), worstDepth, worst,
						worstEdge, edgePairs.get()));
				check(failures, edgePairs.get() > 1_000, label + ": " + edgePairs.get() + " belt edge pairs");
			}
		}
		System.out.print(report);
		assertTrue(failures.isEmpty(), String.join("; ", failures));
	}

	@Test
	void finesHaveTheirSlopes() {
		StringBuilder report = new StringBuilder();
		List<String> failures = new ArrayList<>();
		for (long seed : TatraSurvey.SEEDS) {
			LandscapeModel m = TatraSurvey.with(seed);
			LandscapeModel.Tatra t = TatraSurvey.nearest(seed, 1).get(0);
			String label = TatraSurvey.label(seed, t);
			TatraField field = m.tatraField();
			double k = m.scale().mountainSpacing();
			double step = 3;
			double vHalf = 0.6 * t.halfLength();
			double uMin = -8_000 * k;
			double uMax = 7_000 * k;
			int nu = (int) ((uMax - uMin) / step);
			int nv = (int) (2 * vHalf / step);
			List<Double> gully = java.util.Collections.synchronizedList(new ArrayList<>());
			List<Double> cone = java.util.Collections.synchronizedList(new ArrayList<>());
			List<Double> pin = java.util.Collections.synchronizedList(new ArrayList<>());
			Map<String, boolean[]> mouths = new ConcurrentHashMap<>();
			IntStream.range(0, nu * nv).parallel().forEach(i -> {
				double x = TatraSurvey.x(t, uMin + (i % nu) * step, -vHalf + (i / nu) * step);
				double z = TatraSurvey.z(t, uMin + (i % nu) * step, -vHalf + (i / nu) * step);
				TatraField.Column c = field.column(x, z);
				if (c == null || c.weight < 0.95 || Double.isNaN(c.un)) {
					return;
				}
				boolean g = c.gully > 3;
				boolean s = c.scree > 1.5;
				boolean p = c.spike > 8;
				if (!g && !s && !p) {
					return;
				}
				double slope = slope(m, x, z);
				if (g) {
					gully.add(slope);
				}
				if (s) {
					cone.add(slope);
				}
				if (p) {
					pin.add(slope);
				}
				if (g || s) {
					String key = gullyKey(field, c, x, z);
					if (key != null) {
						boolean[] f = mouths.computeIfAbsent(key, kk -> new boolean[2]);
						synchronized (f) {
							f[0] |= g;
							f[1] |= s;
						}
					}
				}
			});
			int withGully = 0;
			int withCone = 0;
			for (boolean[] f : mouths.values()) {
				if (f[0]) {
					withGully++;
					withCone += f[1] ? 1 : 0;
				}
			}
			double mg = median(gully);
			double mc = median(cone);
			double mp = median(pin);
			report.append(String.format(Locale.ROOT,
					"%s: gully floors %d columns median %.1f°, cones %d median %.1f°, pinnacles %d median %.1f°, cones at %d of %d gullies%n",
					label, gully.size(), mg, cone.size(), mc, pin.size(), mp, withCone, withGully));
			check(failures, gully.size() > 100 && mg >= 30 && mg <= 45, label + ": gully floors " + mg);
			check(failures, cone.size() > 100 && mc >= 30 && mc <= 36, label + ": cones " + mc);
			check(failures, pin.size() > 50 && mp > 45, label + ": pinnacles " + mp);
			check(failures, withGully > 10 && withCone >= 0.5 * withGully, label + ": cones at " + withCone + " of " + withGully);
		}
		System.out.print(report);
		assertTrue(failures.isEmpty(), String.join("; ", failures));
	}

	/**
	 * The zones and soils of the Tatra surface on a 12 m grid over the massif nearest the origin: bare rock, scree and snow
	 * patches occur; the snow lies only on north-facing ground above {@value TatraZonesProbe#SNOW_LOW} m; the rendzina
	 * only on limestone and dolomite, the ranker and podzol of the silicate rocks never there.
	 */
	@Test
	void tatraSurfaceZones() {
		StringBuilder report = new StringBuilder();
		List<String> failures = new ArrayList<>();
		for (long seed : TatraSurvey.SEEDS) {
			LandscapeModel m = TatraSurvey.with(seed);
			pl.polishforests.worldgen.habitat.HabitatClassifier hc = new pl.polishforests.worldgen.habitat.HabitatClassifier(seed, m,
					pl.polishforests.worldgen.habitat.HabitatClassifier.Mode.NATURAL);
			LandscapeModel.Tatra t = TatraSurvey.nearest(seed, 1).get(0);
			String label = TatraSurvey.label(seed, t);
			double step = 12;
			double k = m.scale().mountainSpacing();
			double uMin = -7_000 * k;
			double uMax = 6_500 * k;
			int nu = (int) ((uMax - uMin) / step);
			int nv = (int) (2 * t.halfLength() / step);
			Map<String, AtomicInteger> census = new ConcurrentHashMap<>();
			AtomicInteger snowOff = new AtomicInteger();
			AtomicInteger rendzinaOff = new AtomicInteger();
			AtomicInteger silicateOnLime = new AtomicInteger();
			IntStream.range(0, nu * nv).parallel().forEach(i -> {
				double x = TatraSurvey.x(t, uMin + (i % nu) * step, -t.halfLength() + (i / nu) * step);
				double z = TatraSurvey.z(t, uMin + (i % nu) * step, -t.halfLength() + (i / nu) * step);
				ColumnSample s = m.sample(x, z);
				Substrate rock = s.terrain().rock();
				if (s.hasWater() || rock == null) {
					return;
				}
				int code = hc.classify(s, x, z);
				pl.polishforests.worldgen.habitat.Zone zone = pl.polishforests.worldgen.habitat.Habitat.zone(code);
				pl.polishforests.worldgen.habitat.Soil soil = pl.polishforests.worldgen.habitat.Habitat.soil(code);
				census.computeIfAbsent(rock + "/" + zone, kk -> new AtomicInteger()).incrementAndGet();
				census.computeIfAbsent(rock + "/" + soil, kk -> new AtomicInteger()).incrementAndGet();
				if (zone == pl.polishforests.worldgen.habitat.Zone.SNOW_PATCH) {
					double e = s.terrain().aspect();
					if (s.surface() < TatraZonesProbe.SNOW_LOW || Double.isNaN(e) || e > 50 && e < 310) {
						snowOff.incrementAndGet();
					}
				}
				boolean lime = rock == Substrate.LIMESTONE || rock == Substrate.DOLOMITE;
				if (soil == pl.polishforests.worldgen.habitat.Soil.RENDZINA && !lime) {
					rendzinaOff.incrementAndGet();
				}
				if (lime && (soil == pl.polishforests.worldgen.habitat.Soil.RANKER
						|| soil == pl.polishforests.worldgen.habitat.Soil.MOUNTAIN_PODZOL)) {
					silicateOnLime.incrementAndGet();
				}
			});
			java.util.TreeMap<String, Integer> sorted = new java.util.TreeMap<>();
			census.forEach((kk, v) -> sorted.put(kk, v.get()));
			report.append(label).append(": ").append(sorted).append(String.format(Locale.ROOT,
					"; snow off its sites %d, rendzina off the limestone %d, silicate soils on the limestone %d%n", snowOff.get(),
					rendzinaOff.get(), silicateOnLime.get()));
			for (String zone : new String[] {"BARE_ROCK", "SCREE", "SNOW_PATCH"}) {
				check(failures, sorted.entrySet().stream().anyMatch(e -> e.getKey().endsWith("/" + zone) && e.getValue() > 20),
						label + ": no " + zone);
			}
			check(failures, sorted.entrySet().stream().anyMatch(e -> e.getKey().endsWith("/RENDZINA") && e.getValue() > 20),
					label + ": no rendzina");
			check(failures, snowOff.get() == 0, label + ": snow off its sites " + snowOff.get());
			check(failures, rendzinaOff.get() == 0, label + ": rendzina off the limestone " + rendzinaOff.get());
			check(failures, silicateOnLime.get() == 0, label + ": silicate soils on the limestone " + silicateOnLime.get());
		}
		System.out.print(report);
		assertTrue(failures.isEmpty(), String.join("; ", failures));
	}

	/** Bounds of the snow patches as the test checks them (the threshold of TatraZones less its jitter). */
	static final class TatraZonesProbe {
		static final double SNOW_LOW = 1_640;
	}

	static void check(List<String> failures, boolean ok, String message) {
		if (!ok) {
			failures.add(message);
		}
	}

	/** The tarns of the massif. */
	static List<TatraWaters.Tarn> tarns(LandscapeModel m, LandscapeModel.Tatra t) {
		TatraField field = m.tatraField();
		TatraField.Column c = field.column(t.x(), t.z());
		List<TatraWaters.Tarn> out = new ArrayList<>(m.tatraWaters().tarns(c.massif));
		return out;
	}

	/** Slope of the land height in blocks at a point (degrees, central differences 1 m). */
	static double slope(LandscapeModel m, double x, double z) {
		VerticalScale vs = VerticalScale.GAMEPLAY;
		double gx = vs.blocksForMeters(m.landElevation(x + 1, z)) - vs.blocksForMeters(m.landElevation(x - 1, z));
		double gz = vs.blocksForMeters(m.landElevation(x, z + 1)) - vs.blocksForMeters(m.landElevation(x, z - 1));
		return Math.toDegrees(Math.atan(Math.hypot(gx, gz) / 2));
	}

	/** The gully whose cut or cone the column holds: valley, wall and index, or null. */
	static String gullyKey(TatraField field, TatraField.Column c, double x, double z) {
		double width = c.north ? c.massif.wn() : c.massif.ws();
		double along = field.gullyAlong(x, z, c.un, width);
		int sigma = c.q >= 0 ? 1 : -1;
		long n0 = (long) Math.floor(along / (TatraField.GULLY_SPACING * field.k()));
		String best = null;
		double bd = Double.POSITIVE_INFINITY;
		for (int sg = -1; sg <= 1; sg += 2) {
			for (long n = n0 - 1; n <= n0 + 1; n++) {
				TatraField.Gully g = field.gully(c.massif, c.north, c.valley, sg, n);
				if (g == null) {
					continue;
				}
				double d = Math.abs(along - g.center()) + (sg == sigma ? 0 : 1_000);
				if (d < bd && d < 3 * g.halfWidth() + (sg == sigma ? 0 : 1_000)) {
					bd = d;
					best = c.massif.i() + "/" + c.massif.j() + "/" + c.north + "/" + c.valley + "/" + sg + "/" + n;
				}
			}
		}
		return best;
	}

	static double median(List<Double> xs) {
		if (xs.isEmpty()) {
			return Double.NaN;
		}
		double[] a = xs.stream().mapToDouble(Double::doubleValue).toArray();
		Arrays.sort(a);
		return a[a.length / 2];
	}
}
