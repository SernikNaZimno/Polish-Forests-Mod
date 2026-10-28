package pl.polishforests.gametest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import pl.polishforests.PolishForests;
import pl.polishforests.block.ModBlocks;
import pl.polishforests.block.RootFlareBlock;
import pl.polishforests.block.TrunkBlock;
import pl.polishforests.season.SubSeason;
import pl.polishforests.worldgen.feature.ModFeatures;
import pl.polishforests.worldgen.feature.ProceduralTreeFeature;
import pl.polishforests.worldgen.feature.TreeRoots;
import pl.polishforests.worldgen.habitat.Species;
import pl.polishforests.worldgen.tree.AgeClass;
import pl.polishforests.worldgen.tree.CrownLayer;
import pl.polishforests.worldgen.tree.RootPlan;
import pl.polishforests.worldgen.tree.RootVoxels;
import pl.polishforests.worldgen.tree.TreeRequest;
import pl.polishforests.worldgen.tree.TreeShapePlan;
import pl.polishforests.worldgen.tree.TreeVoxels;

/**
 * The root views of step I1 ({@code -Pgametest=slice -Psites=roots}). First a board in the flat test world: per species
 * of the slice a raised platform of soil (grass over dirt, {@value #SOIL} blocks deep) with a mature and an old-growth
 * tree of the tree planner and their roots ({@link RootPlan}, {@link TreeRoots#write}; every planned flare and root must
 * be in the world), a close-up of each trunk base from eye height ({@code roots_board_<species>_<age>_base}), and for the
 * old tree two copies: one with the soil dug away around the roots ({@code _dug}, the whole root system) and one cut in
 * half through the trunk ({@code _section}, the cross-section of the ground under the tree). Then the worlds of the slice
 * (both scales, natural and present-day mode, summer): at sample plots of both forests the trees with the most flare blocks
 * near the plot center from eye height ({@code roots_<scale>_<mode>_<plot>_<n>_eye}) and close ({@code _base}), and in
 * the natural mode a trench dug beside one tree of each forest with the roots left standing across it ({@code _trench}).
 * Over all roots written in those worlds the test checks: no flare of one tree within a block of another tree's flare or
 * trunk base, every flare over a sturdy block, the top ground block of every root column untouched; the log gets the
 * counts.
 */
final class RootSamples {
	private static final int[] SIZE = {1280, 720};
	/** Board: spacing of the platforms, their half size and the depth of their soil. */
	private static final int SPACING = 34;
	private static final int HALF = 11;
	private static final int SOIL = 9;
	/** Height of the board's ground above the flat world's surface. */
	private static final int LIFT = 14;
	private static final int RADIUS = 40;
	private static final List<String> PLOTS = List.of("oak_hornbeam_natural_1", "oak_hornbeam_managed_1", "fresh_pine_natural_1",
			"fresh_pine_managed_old_growth");
	private static final List<String> PRESENT_DAY_PLOTS = List.of("oak_hornbeam_natural_1", "fresh_pine_natural_1");

	private RootSamples() {
	}

	/** A tree whose roots were written, as the hook reports it. */
	private record Placed(Species species, RootVoxels plan, TreeRoots.Written written) {
	}

	static void run(ClientGameTestContext context) {
		context.runOnClient(mc -> {
			mc.options.ambientOcclusion().set(true);
			mc.options.renderDistance().set(10);
		});
		board(context);
		ConcurrentLinkedQueue<Placed> placed = new ConcurrentLinkedQueue<>();
		ConcurrentLinkedQueue<long[]> woody = new ConcurrentLinkedQueue<>();
		ModFeatures.ROOT_PLACEMENTS = (s, plan, w) -> placed.add(new Placed(s, plan, w));
		ModFeatures.WOODY_PLACEMENTS = (at, s) -> woody.add(new long[] {at.getX(), at.getY(), at.getZ()});
		try {
			for (boolean real : new boolean[] {true, false}) {
				for (boolean presentDay : new boolean[] {false, true}) {
					placed.clear();
					woody.clear();
					world(context, real, presentDay, placed, woody);
				}
			}
		} finally {
			ModFeatures.ROOT_PLACEMENTS = null;
			ModFeatures.WOODY_PLACEMENTS = null;
		}
	}

