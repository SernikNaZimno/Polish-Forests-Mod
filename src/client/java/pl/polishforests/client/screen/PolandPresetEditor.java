package pl.polishforests.client.screen;

import java.util.Set;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.PresetEditor;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import pl.polishforests.PolishForests;
import pl.polishforests.worldgen.chunk.PolandBiomeSource;
import pl.polishforests.worldgen.chunk.PolandChunkGenerator;
import pl.polishforests.worldgen.chunk.PolandSettings;

/** Preset editor for "Poland": the "Customize" button in the world creation menu. */
public final class PolandPresetEditor implements PresetEditor {
	public static final ResourceKey<WorldPreset> PRESET = ResourceKey.create(Registries.WORLD_PRESET,
			PolishForests.id("poland"));
	public static final ResourceKey<WorldPreset> PRESET_GAMEPLAY = ResourceKey.create(Registries.WORLD_PRESET,
			PolishForests.id("poland_gameplay"));
	public static final Set<ResourceKey<WorldPreset>> PRESETS = Set.of(PRESET, PRESET_GAMEPLAY);
	public static final PolandPresetEditor INSTANCE = new PolandPresetEditor();

	private PolandPresetEditor() {
	}

	@Override
	public Screen createEditScreen(CreateWorldScreen parent, WorldCreationContext context) {
		ChunkGenerator overworld = context.selectedDimensions().overworld();
		PolandSettings current = overworld instanceof PolandChunkGenerator g ? g.settings() : PolandSettings.DEFAULT;
		return new PolandWorldOptionsScreen(parent, current, settings -> parent.getUiState().updateDimensions(apply(settings)));
	}

	/**
	 * Dimensions update that gives the overworld a "Poland" generator with the given settings: the "Done" button of the
	 * options screen (also used by the game tests to create a world in a chosen mode).
	 */
	public static WorldCreationContext.DimensionsUpdater apply(PolandSettings settings) {
		return (registries, dimensions) -> {
			// The scale determines the dimension type: the gameplay world is lower, which eases rendering.
			Holder<DimensionType> type = registries.lookupOrThrow(Registries.DIMENSION_TYPE).getOrThrow(settings.scale().dimensionType());
			// A new biome source for the new generator: the source is bound to one generator's model and classifier.
			PolandBiomeSource biomes = PolandBiomeSource.create(registries.lookupOrThrow(Registries.BIOME));
			return new WorldDimensions(WorldDimensions.withOverworld(dimensions.dimensions(), type,
					new PolandChunkGenerator(biomes, settings)));
		};
	}
}
