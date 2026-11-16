package pl.polishforests.client.entity;

import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import pl.polishforests.PolishForests;
import pl.polishforests.entity.base.PolishAnimal;

/**
 * The renderer of every animal of the mod (step FA1): GeckoLib's entity renderer with the model
 * {@code geckolib/models/entity/<species>.geo.json}, the animations {@code geckolib/animations/entity/<species>.animation.json}
 * and the texture {@code textures/entity/<species>/<variant>.png}, all from {@code tools/fauna}. The renderer does not see
 * the entity when the model is drawn, so the variant ({@link PolishAnimal#variant()}) goes to the render state through the
 * {@link #VARIANT} {@code DataTicket}. {@link BoneProbe} lets the fauna game test read the bone positions of a frame.
 */
public class PolishAnimalRenderer<T extends PolishAnimal<T>, R extends LivingEntityRenderState & GeoRenderState>
		extends GeoEntityRenderer<T, R> {
	static final DataTicket<String> VARIANT = DataTicket.create("polishforests_animal_variant", String.class);

	public PolishAnimalRenderer(EntityRendererProvider.Context context, String species, String defaultVariant) {
		super(context, new Model<>(species, defaultVariant));
	}

	@Override
	public void addRenderData(T animatable, Void relatedObject, R renderState, float partialTick) {
		super.addRenderData(animatable, relatedObject, renderState, partialTick);
		renderState.addGeckolibData(VARIANT, animatable.variant());
	}

	@Override
	public void preRenderPass(RenderPassInfo<R> renderPassInfo, SubmitNodeCollector renderTasks) {
		super.preRenderPass(renderPassInfo, renderTasks);
		BoneProbe.attach(renderPassInfo);
	}

	/** The texture of a species and variant. */
	public static Identifier texture(String species, String variant) {
		return PolishForests.id("textures/entity/" + species + "/" + variant + ".png");
	}

	/** The variant in a render state (the default when it is missing). */
	static String variant(GeoRenderState renderState, String defaultVariant) {
		return renderState.getOrDefaultGeckolibData(VARIANT, defaultVariant);
	}

	/** GeckoLib's default entity model with the texture chosen by the variant. */
	static final class Model<T extends PolishAnimal<T>> extends DefaultedEntityGeoModel<T> {
		private final String species;
		private final String defaultVariant;

		Model(String species, String defaultVariant) {
			super(PolishForests.id(species));
			this.species = species;
			this.defaultVariant = defaultVariant;
		}

		@Override
		public Identifier getTextureResource(GeoRenderState renderState) {
			return texture(species, variant(renderState, defaultVariant));
		}
	}
}
