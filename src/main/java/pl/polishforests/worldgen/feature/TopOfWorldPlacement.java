package pl.polishforests.worldgen.feature;

import com.mojang.serialization.MapCodec;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import pl.polishforests.worldgen.chunk.ChunkHabitats;
import pl.polishforests.worldgen.chunk.ForestWorld;
import pl.polishforests.worldgen.vanilla.VanillaForests;

/**
 * Placement modifier {@code polishforests:top_of_world} (step J2 of docs/04-pionowy-wycinek.md): moves the position to
 * the highest block of its column. The vegetation dispatchers of the habitat biomes (tree stand, column layers) are placed
 * at the chunk origin, whose Y is the world bottom, and the biome filter after them reads the biome there; with the cave
 * biomes below the surface that biome can be the deep dark, which has no dispatchers, and the whole forest of the chunk
 * would be skipped. At the top of the world the biome is always the habitat of the column. The dispatchers read only X
 * and Z of the position; in a world without cave biomes the filter passes as before (every habitat biome has every
 * dispatcher). It never draws a random number.
 *
 * <p>Step W1: in the world type "Default + Polish Forests" a chunk can hold vanilla biomes and habitat biomes, and the biome
 * at the chunk origin can be a vanilla one, whose list has no dispatchers. There the position is the top ground block of
 * the first column of the chunk (in index order) whose code is a habitat biome ({@link VanillaForests#present}): the biome
 * filter after it reads the biome the column's code was made from, so the dispatchers run once in every chunk with some
 * forest of the mod and never in a chunk without one.
 */
public final class TopOfWorldPlacement implements PlacementModifier {
	public static final TopOfWorldPlacement INSTANCE = new TopOfWorldPlacement();
	public static final MapCodec<TopOfWorldPlacement> CODEC = MapCodec.unit(() -> INSTANCE);

	private TopOfWorldPlacement() {
	}

	@Override
	public void modify(PlacementContext context, RandomSource random, BlockPos origin, Consumer<BlockPos> output) {
		ForestWorld world = ForestWorld.of(context.generator());
		if (world != null && world.vanilla()) {
			ChunkHabitats h = context.getLevel().getChunk(origin.getX() >> 4, origin.getZ() >> 4)
					.getAttached(ModFeatures.CHUNK_HABITATS);
			int i = h == null ? -1 : firstForest(h);
			if (i >= 0) {
				output.accept(new BlockPos((origin.getX() & ~15) + (i >> 4), h.top()[i], (origin.getZ() & ~15) + (i & 15)));
			}
			return;
		}
		output.accept(origin.atY(context.getLevel().getMaxY()));
	}

	/** Index of the first column whose code is a habitat biome of the world type "Default + Polish Forests", or -1. */
	static int firstForest(ChunkHabitats habitats) {
		int[] codes = habitats.codes();
		for (int i = 0; i < 256; i++) {
			if (VanillaForests.present(pl.polishforests.worldgen.habitat.Habitat.biome(codes[i]))) {
				return i;
			}
		}
		return -1;
	}

	@Override
	public MapCodec<TopOfWorldPlacement> codec() {
		return CODEC;
	}
}
