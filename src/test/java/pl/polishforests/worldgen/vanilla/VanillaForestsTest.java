package pl.polishforests.worldgen.vanilla;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.feature.plan.SpeciesRamp;
import pl.polishforests.worldgen.habitat.ForestSiteType;
import pl.polishforests.worldgen.habitat.Habitat;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.habitat.LandCover;
import pl.polishforests.worldgen.habitat.Species;
import pl.polishforests.worldgen.habitat.Zone;

/**
 * The table of the world type "Default + Polish Forests" (step W1, {@link VanillaForests}, docs/00 G.40): every vanilla
 * forest of the game jar is replaced or kept on purpose, every replacement is a forest of the mod with a canonical code,
 * the species ramp is full for its flags, and the foreign columns carry distinct non-forest codes.
 */
class VanillaForestsTest {
	/**
	 * The vanilla paths of a biome tag: the {@code minecraft} values of every copy on the class path (the game jar's and
	 * the mod's additions, which also name the habitat biomes).
	 */
	static Set<String> tag(String name) {
		String path = "data/minecraft/tags/worldgen/biome/" + name + ".json";
		Set<String> out = new LinkedHashSet<>();
		try {
			var urls = VanillaForestsTest.class.getClassLoader().getResources(path);
			while (urls.hasMoreElements()) {
				try (var reader = new InputStreamReader(urls.nextElement().openStream(), StandardCharsets.UTF_8)) {
					for (JsonElement e : JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("values")) {
						String id = e.isJsonObject() ? e.getAsJsonObject().get("id").getAsString() : e.getAsString();
						if (id.startsWith("minecraft:")) {
							out.add(id.substring("minecraft:".length()));
						}
					}
				}
			}
		} catch (IOException e) {
			throw new AssertionError(e);
		}
		assertFalse(out.isEmpty(), "vanilla tag " + name);
		return out;
	}

	@Test
	void everyVanillaForestIsReplacedOrKeptOnPurpose() {
		Set<String> overworld = tag("is_overworld");
		Set<String> forests = new LinkedHashSet<>(tag("is_forest"));
		forests.addAll(tag("is_taiga"));
		forests.addAll(tag("is_jungle"));
		forests.addAll(Set.of("swamp", "mangrove_swamp", "windswept_forest", "cherry_grove"));
		for (String f : forests) {
			assertTrue(VanillaForests.REPLACEMENTS.containsKey(f) ^ VanillaForests.KEPT.contains(f), f + ": not in the table");
		}
		Set<String> table = new HashSet<>(VanillaForests.REPLACEMENTS.keySet());
		table.addAll(VanillaForests.KEPT);
		assertEquals(VanillaForests.REPLACEMENTS.size() + VanillaForests.KEPT.size(), table.size(), "replaced and kept overlap");
		for (String b : table) {
			assertTrue(overworld.contains(b), b + ": not a vanilla overworld biome");
			assertNotNull(VanillaForestsTest.class.getResource("/data/minecraft/worldgen/biome/" + b + ".json"), b);
		}
	}

	@Test
	void theReplacementsAreForestsWithCanonicalCodes() {
		for (HabitatBiome b : new LinkedHashSet<>(VanillaForests.REPLACEMENTS.values())) {
			assertTrue(VanillaForests.present(b), b.id());
			int code = VanillaForests.code(b);
			assertEquals(b, Habitat.biome(code), b.id());
			assertEquals(Zone.NONE, Habitat.zone(code), b.id());
			assertEquals(LandCover.FOREST, Habitat.cover(code), b.id());
			assertNotEquals(ForestSiteType.NONE, Habitat.siteType(code), b.id());
			assertEquals(VanillaForests.flags(b), Habitat.flags(code), b.id());
			assertTrue(VanillaForestWorld.forest(code), b.id());
		}
		// The four forests of the slice (full planner trees) all grow in that world.
		for (HabitatBiome b : new HabitatBiome[] {HabitatBiome.FRESH_PINE_FOREST, HabitatBiome.MIXED_PINE_FOREST,
				HabitatBiome.MIXED_FOREST, HabitatBiome.OAK_HORNBEAM_FOREST}) {
			assertTrue(VanillaForests.present(b), b.id());
		}
		assertFalse(VanillaForests.present(HabitatBiome.HAY_MEADOW));
		assertEquals(HabitatBiome.OAK_HORNBEAM_FOREST, VanillaForests.replacement("forest"));
		assertEquals(HabitatBiome.FRESH_PINE_FOREST, VanillaForests.replacement("old_growth_pine_taiga"));
		assertEquals(HabitatBiome.ALDER_CARR, VanillaForests.replacement("swamp"));
		assertNull(VanillaForests.replacement("plains"));
		assertNull(VanillaForests.replacement("dappled_forest"));
	}

