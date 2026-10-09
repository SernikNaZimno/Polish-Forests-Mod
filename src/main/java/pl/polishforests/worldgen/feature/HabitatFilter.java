package pl.polishforests.worldgen.feature;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import pl.polishforests.worldgen.chunk.ChunkHabitats;
import pl.polishforests.worldgen.feature.config.HabitatCondition;
import pl.polishforests.worldgen.feature.plan.HabitatMatch;

/**
 * Placement filter {@code polishforests:habitat} (docs/03-m2-biomy.md §8.3): passes a position whose column's habitat
 * code matches the condition (biomes, zones, forest site types, associations, soils). It reads the
 * {@link ChunkHabitats} of the position's chunk, also when that is a neighbor of the decorated chunk, and outside the
 * "Poland" world it never passes. It serves the glacial erratics (§8.1), datapacks and future vanilla-style features.
 *
 * @param condition habitat condition
 */
public record HabitatFilter(HabitatCondition condition, HabitatMatch match) implements PlacementFilter {
	public static final MapCodec<HabitatFilter> CODEC = HabitatCondition.MAP_CODEC.xmap(HabitatFilter::new,
			HabitatFilter::condition);

	public HabitatFilter(HabitatCondition condition) {
		this(condition, condition.match());
	}

	@Override
	public boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos origin) {
		ChunkAccess chunk = context.getLevel().getChunk(origin.getX() >> 4, origin.getZ() >> 4);
		ChunkHabitats habitats = ModFeatures.habitats(chunk, context.generator(), context.getLevel().getSeed());
		return habitats != null && match.matches(habitats.code(origin.getX() & 15, origin.getZ() & 15));
	}

	@Override
	public MapCodec<HabitatFilter> codec() {
		return CODEC;
	}
}
