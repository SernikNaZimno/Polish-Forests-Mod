package pl.polishforests.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The recipes of every wood product of every wood set (step FA7 of docs/04-pionowy-wycinek.md; datagen in
 * {@code src/main/generated}): each one is made of the species' own wood and of nothing of another wood — the planks
 * products (stairs, slab, fence, fence gate, pressure plate, button of step P2; door, trapdoor, sign of FA7) of the
 * species' planks, the hanging sign and the shelf of its stripped log as vanilla's — with vanilla's shape, count and
 * group, and the result is the product of the same species.
 */
class WoodRecipesTest {
	private static final Path RECIPES = Path.of("src/main/generated/data/polishforests/recipe");

	/** Product, material ({@code planks} or {@code stripped_log}), pattern rows, count, group, extra ingredients. */
	private record Expected(String product, String material, int rows, int count, String group, List<String> extra) {
	}

	private static final List<Expected> PRODUCTS = List.of(
			new Expected("stairs", "planks", 3, 4, "wooden_stairs", List.of()),
			new Expected("slab", "planks", 1, 6, "wooden_slab", List.of()),
			new Expected("fence", "planks", 2, 3, "wooden_fence", List.of("minecraft:stick")),
			new Expected("fence_gate", "planks", 2, 1, "wooden_fence_gate", List.of("minecraft:stick")),
			new Expected("pressure_plate", "planks", 1, 1, "wooden_pressure_plate", List.of()),
			new Expected("door", "planks", 3, 3, "wooden_door", List.of()),
			new Expected("trapdoor", "planks", 2, 2, "wooden_trapdoor", List.of()),
			new Expected("sign", "planks", 3, 3, "wooden_sign", List.of("minecraft:stick")),
			new Expected("hanging_sign", "stripped_log", 3, 6, "wooden_hanging_sign", List.of("minecraft:iron_chain")),
			new Expected("shelf", "stripped_log", 3, 6, "shelf", List.of()));

	@Test
	void everyProductIsMadeOfTheWoodOfItsSpecies() throws IOException {
		List<String> problems = new ArrayList<>();
		for (WoodSet set : WoodSet.values()) {
			String p = set.prefix();
			for (Expected e : PRODUCTS) {
				String name = p + "_" + e.product();
				JsonObject r = json(RECIPES.resolve(name + ".json"));
				String material = e.material().equals("planks") ? "polishforests:" + set.planksId() : "polishforests:stripped_" + p + "_log";
				if (!r.getAsJsonObject("result").get("id").getAsString().equals("polishforests:" + name)) {
					problems.add(name + ": result " + r.getAsJsonObject("result"));
				}
				int count = r.getAsJsonObject("result").has("count") ? r.getAsJsonObject("result").get("count").getAsInt() : 1;
				if (count != e.count()) {
					problems.add(name + ": count " + count + " instead of " + e.count());
				}
				if (!r.get("group").getAsString().equals(e.group())) {
					problems.add(name + ": group " + r.get("group").getAsString());
				}
				if (r.getAsJsonArray("pattern").size() != e.rows()) {
					problems.add(name + ": " + r.getAsJsonArray("pattern").size() + " pattern rows instead of " + e.rows());
				}
				List<String> keys = new ArrayList<>();
				for (Map.Entry<String, JsonElement> k : r.getAsJsonObject("key").entrySet()) {
					keys.add(k.getValue().getAsString());
				}
				List<String> expected = new ArrayList<>(e.extra());
				expected.addFirst(material);
				if (!keys.containsAll(expected) || keys.size() != expected.size()) {
					problems.add(name + ": ingredients " + keys + " instead of " + expected);
				}
				for (String k : keys) {
					if (k.startsWith("polishforests:") && !k.contains(p)) {
						problems.add(name + ": made of another wood " + k);
					}
				}
			}
			// The button: one plank, shapeless.
			JsonObject button = json(RECIPES.resolve(p + "_button.json"));
			assertEquals("polishforests:" + set.planksId(), button.getAsJsonArray("ingredients").get(0).getAsString(), p);
		}
		assertTrue(problems.isEmpty(), problems.size() + " problems: " + String.join("; ", problems));
	}

	private static JsonObject json(Path p) throws IOException {
		return JsonParser.parseString(Files.readString(p)).getAsJsonObject();
	}
}
