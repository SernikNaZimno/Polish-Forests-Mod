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

/**
 * Ecotones (rule Z10, step S8b, docs/03-m2-biomy.md §1, §8.8): without the meander, at a straight border the share of
 * the other side's code is a linear ramp, (1 − d/H) / 2 at distance d, from 50% at the border to 0 at the pair's
 * half-width H (in blocks: the half-width in m times k, at most one chunk), on both sides; with the meander the 50% line
 * of a straight border is not straight; water, the beach and the zones in the water never mix; the waterside zones of
 * the land and the floodplain forests (plants) keep their codes, and their neighbors take them in a one-sided ramp from
 * 1 at the border; columns of unknown neighbors keep their code; the mantle and fringe of forest edges have their
 * nominal widths, and the mantle has gaps.
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
				int[] eff = Ecotone.effective(region(cx, cz, world, k), layer, SEED, SALT, 1, false);
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

	/** {@link #profile} with the meander. */
	private static double[] profileWithMeander(int a, int b, Ecotone.Layer layer, double k) {
		IntBinaryOperator world = (x, z) -> x < 0 ? a : b;
		long[] taken = new long[40];
		long[] all = new long[40];
		for (int cx = -2; cx <= 1; cx++) {
			for (int cz = -60; cz < 60; cz++) {
				int[] eff = Ecotone.effective(region(cx, cz, world, k), layer, SEED, SALT, 1, true);
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
		// Forest edges: at least 5 blocks of trees and 3 of soil in any scale (4 m·k of soil is 2 blocks in the gameplay one).
		checkRamp(profile(oak, meadow, Ecotone.Layer.SOIL, 0.5), Ecotone.EDGE_SOIL_MIN, "oak-hornbeam | hay meadow, soil, gameplay");
		checkRamp(profile(oak, meadow, Ecotone.Layer.TREES, 0.5), Ecotone.EDGE_TREES_MIN, "oak-hornbeam | hay meadow, trees, gameplay");
	}


	@Test
	void sharpBordersDoNotMix() {
		int oak = code(HabitatBiome.OAK_HORNBEAM_FOREST, Zone.NONE);
		int meadow = code(HabitatBiome.HAY_MEADOW, Zone.NONE);
		int lake = code(HabitatBiome.LAKE, Zone.NONE);
		int fringe = code(HabitatBiome.WILLOW_POPLAR_FOREST, Zone.HERB_FRINGE);
		int bar = code(HabitatBiome.WILLOW_POPLAR_FOREST, Zone.POINT_BAR);
		int cliff = code(HabitatBiome.HAY_MEADOW, Zone.CLIFF_TOP);
		int beach = code(HabitatBiome.BEACH, Zone.NONE);
		int whiteDune = code(HabitatBiome.WHITE_DUNE, Zone.NONE);
		for (Ecotone.Layer layer : Ecotone.Layer.values()) {
			assertEquals(0, Ecotone.halfWidth(oak, lake, layer));
			assertEquals(0, Ecotone.halfWidth(lake, oak, layer));
			assertEquals(0, Ecotone.halfWidth(fringe, oak, layer), "a waterside zone keeps its code");
			assertEquals(0, Ecotone.halfWidth(fringe, lake, layer));
			assertEquals(0, Ecotone.halfWidth(oak, bar, layer), "bars stay sharp");
			assertEquals(0, Ecotone.halfWidth(meadow, cliff, layer), "the cliff stays sharp");
			assertEquals(0, Ecotone.halfWidth(beach, whiteDune, layer));
			assertEquals(0, Ecotone.halfWidth(oak, whiteDune, layer));
			assertEquals(0, Ecotone.halfWidth(beach, fringe, layer));
		}
		// The forest edge: the plants do not mix (the mantle and the fringe take their place).
		assertEquals(0, Ecotone.halfWidth(oak, meadow, Ecotone.Layer.PLANTS));
		double[] p = profile(oak, lake, Ecotone.Layer.TREES, 1);
		for (int d = 0; d < 40; d++) {
			assertEquals(d < 20 ? 0 : 1, p[d], 0, "no oak-hornbeam code in the lake and no lake code in the forest");
		}
		double[] m = profileWithMeander(oak, lake, Ecotone.Layer.TREES, 1);
		for (int d = 0; d < 40; d++) {
			assertEquals(d < 20 ? 0 : 1, m[d], 0, "the meander does not move the water's edge");
		}
	}

	/** A one-sided ramp of half-width h blocks: the share of side b is 1 − (|d| − 0.5) / h on side a, 1 on side b. */
	private static void checkOneSided(double[] p, int h, String what) {
		StringBuilder sb = new StringBuilder(what + ":");
		for (int d = -20; d < 20; d++) {
			sb.append(String.format(Locale.ROOT, " %d:%.2f", d, p[d + 20]));
		}
		System.out.println(sb);
		for (int d = -20; d < 20; d++) {
			double expected = d >= 0 ? 1 : Math.max(0, 1 - (-d - 0.5) / h);
			assertEquals(expected, p[d + 20], 0.06, what + " at x = " + d);
		}
	}

	/**
	 * The waterside zones of the land keep their codes; their land neighbors take them in a narrow one-sided ramp (the
	 * zone's herbs and tree density fade into the forest), in all layers (round 1 of the S8b review).
	 */
	@Test
	void watersideZonesFadeIntoTheLand() {
		int oak = code(HabitatBiome.OAK_HORNBEAM_FOREST, Zone.NONE);
		int meadow = code(HabitatBiome.WET_MEADOW, Zone.NONE);
		int fringe = code(HabitatBiome.WILLOW_POPLAR_FOREST, Zone.HERB_FRINGE);
		int herbs = code(HabitatBiome.ASH_ALDER_FOREST, Zone.TALL_HERBS);
		for (Ecotone.Layer layer : Ecotone.Layer.values()) {
			assertTrue(Ecotone.halfWidth(oak, fringe, layer) > 0, "the forest takes the zone's code, " + layer);
			assertTrue(Ecotone.sticky(oak, fringe, layer), "one-sided, " + layer);
		}
		checkOneSided(profile(oak, fringe, Ecotone.Layer.TREES, 1), 4, "oak-hornbeam | herb fringe, trees, realistic");
		checkOneSided(profile(meadow, herbs, Ecotone.Layer.PLANTS, 1), 4, "wet meadow | tall herbs, plants, realistic");
		checkOneSided(profile(oak, herbs, Ecotone.Layer.SOIL, 1), 2, "oak-hornbeam | tall herbs, soil, realistic");
		checkOneSided(profile(oak, fringe, Ecotone.Layer.TREES, 0.5), 2, "oak-hornbeam | herb fringe, trees, gameplay");
		double[] m = profileWithMeander(oak, fringe, Ecotone.Layer.TREES, 1);
		for (int d = 20; d < 40; d++) {
			assertEquals(1, m[d], 0, "the zone keeps its code at x = " + (d - 20));
		}
		assertTrue(m[19] > 0.8, "the forest next to the zone takes its code: " + m[19]);
	}

	/** The floodplain forests and alder carrs of §4.6 keep their own plants against other biomes; the other side mixes. */
	@Test
	void denseHabitatsKeepTheirPlants() {
		int oak = code(HabitatBiome.OAK_HORNBEAM_FOREST, Zone.NONE);
		int ash = code(HabitatBiome.ASH_ALDER_FOREST, Zone.NONE);
		int carr = code(HabitatBiome.ALDER_CARR, Zone.NONE);
		int wetMeadow = code(HabitatBiome.WET_MEADOW, Zone.NONE);
		assertEquals(0, Ecotone.halfWidth(ash, oak, Ecotone.Layer.PLANTS));
		assertTrue(Ecotone.halfWidth(oak, ash, Ecotone.Layer.PLANTS) > 0);
		assertTrue(Ecotone.halfWidth(ash, oak, Ecotone.Layer.TREES) > 0);
		assertTrue(Ecotone.halfWidth(ash, carr, Ecotone.Layer.PLANTS) > 0);
		// One-sided: from 1 at the border to 0 at 12 blocks in the oak-hornbeam forest.
		checkOneSided(profile(oak, ash, Ecotone.Layer.PLANTS, 1), 12, "oak-hornbeam | ash-alder forest, plants, realistic");
		// With the meander the ash-alder forest still keeps its plants, also against open land.
		for (int other : new int[] {oak, wetMeadow}) {
			double[] m = profileWithMeander(other, ash, Ecotone.Layer.PLANTS, 1);
			for (int d = 20; d < 40; d++) {
				assertEquals(1, m[d], 0, "ash-alder forest column at x = " + (d - 20) + " keeps its code");
			}
		}
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
		// A uniform region: the codes stay.
		assertArrayEquals(region(3, 4, (x, z) -> oak, 1).own(),
				Ecotone.effective(region(3, 4, (x, z) -> oak, 1), Ecotone.Layer.TREES, SEED, SALT, 1));
		// Patches: the same vector for most columns of a 3-block patch.
		int same = 0;
		for (int x = 0; x < 300; x += 3) {
			double[] a = Ecotone.vector(SEED, x + 1, 7, 3);
			double[] b = Ecotone.vector(SEED, x + 1, 8, 3);
			same += a[0] == b[0] ? 1 : 0;
		}
		assertTrue(same > 40, "columns of a patch share their vector: " + same);
	}

	/**
	 * The meander (round 1 of the S8b review): along a long straight border the 50% line of the trees moves by several
	 * blocks (a moving mean of 16 rows of the position of the transition), without it only by the noise of the draws.
	 */
	@Test
	void transitionsMeanderAtStraightBorders() {
		int oak = code(HabitatBiome.OAK_HORNBEAM_FOREST, Zone.NONE);
		int pine = code(HabitatBiome.FRESH_PINE_FOREST, Zone.NONE);
		int field = code(HabitatBiome.ARABLE_LAND, Zone.NONE);
		for (double k : new double[] {1, 0.5}) {
			for (int[] pair : new int[][] {{oak, pine}, {oak, field}}) {
				double with = meander(pair[0], pair[1], k, true);
				double without = meander(pair[0], pair[1], k, false);
				System.out.printf(Locale.ROOT, "meander of the 50%% line (k %.1f, %s | %s): %.2f blocks, without the field %.2f%n", k,
						Habitat.biome(pair[0]).id(), Habitat.biome(pair[1]).id(), with, without);
				assertTrue(with >= 1.5, "the 50% line meanders: " + with);
				assertTrue(without < 1.0, "without the meander only the noise of the draws: " + without);
			}
		}
	}

	/**
	 * Standard deviation (blocks) of the position of the trees' transition along a straight border along z (code a for
	 * x < 0, b for x ≥ 0), smoothed by a moving mean of 16 rows: the position of a row is the number of its columns in
	 * [−32, 32) with code a, minus 32.
	 */
	private static double meander(int a, int b, double k, boolean meander) {
		IntBinaryOperator world = (x, z) -> x < 0 ? a : b;
		int rows = 64 * 16;
		double[] position = new double[rows];
		for (int cx = -2; cx <= 1; cx++) {
			for (int cz = 0; cz < 64; cz++) {
				int[] eff = Ecotone.effective(region(cx, cz, world, k), Ecotone.Layer.TREES, SEED, Ecotone.TREES_SALT, 1, meander);
				for (int i = 0; i < 256; i++) {
					position[(cz << 4) + (i & 15)] += eff[i] == a ? 1 : 0;
				}
			}
		}
		double[] smooth = new double[rows - 16];
		double mean = 0;
		for (int r = 0; r < smooth.length; r++) {
			double s = 0;
			for (int j = 0; j < 16; j++) {
				s += position[r + j] - 32;
			}
			smooth[r] = s / 16;
			mean += smooth[r];
		}
		mean /= smooth.length;
		double var = 0;
		for (double v : smooth) {
			var += (v - mean) * (v - mean);
		}
		return Math.sqrt(var / smooth.length);
	}

	/** Mantle and fringe at a straight forest edge with the nominal widths (no noises). */
	@Test
	void forestEdgeBelts() {
		int pine = code(HabitatBiome.FRESH_PINE_FOREST, Zone.NONE);
		int meadow = code(HabitatBiome.HAY_MEADOW, Zone.NONE);
		IntBinaryOperator world = (x, z) -> x < 8 ? pine : meadow;
		for (double k : new double[] {1, 0.5}) {
			Ecotone.Region r = region(0, 0, world, k);
			int[] shrubCodes = r.own();
			int[] groundCodes = r.own();
			Ecotone.Edges edges = Ecotone.edges(r, null, shrubCodes, groundCodes);
			double depth = Math.max(Ecotone.MANTLE_MIN, Ecotone.MANTLE * k);
			double out = Math.max(1, Ecotone.MANTLE_OUT * k);
			double fringe = Math.max(Ecotone.FRINGE_MIN, Ecotone.FRINGE * k);
			double inner = Math.max(1, Ecotone.FRINGE_IN * k);
			for (int x = 0; x < 16; x++) {
				int i = x << 4 | 5;
				boolean forest = x < 8;
				double d = forest ? 8 - x : x - 7;
				boolean mantle = d <= (forest ? depth : out);
				boolean inFringe = forest ? d <= inner : d <= fringe;
				String at = "k " + k + ", x " + x;
				assertEquals((mantle ? Ecotone.Edge.MANTLE : Ecotone.Edge.NONE).ordinal(), edges.shrubs()[i], "mantle, " + at);
				assertEquals((inFringe ? Ecotone.Edge.FRINGE : Ecotone.Edge.NONE).ordinal(), edges.ground()[i], "fringe, " + at);
				assertEquals(forest || mantle ? pine : meadow, shrubCodes[i], "an open mantle column takes the forest's code, " + at);
				assertEquals(forest && !inFringe ? pine : meadow, groundCodes[i],
						"a forest column of the fringe takes the open land's code, " + at);
			}
		}
	}

	/**
	 * With the noises: the outer border of the fringe varies along the edge, the mantle has gaps on part of the edge, and
	 * single shrubs stand beyond it.
	 */
	@Test
	void forestEdgesAreNotBands() {
		int pine = code(HabitatBiome.FRESH_PINE_FOREST, Zone.NONE);
		int meadow = code(HabitatBiome.HAY_MEADOW, Zone.NONE);
		Ecotone.Noises noises = Ecotone.Noises.of(SEED);
		java.util.Set<Integer> outer = new java.util.HashSet<>();
		int rows = 0;
		int gapRows = 0;
		int beyond = 0;
		for (int cz = 0; cz < 60; cz++) {
			Ecotone.Region rz = region(0, cz, (x, z) -> x < 8 ? pine : meadow, 1);
			Ecotone.Edges e = Ecotone.edges(rz, noises, rz.own(), rz.own());
			for (int z = 0; z < 16; z++) {
				int last = -1;
				boolean mantle = false;
				for (int x = 0; x < 16; x++) {
					if (e.ground()[x << 4 | z] == Ecotone.Edge.FRINGE.ordinal()) {
						last = x;
					}
					if (e.shrubs()[x << 4 | z] == Ecotone.Edge.MANTLE.ordinal()) {
						mantle |= x >= 6 && x <= 8;
						beyond += x < 8 - 2 * Ecotone.MANTLE * 1.5 || x > 8 + 2 ? 1 : 0;
					}
				}
				outer.add(last);
				rows++;
				gapRows += mantle ? 0 : 1;
			}
		}
		double gaps = (double) gapRows / rows;
		System.out.printf(Locale.ROOT, "forest edge: fringe borders %s, rows without mantle at the edge %.2f, single shrubs %d%n",
				outer, gaps, beyond);
		assertTrue(outer.size() >= 4, "the fringe border is not a straight line: " + outer);
		assertTrue(gaps > 0.15 && gaps < 0.6, "the mantle has gaps on part of the edge: " + gaps);
	}
}
