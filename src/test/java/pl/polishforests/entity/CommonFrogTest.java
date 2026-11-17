package pl.polishforests.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import pl.polishforests.entity.spawn.FrogSpawner;
import pl.polishforests.season.SubSeason;
import pl.polishforests.worldgen.habitat.HabitatBiome;

/**
 * Step FA4 (docs/04-pionowy-wycinek.md "Projekt FA4", G.62-f): the common frog variant, its biome tag and the habitats of
 * the frog spawner. The frogs in the world are the fauna game test's census ({@code -Psites=sound}).
 */
class CommonFrogTest {
	private static final Path VARIANT = Path.of("src/main/resources/data/polishforests/frog_variant/common_frog.json");
	private static final Path TAG = Path.of("src/main/generated/data/polishforests/tags/worldgen/biome/spawns_common_frog.json");
	private static final Path TEXTURE = Path.of("src/main/resources/assets/polishforests/textures/entity/frog/common_frog.png");

	@Test
	void variantOutranksTheVanillaOnes() throws IOException {
		JsonObject v = JsonParser.parseString(Files.readString(VARIANT)).getAsJsonObject();
		assertEquals("polishforests:entity/frog/common_frog", v.get("asset_id").getAsString());
		assertTrue(Files.isRegularFile(TEXTURE), "texture of tools/fauna/frog.py");
		JsonArray conditions = v.getAsJsonArray("spawn_conditions");
		assertEquals(1, conditions.size());
		JsonObject c = conditions.get(0).getAsJsonObject();
		// Vanilla: temperate 0 (no condition), warm and cold 1 (their biome tags); ours wins in its biomes.
		assertTrue(c.get("priority").getAsInt() > 1);
		JsonObject cond = c.getAsJsonObject("condition");
		assertEquals("minecraft:biome", cond.get("type").getAsString());
		assertEquals("#" + FrogSpawner.SPAWNS_COMMON_FROG.location(), cond.get("biomes").getAsString());
	}

	@Test
	void tagHoldsTheForestsAndTheWetZones() throws IOException {
		Set<String> values = new HashSet<>();
		for (JsonElement e : JsonParser.parseString(Files.readString(TAG)).getAsJsonObject().getAsJsonArray("values")) {
			values.add(e.getAsString());
		}
		for (HabitatBiome b : HabitatBiome.values()) {
			if (b.isForest()) {
				assertTrue(values.contains("polishforests:" + b.id()), b.id() + " in the tag");
			}
		}
		for (String wet : new String[] {"alder_carr", "oak_hornbeam_forest", "fen", "reedbed", "raised_bog", "river", "lake"}) {
			assertTrue(values.contains("polishforests:" + wet), wet);
		}
		assertFalse(values.contains("polishforests:heath"));
		assertFalse(values.contains("polishforests:arable_land"));
	}

	@Test
	void spawnerHabitats() {
		assertEquals(1.0F, FrogSpawner.habitatWeight(HabitatBiome.ALDER_CARR));
		assertTrue(FrogSpawner.habitatWeight(HabitatBiome.OAK_HORNBEAM_FOREST) > 0, "grąd by its puddles");
		for (HabitatBiome b : new HabitatBiome[] {HabitatBiome.ASH_ALDER_FOREST, HabitatBiome.WILLOW_POPLAR_FOREST, HabitatBiome.ELM_ASH_FOREST}) {
			assertTrue(FrogSpawner.habitatWeight(b) >= 0.8F, b + ": a floodplain forest");
		}
		for (HabitatBiome b : HabitatBiome.values()) {
			if (!b.isForest()) {
				assertEquals(0F, FrogSpawner.habitatWeight(b), b + ": open land keeps the frogs of its biome list (KFA-4)");
			}
		}
		assertEquals(0F, FrogSpawner.habitatWeight(HabitatBiome.DRY_PINE_FOREST));
		assertEquals(0F, FrogSpawner.habitatWeight(null));
		// Out of hibernation from March to October.
		assertTrue(FrogSpawner.active(SubSeason.EARLY_SPRING));
		assertTrue(FrogSpawner.active(SubSeason.MID_AUTUMN));
		assertFalse(FrogSpawner.active(SubSeason.LATE_AUTUMN));
		assertFalse(FrogSpawner.active(SubSeason.MID_WINTER));
	}
}
