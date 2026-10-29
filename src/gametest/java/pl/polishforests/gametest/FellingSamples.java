package pl.polishforests.gametest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.phys.AABB;
import pl.polishforests.PolishForests;
import pl.polishforests.block.ModBlocks;
import pl.polishforests.block.TrunkBlock;
import pl.polishforests.compat.FellingCompat;
import pl.polishforests.compat.TreeWood;
import pl.polishforests.season.SubSeason;
import pl.polishforests.worldgen.feature.DeadwoodFeature;
import pl.polishforests.worldgen.feature.ModFeatures;
import pl.polishforests.worldgen.feature.ProceduralTreeFeature;
import pl.polishforests.worldgen.feature.TreeRoots;
import pl.polishforests.worldgen.habitat.Species;
import pl.polishforests.worldgen.tree.AgeClass;
import pl.polishforests.worldgen.tree.CrownLayer;
import pl.polishforests.worldgen.tree.DeadwoodPlan;
import pl.polishforests.worldgen.tree.DeadwoodVoxels;
import pl.polishforests.worldgen.tree.RootPlan;
import pl.polishforests.worldgen.tree.RootVoxels;
import pl.polishforests.worldgen.tree.TreeRequest;
import pl.polishforests.worldgen.tree.TreeShapePlan;
import pl.polishforests.worldgen.tree.TreeVoxels;

/**
 * The tree-felling mods of step G1 ({@code -Pgametest=slice -Psites=felling}, with {@code -PtreeFelling=fallingtree} or
 * {@code -PtreeFelling=treeharvester}; FallingTree runs with the recommended configuration of the modpack). A survival
 * player with a netherite axe fells trees with one break of a block ({@code ServerPlayerGameMode#destroyBlock}, the path
 * of a real break, with the mods' events; crouching for Tree Harvester, standing for FallingTree):
 * <ul>
 * <li>a board in the flat test world: every species of the slice in every age class as a canopy tree, with a tree of the
 * same species and age so close that the crowns overlap, the root flares and underground roots of mature and old trees
 * (step I1), a shrub stem of the same trunk block one block diagonally from the trunk base (DP-34), and at the old oak
 * and the old pine a lying log and a windthrow (step V11), which are cut after the tree;</li>
 * <li>the worlds of the slice in both scales (natural mode, summer): at three sample plots the two largest trees and the
 * two nearest the plot center.</li>
 * </ul>
 * Each felling is checked: the whole tree as the parent graph gives it ({@link TreeWood}: trunk, branches, flares) is gone;
 * no other wood (the neighbor, the shrub stem, dead wood), no ground block (soil, the underground roots; dirt and grass
 * turning into each other on random ticks only noted) changed; the
 * drops are the tree's log-giving blocks as logs and its thin wood as sticks; the cut-off leaves fall within
 * {@value #LEAF_TICKS} ticks and the leaves the neighbors' wood holds stay; the break takes at most {@value #MAX_MS} ms
 * (only reported with {@code -PtimingsReportOnly}). Results in {@code build/felling/<mod>.txt} and the log
 * ({@code [felling]}); before/after screenshots {@code felling_<…>_before|after}.
 */
final class FellingSamples {
	private static final int[] SIZE = {1280, 720};
	/** Board: distance of the cells, their half size, soil depth, lift of the ground above the flat world. */
	private static final int CELL = 72;
	private static final int HALF = 22;
	private static final int SOIL = 9;
	private static final int LIFT = 10;
	/** Margin of the checked box around a tree's wood, blocks (the leaves' reach); sources of wood one more margin out. */
	private static final int MARGIN = 7;
	/** The cut-off leaves must be gone after this many ticks. */
	private static final int LEAF_TICKS = 600;
	/** Longest break of one tree, ms. */
	private static final double MAX_MS = 250;
	/** Plots of the worlds and the trees felled at each: the largest and the nearest to the center. */
	private static final List<String> PLOTS = List.of("oak_hornbeam_natural_1", "fresh_pine_managed_old_growth",
			"oak_hornbeam_managed_pole_stage");
	private static final int PLOT_RADIUS = 28;
	private static final int LARGEST = 2;
	private static final int NEAREST = 2;
	/** Board cells with before/after screenshots. */
	private static final Set<String> SHOTS = Set.of("oak_old_growth", "scots_pine_old_growth", "beech_old_growth", "spruce_mature",
			"hornbeam_pole_stage");
	private static final boolean REPORT_ONLY = Boolean.getBoolean("polishforests.timings.reportOnly");

	private FellingSamples() {
	}

