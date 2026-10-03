package pl.polishforests.worldgen.landscape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.habitat.AltitudinalBelts;

class LandscapeModelTest {
	private static final long SEED = 20260927L;

	@Test
	void sameSeedGivesSameTerrain() {
		LandscapeModel a = new LandscapeModel(SEED, 1.0);
		LandscapeModel b = new LandscapeModel(SEED, 1.0);
		for (int i = 0; i < 200; i++) {
			double x = i * 7_919.0 - 500_000;
			double z = i * -3_571.0 + 250_000;
			assertEquals(a.sample(x, z), b.sample(x, z));
		}
	}

	/** For a given seed, scale and point the result does not depend on the query order, the new fields included (records compared with equals). */
	@Test
	void habitatFieldsDoNotDependOnQueryOrder() {
		for (LandscapeScale scale : new LandscapeScale[] {LandscapeScale.REALISTIC, LandscapeScale.GAMEPLAY}) {
			LandscapeModel a = new LandscapeModel(SEED, scale, 1.0);
			LandscapeModel b = new LandscapeModel(SEED, scale, 1.0);
			double step = scale == LandscapeScale.REALISTIC ? 1_501.0 : 97.0;
			ColumnSample[] first = new ColumnSample[200];
			for (int i = 0; i < first.length; i++) {
				first[i] = a.sample(i * step - 100 * step, (i % 20) * step * 7);
			}
			// Reverse order on the second model: different cache states.
			for (int i = first.length - 1; i >= 0; i--) {
				assertEquals(first[i], b.sample(i * step - 100 * step, (i % 20) * step * 7));
			}
		}
	}

