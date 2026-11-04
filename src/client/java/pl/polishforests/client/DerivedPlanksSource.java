package pl.polishforests.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.atlas.SpriteResourceLoader;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import pl.polishforests.PolishForests;
import pl.polishforests.block.PlanksTexture;
import pl.polishforests.block.WoodSet;

/**
 * The atlas sprite source of the planks of the wood sets (step P2 of docs/04-pionowy-wycinek.md, decision U-8): for every
 * {@link WoodSet} the sprite {@code polishforests:block/<wood>_planks} is computed while the block atlas loads from the
 * vanilla planks texture of the game ({@link WoodSet#planksBase()}, read through the resource manager, so a resource pack
 * that changes the vanilla planks changes ours too) by {@link PlanksTexture#derive}; no pixel of vanilla is in the mod.
 *
 * <p>Declared in {@code assets/minecraft/atlases/blocks.json} of the mod as {@code {"type": "polishforests:derived_planks"}},
 * which the atlas reads after vanilla's own sources (the resource stack of the atlas definition), so the derived sprite
 * replaces the fallback texture of the same id from {@code tools/art} that vanilla's directory source has listed. The
 * fallback stays when the base cannot be read, and a resource pack that brings its own texture for our planks keeps it
 * (more than one pack has {@code textures/block/<wood>_planks.png}: nothing derived). The type is registered in vanilla's
 * sprite sources by {@code SpriteSourcesMixin}.
 */
public record DerivedPlanksSource() implements SpriteSource {
	private static final Logger LOG = LogUtils.getLogger();
	public static final Identifier TYPE = PolishForests.id("derived_planks");
	public static final MapCodec<DerivedPlanksSource> MAP_CODEC = MapCodec.unit(DerivedPlanksSource::new);

	/** What the last atlas load did for every planks sprite (read by the client game test, step P2). */
	private static final Map<Identifier, Result> RESULTS = new ConcurrentHashMap<>();

	/**
	 * The outcome of one planks sprite: {@code derived} when computed from {@code base} (else the fallback or a pack's
	 * texture stays), the size and a hash of the pixels ({@link #hash}).
	 */
	public record Result(Identifier sprite, Identifier base, boolean derived, String reason, int width, int height, long hash) {
	}

	public static Map<Identifier, Result> results() {
		return Collections.unmodifiableMap(RESULTS);
	}

	/** The sprite id of the planks of a wood set. */
	public static Identifier sprite(WoodSet set) {
		return PolishForests.id("block/" + set.planksId());
	}

	/** The vanilla planks texture a wood set derives from. */
	public static Identifier base(WoodSet set) {
		return Identifier.withDefaultNamespace("block/" + set.planksBase());
	}

	@Override
	public void run(ResourceManager resources, SpriteSource.Output output) {
		for (WoodSet set : WoodSet.values()) {
			Identifier sprite = sprite(set);
			Identifier base = base(set);
			List<Resource> own = resources.getResourceStack(TEXTURE_ID_CONVERTER.idToFile(sprite));
			if (own.size() > 1) {
				RESULTS.put(sprite, new Result(sprite, base, false, "a resource pack has its own texture", 0, 0, 0));
				continue;
			}
			Optional<Resource> resource = resources.getResource(TEXTURE_ID_CONVERTER.idToFile(base));
			if (resource.isEmpty()) {
				LOG.warn("[planks] base texture {} not found, {} keeps the fallback texture", base, sprite);
				RESULTS.put(sprite, new Result(sprite, base, false, "base texture not found", 0, 0, 0));
				continue;
			}
			output.add(sprite, new Loader(sprite, base, resource.get(), own.isEmpty() ? null : own.getFirst(), set.planksStyle()));
		}
	}

	@Override
	public MapCodec<DerivedPlanksSource> codec() {
		return MAP_CODEC;
	}

	/** A hash of ARGB pixels (FNV-1a over the ints), to compare the sprite with a recomputation in the game test. */
	public static long hash(int[] argb) {
		long h = 0xcbf29ce484222325L;
		for (int p : argb) {
			h ^= p;
			h *= 0x100000001b3L;
		}
		return h;
	}

	/** Reads an image resource as ARGB pixels ({@code [width, height, pixels…]} in {@link Pixels}). */
	public static Pixels read(Resource resource) throws IOException {
		try (InputStream in = resource.open(); NativeImage image = NativeImage.read(in)) {
			return new Pixels(image.getWidth(), image.getHeight(), image.getPixels());
		}
	}

	/** ARGB pixels of an image, row by row. */
	public record Pixels(int width, int height, int[] argb) {
	}

	/** Loads the base, derives the planks and gives the sprite; on a failure the fallback texture (if any). */
	private record Loader(Identifier sprite, Identifier base, Resource resource, @Nullable Resource fallback, PlanksTexture.Style style)
			implements SpriteSource.DiscardableLoader {
		@Override
		public @Nullable SpriteContents get(SpriteResourceLoader loader) {
			try {
				Pixels in = read(resource);
				int h = PlanksTexture.frameHeight(in.width(), in.height());
				int[] out = PlanksTexture.derive(in.argb(), in.width(), in.height(), style);
				NativeImage image = new NativeImage(in.width(), h, false);
				for (int y = 0; y < h; y++) {
					for (int x = 0; x < in.width(); x++) {
						image.setPixel(x, y, out[y * in.width() + x]);
					}
				}
				RESULTS.put(sprite, new Result(sprite, base, true, "derived from " + resource.sourcePackId(), in.width(), h, hash(out)));
				return new SpriteContents(sprite, new FrameSize(in.width(), h), image);
			} catch (IOException | RuntimeException e) {
				LOG.error("[planks] could not derive {} from {}", sprite, base, e);
				RESULTS.put(sprite, new Result(sprite, base, false, "failed: " + e, 0, 0, 0));
				return fallback != null ? loader.loadSprite(sprite, fallback) : null;
			}
		}
	}
}
