package pl.polishforests.worldgen.feature.plan;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import java.util.function.IntBinaryOperator;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.habitat.Association;
import pl.polishforests.worldgen.habitat.ForestSiteType;
import pl.polishforests.worldgen.habitat.Habitat;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.habitat.LandCover;
import pl.polishforests.worldgen.habitat.Soil;
import pl.polishforests.worldgen.habitat.Zone;
import pl.polishforests.worldgen.landscape.Noise;

/**
 * Ecotones (rule Z10, step S8b, docs/03-m2-biomy.md §1, §8.7): at a straight border the share of the other side's code
 * is a linear ramp, (1 − d/H) / 2 at distance d, from 50% at the border to 0 at the pair's half-width H (in blocks: the
 * half-width in m times k, at most one chunk), on both sides; water, the narrow zones and the bare sand of beaches never
 * mix; the floodplain forests keep their plants against other biomes; columns of unknown neighbors keep their code; the
 * effective code of a column depends only on the world codes, not on the chunk it is computed from; the mantle and
 * fringe of forest edges have their nominal widths.
 */
class EcotoneTest {
	private static final long SEED = 20260927L;
	private static final long SALT = 0x77L;

	static int code(HabitatBiome b, Zone z) {
		return Habitat.pack(b, z, ForestSiteType.NONE, Association.TYPICAL, LandCover.forBiome(b), 0, Soil.forBiome(b, z));
	}

