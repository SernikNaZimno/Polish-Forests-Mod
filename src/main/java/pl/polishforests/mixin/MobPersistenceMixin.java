package pl.polishforests.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.frog.Frog;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.polishforests.entity.spawn.FrogSpawner;

/**
 * Step FA4 (KFA-4): the frogs of the mod's frog spawner (entity tag {@link FrogSpawner#TAG}) are outside vanilla's mob
 * counts and vanilla's despawn, like the animals of the mod ({@code PolishAnimal#requiresCustomPersistence}); the frog
 * spawner removes them beyond 128 blocks from every player. Every other mob is unchanged.
 */
@Mixin(Mob.class)
abstract class MobPersistenceMixin {
	@Inject(method = "requiresCustomPersistence", at = @At("HEAD"), cancellable = true)
	private void polishforests$forestFrog(CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof Frog frog && frog.entityTags().contains(FrogSpawner.TAG)) {
			cir.setReturnValue(true);
		}
	}
}