	/** One felling: what was found before and what changed. */
	private static final class Felling {
		final String name;
		final BlockPos hit;
		final Set<BlockPos> wood;
		final int logDrops;
		/** Bases of thin stems on the ground among the log-giving blocks: each gives 1-2 logs (step S1). */
		final int bases;
		final int[] inner;
		final Map<BlockPos, BlockState> before = new HashMap<>();
		final Set<BlockPos> neighborLeaves = new HashSet<>();
		int leavesBefore;
		int orphansBefore;
		double ms;
		int removed;
		int logs;
		int sticks;
		int otherItems;
		int durability;
		int minSticks;
		int maxSticks;
		int leafTicks = -1;
		final List<String> problems = new ArrayList<>();
		final List<String> notes = new ArrayList<>();

		Felling(String name, BlockPos hit, TreeWood.Tree tree) {
			this.name = name;
			this.hit = hit;
			this.wood = new HashSet<>(tree.wood());
			this.logDrops = tree.logDrops();
			this.bases = tree.bases();
			int[] b = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
			for (BlockPos p : wood) {
				b[0] = Math.min(b[0], p.getX());
				b[1] = Math.min(b[1], p.getY());
				b[2] = Math.min(b[2], p.getZ());
				b[3] = Math.max(b[3], p.getX());
				b[4] = Math.max(b[4], p.getY());
				b[5] = Math.max(b[5], p.getZ());
			}
			inner = new int[] {b[0] - MARGIN, b[1] - MARGIN, b[2] - MARGIN, b[3] + MARGIN, b[4] + MARGIN, b[5] + MARGIN};
		}

		String line() {
			return String.format(Locale.ROOT, "%s: wood %d, removed %d, %.1f ms; drops: logs %d (expected %s), sticks %d (%d-%d), other %d; "
					+ "axe damage %d; leaves %d, cut-off leaves gone after %s ticks%s%s", name, wood.size(), removed, ms, logs, bases == 0
					? Integer.toString(logDrops) : logDrops + "-" + (logDrops + bases), sticks,
					minSticks, maxSticks, otherItems, durability, leavesBefore, leafTicks < 0 ? "never" : Integer.toString(leafTicks),
					notes.isEmpty() ? "" : "; " + String.join("; ", notes), problems.isEmpty() ? "" : "; PROBLEMS: " + String.join("; ",
					problems));
		}
	}

	static void run(ClientGameTestContext context) {
		String mod = FabricLoader.getInstance().isModLoaded(FellingCompat.FALLING_TREE) ? FellingCompat.FALLING_TREE
				: FabricLoader.getInstance().isModLoaded(FellingCompat.TREE_HARVESTER) ? FellingCompat.TREE_HARVESTER : null;
		if (mod == null) {
			throw new AssertionError("[felling] no tree-felling mod: run with -PtreeFelling=fallingtree or -PtreeFelling=treeharvester");
		}
		context.runOnClient(mc -> {
			mc.options.ambientOcclusion().set(true);
			mc.options.renderDistance().set(8);
		});
		List<Felling> done = new ArrayList<>();
		board(context, mod, done);
		ConcurrentLinkedQueue<long[]> woody = new ConcurrentLinkedQueue<>();
		ModFeatures.WOODY_PLACEMENTS = (at, s) -> woody.add(new long[] {at.getX(), at.getY(), at.getZ()});
		try {
			for (boolean real : new boolean[] {true, false}) {
				woody.clear();
				world(context, mod, real, woody, done);
			}
		} finally {
			ModFeatures.WOODY_PLACEMENTS = null;
		}
		String hooks = String.format(Locale.ROOT, "hooks: FallingTree steps %d, Tree Harvester trees %d, leaf clearings skipped %d",
				FellingCompat.FALLING_TREE_STEPS.sum(), FellingCompat.TREE_HARVESTER_TREES.sum(), FellingCompat.TREE_HARVESTER_LEAF_SKIPS.sum());
		List<String> lines = new ArrayList<>();
		lines.add("# Step G1: felling with " + mod + " (" + hooks + ")");
		int bad = 0;
		double maxMs = 0;
		int maxLeafTicks = 0;
		for (Felling f : done) {
			lines.add(f.line());
			bad += f.problems.isEmpty() ? 0 : 1;
			maxMs = Math.max(maxMs, f.ms);
			maxLeafTicks = Math.max(maxLeafTicks, f.leafTicks);
		}
		String summary = String.format(Locale.ROOT, "%s: %d fellings, %d with problems; longest break %.1f ms, slowest leaf fall %d ticks; %s",
				mod, done.size(), bad, maxMs, maxLeafTicks, hooks);
		lines.add("# " + summary);
		write(mod, lines);
		PolishForests.LOG.info("[felling] {}", summary);
		boolean hooked = mod.equals(FellingCompat.FALLING_TREE) ? FellingCompat.FALLING_TREE_STEPS.sum() > 0
				: FellingCompat.TREE_HARVESTER_TREES.sum() > 0;
		if (!hooked) {
			throw new AssertionError("[felling] the compatibility hooks of " + mod + " never ran (a changed method of the mod?)");
		}
		if (bad > 0) {
			throw new AssertionError("[felling] " + bad + " of " + done.size() + " fellings with problems, see build/felling/" + mod + ".txt");
		}
	}

