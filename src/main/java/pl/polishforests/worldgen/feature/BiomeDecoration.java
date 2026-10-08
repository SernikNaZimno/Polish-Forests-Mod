package pl.polishforests.worldgen.feature;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import pl.polishforests.PolishForests;
import pl.polishforests.worldgen.habitat.HabitatBiome;

/**
 * Decoration step lists of the biomes (Z5, docs/03-m2-biomy.md §8.1): the same list in every biome, from this one
 * source (datagen writes the biome JSONs from it, {@code FeatureOrderTest} checks them). The only element that differs
 * between biomes is the bone meal carrier at the end of step 9. Vanilla placed features are referenced by id, ours are
 * never inline. Removed from the vanilla lists: lava lakes on the surface, {@code spring_water}, {@code spring_lava},
 * {@code underwater_magma} and {@code glow_lichen} (§8.1).
 */
public final class BiomeDecoration {
	/** Number of decoration steps ({@code GenerationStep.Decoration}). */
	public static final int STEPS = 11;
	/** Step of the vegetation dispatchers ({@code VEGETAL_DECORATION}). */
	public static final int VEGETATION_STEP = 9;

	/** Lava lakes only below Y 0 (decision M2-4). */
	public static final String DEEP_LAVA_LAKE = PolishForests.id("deep_lava_lake").toString();

	/** Ores and disks of step 6 in the vanilla order, without {@code underwater_magma}. */
	public static final List<String> ORES_AND_DISKS = List.of(
			"minecraft:ore_dirt", "minecraft:ore_gravel", "minecraft:ore_granite_upper", "minecraft:ore_granite_lower",
			"minecraft:ore_diorite_upper", "minecraft:ore_diorite_lower", "minecraft:ore_andesite_upper",
			"minecraft:ore_andesite_lower", "minecraft:ore_tuff", "minecraft:ore_coal_upper", "minecraft:ore_coal_lower",
			"minecraft:ore_iron_upper", "minecraft:ore_iron_middle", "minecraft:ore_iron_small", "minecraft:ore_gold",
			"minecraft:ore_gold_lower", "minecraft:ore_redstone", "minecraft:ore_redstone_lower", "minecraft:ore_diamond",
			"minecraft:ore_diamond_medium", "minecraft:ore_diamond_large", "minecraft:ore_diamond_buried",
			"minecraft:ore_lapis", "minecraft:ore_lapis_buried", "minecraft:ore_copper",
			"minecraft:disk_sand", "minecraft:disk_clay", "minecraft:disk_gravel");

	/**
	 * Vegetation dispatchers of step 9 in their final order (§8.2). In step S5 only the tree stand works (basic version:
	 * tree palettes per biome, without zones); the others are registered but do nothing yet (steps S7 and S8).
	 */
	public enum Dispatcher {
		TREE_STAND, DEADWOOD, UNDERSTORY, WATERSIDE_ZONES, GROUND_LAYER, AQUATIC_PLANTS;

		/** Path of the feature type, the feature and the placed feature, e.g. {@code tree_stand}. */
		public String path() {
			return name().toLowerCase(Locale.ROOT);
		}

		public String id() {
			return PolishForests.id(path()).toString();
		}
	}

	private BiomeDecoration() {
	}

	/** Bone meal carrier of the biome (the last placed feature of step 9). */
	public static String boneMealCarrier(HabitatBiome biome) {
		return PolishForests.id(biome.boneMeal().path()).toString();
	}

	/** Placed features of each of the 11 steps for the biome. */
	public static List<List<String>> steps(HabitatBiome biome) {
		List<List<String>> steps = new ArrayList<>(STEPS);
		for (int i = 0; i < STEPS; i++) {
			steps.add(new ArrayList<>());
		}
		steps.get(1).add(DEEP_LAVA_LAKE);
		steps.get(2).add("minecraft:amethyst_geode");
		steps.get(3).addAll(List.of("minecraft:monster_room", "minecraft:monster_room_deep"));
		steps.get(6).addAll(ORES_AND_DISKS);
		for (Dispatcher d : Dispatcher.values()) {
			steps.get(VEGETATION_STEP).add(d.id());
		}
		steps.get(VEGETATION_STEP).add(boneMealCarrier(biome));
		steps.get(10).add("minecraft:freeze_top_layer");
		return steps.stream().<List<String>>map(List::copyOf).toList();
	}
}