	// ------------------------------------------------------------------ board

	private static List<Species> species() {
		return List.of(Species.OAK, Species.BEECH, Species.LINDEN, Species.HORNBEAM, Species.NORWAY_MAPLE, Species.ASH,
				Species.SPRUCE, Species.SCOTS_PINE, Species.BIRCH);
	}

	/** A view of the board: a name and a camera (feet position, yaw, pitch). */
	private record View(String name, double x, double y, double z, double yaw, double pitch) {
	}

	private static void board(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			setUp(sp);
			List<View> views = sp.getServer().computeOnServer(RootSamples::build);
			hideHud(context);
			for (int i = 0; i < views.size(); i++) {
				View v = views.get(i);
				shot(context, sp, "roots_board_" + v.name(), v.x(), v.y(), v.z(), v.yaw(), v.pitch(), i == 0 ? 6 : 1, true);
			}
		}
	}

	/** Builds the board on the server; returns its views. */
	private static List<View> build(MinecraftServer server) {
		ServerLevel level = server.overworld();
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0) + LIFT;
		List<View> views = new ArrayList<>();
		StringBuilder report = new StringBuilder();
		int missing = 0;
		List<Species> species = species();
		for (int s = 0; s < species.size(); s++) {
			Species sp = species.get(s);
			int z = SPACING * s;
			// Copies: mature, old, old dug out, old cut in half.
			for (int copy = 0; copy < 4; copy++) {
				int x = SPACING * copy;
				AgeClass age = copy == 0 ? AgeClass.MATURE : AgeClass.OLD_GROWTH;
				platform(level, x, y, z);
				TreeVoxels tree = TreeShapePlan.plan(new TreeRequest(sp, CrownLayer.CANOPY, age, level.getSeed(), x, y, z));
				ProceduralTreeFeature.write(level, tree, sp, CrownLayer.CANOPY, age);
				int top = y - 1;
				RootVoxels roots = RootPlan.plan(new RootPlan.Request(sp, CrownLayer.CANOPY, age, level.getSeed(), x, y, z, tree.radius(0),
						(wx, wz) -> Math.abs(wx - x) <= HALF && Math.abs(wz - z) <= HALF ? top : TreeShapePlan.Ground.UNKNOWN, null));
				TreeRoots.Written w = TreeRoots.write(level, roots, sp);
				missing += roots.flareCount() - w.flareCount();
				for (int i = 0; i < roots.rootCount(); i++) {
					boolean root = roots.rootKind(i) == RootVoxels.ROOT;
					BlockState b = level.getBlockState(new BlockPos(x + roots.rootX(i), y + roots.rootY(i), z + roots.rootZ(i)));
					if (root && !b.is(ModBlocks.treeRoot())) {
						missing++;
					}
				}
				if (copy == 1) {
					report.append(String.format(Locale.ROOT, " %s: trunk base %d px, flare blocks %d, roots %d, rooted dirt %d;", sp.id(),
							tree.radius(0), roots.flareCount(), roots.rootCount(), roots.dirtCount()));
				}
				String name = sp.id() + "_" + age.name().toLowerCase(Locale.ROOT);
				if (copy <= 1) {
					views.add(base(name + "_base", x, y, z, level, roots));
				} else if (copy == 2) {
					dig(level, x, y, z);
					views.add(look(name + "_dug", x + 6.5, y - 1.2, z + 5.5, x + 0.5, y - 3.5, z + 0.5));
				} else {
					cut(level, x, y, z);
					views.add(look(name + "_section", x + 0.5, y - 4.1, z + 10.5, x + 0.5, y - 3.0, z + 1.0));
				}
			}
		}
		PolishForests.LOG.info("[roots] board:{}", report);
		if (missing > 0) {
			throw new AssertionError("[roots] " + missing + " planned flares or roots of the board are not in the world");
		}
		PolishForests.LOG.info("[roots] board: every planned flare and root written");
		return views;
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

	/** Digs the soil of a platform away, keeping the roots (and everything above the ground). */
	private static void dig(ServerLevel level, int x, int y, int z) {
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int dx = -HALF; dx <= HALF; dx++) {
			for (int dz = -HALF; dz <= HALF; dz++) {
				for (int dy = -SOIL; dy <= -1; dy++) {
					BlockState b = level.getBlockState(p.set(x + dx, y + dy, z + dz));
					if (!(b.getBlock() instanceof TrunkBlock)) {
						level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
					}
				}
			}
		}
	}

	/** Cuts a platform in half: everything south of the trunk's plane, soil and roots, is removed. */
	private static void cut(ServerLevel level, int x, int y, int z) {
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int dx = -HALF; dx <= HALF; dx++) {
			for (int dz = 1; dz <= HALF; dz++) {
				for (int dy = -SOIL; dy <= -1; dy++) {
					level.setBlock(p.set(x + dx, y + dy, z + dz), Blocks.AIR.defaultBlockState(), 2);
				}
			}
		}
	}

	/** A close view of a trunk base from eye height, on the side with the most flare blocks. */
	private static View base(String name, int x, int y, int z, ServerLevel level, RootVoxels roots) {
		double sx = 0;
		double sz = 0;
		for (int i = 0; i < roots.flareCount(); i++) {
			sx += roots.flareX(i);
			sz += roots.flareZ(i);
		}
		double a = roots.flareCount() == 0 ? Math.PI / 4 : Math.atan2(sz, sx) + 0.6;
		double d = 3.6;
		double ex = x + 0.5 + d * Math.cos(a);
		double ez = z + 0.5 + d * Math.sin(a);
		double yaw = Math.toDegrees(Math.atan2(-(x + 0.5 - ex), z + 0.5 - ez));
		double pitch = Math.toDegrees(Math.atan2(1.4, d));
		return new View(name, ex, y, ez, yaw, pitch);
	}

	// ------------------------------------------------------------------ worlds

	private static void world(ClientGameTestContext context, boolean real, boolean presentDay, ConcurrentLinkedQueue<Placed> placed,
			ConcurrentLinkedQueue<long[]> woody) {
		String scale = real ? "realistic" : "gameplay";
		String mode = presentDay ? "present_day" : "natural";
		ResourceKey<WorldPreset> preset = ResourceKey.create(Registries.WORLD_PRESET, PolishForests.id(real ? "poland" : "poland_gameplay"));
		try (TestSingleplayerContext sp = context.worldBuilder().adjustSettings(ui -> SliceClientGameTest.select(ui, preset, presentDay))
				.create()) {
			setUp(sp);
			hideHud(context);
			List<String> names = presentDay ? PRESENT_DAY_PLOTS : PLOTS;
			List<SlicePlots.Plot> plots = SlicePlots.of(real).stream().filter(p -> names.contains(p.name())).toList();
			Set<String> trenched = new HashSet<>();
			TRENCHES.clear();
			for (SlicePlots.Plot plot : plots) {
				sp.getServer().runCommand(String.format(Locale.ROOT, "tp @a %d 200 %d", plot.x(), plot.z()));
				context.waitTicks(20 * 25);
				String forest = plot.name().startsWith("oak") ? "oak" : "pine";
				boolean trench = !presentDay && trenched.add(forest);
				List<Placed> near = sp.getServer().computeOnServer(s -> nearest(List.copyOf(placed), plot));
				List<View> views = sp.getServer().computeOnServer(s -> views(s.overworld(), near));
				// The trench last: it is dug into the world beside the first tree.
				if (trench && !near.isEmpty()) {
					views = new ArrayList<>(views);
					views.add(null);
				}
				for (View v : views) {
					if (v == null) {
						for (View t : sp.getServer().computeOnServer(s -> trench(s.overworld(), near.getFirst()))) {
							shot(context, sp, "roots_" + scale + "_" + mode + "_" + plot.name() + "_" + t.name(), t.x(), t.y(), t.z(), t.yaw(),
									t.pitch(), 3);
						}
						continue;
					}
					shot(context, sp, "roots_" + scale + "_" + mode + "_" + plot.name() + "_" + v.name(), v.x(), v.y(), v.z(), v.yaw(),
							v.pitch(), 3);
				}
			}
			String check = sp.getServer().computeOnServer(s -> check(s.overworld(), List.copyOf(placed), List.copyOf(woody)));
			PolishForests.LOG.info("[roots] {} {}: {}", scale, mode, check);
		}
	}

	/** The trees with the most flare blocks written within {@link #RADIUS} blocks of a plot center, the nearest first. */
	private static List<Placed> nearest(List<Placed> placed, SlicePlots.Plot plot) {
		List<Placed> near = new ArrayList<>();
		int trees = 0;
		for (Placed p : placed) {
			double d = Math.hypot(p.plan().x() - plot.x(), p.plan().z() - plot.z());
			if (d <= RADIUS) {
				trees++;
				if (p.written().flareCount() > 0) {
					near.add(p);
				}
			}
		}
		near.sort((a, b) -> a.written().flareCount() != b.written().flareCount() ? b.written().flareCount() - a.written().flareCount()
				: Double.compare(Math.hypot(a.plan().x() - plot.x(), a.plan().z() - plot.z()), Math.hypot(b.plan().x() - plot.x(),
						b.plan().z() - plot.z())));
		PolishForests.LOG.info("[roots] {} ({}, {}): trees with roots within {} blocks {}, with flares {}", plot.name(), plot.x(),
				plot.z(), RADIUS, trees, near.size());
		return near.subList(0, Math.min(2, near.size()));
	}

	/** Each of the trees from eye height and close. */
	private static List<View> views(ServerLevel level, List<Placed> near) {
		List<View> out = new ArrayList<>();
		for (int k = 0; k < near.size(); k++) {
			Placed p = near.get(k);
			String name = (k + 1) + "_" + p.species().id();
			View eye = camera(level, p, name + "_eye", 6, 10);
			View close = camera(level, p, name + "_base", 3, 4);
			if (eye != null) {
				out.add(eye);
			}
			if (close != null) {
				out.add(close);
			}
		}
		return out;
	}

	/** An eye-height camera between {@code min} and {@code max} blocks from the trunk base with the clearest view of it. */
	private static View camera(ServerLevel level, Placed p, String name, int min, int max) {
		RootVoxels v = p.plan();
		double tx = v.x() + 0.5;
		double tz = v.z() + 0.5;
		double ty = v.y() + 0.4;
		View best = null;
		double bestScore = Double.MAX_VALUE;
		for (int a = 0; a < 16; a++) {
			double angle = a * Math.PI / 8;
			for (int dist = min; dist <= max; dist++) {
				int cx = (int) Math.floor(tx + Math.cos(angle) * dist);
				int cz = (int) Math.floor(tz + Math.sin(angle) * dist);
				int ground = ground(level, cx, cz);
				if (ground == Integer.MIN_VALUE || Math.abs(ground - v.y()) > 1) {
					continue;
				}
				BlockPos feet = new BlockPos(cx, ground, cz);
				if (!free(level.getBlockState(feet)) || !free(level.getBlockState(feet.above()))) {
					continue;
				}
				double ex = cx + 0.5;
				double ez = cz + 0.5;
				double ey = ground + 1.62;
				double len = Math.sqrt((tx - ex) * (tx - ex) + (ty - ey) * (ty - ey) + (tz - ez) * (tz - ez));
				int blocked = 0;
				for (double u = 0.5; u < len - 1.2; u += 0.25) {
					BlockPos b = BlockPos.containing(ex + (tx - ex) * u / len, ey + (ty - ey) * u / len, ez + (tz - ez) * u / len);
					if (!free(level.getBlockState(b))) {
						blocked++;
					}
				}
				double score = blocked + 0.1 * dist;
				if (score < bestScore) {
					bestScore = score;
					double yaw = Math.toDegrees(Math.atan2(-(tx - ex), tz - ez));
					double pitch = -Math.toDegrees(Math.atan2(ty - ey, Math.hypot(tx - ex, tz - ez)));
					best = new View(name, ex, ground, ez, yaw, pitch);
				}
			}
		}
		return best;
	}

	/**
	 * Digs a trench beside a tree (everything but the roots from one block under the top down to 7 blocks deep, 1 to 4
	 * blocks south of the trunk and 6 blocks to each side; the plants and the ground on top too) and returns two views:
	 * from above its south edge down into it, and from its east end along its north wall.
	 */
	private static List<View> trench(ServerLevel level, Placed p) {
		RootVoxels v = p.plan();
		int top = v.y() - 1;
		BlockPos.MutableBlockPos b = new BlockPos.MutableBlockPos();
		int dug = 0;
		int roots = 0;
		for (int dx = -6; dx <= 6; dx++) {
			for (int dz = 1; dz <= 4; dz++) {
				TRENCHES.add(column(v.x() + dx, v.z() + dz));
				for (int y = top + 3; y >= top - 7; y--) {
					BlockState s = level.getBlockState(b.set(v.x() + dx, y, v.z() + dz));
					if (s.getBlock() instanceof TrunkBlock || s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES)) {
						roots += s.is(ModBlocks.treeRoot()) ? 1 : 0;
						continue;
					}
					if (!s.isAir()) {
						level.setBlock(b, Blocks.AIR.defaultBlockState(), 2);
						dug++;
					}
				}
			}
		}
		PolishForests.LOG.info("[roots] trench beside {} at ({}, {}, {}): {} blocks dug, {} root blocks left standing", p.species().id(),
				v.x(), v.y(), v.z(), dug, roots);
		return List.of(look("trench_above_" + p.species().id(), v.x() + 0.5, top + 2.5, v.z() + 5.5, v.x() + 0.5, top - 3.5, v.z() + 1.5),
				look("trench_along_" + p.species().id(), v.x() + 6.5, top - 5.5, v.z() + 3.5, v.x() - 2.5, top - 3.0, v.z() + 1.0));
	}

	/**
	 * The columns of the trenches the test dug in the current world (x, z packed as a chunk position): the flares and roots
	 * left standing there lose their ground by the test, so the check counts them apart.
	 */
	private static final Set<Long> TRENCHES = java.util.concurrent.ConcurrentHashMap.newKeySet();

	private static long column(int x, int z) {
		return (long) x << 32 | z & 0xFFFFFFFFL;
	}

	/** A view from feet position (x, y, z) at eye height towards a target point. */
	private static View look(String name, double x, double y, double z, double tx, double ty, double tz) {
		double ey = y + 1.62;
		double yaw = Math.toDegrees(Math.atan2(-(tx - x), tz - z));
		double pitch = -Math.toDegrees(Math.atan2(ty - ey, Math.hypot(tx - x, tz - z)));
		return new View(name, x, y, z, yaw, pitch);
	}

	/** The block above the ground of a column: the highest sturdy block that is no wood or leaves, plus one. */
	private static int ground(ServerLevel level, int x, int z) {
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z), z);
		for (int k = 0; k < 64; k++) {
			p.move(0, -1, 0);
			BlockState s = level.getBlockState(p);
			if (!s.is(BlockTags.LEAVES) && !s.is(BlockTags.LOGS) && !(s.getBlock() instanceof TrunkBlock) && s.isFaceSturdy(level, p,
					Direction.UP) && s.getFluidState().isEmpty()) {
				return p.getY() + 1;
			}
		}
		return Integer.MIN_VALUE;
	}

	private static boolean free(BlockState s) {
		return s.isAir() || s.is(BlockTags.REPLACEABLE) && !s.is(BlockTags.LEAVES);
	}

	/**
	 * The roots of all trees written in a world against the world: flares of two trees within one block of each other or of
	 * another tree's base (any woody plant of the dispatchers), flares without a sturdy block below, root columns whose top
	 * ground block is a root or rooted dirt; the counts.
	 */
	private static String check(ServerLevel level, List<Placed> placed, List<long[]> woody) {
		Map<Long, Integer> flareOwner = new HashMap<>();
		Set<Long> bases = new HashSet<>();
		for (long[] w : woody) {
			bases.add(BlockPos.asLong((int) w[0], (int) w[1], (int) w[2]));
		}
		long flaresPlanned = 0;
		long flares = 0;
		long rootsPlanned = 0;
		long roots = 0;
		long dirt = 0;
		long hanging = 0;
		int trees = 0;
		int withFlares = 0;
		for (int t = 0; t < placed.size(); t++) {
			Placed p = placed.get(t);
			RootVoxels v = p.plan();
			trees++;
			flaresPlanned += v.flareCount();
			flares += p.written().flareCount();
			withFlares += p.written().flareCount() > 0 ? 1 : 0;
			rootsPlanned += v.rootCount();
			roots += p.written().rootCount();
			dirt += p.written().dirt();
			hanging += p.written().hanging();
			for (int i = 0; i < v.flareCount(); i++) {
				if (p.written().flares()[i]) {
					flareOwner.put(BlockPos.asLong(v.x() + v.flareX(i), v.y(), v.z() + v.flareZ(i)), t);
				}
			}
		}
		int touching = 0;
		int nextToBase = 0;
		int floating = 0;
		int trenchFlares = 0;
		int trenchRoots = 0;
		int changed = 0;
		int surface = 0;
		List<String> bad = new ArrayList<>();
		BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
		for (Map.Entry<Long, Integer> e : flareOwner.entrySet()) {
			BlockPos f = BlockPos.of(e.getKey());
			RootVoxels own = placed.get(e.getValue()).plan();
			BlockState s = level.getBlockState(f);
			if (!(s.getBlock() instanceof RootFlareBlock)) {
				changed++;
				continue;
			}
			if (!level.getBlockState(f.below()).isFaceSturdy(level, f.below(), Direction.UP) && TRENCHES.contains(column(f.getX(), f.getZ()))) {
				trenchFlares++;
			} else if (!level.getBlockState(f.below()).isFaceSturdy(level, f.below(), Direction.UP)) {
				floating++;
				if (bad.size() < 6) {
					bad.add("flare at " + f.toShortString() + " over " + level.getBlockState(f.below()));
				}
			}
			for (int ox = -1; ox <= 1; ox++) {
				for (int oy = -1; oy <= 1; oy++) {
					for (int oz = -1; oz <= 1; oz++) {
						q.set(f.getX() + ox, f.getY() + oy, f.getZ() + oz);
						Integer o = flareOwner.get(q.asLong());
						if (o != null && !o.equals(e.getValue())) {
							touching++;
							if (bad.size() < 6) {
								bad.add("flares touch at " + f.toShortString());
							}
						}
						if (bases.contains(q.asLong()) && !(q.getX() == own.x() && q.getY() == own.y() && q.getZ() == own.z())) {
							nextToBase++;
							if (bad.size() < 6) {
								bad.add("flare at " + f.toShortString() + " next to the base of another woody plant " + q.toShortString());
							}
						}
					}
				}
			}
		}
		// For comparison (felling mods, step G1): woody plants of the dispatchers within one block of a trunk base of a tree
		// with roots, as there were before the flares.
		int besideTrunk = 0;
		for (Placed p : placed) {
			RootVoxels v = p.plan();
			for (int ox = -1; ox <= 1; ox++) {
				for (int oz = -1; oz <= 1; oz++) {
					if ((ox != 0 || oz != 0) && bases.contains(BlockPos.asLong(v.x() + ox, v.y(), v.z() + oz))) {
						besideTrunk++;
					}
				}
			}
		}
		// The cover over the roots in the loaded chunks: the blocks above a root cell up to the first that is no soil or
		// stone (at least COVER of them, one in the trunk column); a pit dug later by a windthrow may take some.
		int covered = 0;
		for (Placed p : placed) {
			RootVoxels v = p.plan();
			for (int i = 0; i < v.rootCount(); i++) {
				if (p.written().roots()[i] == 0) {
					continue;
				}
				int x = v.x() + v.rootX(i);
				int y = v.y() + v.rootY(i);
				int z = v.z() + v.rootZ(i);
				if (!level.hasChunk(x >> 4, z >> 4)) {
					continue;
				}
				covered++;
				int above = 0;
				while (above < RootPlan.COVER) {
					BlockState s = level.getBlockState(q.set(x, y + above + 1, z));
					if (!s.isSolidRender() && !(s.getBlock() instanceof TrunkBlock)) {
						break;
					}
					above++;
				}
				boolean trunkColumn = v.rootX(i) == 0 && v.rootZ(i) == 0;
				if (above < (trunkColumn ? 1 : RootPlan.COVER) && TRENCHES.contains(column(x, z))) {
					trenchRoots++;
				} else if (above < (trunkColumn ? 1 : RootPlan.COVER)) {
					surface++;
					if (bad.size() < 10) {
						bad.add("root at " + x + ", " + y + ", " + z + " under " + above + " blocks");
					}
				}
			}
		}
		String report = String.format(Locale.ROOT, "trees with roots %d, with flares %d (%.1f%%); flare blocks %d of %d planned "
				+ "(%.1f%%, the rest in the way of other blocks), %.2f per tree with flares; roots %d of %d planned (%.1f%%), rooted dirt %d, "
				+ "hanging roots %d; flares touching another tree's flare %d, next to another woody base %d, over no sturdy block %d, "
				+ "replaced later %d; woody bases beside the trunk base of a tree with roots %d; root cells in loaded chunks %d, under less "
				+ "than %d blocks of ground %d; in the trenches of the test (not counted above) flares over air %d, roots uncovered %d%s", trees, withFlares, 100.0 * withFlares / Math.max(1, trees),
				flares, flaresPlanned, 100.0 * flares / Math.max(1, flaresPlanned), flares / (double) Math.max(1, withFlares), roots,
				rootsPlanned, 100.0 * roots / Math.max(1, rootsPlanned), dirt, hanging, touching, nextToBase, floating, changed, besideTrunk, covered,
				RootPlan.COVER, surface, trenchFlares, trenchRoots,
				bad.isEmpty() ? "" : "; e.g. " + String.join(", ", bad));
		// Flares over no sturdy block and roots near the surface only where a later feature (a windthrow's pit) dug the
		// ground away: at most 0.1%.
		if (trees == 0 || flares == 0 || touching > 0 || floating > 0.001 * flares || surface > 0.001 * Math.max(1, covered)) {
			throw new AssertionError("[roots] " + report);
		}
		return report;
	}

	// ------------------------------------------------------------------ helpers

	private static void setUp(TestSingleplayerContext sp) {
		sp.getServer().runCommand("gamerule advance_time false");
		sp.getServer().runCommand("gamerule advance_weather false");
		sp.getServer().runCommand("gamerule spawn_mobs false");
		sp.getServer().runCommand("gamerule send_command_feedback false");
		SeasonDates.set(sp, SubSeason.MID_SUMMER, 4, "roots");
		sp.getServer().runCommand("time set 6000");
		sp.getServer().runCommand("weather clear");
		sp.getServer().runCommand("difficulty peaceful");
		sp.getServer().runCommand("gamemode spectator @a");
	}

	private static void hideHud(ClientGameTestContext context) {
		context.runOnClient(mc -> {
			if (!mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
	}

	private static void shot(ClientGameTestContext context, TestSingleplayerContext sp, String name, double x, double y, double z,
			double yaw, double pitch, int waitSeconds) {
		shot(context, sp, name, x, y, z, yaw, pitch, waitSeconds, false);
	}

	/** {@code board}: a frame of the board on the flat test world, with the short margin of step T8. */
	private static void shot(ClientGameTestContext context, TestSingleplayerContext sp, String name, double x, double y, double z,
			double yaw, double pitch, int waitSeconds, boolean board) {
		sp.getServer().runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch));
		// Step T7: until the view is drawn (ViewReady) instead of a fixed wait of waitSeconds.
		if (board) {
			ViewReady.shotBoard(context, sp, name, SIZE, waitSeconds);
		} else {
			ViewReady.shot(context, sp, name, SIZE, waitSeconds);
		}
		PolishForests.LOG.info(String.format(Locale.ROOT, "[roots] %s: camera (%.1f, %.1f, %.1f) yaw %.0f pitch %.0f", name, x, y, z,
				yaw, pitch));
	}
}
