package pl.polishforests.client.datagen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.features.MiscOverworldFeatures;
import net.minecraft.data.worldgen.placement.PlacementUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.attribute.BackgroundMusic;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.levelgen.carver.WorldCarver;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.heightproviders.UniformHeight;
import net.minecraft.world.level.levelgen.placement.BiomeFilter;
import net.minecraft.world.level.levelgen.placement.CountPlacement;
import net.minecraft.world.level.levelgen.placement.EnvironmentScanPlacement;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.InSquarePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.RarityFilter;
import net.minecraft.world.level.levelgen.placement.SurfaceRelativeThresholdFilter;
import pl.polishforests.PolishForests;
import pl.polishforests.worldgen.chunk.ModBiomeKeys;
import pl.polishforests.worldgen.feature.BiomeDecoration;
import pl.polishforests.worldgen.feature.HabitatFilter;
import pl.polishforests.worldgen.feature.PlantLayerFeature;
import pl.polishforests.worldgen.feature.TreeStandFeature;
import pl.polishforests.worldgen.feature.config.HabitatCondition;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.habitat.Species;

/**
 * Bootstrap of the mod's worldgen registries for datagen (docs/03-m2-biomy.md §8, §10): the 36 biomes from
 * {@link HabitatBiome} (Z8), the vegetation dispatchers of step 9 with their palettes ({@link ModVegetation}, §8.2–8.6),
 * the tree and shrub features ({@link ModTrees}, §8.4), the glacial erratics and the placed features. The step lists
 * come from {@link BiomeDecoration} (Z5).
 */
final class ModWorldgen {
	private ModWorldgen() {
	}

	static ResourceKey<Feature> feature(String path) {
		return ResourceKey.create(Registries.FEATURE, PolishForests.id(path));
	}

	static ResourceKey<PlacedFeature> placed(String path) {
		return ResourceKey.create(Registries.PLACED_FEATURE, PolishForests.id(path));
	}

	// ------------------------------------------------------------------ features

	static void features(BootstrapContext<Feature> context) {
		ModTrees.bootstrap(context);
		HolderGetter<PlacedFeature> placedFeatures = context.lookup(Registries.PLACED_FEATURE);
		ModVegetation.features(context, placedFeatures);
		for (BiomeDecoration.Dispatcher d : BiomeDecoration.Dispatcher.values()) {
			context.register(feature(d.path()), switch (d) {
				case TREE_STAND -> new TreeStandFeature(ModVegetation.treePalette(placedFeatures));
				case DEADWOOD -> new PlantLayerFeature(d, ModVegetation.deadwood(placedFeatures));
				case UNDERSTORY -> new PlantLayerFeature(d, ModVegetation.understory(placedFeatures));
				case WATERSIDE_ZONES -> new PlantLayerFeature(d, ModVegetation.watersideZones(placedFeatures));
				case GROUND_LAYER -> new PlantLayerFeature(d, ModVegetation.groundLayer(placedFeatures));
				case AQUATIC_PLANTS -> new PlantLayerFeature(d, ModVegetation.aquaticPlants(placedFeatures));
			});
		}
	}

	// ------------------------------------------------------------------ placed features

