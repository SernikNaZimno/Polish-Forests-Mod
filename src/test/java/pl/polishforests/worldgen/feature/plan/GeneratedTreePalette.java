package pl.polishforests.worldgen.feature.plan;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import pl.polishforests.worldgen.habitat.Species;

/**
 * The tree palette of the generated data ({@code tree_stand.json}) as the tree stand resolves it for planning
 * ({@link TreeStandPlan.Palette}, as {@code TreeStandFeature} builds it): tree indices by placed feature id in the order
 * of first use, with the layered stands of the rules of the vertical slice (step V9).
 */
final class GeneratedTreePalette {
	final TreeStandPlan.Palette palette;
	/** Placed feature id of each tree index. */
	final List<String> trees = new ArrayList<>();
	/** Species of each tree index. */
	final List<Species> species = new ArrayList<>();

	private GeneratedTreePalette() {
		List<TreeStandPlan.Rule> rules = new ArrayList<>();
		for (JsonElement e : EcotoneSharpnessTest.rules("tree_stand")) {
			JsonObject r = e.getAsJsonObject();
			HabitatMatch match = EcotoneSharpnessTest.match(r);
			float perChunk = r.get("trees_per_chunk").getAsFloat();
			TreeStandPlan.Rule rule = rule(match, perChunk, r.has("trees") ? r.getAsJsonArray("trees") : new JsonArray());
			if (r.has("stand")) {
				JsonObject s = r.getAsJsonObject("stand");
				rule = rule.withLayers(new TreeStandPlan.Layers(
						s.has("managed_canopy") ? rule(match, perChunk, s.getAsJsonArray("managed_canopy")) : null,
						rule(match, perChunk, s.getAsJsonArray("subcanopy")), rule(match, perChunk, s.getAsJsonArray("regeneration")),
						rule(match, perChunk, s.getAsJsonArray("gap_regeneration")), chances(s.getAsJsonArray("managed_chance")),
						chances(s.getAsJsonArray("natural_chance")), s.get("gap_chance").getAsFloat()));
			}
			rules.add(rule);
		}
		palette = new TreeStandPlan.Palette(rules);
	}

	static GeneratedTreePalette load() {
		return new GeneratedTreePalette();
	}

	/** Whether tree index t is a procedural tree ({@code polishforests:tree/<species>}). */
	boolean procedural(int t) {
		return trees.get(t).startsWith("polishforests:tree/");
	}

	private static double[] chances(JsonArray a) {
		// As the game reads them: floats.
		return new double[] {a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
	}

	private TreeStandPlan.Rule rule(HabitatMatch match, float perChunk, JsonArray entries) {
		int n = entries.size();
		int[] index = new int[n];
		int[] weights = new int[n];
		int[] flags = new int[n];
		int[] alternatives = new int[n];
		int[] alternativeFlags = new int[n];
		int[] depth = new int[n];
		for (int i = 0; i < n; i++) {
			JsonObject e = entries.get(i).getAsJsonObject();
			Species s = species(e.get("species").getAsString());
			index[i] = index(e.get("tree").getAsString(), s);
			weights[i] = e.get("weight").getAsInt();
			boolean ramp = !e.has("range_ramp") || e.get("range_ramp").getAsBoolean();
			flags[i] = ramp ? s.flag() : 0;
			alternatives[i] = -1;
			if (e.has("alternative")) {
				JsonObject a = e.getAsJsonObject("alternative");
				Species as = species(a.get("species").getAsString());
				alternatives[i] = index(a.get("tree").getAsString(), as);
				alternativeFlags[i] = as.flag();
			}
			depth[i] = e.has("max_water_depth") ? e.get("max_water_depth").getAsInt() : 0;
		}
		return new TreeStandPlan.Rule(match, perChunk, index, weights, flags, alternatives, alternativeFlags, depth);
	}

	private int index(String id, Species s) {
		int t = trees.indexOf(id);
		if (t < 0) {
			trees.add(id);
			species.add(s);
			t = trees.size() - 1;
		}
		return t;
	}

	private static Species species(String id) {
		return Arrays.stream(Species.values()).filter(s -> s.id().equals(id)).findFirst().orElseThrow();
	}
}
