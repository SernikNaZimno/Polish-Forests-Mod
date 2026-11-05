package pl.polishforests.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import pl.polishforests.worldgen.feature.ProceduralTreeFeature;
import pl.polishforests.worldgen.tree.TreeVoxels;

/**
 * The wood sets of step P2 (decision U-8): the blocks and their families, the logs the trunks drop (the generation keeps
 * its vanilla full logs, P2-c), and the generated data (datagen in {@code src/main/generated}: loot, recipes, tags,
 * names) and textures (the fallback planks of {@code tools/art} in the palette of the derived planks, the atlas source).
 */
class WoodSetTest {
	private static final Path GENERATED = Path.of("src/main/generated");
	private static final Path RESOURCES = Path.of("src/main/resources");

	@BeforeAll
	static void registries() {
		GameRegistries.bootstrap();
	}

	@Test
	void blocksOfEveryWoodSet() {
		for (WoodSet set : WoodSet.values()) {
			WoodBlocks w = ModBlocks.wood(set);
			String p = set.prefix();
			List<String> ids = List.of(p + "_log", p + "_wood", "stripped_" + p + "_log", "stripped_" + p + "_wood", p + "_planks",
					p + "_stairs", p + "_slab", p + "_fence", p + "_fence_gate", p + "_pressure_plate", p + "_button");
			for (int i = 0; i < ids.size(); i++) {
				Block b = w.all().get(i);
				assertEquals(ids.get(i), BuiltInRegistries.BLOCK.getKey(b).getPath());
				assertSame(b, ((net.minecraft.world.item.BlockItem) b.asItem()).getBlock(), ids.get(i));
			}
			for (Block b : w.fullLogs()) {
				assertTrue(b instanceof RotatedPillarBlock, BuiltInRegistries.BLOCK.getKey(b).toString());
			}
			assertTrue(w.stairs() instanceof StairBlock && w.slab() instanceof SlabBlock && w.fence() instanceof FenceBlock
					&& w.fenceGate() instanceof FenceGateBlock && w.pressurePlate() instanceof PressurePlateBlock
					&& w.button() instanceof ButtonBlock, p);
			assertEquals(set.planksId(), BuiltInRegistries.BLOCK.getKey(w.planks()).getPath());
		}
		WoodBlocks pine = ModBlocks.wood(WoodSet.SCOTS_PINE);
		assertSame(ModBlocks.pineLog(), pine.log());
		assertSame(ModBlocks.pinePlanks(), pine.planks());
		assertSame(ModBlocks.strippedPineWood(), pine.strippedWood());
	}

	@Test
	void trunksDropTheLogsOfTheirWood() {
		for (WoodSet set : WoodSet.values()) {
			if (set == WoodSet.SCOTS_PINE) {
				continue;
			}
			assertSame(ModBlocks.wood(set).log(), set.trunk().log(), set.toString());
			assertSame(ModBlocks.wood(set).strippedLog(), set.stripped().log(), set.toString());
			assertEquals(set.species(), set.stripped().species());
			// The generation writes the vanilla full logs as before P2 (P2-c); the species of steps F2 and F3, which had no
			// generation before, write their own logs (F2-d).
			Block full = ProceduralTreeFeature.wood(set.species(), TreeVoxels.LOWER, TreeVoxels.FULL, net.minecraft.core.Direction.UP).getBlock();
			assertSame(set.trunk().fullLog(), full);
			if (set == WoodSet.BLACK_ALDER || set == WoodSet.ELM || set == WoodSet.FIR || set == WoodSet.SYCAMORE_MAPLE) {
				assertSame(ModBlocks.wood(set).log(), full, set.toString());
			} else {
				assertNotSame(ModBlocks.wood(set).log(), full);
			}
		}
		assertSame(Blocks.PALE_OAK_LOG, TreeTrunk.HORNBEAM.fullLog());
		assertSame(Blocks.OAK_LOG, TreeTrunk.ASH.fullLog());
		assertSame(Blocks.OAK_LOG, TreeTrunk.OAK.log());
		assertSame(Blocks.STRIPPED_OAK_LOG, StrippedTrunk.OAK.log());
	}

	@Test
	void lootOfTrunksAndOfTheWoodBlocks() throws IOException {
		for (WoodSet set : WoodSet.values()) {
			String p = set.prefix();
			String log = "polishforests:" + p + "_log";
			if (set != WoodSet.SCOTS_PINE) {
				for (String trunk : List.of(set.trunk().id(), set.stripped().id(), p + "_root_flare")) {
					String table = read(GENERATED.resolve("data/polishforests/loot_table/blocks/" + trunk + ".json"));
					String expected = trunk.startsWith("stripped_") ? "polishforests:stripped_" + p + "_log" : log;
					assertTrue(table.contains("\"" + expected + "\""), trunk + " drops " + expected);
					assertTrue(table.contains("polishforests:trunk_base"), trunk + ": the stem base gives logs (S1)");
					assertTrue(!table.contains("minecraft:pale_oak_log") && !table.contains("minecraft:oak_log"), trunk);
				}
			}
			for (Block b : ModBlocks.wood(set).all()) {
				String id = BuiltInRegistries.BLOCK.getKey(b).getPath();
				String table = read(GENERATED.resolve("data/polishforests/loot_table/blocks/" + id + ".json"));
				assertTrue(table.contains("\"polishforests:" + id + "\""), id + " drops itself");
			}
			assertTrue(read(GENERATED.resolve("data/polishforests/loot_table/blocks/" + p + "_slab.json")).contains("\"double\""));
		}
	}

