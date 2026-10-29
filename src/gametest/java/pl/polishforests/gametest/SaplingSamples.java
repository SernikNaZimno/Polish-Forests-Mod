package pl.polishforests.gametest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;
import pl.polishforests.PolishForests;
import pl.polishforests.block.ModBlocks;
import pl.polishforests.block.SaplingSize;
import pl.polishforests.block.SpeciesSaplingBlock;
import pl.polishforests.block.TreeFoliage;
import pl.polishforests.compat.FellingCompat;
import pl.polishforests.compat.TreeWood;
import pl.polishforests.worldgen.feature.ProceduralTreeFeature;
import pl.polishforests.worldgen.tree.TreeVoxels;

/**
 * The sizes of the trees grown from the saplings of the mod (step S1 of docs/04-pionowy-wycinek.md, decision CP-C.1;
 * {@code -Pgametest=slice -Psites=saplings}, optionally with {@code -PtreeFelling=fallingtree|treeharvester}). On the flat
 * test world, for every species of the slice, a row of saplings with the size forced ({@value #FORCED} of each size, small,
 * medium, large from west to east) and a grid of {@value #RANDOM} saplings fed bone meal until they grow (the size drawn by
 * the sapling itself). Checked:
 * <ul>
 * <li>every grown tree is exactly the planner's tree of one size (every wood block of the configured feature
 * {@code sapling/<species>[/medium|/large]} in place), so the room check saw the tree that grew;</li>
 * <li>the shares of the sizes among the random saplings (all species together) lie within {@value #SHARE_TOLERANCE} of the
 * weights (25 / 55 / 20%), every size comes up; the counts per species are logged;</li>
 * <li>a large draw under a stone ceiling just above the small tree's wood grows the small tree; a sapling with the ceiling
 * two blocks above takes no bone meal and stays;</li>
 * <li>one tree of each size of every species is felled: by hand (every wood block broken from the top down by a survival
 * player with an axe, the stem base last) without a felling mod, or with one break of the mod: the logs lie within the
 * range of {@link TreeWood} (the small tree 1–2 logs from its stem base), every size gives at least one log. With Tree
 * Harvester the sapling it replants at the base is fed bone meal and must grow a tree of one of the sizes (G1-k).</li>
 * </ul>
 * Results in {@code build/saplings/saplings.txt} and the log ({@code [saplings]}); side views
 * {@code saplings_<species>_sizes} (the forced row) and {@code saplings_<species>_random} (the grid).
 */
final class SaplingSamples {
	private static final int[] SIZE = {1280, 720};
	/** Saplings of each size forced per species, random saplings per species. */
	private static final int FORCED = 2;
	private static final int RANDOM = 24;
	private static final int RANDOM_COLUMNS = 6;
	/** Distance of the trees (a large crown reaches about 8 blocks), of the species rows. */
	private static final int STEP = 20;
	private static final int ROW = 140;
	/** Distance from the forced row to the random grid. */
	private static final int GRID_Z = 30;
	/** Most bone meal fed to one sapling. */
	private static final int MAX_MEAL = 96;
	/** Ticks to wait for Tree Harvester's replanting. */
	private static final int REPLANT_TICKS = 200;
	/** Allowed difference of the share of a size among all random saplings from its weight. */
	private static final double SHARE_TOLERANCE = 0.12;

	private SaplingSamples() {
	}

	/** One grown tree. */
	private record Grown(TreeFoliage foliage, BlockPos pos, @Nullable SaplingSize size, boolean forced, int meal, int height,
			int wood) {
	}

