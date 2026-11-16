package pl.polishforests.client.entity;

import com.geckolib.renderer.base.GeoRenderState;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import pl.polishforests.entity.ModEntities;
import pl.polishforests.entity.roedeer.RoeDeer;

/**
 * The roe deer through GeckoLib (step V8; from step FA1 on {@link PolishAnimalRenderer}): model
 * {@code geckolib/models/entity/roe_deer.geo.json}, animations {@code geckolib/animations/entity/roe_deer.animation.json},
 * one of the textures {@code textures/entity/roe_deer/<variant>.png} chosen by the variant ({@link RoeDeer#variant()}:
 * sex, coat, antlers in October, flared rump patch, young); the antler bones of a doe, a young one and a buck that cast
 * them are not drawn ({@link RoeDeer#hiddenBones()}, step FA2).
 */
public class RoeDeerRenderer<R extends LivingEntityRenderState & GeoRenderState> extends PolishAnimalRenderer<RoeDeer, R> {
	private static final String DEFAULT_VARIANT = RoeDeer.variant(false, false);

	public RoeDeerRenderer(EntityRendererProvider.Context context) {
		super(context, "roe_deer", DEFAULT_VARIANT);
	}

	public static Identifier texture(boolean buck, boolean winter) {
		return texture("roe_deer", RoeDeer.variant(buck, winter));
	}

	/** Registers the renderer of the roe deer (vanilla's registry, made accessible by Fabric's transitive access wideners). */
	public static void register() {
		EntityRenderers.register(ModEntities.roeDeer(), RoeDeerRenderer::new);
	}
}