	private static void write(String mod, List<String> lines) {
		Path dir = Path.of(System.getProperty("polishforests.felling.out", "build/felling"));
		try {
			Files.createDirectories(dir);
			Files.write(dir.resolve(mod + ".txt"), lines, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	// ------------------------------------------------------------------ board

	private static List<Species> species() {
		return List.of(Species.OAK, Species.BEECH, Species.LINDEN, Species.HORNBEAM, Species.NORWAY_MAPLE, Species.ASH,
				Species.SPRUCE, Species.SCOTS_PINE, Species.BIRCH);
	}

	/** A board cell as built: the tree to fell, its neighbor, the dead wood beside it. */
	private record Cell(String name, BlockPos base, BlockPos neighbor, List<BlockPos> dead, double height) {
	}

	private static void board(ClientGameTestContext context, String mod, List<Felling> done) {
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			setUp(sp);
			hideHud(context);
			int y = sp.getServer().computeOnServer(s -> s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0)) + LIFT;
			int i = 0;
			for (Species s : species()) {
				for (AgeClass age : AgeClass.values()) {
					int cx = CELL * i++;
					String name = s.id() + "_" + age.name().toLowerCase(Locale.ROOT);
					stand(sp, cx + 0.5 - 8, y + 2, 0.5 - 8);
					context.waitTicks(40);
					Cell cell = sp.getServer().computeOnServer(server -> build(server.overworld(), name, s, age, cx, y));
					context.waitTicks(40);
					boolean shots = SHOTS.contains(name);
					if (shots) {
						shot(context, sp, "felling_board_" + name + "_before", cell, true);
					}
					stand(sp, cx + 0.5 - 8, y + 2, 0.5 - 8);
					fell(context, sp, mod, "board " + name, cell.base(), true, done);
					for (int k = 0; k < cell.dead().size(); k++) {
						BlockPos d = cell.dead().get(k);
						fell(context, sp, mod, "board " + name + " dead wood " + (k == 0 ? "log" : "windthrow"), d, false, done);
					}
					if (shots) {
						shot(context, sp, "felling_board_" + name + "_after", cell, true);
					}
				}
			}
		}
	}

	/** Builds a board cell on the server. */
	private static Cell build(ServerLevel level, String name, Species s, AgeClass age, int cx, int y) {
		platform(level, cx, y, 0);
		TreeVoxels a = TreeShapePlan.plan(new TreeRequest(s, CrownLayer.CANOPY, age, level.getSeed(), cx, y, 0));
		double ra = crownRadius(a);
		TreeVoxels probe = TreeShapePlan.plan(new TreeRequest(s, CrownLayer.CANOPY, age, level.getSeed(), cx + 16, y, 1));
		int gap = (int) Math.clamp(Math.round(0.75 * (ra + crownRadius(probe))), 3, HALF - 4);
		TreeVoxels b = TreeShapePlan.plan(new TreeRequest(s, CrownLayer.CANOPY, age, level.getSeed(), cx + gap, y, 1));
		ProceduralTreeFeature.write(level, a, s, CrownLayer.CANOPY, age);
		ProceduralTreeFeature.write(level, b, s, CrownLayer.CANOPY, age);
		int top = y - 1;
		TreeShapePlan.Ground ground = (wx, wz) -> Math.abs(wx - cx) <= HALF && Math.abs(wz) <= HALF ? top : TreeShapePlan.Ground.UNKNOWN;
		if (age != AgeClass.POLE_STAGE) {
			for (TreeVoxels t : List.of(a, b)) {
				RootVoxels roots = RootPlan.plan(new RootPlan.Request(s, CrownLayer.CANOPY, age, level.getSeed(), t.x(), y, t.z(), t.radius(0),
						ground, null));
				TreeRoots.write(level, roots, s);
			}
		}
		// A shrub stem of the same trunk block one block diagonally from the trunk base (DP-34): 3 blocks of 1 px with leaves.
		TrunkBlock trunk = ModBlocks.trunk(ProceduralTreeFeature.trunk(s, TreeVoxels.LOWER));
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		boolean free = true;
		for (int dy = 0; dy < 3; dy++) {
			BlockState here = level.getBlockState(p.set(cx - 1, y + dy, -1));
			free &= here.isAir() || here.getBlock() instanceof LeavesBlock;
		}
		if (free) {
			for (int dy = 0; dy < 3; dy++) {
				level.setBlock(p.set(cx - 1, y + dy, -1), trunk.state(1, Direction.DOWN), 2);
			}
			BlockState leaf = ProceduralTreeFeature.leaves(s, 0).setValue(LeavesBlock.DISTANCE, 1);
			for (Direction d : List.of(Direction.UP, Direction.WEST, Direction.NORTH)) {
				BlockPos l = new BlockPos(cx - 1, y + 2, -1).relative(d);
				if (level.getBlockState(l).isAir()) {
					level.setBlock(l, leaf, 2);
				}
			}
		}
		List<BlockPos> dead = new ArrayList<>();
		if (age == AgeClass.OLD_GROWTH && (s == Species.OAK || s == Species.SCOTS_PINE)) {
			int[][] at = {{cx - 3, -5}, {cx + 4, -7}};
			DeadwoodPlan.Kind[] kinds = {DeadwoodPlan.Kind.LOG, DeadwoodPlan.Kind.WINDTHROW};
			for (int k = 0; k < kinds.length; k++) {
				DeadwoodVoxels v = DeadwoodPlan.plan(new DeadwoodPlan.Request(s, kinds[k], CrownLayer.CANOPY, AgeClass.OLD_GROWTH, true,
						level.getSeed(), at[k][0], y, at[k][1], ground));
				if (v.empty() || DeadwoodFeature.write(level, v) == 0) {
					PolishForests.LOG.warn("[felling] {}: the {} could not be written", name, kinds[k]);
					continue;
				}
				// The middle block of the dead wood (a block of its wood that is wood in the world).
				TreeVoxels w = v.wood();
				for (int j = w.woodCount() / 2; j < w.woodCount(); j++) {
					BlockPos q = new BlockPos(w.x() + w.woodX(j), w.y() + w.woodY(j), w.z() + w.woodZ(j));
					if (TreeWood.GAME.wood(level.getBlockState(q))) {
						dead.add(q);
						break;
					}
				}
			}
		}
		double height = 0;
		for (int j = 0; j < a.woodCount(); j++) {
			height = Math.max(height, a.woodY(j));
		}
		return new Cell(name, new BlockPos(a.x(), a.y(), a.z()), new BlockPos(b.x(), b.y(), b.z()), dead, height);
	}

	private static double crownRadius(TreeVoxels v) {
		double r = 0;
		for (int i = 0; i < v.leafCount(); i++) {
			r = Math.max(r, Math.hypot(v.leafX(i), v.leafZ(i)));
		}
		return r;
	}

	/** A raised platform of soil: grass on top, dirt below, {@value #SOIL} blocks deep. */
	private static void platform(ServerLevel level, int x, int y, int z) {
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int dx = -HALF; dx <= HALF; dx++) {
			for (int dz = -HALF; dz <= HALF; dz++) {
				for (int dy = -SOIL; dy <= -1; dy++) {
					level.setBlock(p.set(x + dx, y + dy, z + dz), dy == -1 ? Blocks.GRASS_BLOCK.defaultBlockState()
							: Blocks.DIRT.defaultBlockState(), 2);
				}
			}
		}
	}

