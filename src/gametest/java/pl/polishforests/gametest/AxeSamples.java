package pl.polishforests.gametest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jspecify.annotations.Nullable;
import pl.polishforests.PolishForests;
import pl.polishforests.block.ModBlocks;
import pl.polishforests.block.StrippedTrunk;
import pl.polishforests.block.TreeTrunk;
import pl.polishforests.block.TrunkAxe;
import pl.polishforests.block.TrunkBlock;
import pl.polishforests.compat.FellingCompat;
import pl.polishforests.compat.TreeWood;
import pl.polishforests.worldgen.feature.ProceduralTreeFeature;
import pl.polishforests.worldgen.habitat.Species;
import pl.polishforests.worldgen.tree.AgeClass;
import pl.polishforests.worldgen.tree.CrownLayer;
import pl.polishforests.worldgen.tree.TreeRequest;
import pl.polishforests.worldgen.tree.TreeShapePlan;
import pl.polishforests.worldgen.tree.TreeVoxels;

/**
 * The axe on the thin trunks and their hitboxes (step P1 of docs/04-pionowy-wycinek.md; {@code -Pgametest=slice
 * -Psites=axe}, about 1 min; with {@code -PtreeFelling=fallingtree|treeharvester} the stripped trees are felled with the
 * mod). In the flat test world, by a survival player through {@code ServerPlayerGameMode#useItemOn} (the path of a real
 * use, Fabric's use hook included) and {@code #destroyBlock}:
 * <ul>
 * <li>every trunk block (12) at every radius 1-7, upright in the air: the axe strips it into the stripped trunk of its
 * species with the same radius, parent and water, then the sneaking axe thins it by 1 px down to 1 (at 1 nothing
 * happens); a second block is thinned with its bark first, then stripped; each use costs the axe 1 point of durability,
 * none when nothing happens; in adventure mode neither works (vanilla's rule for an axe without {@code can_place_on});
 * breaking the result drops the stripped log or sticks by the radius rule of the trunks (G1-b, G1-e), and a sideways
 * trunk of radius 4 keeps its parent and water when stripped;</li>
 * <li>the hitboxes in the world: a tip ends r px past the center, an elbow has no stub, a fork shows its side arm, a ray
 * from above hits a tip at its drawn top; a lying twig of radius 1 does not collide;</li>
 * <li>a mature oak and a mature Scots pine of the tree planner with every thin trunk stripped are one tree of the same
 * size for {@link TreeWood} and are felled whole (by hand top-down, or with the felling mod by one break), with the
 * logs the tree gives.</li>
 * </ul>
 * Results in {@code build/axe/axe.txt}; any problem fails the test. No screenshots (the look is the user's checklist).
 */
final class AxeSamples {
	/** Spacing of the sample trunks, blocks. */
	private static final int STEP = 3;
	private static final int HEIGHT = 4;

	private AxeSamples() {
	}

