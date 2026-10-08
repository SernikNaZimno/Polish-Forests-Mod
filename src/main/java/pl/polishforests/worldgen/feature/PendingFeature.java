package pl.polishforests.worldgen.feature;

import com.mojang.serialization.MapCodec;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * A vegetation dispatcher of step 9 that is registered with its final identifier and place in the list (Z5,
 * docs/03-m2-biomy.md §8.1–8.2), but does nothing yet: deadwood, understory, waterside zones, ground layer and aquatic
 * plants come in step S7 (each then gets its own class and palette, under the same identifier).
 *
 * @param dispatcher which dispatcher this is
 */
public record PendingFeature(BiomeDecoration.Dispatcher dispatcher) implements Feature {
	private static final Map<BiomeDecoration.Dispatcher, MapCodec<PendingFeature>> CODECS = new EnumMap<>(
			BiomeDecoration.Dispatcher.class);

	static {
		for (BiomeDecoration.Dispatcher d : BiomeDecoration.Dispatcher.values()) {
			if (d != BiomeDecoration.Dispatcher.TREE_STAND) {
				CODECS.put(d, MapCodec.unit(new PendingFeature(d)));
			}
		}
	}

	/** Codec of the feature type of a dispatcher (a unit codec: the feature has no configuration yet). */
	public static MapCodec<PendingFeature> codec(BiomeDecoration.Dispatcher dispatcher) {
		return CODECS.get(dispatcher);
	}

	@Override
	public MapCodec<PendingFeature> codec() {
		return CODECS.get(dispatcher);
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		return false;
	}
}
