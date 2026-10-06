package pl.polishforests.worldgen.landscape;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Locality of the terrain geometry fix (docs/m2/poprawka-geometrii.md, step K0 and point 4 of the golden file
 * protocol, design §4): the fix may change the terrain only in valleys and at water. On a 200 × 200 grid the
 * current model is compared with the frozen copy of the M1 code ({@code landscape.m1}) in the same JVM. A column
 * that is neither cut (surface different from the terrain before valleys and lakes, {@code landElevation}) nor with
 * water or a water level in either model must have the same surface (to 1e-6 m, as the golden hash).
 *
 * <p>What the test guards, in effect: {@code landElevation} (the terrain before valleys and lakes) outside cuts and
 * water. It does not see changes inside valleys and lake basins (they are the purpose of the fix and are checked by the
 * golden patches and the continuity test), nor new cuts or new water outside valleys (such columns are skipped
 * as "cut or with water"); their area is printed, so a jump in the share of skipped columns stands out.
 *
 * <p>Exceptions, added by the steps that change the terrain on purpose outside valleys and water
 * ({@link #exempt}): the reach of a large Beskid massif (G &gt; 0, from K2), the seam of the 5 × 5 region blending
 * window (weight of a cell outside the 3 × 3 window &gt; 0, from K6), the moraine plateaus (D3), the lagoon and
 * the coastal belt (D2, D5, step K5: the low coast reaches {@code COAST_LOW_END} = 6 km·meso from the shoreline, within
 * the {@code shapeCoast} belt of 25 km·meso). In K0 and K1 there
 * were none, and the terrain was identical to M1; K2 exempts the reach of the large massifs.
 *
 * <p>Grids: around (0, 0) at both scales (every 97 m at realistic scale, every 23 m at gameplay scale), one grid
 * on each of the outwash plain and moraine plateau control patches of the golden file at each scale, and grids on
 * the large massif windows of {@code SurfaceContinuityTest} (no massif before K2; from K2 they check that the
 * massif stays within its reach) and on the lagoon patches (for D2 and D5). Seed 20260927, region slider 1.0.
 */
class TerrainLocalityTest {
	static final long SEED = 20260927L;
	static final int N = 200;
	/** Tolerance of the surface comparison, as the rounding of the golden hash. */
	static final double TOLERANCE = 1e-6;
	/** Least number of compared columns on a grid. */
	static final long MIN_COMPARED = 1_000;

	/** Grid N × N with the step {@code step} around (cx, cz). */
	record Grid(String name, LandscapeScale scale, double cx, double cz, double step) {
		@Override
		public String toString() {
			return name;
		}
	}

	static Stream<Grid> grids() {
		LandscapeScale r = LandscapeScale.REALISTIC;
		LandscapeScale g = LandscapeScale.GAMEPLAY;
		return Stream.of(
				new Grid("realistic_zero", r, 0, 0, 97),
				new Grid("gameplay_zero", g, 0, 0, 23),
				// Centered on the control patches outwash_plain_interior and moraine_plateau_interior (golden file,
				// seed 20260927).
				new Grid("realistic_outwash_plain", r, -68_929, -22_477, 37),
				new Grid("realistic_moraine_plateau", r, -64_697, 20_136, 37),
				new Grid("gameplay_outwash_plain", g, 4_893, -776, 7),
				new Grid("gameplay_moraine_plateau", g, 7_818, -2_738, 7),
				// Large massif windows of SurfaceContinuityTest (K2) and the lagoon patches of the golden file (D2, D5).
				// 50 m (10 km side) since K2: with the massif exempted, 40 m (8 km) compared only 988 columns.
				new Grid("gameplay_massif_spawn", g, 6_854, -33_757, 50),
				new Grid("realistic_massif_1723", r, 147_582, -1_525_292, 100),
				new Grid("gameplay_lagoon", g, 345, 10_394, 15),
				new Grid("realistic_lagoon", r, -223_761, -151_100, 60));
	}

	/**
	 * Columns where a step of the fix changes the terrain on purpose outside valleys and water. Steps that need an
	 * exception add it here, with the condition from the design (K2: {@code nearestGreatMassif} reach G &gt; 0;
	 * K6: blend weight of a cell outside the 3 × 3 window &gt; 0; D2 and D5: coastal belt; D3: moraine plateau).
	 */
	static boolean exempt(LandscapeModel m, double x, double z, ColumnSample s) {
		// K2: within the reach of a large Beskid massif (G > 0) the massif changes landElevation on purpose.
		// D2, D5 (step K5): the low coast, its dunes and lagoons reach COAST_LOW_END m·meso from the shoreline (the rest of
		// the belt of shapeCoast, up to 25 km·meso, is unchanged and still compared).
		return m.greatMassifStrength(x, z) > 0 || m.coastDistance(x, z) < LandscapeModel.COAST_LOW_END * m.scale().meso();
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("grids")
	void terrainChangesOnlyInValleysAndAtWater(Grid grid) {
		LandscapeModel m = new LandscapeModel(SEED, grid.scale(), 1.0);
		pl.polishforests.worldgen.landscape.m1.LandscapeModel old = new pl.polishforests.worldgen.landscape.m1.LandscapeModel(SEED,
				grid.scale() == LandscapeScale.REALISTIC ? pl.polishforests.worldgen.landscape.m1.LandscapeScale.REALISTIC
						: pl.polishforests.worldgen.landscape.m1.LandscapeScale.GAMEPLAY, 1.0);
		AtomicLong compared = new AtomicLong();
		AtomicLong cutOrWater = new AtomicLong();
		AtomicLong exempted = new AtomicLong();
		AtomicLong changedAny = new AtomicLong();
		AtomicLong wrong = new AtomicLong();
		AtomicReference<String> worst = new AtomicReference<>("");
		double[] worstD = {0};
		long t0 = System.nanoTime();
		IntStream.range(0, N * N).parallel().forEach(q -> {
			double x = grid.cx() + ((q % N) - N / 2 + 0.37) * grid.step();
			double z = grid.cz() + ((q / N) - N / 2 + 0.61) * grid.step();
			ColumnSample s = m.sample(x, z);
			pl.polishforests.worldgen.landscape.m1.ColumnSample t = old.sample(x, z);
			double d = Math.abs(s.surface() - t.surface());
			if (d > TOLERANCE || s.waterLevel() != t.waterLevel()) {
				changedAny.incrementAndGet();
			}
			boolean cut = Math.abs(s.surface() - s.terrain().rawSurface()) > 1e-9
					|| Math.abs(t.surface() - old.landElevation(x, z)) > 1e-9;
			boolean water = s.waterLevel() != ColumnSample.NO_WATER
					|| t.waterLevel() != pl.polishforests.worldgen.landscape.m1.ColumnSample.NO_WATER;
			if (cut || water) {
				cutOrWater.incrementAndGet();
				return;
			}
			if (exempt(m, x, z, s)) {
				exempted.incrementAndGet();
				return;
			}
			compared.incrementAndGet();
			// Both water levels are NO_WATER here (columns with water in either model were skipped), so only the
			// surface is compared.
			if (d > TOLERANCE) {
				wrong.incrementAndGet();
				synchronized (worstD) {
					if (d >= worstD[0]) {
						worstD[0] = d;
						worst.set(String.format(Locale.ROOT, "(%.1f, %.1f): surface %.4f m, M1 %.4f m, type %s", x, z,
								s.surface(), t.surface(), s.type()));
					}
				}
			}
		});
		long all = (long) N * N;
		System.out.printf(Locale.ROOT, "[locality] %s (%s, (%.0f, %.0f), every %.0f m): %d columns, cut or with water in "
				+ "either model %d (%.1f%%), exempted %d, compared %d, changed against M1 in total %d (%.1f%%), changed "
				+ "outside valleys and water %d%s (%.1f s)%n", grid.name(), grid.scale().id(), grid.cx(), grid.cz(), grid.step(),
				all, cutOrWater.get(), 100.0 * cutOrWater.get() / all, exempted.get(), compared.get(), changedAny.get(),
				100.0 * changedAny.get() / all, wrong.get(), wrong.get() > 0 ? ", largest " + worst.get() : "",
				(System.nanoTime() - t0) / 1e9);
		// Without enough compared columns the test would pass vacuously. At gameplay scale most columns are within the
		// reach of a valley wall (on the outwash plain about 90%), hence the low limit.
		assertTrue(compared.get() >= MIN_COMPARED, grid.name() + ": only " + compared.get() + " columns compared");
		assertTrue(wrong.get() == 0, grid.name() + ": " + wrong.get() + " columns changed outside valleys and water, largest "
				+ worst.get());
	}
}
