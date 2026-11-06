package pl.polishforests.worldgen.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecorator;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecoratorType;
import pl.polishforests.block.ClimberBlock;

/**
 * The hop on a shrub of the floodplain forests and the osier scrub (step F5 of docs/04-pionowy-wycinek.md, the shrub
 * variants {@code shrub/<species>_hop_thin}): a shrub carries hop with the chance {@code shrub_chance} drawn from the
 * column of its lowest stem, then each side of a stem or leaf block below the crown top takes a hop block with the chance
 * {@code probability} drawn from the block's own position ({@link TreeHop#decorate}, salt
 * {@link DecorationRandom#HOP_SALT}): as the hanging vines of step L1, a function of the world seed and the positions, not
 * of the shrub's random source. The hop goes only on air and only beside a block that holds it ({@link ClimberBlock#support}:
 * the leaves; the shoots of radius 1 px do not, round 1 of the F5 review).
 */
public final class HopDecorator extends TreeDecorator {
	public static final MapCodec<HopDecorator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Codec.floatRange(0.0F, 1.0F).fieldOf("shrub_chance").forGetter(d -> d.shrubChance),
			Codec.floatRange(0.0F, 1.0F).fieldOf("probability").forGetter(d -> d.probability)).apply(i, HopDecorator::new));
	public static final TreeDecoratorType<HopDecorator> TYPE = new TreeDecoratorType<>(CODEC);

	private final float shrubChance;
	private final float probability;

	public HopDecorator(float shrubChance, float probability) {
		this.shrubChance = shrubChance;
		this.probability = probability;
	}

	@Override
	protected TreeDecoratorType<?> type() {
		return TYPE;
	}

	@Override
	public void place(TreeDecorator.Context context) {
		if (context.logs().isEmpty()) {
			return;
		}
		BlockPos foot = null;
		for (BlockPos p : context.logs()) {
			if (foot == null || p.getY() < foot.getY() || p.getY() == foot.getY()
					&& (p.getX() < foot.getX() || p.getX() == foot.getX() && p.getZ() < foot.getZ())) {
				foot = p;
			}
		}
		long seed = context.level().getSeed();
		if (TreeHop.unit(seed, foot.getX(), foot.getZ(), 0) >= shrubChance) {
			return;
		}
		int top = Integer.MIN_VALUE;
		for (BlockPos p : context.leaves()) {
			top = Math.max(top, p.getY());
		}
		List<BlockPos> blocks = new ArrayList<>(context.logs());
		blocks.addAll(context.leaves());
		TreeHop.decorate(seed, blocks, top == Integer.MIN_VALUE ? Integer.MAX_VALUE : top, probability,
				p -> context.checkBlock(p, ClimberBlock::support), context::isAir, (p, state) -> {
					context.setBlock(p, state);
					// Round 1 of the F5 review: dropped at the chunk's first tick if a later object takes its support's place.
					context.level().getChunk(p).markPosForPostProcessing(p);
				});
	}
}
