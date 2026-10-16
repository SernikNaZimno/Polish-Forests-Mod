package pl.polishforests.gametest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import pl.polishforests.PolishForests;
import pl.polishforests.block.ModBlocks;
import pl.polishforests.block.RegistrySnapshot;
import pl.polishforests.block.TreeTrunk;
import pl.polishforests.block.TrunkBlock;

/**
 * The sample board of the thin trunks (step V2 of docs/04-pionowy-wycinek.md, {@code -Pgametest=slice -Psites=samples}):
 * in a superflat world, on a stone platform whose first two block layers lie on both sides of a section boundary, the
 * test builds
 * <ul>
 * <li>standing columns of radius 1-7 (4 blocks, across the section boundary);
 * <li>for each of the 6 parent directions a row of radius 1-7: a thick host (radius 7) with a child of the radius reaching
 * into it and a second child of the same radius (joint r → r, the reach of the model out of the block and the tip);
 * <li>joints r + 1 → r on a column and elbows (the trunk turning sideways, with the same and with a thinner radius);
 * <li>waterlogged columns and a branch in a pool;
 * <li>a horizontal branch across a chunk boundary;
 * <li>a Scots pine built by hand from a pipe model (lower and upper trunk, branches of radius 1-2 with steps on the
 * diagonal, leaves at the branch tips);
 * </ul>
 * and takes screenshots ({@code samples_<renderer>_<view>}, renderer {@code sodium} with {@code -Poptimization}, else
 * {@code vanilla}) at noon with smooth lighting (ambient occlusion) on, one at night lit by lanterns, the crosshair
 * outline with the cracks and hit particles while a survival player breaks a trunk, and the destroy particles. It also
 * checks that the registry of the game equals {@code src/test/resources/registry_snapshot.txt} (decision PW-11), that every
 * trunk state has a model and a bark sprite on the client, and logs the number of block states of the mod and of the
 * game.
 */
final class TrunkSamples {
	/** Screenshot size. */
	private static final int[] SIZE = {1280, 720};
	private static final int NOON = 6000;
	private static final int NIGHT = 18000;

	/** The placed blocks of the board (server thread writes, test thread reads after the build). */
	private static final Map<BlockPos, BlockState> BOARD = new LinkedHashMap<>();

	private TrunkSamples() {
	}

