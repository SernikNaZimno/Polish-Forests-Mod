package pl.polishforests.client.entity;

import com.geckolib.renderer.base.GeoRenderState;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import pl.polishforests.entity.ModEntities;
import pl.polishforests.entity.squirrel.RedSquirrel;

/**
 * The red squirrel through GeckoLib (step FA5, on {@link PolishAnimalRenderer}): model
 * {@code geckolib/models/entity/red_squirrel.geo.json}, animations {@code geckolib/animations/entity/red_squirrel.animation.json},
 * one of the textures {@code textures/entity/red_squirrel/<red|dark>_<summer|winter>.png} chosen by
 * {@link RedSquirrel#variant()}; the ear tufts outside the winter coat and the food outside eating are not drawn
 * ({@link RedSquirrel#hiddenBones()}). The model is made at twice the resolution of the other animals
 * ({@code tools/fauna/smallmammal.py}), so it is drawn at half the size ({@link #MODEL_SCALE}, tools/fauna Model.scale).
 */
public class RedSquirrelRenderer<R extends LivingEntityRenderState & GeoRenderState> extends PolishAnimalRenderer<RedSquirrel, R> {
	/** The render scale of the model (tools/fauna/smallmammal.py MODEL_SCALE). */
	public static final float MODEL_SCALE = 0.5F;

	public RedSquirrelRenderer(EntityRendererProvider.Context context) {
		super(context, "red_squirrel", RedSquirrel.variant(false, false));
		withScale(MODEL_SCALE);
		this.shadowRadius = 0.2F;
	}

	/** Registers the renderer of the red squirrel. */
	public static void register() {
		EntityRenderers.register(ModEntities.redSquirrel(), RedSquirrelRenderer::new);
	}
}
