package pl.polishforests.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import pl.polishforests.block.TrunkBlock;
import pl.polishforests.PolishForests;
import pl.polishforests.season.SubSeason;
import pl.polishforests.worldgen.feature.ProceduralTreeFeature;
import pl.polishforests.worldgen.habitat.Species;
import pl.polishforests.worldgen.tree.AgeClass;
import pl.polishforests.worldgen.tree.CrownCover;
import pl.polishforests.worldgen.tree.CrownLayer;
import pl.polishforests.worldgen.tree.CrownTerritories;
import pl.polishforests.worldgen.tree.CrownTerritory;
import pl.polishforests.worldgen.tree.SpeciesShape;
import pl.polishforests.worldgen.tree.StandMap;
import pl.polishforests.worldgen.tree.TreeRequest;
import pl.polishforests.worldgen.tree.TreeShapePlan;
import pl.polishforests.worldgen.tree.TreeVoxels;

/**
 * The tree board of the tree planner (step V6 of docs/04-pionowy-wycinek.md, {@code -Pgametest=slice -Psites=trees}): in
 * the flat test world the feature {@code polishforests:procedural_tree} plants a canopy tree of every species of the
 * planner in the pole stage, the mature stand and the old growth (one row per species), and the planner with crown
 * territories writes two stands of 3 × 3 chunks through {@link ProceduralTreeFeature#write} (a managed mature pine stand
 * and a natural old oak-hornbeam stand with their lower layers, species as in the PNG preview). The test checks that
 * every planned wood block is in the world with its state (the board is level ground with nothing in the way), that every
 * leaf of the board has a distance of at most 6 that the world around it supports, right after the planting and 200
 * ticks later (also for six lone trees with stone in a fifth of their leaf positions and in one branch block, which
 * exercise the partial write of {@link ProceduralTreeFeature#write}), and takes screenshots
 * ({@code trees_<renderer>_<view>}): each species row from the side, the stands from outside, inside and above, and the
 * pine's bark change close up, all in summer (the date is set explicitly, {@link SeasonDates}); then the stands and the
 * lone oaks again in October, November, January and March ({@code trees_<renderer>_season_<sub-season>_<view>}).
 */
final class TreeSamples {
	private static final int[] SIZE = {1280, 720};
	/** The sub-seasons of the seasonal stand views, after the summer board. */
	private static final SubSeason[] SEASONS = {SubSeason.MID_AUTUMN, SubSeason.LATE_AUTUMN, SubSeason.MID_WINTER,
			SubSeason.EARLY_SPRING};
	/** Distance of the species rows (z) and of the age classes in a row (x). */
	private static final int ROW = 64;
	private static final int COLUMN = 18;
	/** Origins of the stands (x, relative to the board) and their extent of 3 chunks. */
	private static final int PINE_STAND_X = 96;
	private static final int GRAD_STAND_X = 176;
	/** Origin (x) of the ecotone of procedural trees and vanilla substitutes (risk R5), its share of substitutes. */
	private static final int ECOTONE_X = 272;
	private static final double SUBSTITUTES = 0.3;
	/** Most bare crown wood open to the side per tree ({@link CrownCover}): lone trees and the two stands. */
	private static final double MAX_BARE_LONE = 0.6;
	private static final double MAX_BARE_STAND = 0.5;
	/** Origin (x) of the lone trees with obstacles in their crowns. */
	private static final int OBSTACLE_X = 380;

	private TreeSamples() {
	}

	/** One placed tree: its voxels and species. */
	private record Placed(TreeVoxels voxels, Species species) {
	}

