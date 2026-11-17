package pl.polishforests.mixin;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.animal.frog.Frog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.polishforests.entity.spawn.FrogSpawner;
import pl.polishforests.sound.ModSounds;

/**
 * Step FA4: a frog of the variant {@code polishforests:common_frog} croaks with the common frog's call of tools/sound
 * instead of vanilla's frog ambient sound (its hurt, death, step and eating sounds stay vanilla's, by id). Other variants
 * are unchanged.
 */
@Mixin(Frog.class)
abstract class FrogSoundMixin {
	@Inject(method = "getAmbientSound", at = @At("HEAD"), cancellable = true)
	private void polishforests$commonFrogCroak(CallbackInfoReturnable<SoundEvent> cir) {
		if (FrogSpawner.isCommonFrog((Frog) (Object) this)) {
			cir.setReturnValue(ModSounds.COMMON_FROG_CROAK);
		}
	}
}
