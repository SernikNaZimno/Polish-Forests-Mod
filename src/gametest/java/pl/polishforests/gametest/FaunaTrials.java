package pl.polishforests.gametest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import pl.polishforests.client.entity.PolishAnimalRenderer;
import pl.polishforests.entity.ModEntities;
import pl.polishforests.entity.base.AnimalProfile;
import pl.polishforests.entity.base.ForestAlarm;
import pl.polishforests.entity.base.PolishAnimal;
import pl.polishforests.entity.base.Thicket;
import pl.polishforests.entity.roedeer.RoeDeer;
import pl.polishforests.season.Seasons;
import pl.polishforests.season.SubSeason;

/**
 * The behaviour trials of the fauna game test (step FA2, docs/04-pionowy-wycinek.md "Projekt FA2"): numbers and
 * assertions only, no screenshots (G.36 U-1). Each trial works with roe deer it places itself ({@code COMMAND} spawn, so
 * they never despawn) around the player (creative, invisible: a threat only from 2-3 blocks), except the looks and the
 * groups, which check every roe deer near the place.
 *
 * <ol>
 * <li><b>Activity by time of day</b> ({@link #activity}): {@value #HERD_GROUPS} groups of three at 16-40 blocks; at dawn
 * (6:30), noon, dusk (18:30) and night (midnight) set by {@code SeasonDates.setTimeOfDay} (time stopped), after
 * {@value #ACTIVITY_SETTLE} ticks {@value #ACTIVITY_SAMPLES} samples every 100 ticks of the shares grazing, resting and
 * moving. Pass: the grazing share at dawn and at dusk is each at least {@value #ACTIVITY_MARGIN} above the share at noon
 * and at night, and the resting share at noon above the one at dawn. The share of the resting deer lying in a thicket
 * ({@link Thicket#score} at least {@link Thicket#MIN_SCORE}) is reported.
 * <li><b>Daily range</b> ({@link #range}): the same deer through 8 times of day 3 hours apart, {@value #RANGE_TICKS} ticks
 * each: the largest and the median horizontal distance from the start. Pass: largest at most the home range plus
 * {@value #RANGE_SLACK} blocks, median at least 1 block (they move).
 * <li><b>Groups</b> ({@link #groups}): mid winter for {@value #GROUP_WINTER_TICKS} ticks (groups merge), then mid summer
 * for {@value #GROUP_SUMMER_TICKS} ticks (groups split): the sizes of the groups of every roe deer within
 * {@value #NEAR} blocks. Pass: summer every group 1-3; winter every group at most 6 and at least {@value #WINTER_SHARE}
 * of the deer in groups of 2-6.
 * <li><b>Looks</b> ({@link #looks}): in each given sub-season, after {@value #LOOK_TICKS} ticks, every roe deer within
 * {@value #NEAR} blocks has the coat and antlers of the sub-season ({@code RoeDeer.looksAsIn}): 100%; every variant seen
 * and every variant of {@code RoeDeer.variants()} has its texture in the client's resources.
 * <li><b>Flight distance</b> ({@link #flightDistance}): a visible player walks (teleport steps of 1 block every 5 ticks, not
 * sprinting, not sneaking) towards a roe deer 50 blocks away: the distance at which it starts to flee is 20-40 blocks, it
 * barks and flares its rump patch.
 * <li><b>Alarm</b> ({@link #alarm}): a test alarm of radius 40 and strength 1 at the player: a deer 8 blocks away flees (it
 * is more than 16 blocks away after 100 ticks), one 30 blocks away on the other side stands alert with the patch flared
 * and does not flee.
 * <li><b>MSPT</b> ({@link #mspt}, {@code -Psites=mspt}, KFA-6): 40 roe deer around the player, the server ticks of the
 * animals timed for {@value #MSPT_TICKS} ticks: at most {@value #MSPT_BUDGET} ms per animal and tick (only reported with
 * {@code -PtimingsReportOnly}), with the server's average tick before and after.
 * </ol>
 */
