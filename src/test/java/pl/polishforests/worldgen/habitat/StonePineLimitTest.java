package pl.polishforests.worldgen.habitat;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.landscape.ColumnSample;
import pl.polishforests.worldgen.landscape.Landform;
import pl.polishforests.worldgen.landscape.LandscapeModel;
import pl.polishforests.worldgen.landscape.LandscapeScale;
import pl.polishforests.worldgen.landscape.Substrate;

/**
 * Round 1 of the TT4 review (Z10, KF-4): the upper limit of the single stone pines in the dwarf pine on the Tatra granite
 * and gneiss is a mosaic, not a contour. In the dwarf pine columns of the crystalline rocks of the Tatras proper (Tatra
 * weight at least 0.9, the full Tatra profile) of the massifs nearest the origin of seeds A and B, binned by 10 m of
 * elevation relative to the column's limit ({@code AltitudinalBelts.STONE_PINE_ABOVE} over its timberline, with the
 * aspect, noise and wind-exposed ridge corrections), the share of the stone pine association is 1 more than
 * {@code STONE_PINE_RAMP} below the limit, 0 more than that above it, on average 0.5–0.97 in the ramp below the limit and
 * 0.03–0.5 in the ramp above it, and changes by at most 0.35 between neighboring bins (before the review it fell from 1 to
 * 0 at the limit).
 */
class StonePineLimitTest {
	private static final int BIN = 10;
	private static final int SPAN = 80;
	private static final double STEP = 16;

	@Test
	void stonePineLimitInTheDwarfPineIsAMosaic() {
		int bins = 2 * SPAN / BIN;
		AtomicLongArray count = new AtomicLongArray(bins);
		AtomicLongArray pino = new AtomicLongArray(bins);
		double ramp = AltitudinalBelts.STONE_PINE_RAMP;
		for (long seed : new long[] {BeltProfileTatraTest.SEED_A, BeltProfileTatraTest.SEED_B}) {
			LandscapeModel m = new LandscapeModel(seed, LandscapeScale.GAMEPLAY, 1.0, true);
			HabitatClassifier k = new HabitatClassifier(seed, m, HabitatClassifier.Mode.NATURAL);
			double box = BeltProfileTatraTest.BOX;
			List<LandscapeModel.Tatra> ts = new ArrayList<>(m.tatras(-box, -box, box, box));
			ts.sort(Comparator.comparingDouble(t -> Math.hypot(t.x(), t.z())));
			LandscapeModel.Tatra t = ts.get(0);
			double half = 2.0 * t.halfLength();
			int cells = (int) (2 * half / STEP);
			double low = BeltProfile.TATRA.timberline() + AltitudinalBelts.STONE_PINE_ABOVE - SPAN - 150;
			double high = BeltProfile.TATRA.timberline() + AltitudinalBelts.STONE_PINE_ABOVE + SPAN + 150;
			IntStream.range(0, cells).parallel().forEach(j -> {
				for (int i = 0; i < cells; i++) {
					double x = t.x() - half + (i + 0.5) * STEP;
					double z = t.z() - half + (j + 0.5) * STEP;
					ColumnSample s = m.sample(x, z);
					if (s.hasWater() || s.terrain().wTatra() < 0.9 || s.surface() < low || s.surface() > high) {
						continue;
					}
					Substrate rock = s.terrain().rock();
					if (rock != Substrate.GRANITE && rock != Substrate.CRYSTALLINE) {
						continue;
					}
					int code = k.classify(s, x, z);
					if (Habitat.biome(code) != HabitatBiome.DWARF_PINE_SCRUB) {
						continue;
					}
					HabitatClassifier.Column c = new HabitatClassifier.Column(k, s, x, z);
					double limit = BeltProfile.TATRA.timberline() + AltitudinalBelts.correction(c)
							- (c.t.has(Landform.RIDGE) && c.slope < AltitudinalBelts.WINDY_RIDGE_SLOPE ? AltitudinalBelts.WINDY_RIDGE : 0);
					double d = c.H - limit - AltitudinalBelts.STONE_PINE_ABOVE;
					if (d < -SPAN || d >= SPAN) {
						continue;
					}
					int b = (int) Math.floor((d + SPAN) / BIN);
					count.incrementAndGet(b);
					if (Habitat.association(code) == Association.PINO_CEMBRAE_PICEETUM) {
						pino.incrementAndGet(b);
					}
				}
			});
		}
		StringBuilder row = new StringBuilder("stone pines in the dwarf pine by elevation over their limit (m: share, columns):");
		double last = Double.NaN;
		double jump = 0;
		long belowN = 0;
		long belowY = 0;
		long aboveN = 0;
		long aboveY = 0;
		boolean outside = true;
		for (int b = 0; b < bins; b++) {
			long n = count.get(b);
			long y = pino.get(b);
			int from = -SPAN + b * BIN;
			row.append(String.format(Locale.ROOT, " %d: %.2f (%d)", from, n == 0 ? Double.NaN : (double) y / n, n));
			if (from + BIN <= -ramp) {
				outside &= y == n;
			} else if (from >= ramp) {
				outside &= y == 0;
			} else if (from < 0) {
				belowN += n;
				belowY += y;
			} else {
				aboveN += n;
				aboveY += y;
			}
			if (n < 30) {
				last = Double.NaN;
				continue;
			}
			double v = (double) y / n;
			if (!Double.isNaN(last)) {
				jump = Math.max(jump, Math.abs(v - last));
			}
			last = v;
		}
		String report = row.toString();
		System.out.println("[tt4-r1] " + report);
		double below = (double) belowY / Math.max(1, belowN);
		double above = (double) aboveY / Math.max(1, aboveN);
		assertTrue(belowN >= 200 && aboveN >= 200, "too few columns in the ramp: " + report);
		assertTrue(outside, "stone pines outside the ramp: " + report);
		assertTrue(below >= 0.5 && below <= 0.97, "share in the ramp below the limit " + below + ": " + report);
		assertTrue(above >= 0.03 && above <= 0.5, "share in the ramp above the limit " + above + ": " + report);
		assertTrue(jump <= 0.35, "step between bins " + jump + ": " + report);
	}
}
