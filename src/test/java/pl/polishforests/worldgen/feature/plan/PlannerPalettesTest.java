package pl.polishforests.worldgen.feature.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.habitat.HabitatBiome;

/**
 * The palettes of the forests of the tree planner (step F1 of docs/04-pionowy-wycinek.md, "Kroki F"): a biome with a
 * layered rule of the tree planner in the generated tree stand palette has no placeholder of M2 in any of the generated
 * palettes of the tree stand, the dead wood, the understory and the ground layer ({@code tree_substitute/} trees,
 * {@code deadwood_substitute/} fallen trees; the thin small trees of the slice, {@code ModTrees.SLICE}, such as
 * {@code tree_substitute/rowan_thin}, are the slice's own), and every forest of {@link ForestBlend#FORESTS} has such a
 * rule.
 */
class PlannerPalettesTest {
	private static final List<String> PALETTES = List.of("tree_stand", "deadwood", "understory", "ground_layer");

	private static Set<HabitatBiome> plannerBiomes() {
		Set<HabitatBiome> out = EnumSet.noneOf(HabitatBiome.class);
		for (JsonElement e : EcotoneSharpnessTest.rules("tree_stand")) {
			JsonObject r = e.getAsJsonObject();
			if (r.has("stand") && r.has("biomes")) {
				r.getAsJsonArray("biomes").forEach(b -> out.add(HabitatBiome.byId(b.getAsString())));
			}
		}
		return out;
	}

	@Test
	void forestsOfThePlannerHaveNoPlaceholdersOfM2() {
		Set<HabitatBiome> planner = plannerBiomes();
		assertEquals(EnumSet.copyOf(List.of(ForestBlend.FORESTS)), planner, "the forests of the blend are the forests of the planner");
		List<String> found = new ArrayList<>();
		for (String palette : PALETTES) {
			int n = 0;
			for (JsonElement e : EcotoneSharpnessTest.rules(palette)) {
				n++;
				JsonObject r = e.getAsJsonObject();
				if (!r.has("biomes")) {
					continue;
				}
				boolean named = false;
				for (JsonElement b : r.getAsJsonArray("biomes")) {
					named |= planner.contains(HabitatBiome.byId(b.getAsString()));
				}
				String text = r.toString().replaceAll("tree_substitute/[a-z_]+_thin", "");
				if (named && (text.contains("tree_substitute/") || text.contains("deadwood_substitute/"))) {
					found.add(palette + " rule " + n + " " + r.getAsJsonArray("biomes"));
				}
			}
		}
		assertEquals(List.of(), found, "rules of the forests of the planner with placeholders of M2");
	}

	/** Every forest of the planner has a dead wood rule of the dead wood planner (dead trees and stumps). */
	@Test
	void forestsOfThePlannerHaveTheDeadWoodOfThePlanner() {
		for (HabitatBiome b : ForestBlend.FORESTS) {
			boolean deadwood = false;
			boolean stumps = false;
			for (JsonElement e : EcotoneSharpnessTest.rules("deadwood")) {
				JsonObject r = e.getAsJsonObject();
				if (r.has("biomes") && r.getAsJsonArray("biomes").toString().contains("\"" + b.id() + "\"")) {
					deadwood |= r.toString().contains("polishforests:deadwood/");
					stumps |= r.toString().contains("polishforests:stump/");
				}
			}
			assertTrue(deadwood && stumps, b.id());
		}
	}
}