	// ------------------------------------------------------------------ worlds

	private static void world(ClientGameTestContext context, String mod, boolean real, ConcurrentLinkedQueue<long[]> woody,
			List<Felling> done) {
		String scale = real ? "realistic" : "gameplay";
		ResourceKey<WorldPreset> preset = ResourceKey.create(Registries.WORLD_PRESET, PolishForests.id(real ? "poland" : "poland_gameplay"));
		try (TestSingleplayerContext sp = context.worldBuilder().adjustSettings(ui -> SliceClientGameTest.select(ui, preset, false))
				.create()) {
			setUp(sp);
			hideHud(context);
			List<SlicePlots.Plot> plots = SlicePlots.of(real).stream().filter(p -> PLOTS.contains(p.name())).toList();
			for (SlicePlots.Plot plot : plots) {
				stand(sp, plot.x() + 0.5, 200, plot.z() + 0.5);
				context.waitTicks(20 * 25);
				List<BlockPos> trees = sp.getServer().computeOnServer(s -> pick(s.overworld(), List.copyOf(woody), plot));
				PolishForests.LOG.info("[felling] {} {}: trees to fell {}", scale, plot.name(), trees);
				for (int k = 0; k < trees.size(); k++) {
					BlockPos base = trees.get(k);
					String name = scale + " " + plot.name() + " " + (k < LARGEST ? "largest " + (k + 1) : "nearest " + (k - LARGEST + 1));
					Cell view = new Cell(name, base, base, List.of(), sp.getServer().computeOnServer(s -> height(s.overworld(), base)));
					String shotName = "felling_" + scale + "_" + plot.name() + "_" + (k + 1);
					if (k == 0) {
						shot(context, sp, shotName + "_before", view, false);
						shotAbove(context, sp, shotName + "_above_before", view);
					}
					stand(sp, base.getX() + 3.5, base.getY() + 2, base.getZ() + 3.5);
					context.waitTicks(20);
					fell(context, sp, mod, name, base, true, done);
					if (k == 0) {
						shot(context, sp, shotName + "_after", view, false);
						shotAbove(context, sp, shotName + "_above_after", view);
					}
				}
			}
		}
	}

