package pl.polishforests.gametest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.AtlasIds;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CeilingHangingSignBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.ShelfBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallHangingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ShelfBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import pl.polishforests.PolishForests;
import pl.polishforests.block.ModBlocks;
import pl.polishforests.block.WoodParts;
import pl.polishforests.block.WoodPartTexture;
import pl.polishforests.block.WoodSet;
import pl.polishforests.client.DerivedPlanksSource;
import pl.polishforests.client.DerivedWoodSource;

/**
 * The doors, trapdoors, signs, hanging signs and shelves of the wood sets (step FA7 of docs/04-pionowy-wycinek.md), part of
 * {@code -Pgametest=slice -Psites=planks} ({@link PlankSamples}). Numbers only, no judgment of the look (G.36 U-1):
 * <ul>
 * <li>every derived texture ({@link DerivedWoodSource}: the block sprites of the door top and bottom, trapdoor, sign, hanging
 * sign and shelf in the block atlas, the flat item sprites of the door, sign and hanging sign in the item atlas, the sign
 * and hanging sign edit screen backgrounds as textures of the texture manager) exists, is not the missing texture, was
 * derived from vanilla's texture of the base wood, has its size, and a recomputation from the resources gives the same
 * pixels (hash);</li>
 * <li>every state of every part has a block model and a particle that are not missing, every part with an item has its item
 * model definition;</li>
 * <li>in a flat test world, a table of every part of every set on a platform: the doors and trapdoors open by hand (empty
 * hand, as a player), every sign and hanging sign takes a text, every shelf holds three items; one screenshot of the table
 * ({@code wood_parts_table}, the only screenshot of the step); then the world is saved, closed and opened again, and the
 * doors and trapdoors are still open, the texts and the items still there.</li>
 * </ul>
 */
final class WoodPartSamples {
	/** Height of the platform of the table (the flat test world's ground is far below). */
	static final int Y = 120;
	/** Columns of the sets along x, rows of the parts along z. */
	private static final int STEP = 3;

	private WoodPartSamples() {
	}

