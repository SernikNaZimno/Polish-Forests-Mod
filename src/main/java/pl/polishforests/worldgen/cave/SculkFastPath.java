package pl.polishforests.worldgen.cave;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SculkVeinBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * An equivalent fast path of vanilla's sculk spreading (step O2 of docs/04-pionowy-wycinek.md, variant (a) of decision
 * J2-t): the sculk patches of the deep dark ({@code minecraft:sculk_patch_deep_dark}, 256 tries per chunk) took 56% of the
 * decoration samples in the profile of step J2, most of them in {@code MultifaceSpreader} spreading sculk veins over the
 * faces around every new sculk block. The result is the same block for block, with the same draws of the random source:
 * <ul>
 * <li>{@code SculkVeinBlock.SculkVeinSpreaderConfig.stateCanBeReplaced} reads the block behind the placement (and on
 * some paths a neighbor of the source) before it looks at the block in the placement, though every branch but the last
 * returns false and the last needs the placement to be replaceable, air, a vein or water; the mixin
 * ({@code SculkVeinSpreaderConfigMixin}) answers false at once for a placement that is none of these ({@link #cannotHold},
 * a sufficient condition of the vanilla answer false), which skips one or two block reads for most placements (stone);
 * <li>{@code MultifaceSpreader.spreadAll} runs the six faces and six directions as loops in the order of the vanilla
 * streams ({@code Direction.stream()}: the order of {@code Direction.values()}) instead of two stream pipelines per
 * call ({@code MultifaceSpreaderMixin}).
 * </ul>
 * Both only drop work whose result is known; block reads have no side effect. {@code -Dpolishforests.sculkFastPath=off}
 * ({@code -PsculkFastPath=off} of the game tests) runs vanilla's code for a comparison in one build.
 */
public final class SculkFastPath {
	/** System property of the switch. */
	public static final String PROPERTY = "polishforests.sculkFastPath";
	/** Whether the fast path runs (read once). */
	public static final boolean ENABLED = !"off".equalsIgnoreCase(System.getProperty(PROPERTY, "").trim());

	private SculkFastPath() {
	}

	/**
	 * Whether a sculk vein can certainly not be placed into a block in this state: not replaceable, not air, no sculk vein
	 * and no water. The last branch of {@code stateCanBeReplaced} is "replaceable, or air, or this vein block, or a water
	 * source", so this is a sufficient condition of its answer false.
	 */
	public static boolean cannotHold(BlockState existing) {
		return !existing.canBeReplaced() && !existing.isAir() && !(existing.getBlock() instanceof SculkVeinBlock)
				&& !existing.is(Blocks.WATER);
	}
}
