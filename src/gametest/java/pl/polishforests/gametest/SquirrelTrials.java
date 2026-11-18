package pl.polishforests.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import pl.polishforests.command.FaunaSites;
import pl.polishforests.entity.ModEntities;
import pl.polishforests.entity.base.PolishAnimal;
import pl.polishforests.entity.squirrel.RedSquirrel;
import pl.polishforests.entity.squirrel.SquirrelMind;
import pl.polishforests.entity.squirrel.TreeMotion;
import pl.polishforests.entity.squirrel.TreeSpace;
import pl.polishforests.worldgen.habitat.HabitatBiome;

/**
 * The trials of the red squirrel in the fauna game test (step FA5, docs/04-pionowy-wycinek.md "Projekt FA5"), numbers
 * only (G.36).
 *
 * <ol>
 * <li><b>Life in the trees</b> ({@link #life}): in a mixed pine forest found by the search of {@code find biome}
 * ({@link FaunaSites#locate}) near the first place of the world (the place itself when none is found), {@value #LIFE_COUNT}
 * squirrels 4-24 blocks around the invisible player, {@value #LIFE_TICKS} ticks at noon, a sample every
 * {@value #LIFE_SAMPLE} ticks: where they are ({@link #where}: on the ground, on a lying log, on a trunk, in a crown), how
 * many are stuck in blocks ({@link #stuck}), the leaps (over a gap too) and the falls with damage. The share in the trees
 * (trunk and crown) must be above 0, no squirrel stuck and no fall with damage.
 * <li><b>Flight up a tree</b> ({@link #flight}): a squirrel on the ground {@value #FLIGHT_FROM_TRUNK} blocks from a trunk
 * of at least 6 blocks, a visible standing player 5 blocks from it on the other side: within {@value #FLIGHT_TICKS} ticks
 * it must flee and be at least 4 blocks up a trunk; the climb time (ticks from the start of the flight to 4 blocks up) is reported.
 * <li><b>MSPT</b> ({@link #mspt}): 40 squirrels around the player in the trees and on the ground, their server ticks timed:
 * at most {@value FaunaTrials#MSPT_BUDGET} ms per squirrel and tick (report only with {@code -PtimingsReportOnly}).
 * </ol>
 * The census of the places ({@link FaunaCensus}) counts the squirrels of the spawners the same way ({@link #where},
 * {@link #stuck}) and needs at least one in every world.
 */
final class SquirrelTrials {
	static final int LIFE_COUNT = 12;
	static final int LIFE_TICKS = 2400;
	static final int LIFE_SAMPLE = 100;
	static final int FLIGHT_FROM_TRUNK = 2;
	static final int FLIGHT_TICKS = 120;
	private static final boolean REPORT_ONLY = Boolean.getBoolean("polishforests.timings.reportOnly");

	private final ClientGameTestContext context;
	private final FaunaCensus census;

	SquirrelTrials(ClientGameTestContext context, FaunaCensus census) {
		this.context = context;
		this.census = census;
	}

	// ------------------------------------------------------------------ where a squirrel is

	/** Where a squirrel is: {@code trunk}, {@code crown} (in the tree mode, leaves within a block), {@code log}, {@code leaves}, {@code ground}. */
	static String where(ServerLevel level, RedSquirrel q) {
		if (q.inTree()) {
			return TreeSpace.inCrown(level, q.position()) ? "crown" : "trunk";
		}
		BlockState below = level.getBlockState(BlockPos.containing(q.getX(), q.getY() - 0.2, q.getZ()));
		if (TreeSpace.lying(below)) {
			return "log";
		}
		return below.is(BlockTags.LEAVES) ? "leaves" : "ground";
	}

	/**
	 * Whether a squirrel is stuck in blocks: in the tree mode a collision of a block other than leaves in its hitbox (shrunk
	 * by 0.02 blocks), on the ground any collision (leaves included) or vanilla's "in a wall".
	 */
	static boolean stuck(ServerLevel level, RedSquirrel q) {
		return blocker(level, q) != null;
	}