	@Test
	void theRangeRampIsFullForEveryFlag() {
		for (double shift : new double[] {-1, 0, 1}) {
			float[] f = SpeciesRamp.factors(VanillaForests.OCEANICITY, VanillaForests.MOUNTAIN_INFLUENCE, shift);
			for (int bit = 0; bit < 4; bit++) {
				assertEquals(1F, f[bit], 1e-6F, "flag bit " + bit + " at shift " + shift);
			}
		}
		// No glacial erratics (they need P at most 0.3).
		assertTrue(VanillaForests.MOUNTAIN_INFLUENCE > 0.3F);
		assertEquals(Species.FLAG_SPRUCE | Species.FLAG_FIR, VanillaForests.flags(HabitatBiome.MONTANE_SPRUCE_FOREST));
	}

	@Test
	void foreignColumnsHaveDistinctNonForestCodes() {
		Set<Integer> codes = new HashSet<>();
		for (VanillaForests.Foreign kind : VanillaForests.Foreign.values()) {
			int code = VanillaForests.foreignCode(kind);
			assertEquals(kind.biome(), Habitat.biome(code), kind.name());
			assertNotEquals(LandCover.FOREST, Habitat.cover(code), kind.name());
			assertFalse(VanillaForestWorld.forest(code), kind.name());
			assertTrue(codes.add(code), kind.name() + ": code twice");
		}
		for (HabitatBiome b : VanillaForests.REPLACEMENTS.values()) {
			assertFalse(codes.contains(VanillaForests.code(b)), b.id());
		}
		assertEquals(VanillaForests.Foreign.RIVER, VanillaForests.foreign("frozen_river", false, false, false));
		assertEquals(VanillaForests.Foreign.SEA, VanillaForests.foreign("deep_lukewarm_ocean", false, false, false));
		assertEquals(VanillaForests.Foreign.BEACH, VanillaForests.foreign("snowy_beach", false, false, false));
		assertEquals(VanillaForests.Foreign.BEACH, VanillaForests.foreign("stony_shore", false, false, false));
		assertEquals(VanillaForests.Foreign.LAND, VanillaForests.foreign("plains", false, false, false));
		assertEquals(VanillaForests.Foreign.SEA, VanillaForests.foreign("custom", false, true, false));
		// Every vanilla river, ocean and beach by its path alone.
		for (String b : tag("is_river")) {
			assertEquals(VanillaForests.Foreign.RIVER, VanillaForests.foreign(b, false, false, false), b);
		}
		for (String b : tag("is_ocean")) {
			assertEquals(VanillaForests.Foreign.SEA, VanillaForests.foreign(b, false, false, false), b);
		}
		for (String b : tag("is_beach")) {
			assertEquals(VanillaForests.Foreign.BEACH, VanillaForests.foreign(b, false, false, false), b);
		}
	}

	@Test
	void steepColumnsDifferByTheThresholdFromANeighbor() {
		short[] tops = new short[256];
		Arrays.fill(tops, (short) 80);
		int i = 5 << 4 | 5;
		int east = 6 << 4 | 5;
		assertFalse(VanillaForestWorld.steep(tops, i));
		tops[east] = (short) (80 + VanillaForestWorld.STEEP - 1);
		assertFalse(VanillaForestWorld.steep(tops, i));
		tops[east] = (short) (80 + VanillaForestWorld.STEEP);
		assertTrue(VanillaForestWorld.steep(tops, i));
		assertTrue(VanillaForestWorld.steep(tops, east));
		assertFalse(VanillaForestWorld.steep(tops, 0));
	}
}