	/** Region of the chunk (cx, cz) from a world code function. */
	static Ecotone.Region region(int cx, int cz, IntBinaryOperator world, double k) {
		int[][] chunks = new int[9][];
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				int[] c = new int[256];
				for (int i = 0; i < 256; i++) {
					c[i] = world.applyAsInt(((cx + dx) << 4) + (i >> 4), ((cz + dz) << 4) + (i & 15));
				}
				chunks[(dx + 1) * 3 + dz + 1] = c;
			}
		}
		return Ecotone.Region.of(cx, cz, chunks, k);
	}

	/**
	 * Share of the columns taking code b at each signed distance from a straight border along x = 0 (code a for x < 0,
	 * b for x ≥ 0), over many chunks: index d + 20 for the column at x = d (d from −20 to 19).
	 */
	private static double[] profile(int a, int b, Ecotone.Layer layer, double k) {
		IntBinaryOperator world = (x, z) -> x < 0 ? a : b;
		long[] taken = new long[40];
		long[] all = new long[40];
		for (int cx = -2; cx <= 1; cx++) {
			for (int cz = -150; cz < 150; cz++) {
				int[] eff = Ecotone.effective(region(cx, cz, world, k), layer, SEED, SALT, 1);
				for (int i = 0; i < 256; i++) {
					int x = (cx << 4) + (i >> 4);
					if (x < -20 || x >= 20) {
						continue;
					}
					all[x + 20]++;
					taken[x + 20] += eff[i] == b ? 1 : 0;
				}
			}
		}
		double[] out = new double[40];
		for (int d = 0; d < 40; d++) {
			out[d] = (double) taken[d] / all[d];
		}
		return out;
	}

	/** The ramp of a pair with half-width h blocks: (1 − (|d| − 0.5) / h) / 2 on the far side, mirrored. */
	private static void checkRamp(double[] p, int h, String what) {
		StringBuilder sb = new StringBuilder(what + ":");
		for (int d = -20; d < 20; d++) {
			sb.append(String.format(Locale.ROOT, " %d:%.2f", d, p[d + 20]));
		}
		System.out.println(sb);
		for (int d = -20; d < 20; d++) {
			double dist = d < 0 ? -d - 0.5 : d + 0.5;
			double other = Math.max(0, (1 - dist / h) / 2);
			double expected = d < 0 ? other : 1 - other;
			assertEquals(expected, p[d + 20], 0.035, what + " at x = " + d);
		}
	}

	@Test
	void linearRampAtStraightBorders() {
		int oak = code(HabitatBiome.OAK_HORNBEAM_FOREST, Zone.NONE);
		int pine = code(HabitatBiome.FRESH_PINE_FOREST, Zone.NONE);
		int alder = code(HabitatBiome.ALDER_CARR, Zone.NONE);
		int meadow = code(HabitatBiome.HAY_MEADOW, Zone.NONE);
		// Similar forests: 24 m·k, capped at one chunk in the realistic scale; 12 blocks in the gameplay scale.
		checkRamp(profile(oak, pine, Ecotone.Layer.TREES, 1), 16, "oak-hornbeam | fresh pine, trees, realistic");
		checkRamp(profile(oak, pine, Ecotone.Layer.TREES, 0.5), 12, "oak-hornbeam | fresh pine, trees, gameplay");
		checkRamp(profile(oak, pine, Ecotone.Layer.SOIL, 1), 8, "oak-hornbeam | fresh pine, soil, realistic");
		// Dry and wet forests 12 m·k.
		checkRamp(profile(pine, alder, Ecotone.Layer.TREES, 1), 12, "fresh pine | alder carr, trees, realistic");
		// Forest and meadow: trees 8 m·k, soil 4 m·k.
		checkRamp(profile(oak, meadow, Ecotone.Layer.TREES, 1), 8, "oak-hornbeam | hay meadow, trees, realistic");
		checkRamp(profile(oak, meadow, Ecotone.Layer.SOIL, 0.5), 2, "oak-hornbeam | hay meadow, soil, gameplay");
	}

	@Test
	void sharpBordersDoNotMix() {
		int oak = code(HabitatBiome.OAK_HORNBEAM_FOREST, Zone.NONE);
		int meadow = code(HabitatBiome.HAY_MEADOW, Zone.NONE);
		int lake = code(HabitatBiome.LAKE, Zone.NONE);
		int fringe = code(HabitatBiome.WILLOW_POPLAR_FOREST, Zone.HERB_FRINGE);
		int beach = code(HabitatBiome.BEACH, Zone.NONE);
		int whiteDune = code(HabitatBiome.WHITE_DUNE, Zone.NONE);
		for (Ecotone.Layer layer : Ecotone.Layer.values()) {
			assertEquals(0, Ecotone.halfWidth(oak, lake, layer));
			assertEquals(0, Ecotone.halfWidth(lake, oak, layer));
			assertEquals(0, Ecotone.halfWidth(oak, fringe, layer));
			assertEquals(0, Ecotone.halfWidth(fringe, oak, layer));
			assertEquals(0, Ecotone.halfWidth(beach, whiteDune, layer));
			assertEquals(0, Ecotone.halfWidth(oak, whiteDune, layer));
		}
		// The forest edge: the plants do not mix (the mantle and the fringe take their place).
		assertEquals(0, Ecotone.halfWidth(oak, meadow, Ecotone.Layer.PLANTS));
		double[] p = profile(oak, lake, Ecotone.Layer.TREES, 1);
		for (int d = 0; d < 40; d++) {
			assertEquals(d < 20 ? 0 : 1, p[d], 0, "no oak-hornbeam code in the lake and no lake code in the forest");
		}
	}

	/** The floodplain forests and alder carrs of §4.6 keep their own plants against other biomes; the other side mixes. */
	@Test
	void denseHabitatsKeepTheirPlants() {
		int oak = code(HabitatBiome.OAK_HORNBEAM_FOREST, Zone.NONE);
		int ash = code(HabitatBiome.ASH_ALDER_FOREST, Zone.NONE);
		int carr = code(HabitatBiome.ALDER_CARR, Zone.NONE);
		assertEquals(0, Ecotone.halfWidth(ash, oak, Ecotone.Layer.PLANTS));
		assertTrue(Ecotone.halfWidth(oak, ash, Ecotone.Layer.PLANTS) > 0);
		assertTrue(Ecotone.halfWidth(ash, oak, Ecotone.Layer.TREES) > 0);
		assertTrue(Ecotone.halfWidth(ash, carr, Ecotone.Layer.PLANTS) > 0);
		double[] p = profile(oak, ash, Ecotone.Layer.PLANTS, 1);
		for (int d = 20; d < 40; d++) {
			assertEquals(1, p[d], 0, "ash-alder forest column at x = " + (d - 20) + " keeps its code");
		}
		assertTrue(p[19] > 0.4, "the oak-hornbeam side takes the ash-alder plants at the border");
	}

	@Test
	void unknownNeighborsAndDeterminism() {
		int oak = code(HabitatBiome.OAK_HORNBEAM_FOREST, Zone.NONE);
		int pine = code(HabitatBiome.FRESH_PINE_FOREST, Zone.NONE);
		IntBinaryOperator world = (x, z) -> x < 16 ? oak : pine;
		Ecotone.Region full = region(0, 0, world, 1);
		assertArrayEquals(Ecotone.effective(full, Ecotone.Layer.TREES, SEED, SALT, 1),
				Ecotone.effective(region(0, 0, world, 1), Ecotone.Layer.TREES, SEED, SALT, 1));
		// The east neighbor unknown: the chunk keeps its codes.
		int[][] chunks = new int[9][];
		chunks[4] = full.own();
		int[] eff = Ecotone.effective(Ecotone.Region.of(0, 0, chunks, 1), Ecotone.Layer.TREES, SEED, SALT, 1);
		assertArrayEquals(full.own(), eff);
		// Patches: the same vector for most columns of a 3-block patch.
		int same = 0;
		for (int x = 0; x < 300; x += 3) {
			double[] a = Ecotone.vector(SEED, x + 1, 7, 3);
			double[] b = Ecotone.vector(SEED, x + 1, 8, 3);
			same += a[0] == b[0] ? 1 : 0;
		}
		assertTrue(same > 40, "columns of a patch share their vector: " + same);
	}

	/** Mantle and fringe widths at a straight forest edge with the nominal widths (no width noise). */
	@Test
	void forestEdgeBelts() {
		int pine = code(HabitatBiome.FRESH_PINE_FOREST, Zone.NONE);
		int meadow = code(HabitatBiome.HAY_MEADOW, Zone.NONE);
		IntBinaryOperator world = (x, z) -> x < 8 ? pine : meadow;
		for (double k : new double[] {1, 0.5}) {
			Ecotone.Region r = region(0, 0, world, k);
			int[] codes = r.own();
			byte[] edges = Ecotone.edges(r, null, codes);
			for (int x = 0; x < 16; x++) {
				int i = x << 4 | 5;
				double d = x < 8 ? 8 - x : x - 7;
				Ecotone.Edge expected = x < 8 ? (d <= Math.max(1, Ecotone.MANTLE * k) ? Ecotone.Edge.MANTLE : Ecotone.Edge.NONE)
						: d <= Math.max(1, Ecotone.MANTLE_OUT * k) ? Ecotone.Edge.MANTLE
								: d <= Math.max(2, Ecotone.FRINGE * k) ? Ecotone.Edge.FRINGE : Ecotone.Edge.NONE;
				assertEquals(expected.ordinal(), edges[i], "k " + k + ", x " + x);
				assertEquals(expected == Ecotone.Edge.MANTLE ? pine : x < 8 ? pine : meadow, codes[i],
						"an open mantle column takes the forest's code (k " + k + ", x " + x + ")");
			}
		}
		// With the width noise the outer border of the fringe varies along the edge.
		Ecotone.Region r = region(0, 0, (x, z) -> x < 8 ? pine : meadow, 1);
		Noise widths = new Noise(SEED).derive("feature.ecotone.edges");
		java.util.Set<Integer> outer = new java.util.HashSet<>();
		for (int cz = 0; cz < 40; cz++) {
			Ecotone.Region rz = region(0, cz, (x, z) -> x < 8 ? pine : meadow, 1);
			byte[] e = Ecotone.edges(rz, widths, null);
			for (int z = 0; z < 16; z++) {
				int last = -1;
				for (int x = 8; x < 16; x++) {
					if (e[x << 4 | z] == Ecotone.Edge.FRINGE.ordinal()) {
						last = x;
					}
				}
				outer.add(last);
			}
		}
		assertTrue(outer.size() >= 4, "the fringe border is not a straight line: " + outer);
		assertEquals(256, r.own().length);
	}
}