	/** What a stuck squirrel is stuck in ({@link #stuck}): the block and its position, or null when it is not stuck. */
	static @Nullable String blocker(ServerLevel level, RedSquirrel q) {
		AABB box = q.getBoundingBox().deflate(0.02);
		BlockPos at = TreeSpace.collision(level, box, q.inTree());
		if (at == null && !q.inTree() && q.isInWall()) {
			at = BlockPos.containing(q.getEyePosition());
		}
		return at == null ? null : level.getBlockState(at) + " at " + at.toShortString();
	}

	static void resetCounters() {
		TreeMotion.resetCounters();
		SquirrelMind.resetCounters();
		RedSquirrel.FALL_HURT.reset();
	}

	/** The counters of the movement now, in the order of {@link #counters(long[])}: a trial reports the differences. */
	static long[] mark() {
		return new long[] {TreeMotion.CLIMBS.sum(), TreeMotion.CLIMB_TICKS.sum(), TreeMotion.LEAPS.sum(), TreeMotion.GAP_LEAPS.sum(),
				TreeMotion.LOG_RUNS.sum(), TreeMotion.DROPS.sum(), TreeMotion.JUMPS_DOWN.sum(), TreeMotion.ABORTS.sum(), TreeMotion.GRABS.sum(),
				TreeMotion.RESCUES.sum(), SquirrelMind.FLIGHTS.sum(), SquirrelMind.FLIGHTS_TO_TREE.sum(), SquirrelMind.FLIGHT_CLIMB_TICKS.sum(),
				SquirrelMind.GROUND_FLIGHTS.sum(), RedSquirrel.FALL_HURT.sum(), SquirrelMind.ATTACH_FAILS.sum(), SquirrelMind.TO_TREE_TIMEOUTS.sum()};
	}

	/** Falls with damage since the mark. */
	static long fallsSince(long[] since) {
		return RedSquirrel.FALL_HURT.sum() - since[14];
	}

	/** The counters of the squirrels' movement since the census of the world began, as one line. */
	static String counters() {
		return counters(new long[17]);
	}

	/** The counters of the squirrels' movement since the mark, as one line. */
	static String counters(long[] since) {
		long[] n = mark();
		for (int i = 0; i < n.length; i++) {
			n[i] -= since[i];
		}
		return String.format(Locale.ROOT, "climbs %d (%d ticks up), leaps %d (over a gap %d), runs onto a log %d, drops %d, jumps down %d,"
				+ " aborted legs %d, trunks taken from the crown %d, rescues from the leaves %d, flights %d (up a tree %d, mean climb %.1f"
				+ " ticks; on the ground %d), runs to a trunk not taken %d (time over %d), falls with damage %d", n[0], n[1], n[2], n[3], n[4],
				n[5], n[6], n[7], n[8], n[9], n[10], n[11], n[11] == 0 ? Double.NaN : n[12] / (double) n[11], n[13], n[15], n[16], n[14]);
	}

	// ------------------------------------------------------------------ life in the trees

	/** Every trial of the squirrel at one place of a world (the MSPT scene apart). */
	void all(TestSingleplayerContext sp, String label, int x, int z) {
		int[] at = mixedPine(sp, x, z);
		life(sp, label, at[0], at[1]);
		flight(sp, label, at[0], at[1]);
	}

	/** The nearest mixed pine forest to the place (the search of find biome), or the place itself. */
	private int[] mixedPine(TestSingleplayerContext sp, int x, int z) {
		double[] at = sp.getServer().computeOnServer(s -> FaunaSites.locate(s.overworld(), HabitatBiome.MIXED_PINE_FOREST, x, z));
		if (at == null) {
			census.line("squirrels: no mixed pine forest found near %d %d, trials at the place", x, z);
			return new int[] {x, z};
		}
		return new int[] {(int) Math.floor(at[0]), (int) Math.floor(at[1])};
	}

