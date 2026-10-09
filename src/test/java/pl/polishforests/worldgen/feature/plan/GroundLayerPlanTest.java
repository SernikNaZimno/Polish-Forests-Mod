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
		// The dwarf shrub share of the pine forests stays (the former sweet berry weight went to the bush).
		for (HabitatBiome b : List.of(HabitatBiome.FRESH_PINE_FOREST, HabitatBiome.BOG_WOODLAND, HabitatBiome.MOIST_PINE_FOREST)) {
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
