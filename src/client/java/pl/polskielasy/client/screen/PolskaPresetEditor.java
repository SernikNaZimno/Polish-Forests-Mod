package pl.polskielasy.client.screen;

import java.util.Set;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.PresetEditor;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import pl.polskielasy.PolskieLasy;
import pl.polskielasy.worldgen.chunk.PolskaChunkGenerator;
import pl.polskielasy.worldgen.chunk.PolskaSettings;

/** Edytor presetów "Polska": przycisk "Dostosuj" w menu tworzenia świata. */
public final class PolskaPresetEditor implements PresetEditor {
	public static final ResourceKey<WorldPreset> PRESET = ResourceKey.create(Registries.WORLD_PRESET,
			PolskieLasy.id("polska"));
	public static final ResourceKey<WorldPreset> PRESET_GAMEPLAY = ResourceKey.create(Registries.WORLD_PRESET,
			PolskieLasy.id("polska_rozgrywka"));
	public static final Set<ResourceKey<WorldPreset>> PRESETS = Set.of(PRESET, PRESET_GAMEPLAY);
	public static final PolskaPresetEditor INSTANCE = new PolskaPresetEditor();

	private PolskaPresetEditor() {
	}

	@Override
	public Screen createEditScreen(CreateWorldScreen parent, WorldCreationContext context) {
		ChunkGenerator overworld = context.selectedDimensions().overworld();
		PolskaSettings current = overworld instanceof PolskaChunkGenerator g ? g.settings() : PolskaSettings.DEFAULT;
		BiomeSource biomes = overworld.getBiomeSource();
		return new PolskaWorldOptionsScreen(parent, current, settings -> parent.getUiState().updateDimensions(
				(registries, dimensions) -> {
					// Skala wyznacza typ wymiaru: świat rozgrywki jest niższy, co odciąża renderowanie.
					Holder<DimensionType> type = registries.lookupOrThrow(Registries.DIMENSION_TYPE)
							.getOrThrow(settings.scale().dimensionType());
					return new WorldDimensions(WorldDimensions.withOverworld(dimensions.dimensions(), type,
							new PolskaChunkGenerator(biomes, settings)));
				}));
	}
}
