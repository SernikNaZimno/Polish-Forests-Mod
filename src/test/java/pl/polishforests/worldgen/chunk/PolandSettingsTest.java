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
				"version", "cover_in_blocks", "loose_cover"}) {
			assertTrue(json.has(field), "missing " + field + " in " + json);
		}
		assertEquals(PolandSettings.CURRENT_VERSION, json.get("version").getAsInt());
		assertFalse(json.get("agriculture").getAsBoolean());
		assertTrue(json.get("cover_in_blocks").getAsBoolean());
		// Step K1: new worlds get the thin loose cover in both scales, and the setting is saved and read back.
		assertEquals("thin", json.get("loose_cover").getAsString());
		assertEquals(LooseCover.THIN, PolandSettings.DEFAULT.withScale(PolandScale.GAMEPLAY).looseCover());
		PolandSettings realistic = PolandSettings.DEFAULT.withLooseCover(LooseCover.REALISTIC);
		assertEquals("realistic", encode(realistic).get("loose_cover").getAsString());
		assertEquals(realistic, decode(encode(realistic).toString()));
		assertEquals(PolandSettings.DEFAULT, decode(json.toString()));
		PolandSettings gameplay = PolandSettings.DEFAULT.withScale(PolandScale.GAMEPLAY);
		assertEquals(gameplay, decode(encode(gameplay).toString()));
		// Step S8: the "present-day Poland" switch is saved and read back in both scales.
		for (PolandSettings today : new PolandSettings[] {PolandSettings.DEFAULT.withAgriculture(true), gameplay.withAgriculture(true)}) {
			JsonObject saved = encode(today);
			assertTrue(saved.get("agriculture").getAsBoolean());
			PolandSettings read = decode(saved.toString());
			assertEquals(today, read);
			assertEquals(HabitatClassifier.Mode.PRESENT_DAY, read.mode());
		}
		assertEquals(PolandSettings.DEFAULT, PolandSettings.DEFAULT.withAgriculture(true).withAgriculture(false));
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
		// A world from before S6 has no "cover_in_blocks" and keeps the old cover (meters compared with blocks).
		assertFalse(v2.coverInBlocks());
		assertFalse(m1.coverInBlocks());
		assertTrue(decode("{\"version\": 2, \"cover_in_blocks\": true}").coverInBlocks());
		// A world from before K1 has no "loose_cover" and keeps the realistic cover, so its new chunks match the old ones.
		assertEquals(LooseCover.REALISTIC, v2.looseCover());
		assertEquals(LooseCover.REALISTIC, m1.looseCover());
		PolandSettings beforeK1 = decode("{\"scale\": \"gameplay\", \"region_scale\": 1.0, \"agriculture\": false, "
				+ "\"managed_forest_share\": 0.85, \"alien_species\": true, \"version\": 2, \"cover_in_blocks\": true}");
		assertEquals(LooseCover.REALISTIC, beforeK1.looseCover());
		assertEquals(PolandSettings.DEFAULT.withScale(PolandScale.GAMEPLAY).withLooseCover(LooseCover.REALISTIC), beforeK1);
		assertEquals(LooseCover.THIN, decode("{\"version\": 2, \"loose_cover\": \"thin\"}").looseCover());
	}

	/** Step K1: both presets create worlds with the thin loose cover. */
	@Test
	void presetsUseTheThinCover() throws java.io.IOException {
		for (String preset : new String[] {"poland", "poland_gameplay"}) {
			String path = "/data/polishforests/worldgen/world_preset/" + preset + ".json";
			try (var in = PolandSettingsTest.class.getResourceAsStream(path)) {
				assertTrue(in != null, "missing " + path);
				JsonObject settings = JsonParser.parseString(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8))
						.getAsJsonObject().getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld")
						.getAsJsonObject("generator").getAsJsonObject("settings");
				PolandSettings read = decode(settings.toString());
				assertEquals(LooseCover.THIN, read.looseCover(), preset);
				assertTrue(read.coverInBlocks(), preset);
			}
		}
	}

	@Test
	void generatorWritesTheSettingsAndReadsMissingOnesAsM1() {
		var field = PolandSettings.GENERATOR_FIELD.codec();
		JsonObject json = field.encodeStart(JsonOps.INSTANCE, PolandSettings.DEFAULT).getOrThrow().getAsJsonObject();
		assertTrue(json.has("settings"), "the default settings are left out: " + json);
		assertEquals(8, json.getAsJsonObject("settings").size());
		assertEquals(PolandSettings.DEFAULT, field.parse(JsonOps.INSTANCE, json).getOrThrow());
		assertEquals(PolandSettings.LEGACY_M1, field.parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow());
	}
}