	/** The atlas, screen texture and model checks (on the client thread). */
	static void client(Minecraft mc, List<String> lines, List<String> problems) {
		TextureAtlas blocks = mc.getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS);
		TextureAtlas items = mc.getAtlasManager().getAtlasOrThrow(AtlasIds.ITEMS);
		Identifier missing = MissingTextureAtlasSprite.getLocation();
		int derived = 0;
		for (WoodSet set : WoodSet.values()) {
			for (WoodPartTexture.Part part : WoodPartTexture.Part.values()) {
				Identifier id = DerivedWoodSource.id(set, part);
				DerivedWoodSource.Result r = DerivedWoodSource.results().get(id);
				int width;
				int height;
				if (part.kind() == WoodPartTexture.Kind.SCREEN) {
					AbstractTexture texture = mc.getTextureManager().getTexture(id);
					if (!(texture instanceof DynamicTexture dynamic) || dynamic.getPixels() == null) {
						problems.add(set + " " + part + ": the screen texture " + id + " is not the derived dynamic texture (" + texture + ")");
						continue;
					}
					width = dynamic.getPixels().getWidth();
					height = dynamic.getPixels().getHeight();
				} else {
					TextureAtlas atlas = part.kind() == WoodPartTexture.Kind.BLOCK ? blocks : items;
					TextureAtlasSprite sprite = atlas.getSprite(id);
					if (sprite.contents().name().equals(missing) || !sprite.contents().name().equals(id)) {
						problems.add(set + " " + part + ": the sprite " + id + " is the missing texture");
						continue;
					}
					width = sprite.contents().width();
					height = sprite.contents().height();
				}
				if (r == null || !r.derived()) {
					problems.add(set + " " + part + ": not derived (" + (r == null ? "no result" : r.reason()) + ")");
					continue;
				}
				Optional<Resource> base = mc.getResourceManager().getResource(DerivedWoodSource.file(r.base(), part));
				Optional<Resource> planks = mc.getResourceManager().getResource(DerivedWoodSource.planksFile(set));
				if (base.isEmpty() || planks.isEmpty()) {
					problems.add(set + " " + part + ": base " + r.base() + " not found again");
					continue;
				}
				try {
					DerivedPlanksSource.Pixels again = DerivedWoodSource.derive(base.get(), planks.get(), set.planksPalette());
					if (DerivedPlanksSource.hash(again.argb()) != r.hash()) {
						problems.add(set + " " + part + ": differs from a recomputation from " + r.base());
					}
					if (again.width() != width || again.height() != height) {
						problems.add(set + " " + part + ": size " + width + "x" + height + " is not the base's " + again.width() + "x"
								+ again.height());
					}
				} catch (IOException e) {
					problems.add(set + " " + part + ": " + e);
				}
				derived++;
			}
		}
		lines.add(String.format(Locale.ROOT, "FA7 derived textures: %d of %d (block and item sprites, screen textures), e.g. %s",
				derived, WoodSet.values().length * WoodPartTexture.Part.values().length,
				DerivedWoodSource.results().get(DerivedWoodSource.id(WoodSet.BEECH, WoodPartTexture.Part.DOOR_TOP))));
		BlockStateModelSet models = mc.getModelManager().getBlockStateModelSet();
		int states = 0;
		for (WoodSet set : WoodSet.values()) {
			WoodParts w = ModBlocks.woodParts(set);
			for (Block b : w.all()) {
				Identifier key = BuiltInRegistries.BLOCK.getKey(b);
				for (BlockState s : b.getStateDefinition().getPossibleStates()) {
					states++;
					if (models.get(s) == models.missingModel()) {
						problems.add(key + ": missing block model for " + s);
					} else if (models.getParticleMaterial(s).sprite().contents().name().equals(missing)) {
						problems.add(key + ": the particle sprite of " + s + " is the missing texture");
					}
				}
			}
			for (Block b : w.withItems()) {
				Identifier key = BuiltInRegistries.BLOCK.getKey(b);
				if (mc.getResourceManager().getResource(PolishForests.id("items/" + key.getPath() + ".json")).isEmpty()) {
					problems.add(key + ": no item model definition");
				}
			}
		}
		lines.add("FA7 block states of the parts checked: " + states);
	}

	/** The table in a flat test world: opening, texts, items, the screenshot, then save, reopen and check again. */
	static void world(ClientGameTestContext context, List<String> lines, List<String> problems) {
		TestWorldSave save;
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			FellingSamples.setUp(sp);
			sp.getServer().runCommand("tp @a 0 " + (Y + 3) + " 0");
			context.waitTicks(40);
			lines.addAll(sp.getServer().computeOnServer(s -> build(s, problems)));
			int width = STEP * WoodSet.values().length;
			sp.getServer().runCommand(String.format(Locale.ROOT, "tp @a %.1f %d %.1f 180 28", width / 2.0, Y + 16, 38.0));
			FellingSamples.hideHud(context);
			context.waitTicks(80);
			context.takeScreenshot(TestScreenshotOptions.of("wood_parts_table").disableCounterPrefix().withSize(1600, 900));
			save = sp.getWorldSave();
		}
		try (TestSingleplayerContext sp = save.open()) {
			context.waitTicks(60);
			lines.add(sp.getServer().computeOnServer(s -> reopened(s, problems)));
		}
	}

	/** The z of the row of a part. */
	private static int row(int part) {
		return 4 * part;
	}

	private static BlockPos at(int set, int part, int dy) {
		return new BlockPos(STEP * set, Y + dy, row(part));
	}

	private static List<String> build(MinecraftServer server, List<String> problems) {
		ServerLevel level = server.overworld();
		ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
		WoodSet[] sets = WoodSet.values();
		// The platform under the table (stone), the backs of the wall signs and the ceilings of the hanging signs (planks).
		for (int x = -2; x <= STEP * sets.length + 1; x++) {
			for (int z = -2; z <= row(6) + 2; z++) {
				level.setBlock(new BlockPos(x, Y, z), Blocks.STONE.defaultBlockState(), 2);
			}
		}
		int opened = 0;
		for (int i = 0; i < sets.length; i++) {
			WoodSet set = sets[i];
			WoodParts w = ModBlocks.woodParts(set);
			Block planks = ModBlocks.wood(set).planks();
			// Row 0: the door (both halves), opened by hand.
			BlockPos door = at(i, 0, 1);
			BlockState lower = w.door().defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH);
			level.setBlock(door, lower.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), 3);
			level.setBlock(door.above(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
			use(player, level, door);
			if (!level.getBlockState(door).getValue(DoorBlock.OPEN) || !level.getBlockState(door.above()).getValue(DoorBlock.OPEN)) {
				problems.add(set + ": the door did not open by hand: " + level.getBlockState(door));
			} else {
				opened++;
			}
			// Row 1: the trapdoor, opened by hand.
			BlockPos trapdoor = at(i, 1, 1);
			level.setBlock(trapdoor, w.trapdoor().defaultBlockState().setValue(TrapDoorBlock.FACING, Direction.SOUTH), 3);
			use(player, level, trapdoor);
			if (!level.getBlockState(trapdoor).getValue(TrapDoorBlock.OPEN)) {
				problems.add(set + ": the trapdoor did not open by hand: " + level.getBlockState(trapdoor));
			} else {
				opened++;
			}
			// Row 2: the standing sign; row 3: the wall sign on a planks block; row 4: the hanging sign under a planks block;
			// row 5: the wall hanging sign beside a planks block (it hangs from the side facing east).
			BlockPos sign = at(i, 2, 1);
			level.setBlock(sign, w.sign().defaultBlockState().setValue(StandingSignBlock.ROTATION, 0), 3);
			BlockPos wallBack = at(i, 3, 1);
			level.setBlock(wallBack, planks.defaultBlockState(), 3);
			BlockPos wallSign = wallBack.south();
			level.setBlock(wallSign, w.wallSign().defaultBlockState().setValue(WallSignBlock.FACING, Direction.SOUTH), 3);
			BlockPos ceiling = at(i, 4, 3);
			level.setBlock(ceiling, planks.defaultBlockState(), 3);
			BlockPos hanging = ceiling.below();
			level.setBlock(hanging, w.hangingSign().defaultBlockState().setValue(CeilingHangingSignBlock.ROTATION, 0), 3);
			BlockPos side = at(i, 5, 2);
			level.setBlock(side, planks.defaultBlockState(), 3);
			BlockPos wallHanging = side.east();
			level.setBlock(wallHanging, w.wallHangingSign().defaultBlockState().setValue(WallHangingSignBlock.FACING, Direction.SOUTH), 3);
			for (BlockPos p : List.of(sign, wallSign, hanging, wallHanging)) {
				if (!(level.getBlockEntity(p) instanceof SignBlockEntity entity)) {
					problems.add(set + ": no sign block entity at " + p + " (" + level.getBlockState(p) + ")");
					continue;
				}
				entity.updateText(t -> text(set, p), SignTextSlot.FRONT);
				entity.setChanged();
			}
			// Row 6: the shelf with three items.
			BlockPos shelf = at(i, 6, 1);
			level.setBlock(shelf, w.shelf().defaultBlockState().setValue(ShelfBlock.FACING, Direction.SOUTH), 3);
			if (level.getBlockEntity(shelf) instanceof ShelfBlockEntity entity) {
				List<ItemStack> held = shelfItems(set);
				for (int slot = 0; slot < held.size(); slot++) {
					entity.setItem(slot, held.get(slot).copy());
				}
				entity.setChanged();
			} else {
				problems.add(set + ": no shelf block entity at " + shelf + " (" + level.getBlockState(shelf) + ")");
			}
		}
		List<String> out = new ArrayList<>();
		out.add(String.format(Locale.ROOT, "FA7 table: %d sets, %d of %d doors and trapdoors opened by hand", sets.length, opened,
				2 * sets.length));
		return out;
	}

	private static String reopened(MinecraftServer server, List<String> problems) {
		ServerLevel level = server.overworld();
		WoodSet[] sets = WoodSet.values();
		int texts = 0;
		int shelves = 0;
		int open = 0;
		for (int i = 0; i < sets.length; i++) {
			WoodSet set = sets[i];
			WoodParts w = ModBlocks.woodParts(set);
			BlockState door = level.getBlockState(at(i, 0, 1));
			BlockState trapdoor = level.getBlockState(at(i, 1, 1));
			if (door.getBlock() == w.door() && door.getValue(DoorBlock.OPEN) && trapdoor.getBlock() == w.trapdoor()
					&& trapdoor.getValue(TrapDoorBlock.OPEN)) {
				open++;
			} else {
				problems.add(set + ": after reopening the door or trapdoor is not there or not open: " + door + ", " + trapdoor);
			}
			BlockPos[] signs = {at(i, 2, 1), at(i, 3, 1).south(), at(i, 4, 2), at(i, 5, 2).east()};
			Block[] blocks = {w.sign(), w.wallSign(), w.hangingSign(), w.wallHangingSign()};
			for (int k = 0; k < signs.length; k++) {
				BlockEntity e = level.getBlockEntity(signs[k]);
				if (level.getBlockState(signs[k]).getBlock() != blocks[k] || !(e instanceof SignBlockEntity entity)) {
					problems.add(set + ": after reopening no " + BuiltInRegistries.BLOCK.getKey(blocks[k]) + " with its text at " + signs[k]);
					continue;
				}
				String want = lines(text(set, signs[k]));
				String got = lines(entity.getText(SignTextSlot.FRONT));
				if (want.equals(got)) {
					texts++;
				} else {
					problems.add(set + ": after reopening the text of " + BuiltInRegistries.BLOCK.getKey(blocks[k]) + " is '" + got
							+ "' instead of '" + want + "'");
				}
			}
			BlockPos shelf = at(i, 6, 1);
			if (level.getBlockEntity(shelf) instanceof ShelfBlockEntity entity) {
				List<ItemStack> want = shelfItems(set);
				boolean same = true;
				for (int slot = 0; slot < want.size(); slot++) {
					same &= ItemStack.isSameItemSameComponents(want.get(slot), entity.getItem(slot))
							&& want.get(slot).getCount() == entity.getItem(slot).getCount();
				}
				if (same) {
					shelves++;
				} else {
					problems.add(set + ": after reopening the shelf holds " + entity.getItems());
				}
			} else {
				problems.add(set + ": after reopening no shelf at " + shelf);
			}
		}
		return String.format(Locale.ROOT, "FA7 after save and reopen: %d of %d doors and trapdoors open, %d of %d sign texts, %d of %d "
				+ "shelves with their items", 2 * open, 2 * sets.length, texts, 4 * sets.length, shelves, sets.length);
	}

	/** The text of a sign of the table: the set and the place (front side). */
	private static SignText text(WoodSet set, BlockPos p) {
		return SignText.EMPTY.asMutable().setLine(0, Component.literal(set.prefix()))
				.setLine(1, Component.literal("FA7 " + p.getX() + " " + p.getZ())).asImmutable();
	}

	private static String lines(SignText text) {
		StringBuilder b = new StringBuilder();
		for (Component c : text.getMessages(false)) {
			b.append(c.getString()).append('|');
		}
		return b.toString();
	}

	/** Three items of a shelf: the planks of the set, a book, two apples. */
	private static List<ItemStack> shelfItems(WoodSet set) {
		return List.of(new ItemStack(ModBlocks.wood(set).planks().asItem()), new ItemStack(Items.BOOK), new ItemStack(Items.APPLE, 2));
	}

	/** A use of the block with an empty hand (as a player's right click). */
	private static void use(ServerPlayer player, ServerLevel level, BlockPos pos) {
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.SOUTH, pos, false);
		player.gameMode.useItemOn(player, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit);
	}
}
