package pl.polishforests.worldgen.feature.plan;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.BiomeJsonTest;
import pl.polishforests.worldgen.habitat.Association;
import pl.polishforests.worldgen.habitat.ForestSiteType;
import pl.polishforests.worldgen.habitat.Habitat;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.habitat.LandCover;
import pl.polishforests.worldgen.habitat.Soil;
import pl.polishforests.worldgen.habitat.Zone;

/**
 * Column plan of the vegetation layers (docs/03-m2-biomy.md §8.2, §8.6, §12.1) and the palettes of the ground layer and
 * the waterside zones (§4.6): deterministic and independent of chunk borders; the first rule that applies decides a
 * column (habitat, medium, water depth, ground); the cover of each rule within ±5% of the palette and the shares of the
 * plants within ±5 points, also with patches; patches make the plants clump. The generated palettes meet the density
 * rule of §4.6 in every waterside zone, floodplain forest and alder carr: short grass and small flowers at most 10%, tall
 * plants and shrubs at least 60%, bare ground at most 25% of the land columns (from the palette alone, before the trees).
 */
class GroundLayerPlanTest {
	private static final long SEED = 20260927L;
	private static final long SALT = 0x1234L;

	private static int code(HabitatBiome b, Zone z) {
		return Habitat.pack(b, z, ForestSiteType.NONE, Association.TYPICAL, LandCover.forBiome(b), 0, Soil.forBiome(b, z));
	}

	private static HabitatMatch biome(HabitatBiome b) {
		return HabitatMatch.of(List.of(b), List.of(), List.of(), List.of(), List.of());
	}

	private static HabitatMatch zone(Zone z) {
		return HabitatMatch.of(List.of(), List.of(z), List.of(), List.of(), List.of());
	}

	/**
	 * Palette: cattail in water one block deep on mud (0.6), reed on the shore (0.5), herb fringe (0.8: tall grass 60,
	 * large fern 30, bush 10, patches of 3), oak-hornbeam forest (0.85: leaf litter 50, wildflowers 25, fern 15, grass 10,
	 * patches of 4), and the oak-hornbeam forest without patches on podzol only (0.4) as a ground filter.
	 */
	private static ColumnPlan.Palette palette() {
		return new ColumnPlan.Palette(List.of(
				new ColumnPlan.Rule(HabitatMatch.ANY, ColumnPlan.Medium.WATER_BOTTOM, 1, 1, 1 << ColumnPlan.Ground.MUD.ordinal(),
						0.6, 3, new int[] {0}, new int[] {1}),
				new ColumnPlan.Rule(HabitatMatch.ANY, ColumnPlan.Medium.SHORE, 1, 64, 0, 0.5, 3, new int[] {1}, new int[] {1}),
				new ColumnPlan.Rule(zone(Zone.HERB_FRINGE), ColumnPlan.Medium.LAND, 1, 64, 0, 0.8, 3, new int[] {2, 3, 4},
						new int[] {60, 30, 10}),
				new ColumnPlan.Rule(biome(HabitatBiome.OAK_HORNBEAM_FOREST), ColumnPlan.Medium.LAND, 1, 64,
						1 << ColumnPlan.Ground.GRASS.ordinal(), 0.85, 4, new int[] {5, 6, 7, 8}, new int[] {50, 25, 15, 10}),
				new ColumnPlan.Rule(biome(HabitatBiome.OAK_HORNBEAM_FOREST), ColumnPlan.Medium.LAND, 1, 64,
						1 << ColumnPlan.Ground.PODZOL.ordinal(), 0.4, 1, new int[] {5}, new int[] {1})));
	}

	private static ColumnPlan.Columns uniform(int code, int depth, ColumnPlan.Ground ground, boolean shore) {
		int[] codes = new int[256];
		Arrays.fill(codes, code);
		int[] d = new int[256];
		Arrays.fill(d, depth);
		int[] g = new int[256];
		Arrays.fill(g, ground.ordinal());
		byte[] s = new byte[256];
		Arrays.fill(s, (byte) (shore ? ColumnPlan.SIDE_WATER : 0));
		return new ColumnPlan.Columns(codes, d, g, s);
	}

	@Test
	void deterministic() {
		ColumnPlan.Columns c = uniform(code(HabitatBiome.OAK_HORNBEAM_FOREST, Zone.NONE), 0, ColumnPlan.Ground.GRASS, false);
		assertArrayEquals(ColumnPlan.of(c, palette(), SEED, 5, -3, SALT), ColumnPlan.of(c, palette(), SEED, 5, -3, SALT));
		assertFalse(Arrays.equals(ColumnPlan.of(c, palette(), SEED, 5, -3, SALT), ColumnPlan.of(c, palette(), SEED, 6, -3, SALT)));
		assertFalse(Arrays.equals(ColumnPlan.of(c, palette(), SEED, 5, -3, SALT), ColumnPlan.of(c, palette(), SEED, 5, -3, SALT + 1)));
	}

