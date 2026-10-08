package pl.polishforests.worldgen.chunk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.habitat.HabitatClassifier;

/**
 * Codec of the world settings: every field is written, also when it equals the default (a later change of a default
 * must not change saved worlds), and missing fields are read as in the version that could leave them out.
 */
class PolandSettingsTest {
	private static JsonObject encode(PolandSettings s) {
		return PolandSettings.CODEC.encodeStart(JsonOps.INSTANCE, s).getOrThrow().getAsJsonObject();
	}

	private static PolandSettings decode(String json) {
		return PolandSettings.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
	}

	@Test
	void defaultIsWrittenInFull() {
		JsonObject json = encode(PolandSettings.DEFAULT);
		for (String field : new String[] {"scale", "region_scale", "agriculture", "managed_forest_share", "alien_species",
				"version"}) {
			assertTrue(json.has(field), "missing " + field + " in " + json);
		}
		assertEquals(PolandSettings.CURRENT_VERSION, json.get("version").getAsInt());
		assertFalse(json.get("agriculture").getAsBoolean());
		assertEquals(PolandSettings.DEFAULT, decode(json.toString()));
		PolandSettings gameplay = PolandSettings.DEFAULT.withScale(PolandScale.GAMEPLAY);
		assertEquals(gameplay, decode(encode(gameplay).toString()));
	}

	@Test
	void missingFieldsAreReadAsInTheirVersion() {
		// An M1 world: no version, and a missing "agriculture" meant the present-day mode.
		PolandSettings m1 = decode("{}");
		assertEquals(PolandSettings.LEGACY_VERSION, m1.version());
		assertTrue(m1.agriculture());
		assertEquals(HabitatClassifier.Mode.PRESENT_DAY, m1.mode());
		assertEquals(PolandSettings.LEGACY_M1, m1);
		assertEquals(PolandScale.GAMEPLAY, decode("{\"scale\": \"gameplay\"}").scale());
		assertTrue(decode("{\"scale\": \"gameplay\"}").agriculture());
		// From version 2 on a missing "agriculture" means natural vegetation (decision M2-B).
		PolandSettings v2 = decode("{\"scale\": \"gameplay\", \"version\": 2}");
		assertFalse(v2.agriculture());
		assertEquals(HabitatClassifier.Mode.NATURAL, v2.mode());
		assertEquals(2, v2.version());
	}

	@Test
	void generatorWritesTheSettingsAndReadsMissingOnesAsM1() {
		var field = PolandSettings.GENERATOR_FIELD.codec();
		JsonObject json = field.encodeStart(JsonOps.INSTANCE, PolandSettings.DEFAULT).getOrThrow().getAsJsonObject();
		assertTrue(json.has("settings"), "the default settings are left out: " + json);
		assertEquals(6, json.getAsJsonObject("settings").size());
		assertEquals(PolandSettings.DEFAULT, field.parse(JsonOps.INSTANCE, json).getOrThrow());
		assertEquals(PolandSettings.LEGACY_M1, field.parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow());
	}
}
