package pl.polishforests.gametest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import pl.polishforests.PolishForests;
import pl.polishforests.block.ModBlocks;
import pl.polishforests.block.PlanksTexture;
import pl.polishforests.block.WoodBlocks;
import pl.polishforests.block.WoodSet;
import pl.polishforests.client.DerivedPlanksSource;

/**
 * The planks of the wood sets in the block atlas (step P2 of docs/04-pionowy-wycinek.md; {@code -Pgametest=slice
 * -Psites=planks}, under a minute, no world). Numbers only, no screenshots (the look is the user's checklist, G.36 U-1):
 * <ul>
 * <li>every planks sprite {@code polishforests:block/<wood>_planks} is in the block atlas and is not the missing texture;
 * it was derived by {@code DerivedPlanksSource} from the vanilla planks of the game (not the fallback texture), its size is
 * the size of the base, and a recomputation of {@link PlanksTexture#derive} on the base read through the resource manager
 * gives the same pixels (hash);</li>
 * <li>every state of every block of every wood set has a block model that is not the missing model, its particle sprite is
 * not the missing texture (the products and the planks show the planks sprite), and every block item has its item model
 * definition.</li>
 * </ul>
 * Results in {@code build/planks/planks.txt}; any problem fails the test.
 */
final class PlankSamples {
	private PlankSamples() {
	}

	static void run(ClientGameTestContext context) {
		List<String> lines = new ArrayList<>();
		List<String> problems = new ArrayList<>();
		lines.add("# Step P2: the derived planks in the block atlas, the models of the wood sets");
		context.waitTicks(20);
		context.runOnClient(mc -> {
			TextureAtlas atlas = mc.getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS);
			Identifier missing = MissingTextureAtlasSprite.getLocation();
			for (WoodSet set : WoodSet.values()) {
				Identifier id = DerivedPlanksSource.sprite(set);
				TextureAtlasSprite sprite = atlas.getSprite(id);
				DerivedPlanksSource.Result r = DerivedPlanksSource.results().get(id);
				String line = String.format(Locale.ROOT, "%s sprite %s %dx%d", set, sprite.contents().name(),
						sprite.contents().width(), sprite.contents().height());
				if (sprite.contents().name().equals(missing) || !sprite.contents().name().equals(id)) {
					problems.add(set + ": the planks sprite " + id + " is the missing texture");
				}
				if (r == null || !r.derived()) {
					problems.add(set + ": the planks were not derived (" + (r == null ? "no result" : r.reason()) + ")");
					lines.add(line + " NOT DERIVED");
					continue;
				}
				line += " " + r.reason() + " hash " + Long.toHexString(r.hash());
				Optional<Resource> base = mc.getResourceManager().getResource(SpriteSource.TEXTURE_ID_CONVERTER.idToFile(r.base()));
				if (base.isEmpty()) {
					problems.add(set + ": base " + r.base() + " not found again");
				} else {
					try {
						DerivedPlanksSource.Pixels px = DerivedPlanksSource.read(base.get());
						int[] again = PlanksTexture.derive(px.argb(), px.width(), px.height(), set.planksStyle());
						if (DerivedPlanksSource.hash(again) != r.hash()) {
							problems.add(set + ": the sprite differs from a recomputation from " + r.base());
						}
						if (sprite.contents().width() != px.width() || sprite.contents().height() != PlanksTexture.frameHeight(px.width(), px.height())) {
							problems.add(set + ": sprite size " + sprite.contents().width() + "x" + sprite.contents().height()
									+ " is not the base's " + px.width());
						}
						Optional<Resource> fallback = mc.getResourceManager().getResource(SpriteSource.TEXTURE_ID_CONVERTER.idToFile(id));
						if (fallback.isPresent()) {
							DerivedPlanksSource.Pixels f = DerivedPlanksSource.read(fallback.get());
							boolean same = f.width() == px.width() && DerivedPlanksSource.hash(f.argb()) == r.hash();
							line += same ? " (equals the fallback!)" : " (not the fallback)";
							if (same) {
								problems.add(set + ": the derived planks equal the fallback texture");
							}
						}
					} catch (IOException e) {
						problems.add(set + ": " + e);
					}
				}
				lines.add(line);
			}
			BlockStateModelSet models = mc.getModelManager().getBlockStateModelSet();
			int states = 0;
			for (WoodSet set : WoodSet.values()) {
				WoodBlocks w = ModBlocks.wood(set);
				Identifier planks = DerivedPlanksSource.sprite(set);
				for (Block b : w.all()) {
					Identifier key = BuiltInRegistries.BLOCK.getKey(b);
					for (BlockState s : b.getStateDefinition().getPossibleStates()) {
						states++;
						if (models.get(s) == models.missingModel()) {
							problems.add(key + ": missing block model for " + s);
							continue;
						}
						Identifier particle = models.getParticleMaterial(s).sprite().contents().name();
						if (particle.equals(missing)) {
							problems.add(key + ": the particle sprite of " + s + " is the missing texture");
						}
						if ((b == w.planks() || w.products().contains(b)) && !particle.equals(planks)) {
							problems.add(key + ": particle " + particle + " instead of the planks " + planks);
						}
					}
					Identifier item = PolishForests.id("items/" + key.getPath() + ".json");
					if (mc.getResourceManager().getResource(item).isEmpty()) {
						problems.add(key + ": no item model definition " + item);
					}
				}
			}
			lines.add("block states checked: " + states);
		});
		lines.add("# problems: " + problems.size());
		lines.addAll(problems);
		write(lines);
		PolishForests.LOG.info("[planks] {} problems, see build/planks/planks.txt", problems.size());
		if (!problems.isEmpty()) {
			throw new AssertionError("[planks] " + problems.size() + " problems, see build/planks/planks.txt: " + problems.getFirst());
		}
	}

	private static void write(List<String> lines) {
		Path dir = Path.of(System.getProperty("polishforests.felling.out", "build/felling")).resolveSibling("planks");
		try {
			Files.createDirectories(dir);
			Files.write(dir.resolve("planks.txt"), lines, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