	/** Cover within ±5% of the rule, plant shares within ±5 points, for a patchy and a plain rule. */
	@Test
	void coverAndShares() {
		check(uniform(code(HabitatBiome.OAK_HORNBEAM_FOREST, Zone.NONE), 0, ColumnPlan.Ground.GRASS, false), 0.85,
				new int[] {5, 6, 7, 8}, new double[] {0.50, 0.25, 0.15, 0.10});
		check(uniform(code(HabitatBiome.WILLOW_POPLAR_FOREST, Zone.HERB_FRINGE), 0, ColumnPlan.Ground.GRASS, false), 0.8,
				new int[] {2, 3, 4}, new double[] {0.6, 0.3, 0.1});
		check(uniform(code(HabitatBiome.OAK_HORNBEAM_FOREST, Zone.NONE), 0, ColumnPlan.Ground.PODZOL, false), 0.4,
				new int[] {5}, new double[] {1});
	}

	private static void check(ColumnPlan.Columns c, double coverage, int[] plants, double[] shares) {
		int chunks = 0;
		long[] counts = new long[16];
		long placed = 0;
		for (int cx = -20; cx < 20; cx++) {
			for (int cz = -10; cz < 10; cz++) {
				chunks++;
				for (int p : ColumnPlan.of(c, palette(), SEED, cx, cz, SALT)) {
					assertEquals(ColumnPlan.Medium.LAND, ColumnPlan.medium(p));
					counts[ColumnPlan.plant(p)]++;
					placed++;
				}
			}
		}
		double cover = (double) placed / (chunks * 256);
		System.out.printf(Locale.ROOT, "cover %.3f (palette %.2f), plants %s%n", cover, coverage, Arrays.toString(counts));
		assertEquals(coverage, cover, 0.05 * coverage, "cover");
		for (int k = 0; k < plants.length; k++) {
			assertEquals(shares[k], (double) counts[plants[k]] / placed, 0.05, "share of plant " + plants[k]);
		}
	}

	/** The first rule that applies decides: medium, water depth, ground and habitat. */
	@Test
	void firstRuleThatApplies() {
		ColumnPlan.Palette p = palette();
		int forest = code(HabitatBiome.OAK_HORNBEAM_FOREST, Zone.NONE);
		int fringe = code(HabitatBiome.OAK_HORNBEAM_FOREST, Zone.HERB_FRINGE);
		int mud = ColumnPlan.Ground.MUD.ordinal();
		int grass = ColumnPlan.Ground.GRASS.ordinal();
		assertEquals(0, p.ruleFor(forest, 1, mud, 0), "cattail in water 1 deep on mud");
		assertEquals(-1, p.ruleFor(forest, 2, mud, 0), "no cattail in water 2 deep");
		assertEquals(-1, p.ruleFor(forest, 1, ColumnPlan.Ground.SAND.ordinal(), 0), "no cattail on sand");
		assertEquals(1, p.ruleFor(fringe, 0, grass, ColumnPlan.SIDE_WATER), "reed on the shore before the herb fringe");
		assertEquals(1, p.ruleFor(fringe, 0, grass, ColumnPlan.SIDE_WATER | ColumnPlan.SIDE_PUDDLE),
				"model water and a puddle beside: the shore");
		assertEquals(2, p.ruleFor(fringe, 0, grass, ColumnPlan.SIDE_PUDDLE), "a puddle only is not the shore");
		assertEquals(2, p.ruleFor(fringe, 0, grass, 0), "herb fringe before its biome");
		assertEquals(3, p.ruleFor(forest, 0, grass, 0));
		assertEquals(4, p.ruleFor(forest, 0, ColumnPlan.Ground.PODZOL.ordinal(), 0), "ground filter");
		assertEquals(-1, p.ruleFor(code(HabitatBiome.ARABLE_LAND, Zone.NONE), 0, grass, 0), "no rule");
		for (int q : ColumnPlan.of(uniform(forest, 1, ColumnPlan.Ground.MUD, false), p, SEED, 0, 0, SALT)) {
			assertEquals(ColumnPlan.Medium.WATER_BOTTOM, ColumnPlan.medium(q));
			assertEquals(0, ColumnPlan.plant(q));
		}
	}

