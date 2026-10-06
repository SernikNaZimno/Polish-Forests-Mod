package pl.polishforests.worldgen.landscape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static pl.polishforests.worldgen.landscape.RiverNetworkTest.SEED;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.chunk.VerticalScale;

/**
 * Review of K5 (docs/m2/poprawka-geometrii.md, K5): standing water is contained within its banks on 1 m grids at the
 * places where the first version of K5 leaked or had seams of the water level, with the rule of
 * {@link RiverNetworkTest#assertSitesContained}: water never next to dry ground below its level, and two standing waters
 * next to each other always at one level. The places: kettle ponds cutting the banks of tunnel valley lakes (A3c, up to
 * 57 m), kettle bogs below the sea next to a lagoon, stripes of tunnel valley lakes with different levels (A1 at gameplay
 * scale beyond ±8 km), oxbow lakes above the floor of another valley and in shafts on a slope; and the first kettle pond
 * and tunnel valley lake found near the origin in each scale. Round 2 of the review of K5: kettle bogs cutting the ground
 * next to a stream (the stream on a causeway up to 30 m above them), tunnel valley lakes below the sea level next to a
 * lagoon (gameplay scale beyond ±20 km) and the basin of a tunnel valley lake with a low level reaching the channel of a
 * valley;
 * and the basins of the tunnel valley lakes without dams and walls ({@link #tunnelValleyBasinsHaveNoDamsOrWalls}).
 */
@Tag("slow")
class StandingWaterContainmentTest {
	/** {x, z, side} of the windows at gameplay scale. */
	static final double[][] GAMEPLAY_SPOTS = {
			{5761, -4758, 400}, {-1274, -6223, 400}, {6130, -6600, 800}, {3032, -7216, 400}, {10730, 12420, 400},
			{17600, -1290, 400}, {-16500, -8820, 300}, {2180, 17650, 400}, {-14940, -1537, 600}, {-642, 2156, 300},
			{7040, -5640, 300}, {-4519, 2968, 300},
			// Round 2 of the review of K5.
			{3755, 11000, 200}, {430, -10310, 200}, {-32039, -24520, 300}, {-6400, 32900, 600}, {-30100, 10080, 400},
			{17620, -1819, 120}, {24520, 16080, 400}};
	/** {x, z, side} of the windows at realistic scale. */
	static final double[][] REALISTIC_SPOTS = {{-77955, 8162, 400}, {-221658, -151118, 1000}};

	@Test
	void standingWaterIsContainedAtTheReviewedSpotsGameplay() {
		check(new LandscapeModel(SEED, LandscapeScale.GAMEPLAY, 1.0), GAMEPLAY_SPOTS, 25);
	}

	@Test
	void standingWaterIsContainedAtTheReviewedSpotsRealistic() {
		check(new LandscapeModel(SEED, 1.0), REALISTIC_SPOTS, 250);
	}

	private static void check(LandscapeModel m, double[][] spots, double step) {
		List<double[]> windows = new ArrayList<>(List.of(spots));
		double[] kettle = RiverNetworkTest.find(m, s -> s.waterKind() == WaterKind.KETTLE, step);
		double[] tunnel = RiverNetworkTest.find(m, s -> s.waterKind() == WaterKind.LAKE
				&& s.waters().standingWaterKind() == ColumnSample.StandingWaterKind.TUNNEL_VALLEY_LAKE, step);
		assertTrue(kettle != null && tunnel != null, m.scale().id() + ": no kettle pond or tunnel valley lake found");
		windows.add(new double[] {kettle[0], kettle[1], 300});
		windows.add(new double[] {tunnel[0], tunnel[1], 300});
		long failures = 0;
		List<String> first = new ArrayList<>();
		for (double[] w : windows) {
			long[] water = new long[1];
			List<String> bad = window(m, w[0], w[1], (int) w[2], water);
			System.out.printf(Locale.ROOT, "[containment] %s (%.0f, %.0f) %d m: %d water columns, %d failures%n", m.scale().id(),
					w[0], w[1], (int) w[2], water[0], bad.size());
			failures += bad.size();
			bad.stream().limit(3).forEach(first::add);
		}
		assertEquals(0, failures, m.scale().id() + ": standing water without a bank, first: " + first);
	}

	/**
	 * Most a dry column next to a tunnel valley lake ({@link ColumnSample.Waters#s()} finite) lies above the terrain before
	 * the valleys and lakes (m): the bank of the lake raises the ground to the water level + 1 m only where it lies lower.
	 */
	static final double DAM_MAX = 3;
	/** Most pairs of dry neighbors (1 m) at a tunnel valley basin differing by more than 2 blocks (gameplay ±20 km). */
	static final long WALLS_MAX = 6;

