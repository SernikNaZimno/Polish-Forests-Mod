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
 * {@code deadwood_substitute/} fallen trees; step F6: the thin rowan of the slice, {@code tree_substitute/rowan_thin},
 * the last placeholder of the slice under that prefix, is the own rowan of the planner), and every forest of
 * {@link ForestBlend#FORESTS} has such a rule.
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
				String text = r.toString();
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

	/**
	 * Review of F6, round 1: the first understory rule of a column decides it, so the dense dwarf pine of the dwarf pine
	 * scrub (coverage 0.05) comes before the sparse patches of the timberline zone (0.015), which would otherwise thin out
	 * the scrub's columns of that zone (its lower edge).
	 */
	@Test
	void dwarfPineScrubRuleComesBeforeTheTimberlineRule() {
		int scrub = -1;
		int timberline = -1;
		int n = 0;
		for (JsonElement e : EcotoneSharpnessTest.rules("understory")) {
			JsonObject r = e.getAsJsonObject();
			if (!r.has("edges")) {
				if (scrub < 0 && r.has("biomes") && r.getAsJsonArray("biomes").toString().contains("\"dwarf_pine_scrub\"")) {
					scrub = n;
					assertEquals(0.05, r.get("coverage").getAsDouble(), 1e-6, "coverage of the first dwarf pine scrub rule");
				}
				if (timberline < 0 && r.has("zones") && r.getAsJsonArray("zones").toString().contains("\"timberline\"")) {
					timberline = n;
				}
			}
			n++;
		}
		assertTrue(scrub >= 0 && timberline >= 0, "both rules exist: " + scrub + ", " + timberline);
		assertTrue(scrub < timberline, "dwarf pine scrub rule " + scrub + " before the timberline rule " + timberline);
	}
}
