package pl.polishforests.worldgen.feature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.habitat.Species;

/**
 * Step C1 (CP-D.8, docs/04-pionowy-wycinek.md "Projekt C1"): the generated tree stand palette of the spruce-stone pine
 * forest (Pino cembrae-Piceetum) plants the dwarf pine (the creeping shrub of step F6, its own feature) in the canopy gaps
 * next to the stone pine, the spruce, the larch and the rowan; no other palette of the montane spruce forest has it in
 * its gaps.
 */
class StonePinePaletteC1Test {
	private static final Path GENERATED = Path.of("src/main/generated/data/polishforests/worldgen/feature");

	@Test
	void dwarfPineInTheGapsOfTheStonePineForest() throws IOException {
		JsonObject root = JsonParser.parseString(Files.readString(GENERATED.resolve("tree_stand.json"))).getAsJsonObject();
		JsonObject stonePine = null;
		int others = 0;
		for (JsonElement e : root.getAsJsonArray("rules")) {
			JsonObject r = e.getAsJsonObject();
			if (!r.has("biomes") || !r.getAsJsonArray("biomes").toString().contains("\"montane_spruce_forest\"") || !r.has("stand")) {
				continue;
			}
			boolean pino = r.has("associations") && r.getAsJsonArray("associations").toString().contains("pino_cembrae_piceetum");
			String gaps = r.getAsJsonObject("stand").getAsJsonArray("gap_regeneration").toString();
			if (pino) {
				stonePine = r;
			} else {
				others++;
				assertTrue(!gaps.contains(Species.DWARF_MOUNTAIN_PINE.id()), "dwarf pine in the gaps of " + r.get("associations"));
			}
		}
		assertNotNull(stonePine);
		assertTrue(others >= 2, "other montane spruce palettes: " + others);
		int total = 0;
		int dwarf = 0;
		java.util.Set<String> species = new java.util.HashSet<>();
		for (JsonElement e : stonePine.getAsJsonObject("stand").getAsJsonArray("gap_regeneration")) {
			JsonObject p = e.getAsJsonObject();
			int w = p.get("weight").getAsInt();
			total += w;
			species.add(p.get("species").getAsString());
			if (p.get("species").getAsString().equals(Species.DWARF_MOUNTAIN_PINE.id())) {
				dwarf += w;
				assertEquals("polishforests:" + Species.DWARF_MOUNTAIN_PINE.path(), p.get("tree").getAsString());
			}
		}
		assertTrue(species.containsAll(java.util.List.of("stone_pine", "spruce", "larch", "rowan", "dwarf_mountain_pine")),
				species.toString());
		// About a quarter of the young plants of the gaps (the rest the trees of the next generation).
		assertTrue(dwarf >= 0.15 * total && dwarf <= 0.35 * total, "dwarf pine weight " + dwarf + " of " + total);
	}
}