	static void run(ClientGameTestContext context) {
		boolean sodium = FabricLoader.getInstance().isModLoaded("sodium");
		boolean nvidium = context.computeOnClient(mc -> RenderProbesClientGameTest.nvidiumEnabled());
		String renderer = !sodium ? "vanilla" : nvidium ? "sodium_nvidium" : "sodium";
		context.runOnClient(mc -> {
			mc.options.ambientOcclusion().set(true);
			mc.options.renderDistance().set(12);
		});
		try (TestSingleplayerContext sp = context.worldBuilder().create()) {
			sp.getServer().runCommand("gamerule advance_time false");
			sp.getServer().runCommand("gamerule advance_weather false");
			sp.getServer().runCommand("gamerule spawn_mobs false");
			sp.getServer().runCommand("gamerule send_command_feedback false");
			// The board in summer (the middle of MID_SUMMER without SS): the date decides the leaf colors since V7.
			SeasonDates.set(sp, SubSeason.MID_SUMMER, 4, "trees");
			sp.getServer().runCommand("weather clear");
			sp.getServer().runCommand("difficulty peaceful");
			List<Placed> placed = new ArrayList<>();
			List<Placed> blocked = new ArrayList<>();
			int[] board = sp.getServer().computeOnServer(s -> build(s, placed, blocked));
			PolishForests.LOG.info("[trees] board at ({}, {}, {}): {} trees", board[0], board[1], board[2], placed.size());
			context.waitTicks(200);
			String later = sp.getServer().computeOnServer(s -> check(s.overworld(), placed, false));
			PolishForests.LOG.info("[trees] after 200 ticks: {}", later);
			String obstacles = sp.getServer().computeOnServer(s -> check(s.overworld(), blocked, false));
			PolishForests.LOG.info("[trees] obstacles after 200 ticks: {}", obstacles);
			sp.getServer().runCommand("gamemode spectator @a");
			context.runOnClient(mc -> {
				if (!mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
			String p = "trees_" + renderer + "_";
			int x0 = board[0];
			int y0 = board[1];
			int z0 = board[2];
			List<Species> species = new ArrayList<>(SpeciesShape.species());
			for (int s = 0; s < species.size(); s++) {
				shot(context, sp, p + species.get(s).id(), x0 + COLUMN, y0 + 12, z0 + ROW * s - 32, 0, -8, 4);
			}
			shot(context, sp, p + "pine_stand_edge", x0 + PINE_STAND_X + 24, y0 + 3, z0 - 22, 0, -12, 6);
			shot(context, sp, p + "pine_stand_inside", x0 + PINE_STAND_X + 8, y0 + 1.6, z0 + 6, 20, -18, 4);
			shot(context, sp, p + "pine_stand_above", x0 + PINE_STAND_X + 24, y0 + 70, z0 + 24, 0, 90, 4);
			shot(context, sp, p + "grad_stand_edge", x0 + GRAD_STAND_X + 24, y0 + 3, z0 - 22, 0, -12, 6);
			shot(context, sp, p + "grad_stand_inside", x0 + GRAD_STAND_X + 8, y0 + 1.6, z0 + 6, 20, -25, 4);
			shot(context, sp, p + "grad_stand_above", x0 + GRAD_STAND_X + 24, y0 + 75, z0 + 24, 0, 90, 4);
			// The bark change of the old pine (row 0, third column), about 3 blocks away.
			Placed oldPine = placed.get(2);
			int mirror = 0;
			for (int i = 0; i < oldPine.voxels().woodCount(); i++) {
				if (oldPine.voxels().bark(i) == TreeVoxels.TRANSITION) {
					mirror = oldPine.voxels().woodY(i);
				}
			}
			shot(context, sp, p + "pine_mirror", oldPine.voxels().x() + 0.5, oldPine.voxels().y() + mirror, oldPine.voxels().z() - 3,
					0, 0, 3);
			// The stands through the year (V7 review round 1): golden October, November, January and March (a new world).
			for (SubSeason sub : SEASONS) {
				int day = sub == SubSeason.MID_AUTUMN ? 4 : 0;
				SeasonDates.setAndWait(context, sp, sub, day, "trees");
				String q = p + "season_" + sub.name().toLowerCase(Locale.ROOT) + "_";
				shot(context, sp, q + "grad_stand_edge", x0 + GRAD_STAND_X + 24, y0 + 3, z0 - 22, 0, -12, 3);
				shot(context, sp, q + "grad_stand_inside", x0 + GRAD_STAND_X + 8, y0 + 1.6, z0 + 6, 20, -25, 3);
				shot(context, sp, q + "pine_stand_edge", x0 + PINE_STAND_X + 24, y0 + 3, z0 - 22, 0, -12, 3);
				shot(context, sp, q + "lone_oak_beech", x0 + COLUMN, y0 + 12, z0 + ROW * species.indexOf(Species.OAK) - 32, 0, -8, 3);
			}
		}
	}

	/** Plants the board on the server; returns its origin (x, y of the first block above the ground, z). */
	private static int[] build(MinecraftServer server, List<Placed> placed, List<Placed> blocked) {
		ServerLevel level = server.overworld();
		ChunkGenerator generator = level.getChunkSource().getGenerator();
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, 0, 0);
		int x0 = 0;
		int z0 = 0;
		List<Species> species = new ArrayList<>(SpeciesShape.species());
		AgeClass[] ages = AgeClass.values();
		int failed = 0;
		for (int s = 0; s < species.size(); s++) {
			for (int a = 0; a < ages.length; a++) {
				BlockPos at = new BlockPos(x0 + COLUMN * a, y, z0 + ROW * s);
				ProceduralTreeFeature f = new ProceduralTreeFeature(species.get(s), CrownLayer.CANOPY, ages[a]);
				if (!f.place(level, generator, level.getRandom(), at)) {
					failed++;
				}
				// The same plan as the feature (a pure function), for the checks.
				placed.add(new Placed(TreeShapePlan.plan(new TreeRequest(species.get(s), CrownLayer.CANOPY, ages[a], level.getSeed(),
						at.getX(), at.getY(), at.getZ())), species.get(s)));
			}
		}
		if (failed > 0) {
			throw new AssertionError(failed + " lone trees of the board were not placed");
		}
		int lone = placed.size();
		stand(level, placed, false, x0 + PINE_STAND_X, y, z0);
		stand(level, placed, true, x0 + GRAD_STAND_X, y, z0);
		// Bare wood of the crowns, open to the side (review of V9, round 2; the limits of StandCrownCoverTest).
		CrownCover.Totals loneCover = new CrownCover.Totals();
		CrownCover.Totals standCover = new CrownCover.Totals();
		for (int i = 0; i < placed.size(); i++) {
			(i < lone ? loneCover : standCover).add(CrownCover.of(placed.get(i).voxels()));
		}
		PolishForests.LOG.info(String.format(Locale.ROOT, "[trees] bare crown wood open to the side: lone trees %.2f per tree, stands %.2f "
				+ "per tree (%s)", loneCover.exposedPerTree(), standCover.exposedPerTree(), standCover.report(18)));
		if (loneCover.exposedPerTree() > MAX_BARE_LONE || standCover.exposedPerTree() > MAX_BARE_STAND) {
			throw new AssertionError(String.format(Locale.ROOT, "[trees] bare crown wood open to the side: lone trees %.2f (limit %.1f), "
					+ "stands %.2f (limit %.1f) per tree", loneCover.exposedPerTree(), MAX_BARE_LONE, standCover.exposedPerTree(),
					MAX_BARE_STAND));
		}
		String first = check(level, placed, true);
		PolishForests.LOG.info("[trees] planted: {}", first);
		PolishForests.LOG.info("[trees] ecotone: {}", ecotone(level, generator, x0 + ECOTONE_X, y, z0));
		PolishForests.LOG.info("[trees] obstacles: {}", obstacles(level, blocked, x0 + OBSTACLE_X, y, z0));
		return new int[] {x0, y, z0};
	}

	/** A stand of 3 × 3 chunks from (x, z) on: every kept candidate with its territory, written by the feature's writer. */
	private static void stand(ServerLevel level, List<Placed> placed, boolean grad, int x, int y, int z) {
		AgeClass age = grad ? AgeClass.OLD_GROWTH : AgeClass.MATURE;
		StandMap map = StandMap.uniform(age, grad);
		CrownTerritories.Session s = new CrownTerritories(level.getSeed(), map).session();
		int cx0 = x >> 4;
		int cz0 = z >> 4;
		for (int cx = cx0; cx < cx0 + 3; cx++) {
			for (int cz = cz0; cz < cz0 + 3; cz++) {
				for (CrownTerritory t : s.chunk(cx, cz, null)) {
					Species sp = species(grad, t.layer(), t.x(), t.z());
					TreeVoxels v = TreeShapePlan.plan(new TreeRequest(sp, t.layer(), age, level.getSeed(), t.x(), y, t.z(), t,
							s::candidate, map));
					if (v.woodCount() == 0) {
						// A suppressed canopy tree (V10): not planted, as in the world.
						continue;
					}
					ProceduralTreeFeature.write(level, v, sp, t.layer(), age);
					placed.add(new Placed(v, sp));
				}
			}
		}
	}

	/**
	 * Risk R5 (docs/04 §8, decision V5-h): an oak-hornbeam stand of 3 × 3 chunks where {@value #SUBSTITUTES} of the
	 * candidates grow the vanilla substitute of M2 ({@code polishforests:tree_substitute/oak}, which ignores the crown territories)
	 * and the others procedural trees with their territories, written once with the procedural trees first and once with
	 * the substitutes first (the area cleared in between, each substitute with the same random source). Reports the blocks
	 * that differ between the two orders by kind, for the record of risk R5 (no assertion): leaves (the leaves written
	 * last win) and wood where a branch of a substitute, which ignores the territories, meets procedural wood (the vanilla
	 * trunk placer overwrites logs, a procedural block is not written over a log, so its parts above stay out).
	 */
	private static String ecotone(ServerLevel level, ChunkGenerator generator, int x, int y, int z) {
		Holder<PlacedFeature> oak = level.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE)
				.getOrThrow(ResourceKey.create(Registries.PLACED_FEATURE, PolishForests.id("tree_substitute/oak")));
		StandMap map = StandMap.uniform(AgeClass.OLD_GROWTH, true);
		CrownTerritories.Session s = new CrownTerritories(level.getSeed(), map).session();
		List<CrownTerritory> all = new ArrayList<>();
		for (int cx = (x >> 4); cx < (x >> 4) + 3; cx++) {
			for (int cz = (z >> 4); cz < (z >> 4) + 3; cz++) {
				all.addAll(s.chunk(cx, cz, null));
			}
		}
		int x1 = x - 16;
		int z1 = z - 16;
		int sx = 80;
		int sy = 44;
		int sz = 80;
		BlockState[][] runs = new BlockState[2][];
		int substitutes = 0;
		for (int order = 0; order < 2; order++) {
			BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
			for (int i = 0; i < sx; i++) {
				for (int j = 0; j < sy; j++) {
					for (int k = 0; k < sz; k++) {
						level.setBlock(pos.set(x1 + i, y + j, z1 + k), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | 16);
					}
				}
			}
			substitutes = 0;
			for (int pass = 0; pass < 2; pass++) {
				boolean vanilla = (pass == 0) == (order == 1);
				for (CrownTerritory t : all) {
					long h = pl.polishforests.worldgen.landscape.Noise.mix(pl.polishforests.worldgen.landscape.Noise.key(t.x(), t.z(), 91));
					boolean substitute = (h >>> 11) * 0x1.0p-53 < SUBSTITUTES;
					if (substitute != vanilla) {
						continue;
					}
					if (substitute) {
						substitutes++;
						oak.value().place(level, generator, new XoroshiroRandomSource(h), new BlockPos(t.x(), y, t.z()));
					} else {
						Species sp = species(true, t.layer(), t.x(), t.z());
						ProceduralTreeFeature.write(level, TreeShapePlan.plan(new TreeRequest(sp, t.layer(), AgeClass.OLD_GROWTH,
								level.getSeed(), t.x(), y, t.z(), t, s::candidate, map)), sp, t.layer(), AgeClass.OLD_GROWTH);
					}
				}
			}
			BlockState[] snap = new BlockState[sx * sy * sz];
			for (int i = 0; i < sx; i++) {
				for (int j = 0; j < sy; j++) {
					for (int k = 0; k < sz; k++) {
						snap[(i * sy + j) * sz + k] = level.getBlockState(pos.set(x1 + i, y + j, z1 + k));
					}
				}
			}
			runs[order] = snap;
		}
		int trunkLog = 0;
		int trunk = 0;
		int log = 0;
		int leaves = 0;
		int other = 0;
		int blocks = 0;
		for (int i = 0; i < runs[0].length; i++) {
			BlockState a = runs[0][i];
			BlockState b = runs[1][i];
			if (!a.isAir() || !b.isAir()) {
				blocks++;
			}
			if (a == b) {
				continue;
			}
			boolean ta = a.getBlock() instanceof TrunkBlock;
			boolean tb = b.getBlock() instanceof TrunkBlock;
			if ((ta && b.is(BlockTags.LOGS) && !tb) || (tb && a.is(BlockTags.LOGS) && !ta)) {
				trunkLog++;
			} else if (ta || tb) {
				trunk++;
			} else if (a.is(BlockTags.LOGS) || b.is(BlockTags.LOGS)) {
				log++;
			} else if (a.getBlock() instanceof LeavesBlock || b.getBlock() instanceof LeavesBlock) {
				leaves++;
			} else {
				other++;
			}
		}
		return String.format(Locale.ROOT, "%d candidates, %d vanilla substitutes; %d blocks, differing between the orders: %d "
				+ "procedural against vanilla wood, %d procedural wood against air or leaves (the parts above a blocked block), "
				+ "%d vanilla wood, %d leaves, %d other", all.size(), substitutes, blocks, trunkLog, trunk, log, leaves, other);
	}

	/**
	 * Lone mature trees of six species with stone in every fifth planned leaf position and in one thin branch block (and
	 * so without the branch's outer part), written by {@link ProceduralTreeFeature#write}: the leaf distances must come
	 * from the wood and the leaves that exist ({@link #check} after the planting and after 200 ticks).
	 */
	private static String obstacles(ServerLevel level, List<Placed> blocked, int x, int y, int z) {
		Species[] species = {Species.OAK, Species.LINDEN, Species.SCOTS_PINE, Species.SPRUCE, Species.BEECH, Species.HORNBEAM};
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int stones = 0;
		for (int k = 0; k < species.length; k++) {
			int tx = x + 40 * (k % 3);
			int tz = z + 40 * (k / 3);
			TreeVoxels v = TreeShapePlan.plan(new TreeRequest(species[k], CrownLayer.CANOPY, AgeClass.MATURE, level.getSeed(), tx, y,
					tz));
			for (int i = 0; i < v.leafCount(); i += 5) {
				level.setBlock(pos.set(v.x() + v.leafX(i), v.y() + v.leafY(i), v.z() + v.leafZ(i)), Blocks.STONE.defaultBlockState(),
						Block.UPDATE_CLIENTS | 16);
				stones++;
			}
			for (int i = v.woodCount() / 2; i < v.woodCount(); i++) {
				if (v.radius(i) <= 2 && v.parent(i) >= TreeVoxels.NORTH) {
					level.setBlock(pos.set(v.x() + v.woodX(i), v.y() + v.woodY(i), v.z() + v.woodZ(i)), Blocks.STONE.defaultBlockState(),
							Block.UPDATE_CLIENTS | 16);
					stones++;
					break;
				}
			}
			ProceduralTreeFeature.write(level, v, species[k], CrownLayer.CANOPY, AgeClass.MATURE);
			blocked.add(new Placed(v, species[k]));
		}
		return stones + " stones; " + check(level, blocked, false);
	}

	/** Species of a stand tree as in the PNG preview ({@code TreePreview.standSpecies}). */
	private static Species species(boolean grad, CrownLayer layer, int x, int z) {
		double u = (pl.polishforests.worldgen.landscape.Noise.mix(pl.polishforests.worldgen.landscape.Noise.key(x, z,
				grad ? 77 : 78)) >>> 11) * 0x1.0p-53;
		if (!grad) {
			return switch (layer) {
				case CANOPY -> u < 0.88 ? Species.SCOTS_PINE : Species.BIRCH;
				case SUBCANOPY -> u < 0.6 ? Species.SPRUCE : Species.BIRCH;
				case REGENERATION -> u < 0.5 ? Species.SPRUCE : Species.SCOTS_PINE;
			};
		}
		return switch (layer) {
			case CANOPY -> u < 0.4 ? Species.OAK : u < 0.7 ? Species.LINDEN : u < 0.8 ? Species.NORWAY_MAPLE : u < 0.9
					? Species.ASH : Species.BEECH;
			case SUBCANOPY -> u < 0.8 ? Species.HORNBEAM : Species.LINDEN;
			case REGENERATION -> u < 0.6 ? Species.HORNBEAM : Species.LINDEN;
		};
	}

	/**
	 * Every planned wood block is in the world with its state, and every leaf at a planned leaf position has a distance of
	 * at most 6 that the world supports: no less than the game would compute from the six neighbors (the least of them,
	 * a log counting 0 and a leaf its own distance, plus one; {@link LeavesBlock#getOptionalDistanceAt}), so a chain of
	 * falling distances leads from every leaf to a log and no leaf decays. (More is allowed: a leaf of a neighboring tree
	 * may be closer to its wood.) The blocks are written with flags that skip the shape updates, so the leaves never get a
	 * tick of their own: the check computes what such a tick would need, so a distance through a leaf that could not be
	 * placed fails it. With {@code strict} a missing wood block fails the test.
	 */
	private static String check(ServerLevel level, List<Placed> placed, boolean strict) {
		int wood = 0;
		int missing = 0;
		int leaves = 0;
		int decaying = 0;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (Placed p : placed) {
			TreeVoxels v = p.voxels();
			for (int i = 0; i < v.woodCount(); i++) {
				pos.set(v.x() + v.woodX(i), v.y() + v.woodY(i), v.z() + v.woodZ(i));
				BlockState want = ProceduralTreeFeature.wood(p.species(), v.bark(i), v.radius(i), Direction.from3DDataValue(v.parent(i)));
				if (level.getBlockState(pos) != want) {
					missing++;
				}
				wood++;
			}
			for (int i = 0; i < v.leafCount(); i++) {
				pos.set(v.x() + v.leafX(i), v.y() + v.leafY(i), v.z() + v.leafZ(i));
				BlockState s = level.getBlockState(pos);
				if (s.getBlock() instanceof LeavesBlock) {
					leaves++;
					int expected = LeavesBlock.DECAY_DISTANCE;
					for (Direction d : Direction.values()) {
						java.util.OptionalInt n = LeavesBlock.getOptionalDistanceAt(level.getBlockState(pos.relative(d)));
						if (n.isPresent()) {
							expected = Math.min(expected, n.getAsInt() + 1);
						}
					}
					int stored = s.getValue(LeavesBlock.DISTANCE);
					if (stored >= LeavesBlock.DECAY_DISTANCE || stored < expected) {
						decaying++;
					}
				}
			}
		}
		String report = String.format(Locale.ROOT, "%d trees, %d wood blocks (%d missing or other), %d leaves (%d at distance 7 or not supported by the world)",
				placed.size(), wood, missing, leaves, decaying);
		if ((strict && missing > 0) || decaying > 0) {
			throw new AssertionError("[trees] " + report);
		}
		return report;
	}

	private static void shot(ClientGameTestContext context, TestSingleplayerContext sp, String name, double x, double y, double z,
			double yaw, double pitch, int waitSeconds) {
		sp.getServer().runCommand(String.format(Locale.ROOT, "tp @a %.2f %.2f %.2f %.1f %.1f", x, y, z, yaw, pitch));
		// Step T7: until the view is drawn (ViewReady) instead of a fixed wait of waitSeconds.
		ViewReady.shot(context, sp, name, SIZE, waitSeconds);
		PolishForests.LOG.info(String.format(Locale.ROOT, "[trees] %s: camera (%.1f, %.1f, %.1f) yaw %.0f pitch %.0f", name, x, y, z,
				yaw, pitch));
	}
}