	/**
	 * Round 2 of the review of K5: the water level of a tunnel valley lake comes from the ground along the lake itself, so
	 * its bank is never a dam: no dry column with the standing water of a tunnel valley lake lies more than
	 * {@value #DAM_MAX} m above the terrain before the valleys and lakes (first round of the review: up to 53 m, 2300 columns
	 * every 10 m at gameplay scale ±20 km). The basin passes into the terrain without walls: of the pairs of dry neighbors
	 * 1 m apart at every point of a grid (gameplay ±20 km every 20 m), at most {@value #WALLS_MAX} where one of them lies
	 * at a tunnel valley lake differ by more than 2 blocks (first round of the review: about 80 every 10 m; what remains is
	 * the half-width of a lake following the young-glacial weight at the edge of its region, docs/m2/poprawka-geometrii.md,
	 * K5). Realistic scale: the young-glacial plateau of the tunnel valley lakes ±20 km every 40 m, no dams and no walls.
	 */
	@Test
	void tunnelValleyBasinsHaveNoDamsOrWalls() {
		long[] g = basins(new LandscapeModel(SEED, LandscapeScale.GAMEPLAY, 1.0), VerticalScale.GAMEPLAY, 0, 0, 20_000, 20);
		long[] r = basins(new LandscapeModel(SEED, 1.0), VerticalScale.REAL, -66_495, 21_873, 20_000, 40);
		assertTrue(g[2] > 1_000 && r[2] > 1_000, "too few columns at tunnel valley lakes: " + g[2] + ", " + r[2]);
		assertEquals(0, g[0], "gameplay: dry columns of a tunnel valley basin above the terrain by more than " + DAM_MAX + " m");
		assertEquals(0, r[0], "realistic: dry columns of a tunnel valley basin above the terrain by more than " + DAM_MAX + " m");
		assertTrue(g[1] <= WALLS_MAX, "gameplay: walls at tunnel valley basins: " + g[1]);
		assertEquals(0, r[1], "realistic: walls at tunnel valley basins");
	}

	/** {dams, walls, columns at tunnel valley lakes} on the grid ({@link #tunnelValleyBasinsHaveNoDamsOrWalls}). */
	private static long[] basins(LandscapeModel m, VerticalScale v, double cx, double cz, double half, double step) {
		int n = (int) Math.round(2 * half / step) + 1;
		AtomicLong dams = new AtomicLong();
		AtomicLong walls = new AtomicLong();
		AtomicLong columns = new AtomicLong();
		List<String> first = Collections.synchronizedList(new ArrayList<>());
		double[] worstDam = {0};
		IntStream.range(0, n).parallel().forEach(j -> {
			for (int i = 0; i < n; i++) {
				double x = cx - half + i * step;
				double z = cz - half + j * step;
				ColumnSample c = m.sample(x, z);
				boolean at = tunnel(c);
				if (at) {
					columns.incrementAndGet();
					double up = c.surface() - c.terrain().rawSurface();
					if (!c.hasWater() && up > DAM_MAX) {
						dams.incrementAndGet();
						synchronized (worstDam) {
							worstDam[0] = Math.max(worstDam[0], up);
						}
						if (first.size() < 5) {
							first.add(String.format(Locale.ROOT, "dam (%.0f, %.0f) %.1f m", x, z, up));
						}
					}
				}
				for (ColumnSample o : new ColumnSample[] {m.sample(x + 1, z), m.sample(x, z + 1)}) {
					if (c.hasWater() || o.hasWater() || !at && !tunnel(o)) {
						continue;
					}
					double blocks = Math.abs(Math.floor(v.blocksForMeters(c.surface())) - Math.floor(v.blocksForMeters(o.surface())));
					if (blocks > 2) {
						walls.incrementAndGet();
						if (first.size() < 5) {
							first.add(String.format(Locale.ROOT, "wall (%.0f, %.0f) %.2f -> %.2f m", x, z, c.surface(), o.surface()));
						}
					}
				}
			}
		});
		System.out.printf(Locale.ROOT, "[tunnel basins] %s: %d columns at tunnel valley lakes, %d dams (largest %.1f m), %d walls %s%n",
				m.scale().id(), columns.get(), dams.get(), worstDam[0], walls.get(), first);
		return new long[] {dams.get(), walls.get(), columns.get()};
	}

	private static boolean tunnel(ColumnSample s) {
		return s.waters().standingWaterKind() == ColumnSample.StandingWaterKind.TUNNEL_VALLEY_LAKE;
	}

	/** Containment failures in the window of the given side centered at (cx, cz), 1 m grid. */
	static List<String> window(LandscapeModel m, double cx, double cz, int n, long[] water) {
		int x0 = (int) Math.floor(cx - n / 2.0);
		int z0 = (int) Math.floor(cz - n / 2.0);
		ColumnSample[][] g = new ColumnSample[n][];
		IntStream.range(0, n).parallel().forEach(j -> {
			ColumnSample[] row = new ColumnSample[n];
			for (int i = 0; i < n; i++) {
				row[i] = m.sample(x0 + i, z0 + j);
			}
			g[j] = row;
		});
		int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
		List<String> bad = new ArrayList<>();
		for (int j = 1; j < n - 1; j++) {
			for (int i = 1; i < n - 1; i++) {
				ColumnSample c = g[j][i];
				if (!c.hasWater()) {
					continue;
				}
				water[0]++;
				for (int[] d : dirs) {
					ColumnSample o = g[j + d[1]][i + d[0]];
					boolean ok;
					if (o.hasWater()) {
						boolean cascade = c.waterKind() == WaterKind.RIVER && o.waterKind() == WaterKind.RIVER;
						boolean flowing = c.waterKind() == WaterKind.RIVER || o.waterKind() == WaterKind.RIVER;
						ok = o.waterLevel() == c.waterLevel() || cascade || flowing && Math.abs(o.waterLevel() - c.waterLevel()) <= 1;
					} else {
						ok = o.surfaceMeters() >= c.waterLevel();
					}
					if (!ok) {
						bad.add(String.format(Locale.ROOT, "(%d, %d) %s %d next to %s %d (ground %.2f)", x0 + i, z0 + j, c.waterKind(),
								c.waterLevel(), o.hasWater() ? o.waterKind() : "land", o.waterLevel(), o.surface()));
					}
				}
			}
		}
		return bad;
	}
}