	/**
	 * The new sample fields (M2, S2 and S3) are finite where they make sense and lie within their ranges. NaN only
	 * as "not applicable", +∞ only as "out of range".
	 */
	@Test
	void habitatFieldsAreFiniteAndInRange() {
		for (LandscapeScale scale : new LandscapeScale[] {LandscapeScale.REALISTIC, LandscapeScale.GAMEPLAY}) {
			LandscapeModel m = new LandscapeModel(SEED, scale, 1.0);
			int rivers = 0;
			int standing = 0;
			int floors = 0;
			int flysch = 0;
			Set<Landform> seen = EnumSet.noneOf(Landform.class);
			List<double[]> points = new ArrayList<>();
			double unit = scale == LandscapeScale.REALISTIC ? 1.0 : 0.05;
			for (int i = 0; i < 6_000; i++) {
				points.add(new double[] {(i % 77) * 7_919.0 * unit - 300_000 * unit, (i / 77) * 7_877.0 * unit - 300_000 * unit});
			}
			// Dense patches near water (valley floors, channels, lakes and kettle ponds) and in the Beskids (the "beskids"
			// patch from the golden test for this seed).
			double[] beskids = scale == LandscapeScale.REALISTIC ? new double[] {154_834, 1_058_738}
					: new double[] {27_609, 3_254};
			for (double[] site : new double[][] {LandscapePreview.findRiver(m),
					LandscapePreview.find(m, LandscapeType.MORAINE_PLATEAU, true), beskids}) {
				for (int i = 0; i < 10_000; i++) {
					points.add(new double[] {site[0] + (i % 100) * 7.0 - 350, site[1] + (i / 100) * 7.0 - 350});
				}
			}
			for (double[] p : points) {
				ColumnSample c = m.sample(p[0], p[1]);
				ColumnSample.Terrain t = c.terrain();
				ColumnSample.Waters w = c.waters();
				String at = " at " + p[0] + "," + p[1] + ": " + c;
				assertTrue(Double.isFinite(t.rawSurface()) && Double.isFinite(t.coastD()) && Double.isFinite(t.convexity()), "terrain" + at);
				double sum = t.wOutwashPlain() + t.wMorainePlateau() + t.wOldGlacialPlain() + t.wFoothills() + t.wBeskids();
				assertEquals(1.0, sum, 1e-9, "type weights" + at);
				assertTrue(t.wCoastland() >= 0 && t.wCoastland() <= 1, "wCoastland" + at);
				assertTrue(c.waterKind() == WaterKind.SEA ? Double.isNaN(t.sandiness()) : t.sandiness() >= 0 && t.sandiness() <= 1,
						"sandiness" + at);
				assertTrue(t.duneHeight() >= 0 && t.duneHeight() <= 23, "duneHeight" + at);
				assertTrue(t.massif() >= 0 && t.massif() <= 1, "massif" + at);
				assertTrue(t.cliffHeight() >= 0 && (t.cliffHeight() > 0) == t.has(Landform.CLIFF), "cliffHeight" + at);
				// Large massif (E12): highest terrain within 3 km·mspace, only in the Beskids from AltitudinalBelts.SUMMIT_FROM.
				assertTrue(Double.isFinite(t.summit()), "summit" + at);
				if (t.wBeskids() > 0 && c.surface() >= AltitudinalBelts.SUMMIT_FROM) {
					assertTrue(t.summit() >= c.surface() - 60 && t.summit() <= 1_760, "summit in the Beskids" + at);
				} else {
					assertEquals(0.0, t.summit(), "summit outside the Beskids or low" + at);
				}
				assertTrue(t.lowShore() >= 0 && t.lowShore() <= 1, "lowShore" + at);
				boolean coastalLand = c.type() == LandscapeType.COASTLAND && c.waterKind() != WaterKind.SEA;
				assertTrue(coastalLand ? t.bareSandWidth() > 0 : Double.isNaN(t.bareSandWidth()), "bareSandWidth" + at);
				if (t.wFoothills() + t.wBeskids() > 0) {
					flysch++;
					assertTrue(t.ridgeProfile() >= 0 && t.ridgeProfile() <= 1.05, "ridgeProfile" + at);
				} else {
					assertTrue(Double.isNaN(t.ridgeProfile()), "ridge outside flysch" + at);
				}
				for (Landform f : Landform.values()) {
					if (t.has(f)) {
						assertTrue(Landform.FROM_SAMPLE.contains(f), "landform not from the sample" + at);
						seen.add(f);
					}
				}
				if (w.streamOrder() > 0) {
					rivers++;
					assertTrue(w.streamOrder() <= 3, "streamOrder" + at);
					assertTrue(Double.isFinite(w.channelDist()) && w.channelWidth() >= 1.5 - 1e-9
							&& w.channelWidth() <= 400 && Double.isFinite(w.channelLevel()), "channel" + at);
					assertTrue(w.floorHalfWidth() > 0 && Double.isFinite(w.floorHalfWidth()), "floor" + at);
					assertTrue(w.channelGradient() >= 0 && Double.isFinite(w.channelGradient()), "channelGradient" + at);
					if (w.inFloor()) {
						floors++;
						assertTrue(w.u() >= 0 && w.u() <= 1, "u" + at);
					} else {
						assertTrue(Double.isNaN(w.u()), "u outside the floor" + at);
					}
				} else {
					assertTrue(w.channelDist() == Double.POSITIVE_INFINITY && !w.inFloor() && !w.headwaters()
							&& !w.convexBank() && Double.isNaN(w.channelWidth()), "no watercourse" + at);
				}
				if (w.standingWaterKind() != ColumnSample.StandingWaterKind.NONE) {
					standing++;
					assertTrue(Double.isFinite(w.s()) && w.shoreLevel() != ColumnSample.NO_WATER
							&& w.standingWaterRadius() >= 0 && Double.isFinite(w.standingWaterRadius()) && w.lakeId() != 0,
							"standing water" + at);
					assertTrue(!w.ombrotrophicPeat() || w.standingWaterKind() == ColumnSample.StandingWaterKind.KETTLE_BOG,
							"ombrotrophic peat outside a kettle bog" + at);
					assertTrue(w.s() <= ringOf(w.standingWaterKind(), scale) + 1e-9, "s outside the standing water belt" + at);
				} else {
					assertTrue(w.s() == Double.POSITIVE_INFINITY && w.shoreLevel() == ColumnSample.NO_WATER,
							"no standing water" + at);
				}
				if (c.waterKind().isLake()) {
					assertTrue(w.s() <= 0, "lake without a negative distance from the shore" + at);
				}
				if (c.waterKind() == WaterKind.RIVER) {
					assertTrue(w.channelDist() <= 0, "d > 0 in the channel" + at);
				}
				ColumnSample.Region reg = c.region();
				assertTrue(reg.oceanicity() >= 0 && reg.oceanicity() <= 1 && reg.mountainInfluence() >= 0
						&& reg.mountainInfluence() <= 1, "regional fields" + at);
				assertTrue(Double.isFinite(t.sBar()) && t.slope() >= 0 && t.slope() < 90
						&& (Double.isNaN(t.aspect()) || t.aspect() >= 0 && t.aspect() < 360), "terrain grid" + at);
			}
			System.out.println(String.format(Locale.ROOT,
					"[habitat fields] %s: %d columns, near watercourses %d, on the floor %d, near standing water %d, with flysch %d, landforms %s",
					scale.id(), points.size(), rivers, floors, standing, flysch, seen));
			assertTrue(rivers > 1_000 && floors > 500 && standing > 200 && flysch > 100, "too few columns near water or in the mountains");
		}
	}

