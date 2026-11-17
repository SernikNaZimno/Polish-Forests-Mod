package pl.polishforests.client.entity;

import com.geckolib.renderer.base.GeoRenderState;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import pl.polishforests.entity.ModEntities;
import pl.polishforests.entity.wildboar.WildBoar;

/**
 * The wild boar through GeckoLib (step FA3, on {@link PolishAnimalRenderer}): model
 * {@code geckolib/models/entity/wild_boar.geo.json}, animations {@code geckolib/animations/entity/wild_boar.animation.json},
 * one of the textures {@code textures/entity/wild_boar/<boar|sow|piglet>.png} chosen by {@link WildBoar#variant()}; the
 * tusks of a sow and a young one are not drawn ({@link WildBoar#hiddenBones()}). The young one is the same model at
 * vanilla's baby scale.
 */
public class WildBoarRenderer<R extends LivingEntityRenderState & GeoRenderState> extends PolishAnimalRenderer<WildBoar, R> {
	public WildBoarRenderer(EntityRendererProvider.Context context) {
		super(context, "wild_boar", WildBoar.SOW);
	}

	/** Registers the renderer of the wild boar. */
	public static void register() {
		EntityRenderers.register(ModEntities.wildBoar(), WildBoarRenderer::new);
	}
}
