package pl.polishforests.worldgen.surface;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.chunk.PolandDimension;
import pl.polishforests.worldgen.chunk.PolandScale;
import pl.polishforests.worldgen.chunk.VerticalScale;
import pl.polishforests.worldgen.habitat.Habitat;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.habitat.HabitatClassifier;
import pl.polishforests.worldgen.habitat.Soil;
import pl.polishforests.worldgen.habitat.Zone;
import pl.polishforests.worldgen.landscape.ColumnSample;
import pl.polishforests.worldgen.landscape.LandscapeModel;

/**
 * Step C1 (CP-D.9, docs/04-pionowy-wycinek.md "Projekt C1"): the top of the subnival belt of the Tatras outside the bare
 * rock is scree (granite scree on the silicate rocks, limestone scree on the limestone and dolomite) with turf only in
 * clumps (a new noise field, 10-25% of the columns, gathered in groups); the soils of every other biome, the ranker of the
 * alpine grassland and the dwarf pine included, do not depend on the clumps.
 */
class SubnivalSoilC1Test {
	private static final long SEED = 20260927L;
	/** The granite ridge patch of {@code -Pgametest=tatra} in the gameplay scale (the massif nearest the origin of seed A). */
	private static final int RIDGE_X = -24_456;
	private static final int RIDGE_Z = 87_894;

	@Test
	void subnivalTopIsScreeWithTurfClumps() {
		assertEquals(Material.GRANITE_SCREE, top(Soil.RANKER, HabitatBiome.SUBNIVAL_ROCKS, false));
		assertEquals(Material.GRASS_BLOCK, top(Soil.RANKER, HabitatBiome.SUBNIVAL_ROCKS, true));
		assertEquals(Material.LIMESTONE_SCREE, top(Soil.RENDZINA, HabitatBiome.SUBNIVAL_ROCKS, false));
		assertEquals(Material.GRASS_BLOCK, top(Soil.RENDZINA, HabitatBiome.SUBNIVAL_ROCKS, true));
		// The turf lies on a block of humus over the scree; the scree is two blocks deep, as the scree zone's.
		int granite = SoilBlocks.dry(Soil.RANKER, Zone.NONE, HabitatBiome.SUBNIVAL_ROCKS, 0.3, 0.3, true, 1);
		assertEquals(Material.COARSE_DIRT, SoilBlocks.layer1(granite));
		assertEquals(Material.GRANITE_SCREE, SoilBlocks.layer2(granite));
		int limestone = SoilBlocks.dry(Soil.RENDZINA, Zone.NONE, HabitatBiome.SUBNIVAL_ROCKS, 0.3, 0.3, true, 1);
		assertEquals(Material.RENDZINA, SoilBlocks.layer1(limestone));
		assertEquals(Material.LIMESTONE_SCREE, SoilBlocks.layer2(limestone));
		assertEquals(SoilBlocks.dry(Soil.GRANITE_SCREE, Zone.SCREE, HabitatBiome.SUBNIVAL_ROCKS, 0.3, 0.3, false, 1),
				SoilBlocks.dry(Soil.RANKER, Zone.NONE, HabitatBiome.SUBNIVAL_ROCKS, 0.3, 0.3, false, 1));
	}

	@Test
	void otherSoilsDoNotDependOnTheClumps() {
		int checked = 0;
		for (HabitatBiome biome : HabitatBiome.values()) {
			if (biome == HabitatBiome.SUBNIVAL_ROCKS) {
				continue;
			}
			for (Zone zone : Zone.values()) {
				for (Soil soil : Soil.values()) {
					for (double q1 = 0.02; q1 < 1; q1 += 0.1) {
						for (double q2 = 0.03; q2 < 1; q2 += 0.1) {
							assertEquals(SoilBlocks.dry(soil, zone, biome, q1, q2, false, 1), SoilBlocks.dry(soil, zone, biome, q1, q2, true, 1),
									biome + "/" + zone + "/" + soil);
							checked++;
						}
					}
				}
			}
		}
		assertTrue(checked > 100_000);
		// The ranker of the alpine grassland and the dwarf pine keeps its mixture of coarse dirt and turf.
		assertEquals(Material.COARSE_DIRT, top(Soil.RANKER, HabitatBiome.ALPINE_GRASSLAND, true, 0.2));
		assertEquals(Material.GRASS_BLOCK, top(Soil.RANKER, HabitatBiome.DWARF_PINE_SCRUB, false, 0.8));
	}