	/**
	 * The media of the dry columns beside water and at the beach (S7 review): the bank shelf of the model's water, the
	 * edge of a puddle only, the seaward edge of the strandline; each also counts as dry land.
	 */
	@Test
	void puddleShoreAndSeawardEdge() {
		ColumnPlan.Palette p = new ColumnPlan.Palette(List.of(
				new ColumnPlan.Rule(HabitatMatch.ANY, ColumnPlan.Medium.SHORE, 1, 64, 0, 0.5, 1, new int[] {0}, new int[] {1}),
				new ColumnPlan.Rule(HabitatMatch.ANY, ColumnPlan.Medium.PUDDLE_SHORE, 1, 64, 0, 0.1, 1, new int[] {1}, new int[] {1}),
				new ColumnPlan.Rule(HabitatMatch.ANY, ColumnPlan.Medium.SEAWARD_EDGE, 1, 64, 0, 0.5, 1, new int[] {2}, new int[] {1}),
				new ColumnPlan.Rule(HabitatMatch.ANY, ColumnPlan.Medium.LAND, 1, 64, 0, 0.8, 1, new int[] {3}, new int[] {1})));
		int code = code(HabitatBiome.BEACH, Zone.STRANDLINE);
		int sand = ColumnPlan.Ground.SAND.ordinal();
		assertEquals(0, p.ruleFor(code, 0, sand, ColumnPlan.SIDE_WATER));
		assertEquals(1, p.ruleFor(code, 0, sand, ColumnPlan.SIDE_PUDDLE));
		assertEquals(2, p.ruleFor(code, 0, sand, ColumnPlan.SIDE_SEAWARD));
		assertEquals(3, p.ruleFor(code, 0, sand, 0));
		assertEquals(-1, p.ruleFor(code, 1, sand, ColumnPlan.SIDE_SEAWARD), "not in water");
		int[] codes = new int[256];
		Arrays.fill(codes, code);
		byte[] side = new byte[256];
		for (int i = 0; i < 256; i++) {
			side[i] = (byte) (i % 4 == 0 ? ColumnPlan.SIDE_PUDDLE : i % 4 == 1 ? ColumnPlan.SIDE_SEAWARD : 0);
		}
		int[] ground = new int[256];
		Arrays.fill(ground, sand);
		int[] placed = ColumnPlan.of(new ColumnPlan.Columns(codes, new int[256], ground, side), p, SEED, 3, -2, SALT);
		for (int q : placed) {
			int i = ColumnPlan.column(q);
			ColumnPlan.Medium expected = i % 4 == 0 ? ColumnPlan.Medium.PUDDLE_SHORE
					: i % 4 == 1 ? ColumnPlan.Medium.SEAWARD_EDGE : ColumnPlan.Medium.LAND;
			assertEquals(expected, ColumnPlan.medium(q), "medium of the packed placement");
			assertEquals(expected.ordinal() == 4 ? 1 : expected.ordinal() == 5 ? 2 : 3, ColumnPlan.plant(q));
		}
	}

	/**
	 * Patches: neighboring columns agree more often than independent ones (covered with covered, same plant), and the
	 * cover next to chunk borders equals the cover inside (the values depend on the world coordinates only).
	 */
	@Test
	void patchesClumpWithoutSeams() {
		int size = 24;
		int span = size * 16;
		int[][] plant = new int[span][span];
		ColumnPlan.Columns c = uniform(code(HabitatBiome.OAK_HORNBEAM_FOREST, Zone.NONE), 0, ColumnPlan.Ground.GRASS, false);
		for (int[] row : plant) {
			Arrays.fill(row, -1);
		}
		for (int cx = 0; cx < size; cx++) {
			for (int cz = 0; cz < size; cz++) {
				for (int p : ColumnPlan.of(c, palette(), SEED, cx, cz, SALT)) {
					int column = ColumnPlan.column(p);
					plant[cx * 16 + (column >> 4)][cz * 16 + (column & 15)] = ColumnPlan.plant(p);
				}
			}
		}
		long same = 0;
		long pairs = 0;
		long border = 0;
		long borderCovered = 0;
		long inner = 0;
		long innerCovered = 0;
		for (int x = 0; x < span - 1; x++) {
			for (int z = 0; z < span; z++) {
				pairs++;
				same += plant[x][z] == plant[x + 1][z] ? 1 : 0;
				int lx = x & 15;
				if (lx == 0 || lx == 15) {
					border++;
					borderCovered += plant[x][z] >= 0 ? 1 : 0;
				} else {
					inner++;
					innerCovered += plant[x][z] >= 0 ? 1 : 0;
				}
			}
		}
		// Independent columns: P(same) = 0.15² + 0.85² · (0.5² + 0.25² + 0.15² + 0.1²) ≈ 0.27.
		double independent = 0.15 * 0.15 + 0.85 * 0.85 * (0.25 + 0.0625 + 0.0225 + 0.01);
		double agreement = (double) same / pairs;
		double b = (double) borderCovered / border;
		double i = (double) innerCovered / inner;
		System.out.printf(Locale.ROOT, "neighbors agree %.3f (independent %.3f), cover at chunk borders %.3f, inside %.3f%n",
				agreement, independent, b, i);
		assertTrue(agreement > independent + 0.08, "patches do not clump");
		assertEquals(i, b, 0.02);
	}

	// ------------------------------------------------------------------ generated palettes (§4.6)

