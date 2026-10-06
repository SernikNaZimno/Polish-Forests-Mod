package pl.polishforests.worldgen.landscape;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Determinism of the terrain (terrain geometry fix, docs/m2/poprawka-geometrii.md): a column must not depend on the
 * order in which columns are sampled, nor on the threads (C2ME generates chunks in parallel and in any order). Each
 * frame is sampled by two fresh models: rows in parallel from the top, and one thread from the bottom-right corner
 * backwards. Every M1 field of {@link ColumnSample} must be identical.
 *
 * <p>Issue inherited from M1 (the frozen copy behaves the same), fixed in step K5.2: the water level of a tunnel
 * valley lake was cached per lake key, but computed from the point where the iteration that finds the lake axis ends,
 * and that point depended on the column that asked first. Where the iteration does not converge (tunnel valleys
 * running W–E, the N–S bands A1) the level, and with it the basin, depended on the sampling order (up to 2 m; in K0
 * 2531 of 160000 columns of the frame of the bands and 12143 of 90000 of the close-up). Since K5.2 the level is
 * computed from a canonical point that depends only on the lake key (the iteration started at its anchor), so every
 * column must be identical, also at tunnel valley lakes.
 *
 * <p>The fields of the channel of the dominant valley ({@code floorChannelDist}, {@code floorChannelWidth},
 * {@code floorChannelLevel}, step K1, F2) must be identical in every column, also at tunnel valley lakes: they come
 * from the river network only, and their candidates depend on the order of the tile list.
 */
@Tag("slow")
class TerrainDeterminismTest {
	static final long SEED = 20260927L;
	/** Square frame of {@code size} × {@code size} columns, {@code mpp} m apart, centered on (cx, cz). */
	record Frame(String name, LandscapeScale scale, double cx, double cz, int size, double mpp) {
		@Override
		public String toString() {
			return name;
		}
	}

	static Stream<Frame> frames() {
		LandscapeScale r = LandscapeScale.REALISTIC;
		LandscapeScale g = LandscapeScale.GAMEPLAY;
		return Stream.of(
				// Tunnel valley lakes with the N–S bands A1 (R_jrynnowe_pasy_6km) and a close-up of the jump at
				// (−82818, 10254) in the realistic_moraine window of SurfaceContinuityTest.
				new Frame("realistic_tunnel_lake_bands", r, -80_695, 10_173, 400, 15),
				new Frame("realistic_tunnel_lake_jump", r, -82_818, 10_254, 300, 2),
				new Frame("realistic_moraine", r, -66_495, 21_873, 300, 66),
				new Frame("gameplay_center", g, 0, 0, 300, 33),
				new Frame("gameplay_moraine", g, -1_074, -2_368, 400, 7.5),
				new Frame("gameplay_beskids", g, 27_609, 3_254, 300, 10),
				// Large massifs (K2): their caches (grid cells, thinning, river rules) must not depend on the order.
				new Frame("gameplay_great_massif", g, 6_854, -33_757, 300, 17),
				new Frame("realistic_great_massif", r, 258_824, -1_539_366, 300, 55));
	}

	static boolean same(ColumnSample a, ColumnSample b) {
		return a.surface() == b.surface() && a.waterLevel() == b.waterLevel() && a.waterKind() == b.waterKind()
				&& a.type() == b.type() && a.substrate() == b.substrate() && a.coverDepth() == b.coverDepth();
	}

	/** Fields of the channel of the dominant valley (F2), NaN equal to NaN. */
	static boolean sameFloorChannel(ColumnSample a, ColumnSample b) {
		ColumnSample.Waters u = a.waters();
		ColumnSample.Waters v = b.waters();
		return Double.compare(u.floorChannelDist(), v.floorChannelDist()) == 0
				&& Double.compare(u.floorChannelWidth(), v.floorChannelWidth()) == 0
				&& Double.compare(u.floorChannelLevel(), v.floorChannelLevel()) == 0;
	}

	static boolean atTunnelLake(ColumnSample s) {
		return s.waters().standingWaterKind() == ColumnSample.StandingWaterKind.TUNNEL_VALLEY_LAKE;
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("frames")
	void columnsDoNotDependOnSamplingOrder(Frame f) {
		int n = f.size();
		double x0 = f.cx() - n / 2.0 * f.mpp();
		double z0 = f.cz() - n / 2.0 * f.mpp();
		ColumnSample[] first = new ColumnSample[n * n];
		ColumnSample[] second = new ColumnSample[n * n];
		long t0 = System.nanoTime();
		LandscapeModel parallel = new LandscapeModel(SEED, f.scale(), 1.0);
		IntStream.range(0, n).parallel().forEach(j -> {
			for (int i = 0; i < n; i++) {
				first[j * n + i] = parallel.sample(x0 + i * f.mpp(), z0 + j * f.mpp());
			}
		});
		LandscapeModel backwards = new LandscapeModel(SEED, f.scale(), 1.0);
		for (int q = n * n - 1; q >= 0; q--) {
			second[q] = backwards.sample(x0 + (q % n) * f.mpp(), z0 + (q / n) * f.mpp());
		}
		int tunnel = 0;
		int other = 0;
		String firstOther = "";
		int floorChannel = 0;
		for (int q = 0; q < n * n; q++) {
			ColumnSample a = first[q];
			ColumnSample b = second[q];
			if (!sameFloorChannel(a, b)) {
				floorChannel++;
			}
			if (same(a, b)) {
				continue;
			}
			if (atTunnelLake(a) || atTunnelLake(b)) {
				tunnel++;
			}
			if (other == 0) {
				firstOther = String.format(Locale.ROOT, " first at (%.2f, %.2f): surface %.4f / %.4f, water %d / %d, "
						+ "%s / %s, standing water %s / %s", x0 + (q % n) * f.mpp(), z0 + (q / n) * f.mpp(), a.surface(),
						b.surface(), a.waterLevel(), b.waterLevel(), a.waterKind(), b.waterKind(),
						a.waters().standingWaterKind(), b.waters().standingWaterKind());
			}
			other++;
		}
		System.out.printf(Locale.ROOT, "[determinism] %s (%s, (%.0f, %.0f), %d x %d every %.1f m): differing columns %d (at a "
				+ "tunnel valley lake %d)%s; channel of the dominant valley (F2) differing in %d (%.1f s)%n", f.name(),
				f.scale().id(), f.cx(), f.cz(), n, n, f.mpp(), other, tunnel, firstOther, floorChannel,
				(System.nanoTime() - t0) / 1e9);
		assertTrue(other == 0, f.name() + ": " + other + " columns depend on the sampling order" + firstOther);
		assertTrue(floorChannel == 0, f.name() + ": the channel of the dominant valley (F2) depends on the sampling order in "
				+ floorChannel + " columns");
	}
}