	/** The clump field: about 18% of the columns, in clumps of a few blocks, gathered in groups with stretches of bare scree. */
	@Test
	void turfClumpsAreFewAndGathered() {
		SurfaceBuilder builder = new SurfaceBuilder(SEED, VerticalScale.GAMEPLAY, true, false);
		int side = 1_024;
		boolean[] turf = new boolean[side * side];
		int n = 0;
		for (int x = 0; x < side; x++) {
			for (int z = 0; z < side; z++) {
				turf[x * side + z] = builder.subnivalTurf(x - 5_000, z + 7_000);
				n += turf[x * side + z] ? 1 : 0;
			}
		}
		double share = n / (double) turf.length;
		Stats s = stats(turf, side, side);
		// The share in windows of 32 × 32 columns: groups of clumps and stretches of nearly bare scree.
		int w = 32;
		double low = 1;
		double high = 0;
		for (int x = 0; x + w <= side; x += w) {
			for (int z = 0; z + w <= side; z += w) {
				int c = 0;
				for (int dx = 0; dx < w; dx++) {
					for (int dz = 0; dz < w; dz++) {
						c += turf[(x + dx) * side + z + dz] ? 1 : 0;
					}
				}
				low = Math.min(low, c / (double) (w * w));
				high = Math.max(high, c / (double) (w * w));
			}
		}
		System.out.printf(Locale.ROOT, "[C1 turf] share %.1f%%, with a turf neighbor %.1f%%, mean clump %.1f columns, "
				+ "largest %d, windows of 32: %.1f-%.1f%%%n", 100 * share, 100 * s.withNeighbor, s.meanClump, s.largest, 100 * low,
				100 * high);
		assertTrue(share >= 0.12 && share <= 0.24, "turf share " + share);
		// Independent columns at the same share would have a turf neighbor in about 55% of the cases.
		assertTrue(s.withNeighbor >= 0.7, "turf columns with a turf neighbor " + s.withNeighbor);
		assertTrue(s.meanClump >= 3 && s.largest <= 400, "clumps " + s.meanClump + ", largest " + s.largest);
		assertTrue(low <= 0.06 && high >= 0.28, "window shares " + low + "-" + high);
		// A pure function of the seed and the column; a new field (another seed, other clumps).
		SurfaceBuilder again = new SurfaceBuilder(SEED, VerticalScale.GAMEPLAY, true, false);
		SurfaceBuilder other = new SurfaceBuilder(SEED + 1, VerticalScale.GAMEPLAY, true, false);
		int same = 0;
		int differ = 0;
		for (int i = 0; i < 4_096; i++) {
			int x = 37 * i - 5_000;
			int z = 11 * i + 7_000;
			assertEquals(builder.subnivalTurf(x, z), again.subnivalTurf(x, z));
			same += builder.subnivalTurf(x, z) == other.subnivalTurf(x, z) ? 1 : 0;
			differ += builder.subnivalTurf(x, z) != other.subnivalTurf(x, z) ? 1 : 0;
		}
		assertTrue(differ > 300, "other seed: " + differ + " of " + (same + differ) + " columns differ");
	}