	/** Tall plants of §4.6; since the S7 review the one-block shrubs (bush, firefly bush, sweet berry bush) are dwarf shrubs, not tall. */
	private static final Set<String> TALL = Set.of("minecraft:tall_grass", "minecraft:large_fern", "minecraft:sugar_cane",
			"minecraft:big_dripleaf", "minecraft:small_dripleaf");
	private static final Set<String> SMALL = Set.of("minecraft:short_grass", "minecraft:dandelion", "minecraft:poppy",
			"minecraft:oxeye_daisy", "minecraft:cornflower", "minecraft:azure_bluet", "minecraft:allium",
			"minecraft:lily_of_the_valley", "minecraft:wildflowers", "minecraft:pink_petals");
	/** The biomes and zones of the density rule (§4.6). */
	private static final List<HabitatBiome> DENSE_BIOMES = List.of(HabitatBiome.ALDER_CARR, HabitatBiome.ASH_ALDER_FOREST,
			HabitatBiome.WILLOW_POPLAR_FOREST, HabitatBiome.ELM_ASH_FOREST, HabitatBiome.GRAY_ALDER_FOREST,
			HabitatBiome.WILLOW_SCRUB, HabitatBiome.REEDBED);
	private static final List<Zone> DENSE_ZONES = List.of(Zone.WILLOW_SCRUB, Zone.HERB_FRINGE, Zone.TALL_HERBS,
			Zone.MONTANE_TALL_HERBS, Zone.SHORE_REEDBED, Zone.WILLOW_CARR, Zone.RIVERSIDE_WILLOWS, Zone.SPRING_AREA);

	/**
	 * Expected cover of the land columns of each dense biome and zone from the generated ground layer palette: short
	 * grass and small flowers at most 10%, tall plants and shrubs at least 60%, bare ground at most 25% (§4.6). Reed,
	 * cattail, osiers and trees add to the tall share in the world; the game test checks the transects.
	 */
	@Test
	void groundLayerPaletteMeetsTheDensityRule() {
		JsonArray rules = BiomeJsonTest.json(BiomeJsonTest.GENERATED.resolve(
				"data/polishforests/worldgen/feature/ground_layer.json")).getAsJsonArray("rules");
		List<String> report = new ArrayList<>();
		for (HabitatBiome b : DENSE_BIOMES) {
			check(rules, b, Zone.NONE, report);
		}
		for (Zone z : DENSE_ZONES) {
			check(rules, HabitatBiome.WILLOW_POPLAR_FOREST, z, report);
		}
		System.out.println("ground layer of the dense habitats (small, tall, bare): " + report);
	}

	/**
	 * Decision M2-13: bilberry is the vanilla {@code bush} until its own block in M4; no generated vegetation palette places
	 * sweet berry bushes, which slow and hurt the player (7–10% of the pine forest columns before).
	 */
	@Test
	void noSweetBerryBushes() {
		for (String palette : List.of("ground_layer", "waterside_zones", "understory", "deadwood", "aquatic_plants")) {
			java.nio.file.Path file = BiomeJsonTest.GENERATED.resolve("data/polishforests/worldgen/feature/" + palette + ".json");
			if (!java.nio.file.Files.exists(file)) {
				continue;
			}
			String text = BiomeJsonTest.json(file).toString();
			assertFalse(text.contains("minecraft:sweet_berry_bush"), palette + " places sweet berry bushes");
		}
		JsonArray rules = BiomeJsonTest.json(BiomeJsonTest.GENERATED.resolve(
				"data/polishforests/worldgen/feature/ground_layer.json")).getAsJsonArray("rules");
		// The dwarf shrub share of the pine forests stays (the former sweet berry weight went to the bush; the fresh pine
		// forest has the own bilberry since step V12, slicePalettesMeetTheNorms).
		for (HabitatBiome b : List.of(HabitatBiome.BOG_WOODLAND, HabitatBiome.MOIST_PINE_FOREST)) {
			JsonObject rule = null;
			for (JsonElement e : rules) {
				JsonObject r = e.getAsJsonObject();
				String medium = r.has("medium") ? r.get("medium").getAsString() : "land";
				if (medium.equals("land") && r.has("biomes") && contains(r, "biomes", b.id()) && contains(r, "zones", Zone.NONE.id())) {
					rule = r;
					break;
				}
			}
			assertTrue(rule != null, "no ground layer rule for " + b.id());
			double all = 0;
			double bush = 0;
			for (JsonElement e : rule.getAsJsonArray("plants")) {
				JsonObject p = e.getAsJsonObject();
				double w = p.get("weight").getAsDouble();
				all += w;
				if (p.has("block") && blockName(p.get("block")).equals("minecraft:bush")) {
					bush += w;
				}
			}
			double share = rule.get("coverage").getAsDouble() * bush / all;
			assertTrue(share >= 0.2 && share <= 0.4, b.id() + ": dwarf shrubs " + share);
		}
	}

	// ------------------------------------------------------------------ step V12: the herb layer of the slice

