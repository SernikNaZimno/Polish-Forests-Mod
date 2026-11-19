package pl.polishforests.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.boat.BoatModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.AbstractBoatRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.state.BoatRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Unit;
import pl.polishforests.block.WoodPartTexture;
import pl.polishforests.block.WoodSet;
import pl.polishforests.client.DerivedWoodSource;
import pl.polishforests.entity.WoodBoats;

/**
 * The renderer of the boats and chest boats of the wood sets (step FA8 of docs/04-pionowy-wycinek.md): vanilla's boat model
 * baked from vanilla's model layers by id ({@code minecraft:boat/oak} or {@code minecraft:chest_boat/oak}: every vanilla
 * wood boat has the same geometry), vanilla's water patch, and the entity texture of the set
 * ({@code polishforests:textures/entity/boat/<wood>.png} or {@code chest_boat/<wood>.png}), which the client derives from
 * vanilla's boat texture of the base wood and registers in the texture manager ({@code DerivedSignScreens}). What it draws
 * is vanilla's boat renderer with another texture: vanilla's renderer takes the texture from the model layer's name, so the
 * mod keeps the model layer of vanilla and gives the texture itself.
 */
public final class WoodBoatRenderer extends AbstractBoatRenderer {
	private final Model.Simple waterPatchModel;
	private final EntityModel<BoatRenderState> model;

	WoodBoatRenderer(EntityRendererProvider.Context context, ModelLayerLocation layer, Identifier texture) {
		super(context, texture);
		this.waterPatchModel = new Model.Simple(context.bakeLayer(ModelLayers.BOAT_WATER_PATCH), t -> RenderTypes.waterMask());
		this.model = new BoatModel(context.bakeLayer(layer));
	}

	/** Registers the renderers of every boat and chest boat of the mod. */
	public static void register() {
		for (WoodSet w : WoodSet.values()) {
			WoodBoats.Set s = WoodBoats.of(w);
			Identifier boat = DerivedWoodSource.id(w, WoodPartTexture.Part.BOAT);
			Identifier chest = DerivedWoodSource.id(w, WoodPartTexture.Part.CHEST_BOAT);
			EntityRenderers.register(s.boat(), context -> new WoodBoatRenderer(context, ModelLayers.OAK_BOAT, boat));
			EntityRenderers.register(s.chestBoat(), context -> new WoodBoatRenderer(context, ModelLayers.OAK_CHEST_BOAT, chest));
		}
	}

	@Override
	protected EntityModel<BoatRenderState> model() {
		return model;
	}

	@Override
	protected void submitTypeAdditions(BoatRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords) {
		if (!state.isUnderWater) {
			submitNodeCollector.submitModel(waterPatchModel, Unit.INSTANCE, poseStack, texture, lightCoords, OverlayTexture.NO_OVERLAY,
					state.outlineColor);
		}
	}
}
