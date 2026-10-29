package pl.polishforests.worldgen.feature;

import com.mojang.serialization.MapCodec;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;

/**
 * Placement modifier {@code polishforests:top_of_world} (step J2 of docs/04-pionowy-wycinek.md): moves the position to
 * the highest block of its column. The vegetation dispatchers of the habitat biomes (tree stand, column layers) are placed
 * at the chunk origin, whose Y is the world bottom, and the biome filter after them reads the biome there; with the cave
 * biomes below the surface that biome can be the deep dark, which has no dispatchers, and the whole forest of the chunk
 * would be skipped. At the top of the world the biome is always the habitat of the column. The dispatchers read only X
 * and Z of the position; in a world without cave biomes the filter passes as before (every habitat biome has every
 * dispatcher). It never draws a random number.
 */
public final class TopOfWorldPlacement implements PlacementModifier {
	public static final TopOfWorldPlacement INSTANCE = new TopOfWorldPlacement();
	public static final MapCodec<TopOfWorldPlacement> CODEC = MapCodec.unit(() -> INSTANCE);

	private TopOfWorldPlacement() {
	}

	@Override
	public void modify(PlacementContext context, RandomSource random, BlockPos origin, Consumer<BlockPos> output) {
		output.accept(origin.atY(context.getLevel().getMaxY()));
	}

	@Override
	public MapCodec<TopOfWorldPlacement> codec() {
		return CODEC;
	}
}