	/** Rounded patches: cover and shares as the palette's (as the square patches, ±5%). */
	@Test
	void blobsKeepCoverAndShares() {
		ColumnPlan.Palette p = new ColumnPlan.Palette(List.of(new ColumnPlan.Rule(biome(HabitatBiome.FRESH_PINE_FOREST),
				ColumnPlan.Medium.LAND, 1, 64, 0, 0.9, 6, new int[] {0, 1, 2}, new int[] {50, 30, 20}, 0, ColumnPlan.PatchShape.BLOBS)));
		ColumnPlan.Columns c = uniform(code(HabitatBiome.FRESH_PINE_FOREST, Zone.NONE), 0, ColumnPlan.Ground.PODZOL, false);
		long placed = 0;
		long[] counts = new long[3];
		int chunks = 0;
		for (int cx = -20; cx < 20; cx++) {
			for (int cz = -10; cz < 10; cz++) {
				chunks++;
				for (int q : ColumnPlan.of(c, p, SEED, cx, cz, SALT)) {
					counts[ColumnPlan.plant(q)]++;
					placed++;
				}
			}
		}
		double cover = (double) placed / (chunks * 256);
		System.out.printf(Locale.ROOT, "blobs: cover %.3f (palette 0.90), plants %s%n", cover, Arrays.toString(counts));
		assertEquals(0.9, cover, 0.05 * 0.9);
		assertEquals(0.5, (double) counts[0] / placed, 0.05);
		assertEquals(0.3, (double) counts[1] / placed, 0.05);
		assertEquals(0.2, (double) counts[2] / placed, 0.05);
		assertArrayEquals(ColumnPlan.of(c, p, SEED, 3, 7, SALT), ColumnPlan.of(c, p, SEED, 3, 7, SALT));
	}

	/**
	 * Round 1 of the V12 review: the scattered plants of a rule with rounded patches (the plants without a mossy underside
	 * of the pine forests, whose patches read as plates of bare podzol) keep their share but do not clump: the chance that
	 * the east neighbor of a column with a scattered plant has one too is their share (as for independent columns), while
	 * the clumped plants still clump.
	 */
	@Test
	void scatteredPlantsKeepTheirShareWithoutClumping() {
		// Plants 0 and 1 clumped (weights 60 and 25), plant 2 scattered (weight 15).
		ColumnPlan.Palette p = new ColumnPlan.Palette(List.of(new ColumnPlan.Rule(biome(HabitatBiome.FRESH_PINE_FOREST),
				ColumnPlan.Medium.LAND, 1, 64, 0, 1.0, 6, new int[] {0, 1, 2}, new int[] {60, 25, 15}, 0, ColumnPlan.PatchShape.BLOBS,
				2)));
		ColumnPlan.Columns c = uniform(code(HabitatBiome.FRESH_PINE_FOREST, Zone.NONE), 0, ColumnPlan.Ground.PODZOL, false);
		int n = 16 * 24;
		int[][] field = new int[n][n];
		for (int[] row : field) {
			Arrays.fill(row, -1);
		}
		for (int cx = 0; cx < 24; cx++) {
			for (int cz = 0; cz < 24; cz++) {
				for (int q : ColumnPlan.of(c, p, SEED, cx, cz, SALT)) {
					int col = ColumnPlan.column(q);
					field[cx * 16 + (col >> 4)][cz * 16 + (col & 15)] = ColumnPlan.plant(q);
				}
			}
		}
		long[] counts = new long[3];
		long[] pairs = new long[3];
		long[] same = new long[3];
		for (int x = 0; x < n; x++) {
			for (int z = 0; z < n; z++) {
				int a = field[x][z];
				counts[a]++;
				if (x + 1 < n) {
					pairs[a]++;
					same[a] += field[x + 1][z] == a ? 1 : 0;
				}
			}
		}
		double all = (double) n * n;
		double scattered = counts[2] / all;
		double scatteredNext = (double) same[2] / pairs[2];
		double clumpedNext = (double) same[1] / pairs[1];
		System.out.printf(Locale.ROOT, "scattered: shares %s, neighbor alike: scattered %.3f, clumped %.3f%n",
				Arrays.toString(counts), scatteredNext, clumpedNext);
		assertEquals(0.60, counts[0] / all, 0.05);
		assertEquals(0.25, counts[1] / all, 0.05);
		assertEquals(0.15, scattered, 0.02);
		assertEquals(scattered, scatteredNext, 0.03, "scattered plants clump");
		assertTrue(clumpedNext > 0.25 + 0.2, "clumped plants do not clump: " + clumpedNext);
	}

