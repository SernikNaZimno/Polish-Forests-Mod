package pl.polishforests.client;

import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;
import net.minecraft.client.color.block.BlockTintSources;
import pl.polishforests.block.GroundPlant;
import pl.polishforests.block.ModBlocks;

/**
 * Tints of the herb layer (step V12), as vanilla's plants: the dwarf shrub as the bush (grass color, tint index 0 of the
 * cross; the berries and the moss carpet have none), the flower beds as vanilla's wildflowers (the flower not tinted,
 * the stem with the grass color, tint index 1), the tall fern as the large fern; the litter is not tinted.
 */
final class ClientGroundPlants {
	private ClientGroundPlants() {
	}

	static void register() {
		for (GroundPlant g : GroundPlant.values()) {
			switch (g.kind()) {
				case BERRY_SHRUB -> BlockColorRegistry.register(List.of(BlockTintSources.grass()), ModBlocks.ground(g));
				case SEASONAL -> BlockColorRegistry.register(List.of(BlockTintSources.constant(-1), BlockTintSources.grass()),
						ModBlocks.ground(g));
				case TALL -> BlockColorRegistry.register(List.of(BlockTintSources.doubleTallGrass()), ModBlocks.ground(g));
				case COVER -> {
				}
			}
		}
	}
}
