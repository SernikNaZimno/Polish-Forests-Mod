package pl.polishforests.worldgen.surface;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.chunk.PolandScale;
import pl.polishforests.worldgen.landscape.ColumnSample;
import pl.polishforests.worldgen.landscape.Noise;
import pl.polishforests.worldgen.landscape.WaterKind;

/**
 * Bank shelf at stepped water (step S6b, docs/03-m2-biomy.md §7.6): random areas of rivers and lakes whose water level
 * differs from the fresh water 6–14 blocks away (steps of the level of mountain streams, confluences, seams between
 * channels, oxbow lakes next to a channel), chosen as in the probe of the review of S6, round 2, which found walls of 2
 * blocks there that the named and the plain random areas of {@link BankShelfTest} lacked. The same criteria as
 * {@link BankShelfTest#shelfOverRandomWaterAreas}: no step of 2 or more on flat model ground, no water flowing from an
 * open edge of the model over removed ground, no new open water edges, no ditch, no dry ground below nearby water, model
 * steps of 1 grown to 2 at most at 1% of the lowered columns. The shore share is only reported: at stepped water the
 * guard keeps the banks at the upper water level on purpose.
 */
@Tag("slow")
class SteppedWaterShelfTest {
	/** Number of stepped areas per scale. */
	private static final int AREAS = 40;

	/** Whether fresh water 6–14 blocks from the water column (x, z) has another level and lies above its own ground. */
	private static boolean stepped(SurfaceFixture f, int x, int z) {
		ColumnSample s = f.model.sample(x, z);
		int w0 = f.builder.waterTopY(s, f.maxY());
		for (int[] d : new int[][] {{8, 0}, {-8, 0}, {0, 8}, {0, -8}, {6, 6}, {-6, 6}, {6, -6}, {-6, -6}, {14, 0}, {-14, 0},
				{0, 14}, {0, -14}}) {
			ColumnSample o = f.model.sample(x + d[0], z + d[1]);
			if (o.hasWater() && o.waterKind() != WaterKind.SEA) {
				int w = f.builder.waterTopY(o, f.maxY());
				if (w != w0 && f.builder.topY(o, f.minY(), f.maxY()) < w) {
					return true;
				}
			}
		}
		return false;
	}

	@Test
	void shelfAtSteppedWater() {
		List<String> failures = new ArrayList<>();
		StringBuilder report = new StringBuilder();
		for (PolandScale scale : PolandScale.values()) {
			boolean real = scale == PolandScale.REALISTIC;
			int range = real ? 150_000 : 25_000;
			long[] total = new long[15];
			List<String> worst = new ArrayList<>();
			int areas = 0;
			long chunks = 0;
			long samples0 = SurfaceBuilder.OUTSIDE_SAMPLES.sum();
			SurfaceFixture f = new SurfaceFixture(scale, true);
			for (int k = 0; areas < AREAS && k < 2_000_000; k++) {
				long h = Noise.mix(k * 0x9E3779B97F4A7C15L + 4242 + (real ? 0 : 6));
				int x = (int) Math.floorMod(h, 2L * range) - range;
				int z = (int) Math.floorMod(h >>> 32, 2L * range) - range;
				ColumnSample s = f.model.sample(x, z);
				if (!s.hasWater() || !(s.waterKind() == WaterKind.RIVER || s.waterKind().isLake()) || !stepped(f, x, z)) {
					continue;
				}
				areas++;
				if (areas % 25 == 0) {
					// A fresh fixture now and then keeps the memory of the plans bounded.
					chunks += f.chunks();
					f = new SurfaceFixture(scale, true);
				}
				List<String> local = new ArrayList<>();
				long[] n = BankShelfTest.area(new BankShelfTest.Grid(f, new int[] {x, z}), "stepped a" + areas + "@" + x + "," + z,
						local, new TreeMap<>());
				for (int j = 0; j < n.length; j++) {
					total[j] += n[j];
				}
				if (n[3] + n[4] + n[6] + n[7] + n[8] + n[9] > 0) {
					worst.add(String.format(Locale.ROOT, "(%d, %d): open %d, walls %d, grown %d, flow %d, ditch %d, below %d; %s",
							x, z, n[3], n[4], n[6], n[7], n[8], n[9], local.subList(0, Math.min(3, local.size()))));
				}
			}
			chunks += f.chunks();
			report.append(String.format(Locale.ROOT, "%s: %d stepped areas, %d chunks, %.1f neighbor samples outside the chunk per "
					+ "chunk, shore columns %d (%.1f%% with water beside the top), lowered %d, new open edges %d, steps >= 2 on flat "
					+ "model ground %d, model step 1 grown to >= 2 %d, grown by more than 1 %d, flow over removed ground %d, ditches "
					+ "%d, ground below water %d; ", scale, areas, chunks,
					(double) (SurfaceBuilder.OUTSIDE_SAMPLES.sum() - samples0) / Math.max(1, chunks), total[0],
					100.0 * total[1] / Math.max(1, total[0]), total[2], total[3], total[4], total[5], total[6], total[7], total[8],
					total[9]));
			if (areas < AREAS || total[3] + total[4] + total[6] + total[7] + total[8] + total[9] > 0
					|| total[5] > 0.01 * total[2]) {
				failures.add(scale + ": " + areas + " areas, grown 1 -> 2 " + total[5] + " of " + total[2] + "; "
						+ worst.subList(0, Math.min(10, worst.size())));
			}
		}
		System.out.println(report);
		assertTrue(failures.isEmpty(), failures + "; " + report);
	}
}
