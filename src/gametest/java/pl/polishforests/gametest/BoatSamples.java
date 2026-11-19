package pl.polishforests.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.entity.vehicle.boat.ChestBoat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import pl.polishforests.block.AnthillBlock;
import pl.polishforests.block.ModBlocks;
import pl.polishforests.block.WoodSet;
import pl.polishforests.entity.WoodBoats;

/**
 * The boats and chest boats of the wood sets (step FA8 of docs/04-pionowy-wycinek.md), part of
 * {@code -Pgametest=slice -Psites=planks} ({@link PlankSamples}). Numbers only, no judgment of the look (G.36 U-1): in a
 * flat test world a pool of water two blocks deep on a platform south of the table of the parts, with a boat and a chest
 * boat of every set on it and a mound of anthill layers beside it (the block of the step in the same screenshot,
 * {@code boats_table}, the only screenshot of the step). After 100 ticks every boat floats (its bottom within
 * {@value #FLOAT_TOLERANCE} blocks of the water surface, not sunk, not removed); the player boards every boat by a use of it
 * (as a right click) and leaves it; every chest boat holds the items put into its chest. Then the world is saved, closed and
 * opened again, and every boat is still there, afloat, of its type, the chest boats with their items.
 */
final class BoatSamples {
	/** The platform (as the table of the parts). */
	static final int Y = WoodPartSamples.Y;
	/** The pool: rows of the boats along x, from this z. */
	private static final int Z0 = 40;
	private static final int STEP = 4;
	/** Water two blocks deep: the surface is the top of the block at Y + 2. */
	private static final double SURFACE = Y + 3;
	/** How far the bottom of a floating boat may lie from the water surface, blocks. */
	static final double FLOAT_TOLERANCE = 0.6;

	private BoatSamples() {
	}