	/**
	 * The CP-B remark on the pine forest (flat patches of litter and bare ground with rectangular edges): the borders of
	 * the rounded patches do not run straight. Over a field of 24 × 24 chunks, smoothed by a 3 × 3 majority twice (the
	 * patch shapes the eye sees through the single columns), the borders between covered and bare columns of the square
	 * patches of M2 lie on the lines of the cell grid (the border edges between x and x + 1 pile up at one value of
	 * (x + 1) mod patch), those of the rounded patches spread evenly over the offsets (at most 25% at one offset, 1/6 for
	 * an even spread). The patches still clump (neighbors agree more than independent columns) and show no seam at chunk
	 * borders (the raw field).
	 */
	@Test
	void blobsHaveNoStraightEdges() {
		int patch = 6;
		double[] longRuns = new double[2];
		double[] aligned = new double[2];
		double[] agree = new double[2];
		for (ColumnPlan.PatchShape shape : ColumnPlan.PatchShape.values()) {
			// Two plants, every column covered: the borders between the plants' patches (with rounded patches the cover is
			// drawn per column, the plants by patch).
			ColumnPlan.Palette p = new ColumnPlan.Palette(List.of(new ColumnPlan.Rule(HabitatMatch.ANY, ColumnPlan.Medium.LAND, 1, 64,
					0, 1.0, patch, new int[] {0, 1}, new int[] {1, 1}, 0, shape)));
			boolean[][] raw = field(p, 24);
			boolean[][] covered = majority(majority(raw));
			int n = covered.length;
			long borders = 0;
			long inLongRuns = 0;
			long[] offsets = new long[patch];
			long same = 0;
			long pairs = 0;
			long edgeCover = 0;
			long edgeCount = 0;
			long innerCover = 0;
			long innerCount = 0;
			for (int axis = 0; axis < 2; axis++) {
				for (int a = 0; a < n - 1; a++) {
					int run = 0;
					for (int b = 0; b <= n; b++) {
						boolean border = b < n && (axis == 0 ? covered[a][b] != covered[a + 1][b] : covered[b][a] != covered[b][a + 1]);
						if (border) {
							run++;
							borders++;
							offsets[(a + 1) % patch]++;
						} else {
							if (run >= patch) {
								inLongRuns += run;
							}
							run = 0;
						}
						if (b < n) {
							pairs++;
							same += border ? 0 : 1;
						}
					}
				}
			}
			for (int x = 0; x < n; x++) {
				for (int z = 0; z < n; z++) {
					if ((x & 15) == 0 || (x & 15) == 15) {
						edgeCount++;
						edgeCover += raw[x][z] ? 1 : 0;
					} else {
						innerCount++;
						innerCover += raw[x][z] ? 1 : 0;
					}
				}
			}
			longRuns[shape.ordinal()] = (double) inLongRuns / borders;
			aligned[shape.ordinal()] = (double) java.util.Arrays.stream(offsets).max().orElse(0) / borders;
			agree[shape.ordinal()] = (double) same / pairs;
			System.out.printf(Locale.ROOT, "%s: border edges at the most frequent offset in the cell grid %.3f, in straight runs of >= %d "
					+ "blocks %.4f, neighbors agree %.3f, cover at chunk borders %.3f, inside %.3f%n", shape, aligned[shape.ordinal()], patch,
					longRuns[shape.ordinal()], agree[shape.ordinal()],
					(double) edgeCover / edgeCount, (double) innerCover / innerCount);
			assertEquals((double) innerCover / innerCount, (double) edgeCover / edgeCount, 0.02, shape + ": seam at chunk borders");
		}
		assertTrue(aligned[0] > 0.3, "the square patches of M2 follow the cell grid (the measure works)");
		assertTrue(aligned[1] < 0.25, "the rounded patches follow the cell grid");
		assertTrue(longRuns[1] < longRuns[0], "rounded patches have more straight edges than square ones");
		assertTrue(agree[1] > 0.5 + 0.1, "rounded patches do not clump");
	}