	static void run(ClientGameTestContext context) {
		boolean sodium = FabricLoader.getInstance().isModLoaded("sodium");
		String renderer = sodium ? "sodium" : "vanilla";
		context.runOnClient(mc -> {
			mc.options.ambientOcclusion().set(true);
			mc.options.renderDistance().set(8);
		});
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getServer().runCommand("gamerule advance_time false");
			sp.getServer().runCommand("gamerule advance_weather false");
			sp.getServer().runCommand("gamerule spawn_mobs false");
			sp.getServer().runCommand("time set " + NOON);
			sp.getServer().runCommand("weather clear");
			sp.getServer().runCommand("difficulty peaceful");
			String registry = sp.getServer().computeOnServer(s -> checkRegistry());
			PolishForests.LOG.info("[samples] {}", registry);
			int[] origin = sp.getServer().computeOnServer(TrunkSamples::build);
			PolishForests.LOG.info("[samples] board built at ({}, {}, {}), section boundary at Y {}", origin[0], origin[1], origin[2],
					origin[1] + 2);
			syncClient(context, sp);
			String models = context.computeOnClient(mc -> checkModels(mc.getModelManager().getBlockStateModelSet()));
			PolishForests.LOG.info("[samples] {}", models);
			Board b = new Board(origin[0], origin[1], origin[2]);
			sp.getServer().runCommand("gamemode spectator @a");
			context.runOnClient(mc -> {
				if (!mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
			String p = "samples_" + renderer + "_";
			shot(context, sp, b, p + "overview", 22, 18, -14, 0, 32, 40);
			shot(context, sp, b, p + "columns", 11, 3.5, -6, 0, 5, 30);
			shot(context, sp, b, p + "section_boundary", 4.5, 2.6, -1.6, 0, 8, 3);
			// Seams of the thick columns at grazing angles, from above and from below (hairlines through the hollow inside).
			shot(context, sp, b, p + "seams_above", 17.5, 4.5, -0.5, 0, 35, 3);
			shot(context, sp, b, p + "seams_below", 17.5, 1.2, -0.8, 0, -25, 3);
			String[] dirs = {"down", "up", "north", "south", "west", "east"};
			for (int i = 0; i < 6; i++) {
				double z = Board.reachRow(i) - 4.5;
				shot(context, sp, b, p + "reach_" + dirs[i] + "_thin", 5.5, 5, z, 0, 22, 3);
				shot(context, sp, b, p + "reach_" + dirs[i] + "_thick", 15.5, 5.5, z - 1, 0, 22, 3);
			}
			shot(context, sp, b, p + "joints", 9.5, 4.5, Board.JOINTS_Z - 6, 0, 12, 3);
			shot(context, sp, b, p + "joints_side", 21, 4.5, Board.JOINTS_Z - 2, 60, 12, 3);
			shot(context, sp, b, p + "chunk_boundary", 16, 3.5, Board.CHUNK_ROW_Z - 4, 0, 12, 3);
			shot(context, sp, b, p + "water", 35, 6, Board.POOL_Z0 - 3, 0, 32, 3);
			shot(context, sp, b, p + "water_under", 31.5, 1.6, Board.POOL_Z0 + 1.5, 0, 0, 3);
			shot(context, sp, b, p + "pine", Board.PINE_X + 0.5, 11, Board.PINE_Z - 20, 0, -8, 30);
			shot(context, sp, b, p + "pine_crown", Board.PINE_X + 0.5, 19, Board.PINE_Z - 7, 0, -12, 3);
			// Night with lanterns at the joints: block light and ambient occlusion on the reaching parts.
			sp.getServer().runCommand("time set " + NIGHT);
			shot(context, sp, b, p + "joints_night", 9.5, 4.5, Board.JOINTS_Z - 6, 0, 12, 3);
			sp.getServer().runCommand("time set " + NOON);
			breaking(context, sp, b, p);
		}
	}

	/**
	 * The client must have every block of the board before the screenshots. With the optimization mods (C2ME) some
	 * section updates of blocks placed right after the world start did not reach the client (the stone platform was
	 * missing in whole chunks of the first Sodium run, the trunks above it were there): the test then sends those
	 * positions again and fails if the client still differs.
	 */
	private static void syncClient(ClientGameTestContext context, TestSingleplayerContext sp) {
		for (int round = 0; round < 3; round++) {
			context.waitTicks(40);
			// The server state now (leaves may have changed their distance since they were placed).
			Map<BlockPos, BlockState> server = sp.getServer().computeOnServer(s -> {
				Map<BlockPos, BlockState> m = new LinkedHashMap<>();
				BOARD.keySet().forEach(pos -> m.put(pos, s.overworld().getBlockState(pos)));
				return m;
			});
			List<BlockPos> missing = context.computeOnClient(mc -> server.entrySet().stream()
					.filter(e -> mc.level.getBlockState(e.getKey()) != e.getValue()).map(Map.Entry::getKey).toList());
			if (missing.isEmpty()) {
				PolishForests.LOG.info("[samples] the client has all {} blocks of the board{}", BOARD.size(),
						round == 0 ? "" : " after " + round + " resend(s)");
				return;
			}
			PolishForests.LOG.warn("[samples] the client misses {} of {} blocks of the board (first at {}), sending them again",
					missing.size(), BOARD.size(), missing.getFirst().toShortString());
			sp.getServer().runOnServer(s -> missing.forEach(pos -> s.overworld().getChunkSource().blockChanged(pos)));
		}
		throw new AssertionError("The client still misses blocks of the board after 3 resends");
	}

	// ------------------------------------------------------------------ registry and models

	/** The registry of the running game equals the snapshot file; returns the state counts for the log. */
	private static String checkRegistry() {
		Path file = Path.of(System.getProperty("polishforests.registry.snapshot", "src/test/resources/registry_snapshot.txt"));
		List<String> expected;
		try {
			expected = Files.readAllLines(file, StandardCharsets.UTF_8).stream().filter(l -> !l.isBlank()).toList();
		} catch (IOException e) {
			throw new AssertionError("Cannot read the registry snapshot " + file, e);
		}
		List<String> now = RegistrySnapshot.lines();
		if (!expected.equals(now)) {
			List<String> diff = new ArrayList<>();
			expected.stream().filter(l -> !now.contains(l)).forEach(l -> diff.add("- " + l));
			now.stream().filter(l -> !expected.contains(l)).forEach(l -> diff.add("+ " + l));
			throw new AssertionError("The registry of the game differs from " + file + ":\n" + String.join("\n", diff));
		}
		int mod = RegistrySnapshot.stateCount();
		return String.format(Locale.ROOT, "registry equals the snapshot (%d lines); block states of the mod %d (%d trunk blocks × 84), "
				+ "of the game %d", now.size() - 1, mod, ModBlocks.trunks().size(), Block.BLOCK_STATE_REGISTRY.size());
	}

	/** Every trunk state has its own model (not the missing model) and a bark particle sprite. */
	private static String checkModels(BlockStateModelSet models) {
		int states = 0;
		for (TrunkBlock block : ModBlocks.trunks().values()) {
			for (BlockState s : block.getStateDefinition().getPossibleStates()) {
				if (models.get(s) == models.missingModel()) {
					throw new AssertionError("No model for " + s);
				}
				if (models.getParticleMaterial(s).sprite().contents().name().equals(MissingTextureAtlasSprite.getLocation())) {
					throw new AssertionError("Missing particle texture for " + s);
				}
				states++;
			}
		}
		return "client models and particle sprites present for all " + states + " trunk states";
	}

	// ------------------------------------------------------------------ the board

	/** Positions on the board: origin (x0, y0 = top of the platform, z0); dy 1 and 2 lie on both sides of a section boundary. */
	private record Board(int x0, int y0, int z0) {
		static final int COLUMNS_Z = 2;
		static final int JOINTS_Z = 46;
		static final int CHUNK_ROW_Z = 50;
		static final int POOL_X0 = 26;
		static final int POOL_Z0 = 8;
		static final int PINE_X = 36;
		static final int PINE_Z = 30;

		/** z of the reach row of a direction index (down, up, north, south, west, east). */
		static int reachRow(int i) {
			return 8 + 6 * i;
		}

		/** x of the sample of radius r (columns, reach rows, joints). */
		static int radiusX(int r) {
			return 2 + 3 * (r - 1);
		}
	}

	private static final class Builder {
		private final ServerLevel level;
		private final Board b;
		private int placed;
		/** Every placed position with its state, for the check that the client got the board. */
		private final Map<BlockPos, BlockState> states = new LinkedHashMap<>();

		Builder(ServerLevel level, Board b) {
			this.level = level;
			this.b = b;
		}

		void put(int dx, int dy, int dz, BlockState s) {
			BlockPos pos = new BlockPos(b.x0() + dx, b.y0() + dy, b.z0() + dz);
			level.setBlock(pos, s, Block.UPDATE_CLIENTS);
			BlockState read = level.getBlockState(pos);
			if (read != s) {
				throw new AssertionError("Placed " + s + " at " + pos.toShortString() + ", read " + read);
			}
			placed++;
			states.put(pos, s);
		}

		/** Leaves around the branch tips: only into air, never over a trunk. */
		void putIfAir(int dx, int dy, int dz, BlockState s) {
			if (level.getBlockState(new BlockPos(b.x0() + dx, b.y0() + dy, b.z0() + dz)).isAir()) {
				put(dx, dy, dz, s);
			}
		}

		void trunk(TreeTrunk t, int dx, int dy, int dz, int r, Direction parent) {
			put(dx, dy, dz, ModBlocks.trunk(t).state(r, parent));
		}

		void wet(TreeTrunk t, int dx, int dy, int dz, int r, Direction parent) {
			put(dx, dy, dz, ModBlocks.trunk(t).state(r, parent).setValue(TrunkBlock.WATERLOGGED, true));
		}
	}

	/** Builds the board on the server; returns its origin. */
	private static int[] build(MinecraftServer server) {
		ServerLevel level = server.overworld();
		int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0) - 1;
		// The platform top two blocks below a section boundary: samples at dy 1 and 2 are in two sections.
		int boundary = Math.floorDiv(ground + 4 + 15, 16) * 16;
		Board b = new Board(0, boundary - 2, 8);
		Builder w = new Builder(level, b);
		for (int dx = -3; dx <= 46; dx++) {
			for (int dz = -8; dz <= 62; dz++) {
				w.put(dx, 0, dz, Blocks.STONE.defaultBlockState());
			}
		}
		TreeTrunk[] species = TreeTrunk.values();
		// Standing columns of radius 1-7, each of another species.
		for (int r = 1; r <= 7; r++) {
			for (int dy = 1; dy <= 4; dy++) {
				w.trunk(species[(r - 1) % species.length], Board.radiusX(r), dy, Board.COLUMNS_Z, r, Direction.DOWN);
			}
		}
		// Reach rows: host of radius 7 and two children of radius r towards -d (the parent of each child lies at +d).
		Direction[] dirs = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
		for (int i = 0; i < dirs.length; i++) {
			Direction d = dirs[i];
			int z = Board.reachRow(i);
			TreeTrunk t = species[i % species.length];
			for (int r = 1; r <= 7; r++) {
				int x = Board.radiusX(r);
				int hostY;
				if (d == Direction.UP) {
					hostY = 3;
					w.put(x, 4, z, Blocks.STONE.defaultBlockState());
					w.trunk(TreeTrunk.OAK, x, hostY, z, 7, Direction.UP);
				} else {
					hostY = 2;
					w.trunk(TreeTrunk.OAK, x, 1, z, 7, Direction.DOWN);
					w.trunk(TreeTrunk.OAK, x, hostY, z, 7, Direction.DOWN);
				}
				for (int k = 1; k <= 2; k++) {
					w.trunk(t, x - d.getStepX() * k, hostY - d.getStepY() * k, z - d.getStepZ() * k, r, d);
				}
			}
		}
		// Joints r + 1 → r and elbows: a column (r + 1, r + 1, r, r); from its top a branch of radius r to the east (the
		// trunk turns), from the third block a thinner branch to the north.
		for (int r = 1; r <= 6; r++) {
			int x = Board.radiusX(r);
			TreeTrunk t = species[(r + 3) % species.length];
			w.trunk(t, x, 1, Board.JOINTS_Z, r + 1, Direction.DOWN);
			w.trunk(t, x, 2, Board.JOINTS_Z, r + 1, Direction.DOWN);
			w.trunk(t, x, 3, Board.JOINTS_Z, r, Direction.DOWN);
			w.trunk(t, x, 4, Board.JOINTS_Z, r, Direction.DOWN);
			w.trunk(t, x + 1, 4, Board.JOINTS_Z, r, Direction.WEST);
			w.trunk(t, x, 3, Board.JOINTS_Z - 1, Math.max(1, r - 1), Direction.SOUTH);
			w.put(x + 1, 1, Board.JOINTS_Z - 2, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
		}
		// A branch of radius 4 along x across the chunk boundary x = 15 | 16, from a column of radius 6.
		w.trunk(TreeTrunk.LINDEN, 12, 1, Board.CHUNK_ROW_Z, 6, Direction.DOWN);
		w.trunk(TreeTrunk.LINDEN, 12, 2, Board.CHUNK_ROW_Z, 6, Direction.DOWN);
		for (int x = 13; x <= 20; x++) {
			w.trunk(TreeTrunk.LINDEN, x, 2, Board.CHUNK_ROW_Z, 4, Direction.WEST);
		}
		pool(w);
		pine(w);
		PolishForests.LOG.info("[samples] {} blocks placed", w.placed);
		BOARD.clear();
		BOARD.putAll(w.states);
		return new int[] {b.x0(), b.y0(), b.z0()};
	}

	/** A pool 3 blocks deep (across the section boundary) with waterlogged columns of radius 1-7 and a branch. */
	private static void pool(Builder w) {
		int x0 = Board.POOL_X0;
		int x1 = x0 + 18;
		int z0 = Board.POOL_Z0;
		int z1 = z0 + 8;
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				boolean wall = x == x0 || x == x1 || z == z0 || z == z1;
				for (int dy = 1; dy <= 3; dy++) {
					w.put(x, dy, z, wall ? Blocks.STONE.defaultBlockState() : Blocks.WATER.defaultBlockState());
				}
			}
		}
		for (int r = 1; r <= 7; r++) {
			int x = x0 + 2 + 2 * (r - 1);
			for (int dy = 1; dy <= 3; dy++) {
				w.wet(TreeTrunk.OAK, x, dy, z0 + 4, r, Direction.DOWN);
			}
			w.trunk(TreeTrunk.OAK, x, 4, z0 + 4, r, Direction.DOWN);
		}
		int x = x1 - 2;
		for (int dy = 1; dy <= 3; dy++) {
			w.wet(TreeTrunk.BIRCH, x, dy, z0 + 2, 5, Direction.DOWN);
		}
		for (int dz = 3; dz <= 6; dz++) {
			w.wet(TreeTrunk.BIRCH, x, 2, z0 + dz, 3, Direction.NORTH);
		}
	}

