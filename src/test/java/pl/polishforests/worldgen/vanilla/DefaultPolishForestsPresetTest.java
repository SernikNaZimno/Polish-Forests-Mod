package pl.polishforests.worldgen.vanilla;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * The world preset {@code polishforests:default_polish_forests} (step W1): the vanilla overworld noise generator with the
 * vanilla multi-noise source wrapped by {@code polishforests:vanilla_forests}, the vanilla nether and end, listed in the
 * world type button ({@code #minecraft:normal}) next to the "Poland" presets.
 */
class DefaultPolishForestsPresetTest {
	private static JsonObject json(String path) {
		var stream = DefaultPolishForestsPresetTest.class.getResourceAsStream(path);
		assertNotNull(stream, path);
		try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
			return JsonParser.parseReader(reader).getAsJsonObject();
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}

	@Test
	void theOverworldIsVanillaWithTheForestsReplaced() {
		JsonObject dims = json("/data/polishforests/worldgen/world_preset/default_polish_forests.json")
				.getAsJsonObject("dimensions");
		assertEquals(3, dims.size());
		JsonObject overworld = dims.getAsJsonObject("minecraft:overworld");
		assertEquals("minecraft:overworld", overworld.get("type").getAsString());
		JsonObject generator = overworld.getAsJsonObject("generator");
		assertEquals("minecraft:noise", generator.get("type").getAsString());
		assertEquals("minecraft:overworld", generator.get("settings").getAsString());
		JsonObject source = generator.getAsJsonObject("biome_source");
		assertEquals("polishforests:vanilla_forests", source.get("type").getAsString());
		JsonObject inner = source.getAsJsonObject("source");
		assertEquals("minecraft:multi_noise", inner.get("type").getAsString());
		assertEquals("minecraft:overworld", inner.get("preset").getAsString());
		JsonObject nether = dims.getAsJsonObject("minecraft:the_nether").getAsJsonObject("generator");
		assertEquals("minecraft:nether", nether.get("settings").getAsString());
		assertEquals("minecraft:nether", nether.getAsJsonObject("biome_source").get("preset").getAsString());
		JsonObject end = dims.getAsJsonObject("minecraft:the_end").getAsJsonObject("generator");
		assertEquals("minecraft:end", end.get("settings").getAsString());
		assertEquals("minecraft:the_end", end.getAsJsonObject("biome_source").get("type").getAsString());
	}

	@Test
	void thePresetIsListedInTheWorldTypeButton() {
		JsonObject tag = json("/data/minecraft/tags/worldgen/world_preset/normal.json");
		assertFalse(tag.get("replace").getAsBoolean());
		boolean listed = false;
		for (JsonElement e : tag.getAsJsonArray("values")) {
			listed |= "polishforests:default_polish_forests".equals(e.getAsString());
		}
		assertTrue(listed);
	}
}