	/** The trees to fell at a plot: the largest and the nearest to the center within {@link #PLOT_RADIUS}. */
	private static List<BlockPos> pick(ServerLevel level, List<long[]> woody, SlicePlots.Plot plot) {
		record Candidate(BlockPos base, int size, double distance) {
		}
		List<Candidate> all = new ArrayList<>();
		Set<BlockPos> seen = new HashSet<>();
		for (long[] w : woody) {
			double d = Math.hypot(w[0] - plot.x(), w[2] - plot.z());
			if (d > PLOT_RADIUS) {
				continue;
			}
			BlockPos base = new BlockPos((int) w[0], (int) w[1], (int) w[2]);
			if (!TreeWood.GAME.trunk(level.getBlockState(base))) {
				base = base.above();
			}
			if (!TreeWood.GAME.trunk(level.getBlockState(base)) || !seen.add(base)) {
				continue;
			}
			TreeWood.Tree t = TreeWood.GAME.of(level::getBlockState, base);
			// Trees only: a crown with leaves and more than a shrub's wood.
			if (t == null || t.size() < 30 || TreeWood.leavesAround(level::getBlockState, t.wood(), 9, false) < 9) {
				continue;
			}
			all.add(new Candidate(base, t.size(), d));
		}
		List<BlockPos> out = new ArrayList<>();
		all.stream().sorted(Comparator.comparingInt(Candidate::size).reversed()).limit(LARGEST).forEach(c -> out.add(c.base()));
		all.stream().sorted(Comparator.comparingDouble(Candidate::distance)).filter(c -> !out.contains(c.base())).limit(NEAREST)
				.forEach(c -> out.add(c.base()));
		return out;
	}

	private static double height(ServerLevel level, BlockPos base) {
		TreeWood.Tree t = TreeWood.GAME.of(level::getBlockState, base);
		return t == null ? 10 : t.wood().stream().mapToInt(BlockPos::getY).max().orElse(base.getY()) - base.getY();
	}

	// ------------------------------------------------------------------ felling

	/**
	 * Fells the tree (or dead wood) with wood at {@code start}: one break of a block with the axe, then the checks; adds the
	 * result to {@code done}. {@code tree} is false for dead wood, which the mods need not fell whole (no leaves).
	 */
	private static void fell(ClientGameTestContext context, TestSingleplayerContext sp, String mod, String name, BlockPos start,
			boolean tree, List<Felling> done) {
		boolean crouch = mod.equals(FellingCompat.TREE_HARVESTER);
		Felling f = sp.getServer().computeOnServer(s -> cut(s, name, start, crouch, tree));
		if (f == null) {
			PolishForests.LOG.warn("[felling] {}: no wood of this mod at {}", name, start);
			return;
		}
		// The cut-off leaves fall: the count of leaves out of reach of any wood until none is left.
		int waited = 0;
		while (waited < LEAF_TICKS) {
			context.waitTicks(20);
			waited += 20;
			int orphans = sp.getServer().computeOnServer(s -> orphans(s.overworld(), f.inner));
			if (orphans <= f.orphansBefore) {
				f.leafTicks = waited;
				break;
			}
		}
		sp.getServer().runOnServer(s -> after(s.overworld(), f, tree));
		PolishForests.LOG.info("[felling] {}", f.line());
		done.add(f);
	}