	/** Standing water belt (m) in which {@link ColumnSample.Waters#s()} is finite (see the field javadoc). */
	private static double ringOf(ColumnSample.StandingWaterKind kind, LandscapeScale scale) {
		double k = scale.local();
		return switch (kind) {
			case SINK_LAKE -> 150 * k;
			case TUNNEL_VALLEY_LAKE -> Math.max(70, 150 * k);
			case OXBOW_LAKE -> 40 * k;
			default -> 45;
		};
	}

	/**
	 * The ring around a sink lake and a tunnel valley lake reaches 150 m·k beyond the shore (alder carr zone,
	 * §4.4 of the M2 plan): going outwards from the lake, s grows up to the belt and is +∞ beyond it.
	 */
	@Test
	void lakeRingsReachAlderCarrBand() {
		for (LandscapeScale scale : new LandscapeScale[] {LandscapeScale.REALISTIC, LandscapeScale.GAMEPLAY}) {
			LandscapeModel m = new LandscapeModel(SEED, scale, 1.0);
			boolean real = scale == LandscapeScale.REALISTIC;
			for (ColumnSample.StandingWaterKind kind : new ColumnSample.StandingWaterKind[] {
					ColumnSample.StandingWaterKind.SINK_LAKE, ColumnSample.StandingWaterKind.TUNNEL_VALLEY_LAKE}) {
				double[] site = RiverNetworkTest.find(m, c -> c.waterKind() == WaterKind.LAKE
						&& c.waters().standingWaterKind() == kind, real ? 900 : 60);
				assertTrue(site != null, "no lake " + kind + " at scale " + scale.id());
				double ring = ringOf(kind, scale);
				double best = 0;
				for (int a = 0; a < 16; a++) {
					double ang = a * Math.PI / 8;
					double far = 0;
					for (int q = 0; q < 6_000; q++) {
						ColumnSample.Waters w = m.sample(site[0] + Math.cos(ang) * q, site[1] + Math.sin(ang) * q).waters();
						if (w.standingWaterKind() == kind) {
							far = Math.max(far, w.s());
						} else if (w.s() == Double.POSITIVE_INFINITY && far > 0) {
							break;
						}
					}
					best = Math.max(best, far);
				}
				System.out.println(String.format(Locale.ROOT, "[lake ring] %s %s: largest s %.1f m (belt %.0f m)",
						scale.id(), kind, best, ring));
				assertTrue(best > ring - 3 && best <= ring + 1e-9, "ring " + kind + ": largest s " + best);
			}
		}
	}

	/** Sandiness is a noise quantile: its distribution is close to uniform on [0, 1]. */
	@Test
	void sandinessIsUniform() {
		LandscapeModel m = new LandscapeModel(SEED, 1.0);
		double[] v = new double[10_000];
		int n = 0;
		for (int i = 0; i < v.length; i++) {
			ColumnSample c = m.sample((i % 100) * 1_217.0 - 60_000, (i / 100) * 1_193.0 - 60_000);
			if (c.waterKind() != WaterKind.SEA) {
				v[n++] = c.terrain().sandiness();
			}
		}
		assertTrue(n > 5_000, "too little land: " + n);
		v = Arrays.copyOf(v, n);
		Arrays.sort(v);
		for (int q = 1; q < 10; q++) {
			assertEquals(q / 10.0, v[q * v.length / 10], 0.05, "sandiness quantile " + q + "/10");
		}
	}