	/** Places up to n squirrels 4-24 blocks around the player on valid places of the spawners. */
	private static List<RedSquirrel> place(TestSingleplayerContext sp, int n) {
		return sp.getServer().computeOnServer(s -> {
			ServerLevel level = s.overworld();
			ServerPlayer p = level.players().getFirst();
			List<RedSquirrel> out = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				double angle = Math.PI * 2 * i / n;
				double d = 4 + 20.0 * ((i * 7) % n) / n;
				Vec3 at = FaunaCensus.trialSpot(level, ModEntities.redSquirrel(), p.getBlockX() + (int) Math.round(Math.cos(angle) * d),
						p.getBlockZ() + (int) Math.round(Math.sin(angle) * d));
				RedSquirrel q = ModEntities.redSquirrel().create(level, EntitySpawnReason.COMMAND);
				q.snapTo(at.x, at.y, at.z, level.getRandom().nextFloat() * 360, 0);
				q.finalizeSpawn(level, level.getCurrentDifficultyAt(q.blockPosition()), EntitySpawnReason.COMMAND, null);
				level.addFreshEntity(q);
				out.add(q);
			}
			return out;
		});
	}

	void life(TestSingleplayerContext sp, String label, int x, int z) {
		FaunaCensus.teleport(sp, x, z);
		context.waitTicks(200);
		long[] since = sp.getServer().computeOnServer(s -> mark());
		List<RedSquirrel> squirrels = place(sp, LIFE_COUNT);
		Map<String, Integer> where = new TreeMap<>();
		int samples = 0;
		List<String> stuck = new ArrayList<>();
		for (int t = 0; t < LIFE_TICKS; t += LIFE_SAMPLE) {
			context.waitTicks(LIFE_SAMPLE);
			sp.getServer().runOnServer(s -> {
				ServerLevel level = s.overworld();
				for (RedSquirrel q : squirrels) {
					if (!q.isAlive()) {
						continue;
					}
					where.merge(where(level, q), 1, Integer::sum);
					String in = blocker(level, q);
					if (in != null) {
						stuck.add(String.format(Locale.ROOT, "at %.2f %.2f %.2f (%s, %s) in %s", q.getX(), q.getY(), q.getZ(), q.motionKind(),
								where(level, q), in));
					}
				}
			});
			samples++;
		}
		int all = where.values().stream().mapToInt(Integer::intValue).sum();
		int inTrees = where.getOrDefault("trunk", 0) + where.getOrDefault("crown", 0);
		long alive = sp.getServer().computeOnServer(s -> squirrels.stream().filter(Entity::isAlive).count());
		census.line("%s squirrels in a mixed pine forest at %d %d: %d placed (%d alive after %d ticks), %d samples: %s; in the trees %.1f%%"
				+ " (trunk %.1f%%, crown %.1f%%); stuck %d; %s", label, x, z, LIFE_COUNT, alive, LIFE_TICKS, samples, where,
				100.0 * inTrees / Math.max(1, all), 100.0 * where.getOrDefault("trunk", 0) / Math.max(1, all),
				100.0 * where.getOrDefault("crown", 0) / Math.max(1, all), stuck.size(), counters(since));
		if (inTrees == 0) {
			census.fail("%s: no squirrel on a trunk or in a crown in %d samples", label, samples);
		}
		if (!stuck.isEmpty()) {
			census.fail("%s: squirrels stuck in blocks %d times: %s", label, stuck.size(), String.join("; ", stuck.subList(0,
					Math.min(5, stuck.size()))));
		}
		long hurt = fallsSince(since);
		if (hurt > 0) {
			census.fail("%s: %d falls of squirrels with damage", label, hurt);
		}
		sp.getServer().runOnServer(s -> squirrels.forEach(Entity::discard));
	}

	// ------------------------------------------------------------------ flight up a tree

	/**
	 * A trunk of at least 6 blocks 6-16 blocks from the player with a place on the ground {@value #FLIGHT_FROM_TRUNK}
	 * blocks from it at its base, nearest first, or null. Every column of the ring is looked at (trunksNear stops one ring
	 * after the nearest trunk, which in a forest is closer than 6 blocks), its base within 3 blocks of the player's feet, and
	 * the ground 5 blocks further out (where the trial player stands) within 2 blocks of the squirrel's.
	 */
	private static int @Nullable [] flightSpot(ServerLevel level, ServerPlayer p) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int r = 6; r <= 16; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					for (int dy = -3; dy <= 3; dy++) {
						pos.set(p.getBlockX() + dx, p.getBlockY() + dy, p.getBlockZ() + dz);
						if (!TreeSpace.upright(level.getBlockState(pos))) {
							continue;
						}
						TreeSpace.Trunk t = TreeSpace.trunkAt(level, pos);
						if (t == null || t.height() < 6 || Math.abs(t.baseY() - p.getBlockY()) > 3) {
							break;
						}
						for (int[] side : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
							int x = t.x() + side[0] * FLIGHT_FROM_TRUNK;
							int z = t.z() + side[1] * FLIGHT_FROM_TRUNK;
							Vec3 at = pl.polishforests.entity.spawn.FaunaSpawner.placeUncounted(level, ModEntities.redSquirrel(), x, z);
							// the player's ground 5 blocks further out near the squirrel's (not on a rock, a hill or a stump)
							int px = Mth.floor(x + 0.5 + side[0] * 5);
							int pz = Mth.floor(z + 0.5 + side[1] * 5);
							int py = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, px, pz);
							if (at != null && Math.abs(at.y - t.baseY()) <= 1 && Math.abs(py - at.y) <= 2) {
								return new int[] {x, z, t.x(), t.z()};
							}
						}
						break;
					}
				}
			}
		}
		return null;
	}

	void flight(TestSingleplayerContext sp, String label, int x, int z) {
		FaunaCensus.teleport(sp, x, z);
		context.waitTicks(200);
		long[] since = sp.getServer().computeOnServer(s -> mark());
		int[] spot = sp.getServer().computeOnServer(s -> flightSpot(s.overworld(), s.overworld().players().getFirst()));
		if (spot == null) {
			census.fail("%s squirrel flight: no trunk of 6 blocks with a place next to it within 16 blocks of %d %d", label, x, z);
			return;
		}
		// The player 5 blocks from the squirrel on the side away from the trunk, visible and standing (flight distance 8).
		double dx = spot[0] - spot[2];
		double dz = spot[1] - spot[3];
		double len = Math.max(1e-6, Math.hypot(dx, dz));
		double px = spot[0] + 0.5 + dx / len * 5;
		double pz = spot[1] + 0.5 + dz / len * 5;
		int py = sp.getServer().computeOnServer(s -> s.overworld().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
				(int) Math.floor(px), (int) Math.floor(pz)));
		sp.getServer().runCommand("effect clear @a minecraft:invisibility");
		sp.getServer().runCommand(String.format(Locale.ROOT, "tp @a %.2f %d %.2f", px, py, pz));
		RedSquirrel q = sp.getServer().computeOnServer(s -> {
			ServerLevel level = s.overworld();
			Vec3 at = pl.polishforests.entity.spawn.FaunaSpawner.placeUncounted(level, ModEntities.redSquirrel(), spot[0], spot[1]);
			RedSquirrel r = ModEntities.redSquirrel().create(level, EntitySpawnReason.COMMAND);
			r.snapTo(at.x, at.y, at.z, 0, 0);
			level.addFreshEntity(r);
			return r;
		});
		double groundY = q.getY();
		double[] best = {0};
		boolean[] fled = {false};
		double[] dist = {Double.MAX_VALUE};
		List<String> goals = new ArrayList<>();
		for (int t = 0; t < FLIGHT_TICKS; t += 5) {
			context.waitTicks(5);
			sp.getServer().runOnServer(s -> {
				fled[0] |= q.isFleeing();
				dist[0] = Math.min(dist[0], q.distanceTo(s.overworld().players().getFirst()));
				if (q.inTree()) {
					best[0] = Math.max(best[0], q.getY() - groundY);
				}
				String g = String.format(Locale.ROOT, "%s at %.1f %.1f %.1f", q.mind().describe(), q.getX(), q.getY(), q.getZ());
				if (goals.isEmpty() || !goals.getLast().startsWith(q.mind().describe() + " at")) {
					goals.add(g);
				}
			});
		}
		String end = sp.getServer().computeOnServer(s -> String.format(Locale.ROOT, "squirrel at %.1f %.1f %.1f, %s, state %s",
				q.getX(), q.getY(), q.getZ(), where(s.overworld(), q), q.state().id()));
		census.line("%s squirrel flight: trunk at %d %d, squirrel %d blocks from it, player 5 blocks away (nearest %.1f): fleeing %s, highest"
				+ " %.1f blocks up a trunk in %d ticks; %s; %s", label, spot[2], spot[3], FLIGHT_FROM_TRUNK, dist[0], fled[0], best[0], FLIGHT_TICKS, end,
				counters(since));
		if (best[0] < 4 || !fled[0]) {
			census.fail("%s: the squirrel did not flee up a tree (fleeing %s, highest %.1f blocks up a trunk); goals: %s", label, fled[0], best[0],
					String.join("; ", goals.subList(0, Math.min(10, goals.size()))));
		}
		sp.getServer().runOnServer(s -> q.discard());
		sp.getServer().runCommand("effect give @a minecraft:invisibility infinite 0 true");
		FaunaCensus.teleport(sp, x, z);
	}

	// ------------------------------------------------------------------ MSPT

	void mspt(TestSingleplayerContext sp, String label, int x, int z) {
		int[] at = mixedPine(sp, x, z);
		FaunaCensus.teleport(sp, at[0], at[1]);
		context.waitTicks(200);
		// Only squirrels tick in the timing: the other animals of the mod around are removed.
		sp.getServer().runOnServer(s -> {
			ServerLevel level = s.overworld();
			List<Entity> other = new ArrayList<>();
			level.getAllEntities().forEach(e -> {
				if (e instanceof PolishAnimal<?> && !(e instanceof RedSquirrel)) {
					other.add(e);
				}
			});
			other.forEach(Entity::discard);
		});
		List<RedSquirrel> squirrels = place(sp, 40);
		// Let them climb first: the timing covers squirrels in the trees and on the ground.
		context.waitTicks(400);
		String spread = sp.getServer().computeOnServer(s -> {
			Map<String, Integer> where = new TreeMap<>();
			squirrels.stream().filter(Entity::isAlive).forEach(q -> where.merge(where(s.overworld(), q), 1, Integer::sum));
			return where.toString();
		});
		sp.getServer().runOnServer(s -> PolishAnimal.setTiming(true));
		context.waitTicks(FaunaTrials.MSPT_TICKS);
		double[] t = sp.getServer().computeOnServer(s -> {
			PolishAnimal.setTiming(false);
			return new double[] {PolishAnimal.TICK_NANOS.sum() / 1e6, PolishAnimal.TICKS.sum()};
		});
		double perAnimal = t[1] == 0 ? Double.NaN : t[0] / t[1];
		long all = sp.getServer().computeOnServer(s -> (long) s.overworld().getEntities(ModEntities.redSquirrel(), Entity::isAlive).size());
		census.line("%s MSPT red squirrel: 40 trial squirrels (%d loaded; at the start %s), %.0f animal ticks in %d ticks: %.4f ms per animal"
				+ " and tick (budget %.2f)%s", label, all, spread, t[1], FaunaTrials.MSPT_TICKS, perAnimal, FaunaTrials.MSPT_BUDGET,
				REPORT_ONLY ? "; report only (-PtimingsReportOnly)" : "");
		if (!(perAnimal <= FaunaTrials.MSPT_BUDGET) && !REPORT_ONLY) {
			census.fail("%s MSPT %.4f ms per red squirrel and tick above %.2f", label, perAnimal, FaunaTrials.MSPT_BUDGET);
		}
		sp.getServer().runOnServer(s -> squirrels.forEach(Entity::discard));
	}
}
