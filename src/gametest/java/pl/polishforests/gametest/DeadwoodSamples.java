package pl.polishforests.gametest;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
import pl.polishforests.block.TrunkBlock;
import pl.polishforests.season.SubSeason;
import pl.polishforests.worldgen.feature.DeadwoodFeature;
import pl.polishforests.worldgen.feature.ModFeatures;
import pl.polishforests.worldgen.habitat.Species;
import pl.polishforests.worldgen.tree.AgeClass;
import pl.polishforests.worldgen.tree.CrownLayer;
import pl.polishforests.worldgen.tree.DeadwoodPlan;
import pl.polishforests.worldgen.tree.DeadwoodVoxels;
import pl.polishforests.worldgen.tree.TreeShapePlan;
import pl.polishforests.worldgen.tree.TreeVoxels;

/**
 * The dead wood views of step V11 ({@code -Pgametest=slice -Psites=deadwood}): first a board in the flat test world, a row
 * per species of the slice with each object of the dead wood planner (a log, a snag, a broken tree, a windthrow of an
 * old-growth tree of a natural stand, a stump of a managed one), written by {@link DeadwoodFeature#write} and checked
 * block by block (the board is level ground with nothing in the way), screenshots of each row from the side and close-ups
 * ({@code deadwood_board_<view>}); then the worlds of the slice in both scales (natural mode, summer): at the sample plots
 * of both forests in natural and managed stands the test finds the objects the generation placed within
 * {@link #RADIUS} blocks of the plot center ({@code ModFeatures.DEADWOOD_PLACEMENTS}) and shoots the nearest of each kind
 * from eye height with the clearest line of sight ({@code deadwood_<scale>_<plot>_<kind>}). Over all objects placed in
 * those worlds it checks the lying wood against the world ({@link #check}): at least {@link DeadwoodPlan#MIN_RESTING} of
 * each lying stem rests on the ground (no air below), few blocks over air and no rails, no wood over water or a dirt path;
 * the log gets the shares.
 */
final class DeadwoodSamples {
	private static final int[] SIZE = {1280, 720};
	private static final int ROW = 72;
	private static final int COLUMN = 64;
	/** Search radius for objects around a plot center, blocks. */
	private static final int RADIUS = 40;
	/** Plots of the views (names of {@code slice_plots.txt}). */
	private static final List<String> PLOTS = List.of("oak_hornbeam_natural_1", "oak_hornbeam_managed_1", "fresh_pine_natural_1",
			"fresh_pine_managed_mature");

	private DeadwoodSamples() {
	}

	static void run(ClientGameTestContext context) {
		context.runOnClient(mc -> {
			mc.options.ambientOcclusion().set(true);
			mc.options.renderDistance().set(10);
		});
		board(context);
		Map<Long, DeadwoodVoxels> placed = new ConcurrentHashMap<>();
		ModFeatures.DEADWOOD_PLACEMENTS = (at, plan) -> placed.put(NormsCensus.key(at.getX(), at.getZ()), plan);
		try {
			for (boolean real : new boolean[] {true, false}) {
				placed.clear();
				world(context, real, placed);
			}
		} finally {
			ModFeatures.DEADWOOD_PLACEMENTS = null;
		}
	}

	// ------------------------------------------------------------------ board

