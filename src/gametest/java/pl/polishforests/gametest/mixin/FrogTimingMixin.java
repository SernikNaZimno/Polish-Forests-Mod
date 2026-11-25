package pl.polishforests.gametest.mixin;

import net.minecraft.world.entity.animal.frog.Frog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.polishforests.gametest.FrogTiming;

/**
 * Step FA10 (KFA-6): times the server ticks of frogs while {@link FrogTiming} is on (the MSPT scenes of the fauna game test
 * with the common frog). Only in the game tests; without the timing on, the tick is unchanged.
 */
@Mixin(Frog.class)
abstract class FrogTimingMixin {
	@Unique
	private long polishforestsGametest$tickStart;

	@Inject(method = "tick", at = @At("HEAD"))
	private void polishforestsGametest$tickHead(CallbackInfo ci) {
		polishforestsGametest$tickStart = FrogTiming.on() && !((Frog) (Object) this).level().isClientSide() ? System.nanoTime() : 0;
	}

	@Inject(method = "tick", at = @At("RETURN"))
	private void polishforestsGametest$tickReturn(CallbackInfo ci) {
		if (polishforestsGametest$tickStart != 0) {
			FrogTiming.add(System.nanoTime() - polishforestsGametest$tickStart);
			polishforestsGametest$tickStart = 0;
		}
	}
}