	static void placedFeatures(BootstrapContext<PlacedFeature> context) {
		HolderGetter<Feature> features = context.lookup(Registries.FEATURE);
		// Trees, shrubs and the variants of the palettes: only where a sapling would survive (§8.4; osiers also on the sand
		// and gravel of bars); no biome filter (a sub-feature of a palette).
		List<String> trees = new ArrayList<>();
		ModTrees.TREES.forEach(s -> trees.add(s.path()));
		ModTrees.SHRUBS.forEach(s -> trees.add(s.path()));
		trees.addAll(ModTrees.VARIANTS);
		for (String path : trees) {
			context.register(placed(path), new PlacedFeature(features.getOrThrow(ModTrees.key(path)), ModTrees.placement(path)));
		}
		// The shapes inside the oak and birch selectors and the erratic stones inside theirs: no filter.
		for (String path : ModTrees.SHAPES) {
			context.register(placed(path), new PlacedFeature(features.getOrThrow(ModTrees.key(path)), List.of()));
		}
		for (String stone : ModVegetation.ERRATICS) {
			String path = ModVegetation.erratic(stone);
			context.register(placed(path), new PlacedFeature(features.getOrThrow(ModTrees.key(path)), List.of()));
		}
		// Fallen trees: where a sapling would survive.
		for (Species s : ModVegetation.DEADWOOD) {
			String path = ModVegetation.deadwood(s);
			context.register(placed(path), new PlacedFeature(features.getOrThrow(ModTrees.key(path)),
					List.of(PlacementUtils.filteredByBlockSurvival(Blocks.OAK_SAPLING))));
		}
		// Glacial erratics (step 2, §8.1): once in 6 chunks, on the young glacial till and sands (soils of the habitat
		// code), never in water (the blob needs ground below).
		context.register(placed("glacial_erratics"), new PlacedFeature(features.getOrThrow(ModTrees.key("glacial_erratics")),
				List.of(RarityFilter.onAverageOnceEvery(6), InSquarePlacement.spread(),
						net.minecraft.world.level.levelgen.placement.HeightmapPlacement.onHeightmap(Heightmap.Types.OCEAN_FLOOR_WG),
						new HabitatFilter(new HabitatCondition(List.of(), List.of(), List.of(), List.of(), ModVegetation.ERRATIC_SOILS),
								ModVegetation.ERRATIC_MAX_MOUNTAIN_INFLUENCE),
						BiomeFilter.biome())));
		for (BiomeDecoration.Dispatcher d : BiomeDecoration.Dispatcher.values()) {
			context.register(placed(d.path()), new PlacedFeature(features.getOrThrow(feature(d.path())), List.of(BiomeFilter.biome())));
		}
		// Lava lakes only below Y 0 (decision M2-4, §8.1): the vanilla underground lava lake with a lower height range.
		context.register(placed("deep_lava_lake"), new PlacedFeature(features.getOrThrow(MiscOverworldFeatures.LAKE_LAVA), List.of(
				RarityFilter.onAverageOnceEvery(9),
				InSquarePlacement.spread(),
				HeightRangePlacement.of(UniformHeight.of(VerticalAnchor.absolute(-54), VerticalAnchor.absolute(0))),
				EnvironmentScanPlacement.scanningFor(Direction.DOWN, BlockPredicate.allOf(
						BlockPredicate.not(BlockPredicate.ONLY_IN_AIR_PREDICATE), BlockPredicate.insideWorld(new BlockPos(0, -5, 0))), 32),
				SurfaceRelativeThresholdFilter.of(Heightmap.Types.OCEAN_FLOOR_WG, Integer.MIN_VALUE, -5), BiomeFilter.biome())));
		// Bone meal carriers (§8.1): never placed (count 0), they only give bone meal the flowers of the biome group
		// (polishforests:flowers/*, in the feature tag minecraft:can_spawn_from_bone_meal). The top-level placed features
		// end with the biome filter, as vanilla requires (datagen checks it).
		for (HabitatBiome.BoneMeal b : HabitatBiome.BoneMeal.values()) {
			String group = b.path().substring(b.path().indexOf('/') + 1);
			context.register(placed(b.path()), new PlacedFeature(features.getOrThrow(ModTrees.key(ModVegetation.flowers(group))),
					List.of(CountPlacement.of(ConstantInt.of(0)), BiomeFilter.biome())));
		}
	}

	// ------------------------------------------------------------------ biomes

	static void biomes(BootstrapContext<Biome> context) {
		HolderGetter<PlacedFeature> placedFeatures = context.lookup(Registries.PLACED_FEATURE);
		HolderGetter<WorldCarver> carvers = context.lookup(Registries.CARVER);
		for (HabitatBiome b : HabitatBiome.values()) {
			context.register(ModBiomeKeys.key(b), biome(b, placedFeatures, carvers));
		}
	}

	private static Biome biome(HabitatBiome b, HolderGetter<PlacedFeature> placedFeatures, HolderGetter<WorldCarver> carvers) {
		// No carvers: the generator does not run them (§8.1).
		BiomeGenerationSettings.Builder generation = new BiomeGenerationSettings.Builder(placedFeatures, carvers);
		List<List<String>> steps = BiomeDecoration.steps(b);
		for (int step = 0; step < steps.size(); step++) {
			for (String id : steps.get(step)) {
				generation.addFeature(step, placedFeatures.getOrThrow(ResourceKey.create(Registries.PLACED_FEATURE,
						Identifier.parse(id))));
			}
		}
		BiomeSpecialEffects.Builder effects = new BiomeSpecialEffects.Builder().waterColor(b.waterColor())
				.grassColorOverride(b.grassColor()).foliageColorOverride(b.foliageColor())
				.dryFoliageColorOverride(b.dryFoliageColor());
		Biome.BiomeBuilder builder = new Biome.BiomeBuilder().hasPrecipitation(true).temperature(b.temperature())
				.downfall(b.downfall())
				.setAttribute(EnvironmentAttributes.SKY_COLOR, ARGB.vector3fFromRGB24(skyColor(b.temperature())))
				.specialEffects(effects.build())
				.mobSpawnSettings(spawns(b.spawns()))
				.generationSettings(generation.build());
		switch (b.music()) {
			case FOREST -> builder.setAttribute(EnvironmentAttributes.BACKGROUND_MUSIC, new BackgroundMusic(SoundEvents.MUSIC_BIOME_FOREST));
			case OLD_GROWTH_TAIGA -> builder.setAttribute(EnvironmentAttributes.BACKGROUND_MUSIC,
					new BackgroundMusic(SoundEvents.MUSIC_BIOME_OLD_GROWTH_TAIGA));
			case SWAMP -> builder.setAttribute(EnvironmentAttributes.BACKGROUND_MUSIC, new BackgroundMusic(SoundEvents.MUSIC_BIOME_SWAMP));
			case MEADOW -> builder.setAttribute(EnvironmentAttributes.BACKGROUND_MUSIC, new BackgroundMusic(SoundEvents.MUSIC_BIOME_MEADOW));
			case DEFAULT -> {
			}
		}
		if (b.increasedFireBurnout()) {
			builder.setAttribute(EnvironmentAttributes.INCREASED_FIRE_BURNOUT, true);
		}
		if (b.waterFogColor() >= 0) {
			builder.setAttribute(EnvironmentAttributes.WATER_FOG_COLOR, ARGB.vector3fFromRGB24(b.waterFogColor()));
		}
		return builder.build();
	}

