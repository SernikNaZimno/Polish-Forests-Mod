package pl.polishforests.client.datagen;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.features.MiscOverworldFeatures;
import net.minecraft.data.worldgen.features.VegetationFeatures;
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
import pl.polishforests.worldgen.feature.PendingFeature;
import pl.polishforests.worldgen.feature.TreeStandFeature;
import pl.polishforests.worldgen.feature.config.TreePalette;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.habitat.Species;

/**
 * Bootstrap of the mod's worldgen registries for datagen (docs/03-m2-biomy.md §8, §10): the 36 biomes from
 * {@link HabitatBiome} (Z8), the vegetation dispatchers of step 9 with the tree palettes (§8.5), the tree features and
 * the placed features. The step lists come from {@link BiomeDecoration} (Z5).
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
		for (BiomeDecoration.Dispatcher d : BiomeDecoration.Dispatcher.values()) {
			context.register(feature(d.path()), d == BiomeDecoration.Dispatcher.TREE_STAND
					? new TreeStandFeature(treePalette(placedFeatures)) : new PendingFeature(d));
		}
	}

	/**
	 * Tree palette (§8.5): trees per chunk and composition in percent. A species with a range flag (beech, fir, spruce,
	 * hornbeam) grows only within its range; "beech or spruce" is beech with spruce as the alternative species, so the
	 * full weight goes to whichever of the two is within range (in the lowlands they rarely overlap). Basic version of S5:
	 * one rule per biome, without zones (the timberline, the wind belt and the stunted pine come in step S7).
	 */
	private static TreePalette treePalette(HolderGetter<PlacedFeature> placed) {
		List<TreePalette.Rule> rules = new ArrayList<>();
		rule(rules, placed, HabitatBiome.DRY_PINE_FOREST, 7, Map.of(Species.SCOTS_PINE, 95, Species.BIRCH, 5));
		rule(rules, placed, HabitatBiome.FRESH_PINE_FOREST, 11, Map.of(Species.SCOTS_PINE, 85, Species.BIRCH, 10, Species.SPRUCE, 5));
		rule(rules, placed, HabitatBiome.COASTAL_PINE_FOREST, 9, Map.of(Species.SCOTS_PINE, 90, Species.BIRCH, 10));
		rule(rules, placed, HabitatBiome.MOIST_PINE_FOREST, 10, Map.of(Species.SCOTS_PINE, 65, Species.BIRCH, 25, Species.SPRUCE, 10));
		rule(rules, placed, HabitatBiome.BOG_WOODLAND, 6, Map.of(Species.SCOTS_PINE, 70, Species.BIRCH, 30));
		rule(rules, placed, HabitatBiome.MIXED_PINE_FOREST, 10, Map.of(Species.SCOTS_PINE, 55, Species.OAK, 25, Species.BIRCH, 10,
				Species.BEECH, 10), Map.of(Species.BEECH, Species.SPRUCE));
		rule(rules, placed, HabitatBiome.MIXED_FOREST, 9, Map.of(Species.OAK, 45, Species.SCOTS_PINE, 30, Species.BEECH, 15,
				Species.HORNBEAM, 10), Map.of(Species.BEECH, Species.SPRUCE));
		rule(rules, placed, HabitatBiome.OAK_HORNBEAM_FOREST, 9, Map.of(Species.OAK, 35, Species.HORNBEAM, 30, Species.LINDEN, 15,
				Species.ASH, 3, Species.NORWAY_MAPLE, 2, Species.BEECH, 10, Species.SPRUCE, 5));
		rule(rules, placed, HabitatBiome.LOWLAND_BEECH_FOREST, 7, Map.of(Species.BEECH, 85, Species.OAK, 10, Species.SYCAMORE_MAPLE, 5));
		rule(rules, placed, HabitatBiome.ALDER_CARR, 9, Map.of(Species.BLACK_ALDER, 85, Species.BIRCH, 10, Species.ASH, 5));
		rule(rules, placed, HabitatBiome.ASH_ALDER_FOREST, 9, Map.of(Species.BLACK_ALDER, 55, Species.ASH, 30, Species.ELM, 10,
				Species.BIRCH, 5));
		rule(rules, placed, HabitatBiome.WILLOW_POPLAR_FOREST, 7, Map.of(Species.WHITE_WILLOW, 70, Species.POPLAR, 30));
		rule(rules, placed, HabitatBiome.ELM_ASH_FOREST, 8, Map.of(Species.OAK, 35, Species.ASH, 30, Species.ELM, 25,
				Species.NORWAY_MAPLE, 5, Species.LINDEN, 5));
		rule(rules, placed, HabitatBiome.UPLAND_FIR_FOREST, 10, Map.of(Species.FIR, 50, Species.BEECH, 25, Species.OAK, 15,
				Species.SCOTS_PINE, 10));
		rule(rules, placed, HabitatBiome.MONTANE_BEECH_FOREST, 8, Map.of(Species.BEECH, 70, Species.FIR, 20, Species.SPRUCE, 5,
				Species.SYCAMORE_MAPLE, 5));
		rule(rules, placed, HabitatBiome.MONTANE_SPRUCE_FOREST, 12, Map.of(Species.SPRUCE, 90, Species.ROWAN, 10));
		rule(rules, placed, HabitatBiome.GRAY_ALDER_FOREST, 8, Map.of(Species.GRAY_ALDER, 70, Species.ASH, 15, Species.SPRUCE, 10,
				Species.WHITE_WILLOW, 5));
		rule(rules, placed, HabitatBiome.RAISED_BOG, 0.3F, Map.of(Species.SCOTS_PINE, 1));
		rule(rules, placed, HabitatBiome.HEATH, 0.5F, Map.of(Species.SCOTS_PINE, 60, Species.BIRCH, 40));
		rule(rules, placed, HabitatBiome.WET_MEADOW, 0.1F, Map.of(Species.BLACK_ALDER, 60, Species.WHITE_WILLOW, 40));
		rule(rules, placed, HabitatBiome.HAY_MEADOW, 0.1F, Map.of(Species.OAK, 50, Species.BIRCH, 30, Species.LINDEN, 20));
		rule(rules, placed, HabitatBiome.GRAY_DUNE, 0.125F, Map.of(Species.SCOTS_PINE, 1));
		return new TreePalette(rules);
	}

	/** A rule with the species in the order of {@link Species} (stable JSON). */
	private static void rule(List<TreePalette.Rule> rules, HolderGetter<PlacedFeature> placed, HabitatBiome biome,
			float treesPerChunk, Map<Species, Integer> composition) {
		rule(rules, placed, biome, treesPerChunk, composition, Map.of());
	}

	/** A rule whose species may have an alternative species that takes their weight out of their range. */
	private static void rule(List<TreePalette.Rule> rules, HolderGetter<PlacedFeature> placed, HabitatBiome biome,
			float treesPerChunk, Map<Species, Integer> composition, Map<Species, Species> alternatives) {
		List<TreePalette.Entry> entries = new ArrayList<>();
		for (Species s : Species.values()) {
			Integer weight = composition.get(s);
			if (weight != null) {
				Species alt = alternatives.get(s);
				entries.add(new TreePalette.Entry(s, placed.getOrThrow(placed(s.path())), weight, java.util.Optional.ofNullable(alt)
						.map(a -> new TreePalette.Alternative(a, placed.getOrThrow(placed(a.path()))))));
			}
		}
		rules.add(new TreePalette.Rule(List.of(biome), treesPerChunk, entries));
	}

	// ------------------------------------------------------------------ placed features

	static void placedFeatures(BootstrapContext<PlacedFeature> context) {
		HolderGetter<Feature> features = context.lookup(Registries.FEATURE);
		for (Species s : ModTrees.TREES) {
			// Trees: only where a sapling would survive (§8.4); no biome filter (a sub-feature of a palette).
			context.register(placed(s.path()), new PlacedFeature(features.getOrThrow(ModTrees.key(s)),
					List.of(PlacementUtils.filteredByBlockSurvival(Blocks.OAK_SAPLING))));
		}
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
		// Bone meal carriers (§8.1): never placed (count 0), they only give bone meal its flowers in the biome. The
		// top-level placed features end with the biome filter, as vanilla requires (datagen checks it).
		Map<HabitatBiome.BoneMeal, ResourceKey<Feature>> flowers = Map.of(
				HabitatBiome.BoneMeal.FOREST, VegetationFeatures.WILDFLOWER,
				HabitatBiome.BoneMeal.MEADOW, VegetationFeatures.FLOWER_MEADOW,
				HabitatBiome.BoneMeal.WETLAND, VegetationFeatures.FLOWER_DEFAULT,
				HabitatBiome.BoneMeal.MOUNTAIN, VegetationFeatures.FLOWER_PLAIN);
		for (HabitatBiome.BoneMeal b : HabitatBiome.BoneMeal.values()) {
			context.register(placed(b.path()), new PlacedFeature(features.getOrThrow(flowers.get(b)),
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
