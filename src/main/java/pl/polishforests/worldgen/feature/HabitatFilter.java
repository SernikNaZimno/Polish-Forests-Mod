package pl.polishforests.worldgen.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
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
 * code matches the condition (biomes, zones, forest site types, associations, soils) and whose chunk has a mountain
 * influence P of at most {@code max_mountain_influence} (default 1: any). It reads the {@link ChunkHabitats} of the
 * position's chunk, also when that is a neighbor of the decorated chunk, and outside the "Poland" world it never passes.
 * It serves the glacial erratics (§8.1; P below 0.3, since the Scandinavian ice sheet did not reach the Carpathians and
 * the habitat code has no substrate), datapacks and future vanilla-style features.
 *
 * @param condition            habitat condition
 * @param maxMountainInfluence greatest mountain influence P of the position's chunk
 */
public record HabitatFilter(HabitatCondition condition, float maxMountainInfluence, HabitatMatch match)
		implements PlacementFilter {
	public static final MapCodec<HabitatFilter> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			HabitatCondition.MAP_CODEC.forGetter(HabitatFilter::condition),
			Codec.floatRange(0.0F, 1.0F).optionalFieldOf("max_mountain_influence", 1.0F)
					.forGetter(HabitatFilter::maxMountainInfluence)
	).apply(i, HabitatFilter::new));

	public HabitatFilter(HabitatCondition condition, float maxMountainInfluence) {
		this(condition, maxMountainInfluence, condition.match());
	}

	public HabitatFilter(HabitatCondition condition) {
		this(condition, 1.0F);
	}

	@Override
	public boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos origin) {
		ChunkAccess chunk = context.getLevel().getChunk(origin.getX() >> 4, origin.getZ() >> 4);
		ChunkHabitats habitats = ModFeatures.habitats(chunk, context.generator(), context.getLevel().getSeed());
		return habitats != null && habitats.mountainInfluence() <= maxMountainInfluence
				&& match.matches(habitats.code(origin.getX() & 15, origin.getZ() & 15));
	}

	@Override
	public MapCodec<HabitatFilter> codec() {
		return CODEC;
	}
}
