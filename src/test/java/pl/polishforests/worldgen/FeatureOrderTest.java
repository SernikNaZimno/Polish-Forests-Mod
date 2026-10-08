package pl.polishforests.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.feature.BiomeDecoration;
import pl.polishforests.worldgen.habitat.HabitatBiome;

/**
 * Decoration step lists of the biomes from datagen (Z5, docs/03-m2-biomy.md §8.1): every biome has the same 11 lists,
 * except the bone meal carrier, which is always the last element of step 9; the lists match {@link BiomeDecoration};
 * every referenced placed feature and feature exists (ours in {@code src/main/generated}, vanilla ones in the game jar);
 * the removed vanilla features are absent and our placed features are never inline. Identical lists in the same order
 * make a "Feature order cycle" on our side impossible.
 */
class FeatureOrderTest {
	/** Vanilla features left out of the lists on purpose (§8.1). */
	private static final Set<String> REMOVED = Set.of("minecraft:lake_lava_surface", "minecraft:lake_lava_underground",
			"minecraft:spring_water", "minecraft:spring_lava", "minecraft:glow_lichen", "minecraft:underwater_magma");

	private static List<List<String>> steps(HabitatBiome b) {
		JsonArray features = BiomeJsonTest.json(BiomeJsonTest.biomeFile(b)).getAsJsonArray("features");
		List<List<String>> steps = new ArrayList<>();
		for (JsonElement step : features) {
			List<String> ids = new ArrayList<>();
			for (JsonElement e : step.getAsJsonArray()) {
				assertTrue(e.isJsonPrimitive(), b.id() + ": inline placed feature");
				ids.add(e.getAsString());
			}
			steps.add(ids);
		}
		return steps;
	}

	@Test
	void listsAreIdenticalExceptTheBoneMealCarrier() {
		List<List<String>> reference = null;
		for (HabitatBiome b : HabitatBiome.values()) {
			List<List<String>> steps = steps(b);
			assertEquals(BiomeDecoration.STEPS, steps.size(), b.id());
			assertEquals(BiomeDecoration.steps(b), steps, b.id() + ": lists differ from BiomeDecoration");
			List<String> vegetation = steps.get(BiomeDecoration.VEGETATION_STEP);
			String carrier = vegetation.getLast();
			assertEquals(BiomeDecoration.boneMealCarrier(b), carrier, b.id());
			assertTrue(carrier.startsWith("polishforests:bone_meal/"), b.id() + ": the last feature of step 9 is not a carrier");
			for (int i = 0; i < steps.size(); i++) {
				for (int j = 0; j < steps.get(i).size(); j++) {
					String id = steps.get(i).get(j);
					boolean isCarrier = id.startsWith("polishforests:bone_meal/");
					assertEquals(i == BiomeDecoration.VEGETATION_STEP && j == steps.get(i).size() - 1, isCarrier,
							b.id() + ": carrier " + id + " not at the end of step 9");
					assertFalse(REMOVED.contains(id), b.id() + ": removed feature " + id);
				}
			}
			List<List<String>> withoutCarrier = withoutCarrier(steps);
			if (reference == null) {
				reference = withoutCarrier;
			} else {
				assertEquals(reference, withoutCarrier, b.id() + ": the lists differ from the other biomes");
			}
		}
		List<String> dispatchers = reference.get(BiomeDecoration.VEGETATION_STEP);
		assertEquals(List.of("polishforests:tree_stand", "polishforests:deadwood", "polishforests:understory",
				"polishforests:waterside_zones", "polishforests:ground_layer", "polishforests:aquatic_plants"), dispatchers);
	}

	@Test
	void everyIdExists() {
		for (List<String> step : BiomeDecoration.steps(HabitatBiome.FEN)) {
			for (String id : step) {
				assertPlacedFeatureExists(id);
			}
		}
		for (HabitatBiome b : HabitatBiome.values()) {
			assertPlacedFeatureExists(BiomeDecoration.boneMealCarrier(b));
		}
		// The tree palette names only existing trees, and every tree feature exists.
		JsonObject treeStand = BiomeJsonTest.json(BiomeJsonTest.GENERATED.resolve(
				"data/polishforests/worldgen/feature/tree_stand.json"));
		assertEquals("polishforests:tree_stand", treeStand.get("type").getAsString());
		int rules = 0;
		for (JsonElement rule : treeStand.getAsJsonArray("rules")) {
			for (JsonElement tree : rule.getAsJsonObject().getAsJsonArray("trees")) {
				assertPlacedFeatureExists(tree.getAsJsonObject().get("tree").getAsString());
			}
			rules++;
		}
		assertTrue(rules >= 17, "tree palette rules: " + rules);
	}

	/** A placed feature exists, and so does the feature it places. */
	private static void assertPlacedFeatureExists(String id) {
		JsonObject placed = load("placed_feature", id);
		assertNotNull(placed, "missing placed feature " + id);
		String feature = placed.get("feature").getAsString();
		assertNotNull(load("feature", feature), "missing feature " + feature + " of " + id);
	}

	private static JsonObject load(String kind, String id) {
		String namespace = id.substring(0, id.indexOf(':'));
		String path = id.substring(id.indexOf(':') + 1);
		if (namespace.equals("polishforests")) {
			var file = BiomeJsonTest.GENERATED.resolve("data/polishforests/worldgen/" + kind + "/" + path + ".json");
			return Files.exists(file) ? BiomeJsonTest.json(file) : null;
		}
		var resource = FeatureOrderTest.class.getResourceAsStream("/data/" + namespace + "/worldgen/" + kind + "/" + path + ".json");
		if (resource == null) {
			return null;
		}
		try (var reader = new java.io.InputStreamReader(resource, java.nio.charset.StandardCharsets.UTF_8)) {
			return com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
		} catch (java.io.IOException e) {
			throw new AssertionError(e);
		}
	}

	private static List<List<String>> withoutCarrier(List<List<String>> steps) {
		List<List<String>> out = new ArrayList<>();
		for (int i = 0; i < steps.size(); i++) {
			List<String> step = new ArrayList<>(steps.get(i));
			if (i == BiomeDecoration.VEGETATION_STEP) {
				step.removeLast();
			}
			out.add(step);
		}
		return out;
	}
}
