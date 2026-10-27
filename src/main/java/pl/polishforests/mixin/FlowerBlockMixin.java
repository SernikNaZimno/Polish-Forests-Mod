package pl.polishforests.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.polishforests.season.GroundPhases;
import pl.polishforests.season.SeasonPhase;

/**
 * Review round 1 of step V13: the vanilla lily of the valley in the Polska world dies back to its rhizome from October to
 * the end of April ({@link GroundPhases#lilyOfTheValley}); the client draws nothing then, and, as the withdrawn wood
 * anemone (rule R12), the block has no outline on either side, so the player does not aim at an invisible plant. Other
 * flowers and other worlds are unchanged; where the getter is not a level (generation, meshing) the shape is vanilla's.
 */
@Mixin(FlowerBlock.class)
abstract class FlowerBlockMixin {
	@Inject(method = "getShape", at = @At("HEAD"), cancellable = true)
	private void polishforests$withdrawnLily(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context,
			CallbackInfoReturnable<VoxelShape> cir) {
		if (state.is(Blocks.LILY_OF_THE_VALLEY) && level instanceof Level l && SeasonPhase.polishWorld(l, pos)
				&& GroundPhases.lilyOfTheValley(SeasonPhase.step(l), pos.getX(), pos.getZ()) == GroundPhases.Look.NONE) {
			cir.setReturnValue(Shapes.empty());
		}
	}
}
