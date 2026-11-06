package pl.polishforests.worldgen.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import pl.polishforests.block.TreeFoliage;
import pl.polishforests.worldgen.feature.config.TreePalette;
import pl.polishforests.worldgen.habitat.Species;
import pl.polishforests.worldgen.tree.AgeClass;
import pl.polishforests.worldgen.tree.CreepingShrubPlan;
import pl.polishforests.worldgen.tree.CrownLayer;
import pl.polishforests.worldgen.tree.TreeVoxels;

/**
 * Feature type {@code polishforests:creeping_shrub} (step F6 of docs/04-pionowy-wycinek.md): one creeping shrub of
 * {@link CreepingShrubPlan} (the dwarf mountain pine of the dwarf pine scrub, the timberline and its saplings) with its
 * base at the origin, which must be free with ground below. The ground of the shrub's columns comes from the world
 * around the base (the first free block above a block with a sturdy top within {@value #REACH} blocks up or down; a
 * column whose top is water, leaves or wood is unknown, so a stem stops there), and the writer is the one of the tree
 * planner ({@link ProceduralTreeFeature#writeCounted}: wood on air or blocks trees replace, leaves never on another
 * tree's leaves, leaf distances from the wood that exists).
 *
 * @param species a shrub with a leaf block (the dwarf mountain pine)
 * @param size    factor of the stem count and length (1: the shrub of the scrub)
 */
public record CreepingShrubFeature(Species species, float size) implements Feature {
	public static final MapCodec<CreepingShrubFeature> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			TreePalette.SPECIES_CODEC.fieldOf("species").forGetter(CreepingShrubFeature::species),
			Codec.floatRange(0.1F, 3.0F).optionalFieldOf("size", 1.0F).forGetter(CreepingShrubFeature::size))
			.apply(i, CreepingShrubFeature::new));
	/** How far the ground of a column is searched up and down from the base. */
	static final int REACH = 3;

	public CreepingShrubFeature {
		if (TreeFoliage.of(species) == null) {
			throw new IllegalArgumentException("no leaf block for " + species);
		}
	}

	@Override
	public MapCodec<CreepingShrubFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		BlockPos below = origin.below();
		if (origin.getY() <= level.getMinY() || !free(level.getBlockState(origin)) || !ProceduralTreeFeature.ground(level, below)) {
			return false;
		}
		TreeVoxels v = plan(level, origin);
		if (ProceduralTreeFeature.writeCounted(level, v, species, CrownLayer.REGENERATION, AgeClass.MATURE) >>> 32 == 0) {
			return false;
		}
		ModFeatures.woodyPlaced(origin, species);
		return true;
	}

	/** The shrub at {@code origin} on the ground of the world (a pure function of the seed, the position and the ground). */
	public TreeVoxels plan(WorldGenLevel level, BlockPos origin) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int ox = origin.getX();
		int oy = origin.getY();
		int oz = origin.getZ();
		CreepingShrubPlan.Ground ground = (dx, dz) -> {
			for (int dy = REACH; dy >= -REACH; dy--) {
				pos.set(ox + dx, oy + dy - 1, oz + dz);
				BlockState s = level.getBlockState(pos);
				if (s.isAir() || s.is(BlockTags.REPLACEABLE_BY_TREES) && !s.is(BlockTags.LEAVES)) {
					continue;
				}
				if (s.is(BlockTags.LEAVES) || s.is(BlockTags.LOGS) || !s.getFluidState().isEmpty()
						|| !s.isFaceSturdy(level, pos, Direction.UP)) {
					return CreepingShrubPlan.UNKNOWN;
				}
				return dy;
			}
			return CreepingShrubPlan.UNKNOWN;
		};
		return CreepingShrubPlan.plan(new CreepingShrubPlan.Request(level.getSeed(), ox, oy, oz, size, ground));
	}

	private static boolean free(BlockState state) {
		return state.isAir() || state.is(BlockTags.REPLACEABLE_BY_TREES);
	}
}