	/**
	 * A Scots pine from a pipe model: radius² of the trunk = 1 + Σ radius² of the branches above (Leonardo's rule, n = 2),
	 * the lower two thirds with the lower bark, the upper third with the "mirror"; branches in the top third (some of
	 * them climbing one block on the way, a step on the diagonal), dead stubs below, leaves at the branch tips.
	 */
	private static void pine(Builder w) {
		int x0 = Board.PINE_X;
		int z0 = Board.PINE_Z;
		int height = 22;
		// Branches: height, direction, radius at the trunk, length, whether it climbs one block at the middle.
		record Branch(int dy, Direction dir, int r, int length, boolean climbs) {
		}
		List<Branch> branches = List.of(new Branch(15, Direction.NORTH, 2, 4, true), new Branch(16, Direction.EAST, 2, 4, false),
				new Branch(17, Direction.SOUTH, 2, 3, true), new Branch(18, Direction.WEST, 2, 3, false),
				new Branch(19, Direction.NORTH, 1, 2, false), new Branch(20, Direction.EAST, 1, 2, true),
				new Branch(21, Direction.SOUTH, 1, 1, false));
		Map<Integer, Integer> stubs = new LinkedHashMap<>();
		stubs.put(9, 0);
		stubs.put(11, 2);
		stubs.put(13, 1);
		Direction[] around = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
		for (int dy = 1; dy <= height; dy++) {
			double area = 1;
			for (Branch br : branches) {
				area += br.dy() >= dy ? br.r() * br.r() : 0;
			}
			for (int s : stubs.keySet()) {
				area += s >= dy ? 1 : 0;
			}
			int r = (int) Math.round(Math.sqrt(area));
			TreeTrunk t = dy > height * 2 / 3 ? TreeTrunk.SCOTS_PINE_UPPER : TreeTrunk.SCOTS_PINE;
			w.trunk(t, x0, dy, z0, Math.min(r, TrunkBlock.MAX_RADIUS), Direction.DOWN);
		}
		BlockState leaves = Blocks.SPRUCE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
		for (Branch br : branches) {
			int x = x0;
			int y = br.dy();
			int z = z0;
			Direction parent = br.dir().getOpposite();
			for (int k = 1; k <= br.length(); k++) {
				int r = k > br.length() / 2 ? 1 : br.r();
				if (br.climbs() && k == br.length() / 2 + 1) {
					y++;                                       // one block up: the child above its parent
					w.trunk(TreeTrunk.SCOTS_PINE_UPPER, x, y, z, r, Direction.DOWN);
					parent = Direction.DOWN;
					continue;
				}
				x += br.dir().getStepX();
				z += br.dir().getStepZ();
				w.trunk(TreeTrunk.SCOTS_PINE_UPPER, x, y, z, r, parent);
				parent = br.dir().getOpposite();
			}
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					for (int dy = 0; dy <= 1; dy++) {
						if (Math.abs(dx) + Math.abs(dz) + dy <= 2 && (dx != 0 || dz != 0 || dy != 0)) {
							w.putIfAir(x + dx, y + dy, z + dz, leaves);
						}
					}
				}
			}
		}
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				w.putIfAir(x0 + dx, height + 1, z0 + dz, leaves);
			}
		}
		w.putIfAir(x0, height + 2, z0, leaves);
		for (Map.Entry<Integer, Integer> s : stubs.entrySet()) {
			Direction d = around[s.getValue()];
			w.trunk(TreeTrunk.SCOTS_PINE, x0 + d.getStepX(), s.getKey(), z0 + d.getStepZ(), 1, d.getOpposite());
		}
	}

	// ------------------------------------------------------------------ screenshots

	/** A screenshot from (dx, dy, dz) relative to the board, after waiting for the terrain. */
	private static void shot(ClientGameTestContext context, TestSingleplayerContext sp, Board b, String name, double dx, double dy, double dz,
			double yaw, double pitch, int waitSeconds) {
		sp.getServer().runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", b.x0() + dx, b.y0() + dy, b.z0() + dz, yaw, pitch));
		context.waitTicks(20 * waitSeconds);
		context.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix().withSize(SIZE[0], SIZE[1]));
		PolishForests.LOG.info(String.format(Locale.ROOT, "[samples] %s: camera (%.1f, %.1f, %.1f) yaw %.0f pitch %.0f", name,
				b.x0() + dx, b.y0() + dy, b.z0() + dz, yaw, pitch));
	}

	/**
	 * A survival player on the platform breaks a standing birch trunk of radius 4 by hand: a screenshot of the crosshair outline
	 * with the cracks and hit particles halfway, then of the destroy particles right after it breaks; the drop must be the
	 * log of the species (loot of radius ≥ 3).
	 */
	private static void breaking(ClientGameTestContext context, TestSingleplayerContext sp, Board b, String prefix) {
		BlockPos target = new BlockPos(b.x0() + Board.radiusX(4), b.y0() + 2, b.z0() + Board.COLUMNS_Z);
		sp.getServer().runCommand("gamemode survival @a");
		context.runOnClient(mc -> {
			if (mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
		sp.getServer().runCommand(String.format(Locale.ROOT, "tp @a %.2f %d %.2f 0 0", target.getX() + 0.5, b.y0() + 1, target.getZ() - 2.5));
		context.waitTicks(40);
		context.getInput().lookAt(target);
		context.waitTicks(2);
		String hit = context.computeOnClient(mc -> String.valueOf(mc.hitResult));
		PolishForests.LOG.info("[samples] breaking: target {}, hit {}", target.toShortString(), hit);
		context.getInput().holdKey(o -> o.keyAttack);
		context.waitTicks(30);
		// At the size of the window: with another size the HUD (crosshair, hotbar) is laid out for the window.
		context.takeScreenshot(TestScreenshotOptions.of(prefix + "breaking").disableCounterPrefix());
		int ticks = context.waitFor(mc -> mc.level.getBlockState(target).isAir(), 400);
		context.getInput().releaseKey(o -> o.keyAttack);
		context.waitTicks(1);
		context.takeScreenshot(TestScreenshotOptions.of(prefix + "destroyed").disableCounterPrefix());
		PolishForests.LOG.info("[samples] trunk of radius 4 broken by hand after {} more ticks", ticks);
		TreeTrunk species = TreeTrunk.values()[3];
		context.waitTicks(10);
		String drops = sp.getServer().computeOnServer(s -> s.overworld()
				.getEntitiesOfClass(ItemEntity.class, new AABB(target).inflate(3))
				.stream().map(e -> e.getItem().getCount() + " " + BuiltInRegistries.ITEM.getKey(e.getItem().getItem()))
				.toList().toString());
		PolishForests.LOG.info("[samples] drops of the broken {}: {}", species.id(), drops);
		String log = BuiltInRegistries.ITEM.getKey(species.log().asItem()).toString();
		if (!drops.contains(log)) {
			throw new AssertionError("A trunk of radius 4 of " + species.id() + " dropped " + drops + " instead of " + log);
		}
		sp.getServer().runCommand("gamemode spectator @a");
	}
}