final class FaunaTrials {
	static final int HERD_GROUPS = 8;
	static final int ACTIVITY_SETTLE = 400;
	static final int ACTIVITY_SAMPLES = 6;
	static final double ACTIVITY_MARGIN = 0.10;
	static final int RANGE_TICKS = 300;
	static final int RANGE_SLACK = 32;
	static final int GROUP_WINTER_TICKS = 1200;
	static final int GROUP_SUMMER_TICKS = 400;
	static final double WINTER_SHARE = 0.9;
	static final int LOOK_TICKS = 120;
	static final int NEAR = 192;
	static final int MSPT_TICKS = 1200;
	static final double MSPT_BUDGET = 0.05;
	private static final boolean REPORT_ONLY = Boolean.getBoolean("polishforests.timings.reportOnly");
	/** Times of day of the activity trial: dawn 6:30, noon, dusk 18:30, midnight. */
	static final int[] TIMES = {500, 6000, 12500, 18000};
	static final String[] TIME_NAMES = {"dawn", "noon", "dusk", "night"};

	private final ClientGameTestContext context;
	private final FaunaCensus census;

	FaunaTrials(ClientGameTestContext context, FaunaCensus census) {
		this.context = context;
		this.census = census;
	}

	/** Every trial at one place of a world (the MSPT scene apart); the looks in the given sub-seasons. */
	void all(TestSingleplayerContext sp, String label, int x, int z, List<SubSeason> lookSeasons) {
		SeasonDates.set(sp, SubSeason.MID_SUMMER, 0, label);
		List<RoeDeer> herd = activity(sp, label, x, z);
		range(sp, label, herd);
		groups(sp, label);
		looks(sp, label, lookSeasons);
		sp.getServer().runOnServer(s -> herd.forEach(Entity::discard));
		SeasonDates.set(sp, SubSeason.MID_SUMMER, 0, label);
		flightDistance(sp, label, x, z);
		alarm(sp, label, x, z);
	}

	// ------------------------------------------------------------------ herd

	/** Places {@value #HERD_GROUPS} groups of three roe deer 16-40 blocks around the player, each following its first. */
	private List<RoeDeer> herd(TestSingleplayerContext sp) {
		return sp.getServer().computeOnServer(s -> {
			ServerLevel level = s.overworld();
			ServerPlayer p = level.players().getFirst();
			List<RoeDeer> out = new ArrayList<>();
			for (int g = 0; g < HERD_GROUPS; g++) {
				double angle = Math.PI * 2 * g / HERD_GROUPS;
				double d = 16 + 24 * ((g * 5) % HERD_GROUPS) / (double) HERD_GROUPS;
				RoeDeer leader = null;
				for (int i = 0; i < 3; i++) {
					Vec3 at = FaunaCensus.trialSpot(level, (int) Math.floor(p.getX() + Math.cos(angle) * d) + i, (int) Math.floor(p.getZ()
							+ Math.sin(angle) * d) + i);
					RoeDeer deer = ModEntities.roeDeer().create(level, EntitySpawnReason.COMMAND);
					deer.snapTo(at.x, at.y, at.z, level.getRandom().nextFloat() * 360, 0);
					deer.finalizeSpawn(level, level.getCurrentDifficultyAt(deer.blockPosition()), EntitySpawnReason.COMMAND, null);
					if (leader == null) {
						leader = deer;
					} else {
						deer.followLeader(leader);
					}
					level.addFreshEntity(deer);
					out.add(deer);
				}
			}
			return out;
		});
	}

	// ------------------------------------------------------------------ activity by time of day

	List<RoeDeer> activity(TestSingleplayerContext sp, String label, int x, int z) {
		FaunaCensus.teleport(sp, x, z);
		context.waitTicks(200);
		List<RoeDeer> herd = herd(sp);
		double[][] shares = new double[TIMES.length][];
		for (int t = 0; t < TIMES.length; t++) {
			SeasonDates.setTimeOfDay(sp, TIMES[t], label);
			context.waitTicks(ACTIVITY_SETTLE);
			double[] sum = new double[5];
			for (int i = 0; i < ACTIVITY_SAMPLES; i++) {
				context.waitTicks(100);
				double[] s = sp.getServer().computeOnServer(server -> activityShares(server.overworld(), herd));
				for (int k = 0; k < sum.length; k++) {
					sum[k] += s[k] / ACTIVITY_SAMPLES;
				}
			}
			shares[t] = sum;
			census.line("%s activity at %s (%d): grazing %.2f, resting %.2f, moving %.2f, other %.2f; resting in a thicket %.2f", label,
					TIME_NAMES[t], TIMES[t], sum[0], sum[1], sum[2], sum[3], sum[4]);
		}
		double dawn = shares[0][0];
		double noon = shares[1][0];
		double dusk = shares[2][0];
		double night = shares[3][0];
		boolean ok = dawn >= noon + ACTIVITY_MARGIN && dawn >= night + ACTIVITY_MARGIN && dusk >= noon + ACTIVITY_MARGIN
				&& dusk >= night + ACTIVITY_MARGIN && shares[1][1] > shares[0][1];
		census.line("%s activity: grazing dawn %.2f, noon %.2f, dusk %.2f, night %.2f; resting noon %.2f, dawn %.2f; %s", label, dawn, noon,
				dusk, night, shares[1][1], shares[0][1], ok ? "as scheduled" : "NOT as scheduled");
		if (!ok) {
			census.fail("%s activity: grazing at dawn and dusk not %.2f above noon and night, or resting at noon not above dawn", label,
					ACTIVITY_MARGIN);
		}
		return herd;
	}