	/**
	 * The chunks of the subnival belt around the granite ridge of the gameplay Tatras (seed A), built as the generator
	 * builds them: on the columns of the subnival rocks without a zone the top is scree of the rock of the soil or turf
	 * (the bare rock of the steep faces apart), turf on 10-25% of the soil columns and gathered; the alpine grassland and the
	 * dwarf pine in the same chunks keep their tops (no scree on them).
	 */
	@Test
	void subnivalChunksOfTheTatras() {
		PolandScale scale = PolandScale.GAMEPLAY;
		LandscapeModel model = new LandscapeModel(SEED, scale.landscape(), 1.0, true);
		HabitatClassifier classifier = new HabitatClassifier(SEED, model, HabitatClassifier.Mode.NATURAL);
		SurfaceBuilder builder = new SurfaceBuilder(SEED, scale.vertical(), true, false);
		List<long[]> chunks = new ArrayList<>();
		java.util.Set<Long> seen = new java.util.HashSet<>();
		for (int r = 0; r <= 40 && chunks.size() < 24; r++) {
			for (int dx = -r; dx <= r && chunks.size() < 24; dx++) {
				for (int dz = -r; dz <= r && chunks.size() < 24; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					int x = RIDGE_X + 40 * dx;
					int z = RIDGE_Z + 40 * dz;
					int code = classifier.classify(model.sample(x, z), x, z);
					long key = (long) (x >> 4) << 32 | (z >> 4) & 0xFFFF_FFFFL;
					if (Habitat.biome(code) == HabitatBiome.SUBNIVAL_ROCKS && Habitat.zone(code) == Zone.NONE && seen.add(key)) {
						chunks.add(new long[] {x >> 4, z >> 4});
					}
				}
			}
		}
		assertTrue(chunks.size() >= 8, "subnival chunks near the granite ridge: " + chunks.size());
		Map<String, Integer> tops = new TreeMap<>();
		int soilColumns = 0;
		int turfColumns = 0;
		int bare = 0;
		int turfWithNeighbor = 0;
		int wrongScree = 0;
		int otherTops = 0;
		int openOnScree = 0;
		for (long[] c : chunks) {
			int minX = (int) c[0] << 4;
			int minZ = (int) c[1] << 4;
			ColumnSample[] columns = new ColumnSample[256];
			int[] codes = new int[256];
			for (int i = 0; i < 256; i++) {
				columns[i] = model.sample(minX + (i >> 4), minZ + (i & 15));
				codes[i] = classifier.classify(columns[i], minX + (i >> 4), minZ + (i & 15));
			}
			ChunkSurface plan = builder.build(columns, codes, minX, minZ, scale.vertical().minY(), scale.vertical().maxY(),
					PolandDimension.DEEP_ROCK_Y, model::sample);
			boolean[] turf = new boolean[256];
			boolean[] subnival = new boolean[256];
			for (int i = 0; i < 256; i++) {
				HabitatBiome b = Habitat.biome(codes[i]);
				Material top = plan.topMaterial(i);
				if (plan.wet(i) || (plan.flags(i) & (ChunkSurface.PUDDLE | ChunkSurface.PUDDLE_MUD | ChunkSurface.HUMMOCK)) != 0) {
					continue;
				}
				if (b == HabitatBiome.ALPINE_GRASSLAND || b == HabitatBiome.DWARF_PINE_SCRUB) {
					openOnScree += top == Material.GRANITE_SCREE || top == Material.LIMESTONE_SCREE ? 1 : 0;
					continue;
				}
				if (b != HabitatBiome.SUBNIVAL_ROCKS || Habitat.zone(codes[i]) != Zone.NONE) {
					continue;
				}
				tops.merge(top.id(), 1, Integer::sum);
				if (top == Material.GRASS_BLOCK) {
					soilColumns++;
					turfColumns++;
					turf[i] = true;
					subnival[i] = true;
				} else if (top == Material.GRANITE_SCREE || top == Material.LIMESTONE_SCREE) {
					soilColumns++;
					subnival[i] = true;
					Material want = Habitat.soil(codes[i]) == Soil.RENDZINA ? Material.LIMESTONE_SCREE : Material.GRANITE_SCREE;
					wrongScree += top != want ? 1 : 0;
				} else if (top == Material.tatraRock(columns[i].terrain().rock())) {
					bare++;
				} else {
					otherTops++;
				}
			}
			for (int i = 0; i < 256; i++) {
				if (!turf[i]) {
					continue;
				}
				int x = i >> 4;
				int z = i & 15;
				boolean n = x > 0 && turf[i - 16] || x < 15 && turf[i + 16] || z > 0 && turf[i - 1] || z < 15 && turf[i + 1];
				turfWithNeighbor += n ? 1 : 0;
			}
		}
		double share = turfColumns / (double) Math.max(1, soilColumns);
		double gathered = turfWithNeighbor / (double) Math.max(1, turfColumns);
		System.out.printf(Locale.ROOT, "[C1 subnival] %d chunks: tops %s; soil columns %d, turf %.1f%% (%.1f%% with a turf "
				+ "neighbor), bare rock %d, other %d, scree of the wrong rock %d, scree on the alpine grassland or dwarf pine %d%n",
				chunks.size(), tops, soilColumns, 100 * share, 100 * gathered, bare, otherTops, wrongScree, openOnScree);
		assertTrue(soilColumns >= 500, "subnival soil columns " + soilColumns);
		assertTrue(share >= 0.08 && share <= 0.25, "turf share " + share);
		assertTrue(gathered >= 0.6, "turf columns with a turf neighbor " + gathered);
		assertEquals(0, wrongScree);
		assertEquals(0, otherTops, "tops " + tops);
		assertEquals(0, openOnScree, "scree tops on the alpine grassland or the dwarf pine");
		assertNotEquals(0, tops.getOrDefault(Material.GRANITE_SCREE.id(), 0) + tops.getOrDefault(Material.LIMESTONE_SCREE.id(), 0));
	}

