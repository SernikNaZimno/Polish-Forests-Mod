package pl.polishforests.client.mixin;

import com.mojang.serialization.MapCodec;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteSources;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.polishforests.client.DerivedPlanksSource;

/**
 * Step P2: registers the atlas sprite source {@code polishforests:derived_planks} ({@link DerivedPlanksSource}, the planks of
 * the wood sets derived from the vanilla planks) next to vanilla's five types, at the end of {@code SpriteSources.bootstrap}
 * (the client bootstrap, before any atlas definition is read). Fabric API has no registry for sprite source types.
 */
@Mixin(SpriteSources.class)
abstract class SpriteSourcesMixin {
	@Shadow
	@Final
	private static ExtraCodecs.LateBoundIdMapper<Identifier, MapCodec<? extends SpriteSource>> ID_MAPPER;

	@Inject(method = "bootstrap", at = @At("TAIL"))
	private static void polishforests$derivedPlanks(CallbackInfo ci) {
		ID_MAPPER.put(DerivedPlanksSource.TYPE, DerivedPlanksSource.MAP_CODEC);
	}
}