	/** Shares of the live deer grazing, resting, moving (or fleeing), other; and of the resting ones in a thicket. */
	private static double[] activityShares(ServerLevel level, List<RoeDeer> herd) {
		int n = 0;
		int[] c = new int[4];
		int resting = 0;
		int thicket = 0;
		for (RoeDeer d : herd) {
			if (!d.isAlive()) {
				continue;
			}
			n++;
			PolishAnimal.State s = d.state();
			int k = switch (s) {
				case GRAZING -> 0;
				case RESTING -> 1;
				case MOVING, FLEEING -> 2;
				default -> 3;
			};
			c[k]++;
			if (s == PolishAnimal.State.RESTING) {
				resting++;
				if (Thicket.score(level, d.blockPosition()) >= Thicket.MIN_SCORE) {
					thicket++;
				}
			}
		}
		double m = Math.max(1, n);
		return new double[] {c[0] / m, c[1] / m, c[2] / m, c[3] / m, resting == 0 ? 0 : thicket / (double) resting};
	}

	// ------------------------------------------------------------------ daily range

	void range(TestSingleplayerContext sp, String label, List<RoeDeer> herd) {
		Map<UUID, Vec3> start = sp.getServer().computeOnServer(s -> {
			Map<UUID, Vec3> m = new HashMap<>();
			herd.forEach(d -> m.put(d.getUUID(), d.position()));
			return m;
		});
		Map<UUID, Double> largest = new HashMap<>();
		Map<UUID, Double> last = new HashMap<>();
		for (int h = 0; h < 8; h++) {
			SeasonDates.setTimeOfDay(sp, h * 3000, label);
			context.waitTicks(RANGE_TICKS);
			sp.getServer().runOnServer(s -> {
				for (RoeDeer d : herd) {
					Vec3 a = start.get(d.getUUID());
					if (a == null || !d.isAlive()) {
						continue;
					}
					double dist = Math.hypot(d.getX() - a.x, d.getZ() - a.z);
					largest.merge(d.getUUID(), dist, Math::max);
					last.put(d.getUUID(), dist);
				}
			});
		}
		List<Double> l = new ArrayList<>(largest.values());
		l.sort(null);
		double max = l.isEmpty() ? 0 : l.getLast();
		double median = l.isEmpty() ? 0 : l.get(l.size() / 2);
		double meanLast = last.values().stream().mapToDouble(Double::doubleValue).average().orElse(0);
		int limit = RoeDeer.PROFILE.homeRange() + RANGE_SLACK;
		census.line("%s daily range: %d deer through 8 times of day x %d ticks: largest distance from the start %.1f, median of the"
				+ " largest %.1f, mean at the end %.1f blocks (limit %d)", label, l.size(), RANGE_TICKS, max, median, meanLast, limit);
		if (l.isEmpty() || max > limit || median < 1) {
			census.fail("%s daily range: largest %.1f (limit %d), median %.1f (at least 1)", label, max, limit, median);
		}
	}

	// ------------------------------------------------------------------ groups