	@Test
	void recipesOfEveryWoodSet() throws IOException {
		for (WoodSet set : WoodSet.values()) {
			String p = set.prefix();
			JsonObject planks = json(GENERATED.resolve("data/polishforests/recipe/" + p + "_planks.json"));
			assertEquals("#polishforests:" + p + "_full_logs", planks.getAsJsonArray("ingredients").get(0).getAsString());
			assertEquals(4, planks.getAsJsonObject("result").get("count").getAsInt());
			assertEquals("polishforests:" + p + "_log",
					json(GENERATED.resolve("data/polishforests/recipe/" + p + "_wood.json")).getAsJsonObject("key").get("#").getAsString());
			assertEquals("polishforests:stripped_" + p + "_log",
					json(GENERATED.resolve("data/polishforests/recipe/stripped_" + p + "_wood.json")).getAsJsonObject("key").get("#").getAsString());
			for (String product : List.of("stairs", "slab", "fence", "fence_gate", "pressure_plate", "button")) {
				JsonObject r = json(GENERATED.resolve("data/polishforests/recipe/" + p + "_" + product + ".json"));
				assertTrue(r.toString().contains("polishforests:" + p + "_planks"), p + "_" + product + " is made of the planks");
				assertEquals("polishforests:" + p + "_" + product, r.getAsJsonObject("result").get("id").getAsString());
				assertEquals("wooden_" + product, r.get("group").getAsString());
			}
			// The full logs make the planks, the thin trunks (silk touch) do not (V2-d).
			String full = read(GENERATED.resolve("data/polishforests/tags/item/" + p + "_full_logs.json"));
			for (Block b : ModBlocks.wood(set).fullLogs()) {
				assertTrue(full.contains(BuiltInRegistries.BLOCK.getKey(b).toString()), p);
			}
			assertTrue(!full.contains("_trunk"), p);
		}
	}

	@Test
	void tagsAsVanillaWood() throws IOException {
		String[][] tags = {{"planks", "_planks"}, {"wooden_stairs", "_stairs"}, {"wooden_slabs", "_slab"}, {"wooden_fences", "_fence"},
				{"fence_gates", "_fence_gate"}, {"wooden_pressure_plates", "_pressure_plate"}, {"wooden_buttons", "_button"}};
		for (String kind : List.of("block", "item")) {
			for (String[] t : tags) {
				String tag = read(GENERATED.resolve("data/minecraft/tags/" + kind + "/" + t[0] + ".json"));
				for (WoodSet set : WoodSet.values()) {
					assertTrue(tag.contains("\"polishforests:" + set.prefix() + t[1] + "\""), kind + " #" + t[0] + ": " + set);
				}
			}
		}
		String natural = read(GENERATED.resolve("data/minecraft/tags/block/overworld_natural_logs.json"));
		String burn = read(GENERATED.resolve("data/minecraft/tags/item/logs_that_burn.json"));
		for (WoodSet set : WoodSet.values()) {
			assertTrue(natural.contains("\"polishforests:" + set.prefix() + "_log\""), set.toString());
			assertTrue(read(GENERATED.resolve("data/polishforests/tags/block/" + set.species().id() + "_logs.json"))
					.contains("#polishforests:" + set.prefix() + "_full_logs"), set.toString());
			assertTrue(burn.contains("#polishforests:" + set.species().id() + "_logs"), set.toString());
		}
	}

	@Test
	void namesInBothLanguages() throws IOException {
		JsonObject en = json(GENERATED.resolve("assets/polishforests/lang/en_us.json"));
		JsonObject pl = json(GENERATED.resolve("assets/polishforests/lang/pl_pl.json"));
		assertEquals("Hornbeam Planks", en.get("block.polishforests.hornbeam_planks").getAsString());
		assertEquals("Deski grabowe", pl.get("block.polishforests.hornbeam_planks").getAsString());
		assertEquals("Płot klonowy", pl.get("block.polishforests.norway_maple_fence").getAsString());
		assertEquals("Okorowana kłoda jesionowa", pl.get("block.polishforests.stripped_ash_log").getAsString());
		assertEquals("Kłoda sosnowa", pl.get("block.polishforests.scots_pine_log").getAsString());
		for (WoodSet set : WoodSet.values()) {
			for (Block b : ModBlocks.wood(set).all()) {
				String key = b.getDescriptionId();
				assertTrue(en.has(key) && pl.has(key), key);
			}
		}
	}

	/** The fallback planks of tools/art use the palette of the derived planks only; the atlas declares the derived source. */
	@Test
	void fallbackPlanksInThePaletteAndTheAtlasSource() throws IOException {
		for (WoodSet set : WoodSet.values()) {
			Set<Integer> palette = new HashSet<>();
			for (int c : set.planksPalette()) {
				palette.add(c);
			}
			BufferedImage img = ImageIO.read(RESOURCES.resolve("assets/polishforests/textures/block/" + set.planksId() + ".png").toFile());
			assertEquals(16, img.getWidth());
			for (int y = 0; y < img.getHeight(); y++) {
				for (int x = 0; x < img.getWidth(); x++) {
					int rgb = img.getRGB(x, y) & 0xffffff;
					assertTrue(palette.contains(rgb), set + ": fallback color " + Integer.toHexString(rgb) + " not in WoodSet's palette");
				}
			}
			for (String top : List.of(set.prefix() + "_log_top", "stripped_" + set.prefix() + "_log_top")) {
				assertTrue(Files.exists(RESOURCES.resolve("assets/polishforests/textures/block/" + top + ".png")), top);
			}
		}
		assertTrue(read(RESOURCES.resolve("assets/minecraft/atlases/blocks.json")).contains("\"polishforests:derived_planks\""));
	}

	private static String read(Path p) throws IOException {
		return Files.readString(p);
	}

	private static JsonObject json(Path p) throws IOException {
		return JsonParser.parseString(read(p)).getAsJsonObject();
	}
}