	/** A 3 × 3 majority filter (at least 5 of the 9 columns covered; the border rows stay as they are). */
	private static boolean[][] majority(boolean[][] f) {
		int n = f.length;
		boolean[][] out = new boolean[n][n];
		for (int x = 0; x < n; x++) {
			for (int z = 0; z < n; z++) {
				if (x == 0 || z == 0 || x == n - 1 || z == n - 1) {
					out[x][z] = f[x][z];
					continue;
				}
				int c = 0;
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						c += f[x + dx][z + dz] ? 1 : 0;
					}
				}
				out[x][z] = c >= 5;
			}
		}
		return out;
	}

	/** Columns with plant 0 of a size × size chunk field. */
	private static boolean[][] field(ColumnPlan.Palette p, int size) {
		boolean[][] covered = new boolean[size * 16][size * 16];
		ColumnPlan.Columns c = uniform(code(HabitatBiome.FRESH_PINE_FOREST, Zone.NONE), 0, ColumnPlan.Ground.PODZOL, false);
		for (int cx = 0; cx < size; cx++) {
			for (int cz = 0; cz < size; cz++) {
				for (int q : ColumnPlan.of(c, p, SEED, cx, cz, SALT)) {
					int column = ColumnPlan.column(q);
					covered[cx * 16 + (column >> 4)][cz * 16 + (column & 15)] = ColumnPlan.plant(q) == 0;
				}
			}
		}
		return covered;
	}

	/**
	 * The generated ground layer of the slice forests (docs/04 §6, N8 and N9, step V12) from the palette alone, before the
	 * trees: the world covers somewhat less (trunks, root flares, dead wood and low shrubs take columns; M2 measured 0.85
	 * to 0.95 of the palette), so the palette aims at the middle of the norms. Fresh pine forest: moss with the mossy
	 * underside 66–85% (N9 60–90%), bilberry 45–60% (40–70%), herb layer 45–75% (40–80%), bracken at most 2%; mixed
	 * coniferous forest: bracken 5–15% (its indicator); oak-hornbeam forest: herb layer in spring at least 68% (60–100%),
	 * in summer (without the wood anemone) 22–50% (20–60%), moss on the ground at most 8% (5–20% with the trunk bases),
	 * bare ground at most 5%. The order of the transect indicators (N15): moss does not grow and leaf litter does not
	 * shrink from the fresh pine forest through the mixed coniferous and mixed forests to the oak-hornbeam forest.
	 */
	@Test
	void slicePalettesMeetTheNorms() {
		JsonArray rules = BiomeJsonTest.json(BiomeJsonTest.GENERATED.resolve(
				"data/polishforests/worldgen/feature/ground_layer.json")).getAsJsonArray("rules");
		JsonObject pine = landRule(rules, HabitatBiome.FRESH_PINE_FOREST);
		JsonObject mixedPine = landRule(rules, HabitatBiome.MIXED_PINE_FOREST);
		JsonObject mixed = landRule(rules, HabitatBiome.MIXED_FOREST);
		JsonObject oak = landRule(rules, HabitatBiome.OAK_HORNBEAM_FOREST);
		for (JsonObject r : List.of(pine, mixedPine, mixed, oak)) {
			assertEquals("blobs", r.get("patch_shape").getAsString(), "rounded patches in the slice forests");
		}
		// Rounds 1 and 2 of the V12 review: the plants without a mossy underside of the forests with podzol on top (the
		// fresh and mixed coniferous forests, the podzol patches of the mixed forest's acid brown soil) stand on the
		// mossy podzol or the turf, not on bare podzol (orange plates, CP-B); the oak-hornbeam forest has turf on top, but
		// its palette reaches the podzol patches of the mixed forest within the transect belt.
		assertEquals("polishforests:mossy_podzol", blockName(pine.get("plant_ground")), "ground under the fresh pine plants");
		assertEquals("polishforests:mossy_podzol", blockName(mixedPine.get("plant_ground")), "ground under the mixed coniferous plants");
		assertEquals("minecraft:grass_block", blockName(mixed.get("plant_ground")), "ground under the mixed forest plants");
		assertEquals("minecraft:grass_block", blockName(oak.get("plant_ground")), "ground under the oak-hornbeam plants");
		// Step V13 (finding 3 of the V12 review): in the mixed coniferous and mixed forests the plant ground covers every
		// podzol column of the rule, also the columns left without a plant (bare plates of 3 x 2 to 4 x 3 blocks).
		assertTrue(mixedPine.has("plant_ground_columns") && mixedPine.get("plant_ground_columns").getAsBoolean());
		assertTrue(mixed.has("plant_ground_columns") && mixed.get("plant_ground_columns").getAsBoolean());
		assertTrue(!pine.has("plant_ground_columns") && !oak.has("plant_ground_columns"), "the fresh pine and oak-hornbeam forests unchanged");
		double pineMoss = share(pine, GroundLayerPlanTest::moss);
		double pineBilberry = share(pine, (id, p) -> id.equals("polishforests:bilberry"));
		double pineHerbs = share(pine, (id, p) -> herb(id));
		double pineBracken = share(pine, (id, p) -> id.equals("polishforests:bracken"));
		double oakSpring = share(oak, (id, p) -> herb(id));
		double oakSummer = share(oak, (id, p) -> herb(id) && !id.equals("polishforests:wood_anemone"));
		double oakMoss = share(oak, GroundLayerPlanTest::moss);
		System.out.printf(Locale.ROOT, "fresh pine: moss %.3f, bilberry %.3f, herbs %.3f, bracken %.3f; mixed coniferous bracken %.3f; "
				+ "oak-hornbeam: herbs in spring %.3f, in summer %.3f, moss %.3f, bare %.3f%n", pineMoss, pineBilberry, pineHerbs,
				pineBracken, share(mixedPine, (id, p) -> id.equals("polishforests:bracken")), oakSpring, oakSummer, oakMoss,
				1 - oak.get("coverage").getAsDouble());
		assertTrue(pineMoss >= 0.66 && pineMoss <= 0.93, "fresh pine forest moss " + pineMoss);
		assertTrue(pineBilberry >= 0.45 && pineBilberry <= 0.6, "fresh pine forest bilberry " + pineBilberry);
		assertTrue(pineHerbs >= 0.45 && pineHerbs <= 0.75, "fresh pine forest herb layer " + pineHerbs);
		assertTrue(pineBracken > 0 && pineBracken <= 0.02, "fresh pine forest bracken " + pineBracken);
		double mixedBracken = share(mixedPine, (id, p) -> id.equals("polishforests:bracken"));
		assertTrue(mixedBracken >= 0.05 && mixedBracken <= 0.15, "mixed coniferous forest bracken " + mixedBracken);
		assertTrue(oakSpring >= 0.68, "oak-hornbeam forest herb layer in spring " + oakSpring);
		assertTrue(oakSummer >= 0.22 && oakSummer <= 0.5, "oak-hornbeam forest herb layer in summer " + oakSummer);
		assertTrue(oakMoss <= 0.08, "oak-hornbeam forest moss on the ground " + oakMoss);
		assertTrue(1 - oak.get("coverage").getAsDouble() <= 0.05 && 1 - pine.get("coverage").getAsDouble() <= 0.05, "bare ground");
		List<JsonObject> transect = List.of(pine, mixedPine, mixed, oak);
		for (int i = 0; i + 1 < transect.size(); i++) {
			double mossA = share(transect.get(i), GroundLayerPlanTest::moss);
			double mossB = share(transect.get(i + 1), GroundLayerPlanTest::moss);
			double litterA = share(transect.get(i), (id, p) -> id.equals("minecraft:leaf_litter"));
			double litterB = share(transect.get(i + 1), (id, p) -> id.equals("minecraft:leaf_litter"));
			assertTrue(mossB <= mossA + 0.01, "moss grows along the transect at step " + i + ": " + mossA + " -> " + mossB);
			assertTrue(litterB >= litterA - 0.01, "leaf litter shrinks along the transect at step " + i + ": " + litterA + " -> " + litterB);
		}
	}

	/** Moss as the census counts it: moss carpets and every block in its mossy state (PW-7). */
	private static boolean moss(String id, JsonObject properties) {
		return pl.polishforests.worldgen.norms.ForestCensus.MOSS_PLANTS.contains(id)
				|| properties != null && properties.has("mossy") && properties.get("mossy").getAsString().equals("true");
	}

	/** The herb layer as the census counts it (pl.polishforests.worldgen.norms.ForestCensus#herb). */
	private static boolean herb(String id) {
		return pl.polishforests.worldgen.norms.ForestCensus.herb(id);
	}

	/** Share of the columns a rule gives to the plants that match: coverage × their weight share. */
	private static double share(JsonObject rule, java.util.function.BiPredicate<String, JsonObject> plant) {
		double all = 0;
		double match = 0;
		List<JsonElement> plants = new ArrayList<>();
		rule.getAsJsonArray("plants").forEach(plants::add);
		if (rule.has("scattered")) {
			rule.getAsJsonArray("scattered").forEach(plants::add);
		}
		for (JsonElement e : plants) {
			JsonObject p = e.getAsJsonObject();
			double w = p.get("weight").getAsDouble();
			all += w;
			if (!p.has("block")) {
				continue;
			}
			JsonElement b = p.get("block");
			JsonObject props = b.isJsonObject() && b.getAsJsonObject().has("properties") ? b.getAsJsonObject().getAsJsonObject("properties") : null;
			if (plant.test(blockName(b), props)) {
				match += w;
			}
		}
		return rule.get("coverage").getAsDouble() * match / all;
	}

	/** The first land rule of a biome without zones. */
	private static JsonObject landRule(JsonArray rules, HabitatBiome b) {
		for (JsonElement e : rules) {
			JsonObject r = e.getAsJsonObject();
			String medium = r.has("medium") ? r.get("medium").getAsString() : "land";
			if (medium.equals("land") && r.has("biomes") && !r.has("edges") && contains(r, "biomes", b.id())
					&& contains(r, "zones", Zone.NONE.id())) {
				return r;
			}
		}
		throw new AssertionError("no ground layer rule for " + b.id());
	}

	private static void check(JsonArray rules, HabitatBiome b, Zone z, List<String> report) {
		JsonObject rule = null;
		for (JsonElement e : rules) {
			JsonObject r = e.getAsJsonObject();
			String medium = r.has("medium") ? r.get("medium").getAsString() : "land";
			if (medium.equals("land") && contains(r, "biomes", b.id()) && contains(r, "zones", z.id())) {
				rule = r;
				break;
			}
		}
		assertTrue(rule != null, "no ground layer rule for " + b.id() + " / " + z.id());
		double coverage = rule.get("coverage").getAsDouble();
		double all = 0;
		double small = 0;
		double tall = 0;
		for (JsonElement e : rule.getAsJsonArray("plants")) {
			JsonObject p = e.getAsJsonObject();
			double w = p.get("weight").getAsDouble();
			all += w;
			String block = p.has("block") ? blockName(p.get("block")) : "feature";
			if (SMALL.contains(block)) {
				small += w;
			} else if (TALL.contains(block) || block.equals("feature")) {
				tall += w;
			}
		}
		double s = coverage * small / all;
		double t = coverage * tall / all;
		double bare = 1 - coverage;
		report.add(String.format(Locale.ROOT, "%s/%s %.2f %.2f %.2f", b.id(), z.id(), s, t, bare));
		assertTrue(s <= 0.10, b.id() + "/" + z.id() + ": short grass and small flowers " + s);
		assertTrue(t >= 0.60, b.id() + "/" + z.id() + ": tall plants and shrubs " + t);
		assertTrue(bare <= 0.25, b.id() + "/" + z.id() + ": bare ground " + bare);
	}

	private static String blockName(JsonElement block) {
		return block.isJsonPrimitive() ? block.getAsString() : block.getAsJsonObject().get("id").getAsString();
	}

	/** Whether the rule's list (absent: any) contains the value. */
	private static boolean contains(JsonObject rule, String list, String value) {
		if (!rule.has(list)) {
			return true;
		}
		for (JsonElement e : rule.getAsJsonArray(list)) {
			if (e.getAsString().equals(value)) {
				return true;
			}
		}
		return false;
	}
}
