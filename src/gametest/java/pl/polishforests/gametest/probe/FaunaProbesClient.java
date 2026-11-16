package pl.polishforests.gametest.probe;

import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.RenderPassInfo;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import pl.polishforests.client.entity.BoneProbe;

/** Client side of the axis gauge (step V8): GeckoLib's default entity renderer, reporting its bones to {@link BoneProbe}. */
public final class FaunaProbesClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		if (!FaunaProbes.enabled()) {
			return;
		}
		EntityRenderers.register(FaunaProbes.axisGauge(), GaugeRenderer::new);
	}

	static final class GaugeRenderer extends GeoEntityRenderer<FaunaProbes.AxisGauge, LivingEntityRenderState> {
		GaugeRenderer(EntityRendererProvider.Context context) {
			super(context, new DefaultedEntityGeoModel<>(RenderProbes.id("axis_gauge")));
		}

		@Override
		public void preRenderPass(RenderPassInfo<LivingEntityRenderState> renderPassInfo, SubmitNodeCollector renderTasks) {
			super.preRenderPass(renderPassInfo, renderTasks);
			BoneProbe.attach(renderPassInfo);
		}
	}
}