	/** Sky color of a vanilla biome with the same temperature ({@code OverworldBiomes.calculateSkyColor}). */
	private static int skyColor(float temperature) {
		float t = Mth.clamp(temperature / 3.0F, -1.0F, 1.0F);
		return ARGB.opaque(Mth.hsvToRgb(0.62222224F - t * 0.05F, 0.5F + t * 0.1F, 1.0F));
	}

	/**
	 * Natural spawns (§10): forests without farm animals, meadows with them, frogs in wetlands, salmon and drowned in
	 * rivers, cod without squid in the sea; monsters as in the vanilla forest (cave spawns included), without witches in
	 * the alder carr and without surface slimes. Polish fauna comes in M6.
	 */
	private static MobSpawnSettings spawns(HabitatBiome.Spawns profile) {
		MobSpawnSettings.Builder mobs = new MobSpawnSettings.Builder();
		switch (profile) {
			case FOREST, WET_FOREST -> {
				mobs.addSpawn(EntityTypes.WOLF, 5, 4, 4);
				mobs.addSpawn(EntityTypes.FOX, 8, 2, 4);
				mobs.addSpawn(EntityTypes.RABBIT, 4, 2, 3);
				if (profile == HabitatBiome.Spawns.WET_FOREST) {
					mobs.addSpawn(EntityTypes.FROG, 10, 2, 5);
				}
			}
			case OPEN -> {
				mobs.addSpawn(EntityTypes.SHEEP, 12, 4, 4);
				mobs.addSpawn(EntityTypes.PIG, 10, 4, 4);
				mobs.addSpawn(EntityTypes.CHICKEN, 10, 4, 4);
				mobs.addSpawn(EntityTypes.COW, 8, 4, 4);
			}
			case WETLAND -> mobs.addSpawn(EntityTypes.FROG, 10, 2, 5);
			case SPARSE -> mobs.addSpawn(EntityTypes.RABBIT, 4, 2, 3);
			case RIVER -> {
				mobs.addSpawn(EntityTypes.SALMON, 5, 1, 5);
				mobs.addSpawn(EntityTypes.DROWNED, 30, 1, 1);
			}
			case LAKE -> {
			}
			case SEA -> {
				mobs.addSpawn(EntityTypes.COD, 10, 3, 6);
				mobs.addSpawn(EntityTypes.DROWNED, 30, 1, 1);
			}
		}
		mobs.addSpawn(EntityTypes.BAT, 10, 8, 8);
		mobs.addSpawn(EntityTypes.GLOW_SQUID, 10, 4, 6);
		mobs.addSpawn(EntityTypes.SPIDER, 100, 4, 4);
		mobs.addSpawn(EntityTypes.ZOMBIE, 95, 4, 4);
		mobs.addSpawn(EntityTypes.ZOMBIE_VILLAGER, 5, 1, 1);
		mobs.addSpawn(EntityTypes.SKELETON, 100, 4, 4);
		mobs.addSpawn(EntityTypes.CREEPER, 100, 4, 4);
		mobs.addSpawn(EntityTypes.SLIME, 100, 4, 4);
		mobs.addSpawn(EntityTypes.ENDERMAN, 10, 1, 4);
		if (profile != HabitatBiome.Spawns.WET_FOREST) {
			mobs.addSpawn(EntityTypes.WITCH, 5, 1, 1);
		}
		return mobs.build();
	}
}
