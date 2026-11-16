package pl.polishforests.entity.base;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * The thicket where the animals rest (step FA2, docs/04-pionowy-wycinek.md "Projekt FA2"): a place to lie down among the
 * undergrowth, the regeneration or a young stand, read from the blocks around (the stand plan exists only while a chunk
 * generates). The score of a place is the number of undergrowth blocks (leaves and saplings: the shrubs, the young trees
 * and the low crowns of a thicket; the canopy is higher) in the {@value #SIDE} × {@value #SIDE} columns around it, from the
 * height of the feet up to {@value #HEIGHT} blocks above them; a thicket has a score of at least {@value #MIN_SCORE}.
 */
public final class Thicket {
	/** Side of the square of columns around a place (blocks, odd). */
	public static final int SIDE = 5;
	/** Layers above the feet counted (blocks). */
	public static final int HEIGHT = 3;
	/** Least score of a thicket. */
	public static final int MIN_SCORE = 6;
	/** Places tried around the animal for a rest. */
	public static final int CANDIDATES = 16;

	private Thicket() {
	}

	/** Whether a block is undergrowth (leaves or a sapling). */
	public static boolean undergrowth(BlockState state) {
		return state.is(BlockTags.LEAVES) || state.is(BlockTags.SAPLINGS);
	}

	/** The score of a place (feet position): undergrowth blocks around it, see the class comment. */
	public static int score(Level level, BlockPos feet) {
		int r = SIDE / 2;
		int n = 0;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				for (int dy = 0; dy < HEIGHT; dy++) {
					if (undergrowth(level.getBlockState(p.set(feet.getX() + dx, feet.getY() + dy, feet.getZ() + dz)))) {
						n++;
					}
				}
			}
		}
		return n;
	}

	/**
	 * Whether the animal can lie at a feet position: room for the body, firm ground that is no leaf (or a thin block with
	 * collision at the feet, such as a litter or moss layer, that it lies on), no fluid.
	 */
	public static boolean standable(Level level, BlockPos feet) {
		BlockPos below = feet.below();
		BlockState ground = level.getBlockState(below);
		if (ground.is(BlockTags.LEAVES) || !level.getFluidState(feet).isEmpty() || !level.getFluidState(below).isEmpty()
				|| !level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) {
			return false;
		}
		var atFeet = level.getBlockState(feet).getCollisionShape(level, feet);
		if (!atFeet.isEmpty()) {
			return !level.getBlockState(feet).is(BlockTags.LEAVES) && atFeet.max(Direction.Axis.Y) <= 0.9;
		}
		return ground.isFaceSturdy(level, below, Direction.UP);
	}

	/**
	 * The best thicket among {@value #CANDIDATES} places drawn within {@code radius} blocks of {@code around} (the ground of
	 * {@code MOTION_BLOCKING_NO_LEAVES}, so under the shrubs and crowns), or null when none scores at least
	 * {@value #MIN_SCORE}.
	 */
	public static @Nullable BlockPos find(Level level, BlockPos around, int radius, RandomSource random) {
		BlockPos best = null;
		int bestScore = MIN_SCORE - 1;
		for (int i = 0; i < CANDIDATES; i++) {
			int x = around.getX() + random.nextInt(2 * radius + 1) - radius;
			int z = around.getZ() + random.nextInt(2 * radius + 1) - radius;
			if (!level.hasChunkAt(new BlockPos(x, around.getY(), z))) {
				continue;
			}
			BlockPos feet = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
			if (Math.abs(feet.getY() - around.getY()) > 6 || !standable(level, feet)) {
				continue;
			}
			int s = score(level, feet);
			if (s > bestScore) {
				best = feet;
				bestScore = s;
			}
		}
		return best;
	}
}