	@Test
	void mountainsNeverTouchLowlandRegions() {
		LandscapeModel m = new LandscapeModel(SEED, 1.0);
		int beskids = 0;
		for (long cx = -40; cx <= 40; cx++) {
			for (long cz = -40; cz <= 40; cz++) {
				if (m.regionType(cx, cz) != LandscapeType.BESKIDS) {
					continue;
				}
				beskids++;
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						assertFalse(m.regionType(cx + dx, cz + dz).isLowland(),
								"Beskids in cell " + cx + "," + cz + " border the lowland " + m.regionType(cx + dx, cz + dz) + " at " + (cx + dx) + "," + (cz + dz));
					}
				}
			}
		}
		assertTrue(beskids > 0, "no Beskids in the test area of about 5000 × 5000 km");
	}

	@Test
	void allRegionTypesOccur() {
		LandscapeModel m = new LandscapeModel(SEED, 1.0);
		int[] count = new int[LandscapeType.values().length];
		for (long cx = -40; cx <= 40; cx++) {
			for (long cz = -40; cz <= 40; cz++) {
				count[m.regionType(cx, cz).ordinal()]++;
			}
		}
		StringBuilder sb = new StringBuilder();
		for (LandscapeType t : LandscapeType.values()) {
			sb.append(t).append('=').append(count[t.ordinal()]).append(' ');
		}
		System.out.println("Macroregion distribution: " + sb);
		for (LandscapeType t : LandscapeType.values()) {
			if (t.isRegionType()) {
				assertTrue(count[t.ordinal()] > 0, "missing type " + t);
			}
		}
	}

	@Test
	void elevationsStayWithinPolishRange() {
		LandscapeModel m = new LandscapeModel(SEED, 1.0);
		for (int i = 0; i < 4_000; i++) {
			double x = (i % 63) * 31_337.0 - 1_000_000;
			double z = (i / 63) * 29_989.0 - 1_000_000;
			ColumnSample s = m.sample(x, z);
			double max = s.type().isLowland() ? 420 : 1_800;
			assertTrue(s.surface() > -120 && s.surface() < max,
					"elevation out of range: " + s + " at " + x + "," + z);
		}
	}

	/**
	 * Standing water must be surrounded by land higher than its surface or by water at the same level.
	 * For rivers a 1 m step is allowed (a known simplification of the M1 model).
	 */
	@Test
	void waterIsAlwaysContained() {
		assertWaterContained(new LandscapeModel(SEED, 1.0));
	}

	@Test
	void waterIsAlwaysContainedAtGameplayScale() {
		assertWaterContained(new LandscapeModel(SEED, LandscapeScale.GAMEPLAY, 1.0));
	}

	@Test
	void gameplayScaleHasSmallRegionsAndAllTypesNearby() {
		LandscapeModel m = new LandscapeModel(SEED, LandscapeScale.GAMEPLAY, 1.0);
		assertEquals(1_400.0, m.regionSize(), 1e-9);
		int[] count = new int[LandscapeType.values().length];
		for (long cx = -60; cx <= 60; cx++) {
			for (long cz = -60; cz <= 60; cz++) {
				count[m.regionType(cx, cz).ordinal()]++;
				if (m.regionType(cx, cz) == LandscapeType.BESKIDS) {
					for (int dx = -1; dx <= 1; dx++) {
						for (int dz = -1; dz <= 1; dz++) {
							assertFalse(m.regionType(cx + dx, cz + dz).isLowland());
						}
					}
				}
			}
		}
		StringBuilder sb = new StringBuilder();
		for (LandscapeType t : LandscapeType.values()) {
			sb.append(t).append('=').append(count[t.ordinal()]).append(' ');
			if (t.isRegionType()) {
				assertTrue(count[t.ordinal()] > 0, "within a radius of about 85 km, missing type " + t);
			}
		}
		System.out.println("Distribution (gameplay scale, 170 x 170 km): " + sb);
	}

	/**
	 * Large Beskid massifs (M2-8, design §3.2): in the windows of {@link GreatMassifSurvey} at least 6 massifs at
	 * realistic scale and 15 at gameplay scale, every realized summit at least the target − 40 m and at most 1750 m, at
	 * least one summit of 1700 m or more at each scale. The massif centers are at least GM_SEPARATION · Ra apart, so
	 * the reaches (less than 1.2 Ra) never overlap. Outside the reach of the massifs the terrain before valleys and
	 * lakes is the M1 terrain (the frozen copy {@code landscape.m1}): 10,000 random Beskids points in each window.
	 * The nearest massif from {@link LandscapeModel#nearestGreatMassif} is the nearest one of the window.
	 */
	@Test
	void greatMassifsReachTarget() {
		for (LandscapeScale sc : new LandscapeScale[] {LandscapeScale.REALISTIC, LandscapeScale.GAMEPLAY}) {
			GreatMassifSurvey.Window w = GreatMassifSurvey.window(sc);
			List<GreatMassifSurvey.Massif> list = GreatMassifSurvey.survey(sc);
			double ra = GreatMassifSurvey.ra(sc);
			StringBuilder sb = new StringBuilder();
			double highest = 0;
			for (GreatMassifSurvey.Massif s : list) {
				LandscapeModel.GreatMassif g = s.massif();
				sb.append(String.format(Locale.ROOT, "%n  (%.0f, %.0f) target %.0f m, summit %.0f m (%+.0f)", g.x(), g.z(),
						g.targetSummit(), s.summit(), s.summit() - g.targetSummit()));
				highest = Math.max(highest, s.summit());
				assertTrue(s.summit() >= g.targetSummit() - 40 && s.summit() <= 1_750, sc.id() + ": massif (" + g.x() + ", "
						+ g.z() + ") target " + g.targetSummit() + " m, summit " + s.summit() + " m");
				for (GreatMassifSurvey.Massif o : list) {
					if (o != s) {
						assertTrue(Math.hypot(o.massif().x() - g.x(), o.massif().z() - g.z()) >= LandscapeModel.GM_SEPARATION * ra,
								sc.id() + ": massifs closer than the separation: " + g + ", " + o.massif());
					}
				}
			}
			System.out.printf(Locale.ROOT, "%s: %d large massifs in the window %.0f km, highest summit %.0f m:%s%n", sc.id(),
					list.size(), w.side() / 1_000, highest, sb);
			assertTrue(list.size() >= w.minCount(), sc.id() + ": " + list.size() + " massifs, expected at least " + w.minCount());
			assertTrue(highest >= 1_700, sc.id() + ": highest massif " + highest + " m");

			LandscapeModel m = new LandscapeModel(SEED, sc, 1.0);
			LandscapeModel.GreatMassif nearest = m.nearestGreatMassif(0, 0);
			assertTrue(nearest != null, sc.id() + ": no large massif");
			if (Math.abs(nearest.x()) < w.side() / 2 - Math.abs(w.cx()) && Math.abs(nearest.z()) < w.side() / 2 - Math.abs(w.cz())) {
				double d = Math.hypot(nearest.x(), nearest.z());
				for (GreatMassifSurvey.Massif s : list) {
					assertTrue(Math.hypot(s.massif().x(), s.massif().z()) >= d, sc.id() + ": nearestGreatMassif is not the nearest");
				}
				assertTrue(list.stream().anyMatch(s -> s.massif().equals(nearest)), sc.id() + ": nearest massif not in the window");
			}

			// Outside the reach of the massifs the terrain before valleys and lakes is the M1 terrain.
			pl.polishforests.worldgen.landscape.m1.LandscapeModel old = new pl.polishforests.worldgen.landscape.m1.LandscapeModel(SEED,
					sc == LandscapeScale.REALISTIC ? pl.polishforests.worldgen.landscape.m1.LandscapeScale.REALISTIC
							: pl.polishforests.worldgen.landscape.m1.LandscapeScale.GAMEPLAY, 1.0);
			java.util.Random rnd = new java.util.Random(5);
			int compared = 0;
			int inReach = 0;
			for (int t = 0; t < 4_000_000 && compared < 10_000; t++) {
				double x = w.minX() + rnd.nextDouble() * w.side();
				double z = w.minZ() + rnd.nextDouble() * w.side();
				if (m.typeWeights(x, z)[LandscapeType.BESKIDS.ordinal()] <= 0.5) {
					continue;
				}
				if (m.greatMassifStrength(x, z) > 0) {
					inReach++;
					continue;
				}
				compared++;
				double h = m.landElevation(x, z);
				double h1 = old.landElevation(x, z);
				assertEquals(h1, h, 1e-6, sc.id() + ": terrain outside the massifs changed at (" + x + ", " + z + ")");
			}
			System.out.printf(Locale.ROOT, "%s: %d Beskids points outside the massifs equal to M1 (%d in reach skipped)%n",
					sc.id(), compared, inReach);
			assertTrue(compared >= 10_000, sc.id() + ": only " + compared + " points compared");
		}
	}

	/**
	 * The top of a large massif is a dome and not a plateau (review of the design, soft ceiling in step K2): on every
	 * massif of {@link GreatMassifSurvey} the area at or above 1650 m (alpine grassland) is at most 0.35 of the belt
	 * 1390–1650 m (dwarf pine) and at most 2.5 km² at realistic scale and 0.25 km² at gameplay scale. On Babia Gora
	 * the alpine belt is a narrow summit dome, several times smaller than the dwarf pine belt.
	 */
	@Test
	void greatMassifSummitIsNotPlateau() {
		for (LandscapeScale sc : new LandscapeScale[] {LandscapeScale.REALISTIC, LandscapeScale.GAMEPLAY}) {
			double limit = sc == LandscapeScale.REALISTIC ? 2.5 : 0.25;
			StringBuilder sb = new StringBuilder();
			List<String> bad = new ArrayList<>();
			for (GreatMassifSurvey.Massif s : GreatMassifSurvey.survey(sc)) {
				LandscapeModel.GreatMassif g = s.massif();
				double ratio = s.area1650() / Math.max(1e-9, s.belt());
				String line = String.format(Locale.ROOT, "(%.0f, %.0f) summit %.0f m: >= 1390 m %.2f km², >= 1650 m %.3f km², "
						+ "ratio %.2f", g.x(), g.z(), s.summit(), s.area1390(), s.area1650(), ratio);
				sb.append(System.lineSeparator()).append("  ").append(line);
				if (ratio > 0.35 || s.area1650() > limit) {
					bad.add(line);
				}
			}
			System.out.printf(Locale.ROOT, "%s: summit domes (limits: ratio <= 0.35, >= 1650 m <= %.2f km²):%s%n", sc.id(),
					limit, sb);
			assertTrue(bad.isEmpty(), sc.id() + ": plateau on " + bad);
		}
	}

	/**
	 * The dome of a large massif is a field over the region cells and not a property of the Beskids cell (review of step
	 * K2). At gameplay scale the Beskids band is only one or two region cells wide, so the reach of 17 of the 25
	 * massifs of {@link GreatMassifSurvey} crosses into foothills cells, also in the core. The first version of K2 lifted
	 * only the Beskids cells, and the dome ended at the cell boundary in a wall of up to 1100 m over the 280 m blending
	 * belt. In the columns of the reach where both the Beskids and the foothills cell weigh at least 0.05, the height of
	 * the two cells ({@code cellElevation}) differs by at most {@link #DOME_GAP} m in the core (G &gt; 0.3) and by at
	 * most {@link #DOME_GAP_SUMMIT} m at G &gt; 0.6 (first version of K2: 1115 and 1353 m at gameplay scale, 867 and
	 * 1072 m at realistic scale). The rest is the different flysch relief of the two cell types (A6, M5).
	 */
	@Test
	void greatMassifDomeSpansRegionTypes() {
		for (LandscapeScale sc : new LandscapeScale[] {LandscapeScale.REALISTIC, LandscapeScale.GAMEPLAY}) {
			int k = sc == LandscapeScale.REALISTIC ? 0 : 1;
			LandscapeModel m = GreatMassifSurvey.model(sc);
			double ra = GreatMassifSurvey.ra(sc);
			double step = sc == LandscapeScale.REALISTIC ? 100 : 20;
			int n = (int) Math.ceil(2.6 * ra / step);
			double core = 0;
			double summit = 0;
			int mixed = 0;
			String where = "-";
			for (GreatMassifSurvey.Massif s : GreatMassifSurvey.survey(sc)) {
				LandscapeModel.GreatMassif g = s.massif();
				// {G, |h(Beskids cell) - h(foothills cell)|, x, z} of the mixed columns in the reach.
				double[][] gaps = IntStream.range(0, n * n).parallel().mapToObj(q -> {
					double x = g.x() - n * step / 2 + (q % n) * step;
					double z = g.z() - n * step / 2 + (q / n) * step;
					double strength = m.greatMassifStrength(x, z);
					LandscapeModel.Blend b = strength > 0 ? m.blend(x, z) : null;
					if (b == null || b.weight(LandscapeType.BESKIDS) < 0.05 || b.weight(LandscapeType.FOOTHILLS) < 0.05) {
						return null;
					}
					double hb = Double.NaN;
					double hf = Double.NaN;
					double wb = 0;
					double wf = 0;
					for (int i = 0; i < b.count(); i++) {
						LandscapeModel.Cell c = b.cells()[i];
						double w = b.weights()[i];
						if (c.type() == LandscapeType.BESKIDS && w > wb) {
							wb = w;
							hb = m.cellElevation(c, x, z);
						} else if (c.type() == LandscapeType.FOOTHILLS && w > wf) {
							wf = w;
							hf = m.cellElevation(c, x, z);
						}
					}
					return new double[] {strength, Math.abs(hb - hf), x, z};
				}).filter(v -> v != null).toArray(double[][]::new);
				mixed += gaps.length;
				for (double[] v : gaps) {
					if (v[0] > LandscapeModel.GM_CORE && v[1] > core) {
						core = v[1];
						where = String.format(Locale.ROOT, "(%.0f, %.0f) G %.2f", v[2], v[3], v[0]);
					}
					if (v[0] > 0.6) {
						summit = Math.max(summit, v[1]);
					}
				}
			}
			System.out.printf(Locale.ROOT, "%s: %d mixed Beskids and foothills columns in the massif reaches; largest height "
					+ "difference of the two cells in the core %.0f m at %s (limit %.0f m), at G > 0.6 %.0f m (limit %.0f m)%n",
					sc.id(), mixed, core, where, DOME_GAP[k], summit, DOME_GAP_SUMMIT[k]);
			assertTrue(core <= DOME_GAP[k], sc.id() + ": dome cut by a region cell boundary, " + core + " m at " + where);
			assertTrue(summit <= DOME_GAP_SUMMIT[k], sc.id() + ": dome cut by a region cell boundary at G > 0.6, " + summit + " m");
		}
	}

	/**
	 * Limits of {@link #greatMassifDomeSpansRegionTypes} in m, realistic and gameplay scale, measured after the review
	 * of K2 (core 502 and 759 m, G &gt; 0.6 359 and 594 m).
	 */
	static final double[] DOME_GAP = {503, 760};
	static final double[] DOME_GAP_SUMMIT = {360, 595};

	private static void assertWaterContained(LandscapeModel m) {
		List<double[]> sites = new ArrayList<>();
		double[] outwashPlain = LandscapePreview.find(m, LandscapeType.OUTWASH_PLAIN, true);
		double[] moraine = LandscapePreview.find(m, LandscapeType.MORAINE_PLATEAU, true);
		double[] river = LandscapePreview.findRiver(m);
		for (double[] p : new double[][] {outwashPlain, moraine, river}) {
			if (p != null) {
				sites.add(p);
			}
		}
		assertFalse(sites.isEmpty());
		int waterColumns = 0;
		int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		for (double[] site : sites) {
			int n = 700;
			int x0 = (int) site[0] - n / 2;
			int z0 = (int) site[1] - n / 2;
			ColumnSample[][] g = new ColumnSample[n][n];
			java.util.stream.IntStream.range(0, n).parallel().forEach(j -> {
				for (int i = 0; i < n; i++) {
					g[j][i] = m.sample(x0 + i, z0 + j);
				}
			});
			for (int j = 1; j < n - 1; j++) {
				for (int i = 1; i < n - 1; i++) {
					ColumnSample c = g[j][i];
					if (!c.hasWater()) {
						continue;
					}
					waterColumns++;
					for (int[] d : dirs) {
						ColumnSample o = g[j + d[1]][i + d[0]];
						boolean ok;
						if (o.hasWater()) {
							// Rapids and cascades are allowed between channel columns (water falls into water).
							ok = o.waterLevel() == c.waterLevel()
									|| c.waterKind() == WaterKind.RIVER && o.waterKind() == WaterKind.RIVER;
						} else {
							ok = o.surfaceMeters() >= c.waterLevel();
						}
						assertTrue(ok, "water without a bank at " + (x0 + i) + "," + (z0 + j) + ": " + c + " next to " + o);
					}
				}
			}
		}
		assertTrue(waterColumns > 100, "too little water in the test areas: " + waterColumns);
	}
}