	static void world(ClientGameTestContext context, List<String> lines, List<String> problems) {
		TestWorldSave save;
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			FellingSamples.setUp(sp);
			sp.getServer().runCommand(String.format(Locale.ROOT, "tp @a %d %d %d", STEP * WoodSet.values().length / 2, Y + 5, Z0 - 4));
			context.waitTicks(40);
			sp.getServer().runOnServer(BoatSamples::build);
			context.waitTicks(100);
			lines.addAll(sp.getServer().computeOnServer(s -> check(s, problems, false)));
			int width = STEP * WoodSet.values().length;
			sp.getServer().runCommand(String.format(Locale.ROOT, "tp @a %.1f %d %.1f 0 30", width / 2.0, Y + 12, Z0 - 14.0));
			FellingSamples.hideHud(context);
			context.waitTicks(60);
			context.takeScreenshot(TestScreenshotOptions.of("boats_table").disableCounterPrefix().withSize(1600, 900));
			save = sp.getWorldSave();
		}
		try (TestSingleplayerContext sp = save.open()) {
			context.waitTicks(80);
			lines.addAll(sp.getServer().computeOnServer(s -> check(s, problems, true)));
		}
	}

	private static BlockPos at(int set, int kind) {
		return new BlockPos(STEP * set, Y + 3, Z0 + 3 * kind);
	}

	/** The pool, the boats with the items of their chests, and the mound beside the pool. */
	private static void build(MinecraftServer server) {
		ServerLevel level = server.overworld();
		WoodSet[] sets = WoodSet.values();
		int x1 = STEP * sets.length + 1;
		for (int x = -3; x <= x1 + 1; x++) {
			for (int z = Z0 - 3; z <= Z0 + 6; z++) {
				boolean rim = x == -3 || x == x1 + 1 || z == Z0 - 3 || z == Z0 + 6;
				level.setBlock(new BlockPos(x, Y, z), Blocks.STONE.defaultBlockState(), 2);
				for (int y = 1; y <= 2; y++) {
					level.setBlock(new BlockPos(x, Y + y, z), (rim ? Blocks.STONE : Blocks.WATER).defaultBlockState(), 2);
				}
			}
		}
		// The mound beside the pool, west of it on the platform: a large dome of 3 x 3 columns (layers by column).
		int[][] mound = {{5, 10, 5}, {10, 16, 9}, {6, 9, 5}};
		for (int dx = 0; dx < 3; dx++) {
			for (int dz = 0; dz < 3; dz++) {
				BlockPos base = new BlockPos(-8 + dx, Y, Z0 + dz);
				level.setBlock(base, Blocks.PODZOL.defaultBlockState(), 2);
				int layers = mound[dx][dz];
				for (int y = 1; layers > 0; y++) {
					int here = Math.min(layers, AnthillBlock.MAX_LAYERS);
					level.setBlock(base.above(y), ModBlocks.anthill().defaultBlockState().setValue(AnthillBlock.LAYERS, here), 2);
					layers -= here;
				}
			}
		}
		for (int i = 0; i < sets.length; i++) {
			WoodBoats.Set s = WoodBoats.of(sets[i]);
			spawn(level, s.boat(), at(i, 0));
			AbstractBoat chest = spawn(level, s.chestBoat(), at(i, 1));
			if (chest instanceof ChestBoat c) {
				List<ItemStack> items = chestItems(sets[i]);
				for (int slot = 0; slot < items.size(); slot++) {
					c.setItem(slot, items.get(slot).copy());
				}
			}
		}
	}

	private static AbstractBoat spawn(ServerLevel level, EntityType<? extends AbstractBoat> type, BlockPos at) {
		AbstractBoat boat = type.create(level, EntitySpawnReason.COMMAND);
		if (boat == null) {
			return null;
		}
		boat.snapTo(at.getX() + 0.5, at.getY() + 0.2, at.getZ() + 0.5, 0, 0);
		level.addFreshEntity(boat);
		return boat;
	}

	/** Every boat in its place, afloat; before the save also boarded by the player; the chest boats with their items. */
	private static List<String> check(MinecraftServer server, List<String> problems, boolean reopened) {
		ServerLevel level = server.overworld();
		ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
		WoodSet[] sets = WoodSet.values();
		int present = 0;
		int afloat = 0;
		int boarded = 0;
		int chests = 0;
		String when = reopened ? "after reopening" : "before the save";
		double lowest = Double.MAX_VALUE;
		double highest = -Double.MAX_VALUE;
		for (int i = 0; i < sets.length; i++) {
			WoodBoats.Set s = WoodBoats.of(sets[i]);
			for (int kind = 0; kind < 2; kind++) {
				EntityType<?> type = kind == 0 ? s.boat() : s.chestBoat();
				BlockPos at = at(i, kind);
				List<AbstractBoat> found = new ArrayList<>(level.getEntitiesOfClass(AbstractBoat.class, new AABB(at).inflate(1.5, 2, 1.4),
						b -> b.getType() == type));
				String name = BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath();
				if (found.size() != 1) {
					problems.add(name + " " + when + ": " + found.size() + " boats of the type at " + at);
					continue;
				}
				present++;
				AbstractBoat boat = found.getFirst();
				double offset = boat.getY() - SURFACE;
				lowest = Math.min(lowest, offset);
				highest = Math.max(highest, offset);
				if (boat.isRemoved() || Math.abs(offset) > FLOAT_TOLERANCE || boat.isUnderWater()) {
					problems.add(String.format(Locale.ROOT, "%s %s: not afloat, bottom %.2f from the water surface%s", name, when, offset,
							boat.isUnderWater() ? ", under water" : ""));
				} else {
					afloat++;
				}
				if (!reopened) {
					player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
					player.snapTo(boat.getX(), boat.getY() + 1, boat.getZ());
					player.interactOn(boat, InteractionHand.MAIN_HAND, new Vec3(0, 0.3, 0));
					if (player.getVehicle() == boat) {
						boarded++;
					} else {
						problems.add(name + ": the player did not board it by a use (vehicle " + player.getVehicle() + ")");
					}
					player.stopRiding();
				}
				if (kind == 1) {
					if (boat instanceof ChestBoat c && holds(c, chestItems(sets[i]))) {
						chests++;
					} else {
						problems.add(name + " " + when + ": the chest does not hold its items");
					}
				}
			}
		}
		List<String> out = new ArrayList<>();
		out.add(String.format(Locale.ROOT, "FA8 boats %s: %d of %d present, %d afloat (bottom %.2f … %.2f from the surface), %d boarded, "
				+ "%d of %d chests with their items", when, present, 2 * sets.length, afloat, lowest, highest, boarded, chests, sets.length));
		return out;
	}

	private static boolean holds(ChestBoat c, List<ItemStack> want) {
		for (int slot = 0; slot < want.size(); slot++) {
			ItemStack got = c.getItem(slot);
			if (!ItemStack.isSameItemSameComponents(want.get(slot), got) || want.get(slot).getCount() != got.getCount()) {
				return false;
			}
		}
		return true;
	}

	/** The items of the chest of a set's chest boat: its planks, a stack of sticks, an apple. */
	private static List<ItemStack> chestItems(WoodSet set) {
		return List.of(new ItemStack(ModBlocks.wood(set).planks().asItem(), 5), new ItemStack(Items.STICK, 16), new ItemStack(Items.APPLE));
	}
}
