package pl.polishforests.client.datagen;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import pl.polishforests.PolishForests;
import pl.polishforests.worldgen.chunk.ModBiomeKeys;
import pl.polishforests.worldgen.habitat.HabitatBiome;

import static pl.polishforests.worldgen.habitat.HabitatBiome.*;

/**
 * Biome tags (docs/03-m2-biomy.md §6.3, §10, §10.1): vanilla tags that place structures and drive vanilla mechanics,
 * the {@code has_structure/*} tags that list biomes explicitly, the Serene Seasons tags and the mod's own tags. All
 * tags are additive ({@code "replace": false}).
 *
 * <p>Structures (decision M2-C: all that can be placed sensibly): every vanilla {@code has_structure/*} tag of 26.3 has
 * an entry in {@link #STRUCTURES} or is reached through {@code is_overworld}, {@code is_forest}, {@code is_river},
 * {@code is_beach}, {@code is_ocean} or {@code is_mountain}; the choice for each structure is in the table in §10.1.
 */
final class ModBiomeTagProvider extends FabricTagsProvider<Biome> {
	/**
	 * Explicit additions to the vanilla {@code has_structure/<structure>} tags. Structures not listed here either reach
	 * our biomes through a vanilla tag in their list (stronghold: {@code #is_overworld}; shipwrecks, buried treasure, ocean
	 * ruined portal: {@code #is_ocean}, {@code #is_beach}; mountain ruined portal: {@code #is_mountain}) or need
	 * conditions absent in the world (monument, pyramids, jungle temple, ancient city, desert, savanna and snowy
	 * villages, badlands mineshaft, warm ocean ruins, other abandoned camps, the Nether and the End).
	 */
	static final Map<String, List<HabitatBiome>> STRUCTURES = new LinkedHashMap<>();

	static {
		List<HabitatBiome> nonForestLand = biomes(b -> b.group() == Group.NON_FOREST);
		List<HabitatBiome> openLand = List.of(HAY_MEADOW, ARABLE_LAND, HEATH);
		STRUCTURES.put("mineshaft", concat(nonForestLand, List.of(LAGOON, LAKE, DYSTROPHIC_LAKE)));
		STRUCTURES.put("trial_chambers", List.of(HabitatBiome.values()));
		STRUCTURES.put("village_plains", openLand);
		STRUCTURES.put("village_taiga", List.of(FRESH_PINE_FOREST, MIXED_PINE_FOREST));
		STRUCTURES.put("pillager_outpost", openLand);
		STRUCTURES.put("swamp_hut", List.of(ALDER_CARR, FEN));
		STRUCTURES.put("igloo", List.of(ALPINE_GRASSLAND));
		STRUCTURES.put("woodland_mansion", List.of(OAK_HORNBEAM_FOREST, LOWLAND_BEECH_FOREST, MONTANE_BEECH_FOREST));
		STRUCTURES.put("trail_ruins", concat(HabitatBiome.PINE_FORESTS, List.of(MONTANE_SPRUCE_FOREST, OAK_HORNBEAM_FOREST)));
		STRUCTURES.put("ruined_portal_standard", nonForestLand);
		STRUCTURES.put("ruined_portal_swamp", List.of(ALDER_CARR, RAISED_BOG, FEN));
		STRUCTURES.put("ocean_ruin_cold", List.of(SEA));
		STRUCTURES.put("abandoned_camp_forest", List.of(MIXED_FOREST, OAK_HORNBEAM_FOREST, LOWLAND_BEECH_FOREST, ELM_ASH_FOREST,
				MONTANE_BEECH_FOREST));
		STRUCTURES.put("abandoned_camp_birch_forest", List.of(BOG_WOODLAND));
		STRUCTURES.put("abandoned_camp_old_growth_pine_taiga", List.of(DRY_PINE_FOREST, FRESH_PINE_FOREST, COASTAL_PINE_FOREST,
				MOIST_PINE_FOREST, MIXED_PINE_FOREST));
		STRUCTURES.put("abandoned_camp_old_growth_spruce_taiga", List.of(MONTANE_SPRUCE_FOREST));
		STRUCTURES.put("abandoned_camp_taiga", List.of(UPLAND_FIR_FOREST));
		STRUCTURES.put("abandoned_camp_dappled_forest", List.of(WILLOW_POPLAR_FOREST));
		STRUCTURES.put("abandoned_camp_meadow", List.of(HAY_MEADOW));
		STRUCTURES.put("abandoned_camp_swamp", List.of(ALDER_CARR));
	}

	ModBiomeTagProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
		super(output, Registries.BIOME, registries);
	}

	@Override
	protected void addTags(HolderLookup.Provider registries) {
		add(vanilla("is_overworld"), biomes(b -> true));
		add(vanilla("stronghold_biased_to"), biomes(b -> !b.isWater()));
		add(vanilla("is_forest"), biomes(HabitatBiome::isForest));
		add(vanilla("is_river"), List.of(RIVER, STREAM));
		add(vanilla("is_beach"), List.of(BEACH));
		add(vanilla("is_ocean"), List.of(SEA));
		add(vanilla("is_mountain"), biomes(HabitatBiome::isMountain));
		add(vanilla("water_on_map_outlines"), List.of(LAGOON, LAKE, DYSTROPHIC_LAKE));
		STRUCTURES.forEach((structure, biomes) -> add(vanilla("has_structure/" + structure), biomes));

		// Serene Seasons (§6.3): 25% of the seasonal color change in pine and spruce forests, the dwarf pine, the raised
		// bog and the gray dune; nothing on the blacklist (it would rain over waters in winter).
		add(tag("sereneseasons", "lesser_color_change_biomes"), List.of(DRY_PINE_FOREST, FRESH_PINE_FOREST, COASTAL_PINE_FOREST,
				MOIST_PINE_FOREST, BOG_WOODLAND, MONTANE_SPRUCE_FOREST, DWARF_PINE_SCRUB, RAISED_BOG, GRAY_DUNE));

		add(own("polish_climate"), biomes(b -> true));
		add(own("forests"), biomes(HabitatBiome::isForest));
		add(own("pine_forests"), HabitatBiome.PINE_FORESTS);
		add(own("waterside"), List.of(ALDER_CARR, ASH_ALDER_FOREST, WILLOW_POPLAR_FOREST, ELM_ASH_FOREST, GRAY_ALDER_FOREST, FEN,
				REEDBED, WILLOW_SCRUB));
		add(own("montane"), List.of(MONTANE_BEECH_FOREST, MONTANE_SPRUCE_FOREST, GRAY_ALDER_FOREST, DWARF_PINE_SCRUB,
				ALPINE_GRASSLAND));
	}

	private void add(TagKey<Biome> tag, List<HabitatBiome> biomes) {
		var appender = builder(tag);
		for (HabitatBiome b : biomes) {
			appender.add(ModBiomeKeys.key(b));
		}
	}

	static List<HabitatBiome> biomes(Predicate<HabitatBiome> filter) {
		return Arrays.stream(HabitatBiome.values()).filter(filter).toList();
	}

	private static List<HabitatBiome> concat(List<HabitatBiome> a, List<HabitatBiome> b) {
		return java.util.stream.Stream.concat(a.stream(), b.stream()).toList();
	}

	private static TagKey<Biome> vanilla(String path) {
		return tag("minecraft", path);
	}

	private static TagKey<Biome> own(String path) {
		return TagKey.create(Registries.BIOME, PolishForests.id(path));
	}

	private static TagKey<Biome> tag(String namespace, String path) {
		return TagKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath(namespace, path));
	}

	@Override
	public String getName() {
		return "Polish Forests biome tags";
	}
}