	static void run(ClientGameTestContext context) {
		String mod = FabricLoader.getInstance().isModLoaded(FellingCompat.FALLING_TREE) ? FellingCompat.FALLING_TREE
				: FabricLoader.getInstance().isModLoaded(FellingCompat.TREE_HARVESTER) ? FellingCompat.TREE_HARVESTER : null;
		List<String> lines = new ArrayList<>();
		List<String> problems = new ArrayList<>();
		lines.add("# Step P1: the axe on the thin trunks, hitboxes, felling stripped trees " + (mod == null ? "by hand" : "with " + mod));
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			FellingSamples.setUp(sp);
			int y = sp.getServer().computeOnServer(s -> s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0));
			FellingSamples.stand(sp, 20.5, y + 6, -6.5);
			context.waitTicks(40);
			lines.addAll(sp.getServer().computeOnServer(s -> axe(s, y, problems)));
			context.waitTicks(10);
			lines.addAll(sp.getServer().computeOnServer(s -> hitboxes(s.overworld(), y + 2, problems)));
			int[] at = {0};
			for (Species s : new Species[] {Species.OAK, Species.SCOTS_PINE}) {
				int x = 40 + 30 * at[0]++;
				FellingSamples.stand(sp, x + 0.5 - 8, y + 2, 40.5 - 8);
				context.waitTicks(20);
				lines.add(sp.getServer().computeOnServer(server -> fell(server, s, x, y, 40, mod, problems)));
				context.waitTicks(20);
			}
		}
		lines.add("# problems: " + problems.size());
		lines.addAll(problems);
		write(lines);
		if (!problems.isEmpty()) {
			throw new AssertionError("[axe] " + problems.size() + " problems, see build/axe/axe.txt: " + problems.getFirst());
		}
	}

	// ------------------------------------------------------------------ stripping and thinning

	private static List<String> axe(MinecraftServer server, int y, List<String> problems) {
		ServerLevel level = server.overworld();
		ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
		List<String> out = new ArrayList<>();
		TreeTrunk[] trunks = TreeTrunk.values();
		for (int i = 0; i < trunks.length; i++) {
			TreeTrunk t = trunks[i];
			TrunkBlock bark = ModBlocks.trunk(t);
			TrunkBlock bare = ModBlocks.stripped(StrippedTrunk.of(t));
			Block strippedLog = StrippedTrunk.of(t).log();
			for (int r = TrunkBlock.MIN_RADIUS; r <= TrunkBlock.MAX_RADIUS; r++) {
				String name = t.id() + " r" + r;
				BlockPos a = new BlockPos(STEP * r, y + HEIGHT, STEP * 2 * i);
				BlockPos b = a.south(STEP);
				level.setBlock(a, bark.state(r, Direction.DOWN), 3);
				level.setBlock(b, bark.state(r, Direction.DOWN), 3);
				ItemStack stack = axeInHand(player);
				// A: strip, then thin.
				use(player, level, a, false);
				expect(problems, name + " strip", level.getBlockState(a), bare, r, Direction.DOWN, false);
				expectDamage(problems, name + " strip", stack, 1);
				use(player, level, a, true);
				int thin = Math.max(TrunkBlock.MIN_RADIUS, r - 1);
				expect(problems, name + " thin stripped", level.getBlockState(a), bare, thin, Direction.DOWN, false);
				expectDamage(problems, name + " thin stripped", stack, r > TrunkBlock.MIN_RADIUS ? 2 : 1);
				// B: thin with the bark, then strip.
				ItemStack second = axeInHand(player);
				use(player, level, b, true);
				expect(problems, name + " thin bark", level.getBlockState(b), bark, thin, Direction.DOWN, false);
				use(player, level, b, false);
				expect(problems, name + " strip thinned", level.getBlockState(b), bare, thin, Direction.DOWN, false);
				expectDamage(problems, name + " thin and strip", second, r > TrunkBlock.MIN_RADIUS ? 2 : 1);
				// Loot of A (in the air, so no stem base): the stripped log of the species or sticks by the radius.
				int[] drops = breakAndCount(player, level, a, strippedLog.asItem());
				boolean log = TreeWood.dropsLog(bare.state(thin, Direction.DOWN));
				if (log ? drops[0] != 1 || drops[1] != 0 : drops[0] != 0 || drops[1] > 1) {
					problems.add(String.format(Locale.ROOT, "%s: broken at r%d: %d %s, %d sticks, %d other", name, thin, drops[0],
							BuiltInRegistries.ITEM.getKey(strippedLog.asItem()), drops[1], drops[2]));
				}
				if (drops[2] != 0) {
					problems.add(name + ": " + drops[2] + " other items from a stripped trunk");
				}
				level.setBlock(b, Blocks.AIR.defaultBlockState(), 3);
			}
			// A sideways trunk with water keeps its parent and water when stripped.
			BlockPos c = new BlockPos(STEP * 9, y + HEIGHT, STEP * 2 * i);
			level.setBlock(c, bark.state(4, Direction.NORTH).setValue(TrunkBlock.WATERLOGGED, true), 3);
			axeInHand(player);
			use(player, level, c, false);
			expect(problems, t.id() + " sideways waterlogged", level.getBlockState(c), bare, 4, Direction.NORTH, true);
			use(player, level, c, true);
			expect(problems, t.id() + " sideways waterlogged thin", level.getBlockState(c), bare, 3, Direction.NORTH, true);
			level.setBlock(c, Blocks.AIR.defaultBlockState(), 3);
			out.add(String.format(Locale.ROOT, "axe %s -> %s: strip, thin and loot at radius 1-7, sideways waterlogged", t.id(),
					BuiltInRegistries.BLOCK.getKey(bare)));
		}
		// Adventure mode: neither stripping nor thinning (an axe without can_place_on).
		BlockPos d = new BlockPos(STEP * 11, y + HEIGHT, 0);
		level.setBlock(d, ModBlocks.trunk(TreeTrunk.OAK).state(5, Direction.DOWN), 3);
		player.setGameMode(GameType.ADVENTURE);
		ItemStack stack = axeInHand(player);
		use(player, level, d, false);
		use(player, level, d, true);
		player.setGameMode(GameType.SURVIVAL);
		player.getAbilities().mayfly = true;
		player.getAbilities().flying = true;
		player.onUpdateAbilities();
		expect(problems, "adventure", level.getBlockState(d), ModBlocks.trunk(TreeTrunk.OAK), 5, Direction.DOWN, false);
		expectDamage(problems, "adventure", stack, 0);
		// Not an axe: nothing.
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
		use(player, level, d, true);
		expect(problems, "stick", level.getBlockState(d), ModBlocks.trunk(TreeTrunk.OAK), 5, Direction.DOWN, false);
		level.setBlock(d, Blocks.AIR.defaultBlockState(), 3);
		out.add("adventure mode and a stick: no change, no durability");
		return out;
	}

	private static ItemStack axeInHand(ServerPlayer player) {
		ItemStack stack = new ItemStack(Items.IRON_AXE);
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
		return stack;
	}

	/** A right click on the top face of the block, sneaking or not. */
	private static void use(ServerPlayer player, ServerLevel level, BlockPos pos, boolean sneak) {
		player.setShiftKeyDown(sneak);
		player.setPose(sneak ? Pose.CROUCHING : Pose.STANDING);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
		player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
		player.setShiftKeyDown(false);
		player.setPose(Pose.STANDING);
	}

	private static void expect(List<String> problems, String what, BlockState s, Block block, int radius, Direction parent, boolean water) {
		if (s.getBlock() != block || s.getValue(TrunkBlock.RADIUS) != radius || s.getValue(TrunkBlock.PARENT) != parent
				|| s.getValue(TrunkBlock.WATERLOGGED) != water) {
			problems.add(String.format(Locale.ROOT, "%s: %s, expected %s radius %d parent %s water %b", what, s,
					BuiltInRegistries.BLOCK.getKey(block), radius, parent, water));
		}
	}

	private static void expectDamage(List<String> problems, String what, ItemStack stack, int damage) {
		if (stack.getDamageValue() != damage * TrunkAxe.DAMAGE) {
			problems.add(what + ": axe damage " + stack.getDamageValue() + ", expected " + damage);
		}
	}

	/** Breaks a block with the axe and counts the drops: {log, sticks, other}. */
	private static int[] breakAndCount(ServerPlayer player, ServerLevel level, BlockPos pos, Item log) {
		axeInHand(player);
		player.gameMode.destroyBlock(pos);
		int[] n = new int[3];
		for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2))) {
			ItemStack st = e.getItem();
			n[st.is(log) ? 0 : st.is(Items.STICK) ? 1 : 2] += st.getCount();
			e.discard();
		}
		return n;
	}

	// ------------------------------------------------------------------ hitboxes

	private static List<String> hitboxes(ServerLevel level, int y, List<String> problems) {
		List<String> out = new ArrayList<>();
		TrunkBlock oak = ModBlocks.trunk(TreeTrunk.OAK);
		int x = 0;
		int z = -10;
		for (int r = TrunkBlock.MIN_RADIUS; r <= TrunkBlock.MAX_RADIUS; r++, x += STEP) {
			// A column of two blocks; the upper one is a tip.
			BlockPos base = new BlockPos(x, y, z);
			BlockPos tip = base.above();
			level.setBlock(base, oak.state(r, Direction.DOWN), 3);
			level.setBlock(tip, oak.state(r, Direction.DOWN), 3);
			AABB column = level.getBlockState(base).getShape(level, base).bounds();
			AABB top = level.getBlockState(tip).getShape(level, tip).bounds();
			check(problems, "column r" + r, column.maxY, 1.0);
			check(problems, "tip r" + r, top.maxY, (8 + r) / 16.0);
			// A ray down the axis hits the tip at its drawn top, not at the top of the block.
			Vec3 from = new Vec3(x + 0.5, y + 3.5, z + 0.5);
			BlockHitResult hit = level.clip(new ClipContext(from, from.add(0, -3, 0), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE,
					CollisionContext.empty()));
			if (hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(tip) || Math.abs(hit.getLocation().y - (y + 1 + (8 + r) / 16.0)) > 1e-6) {
				problems.add(String.format(Locale.ROOT, "ray r%d: %s at %s", r, hit.getType(), hit.getLocation()));
			}
			// An elbow: the tip turns east into a branch of the same radius; the column block has no stub above.
			BlockPos side = base.east();
			level.setBlock(tip, Blocks.AIR.defaultBlockState(), 3);
			level.setBlock(side, oak.state(r, Direction.WEST), 3);
			AABB elbow = level.getBlockState(base).getShape(level, base).bounds();
			check(problems, "elbow r" + r + " top", elbow.maxY, (8 + r) / 16.0);
			check(problems, "elbow r" + r + " east", elbow.maxX, 1.0);
			// A fork: the column goes on up and the branch stays (with radius 1 in place of r when thinner).
			level.setBlock(tip, oak.state(r, Direction.DOWN), 3);
			AABB fork = level.getBlockState(base).getShape(level, base).bounds();
			check(problems, "fork r" + r + " top", fork.maxY, 1.0);
			check(problems, "fork r" + r + " east", fork.maxX, 1.0);
			// Collision of a lying twig of radius 1: none; of the branch of radius r ≥ 2: its drawn top.
			BlockState branch = level.getBlockState(side);
			boolean empty = branch.getCollisionShape(level, side, CollisionContext.empty()).isEmpty();
			if (empty != (r == 1)) {
				problems.add("branch r" + r + " collision " + (empty ? "empty" : "not empty"));
			}
			for (BlockPos p : List.of(base, tip, side)) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
			}
		}
		out.add("hitboxes: column, tip, ray, elbow, fork and twig collision at radius 1-7");
		return out;
	}

	private static void check(List<String> problems, String what, double got, double expected) {
		if (Math.abs(got - expected) > 1e-6) {
			problems.add(String.format(Locale.ROOT, "%s: %.4f, expected %.4f", what, got, expected));
		}
	}

	// ------------------------------------------------------------------ felling stripped trees

	private static String fell(MinecraftServer server, Species s, int x, int y, int z, @Nullable String mod, List<String> problems) {
		ServerLevel level = server.overworld();
		ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
		TreeVoxels v = TreeShapePlan.plan(new TreeRequest(s, CrownLayer.CANOPY, AgeClass.MATURE, level.getSeed(), x, y, z));
		ProceduralTreeFeature.write(level, v, s, CrownLayer.CANOPY, AgeClass.MATURE);
		BlockPos base = new BlockPos(v.x(), v.y(), v.z());
		TreeWood.Tree before = TreeWood.GAME.of(level::getBlockState, base);
		String name = "fell stripped " + s.id();
		if (before == null) {
			problems.add(name + ": no tree at " + base);
			return name + ": no tree";
		}
		int stripped = 0;
		for (BlockPos p : before.wood()) {
			BlockState st = level.getBlockState(p);
			if (st.getBlock() instanceof TrunkBlock && TrunkAxe.thinnable(st)) {
				axeInHand(player);
				use(player, level, p, false);
				stripped += level.getBlockState(p).getBlock() != st.getBlock() ? 1 : 0;
			}
		}
		TreeWood.Tree after = TreeWood.GAME.of(level::getBlockState, base);
		if (after == null || after.size() != before.size() || after.logDrops() != before.logDrops()) {
			problems.add(name + ": the stripped tree is not the same tree (" + before.size() + " -> " + (after == null ? "none" : after.size()) + ")");
			return name + ": changed by stripping";
		}
		int[] b = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
		for (BlockPos p : after.wood()) {
			b[0] = Math.min(b[0], p.getX());
			b[1] = Math.min(b[1], p.getY());
			b[2] = Math.min(b[2], p.getZ());
			b[3] = Math.max(b[3], p.getX());
			b[4] = Math.max(b[4], p.getY());
			b[5] = Math.max(b[5], p.getZ());
		}
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.NETHERITE_AXE));
		boolean crouch = FellingCompat.TREE_HARVESTER.equals(mod);
		if (crouch) {
			player.setShiftKeyDown(true);
			player.setPose(Pose.CROUCHING);
		}
		if (mod == null) {
			List<BlockPos> order = new ArrayList<>(after.wood());
			order.sort((p, q) -> Integer.compare(q.getY(), p.getY()));
			for (BlockPos p : order) {
				player.gameMode.destroyBlock(p);
			}
		} else {
			player.gameMode.destroyBlock(after.wood().contains(base.above()) ? base.above() : base);
		}
		if (crouch) {
			player.setShiftKeyDown(false);
			player.setPose(Pose.STANDING);
		}
		int left = 0;
		for (BlockPos p : after.wood()) {
			left += TreeWood.GAME.wood(level.getBlockState(p)) ? 1 : 0;
		}
		int logs = 0;
		int strippedLogs = 0;
		Item log = StrippedTrunk.of(ProceduralTreeFeature.trunk(s, TreeVoxels.LOWER)).log().asItem();
		AABB box = new AABB(b[0] - 3, b[1] - 3, b[2] - 3, b[3] + 4, b[4] + 4, b[5] + 4);
		for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, box)) {
			ItemStack st = e.getItem();
			if (st.is(ItemTags.LOGS)) {
				logs += st.getCount();
				strippedLogs += st.is(log) ? st.getCount() : 0;
			}
			e.discard();
		}
		String line = String.format(Locale.ROOT, "%s %s: wood %d, stripped %d, left %d, logs %d (expected %d-%d), of them stripped %d",
				name, mod == null ? "by hand" : "with " + mod, after.size(), stripped, left, logs, after.logDrops(),
				after.logDrops() + after.bases(), strippedLogs);
		if (left > 0 || logs < Math.max(1, after.logDrops()) || logs > after.logDrops() + after.bases() || stripped == 0 || strippedLogs == 0) {
			problems.add(line);
		}
		PolishForests.LOG.info("[axe] {}", line);
		return line;
	}

	private static void write(List<String> lines) {
		Path dir = Path.of(System.getProperty("polishforests.felling.out", "build/felling")).resolveSibling("axe");
		try {
			Files.createDirectories(dir);
			Files.write(dir.resolve("axe.txt"), lines, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