	/** Records the box, breaks the block, counts the drops and what was removed. */
	private static Felling cut(MinecraftServer server, String name, BlockPos start, boolean crouch, boolean tree) {
		ServerLevel level = server.overworld();
		ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
		TreeWood.Tree t = TreeWood.GAME.of(level::getBlockState, start);
		if (t == null) {
			return null;
		}
		BlockPos hit = start;
		if (tree && t.wood().contains(start.above())) {
			hit = start.above();
		}
		Felling f = new Felling(name, hit, t);
		int[] b = f.inner;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int x = b[0]; x <= b[3]; x++) {
			for (int y = b[1]; y <= b[4]; y++) {
				for (int z = b[2]; z <= b[5]; z++) {
					BlockState s = level.getBlockState(p.set(x, y, z));
					if (!s.isAir()) {
						f.before.put(p.immutable(), s);
						if (s.getBlock() instanceof LeavesBlock) {
							f.leavesBefore++;
						}
					}
				}
			}
		}
		f.neighborLeaves.addAll(supported(level, b, f.wood));
		f.orphansBefore = orphans(level, b);
		if (f.orphansBefore > 0) {
			f.notes.add(f.orphansBefore + " leaves without wood already before the felling");
		}
		int[] range = sticks(level, f.wood);
		f.minSticks = range[0];
		f.maxSticks = range[1];
		ItemStack axe = new ItemStack(Items.NETHERITE_AXE);
		player.setItemInHand(InteractionHand.MAIN_HAND, axe);
		if (crouch) {
			player.setShiftKeyDown(true);
			player.setPose(Pose.CROUCHING);
		}
		long t0 = System.nanoTime();
		player.gameMode.destroyBlock(hit);
		f.ms = (System.nanoTime() - t0) / 1e6;
		if (crouch) {
			player.setShiftKeyDown(false);
			player.setPose(Pose.STANDING);
		}
		f.durability = player.getMainHandItem().getDamageValue();
		for (BlockPos q : f.wood) {
			if (!TreeWood.GAME.wood(level.getBlockState(q))) {
				f.removed++;
			}
		}
		AABB box = new AABB(b[0], b[1], b[2], b[3] + 1, b[4] + 1, b[5] + 1);
		for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, box)) {
			ItemStack st = e.getItem();
			if (st.is(ItemTags.LOGS)) {
				f.logs += st.getCount();
			} else if (st.is(Items.STICK)) {
				f.sticks += st.getCount();
			} else {
				f.otherItems += st.getCount();
			}
		}
		if (tree) {
			if (f.removed < f.wood.size()) {
				f.problems.add(String.format(Locale.ROOT, "not felled whole: %d of %d wood blocks left (leaves around the top wood: %d)",
						f.wood.size() - f.removed, f.wood.size(), topLeaves(level, f.wood)));
			}
			if (f.removed == f.wood.size() && (f.logs < f.logDrops || f.logs > f.logDrops + f.bases)) {
				f.problems.add("logs dropped " + f.logs + ", expected " + (f.bases == 0 ? Integer.toString(f.logDrops)
						: f.logDrops + "-" + (f.logDrops + f.bases)));
			}
			if (f.removed == f.wood.size() && (f.sticks < f.minSticks || f.sticks > f.maxSticks)) {
				f.problems.add("sticks " + f.sticks + " outside " + f.minSticks + "-" + f.maxSticks);
			}
			if (f.ms > MAX_MS) {
				(REPORT_ONLY ? f.notes : f.problems).add(String.format(Locale.ROOT, "break took %.0f ms (limit %.0f)", f.ms, MAX_MS));
			}
		} else {
			f.notes.add(f.removed == f.wood.size() ? "dead wood felled whole" : f.removed == 1 ? "dead wood: only the block hit"
					: "dead wood: " + f.removed + " of " + f.wood.size() + " blocks");
		}
		// Cleared, so that the next felling counts only its own drops.
		for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, box.inflate(4))) {
			e.discard();
		}
		return f;
	}

	/** The checks after the leaves fell: nothing but the tree's wood, its leaves and loose plants changed. */
	private static void after(ServerLevel level, Felling f, boolean tree) {
		int[] b = f.inner;
		int woodChanged = 0;
		int groundChanged = 0;
		int grassSpread = 0;
		int neighborLeavesLost = 0;
		int leavesFallen = 0;
		int other = 0;
		List<String> examples = new ArrayList<>();
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int x = b[0]; x <= b[3]; x++) {
			for (int y = b[1]; y <= b[4]; y++) {
				for (int z = b[2]; z <= b[5]; z++) {
					p.set(x, y, z);
					if (f.wood.contains(p)) {
						continue;
					}
					BlockState was = f.before.getOrDefault(p, Blocks.AIR.defaultBlockState());
					BlockState now = level.getBlockState(p);
					if (was == now) {
						continue;
					}
					if (TreeWood.GAME.wood(was)) {
						woodChanged++;
						if (examples.size() < 5) {
							examples.add("wood at " + p.toShortString() + " " + was + " -> " + now);
						}
					} else if (was.getBlock() instanceof LeavesBlock) {
						leavesFallen++;
						if (f.neighborLeaves.contains(p) && !(now.getBlock() instanceof LeavesBlock)) {
							neighborLeavesLost++;
						}
					} else if (soilCover(was) && soilCover(now)) {
						// Grass spreading onto the dirt under the trunk or dying back on random ticks: not the felling.
						grassSpread++;
					} else if (ground(was)) {
						groundChanged++;
						if (examples.size() < 5) {
							examples.add("ground at " + p.toShortString() + " " + was + " -> " + now);
						}
					} else {
						other++;
					}
				}
			}
		}
		int saplings = 0;
		for (BlockPos q : f.wood) {
			saplings += level.getBlockState(q).getBlock() instanceof SaplingBlock ? 1 : 0;
		}
		f.notes.add(String.format(Locale.ROOT, "leaves fallen %d, neighbor-held leaves kept %d of %d, other blocks changed %d",
				leavesFallen, f.neighborLeaves.size() - neighborLeavesLost, f.neighborLeaves.size(), other));
		if (grassSpread > 0) {
			f.notes.add("grass spread or died back on " + grassSpread + " ground blocks");
		}
		if (saplings > 0) {
			f.notes.add("sapling replanted on " + saplings + " places of the tree's wood");
		}
		if (woodChanged > 0) {
			f.problems.add(woodChanged + " blocks of other wood changed");
		}
		if (groundChanged > 0) {
			f.problems.add(groundChanged + " ground blocks changed");
		}
		if (neighborLeavesLost > 0) {
			f.problems.add(neighborLeavesLost + " leaves held by other wood fell");
		}
		if (tree && f.leafTicks < 0) {
			f.problems.add("cut-off leaves still hanging after " + LEAF_TICKS + " ticks: " + orphans(level, b));
		}
		if (!examples.isEmpty()) {
			f.problems.add("e.g. " + String.join(", ", examples));
		}
	}

	/** Dirt or grass: the soil cover that random ticks turn into each other. */
	private static boolean soilCover(BlockState s) {
		return s.is(Blocks.DIRT) || s.is(Blocks.GRASS_BLOCK);
	}

	/** Ground: full solid blocks, soil, the underground roots. */
	private static boolean ground(BlockState s) {
		return s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(ModBlocks.treeRoot()) || s.isSolidRender()
				&& !(s.getBlock() instanceof LeavesBlock);
	}

	/** Sticks a felled tree may drop: radius 2 off the stem 1-2, twigs 0-1 (a stem base on the ground gives logs, S1). */
	private static int[] sticks(ServerLevel level, Set<BlockPos> wood) {
		int min = 0;
		int max = 0;
		for (BlockPos p : wood) {
			BlockState s = level.getBlockState(p);
			if (!(s.getBlock() instanceof TrunkBlock) || TreeWood.dropsLog(s) || TreeWood.GAME.baseLog(level::getBlockState, p, s)) {
				continue;
			}
			if (s.getValue(TrunkBlock.RADIUS) == TrunkBlock.MIN_RADIUS) {
				max++;
			} else {
				min++;
				max += 2;
			}
		}
		return new int[] {min, max};
	}

	/** Leaves next to the topmost wood blocks of a tree (FallingTree needs at least one there). */
	private static int topLeaves(ServerLevel level, Set<BlockPos> wood) {
		int top = wood.stream().mapToInt(BlockPos::getY).max().orElse(0);
		int n = 0;
		for (BlockPos p : wood) {
			if (p.getY() == top) {
				for (Direction d : Direction.values()) {
					n += level.getBlockState(p.relative(d)).getBlock() instanceof LeavesBlock ? 1 : 0;
				}
			}
		}
		return n;
	}

	/**
	 * The leaves of the box that wood other than {@code exclude} holds (within 6 steps through leaves, as the vanilla
	 * distance), wood looked for up to {@link #MARGIN} blocks outside the box.
	 */
	private static Set<BlockPos> supported(ServerLevel level, int[] b, Set<BlockPos> exclude) {
		Map<Long, Integer> dist = distances(level, b, exclude);
		Set<BlockPos> out = new HashSet<>();
		for (Map.Entry<Long, Integer> e : dist.entrySet()) {
			BlockPos p = BlockPos.of(e.getKey());
			if (e.getValue() > 0 && inside(b, p)) {
				out.add(p);
			}
		}
		return out;
	}

	/** Leaves of the box (not persistent) that no wood holds any more. */
	private static int orphans(ServerLevel level, int[] b) {
		Map<Long, Integer> dist = distances(level, b, Set.of());
		int n = 0;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int x = b[0]; x <= b[3]; x++) {
			for (int y = b[1]; y <= b[4]; y++) {
				for (int z = b[2]; z <= b[5]; z++) {
					BlockState s = level.getBlockState(p.set(x, y, z));
					if (s.getBlock() instanceof LeavesBlock && !s.getValue(LeavesBlock.PERSISTENT) && !dist.containsKey(p.asLong())) {
						n++;
					}
				}
			}
		}
		return n;
	}

	/** Steps from wood (0) through leaves, up to 6, from all wood of the box widened by {@link #MARGIN} but {@code exclude}. */
	private static Map<Long, Integer> distances(ServerLevel level, int[] b, Set<BlockPos> exclude) {
		Map<Long, Integer> dist = new HashMap<>();
		ArrayDeque<BlockPos> open = new ArrayDeque<>();
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int x = b[0] - MARGIN; x <= b[3] + MARGIN; x++) {
			for (int y = b[1] - MARGIN; y <= b[4] + MARGIN; y++) {
				for (int z = b[2] - MARGIN; z <= b[5] + MARGIN; z++) {
					BlockState s = level.getBlockState(p.set(x, y, z));
					if (s.is(BlockTags.PREVENTS_NEARBY_LEAF_DECAY) && !exclude.contains(p)) {
						dist.put(p.asLong(), 0);
						open.add(p.immutable());
					}
				}
			}
		}
		while (!open.isEmpty()) {
			BlockPos q = open.poll();
			int d = dist.get(q.asLong());
			if (d >= LeavesBlock.DECAY_DISTANCE - 1) {
				continue;
			}
			for (Direction dir : Direction.values()) {
				BlockPos r = q.relative(dir);
				if (dist.containsKey(r.asLong()) || !(level.getBlockState(r).getBlock() instanceof LeavesBlock)) {
					continue;
				}
				dist.put(r.asLong(), d + 1);
				open.add(r);
			}
		}
		return dist;
	}

	private static boolean inside(int[] b, BlockPos p) {
		return p.getX() >= b[0] && p.getX() <= b[3] && p.getY() >= b[1] && p.getY() <= b[4] && p.getZ() >= b[2] && p.getZ() <= b[5];
	}

	// ------------------------------------------------------------------ helpers

	static void setUp(TestSingleplayerContext sp) {
		sp.getServer().runCommand("gamerule advance_time false");
		sp.getServer().runCommand("gamerule advance_weather false");
		sp.getServer().runCommand("gamerule spawn_mobs false");
		sp.getServer().runCommand("gamerule send_command_feedback false");
		sp.getServer().runCommand("gamerule fall_damage false");
		SeasonDates.set(sp, SubSeason.MID_SUMMER, 4, "felling");
		sp.getServer().runCommand("time set 6000");
		sp.getServer().runCommand("weather clear");
		sp.getServer().runCommand("difficulty peaceful");
		sp.getServer().runOnServer(s -> {
			ServerPlayer player = s.getPlayerList().getPlayers().getFirst();
			player.setGameMode(GameType.SURVIVAL);
			player.getAbilities().invulnerable = true;
			player.getAbilities().mayfly = true;
			player.getAbilities().flying = true;
			player.onUpdateAbilities();
		});
	}

	/** Puts the (flying, survival) player at a place. */
	static void stand(TestSingleplayerContext sp, double x, double y, double z) {
		sp.getServer().runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f", x, y, z));
	}

	static void hideHud(ClientGameTestContext context) {
		context.runOnClient(mc -> {
			if (!mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
	}

	/**
	 * A view down on a tree of a world from above the canopy (south of it, steeply down), where the gap it leaves shows;
	 * from the ground the trunks of a dense stand hide it.
	 */
	private static void shotAbove(ClientGameTestContext context, TestSingleplayerContext sp, String name, Cell cell) {
		double x = cell.base().getX() + 0.5;
		double z = cell.base().getZ() + 0.5 + 12;
		double y = cell.base().getY() + cell.height() + 22;
		sp.getServer().runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f 180.0 60.0", x, y, z));
		ViewReady.shot(context, sp, name, SIZE, 3);
	}

	/** A view of a tree from the south-east, far enough for its height. */
	private static void shot(ClientGameTestContext context, TestSingleplayerContext sp, String name, Cell cell, boolean board) {
		double d = Math.max(10, cell.height() * 1.1);
		double x = cell.base().getX() + 0.5 + d * 0.6;
		double z = cell.base().getZ() + 0.5 + d * 0.8;
		double y = cell.base().getY() + Math.min(8, cell.height() * 0.2);
		double tx = cell.base().getX() + 0.5 - x;
		double tz = cell.base().getZ() + 0.5 - z;
		double ty = cell.base().getY() + cell.height() * 0.45 - (y + 1.62);
		double yaw = Math.toDegrees(Math.atan2(-tx, tz));
		double pitch = -Math.toDegrees(Math.atan2(ty, Math.hypot(tx, tz)));
		sp.getServer().runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch));
		if (board) {
			// Flat test world: the short margin of step T8.
			ViewReady.shotBoard(context, sp, name, SIZE, 3);
		} else {
			ViewReady.shot(context, sp, name, SIZE, 3);
		}
		PolishForests.LOG.info(String.format(Locale.ROOT, "[felling] %s: camera (%.1f, %.1f, %.1f) yaw %.0f pitch %.0f", name, x, y, z,
				yaw, pitch));
	}
}