	void groups(TestSingleplayerContext sp, String label) {
		SeasonDates.set(sp, SubSeason.MID_WINTER, 0, label);
		context.waitTicks(GROUP_WINTER_TICKS);
		Map<Integer, Integer> winter = sp.getServer().computeOnServer(s -> herdSizes(s.overworld()));
		SeasonDates.set(sp, SubSeason.MID_SUMMER, 0, label);
		context.waitTicks(GROUP_SUMMER_TICKS);
		Map<Integer, Integer> summer = sp.getServer().computeOnServer(s -> herdSizes(s.overworld()));
		AnimalProfile p = RoeDeer.PROFILE;
		int winterDeer = 0;
		int winterOk = 0;
		boolean winterTooLarge = false;
		for (Map.Entry<Integer, Integer> e : winter.entrySet()) {
			winterDeer += e.getKey() * e.getValue();
			if (p.winterGroup().contains(e.getKey())) {
				winterOk += e.getKey() * e.getValue();
			}
			winterTooLarge |= e.getKey() > p.winterGroup().max();
		}
		boolean summerOk = summer.keySet().stream().allMatch(p.summerGroup()::contains);
		double share = winterDeer == 0 ? 0 : winterOk / (double) winterDeer;
		census.line("%s groups (size: count) within %d blocks: winter after %d ticks %s, %.2f of %d deer in groups of %d-%d; summer after"
				+ " %d ticks %s", label, NEAR, GROUP_WINTER_TICKS, winter, share, winterDeer, p.winterGroup().min(), p.winterGroup().max(),
				GROUP_SUMMER_TICKS, summer);
		if (winterDeer == 0 || winterTooLarge || share < WINTER_SHARE) {
			census.fail("%s groups: winter %s (%.2f in %d-%d, at least %.2f, none above %d)", label, winter, share, p.winterGroup().min(),
					p.winterGroup().max(), WINTER_SHARE, p.winterGroup().max());
		}
		if (summer.isEmpty() || !summerOk) {
			census.fail("%s groups: summer %s (every group %d-%d)", label, summer, p.summerGroup().min(), p.summerGroup().max());
		}
	}

	/** The sizes of the groups (by group id) of the live roe deer within {@value #NEAR} blocks of the player: size to count. */
	static Map<Integer, Integer> herdSizes(ServerLevel level) {
		ServerPlayer p = level.players().getFirst();
		Map<UUID, Integer> groups = new HashMap<>();
		for (RoeDeer d : near(level, p.position())) {
			groups.merge(d.herdId(), 1, Integer::sum);
		}
		Map<Integer, Integer> sizes = new TreeMap<>();
		groups.values().forEach(n -> sizes.merge(n, 1, Integer::sum));
		return sizes;
	}

	private static List<RoeDeer> near(ServerLevel level, Vec3 at) {
		AABB box = new AABB(at.x - NEAR, level.getMinY(), at.z - NEAR, at.x + NEAR, level.getMaxY(), at.z + NEAR);
		return level.getEntities(ModEntities.roeDeer(), box, Entity::isAlive);
	}

	// ------------------------------------------------------------------ looks

	void looks(TestSingleplayerContext sp, String label, List<SubSeason> seasons) {
		Set<String> seen = new HashSet<>();
		for (SubSeason sub : seasons) {
			SeasonDates.set(sp, sub, 0, label);
			context.waitTicks(LOOK_TICKS);
			int[] n = new int[3];
			Map<String, Integer> looks = new TreeMap<>();
			List<String> wrong = new ArrayList<>();
			sp.getServer().runOnServer(s -> {
				SubSeason now = Seasons.provider().subSeason(s.overworld());
				for (RoeDeer d : near(s.overworld(), s.overworld().players().getFirst().position())) {
					n[0]++;
					if (d.looksAsIn(now)) {
						n[1]++;
					} else if (wrong.size() < 5) {
						wrong.add(String.format(Locale.ROOT, "%s buck %s young %s coat %s antlers %s", d.getUUID(), d.isBuck(), d.isBaby(),
								d.hasWinterCoat() ? "winter" : "summer", d.antlers().id()));
					}
					if (d.isBaby()) {
						n[2]++;
					}
					seen.add(d.variant());
					d.looks().forEach(k -> looks.merge(k, 1, Integer::sum));
				}
				if (now != sub) {
					wrong.add("the date is " + now + ", not " + sub);
				}
			});
			census.line("%s looks in %s: %d of %d roe deer as in the sub-season, young %d; %s%s", label, sub, n[1], n[0], n[2], looks,
					wrong.isEmpty() ? "" : " wrong " + wrong);
			if (n[0] == 0 || n[1] != n[0] || !wrong.isEmpty()) {
				census.fail("%s looks in %s: %d of %d as in the sub-season %s", label, sub, n[1], n[0], wrong);
			}
		}
		Set<String> all = new HashSet<>(RoeDeer.variants());
		all.addAll(seen);
		List<String> missing = new ArrayList<>();
		for (String v : all) {
			boolean present = context.computeOnClient(mc -> mc.getResourceManager().getResource(PolishAnimalRenderer.texture("roe_deer", v))
					.isPresent());
			if (!present) {
				missing.add(v);
			}
		}
		census.line("%s variants seen %s; textures missing %s", label, new java.util.TreeSet<>(seen), missing);
		if (!missing.isEmpty()) {
			census.fail("%s textures missing for variants %s", label, missing);
		}
	}

