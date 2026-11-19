package pl.polishforests.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.atlas.SpriteResourceLoader;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;

import net.minecraft.server.packs.resources.ResourceManager;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import pl.polishforests.PolishForests;
import pl.polishforests.block.WoodPartTexture;
import pl.polishforests.block.WoodSet;

/**
 * The atlas sprite source of the doors, trapdoors, signs, hanging signs and shelves of the wood sets (step FA7 of
 * docs/04-pionowy-wycinek.md): for every {@link WoodSet} and every part of the atlas ({@code "atlas": "block"}: the door
 * top and bottom, trapdoor, sign, hanging sign and shelf; {@code "item"}: the flat items of the door, sign and hanging sign)
 * the sprite {@code polishforests:<atlas>/<wood>_<part>} is computed while the atlas loads from vanilla's texture of the
 * same part of the set's base wood ({@link WoodSet#partsBase()}) and vanilla's planks of that wood, both read through the
 * resource manager (a resource pack that changes them changes ours), by {@link WoodPartTexture#derive}. No pixel of vanilla
 * is in the mod, and there is no fallback file: a resource pack that brings its own texture of our part keeps it (vanilla's
 * directory source lists it, nothing is derived), a base that cannot be read leaves the sprite missing (logged). The
 * screen textures of the sign edit screens are not in an atlas ({@link DerivedSignScreens}).
 *
 * <p>Step FA8: also the flat items of the boat and the chest boat (the chest of the chest boat stays as vanilla's,
 * {@link WoodPartTexture.Part#shared()}); the entity textures of the boats are not in an atlas either
 * ({@link DerivedSignScreens}).
 *
 * <p>Declared in {@code assets/minecraft/atlases/blocks.json} and {@code items.json} of the mod as
 * {@code {"type": "polishforests:derived_wood", "atlas": "block"}} (or {@code "item"}); the type is registered in vanilla's
 * sprite sources by {@code SpriteSourcesMixin} next to {@code polishforests:derived_planks} (step P2).
 */
