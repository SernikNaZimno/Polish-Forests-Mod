package pl.polishforests.client.entity;

import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import pl.polishforests.PolishForests;
import pl.polishforests.entity.ModEntities;
import pl.polishforests.entity.roedeer.RoeDeer;

/**
 * The roe deer through GeckoLib (step V8): model {@code geckolib/models/entity/roe_deer.geo.json}, animations
 * {@code geckolib/animations/entity/roe_deer.animation.json}, one of four textures
 * {@code textures/entity/roe_deer/<doe|buck>_<summer|winter>.png}; all from {@code tools/fauna}. The renderer does not
 * see the entity, so the variant goes to the render state through {@link DataTicket}s; a doe's antler bones are not
 * drawn. {@link BoneProbe} lets the fauna game test read the bone positions of a frame.
 */
public class RoeDeerRenderer<R extends LivingEntityRenderState & GeoRenderState> extends GeoEntityRenderer<RoeDeer, R> {
	static final DataTicket<Boolean> BUCK = DataTicket.create("polishforests_roe_deer_buck", Boolean.class);
	static final DataTicket<Boolean> WINTER_COAT = DataTicket.create("polishforests_roe_deer_winter_coat", Boolean.class);
	private static final String[] ANTLERS = {"antler_left", "antler_right"};

	public RoeDeerRenderer(EntityRendererProvider.Context context) {
		super(context, new Model());
	}

	@Override
	public void addRenderData(RoeDeer animatable, Void relatedObject, R renderState, float partialTick) {
		super.addRenderData(animatable, relatedObject, renderState, partialTick);
		renderState.addGeckolibData(BUCK, animatable.isBuck());
		renderState.addGeckolibData(WINTER_COAT, animatable.hasWinterCoat());
	}

	@Override
	public void adjustModelBonesForRender(RenderPassInfo<R> renderPassInfo, BoneSnapshots snapshots) {
		super.adjustModelBonesForRender(renderPassInfo, snapshots);
		if (!renderPassInfo.getOrDefaultGeckolibData(BUCK, false)) {
			for (String bone : ANTLERS) {
				snapshots.ifPresent(bone, s -> s.skipRender(true));
			}
		}
	}

	@Override
	public void preRenderPass(RenderPassInfo<R> renderPassInfo, SubmitNodeCollector renderTasks) {
		super.preRenderPass(renderPassInfo, renderTasks);
		BoneProbe.attach(renderPassInfo);
	}

	/** The default entity model of GeckoLib with the texture chosen by the variant. */
	static final class Model extends DefaultedEntityGeoModel<RoeDeer> {
		Model() {
			super(PolishForests.id("roe_deer"));
		}

		@Override
		public Identifier getTextureResource(GeoRenderState renderState) {
			boolean buck = renderState.getOrDefaultGeckolibData(BUCK, false);
			boolean winter = renderState.getOrDefaultGeckolibData(WINTER_COAT, false);
			return texture(buck, winter);
		}
	}

	public static Identifier texture(boolean buck, boolean winter) {
		return PolishForests.id("textures/entity/roe_deer/" + (buck ? "buck" : "doe") + "_" + (winter ? "winter" : "summer") + ".png");
	}

	/** Registers the renderer of the roe deer (vanilla's registry, made accessible by Fabric's transitive access wideners). */
	public static void register() {
		EntityRenderers.register(ModEntities.roeDeer(), RoeDeerRenderer::new);
	}
}
