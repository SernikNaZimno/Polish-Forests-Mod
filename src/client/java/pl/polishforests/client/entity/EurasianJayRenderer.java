package pl.polishforests.client.entity;

import com.geckolib.renderer.base.GeoRenderState;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import pl.polishforests.entity.ModEntities;
import pl.polishforests.entity.jay.EurasianJay;

/**
 * The Eurasian jay through GeckoLib (step FA6, on {@link PolishAnimalRenderer}): model
 * {@code geckolib/models/entity/eurasian_jay.geo.json}, animations {@code geckolib/animations/entity/eurasian_jay.animation.json},
 * texture {@code textures/entity/eurasian_jay/adult.png}; the acorn in the bill is drawn only while the jay carries one
 * ({@link EurasianJay#hiddenBones()}). The model is made at four times the resolution of the quadrupeds
 * ({@code tools/fauna/bird.py}), so it is drawn at a quarter of the size ({@link #MODEL_SCALE}, tools/fauna Model.scale).
 */
public class EurasianJayRenderer<R extends LivingEntityRenderState & GeoRenderState> extends PolishAnimalRenderer<EurasianJay, R> {
	/** The render scale of the model (tools/fauna/bird.py MODEL_SCALE). */
	public static final float MODEL_SCALE = 0.25F;

	public EurasianJayRenderer(EntityRendererProvider.Context context) {
		super(context, "eurasian_jay", EurasianJay.VARIANT);
		withScale(MODEL_SCALE);
		this.shadowRadius = 0.15F;
	}

	/** Registers the renderer of the Eurasian jay. */
	public static void register() {
		EntityRenderers.register(ModEntities.eurasianJay(), EurasianJayRenderer::new);
	}
}
