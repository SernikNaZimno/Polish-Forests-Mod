package pl.polishforests.client;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.reloader.ResourceReloaderKeys;
import net.fabricmc.fabric.api.resource.v1.reloader.SimpleReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import pl.polishforests.PolishForests;
import pl.polishforests.block.WoodPartTexture;
import pl.polishforests.block.WoodSet;

/**
 * The backgrounds of the sign and hanging sign edit screens of the wood sets (step FA7 of docs/04-pionowy-wycinek.md):
 * {@code polishforests:textures/gui/signs/<wood>.png} and {@code polishforests:textures/gui/hanging_signs/<wood>.png} (the
 * screens of Fabric API read the namespaced wood type {@code polishforests:<wood>}, {@link WoodSet#woodType()}). They are
 * plain textures, not atlas sprites, so they are derived on every resource reload ({@link WoodPartTexture#derive} from
 * vanilla's screen texture of the base wood and its planks, read through the resource manager; no pixel of vanilla in the
 * mod) and registered in the texture manager as dynamic textures under their ids. A resource pack with its own texture of
 * ours keeps it (our dynamic texture is released, the texture manager then loads the pack's file).
 *
 * <p>Step FA8: the same for the entity textures of the boats and chest boats ({@code polishforests:textures/entity/boat/<wood>.png},
 * {@code textures/entity/chest_boat/<wood>.png}, from vanilla's boat textures of the base wood; the chest of the chest boat
 * stays as vanilla's), which the boat renderers of the mod draw ({@link pl.polishforests.client.entity.WoodBoatRenderer}).
 */

public final class DerivedSignScreens extends SimpleReloadListener<Map<Identifier, DerivedSignScreens.Prepared>> {
	private static final Logger LOG = LogUtils.getLogger();
	public static final Identifier ID = PolishForests.id("derived_sign_screens");

	/** The textures this listener registered (released when a pack takes one over). */
	private final Set<Identifier> registered = new HashSet<>();

	/** A prepared screen texture: the derived pixels, or {@code null} when a pack has its own texture or the base failed. */
	record Prepared(DerivedPlanksSource.Pixels pixels) {
	}

	/** Registers the listener after the texture manager's reload. */
	public static void register() {
		ResourceLoader loader = ResourceLoader.get(PackType.CLIENT_RESOURCES);
		loader.registerReloadListener(ID, new DerivedSignScreens());
		loader.addListenerOrdering(ResourceReloaderKeys.Client.TEXTURES, ID);
	}

	@Override
	protected Map<Identifier, Prepared> prepare(PreparableReloadListener.SharedState state) {
		ResourceManager resources = state.resourceManager();
		Map<Identifier, Prepared> out = new LinkedHashMap<>();
		for (WoodSet set : WoodSet.values()) {
			for (WoodPartTexture.Part part : WoodPartTexture.Part.values()) {
				if (!part.kind().ownFile()) {
					continue;
				}
				Identifier id = DerivedWoodSource.id(set, part);
				Identifier base = DerivedWoodSource.base(set, part);
				if (resources.getResource(id).isPresent()) {
					DerivedWoodSource.record(new DerivedWoodSource.Result(id, base, false, "a resource pack has its own texture", 0, 0, 0));
					out.put(id, new Prepared(null));
					continue;
				}
				Optional<Resource> in = resources.getResource(base);
				try {
					Optional<DerivedPlanksSource.Pixels> derived = DerivedWoodSource.derive(resources, set, part);
					if (in.isEmpty() || derived.isEmpty()) {
						LOG.warn("[wood parts] texture {} or its planks not found, {} stays missing", base, id);
						DerivedWoodSource.record(new DerivedWoodSource.Result(id, base, false, "base texture not found", 0, 0, 0));
						out.put(id, new Prepared(null));
						continue;
					}
					DerivedPlanksSource.Pixels px = derived.get();
					DerivedWoodSource.record(new DerivedWoodSource.Result(id, base, true, "derived from " + in.get().sourcePackId(),
							px.width(), px.height(), DerivedPlanksSource.hash(px.argb())));
					out.put(id, new Prepared(px));
				} catch (IOException | RuntimeException e) {
					LOG.error("[wood parts] could not derive {} from {}", id, base, e);
					DerivedWoodSource.record(new DerivedWoodSource.Result(id, base, false, "failed: " + e, 0, 0, 0));
					out.put(id, new Prepared(null));
				}
			}
		}
		return out;
	}

	@Override
	protected void apply(Map<Identifier, Prepared> prepared, PreparableReloadListener.SharedState state) {
		TextureManager textures = Minecraft.getInstance().getTextureManager();
		prepared.forEach((id, p) -> {
			if (p.pixels() != null) {
				textures.register(id, new DynamicTexture(id::toString, DerivedWoodSource.image(p.pixels())));
				registered.add(id);
			} else if (registered.remove(id)) {
				textures.release(id);
			}
		});
	}
}
