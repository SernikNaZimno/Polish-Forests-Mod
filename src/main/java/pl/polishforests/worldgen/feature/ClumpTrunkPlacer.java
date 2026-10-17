package pl.polishforests.worldgen.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacerType;
import pl.polishforests.block.TrunkBlock;

/**
 * The stems of a multi-stemmed shrub (review of V9, round 2: the hazel and the alder buckthorn of the slice): a main stem
 * as the straight trunk placer makes it, and up to {@code side_stems} more stems one block beside it, each starting from
 * the root crown with a block lying on the ground towards the main stem (the {@link TrunkBlock#PARENT} of a thin trunk
 * block points there, so the shrub stays one woody object) and rising one or two blocks less than the main stem. Each
 * stem carries a crown of the foliage placer; the side stems a crown one smaller. A side stem needs free space and solid
 * ground under its foot block, otherwise it is left out.
 */
public final class ClumpTrunkPlacer extends TrunkPlacer {
	public static final MapCodec<ClumpTrunkPlacer> CODEC = RecordCodecBuilder.mapCodec(i -> trunkPlacerParts(i)
			.and(Codec.intRange(0, 3).fieldOf("side_stems").forGetter(p -> p.sideStems))
			.apply(i, ClumpTrunkPlacer::new));
	public static final TrunkPlacerType<ClumpTrunkPlacer> TYPE = new TrunkPlacerType<>(CODEC);
	private static final Direction[] SIDES = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

	private final int sideStems;

	public ClumpTrunkPlacer(int baseHeight, int heightRandA, int heightRandB, int sideStems) {
		super(baseHeight, heightRandA, heightRandB);
		this.sideStems = sideStems;
	}

	@Override
	protected TrunkPlacerType<?> type() {
		return TYPE;
	}

	@Override
	public List<FoliagePlacer.FoliageAttachment> placeTrunk(WorldGenLevel level, BiConsumer<BlockPos, BlockState> trunkSetter,
			RandomSource random, int treeHeight, BlockPos origin, TreeFeature tree) {
		placeBelowTrunkBlock(level, trunkSetter, random, origin.below(), tree);
		for (int y = 0; y < treeHeight; y++) {
			placeLog(level, trunkSetter, random, origin.above(y), tree);
		}
		List<FoliagePlacer.FoliageAttachment> crowns = new ArrayList<>();
		crowns.add(new FoliagePlacer.FoliageAttachment(origin.above(treeHeight), 0, false));
		int stems = sideStems == 0 ? 0 : 1 + random.nextInt(sideStems);
		int first = random.nextInt(SIDES.length);
		for (int s = 0; s < stems; s++) {
			// Every other side first, so two side stems stand opposite each other around the main stem.
			Direction side = SIDES[(first + 2 * s + s / 2) % SIDES.length];
			BlockPos foot = origin.relative(side);
			if (!validTreePos(level, foot) || !level.getBlockState(foot.below()).isSolidRender()) {
				continue;
			}
			int height = Math.max(1, treeHeight - 1 - random.nextInt(2));
			placeLog(level, trunkSetter, random, foot, tree, st -> withParent(st, side.getOpposite()));
			int top = 1;
			for (; top < height && validTreePos(level, foot.above(top)); top++) {
				placeLog(level, trunkSetter, random, foot.above(top), tree, st -> withParent(st, Direction.DOWN));
			}
			crowns.add(new FoliagePlacer.FoliageAttachment(foot.above(top), -1, false));
		}
		return crowns;
	}

	private static BlockState withParent(BlockState state, Direction parent) {
		return state.hasProperty(TrunkBlock.PARENT) ? state.setValue(TrunkBlock.PARENT, parent) : state;
	}
}