	// ------------------------------------------------------------------ flight distance, alarm

	void flightDistance(TestSingleplayerContext sp, String label, int x, int z) {
		FaunaCensus.teleport(sp, x, z);
		context.waitTicks(100);
		sp.getServer().runCommand("effect clear @a minecraft:invisibility");
		long barks0 = RoeDeer.BARKS.sum();
		RoeDeer deer = sp.getServer().computeOnServer(s -> {
			ServerLevel level = s.overworld();
			ServerPlayer p = level.players().getFirst();
			Vec3 at = FaunaCensus.trialSpot(level, p.getBlockX() + 50, p.getBlockZ());
			RoeDeer d = ModEntities.roeDeer().create(level, EntitySpawnReason.COMMAND);
			d.snapTo(at.x, at.y, at.z, 0, 0);
			level.addFreshEntity(d);
			return d;
		});
		context.waitTicks(20);
		double startDistance = -1;
		boolean flared = false;
		for (int step = 0; step < 60 && startDistance < 0; step++) {
			double[] r = sp.getServer().computeOnServer(s -> {
				ServerPlayer p = s.overworld().players().getFirst();
				return new double[] {deer.getX() - p.getX(), deer.getZ() - p.getZ(), deer.distanceTo(p), deer.isFleeing() ? 1 : 0,
						deer.flared() ? 1 : 0};
			});
			if (r[3] > 0) {
				startDistance = r[2];
				flared = r[4] > 0;
				break;
			}
			if (r[2] < 6) {
				break;
			}
			double len = Math.hypot(r[0], r[1]);
			sp.getServer().runOnServer(s -> {
				ServerPlayer p = s.overworld().players().getFirst();
				double nx = p.getX() + r[0] / len;
				double nz = p.getZ() + r[1] / len;
				s.overworld().getChunk((int) Math.floor(nx) >> 4, (int) Math.floor(nz) >> 4);
				double ny = s.overworld().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
						(int) Math.floor(nx), (int) Math.floor(nz)) + 0.01;
				p.setSprinting(false);
				p.setShiftKeyDown(false);
				s.getCommands().performPrefixedCommand(s.createCommandSourceStack().withSuppressedOutput(),
						String.format(Locale.ROOT, "tp @a %.3f %.3f %.3f", nx, ny, nz));
			});
			context.waitTicks(5);
		}
		context.waitTicks(10);
		long barks = RoeDeer.BARKS.sum() - barks0;
		AnimalProfile pr = RoeDeer.PROFILE;
		census.line("%s flight distance: a walking visible player made the roe deer flee at %.1f blocks (expected about %.0f, %d-%d), patch"
				+ " flared %s, barks %d", label, startDistance, (pr.fleeMin() + pr.fleeMax()) / 2.0, pr.fleeMin(), pr.fleeMax(), flared, barks);
		if (startDistance < pr.fleeMin() || startDistance > pr.fleeMax() + 2 || !flared || barks < 1) {
			census.fail("%s flight distance %.1f (%d-%d), flared %s, barks %d", label, startDistance, pr.fleeMin(), pr.fleeMax(), flared,
					barks);
		}
		sp.getServer().runOnServer(s -> deer.discard());
		sp.getServer().runCommand("effect give @a minecraft:invisibility infinite 0 true");
	}

	void alarm(TestSingleplayerContext sp, String label, int x, int z) {
		FaunaCensus.teleport(sp, x, z);
		context.waitTicks(100);
		long heard0 = ForestAlarm.HEARD.sum();
		List<RoeDeer> deer = sp.getServer().computeOnServer(s -> {
			ServerLevel level = s.overworld();
			ServerPlayer p = level.players().getFirst();
			List<RoeDeer> out = new ArrayList<>();
			for (int[] off : new int[][] {{8, 0}, {-30, 0}}) {
				Vec3 at = FaunaCensus.trialSpot(level, p.getBlockX() + off[0], p.getBlockZ() + off[1]);
				RoeDeer d = ModEntities.roeDeer().create(level, EntitySpawnReason.COMMAND);
				d.snapTo(at.x, at.y, at.z, 0, 0);
				level.addFreshEntity(d);
				out.add(d);
			}
			return out;
		});
		context.waitTicks(40);
		double[] d0 = sp.getServer().computeOnServer(s -> {
			ServerPlayer p = s.overworld().players().getFirst();
			ForestAlarm.raise(s.overworld(), new ForestAlarm(p.position(), 40, 1.0F, ForestAlarm.Kind.TEST, null));
			return new double[] {deer.get(0).distanceTo(p), deer.get(1).distanceTo(p)};
		});
		boolean[] state = new boolean[4];
		double[] far = {0};
		for (int i = 0; i < 10; i++) {
			context.waitTicks(10);
			sp.getServer().runOnServer(s -> {
				ServerPlayer p = s.overworld().players().getFirst();
				state[0] |= deer.get(0).isFleeing();
				state[1] |= deer.get(1).isFleeing();
				state[2] |= deer.get(1).isAlerted() || deer.get(1).action() == PolishAnimal.Action.ALERT;
				state[3] |= deer.get(1).flared();
				far[0] = Math.max(far[0], deer.get(0).distanceTo(p));
			});
		}
		long heard = ForestAlarm.HEARD.sum() - heard0;
		census.line("%s alarm (radius 40, strength 1): near deer at %.1f fled %s, largest distance %.1f; far deer at %.1f fled %s, alert %s,"
				+ " flared %s; heard %d", label, d0[0], state[0], far[0], d0[1], state[1], state[2], state[3], heard);
		if (!state[0] || far[0] <= 16 || state[1] || !state[2] || !state[3]) {
			census.fail("%s alarm: near fled %s (largest %.1f), far fled %s, alert %s, flared %s", label, state[0], far[0], state[1], state[2],
					state[3]);
		}
		sp.getServer().runOnServer(s -> deer.forEach(Entity::discard));
	}

	// ------------------------------------------------------------------ MSPT

	void mspt(TestSingleplayerContext sp, String label, int x, int z) {
		FaunaCensus.teleport(sp, x, z);
		context.waitTicks(400);
		double before = averageTick(sp);
		List<RoeDeer> deer = new ArrayList<>();
		for (int k = 0; k < 40 / (HERD_GROUPS * 3) + 1 && deer.size() < 40; k++) {
			deer.addAll(herd(sp));
		}
		List<RoeDeer> extra = deer.subList(Math.min(40, deer.size()), deer.size());
		sp.getServer().runOnServer(s -> extra.forEach(Entity::discard));
		int n = Math.min(40, deer.size());
		context.waitTicks(200);
		sp.getServer().runOnServer(s -> PolishAnimal.setTiming(true));
		context.waitTicks(MSPT_TICKS);
		double[] t = sp.getServer().computeOnServer(s -> {
			PolishAnimal.setTiming(false);
			return new double[] {PolishAnimal.TICK_NANOS.sum() / 1e6, PolishAnimal.TICKS.sum()};
		});
		double after = averageTick(sp);
		double perAnimal = t[1] == 0 ? Double.NaN : t[0] / t[1];
		long all = sp.getServer().computeOnServer(s -> (long) s.overworld().getEntities(ModEntities.roeDeer(), Entity::isAlive).size());
		census.line("%s MSPT: %d trial roe deer (%d loaded), %.0f animal ticks in %d ticks: %.4f ms per animal and tick (budget %.2f); server"
				+ " average tick %.2f ms before, %.2f ms after (difference per trial deer %.4f ms)%s", label, n, all, t[1], MSPT_TICKS, perAnimal,
				MSPT_BUDGET, before, after, (after - before) / n, REPORT_ONLY ? "; report only (-PtimingsReportOnly)" : "");
		if (!(perAnimal <= MSPT_BUDGET) && !REPORT_ONLY) {
			census.fail("%s MSPT %.4f ms per roe deer and tick above %.2f", label, perAnimal, MSPT_BUDGET);
		}
		sp.getServer().runOnServer(s -> deer.subList(0, n).forEach(Entity::discard));
	}

	/** The server's average tick time (ms) over 200 ticks. */
	private double averageTick(TestSingleplayerContext sp) {
		double sum = 0;
		for (int i = 0; i < 4; i++) {
			context.waitTicks(50);
			sum += sp.getServer().computeOnServer(s -> s.getAverageTickTimeNanos() / 1e6);
		}
		return sum / 4;
	}
}