	private static void board(ClientGameTestContext context) {
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			setUp(sp);
			List<DeadwoodVoxels> plans = new ArrayList<>();
			sp.getServer().computeOnServer(s -> build(s, plans));
			hideHud(context);
			// Every object from its best eye-height camera (the oak and the spruce windthrow also from above).
			List<View> views = sp.getServer().computeOnServer(s -> {
				List<View> out = new ArrayList<>();
				for (DeadwoodVoxels v : plans) {
					View c = camera(s.overworld(), v);
					if (c != null) {
						out.add(c);
					}
				}
				return out;
			});
			for (int i = 0; i < views.size(); i++) {
				View c = views.get(i);
				shot(context, sp, "deadwood_board_" + c.name(), c.x(), c.y(), c.z(), c.yaw(), c.pitch(), i == 0 ? 4 : 1);
			}
		}
	}

	private static List<Species> species() {
		return List.of(Species.OAK, Species.HORNBEAM, Species.LINDEN, Species.ASH, Species.NORWAY_MAPLE, Species.BEECH,
				Species.SCOTS_PINE, Species.SPRUCE, Species.BIRCH);
	}

	/** Builds the board on the server, adding the plans to {@code plans}; returns its origin (x, y, z). */
	private static int[] build(MinecraftServer server, List<DeadwoodVoxels> plans) {
		ServerLevel level = server.overworld();
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
		TreeShapePlan.Ground ground = (x, z) -> y - 1;
		DeadwoodPlan.Kind[] kinds = DeadwoodPlan.Kind.values();
		int missing = 0;
		int wood = 0;
		StringBuilder report = new StringBuilder();
		List<Species> species = species();
		for (int s = 0; s < species.size(); s++) {
			for (int k = 0; k < kinds.length; k++) {
				boolean stump = kinds[k] == DeadwoodPlan.Kind.STUMP;
				int x = COLUMN * k;
				int z = ROW * s;
				DeadwoodVoxels v = DeadwoodPlan.plan(new DeadwoodPlan.Request(species.get(s), kinds[k], CrownLayer.CANOPY,
						stump ? AgeClass.MATURE : AgeClass.OLD_GROWTH, !stump, level.getSeed(), x, y, z, ground));
				if (v.empty()) {
					throw new AssertionError("[deadwood] empty plan on the board: " + kinds[k] + " of " + species.get(s));
				}
				DeadwoodFeature.write(level, v);
				plans.add(v);
				TreeVoxels t = v.wood();
				for (int i = 0; i < t.woodCount(); i++) {
					BlockPos at = new BlockPos(t.x() + t.woodX(i), t.y() + t.woodY(i), t.z() + t.woodZ(i));
					BlockState b = level.getBlockState(at);
					wood++;
					if (!(b.getBlock() instanceof TrunkBlock) || b.getValue(TrunkBlock.RADIUS) != t.radius(i)) {
						missing++;
						PolishForests.LOG.warn("[deadwood] board: {} of {} misses wood block {} at {} (parent {}): {}", kinds[k],
								species.get(s).id(), i, at, t.parentIndex(i), b);
					}
				}
				report.append(String.format(Locale.ROOT, " %s %s: %d wood, %d soil, %d pit, %d cover, direction %d;",
						species.get(s).id(), kinds[k].name().toLowerCase(Locale.ROOT), t.woodCount(), v.soil().length, v.pit().length,
						v.cover().length, v.direction()));
			}
		}
		PolishForests.LOG.info("[deadwood] board:{}", report);
		if (missing > 0) {
			throw new AssertionError("[deadwood] " + missing + " of " + wood + " planned wood blocks of the board are not in the world");
		}
		PolishForests.LOG.info("[deadwood] board: all {} wood blocks written", wood);
		return new int[] {0, y, 0};
	}

	// ------------------------------------------------------------------ worlds

	private static void world(ClientGameTestContext context, boolean real, Map<Long, DeadwoodVoxels> placed) {
		String scale = real ? "realistic" : "gameplay";
		ResourceKey<WorldPreset> preset = ResourceKey.create(Registries.WORLD_PRESET, PolishForests.id(real ? "poland" : "poland_gameplay"));
		try (TestSingleplayerContext sp = context.worldBuilder().adjustSettings(ui -> SliceClientGameTest.select(ui, preset, false))
				.create()) {
			setUp(sp);
			hideHud(context);
			List<SlicePlots.Plot> plots = SlicePlots.of(real).stream().filter(p -> PLOTS.contains(p.name())).toList();
			for (SlicePlots.Plot plot : plots) {
				sp.getServer().runCommand(String.format(Locale.ROOT, "tp @a %d 200 %d", plot.x(), plot.z()));
				context.waitTicks(20 * 25);
				List<View> views = sp.getServer().computeOnServer(s -> views(s.overworld(), placed, plot));
				for (View v : views) {
					shot(context, sp, "deadwood_" + scale + "_" + plot.name() + "_" + v.name(), v.x(), v.y(), v.z(), v.yaw(), v.pitch(),
							4);
				}
			}
			String check = sp.getServer().computeOnServer(s -> check(s.overworld(), placed));
			PolishForests.LOG.info("[deadwood] {}: {}", scale, check);
		}
	}

	private record View(String name, double x, double y, double z, double yaw, double pitch) {
	}

	/** The nearest object of each kind around a plot and a camera for it. */
	private static List<View> views(ServerLevel level, Map<Long, DeadwoodVoxels> placed, SlicePlots.Plot plot) {
		Map<DeadwoodPlan.Kind, DeadwoodVoxels> nearest = new EnumMap<>(DeadwoodPlan.Kind.class);
		Map<DeadwoodPlan.Kind, Integer> counts = new EnumMap<>(DeadwoodPlan.Kind.class);
		for (DeadwoodVoxels v : placed.values()) {
			double d = Math.hypot(v.wood().x() - plot.x(), v.wood().z() - plot.z());
			if (d > RADIUS) {
				continue;
			}
			counts.merge(v.kind(), 1, Integer::sum);
			DeadwoodVoxels best = nearest.get(v.kind());
			if (best == null || d < Math.hypot(best.wood().x() - plot.x(), best.wood().z() - plot.z())) {
				nearest.put(v.kind(), v);
			}
		}
		PolishForests.LOG.info("[deadwood] {} ({}, {}): objects within {} blocks {}", plot.name(), plot.x(), plot.z(), RADIUS, counts);
		List<View> out = new ArrayList<>();
		for (DeadwoodVoxels v : nearest.values()) {
			View c = camera(level, v);
			if (c != null) {
				out.add(c);
			}
		}
		return out;
	}

	/**
	 * A camera at eye height 6–12 blocks from the object with the fewest blocks between the eye and the object's middle
	 * (not counting the object's own blocks), across lying wood first.
	 */
	private static View camera(ServerLevel level, DeadwoodVoxels v) {
		TreeVoxels t = v.wood();
		Set<Long> own = new HashSet<>();
		double sx = 0;
		double sz = 0;
		int top = 0;
		for (int i = 0; i < t.woodCount(); i++) {
			own.add(BlockPos.asLong(t.x() + t.woodX(i), t.y() + t.woodY(i), t.z() + t.woodZ(i)));
			sx += t.woodX(i);
			sz += t.woodZ(i);
			top = Math.max(top, t.woodY(i));
		}
		for (int s : v.soil()) {
			own.add(BlockPos.asLong(t.x() + v.x(s), t.y() + v.y(s), t.z() + v.z(s)));
		}
		// The aim: the middle of lying wood (a windthrow: a third of the way from its root plate), the lower half of a
		// standing object.
		double share = v.kind() == DeadwoodPlan.Kind.WINDTHROW ? 0.6 : 1;
		double tx = t.x() + 0.5 + (v.kind().standing() && v.kind() != DeadwoodPlan.Kind.BROKEN ? 0 : share * sx / t.woodCount());
		double tz = t.z() + 0.5 + (v.kind().standing() && v.kind() != DeadwoodPlan.Kind.BROKEN ? 0 : share * sz / t.woodCount());
		double ty = t.y() + (v.kind() == DeadwoodPlan.Kind.SNAG ? Math.min(top, 10) * 0.4 + 0.5 : v.kind() == DeadwoodPlan.Kind.WINDTHROW
				? 1.2 : 0.6);
		View best = null;
		double bestScore = Double.MAX_VALUE;
		for (int a = 0; a < 16; a++) {
			double angle = a * Math.PI / 8;
			double dx = Math.cos(angle);
			double dz = Math.sin(angle);
			double across = v.direction() < 0 ? 0 : Math.abs(dx * Direction.from3DDataValue(v.direction()).getStepX()
					+ dz * Direction.from3DDataValue(v.direction()).getStepZ());
			for (int dist = 7; dist <= 13; dist += 2) {
				int cx = (int) Math.floor(tx + dx * dist);
				int cz = (int) Math.floor(tz + dz * dist);
				int ground = ground(level, cx, cz);
				double ey = ground + 1.62;
				BlockPos feet = new BlockPos(cx, ground, cz);
				// On the ground near the object's level (not on a root plate, a log or a step of the terrain).
				if (ground == Integer.MIN_VALUE || Math.abs(ground - t.y()) > 2 || !free(level.getBlockState(feet)) || !free(level.getBlockState(feet.above()))) {
					continue;
				}
				// Blocks around the eye fill the frame: trunks, leaves, soil within a block of the camera count three times.
				int near = 0;
				for (int ox = -1; ox <= 1; ox++) {
					for (int oz = -1; oz <= 1; oz++) {
						for (int oy = 0; oy <= 2; oy++) {
							BlockState n = level.getBlockState(feet.offset(ox, oy, oz));
							if (!free(n)) {
								near++;
							}
						}
					}
				}
				int blocked = 3 * near;
				double ex = cx + 0.5;
				double ez = cz + 0.5;
				double len = Math.sqrt((tx - ex) * (tx - ex) + (ty - ey) * (ty - ey) + (tz - ez) * (tz - ez));
				for (double u = 0.5; u < len - 0.8; u += 0.25) {
					int bx = (int) Math.floor(ex + (tx - ex) * u / len);
					int by = (int) Math.floor(ey + (ty - ey) * u / len);
					int bz = (int) Math.floor(ez + (tz - ez) * u / len);
					if (own.contains(BlockPos.asLong(bx, by, bz))) {
						continue;
					}
					if (!level.getBlockState(new BlockPos(bx, by, bz)).isAir()) {
						blocked++;
					}
				}
				double score = blocked + 2 * across + 0.1 * dist;
				if (score < bestScore) {
					bestScore = score;
					double yaw = Math.toDegrees(Math.atan2(-(tx - ex), tz - ez));
					double pitch = -Math.toDegrees(Math.atan2(ty - ey, Math.hypot(tx - ex, tz - ez)));
					best = new View(v.kind().name().toLowerCase(Locale.ROOT) + "_" + v.species().id(), ex, ey - 1.62, ez, yaw, pitch);
				}
			}
		}
		if (best != null) {
			PolishForests.LOG.info(String.format(Locale.ROOT, "[deadwood] %s at (%d, %d, %d): camera score %.1f", best.name(), t.x(), t.y(),
					t.z(), bestScore));
		}
		return best;
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
	 * The lying wood of all objects placed in the world against the world: the air below each block of a lying stem
	 * ({@link DeadwoodVoxels#lyingStem}), water or a dirt path below; the objects whose wood is in the world (a later feature
	 * may have replaced some). Review of V11, round 1: a block rests only with no air below; the check reports the blocks
	 * over one block of air and over more, the longest run of blocks over air in a stem, and fails when fewer than 95% of
	 * the stems rest on {@link DeadwoodPlan#MIN_RESTING} of their length, more than {@link #MAX_OVER_AIR} of the blocks lie
	 * over air, more than {@link #MAX_RAILS} of the stems have a run of {@link DeadwoodPlan#MAX_GAP_RUN} + 1 or more such
	 * blocks (a rail), or any wood lies over water or a dirt path.
	 */
	private static String check(ServerLevel level, Map<Long, DeadwoodVoxels> placed) {
		Map<DeadwoodPlan.Kind, Integer> kinds = new EnumMap<>(DeadwoodPlan.Kind.class);
		int lying = 0;
		int resting = 0;
		int blocks = 0;
		int[] byAir = new int[3];
		int rails = 0;
		int longest = 0;
		int overWater = 0;
		int overPath = 0;
		List<String> bad = new ArrayList<>();
		for (DeadwoodVoxels v : placed.values()) {
			kinds.merge(v.kind(), 1, Integer::sum);
			TreeVoxels t = v.wood();
			int n = 0;
			int ok = 0;
			int run = 0;
			int maxRun = 0;
			for (int i : v.lyingStem()) {
				BlockPos p = new BlockPos(t.x() + t.woodX(i), t.y(), t.z() + t.woodZ(i));
				if (!(level.getBlockState(p).getBlock() instanceof TrunkBlock)) {
					continue;
				}
				int air = 0;
				BlockPos.MutableBlockPos below = p.mutable().move(0, -1, 0);
				while (air < 8) {
					BlockState b = level.getBlockState(below);
					if (!b.isAir() && !b.is(BlockTags.REPLACEABLE)) {
						if (!b.getFluidState().isEmpty()) {
							overWater++;
						}
						if (b.is(Blocks.DIRT_PATH)) {
							overPath++;
						}
						break;
					}
					air++;
					below.move(0, -1, 0);
				}
				n++;
				byAir[Math.min(air, 2)]++;
				ok += air == 0 ? 1 : 0;
				run = air == 0 ? 0 : run + 1;
				maxRun = Math.max(maxRun, run);
			}
			if (n == 0) {
				continue;
			}
			lying++;
			blocks += n;
			longest = Math.max(longest, maxRun);
			if (maxRun > DeadwoodPlan.MAX_GAP_RUN) {
				rails++;
			}
			if (ok >= DeadwoodPlan.MIN_RESTING * n - 1e-9) {
				resting++;
			}
			if ((ok < DeadwoodPlan.MIN_RESTING * n - 1e-9 || maxRun > DeadwoodPlan.MAX_GAP_RUN) && bad.size() < 8) {
				bad.add(String.format(Locale.ROOT, "%s %s at (%d, %d, %d): %d of %d resting, run %d", v.kind(), v.species().id(), t.x(), t.y(),
						t.z(), ok, n, maxRun));
			}
		}
		double overAir = (blocks - byAir[0]) / (double) Math.max(1, blocks);
		String report = String.format(Locale.ROOT, "objects %s; lying %d, resting (no air below) on at least %.0f%% of their length %d "
				+ "(%.1f%%), lying blocks %d: no air %.1f%%, one block of air %.1f%%, more %.1f%%; stems with %d or more blocks over air in "
				+ "a row %d (%.1f%%), longest run %d; over water %d, over a dirt path %d%s", kinds, lying, 100 * DeadwoodPlan.MIN_RESTING,
				resting, 100.0 * resting / Math.max(1, lying), blocks, 100.0 * byAir[0] / Math.max(1, blocks),
				100.0 * byAir[1] / Math.max(1, blocks), 100.0 * byAir[2] / Math.max(1, blocks), DeadwoodPlan.MAX_GAP_RUN + 1, rails,
				100.0 * rails / Math.max(1, lying), longest, overWater, overPath, bad.isEmpty() ? "" : "; e.g. " + String.join(", ", bad));
		if (lying == 0 || resting < 0.95 * lying || overAir > MAX_OVER_AIR || rails > MAX_RAILS * lying || overWater > 0 || overPath > 0) {
			throw new AssertionError("[deadwood] " + report);
		}
		return report;
	}

	/** Most of the lying blocks over air in the world (the planner allows 20% per stem, single blocks over a dip). */
	private static final double MAX_OVER_AIR = 0.05;
	/** Most of the lying stems with a run of blocks over air (the world differs from the planner's ground: pits, features). */
	private static final double MAX_RAILS = 0.01;

	// ------------------------------------------------------------------ helpers

	private static void setUp(TestSingleplayerContext sp) {
		sp.getServer().runCommand("gamerule advance_time false");
		sp.getServer().runCommand("gamerule advance_weather false");
		sp.getServer().runCommand("gamerule spawn_mobs false");
		sp.getServer().runCommand("gamerule send_command_feedback false");
		SeasonDates.set(sp, SubSeason.MID_SUMMER, 4, "deadwood");
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
		sp.getServer().runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch));
		// Step T7: until the view is drawn (ViewReady) instead of a fixed wait of waitSeconds.
		ViewReady.shot(context, sp, name, SIZE, waitSeconds);
		PolishForests.LOG.info(String.format(Locale.ROOT, "[deadwood] %s: camera (%.1f, %.1f, %.1f) yaw %.0f pitch %.0f", name, x, y, z,
				yaw, pitch));
	}
}
