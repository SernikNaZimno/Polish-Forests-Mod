package pl.polishforests.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.polishforests.block.ClimberBlock;

/**
 * Round 1 of the F5 review: the hop ({@link ClimberBlock}, tag {@code minecraft:climbable}) holds a climbing entity only
 * while it stands. From November to April it has died back ({@link ClimberBlock#withdrawn}: the client draws nothing and
 * the block has no outline, rule R12), and then it is not a ladder either: no climbing, no hold when sneaking, the fall
 * damage as on air. Both sides compute the season the same way, so the client's movement and the server agree. Other
 * climbable blocks are unchanged.
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityClimbableMixin {
	@Inject(method = "onClimbable", at = @At("HEAD"), cancellable = true)
	private void polishforests$withdrawnClimber(CallbackInfoReturnable<Boolean> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		BlockState state = self.getInBlockState();
		if (state.getBlock() instanceof ClimberBlock climber && climber.withdrawn(self.level(), self.blockPosition())) {
			cir.setReturnValue(false);
		}
	}
}