	private static Material top(Soil soil, HabitatBiome biome, boolean turf) {
		return top(soil, biome, turf, 0.5);
	}

	private static Material top(Soil soil, HabitatBiome biome, boolean turf, double q1) {
		return SoilBlocks.top(SoilBlocks.dry(soil, Zone.NONE, biome, q1, 0.5, turf, 1));
	}

	private record Stats(double withNeighbor, double meanClump, int largest) {
	}

	/** Share of the turf columns with a turf neighbor (by a side), mean and largest clump (side-connected). */
	private static Stats stats(boolean[] turf, int w, int h) {
		int with = 0;
		int n = 0;
		boolean[] seen = new boolean[turf.length];
		int[] queue = new int[turf.length];
		int clumps = 0;
		int largest = 0;
		for (int x = 0; x < w; x++) {
			for (int z = 0; z < h; z++) {
				int i = x * h + z;
				if (!turf[i]) {
					continue;
				}
				n++;
				boolean nb = x > 0 && turf[i - h] || x < w - 1 && turf[i + h] || z > 0 && turf[i - 1] || z < h - 1 && turf[i + 1];
				with += nb ? 1 : 0;
				if (seen[i]) {
					continue;
				}
				int head = 0;
				int tail = 0;
				queue[tail++] = i;
				seen[i] = true;
				while (head < tail) {
					int a = queue[head++];
					int ax = a / h;
					int az = a % h;
					int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
					for (int[] sd : sides) {
						int bx = ax + sd[0];
						int bz = az + sd[1];
						if (bx < 0 || bz < 0 || bx >= w || bz >= h) {
							continue;
						}
						int b = bx * h + bz;
						if (turf[b] && !seen[b]) {
							seen[b] = true;
							queue[tail++] = b;
						}
					}
				}
				clumps++;
				largest = Math.max(largest, tail);
			}
		}
		return new Stats(n == 0 ? 0 : with / (double) n, clumps == 0 ? 0 : n / (double) clumps, largest);
	}
}