public record DerivedWoodSource(String atlas) implements SpriteSource {
	private static final Logger LOG = LogUtils.getLogger();
	public static final Identifier TYPE = PolishForests.id("derived_wood");
	public static final MapCodec<DerivedWoodSource> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			Codec.STRING.fieldOf("atlas").forGetter(DerivedWoodSource::atlas)).apply(i, DerivedWoodSource::new));

	/** What the last loads did for every derived texture, sprites and screen textures (read by the client game test). */
	private static final Map<Identifier, Result> RESULTS = new ConcurrentHashMap<>();

	/**
	 * The outcome of one derived texture: {@code derived} when computed from {@code base} (else a pack's texture stays or
	 * the sprite is missing), the size and a hash of the pixels ({@link DerivedPlanksSource#hash}).
	 */
	public record Result(Identifier id, Identifier base, boolean derived, String reason, int width, int height, long hash) {
	}

	public static Map<Identifier, Result> results() {
		return Collections.unmodifiableMap(RESULTS);
	}

	static void record(Result r) {
		RESULTS.put(r.id(), r);
	}

	/** The id of a derived texture of a wood set: a sprite id, or the texture file of a screen part. */
	public static Identifier id(WoodSet set, WoodPartTexture.Part part) {
		return PolishForests.id(part.path(set.prefix()));
	}

	/** Vanilla's texture of the part a wood set's texture derives from (a sprite id or a screen texture file). */
	public static Identifier base(WoodSet set, WoodPartTexture.Part part) {
		return Identifier.withDefaultNamespace(part.path(set.partsBase()));
	}

	/** The file of a sprite, screen or (step FA8) entity texture id. */
	public static Identifier file(Identifier id, WoodPartTexture.Part part) {
		return part.kind().ownFile() ? id : TEXTURE_ID_CONVERTER.idToFile(id);
	}

	/** Vanilla's planks texture of the base wood of a set (the brightness range of the derivation). */
	public static Identifier planksFile(WoodSet set) {
		return TEXTURE_ID_CONVERTER.idToFile(Identifier.withDefaultNamespace("block/" + set.planksBase()));
	}

	/** The parts this source derives. */
	public List<WoodPartTexture.Part> parts() {
		WoodPartTexture.Kind kind = WoodPartTexture.Kind.valueOf(atlas.toUpperCase(Locale.ROOT));
		List<WoodPartTexture.Part> out = new ArrayList<>();
		for (WoodPartTexture.Part p : WoodPartTexture.Part.values()) {
			if (p.kind() == kind && !kind.ownFile()) {
				out.add(p);
			}
		}
		return out;
	}

	@Override
	public void run(ResourceManager resources, SpriteSource.Output output) {
		for (WoodSet set : WoodSet.values()) {
			for (WoodPartTexture.Part part : parts()) {
				Identifier sprite = id(set, part);
				Identifier base = base(set, part);
				if (resources.getResource(file(sprite, part)).isPresent()) {
					record(new Result(sprite, base, false, "a resource pack has its own texture", 0, 0, 0));
					continue;
				}
				Optional<Resource> in = resources.getResource(file(base, part));
				Optional<Resource> planks = resources.getResource(planksFile(set));
				Optional<Resource> shared = part.shared() == null ? Optional.empty()
						: resources.getResource(file(base(set, part.shared()), part.shared()));
				if (in.isEmpty() || planks.isEmpty() || part.shared() != null && shared.isEmpty()) {
					LOG.warn("[wood parts] base texture {} or {} not found, {} stays missing", base, planksFile(set), sprite);
					record(new Result(sprite, base, false, "base texture not found", 0, 0, 0));
					continue;
				}
				output.add(sprite, new Loader(sprite, base, in.get(), shared.orElse(null), planks.get(), set.planksPalette()));
			}
		}
	}

	@Override
	public MapCodec<DerivedWoodSource> codec() {
		return MAP_CODEC;
	}

	/** Reads a part and the planks of its base and derives the part ({@code [width, height]} in the result). */
	public static DerivedPlanksSource.Pixels derive(Resource part, Resource planks, int[] palette) throws IOException {
		return derive(part, null, planks, palette);
	}

	/**
	 * Step FA8: {@link #derive(Resource, Resource, int[])} of a part that shares its wood with another part of its base
	 * ({@code shared}: the boat of a chest boat, {@link WoodPartTexture#deriveShared}); a null {@code shared} derives the whole
	 * part.
	 */
	public static DerivedPlanksSource.Pixels derive(Resource part, @Nullable Resource shared, Resource planks, int[] palette)
			throws IOException {
		DerivedPlanksSource.Pixels in = DerivedPlanksSource.read(part);
		DerivedPlanksSource.Pixels p = DerivedPlanksSource.read(planks);
		if (shared == null) {
			return new DerivedPlanksSource.Pixels(in.width(), in.height(),
					WoodPartTexture.derive(in.argb(), in.width(), in.height(), p.argb(), p.width(), p.height(), palette));
		}
		DerivedPlanksSource.Pixels s = DerivedPlanksSource.read(shared);
		return new DerivedPlanksSource.Pixels(in.width(), in.height(), WoodPartTexture.deriveShared(in.argb(), in.width(), in.height(),
				s.argb(), s.width(), s.height(), p.argb(), p.width(), p.height(), palette));
	}

	/**
	 * Step FA8: the derived part of a set from vanilla's textures read through {@code resources} (the base of the part, its
	 * shared base when it has one, the planks of the base), or empty when one of them is missing.
	 */
	public static Optional<DerivedPlanksSource.Pixels> derive(ResourceProvider resources, WoodSet set, WoodPartTexture.Part part)
			throws IOException {
		Optional<Resource> in = resources.getResource(file(base(set, part), part));
		Optional<Resource> planks = resources.getResource(planksFile(set));
		Optional<Resource> shared = part.shared() == null ? Optional.empty()
				: resources.getResource(file(base(set, part.shared()), part.shared()));
		if (in.isEmpty() || planks.isEmpty() || part.shared() != null && shared.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(derive(in.get(), shared.orElse(null), planks.get(), set.planksPalette()));
	}

	/** An image of ARGB pixels (the derived texture). */
	static NativeImage image(DerivedPlanksSource.Pixels px) {
		NativeImage image = new NativeImage(px.width(), px.height(), false);
		for (int y = 0; y < px.height(); y++) {
			for (int x = 0; x < px.width(); x++) {
				image.setPixel(x, y, px.argb()[y * px.width() + x]);
			}
		}
		return image;
	}

	/** Loads the base and its planks, derives the part and gives the sprite (no sprite on a failure: missing). */
	private record Loader(Identifier sprite, Identifier base, Resource resource, @Nullable Resource shared, Resource planks,
			int[] palette) implements SpriteSource.DiscardableLoader {
		@Override
		public @Nullable SpriteContents get(SpriteResourceLoader loader) {
			try {
				DerivedPlanksSource.Pixels out = derive(resource, shared, planks, palette);
				record(new Result(sprite, base, true, "derived from " + resource.sourcePackId(), out.width(), out.height(),
						DerivedPlanksSource.hash(out.argb())));
				return new SpriteContents(sprite, new FrameSize(out.width(), out.height()), image(out));
			} catch (IOException | RuntimeException e) {
				LOG.error("[wood parts] could not derive {} from {}", sprite, base, e);
				record(new Result(sprite, base, false, "failed: " + e, 0, 0, 0));
				return null;
			}
		}
	}
}