	static void run(ClientGameTestContext context) {
		String mod = FabricLoader.getInstance().isModLoaded(FellingCompat.FALLING_TREE) ? FellingCompat.FALLING_TREE
				: FabricLoader.getInstance().isModLoaded(FellingCompat.TREE_HARVESTER) ? FellingCompat.TREE_HARVESTER : null;
		context.runOnClient(mc -> {
			mc.options.ambientOcclusion().set(true);
			mc.options.renderDistance().set(12);
		});
		List<String> lines = new ArrayList<>();
		List<String> problems = new ArrayList<>();
		lines.add("# Step S1: trees grown from saplings, felling " + (mod == null ? "by hand" : "with " + mod));
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			FellingSamples.setUp(sp);
			FellingSamples.hideHud(context);
			int y = sp.getServer().computeOnServer(s -> s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0));
			List<Grown> all = new ArrayList<>();
			TreeFoliage[] foliage = TreeFoliage.values();
			for (int i = 0; i < foliage.length; i++) {
				TreeFoliage f = foliage[i];
				int z0 = i * ROW;
				FellingSamples.stand(sp, 50.5, y + 30, z0 + 0.5);
				context.waitTicks(40);
				List<Grown> grown = sp.getServer().computeOnServer(s -> grow(s.overworld(), f, y, z0, problems));
				all.addAll(grown);
				for (Grown g : grown) {
					lines.add(line(g));
				}
				context.waitTicks(20);
				// The forced row from the south (small, medium, large from left to right), the random grid from the south-west and
				// above.
				view(context, sp, "saplings_" + f.species().id() + "_sizes", (SaplingSize.values().length * FORCED - 1) * STEP / 2.0 + 0.5,
						y + 9, z0 + 46.5, 180, 4);
				view(context, sp, "saplings_" + f.species().id() + "_random", -25.5, y + 30, z0 + GRID_Z + RANDOM / RANDOM_COLUMNS * STEP + 25.5,
						225, 25);
			}
			lines.addAll(distribution(all, problems));
			lines.addAll(sp.getServer().computeOnServer(s -> fallback(s.overworld(), y, foliage.length * ROW, problems)));
			// Felling: the first tree of each size of the forced rows.
			List<Grown> felled = new ArrayList<>();
			for (Grown g : all) {
				if (!g.forced() || g.size() == null || g.pos().getX() % (FORCED * STEP) != 0) {
					continue;
				}
				FellingSamples.stand(sp, g.pos().getX() + 0.5 - 6, y + 2, g.pos().getZ() + 0.5 - 6);
				context.waitTicks(10);
				String line = sp.getServer().computeOnServer(s -> fell(s, g, mod, problems));
				lines.add(line);
				PolishForests.LOG.info("[saplings] {}", line);
				felled.add(g);
				context.waitTicks(10);
			}
			if (FellingCompat.TREE_HARVESTER.equals(mod)) {
				// Tree Harvester replants a sapling at the base a few seconds after the felling (G1-k).
				context.waitTicks(REPLANT_TICKS);
				for (Grown g : felled) {
					String line = sp.getServer().computeOnServer(s -> replant(s.overworld(), g));
					lines.add(line);
					PolishForests.LOG.info("[saplings] {}", line);
				}
			}
		}
		lines.add("# problems: " + problems.size());
		lines.addAll(problems);
		write(lines);
		if (!problems.isEmpty()) {
			throw new AssertionError("[saplings] " + problems.size() + " problems, see build/saplings/saplings.txt: " + problems.getFirst());
		}
	}

	private static String line(Grown g) {
		return String.format(Locale.ROOT, "%s %s at %d %d: %s, bone meal %d, %d blocks tall, %d wood", g.forced() ? "forced" : "random",
				g.foliage().species().id(), g.pos().getX(), g.pos().getZ(), g.size() == null ? "NOT GROWN" : g.size(), g.meal(), g.height(),
				g.wood());
	}

	/** Grows the forced row and the random grid of a species (on the server). */
	private static List<Grown> grow(ServerLevel level, TreeFoliage f, int y, int z0, List<String> problems) {
		SpeciesSaplingBlock sapling = ModBlocks.sapling(f);
		List<Grown> out = new ArrayList<>();
		int x = 0;
		for (SaplingSize size : SaplingSize.values()) {
			for (int k = 0; k < FORCED; k++) {
				BlockPos pos = new BlockPos(x, y, z0);
				x += STEP;
				level.setBlock(pos, sapling.defaultBlockState().setValue(SaplingBlock.STAGE, 1), 3);
				SaplingSize got = sapling.grow(level, pos, level.getBlockState(pos), level.getRandom(), size);
				out.add(check(level, f, pos, true, 0, got, problems));
				if (got != size) {
					problems.add(String.format(Locale.ROOT, "%s forced %s at %s grew %s", f.species().id(), size, pos, got));
				}
			}
		}
		for (int k = 0; k < RANDOM; k++) {
			BlockPos pos = new BlockPos((k % RANDOM_COLUMNS) * STEP, y, z0 + GRID_Z + (k / RANDOM_COLUMNS) * STEP);
			level.setBlock(pos, sapling.defaultBlockState(), 3);
			int meal = 0;
			while (meal < MAX_MEAL && level.getBlockState(pos).is(sapling)) {
				BlockState st = level.getBlockState(pos);
				if (!sapling.isValidBonemealTarget(level, pos, st, BonemealSource.INTERACTION)) {
					problems.add(String.format(Locale.ROOT, "%s sapling at %s takes no bone meal on free ground", f.species().id(), pos));
					break;
				}
				meal++;
				if (sapling.isBonemealSuccess(level, level.getRandom(), pos, st, BonemealSource.INTERACTION)) {
					sapling.performBonemeal(level, level.getRandom(), pos, st, BonemealSource.INTERACTION);
				}
			}
			out.add(check(level, f, pos, false, meal, null, problems));
		}
		return out;
	}

	/** Which size grew at {@code pos} (the planner's tree of that size is in place); a problem when none. */
	private static Grown check(ServerLevel level, TreeFoliage f, BlockPos pos, boolean forced, int meal, @Nullable SaplingSize expected,
			List<String> problems) {
		SpeciesSaplingBlock sapling = ModBlocks.sapling(f);
		SaplingSize found = null;
		TreeVoxels tree = null;
		for (SaplingSize s : SaplingSize.values()) {
			TreeVoxels v = plan(level, sapling, s, pos);
			if (v != null && missing(level, f, v) == 0) {
				found = s;
				tree = v;
				break;
			}
		}
		if (found == null) {
			problems.add(String.format(Locale.ROOT, "%s at %s: no size's tree in place (block %s)", f.species().id(), pos,
					level.getBlockState(pos)));
		} else if (expected != null && found != expected) {
			problems.add(String.format(Locale.ROOT, "%s at %s: grew %s, the tree in place is %s", f.species().id(), pos, expected, found));
		}
		int height = 0;
		if (tree != null) {
			for (int i = 0; i < tree.woodCount(); i++) {
				height = Math.max(height, tree.woodY(i) + 1);
			}
			for (int i = 0; i < tree.leafCount(); i++) {
				height = Math.max(height, tree.leafY(i) + 1);
			}
		}
		return new Grown(f, pos, found, forced, meal, height, tree == null ? 0 : tree.woodCount());
	}

	private static @Nullable TreeVoxels plan(ServerLevel level, SpeciesSaplingBlock sapling, SaplingSize size, BlockPos pos) {
		Feature feature = level.registryAccess().lookupOrThrow(Registries.FEATURE).getValue(sapling.feature(size));
		return feature instanceof ProceduralTreeFeature t ? t.plan(level.getSeed(), pos) : null;
	}

	/** Wood blocks of a planned tree that the world does not hold. */
	private static int missing(ServerLevel level, TreeFoliage f, TreeVoxels v) {
		int n = 0;
		for (int k = 0; k < v.woodCount(); k++) {
			BlockPos w = new BlockPos(v.x() + v.woodX(k), v.y() + v.woodY(k), v.z() + v.woodZ(k));
			if (level.getBlockState(w) != ProceduralTreeFeature.wood(f.species(), v.bark(k), v.radius(k),
					Direction.from3DDataValue(v.parent(k)))) {
				n++;
			}
		}
		return n;
	}

	/** The shares of the sizes among the random saplings, per species and together. */
	private static List<String> distribution(List<Grown> all, List<String> problems) {
		List<String> out = new ArrayList<>();
		Map<SaplingSize, Integer> total = new EnumMap<>(SaplingSize.class);
		int n = 0;
		for (TreeFoliage f : TreeFoliage.values()) {
			Map<SaplingSize, Integer> c = new EnumMap<>(SaplingSize.class);
			int meal = 0;
			int k = 0;
			for (Grown g : all) {
				if (g.forced() || g.foliage() != f || g.size() == null) {
					continue;
				}
				c.merge(g.size(), 1, Integer::sum);
				total.merge(g.size(), 1, Integer::sum);
				meal += g.meal();
				k++;
				n++;
			}
			String line = String.format(Locale.ROOT, "distribution %s: small %d, medium %d, large %d of %d; bone meal %.1f per tree",
					f.species().id(), c.getOrDefault(SaplingSize.SMALL, 0), c.getOrDefault(SaplingSize.MEDIUM, 0),
					c.getOrDefault(SaplingSize.LARGE, 0), k, k == 0 ? 0.0 : meal / (double) k);
			out.add(line);
			PolishForests.LOG.info("[saplings] {}", line);
		}
		StringBuilder sb = new StringBuilder("distribution all species:");
		for (SaplingSize s : SaplingSize.values()) {
			double share = n == 0 ? 0 : total.getOrDefault(s, 0) / (double) n;
			sb.append(String.format(Locale.ROOT, " %s %d (%.1f%%, weight %.0f%%)", s.name().toLowerCase(Locale.ROOT), total.getOrDefault(s, 0),
					100 * share, 100 * s.share()));
			if (total.getOrDefault(s, 0) == 0 || Math.abs(share - s.share()) > SHARE_TOLERANCE) {
				problems.add(String.format(Locale.ROOT, "share of %s %.3f, weight %.2f", s, share, s.share()));
			}
		}
		out.add(sb.toString());
		PolishForests.LOG.info("[saplings] {}", sb);
		return out;
	}

	/** The fallback under a ceiling (on the server): a large draw grows the small tree; no room for the trunk, no growth. */
	private static List<String> fallback(ServerLevel level, int y, int z, List<String> problems) {
		List<String> out = new ArrayList<>();
		TreeFoliage[] all = TreeFoliage.values();
		for (int i = 0; i < all.length; i++) {
			SpeciesSaplingBlock sapling = ModBlocks.sapling(all[i]);
			BlockPos pos = new BlockPos(i * STEP, y, z);
			TreeVoxels small = plan(level, sapling, SaplingSize.SMALL, pos);
			TreeVoxels medium = plan(level, sapling, SaplingSize.MEDIUM, pos);
			int top = 0;
			for (int k = 0; k < small.woodCount(); k++) {
				top = Math.max(top, small.woodY(k));
			}
			int mediumTop = 0;
			for (int k = 0; k < medium.woodCount(); k++) {
				mediumTop = Math.max(mediumTop, medium.woodY(k));
			}
			ceiling(level, pos, top + 1);
			level.setBlock(pos, sapling.defaultBlockState().setValue(SaplingBlock.STAGE, 1), 3);
			SaplingSize got = sapling.grow(level, pos, level.getBlockState(pos), level.getRandom(), SaplingSize.LARGE);
			boolean exact = got != null && missing(level, all[i], small) == 0;
			String line = String.format(Locale.ROOT, "fallback %s: ceiling %d above (small wood up to %d, medium %d), large draw grew %s%s",
					all[i].species().id(), top + 1, top, mediumTop, got, exact ? " (the small tree in place)" : "");
			out.add(line);
			PolishForests.LOG.info("[saplings] {}", line);
			if (mediumTop > top && (got != SaplingSize.SMALL || !exact)) {
				problems.add(line);
			}
			// No room for the trunk: two blocks above.
			BlockPos low = pos.offset(0, 0, STEP);
			ceiling(level, low, 2);
			level.setBlock(low, sapling.defaultBlockState(), 3);
			boolean meal = sapling.isValidBonemealTarget(level, low, level.getBlockState(low), BonemealSource.INTERACTION);
			for (int k = 0; k < 4; k++) {
				sapling.advanceTree(level, low, level.getBlockState(low), level.getRandom());
			}
			boolean stays = level.getBlockState(low).is(sapling);
			String low2 = String.format(Locale.ROOT, "blocked %s: ceiling 2 above, takes bone meal %s, still a sapling %s",
					all[i].species().id(), meal, stays);
			out.add(low2);
			if (meal || !stays) {
				problems.add(low2);
			}
		}
		return out;
	}

	/** A stone layer of 9 × 9 blocks {@code dy} blocks above {@code pos}. */
	private static void ceiling(ServerLevel level, BlockPos pos, int dy) {
		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = -4; dz <= 4; dz++) {
				level.setBlock(pos.offset(dx, dy, dz), Blocks.STONE.defaultBlockState(), 2);
			}
		}
	}

	/**
	 * Fells a grown tree (on the server): by hand, every wood block from the top down, or with one break of the felling
	 * mod; counts the logs and sticks dropped around it.
	 */
	private static String fell(MinecraftServer server, Grown g, @Nullable String mod, List<String> problems) {
		ServerLevel level = server.overworld();
		ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
		TreeWood.Tree t = TreeWood.GAME.of(level::getBlockState, g.pos());
		String name = g.foliage().species().id() + " " + g.size();
		if (t == null) {
			problems.add("fell " + name + ": no tree at " + g.pos());
			return "fell " + name + ": no tree";
		}
		int[] b = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
		for (BlockPos p : t.wood()) {
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
		long parts = FellingCompat.FALLING_TREE_PARTS.sum();
		if (mod == null) {
			List<BlockPos> order = new ArrayList<>(t.wood());
			order.sort((p, q) -> Integer.compare(q.getY(), p.getY()));
			for (BlockPos p : order) {
				player.gameMode.destroyBlock(p);
			}
		} else {
			player.gameMode.destroyBlock(t.wood().contains(g.pos().above()) ? g.pos().above() : g.pos());
		}
		if (crouch) {
			player.setShiftKeyDown(false);
			player.setPose(Pose.STANDING);
		}
		int left = 0;
		for (BlockPos p : t.wood()) {
			left += TreeWood.GAME.wood(level.getBlockState(p)) ? 1 : 0;
		}
		int logs = 0;
		int sticks = 0;
		AABB box = new AABB(b[0] - 3, b[1] - 3, b[2] - 3, b[3] + 4, b[4] + 4, b[5] + 4);
		for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, box)) {
			ItemStack st = e.getItem();
			if (st.is(ItemTags.LOGS)) {
				logs += st.getCount();
			} else if (st.is(Items.STICK)) {
				sticks += st.getCount();
			}
			e.discard();
		}
		String line = String.format(Locale.ROOT, "fell %s %s: wood %d, left %d, logs %d (expected %d-%d, stem bases %d), sticks %d%s",
				mod == null ? "by hand" : "with " + mod, name, t.size(), left, logs, t.logDrops(), t.logDrops() + t.bases(), t.bases(), sticks,
				FellingCompat.FALLING_TREE.equals(mod) ? ", FallingTree parts with their position " + (FellingCompat.FALLING_TREE_PARTS.sum() - parts) : "");
		if (left > 0 || logs < Math.max(1, t.logDrops()) || logs > t.logDrops() + t.bases()) {
			problems.add(line);
		}
		if (g.size() == SaplingSize.SMALL && (t.bases() != 1 || logs < 1 || logs > 2)) {
			problems.add("small tree not 1-2 logs from the stem base: " + line);
		}
		return line;
	}

	/** Tree Harvester replants a sapling at the base later (G1-k): feeds it bone meal, it must grow a tree of one size. */
	private static String replant(ServerLevel level, Grown g) {
		String name = "replant " + g.foliage().species().id() + " " + g.size();
		BlockState st = level.getBlockState(g.pos());
		if (!(st.getBlock() instanceof SpeciesSaplingBlock sapling)) {
			return name + ": no sapling replanted (" + st + ")";
		}
		for (int k = 0; k < MAX_MEAL && level.getBlockState(g.pos()).is(sapling); k++) {
			BlockState s = level.getBlockState(g.pos());
			if (sapling.isValidBonemealTarget(level, g.pos(), s, BonemealSource.INTERACTION)) {
				sapling.performBonemeal(level, level.getRandom(), g.pos(), s, BonemealSource.INTERACTION);
			}
		}
		for (SaplingSize s : SaplingSize.values()) {
			TreeVoxels v = plan(level, sapling, s, g.pos());
			if (v != null && missing(level, g.foliage(), v) == 0) {
				return name + ": the replanted " + BuiltInRegistries.BLOCK.getKey(sapling) + " grew " + s;
			}
		}
		return name + ": the replanted sapling did not grow (" + level.getBlockState(g.pos()) + ")";
	}

	/** A view from the camera at (x, y, z) with a yaw and pitch. */
	private static void view(ClientGameTestContext context, TestSingleplayerContext sp, String name, double x, double y, double z,
			double yaw, double pitch) {
		sp.getServer().runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch));
		ViewReady.shotBoard(context, sp, name, SIZE, 3);
	}

	private static void write(List<String> lines) {
		// Next to the felling results (the build directory; the game runs in build/run/clientGameTest).
		Path dir = Path.of(System.getProperty("polishforests.felling.out", "build/felling")).resolveSibling("saplings");
		try {
			Files.createDirectories(dir);
			Files.write(dir.resolve("saplings.txt"), lines, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
