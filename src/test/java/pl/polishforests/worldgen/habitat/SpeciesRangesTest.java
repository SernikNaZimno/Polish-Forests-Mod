package pl.polishforests.worldgen.habitat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.landscape.ColumnSample;
import pl.polishforests.worldgen.landscape.LandscapeModel;
import pl.polishforests.worldgen.landscape.LandscapeScale;
import pl.polishforests.worldgen.landscape.Noise;

/**
 * Species ranges (docs/03-m2-biomy.md §9, §12.1): in the world (3 REAL seeds over 1000 × 1000 km, 1 GAMEPLAY
 * seed over 30 × 30 km) there is no beech at O &lt; 0.35 and P &lt; 0.35, no fir at P &lt; 0.45 and no natural
 * spruce at O &gt; 0.35 and P &lt; 0.45 (habitat flags and the biomes that require them).
 */
class SpeciesRangesTest {
	static final int BEECH = 0;
	static final int FIR = 1;
	static final int SPRUCE = 2;
	static final int BEECH_FORESTS = 3;
	static final int FIR_FORESTS = 4;
	static final int COLUMNS = 5;
	static final int WITH_BEECH = 6;
	static final int WITH_FIR = 7;
	static final int WITH_SPRUCE = 8;

	static AtomicLongArray check(long seed, LandscapeScale sc, double sideLength, int clusters) {
		LandscapeModel m = new LandscapeModel(seed, sc, 1.0);
		HabitatClassifier k = new HabitatClassifier(seed, sc, HabitatClassifier.Mode.NATURAL);
		AtomicLongArray l = new AtomicLongArray(9);
		double step = 125 * sc.local();
		IntStream.range(0, clusters).parallel().forEach(q -> {
			long h = Noise.mix(seed * 17 + q);
			double cx = ((h >>> 11) * 0x1.0p-53 - 0.5) * sideLength;
			double cz = ((Noise.mix(h) >>> 11) * 0x1.0p-53 - 0.5) * sideLength;
			for (int j = 0; j < 8; j++) {
				for (int i = 0; i < 8; i++) {
					double x = cx + i * step;
					double z = cz + j * step;
					ColumnSample s = m.sample(x, z);
					if (s.hasWater()) {
						continue;
					}
					int code = k.classify(s, x, z);
					double o = s.region().oceanicity();
					double p = s.region().mountainInfluence();
					HabitatBiome b = Habitat.biome(code);
					l.incrementAndGet(COLUMNS);
					l.addAndGet(WITH_BEECH, Habitat.has(code, Species.BEECH) ? 1 : 0);
					l.addAndGet(WITH_FIR, Habitat.has(code, Species.FIR) ? 1 : 0);
					l.addAndGet(WITH_SPRUCE, Habitat.has(code, Species.SPRUCE) ? 1 : 0);
					if (o < 0.35 && p < 0.35) {
						l.addAndGet(BEECH, Habitat.has(code, Species.BEECH) ? 1 : 0);
						l.addAndGet(BEECH_FORESTS, b == HabitatBiome.LOWLAND_BEECH_FOREST || b == HabitatBiome.MONTANE_BEECH_FOREST ? 1 : 0);
					}
					if (p < 0.45) {
						l.addAndGet(FIR, Habitat.has(code, Species.FIR) ? 1 : 0);
						l.addAndGet(FIR_FORESTS, b == HabitatBiome.UPLAND_FIR_FOREST ? 1 : 0);
					}
					if (o > 0.35 && p < 0.45) {
						l.addAndGet(SPRUCE, Habitat.has(code, Species.SPRUCE) ? 1 : 0);
					}
				}
			}
		});
		return l;
	}

	static void print(String label, AtomicLongArray l) {
		double n = l.get(COLUMNS);
		System.out.printf(Locale.ROOT, "%s: %d land columns; beech in range %.1f%%, fir %.1f%%, natural spruce %.1f%%%n", label,
				l.get(COLUMNS), 100 * l.get(WITH_BEECH) / n, 100 * l.get(WITH_FIR) / n, 100 * l.get(WITH_SPRUCE) / n);
	}

	static void checkZeros(String label, AtomicLongArray l) {
		assertEquals(0, l.get(BEECH), label + ": beech at O < 0.35 and P < 0.35");
		assertEquals(0, l.get(BEECH_FORESTS), label + ": beech forests at O < 0.35 and P < 0.35");
		assertEquals(0, l.get(FIR), label + ": fir at P < 0.45");
		assertEquals(0, l.get(FIR_FORESTS), label + ": fir forest at P < 0.45");
		assertEquals(0, l.get(SPRUCE), label + ": natural spruce at O > 0.35 and P < 0.45");
		// The ranges must not be empty: beech, fir and natural spruce grow somewhere in the world.
		assertTrue(l.get(WITH_BEECH) > 0 && l.get(WITH_FIR) > 0 && l.get(WITH_SPRUCE) > 0, label + ": empty range");
	}

	@Test
	void rangesAtRealisticScale() {
		for (long seed : BiomeSharesTest.SEEDS) {
			AtomicLongArray l = check(seed, LandscapeScale.REALISTIC, 1_000_000, 800);
			print("REAL, seed " + seed, l);
			checkZeros("REAL, seed " + seed, l);
		}
	}

	@Test
	void rangesAtGameplayScale() {
		AtomicLongArray l = check(20260927L, LandscapeScale.GAMEPLAY, 30_000, 800);
		print("GAMEPLAY", l);
		// Over 30 × 30 km at gameplay scale (about 1400 km·zs in REAL) the regional fields change little, so
		// we only check the zeros (a range may lie entirely on one side of the threshold).
		assertEquals(0, l.get(BEECH));
		assertEquals(0, l.get(BEECH_FORESTS));
		assertEquals(0, l.get(FIR));
		assertEquals(0, l.get(FIR_FORESTS));
		assertEquals(0, l.get(SPRUCE));
	}
}
