package pl.polishforests.worldgen.feature.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import java.util.function.IntBinaryOperator;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.habitat.Zone;
import pl.polishforests.worldgen.landscape.Noise;

/**
 * The provable tree code of a column of a neighboring chunk ({@link Ecotone#provableTrees}, rule (i+) of decision V5-i,
 * step V9): wherever it gives a code, the code equals the effective tree code the column's own chunk computes with its
 * own region ({@link Ecotone#effective}); in the decorated chunk itself it always gives one; and it proves most of the
 * columns near the chunk's border.
 */
class EcotoneProvableTest {
	private static final long SEED = 20260927L;

	/** A world of forest patches (oak-hornbeam, mixed, fresh pine), meadows, herb fringes and lakes. */
	private static IntBinaryOperator world(double k) {
		Noise n = new Noise(SEED).derive("test.provable");
		int ohf = EcotoneTest.code(HabitatBiome.OAK_HORNBEAM_FOREST, Zone.NONE);
		int mf = EcotoneTest.code(HabitatBiome.MIXED_FOREST, Zone.NONE);
		int fpf = EcotoneTest.code(HabitatBiome.FRESH_PINE_FOREST, Zone.NONE);
		int meadow = EcotoneTest.code(HabitatBiome.HAY_MEADOW, Zone.NONE);
		int fringe = EcotoneTest.code(HabitatBiome.HAY_MEADOW, Zone.HERB_FRINGE);
		int lake = EcotoneTest.code(HabitatBiome.LAKE, Zone.NONE);
		return (x, z) -> {
			double a = n.at(x, z, 90 * k);
			double b = n.at(x + 5_000, z - 3_000, 70 * k);
			double c = n.at(x - 9_000, z + 1_000, 50 * k);
			if (c > 0.55) {
				return lake;
			}
			if (c > 0.45) {
				return fringe;
			}
			if (b > 0.4) {
				return meadow;
			}
			return a > 0.15 ? ohf : a < -0.15 ? fpf : mf;
		};
	}

	@Test
	void provedCodesEqualTheCodesOfTheColumnsOwnChunk() {
		for (double k : new double[] {1.0, 0.5}) {
			IntBinaryOperator w = world(k);
			long proved = 0;
			long asked = 0;
			long[] provedBy = new long[16];
			long[] askedBy = new long[16];
			for (int cx = -6; cx < 6; cx++) {
				for (int cz = -6; cz < 6; cz++) {
					Ecotone.Region region = EcotoneTest.region(cx, cz, w, k);
					int[] own = Ecotone.effective(region, Ecotone.Layer.TREES, SEED, Ecotone.TREES_SALT, 1);
					for (int i = 0; i < 256; i++) {
						assertEquals(own[i], Ecotone.provableTrees(region, SEED, Ecotone.TREES_SALT, i >> 4, i & 15),
								"own chunk column " + i);
					}
					for (int dx = -1; dx <= 1; dx++) {
						for (int dz = -1; dz <= 1; dz++) {
							if (dx == 0 && dz == 0) {
								continue;
							}
							int[] theirs = Ecotone.effective(EcotoneTest.region(cx + dx, cz + dz, w, k), Ecotone.Layer.TREES, SEED,
									Ecotone.TREES_SALT, 1);
							for (int i = 0; i < 256; i++) {
								int lx = dx * 16 + (i >> 4);
								int lz = dz * 16 + (i & 15);
								int d = Math.max(Math.max(-lx - 1, lx - 16), Math.max(-lz - 1, lz - 16));
								int p = Ecotone.provableTrees(region, SEED, Ecotone.TREES_SALT, lx, lz);
								asked++;
								askedBy[d]++;
								if (p != Ecotone.UNKNOWN) {
									assertEquals(theirs[i], p, "column " + lx + ", " + lz + " of chunk " + cx + ", " + cz);
									proved++;
									provedBy[d]++;
								}
							}
						}
					}
				}
			}
			StringBuilder by = new StringBuilder();
			for (int d = 0; d < 8; d++) {
				by.append(String.format(Locale.ROOT, " %d: %.1f%%", d, 100.0 * provedBy[d] / askedBy[d]));
			}
			System.out.printf(Locale.ROOT, "[provable] k %.1f: %.1f%% of the columns of the 8 neighbors proved; by distance from the "
					+ "chunk's border:%s%n", k, 100.0 * proved / asked, by);
			assertTrue(provedBy[0] > 0.5 * askedBy[0], "most columns next to the border are proved");
		}
	}
}
