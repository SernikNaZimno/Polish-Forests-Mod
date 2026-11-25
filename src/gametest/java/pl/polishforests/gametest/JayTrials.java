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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import pl.polishforests.command.FaunaSites;
import pl.polishforests.entity.ModEntities;
import pl.polishforests.entity.base.ForestAlarm;
import pl.polishforests.entity.base.PolishAnimal;
import pl.polishforests.entity.jay.EurasianJay;
import pl.polishforests.entity.jay.JayFlight;
import pl.polishforests.entity.jay.JayGeometry;
import pl.polishforests.entity.jay.JayMind;
import pl.polishforests.entity.jay.JaySpace;
import pl.polishforests.entity.squirrel.TreeSpace;
import pl.polishforests.season.SubSeason;
import pl.polishforests.sound.ForestAmbience;
import pl.polishforests.worldgen.habitat.HabitatBiome;

/**
 * The trials of the Eurasian jay in the fauna game test (step FA6, docs/04-pionowy-wycinek.md "Projekt FA6"), numbers only
 * (G.36).
 *
 * <ol>
 * <li><b>Life under the crowns</b> ({@link #life}): in an oak-hornbeam forest found by the search of {@code find biome}
 * ({@link FaunaSites#locate}) near the first place of the world (the place itself when none is found), {@value #LIFE_COUNT}
 * jays 4-24 blocks around the invisible player, {@value #LIFE_TICKS} ticks at noon in mid summer, a sample every
 * {@value #LIFE_SAMPLE} ticks: where they are ({@link #where}: flying, on a branch, on the leaves of a crown, on the
 * ground, else in the air), every flight sample against the local canopy top ({@link JaySpace#localCanopy}, the canopy
 * heightmap {@code MOTION_BLOCKING} within {@value JayGeometry#LOCAL_RADIUS} blocks: under it when the top of the hitbox
 * is below, {@link JayGeometry#underCanopy}) and against its own column ({@link #flightPlace}: under its crown, in a gap
 * between the crowns, over its leaves taking off or landing, over the understory, over a crown in the canopy layer), and how
 * many are stuck in blocks ({@link #stuck}). Pass: at least {@value #MIN_FLIGHT_SAMPLES} flight samples (step FA9: up to
 * {@value #LIFE_EXTENSIONS} more rounds of {@value #LIFE_TICKS} ticks while there are fewer), at least
 * {@value #UNDER_CANOPY_SHARE} of them under the local canopy top, at most {@value #OVER_CROWN_SHARE} of them over a crown
 * in the canopy layer (the local measure alone counts a flight over a lower crown beside taller ones as under the canopy),
 * some perched samples (branch or leaves), some on the ground, none stuck.
 * <li><b>Alarm</b> ({@link #alarm}): the direct chain: a jay on the ground, a roe deer and a wild boar 10 blocks from it,
 * a roe deer and a boar 40 blocks from it on the other sides, the player invisible and still: the jay's screech
 * ({@link JayMind#screech}) must make the near two react (alert or fleeing) and not the far two. The natural chain: three
 * jays 6-14 blocks from a visible player who sprints for {@value #RUN_TICKS} ticks with the ambience on: at least one
 * screech of a jay entity ({@link JayMind#SCREECHES}) whose {@link ForestAlarm.Kind#JAY_BIRD} alarm animals hear, and no
 * screech of the ambience of FA4 while the jays are near ({@link ForestAmbience#JAY_SCREECHES}: no doubling; the deferred
 * ones are reported).
 * <li><b>MSPT</b> ({@link #mspt}): 40 jays around the player in an oak-hornbeam forest (the other animals removed), their
 * server ticks timed: at most {@value FaunaTrials#MSPT_BUDGET} ms per jay and tick (report only with
 * {@code -PtimingsReportOnly}).
 * </ol>
 * The census of the places ({@link FaunaCensus}) counts the jays of the spawners the same way ({@link #where}, {@link #stuck})
 * and needs at least one in every world.
 */
final class JayTrials {
	static final int LIFE_COUNT = 12;
	static final int LIFE_TICKS = 2400;
	static final int LIFE_SAMPLE = 5;
	static final int MIN_FLIGHT_SAMPLES = 50;
	/**
	 * Step FA9: how many more {@value #LIFE_TICKS}-tick rounds the trial samples while it has fewer than
	 * {@value #MIN_FLIGHT_SAMPLES} flight samples (the count of one round varied 30-252 between runs at the same place; the
	 * thresholds of the shares stay the same, only the sample grows).
	 */
	static final int LIFE_EXTENSIONS = 2;
	static final double UNDER_CANOPY_SHARE = 0.9;
	/** Round 1 of the FA6 review: the feet at most this high over the leaves of the column count as taking off or landing. */
	static final double LOW_OVER = 1.5;
	/**
	 * Round 1 of the FA6 review: the top of the hitbox less than this far under the local canopy top counts as in the canopy
	 * layer (blocks); deeper, over the leaves of a lower tree, the jay flies over the understory under the taller crowns.
	 */
	static final double CANOPY_LAYER = 4.0;
	/** Round 1 of the FA6 review: the largest share of the flight samples over a crown in the canopy layer ({@link #flightPlace} {@code over}). */
	static final double OVER_CROWN_SHARE = 0.10;
	static final int RUN_TICKS = 200;
	static final int ALARM_NEAR = 10;
	static final int ALARM_FAR = 40;
	/** Step FA9: the animals of the mod within this many blocks (horizontal) of the player are removed before the direct chain. */
	static final int ISOLATION = 96;
	private static final boolean REPORT_ONLY = Boolean.getBoolean("polishforests.timings.reportOnly");

	private final ClientGameTestContext context;
	private final FaunaCensus census;

	JayTrials(ClientGameTestContext context, FaunaCensus census) {
		this.context = context;
		this.census = census;
	}

	// ------------------------------------------------------------------ where a jay is

	/**
	 * Where a jay is: {@code flying}, {@code branch} (on top of a log), {@code leaves} (on a leaf block), {@code ground}
	 * (on the ground of its column), {@code air} (else: falling, standing on something else above the ground).
	 */
	static String where(ServerLevel level, EurasianJay j) {
		if (j.flight().active()) {
			return "flying";
		}
		BlockState below = level.getBlockState(BlockPos.containing(j.getX(), j.getY() - 0.1, j.getZ()));
		if (j.onGround() && below.is(BlockTags.LEAVES)) {
			return "leaves";
		}
		if (j.onGround() && below.is(BlockTags.LOGS)) {
			return "branch";
		}
		int ground = JaySpace.ground(level, j.getBlockX(), j.getBlockZ());
		return j.getY() <= ground + 0.6 ? "ground" : "air";
	}

	/**
	 * Where a flying jay is against its own column (round 1 of the FA6 review): {@code crown} (the top of the hitbox under the
	 * canopy top of its column: leaves or a log above it), {@code gap} (over the canopy top of its column, a column without
	 * leaves or logs over the ground: a gap between the crowns), {@code low} (over the leaves of its column, the feet at most
	 * {@value #LOW_OVER} blocks over them: taking off from or landing on that crown), {@code understory} (over the leaves of
	 * its column, higher, but the top of the hitbox at least {@value #CANOPY_LAYER} blocks under the local canopy top: over a
	 * shrub or a young tree under the taller crowns), {@code over} (else: over a crown in the canopy layer).
	 */
	static String flightPlace(ServerLevel level, EurasianJay j) {
		int top = JaySpace.canopyTop(level, j.getBlockX(), j.getBlockZ());
		if (JayGeometry.underCanopy(j.getY(), j.getBbHeight(), top)) {
			return "crown";
		}
		if (top <= JaySpace.ground(level, j.getBlockX(), j.getBlockZ())) {
			return "gap";
		}
		if (j.getY() <= top + LOW_OVER) {
			return "low";
		}
		int local = JaySpace.localCanopy(level, j.getX(), j.getZ());
		return j.getY() + j.getBbHeight() <= local - CANOPY_LAYER ? "understory" : "over";
	}

	/** Whether a jay is stuck in blocks: any collision in its hitbox shrunk by 0.02 blocks (the leaves included), or "in a wall". */
	static boolean stuck(ServerLevel level, EurasianJay j) {
		return blocker(level, j) != null;
	}

	/** What a stuck jay is stuck in ({@link #stuck}), or null. */
	static @Nullable String blocker(ServerLevel level, EurasianJay j) {
		AABB box = j.getBoundingBox().deflate(0.02);
		BlockPos at = TreeSpace.collision(level, box, false);
		if (at == null && j.isInWall()) {
			at = BlockPos.containing(j.getEyePosition());
		}
		return at == null ? null : level.getBlockState(at) + " at " + at.toShortString();
	}

	static void resetCounters() {
		JayFlight.resetCounters();
		JayMind.resetCounters();
		JaySpace.PERCHES_UNDER.reset();
		JaySpace.PERCHES_EDGE.reset();
	}

	/** The counters of the jays now, in the order of {@link #counters(long[])}. */
	static long[] mark() {
		return new long[] {JayFlight.FLIGHTS.sum(), JayFlight.FLIGHT_TICKS.sum(), JayFlight.LANDED_BRANCH.sum(), JayFlight.LANDED_LEAVES.sum(),
				JayFlight.LANDED_GROUND.sum(), JayFlight.NO_ROUTE.sum(), JayFlight.STALLED.sum(), JayFlight.TWO_LEGS.sum(), JayMind.SCREECHES.sum(),
				JayMind.SCREECH_HEARD.sum(), JayMind.CACHES.sum(), JayMind.FLIGHTS_FROM_THREAT.sum(), JayMind.GROUND_FEEDS.sum(),
				JayMind.NO_PERCH.sum(), ForestAmbience.JAY_DEFERRED.sum(), ForestAmbience.JAY_VOICE_DEFERRED.sum(), JayFlight.PLAN_HIGH.sum(),
				JayFlight.PLAN_BLOCKED.sum(), JaySpace.PERCHES_UNDER.sum(), JaySpace.PERCHES_EDGE.sum(), JayMind.AMBIENCE_HANDED.sum()};
	}

	/** The counters of the jays since the census of the world began, as one line. */
	static String counters() {
		return counters(new long[21]);
	}

	/** The counters of the jays since the mark, as one line. */
	static String counters(long[] since) {
		long[] n = mark();
		for (int i = 0; i < n.length; i++) {
			n[i] -= since[i];
		}
		return String.format(Locale.ROOT, "flights %d (%d ticks in the air, mean %.1f; of more legs %d), landings on a branch %d, on leaves %d,"
				+ " on the ground %d, no route %d (legs left over the canopy %d, blocked %d), stalled %d; perches found under a crown %d, on the"
				+ " edge of one %d; screeches %d (heard by %d), acorns hidden %d, flights from a threat %d, ground feeding bouts %d, no perch"
				+ " found %d; ambience screeches left to a jay %d (taken over by a jay %d), ambience jay voices left out %d", n[0], n[1],
				n[0] == 0 ? Double.NaN : n[1] / (double) n[0], n[7], n[2], n[3], n[4], n[5], n[16], n[17], n[6], n[18], n[19], n[8], n[9], n[10],
				n[11], n[12], n[13], n[14], n[20], n[15]);
	}

	// ------------------------------------------------------------------ places

	/** Every trial of the jay at one place of a world (the MSPT scene apart). */
	void all(TestSingleplayerContext sp, String label, int x, int z) {
		int[] at = oakForest(sp, x, z);
		SeasonDates.set(sp, SubSeason.MID_SUMMER, 0, label);
		life(sp, label, at[0], at[1]);
		alarm(sp, label, at[0], at[1]);
		// the acorns in mid autumn (counted)
		SeasonDates.set(sp, SubSeason.MID_AUTUMN, 0, label);
		caching(sp, label, at[0], at[1]);
		SeasonDates.set(sp, SubSeason.MID_SUMMER, 0, label);
	}

	/** The nearest oak-hornbeam forest to the place (the search of find biome), or the place itself. */
	private int[] oakForest(TestSingleplayerContext sp, int x, int z) {
		double[] at = sp.getServer().computeOnServer(s -> FaunaSites.locate(s.overworld(), HabitatBiome.OAK_HORNBEAM_FOREST, x, z));
		if (at == null) {
			census.line("jays: no oak-hornbeam forest found near %d %d, trials at the place", x, z);
			return new int[] {x, z};
		}
		return new int[] {(int) Math.floor(at[0]), (int) Math.floor(at[1])};
	}

	/** Places up to n jays 4-24 blocks around the player on valid places of the spawners. */
	static List<EurasianJay> place(TestSingleplayerContext sp, int n, int min, int max) {
		return sp.getServer().computeOnServer(s -> {
			ServerLevel level = s.overworld();
			ServerPlayer p = level.players().getFirst();
			List<EurasianJay> out = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				double angle = Math.PI * 2 * i / n;
				double d = min + (max - min) * (double) ((i * 7) % n) / n;
				Vec3 at = FaunaCensus.trialSpot(level, ModEntities.eurasianJay(), p.getBlockX() + (int) Math.round(Math.cos(angle) * d),
						p.getBlockZ() + (int) Math.round(Math.sin(angle) * d));
				out.add(spawn(level, at));
			}
			return out;
		});
	}

	private static EurasianJay spawn(ServerLevel level, Vec3 at) {
		EurasianJay j = ModEntities.eurasianJay().create(level, EntitySpawnReason.COMMAND);
		j.snapTo(at.x, at.y, at.z, level.getRandom().nextFloat() * 360, 0);
		j.finalizeSpawn(level, level.getCurrentDifficultyAt(j.blockPosition()), EntitySpawnReason.COMMAND, null);
		level.addFreshEntity(j);
		return j;
	}

	// ------------------------------------------------------------------ life under the crowns

	void life(TestSingleplayerContext sp, String label, int x, int z) {
		FaunaCensus.teleport(sp, x, z);
		context.waitTicks(200);
		long[] since = sp.getServer().computeOnServer(s -> mark());
		List<EurasianJay> jays = place(sp, LIFE_COUNT, 4, 24);
		Map<String, Integer> where = new TreeMap<>();
		int[] flight = new int[4];   // samples in flight, under the local canopy top, under the column's own, in the band
		Map<String, Integer> places = new TreeMap<>();   // flight samples against their own column (flightPlace)
		List<String> over = new ArrayList<>();
		List<String> overCrown = new ArrayList<>();
		// flight samples over the leaves of their own column higher than LOW_OVER, by the depth of the top of the hitbox under
		// the local canopy top: <4, 4-8, 8-16, 16+ blocks
		int[] depth = new int[4];
		List<String> stuck = new ArrayList<>();
		int ticks = LIFE_TICKS;
		for (int t = 0; t < ticks; t += LIFE_SAMPLE) {
			if (t + LIFE_SAMPLE >= ticks && flight[0] < MIN_FLIGHT_SAMPLES && ticks < LIFE_TICKS * (1 + LIFE_EXTENSIONS)) {
				ticks += LIFE_TICKS;   // step FA9: too few flight samples yet, one more round
			}
			context.waitTicks(LIFE_SAMPLE);
			sp.getServer().runOnServer(s -> {
				ServerLevel level = s.overworld();
				for (EurasianJay j : jays) {
					if (!j.isAlive()) {
						continue;
					}
					String w = where(level, j);
					where.merge(w, 1, Integer::sum);
					if (w.equals("flying")) {
						flight[0]++;
						float h = j.getBbHeight();
						int local = JaySpace.localCanopy(level, j.getX(), j.getZ());
						if (JayGeometry.underCanopy(j.getY(), h, local)) {
							flight[1]++;
						} else if (over.size() < 5) {
							over.add(String.format(Locale.ROOT, "%.1f %.1f %.1f (top %.1f, local canopy %d, ground %d)", j.getX(), j.getY(), j.getZ(),
									j.getY() + h, local, JaySpace.ground(level, j.getBlockX(), j.getBlockZ())));
						}
						if (JayGeometry.underCanopy(j.getY(), h, JaySpace.canopyTop(level, j.getBlockX(), j.getBlockZ()))) {
							flight[2]++;
						}
						if (JaySpace.inBand(level, j.position(), h)) {
							flight[3]++;
						}
						String place = flightPlace(level, j);
						places.merge(place, 1, Integer::sum);
						if (place.equals("over") || place.equals("understory")) {
							double d = local - (j.getY() + h);
							depth[d < 4 ? 0 : d < 8 ? 1 : d < 16 ? 2 : 3]++;
						}
						if (place.equals("over") && overCrown.size() < 5) {
							overCrown.add(String.format(Locale.ROOT, "%.1f %.1f %.1f (column top %d, local canopy %d, ground %d)", j.getX(), j.getY(),
									j.getZ(), JaySpace.canopyTop(level, j.getBlockX(), j.getBlockZ()), local,
									JaySpace.ground(level, j.getBlockX(), j.getBlockZ())));
						}
					}
					String in = blocker(level, j);
					if (in != null) {
						stuck.add(String.format(Locale.ROOT, "at %.2f %.2f %.2f (%s) in %s", j.getX(), j.getY(), j.getZ(), w, in));
					}
				}
			});
		}
		int perched = where.getOrDefault("branch", 0) + where.getOrDefault("leaves", 0);
		double under = flight[0] == 0 ? Double.NaN : flight[1] / (double) flight[0];
		int n = Math.max(1, flight[0]);
		double overShare = places.getOrDefault("over", 0) / (double) n;
		int overLeaves = places.getOrDefault("low", 0) + places.getOrDefault("understory", 0) + places.getOrDefault("over", 0);
		long alive = sp.getServer().computeOnServer(s -> jays.stream().filter(Entity::isAlive).count());
		census.line("%s jays in an oak-hornbeam forest at %d %d: %d placed (%d alive after %d ticks), samples every %d ticks %s; flight samples"
				+ " %d: under the local canopy top %.1f%% (threshold %.0f%%), under the column's own canopy top %.1f%%, in the cruise band"
				+ " %.1f%%; against the own column %s: under its crown %.1f%%, in a gap between the crowns %.1f%%, over its leaves %.1f%% (of"
				+ " them taking off or landing, at most %.1f blocks over them, %.1f%%; higher, over the understory %.0f+ blocks under the local"
				+ " canopy top, %.1f%%; over a crown in the canopy layer %.1f%%, threshold %.0f%%; these two by the depth of the top of the"
				+ " hitbox under the local canopy top <4 / 4-8 / 8-16 / 16+ blocks %s); perched"
				+ " %d, stuck %d; %s%s%s", label, x, z, LIFE_COUNT, alive, ticks, LIFE_SAMPLE, where, flight[0], 100 * under,
				100 * UNDER_CANOPY_SHARE, 100.0 * flight[2] / n, 100.0 * flight[3] / n, places, 100.0 * places.getOrDefault("crown", 0) / n,
				100.0 * places.getOrDefault("gap", 0) / n, 100.0 * overLeaves / n, LOW_OVER, 100.0 * places.getOrDefault("low", 0) / n,
				CANOPY_LAYER, 100.0 * places.getOrDefault("understory", 0) / n, 100 * overShare, 100 * OVER_CROWN_SHARE,
				java.util.Arrays.toString(depth), perched, stuck.size(), counters(since),
				over.isEmpty() ? "" : "; above the canopy e.g. " + String.join("; ", over),
				overCrown.isEmpty() ? "" : "; over a crown e.g. " + String.join("; ", overCrown));
		if (flight[0] < MIN_FLIGHT_SAMPLES) {
			census.fail("%s: only %d flight samples of jays (at least %d)", label, flight[0], MIN_FLIGHT_SAMPLES);
		} else if (!(under >= UNDER_CANOPY_SHARE)) {
			census.fail("%s: %.1f%% of the jays' flight samples under the local canopy top (at least %.0f%%)", label, 100 * under,
					100 * UNDER_CANOPY_SHARE);
		}
		if (flight[0] >= MIN_FLIGHT_SAMPLES && overShare > OVER_CROWN_SHARE) {
			census.fail("%s: %.1f%% of the jays' flight samples over a crown in the canopy layer (over the leaves of their own column, top of"
					+ " the hitbox less than %.0f blocks under the local canopy top; at most %.0f%%)", label, 100 * overShare, CANOPY_LAYER,
					100 * OVER_CROWN_SHARE);
		}
		if (perched == 0) {
			census.fail("%s: no jay on a branch or the leaves of a crown in the samples", label);
		}
		if (where.getOrDefault("ground", 0) == 0) {
			census.fail("%s: no jay on the ground in the samples", label);
		}
		if (!stuck.isEmpty()) {
			census.fail("%s: jays stuck in blocks %d times: %s", label, stuck.size(), String.join("; ", stuck.subList(0, Math.min(5, stuck.size()))));
		}
		sp.getServer().runOnServer(s -> jays.forEach(Entity::discard));
	}

	// ------------------------------------------------------------------ the acorns

	/** Mid autumn in the oak-hornbeam forest: the jays hide acorns (counted; a jay carrying one seen at least once). */
	void caching(TestSingleplayerContext sp, String label, int x, int z) {
		FaunaCensus.teleport(sp, x, z);
		context.waitTicks(100);
		long[] since = sp.getServer().computeOnServer(s -> mark());
		List<EurasianJay> jays = place(sp, LIFE_COUNT, 4, 24);
		int[] carrying = {0, 0};
		for (int t = 0; t < LIFE_TICKS; t += 20) {
			context.waitTicks(20);
			sp.getServer().runOnServer(s -> {
				for (EurasianJay j : jays) {
					if (j.isAlive()) {
						carrying[0]++;
						if (j.isCarrying()) {
							carrying[1]++;
						}
					}
				}
			});
		}
		long hidden = sp.getServer().computeOnServer(s -> JayMind.CACHES.sum()) - since[10];
		census.line("%s jays in mid autumn: %d samples, carrying an acorn in %d; acorns hidden %d; %s", label, carrying[0], carrying[1], hidden,
				counters(since));
		if (hidden < 1 || carrying[1] < 1) {
			census.fail("%s: no acorn hidden by %d jays in %d ticks of mid autumn in an oak-hornbeam forest (carrying %d)", label, LIFE_COUNT,
					LIFE_TICKS, carrying[1]);
		}
		sp.getServer().runOnServer(s -> jays.forEach(Entity::discard));
	}

	// ------------------------------------------------------------------ the alarm

	void alarm(TestSingleplayerContext sp, String label, int x, int z) {
		FaunaCensus.teleport(sp, x, z);
		SeasonDates.setTimeOfDay(sp, 6000, label);
		context.waitTicks(100);
		// The direct chain: the screech of one jay, the player invisible and still.
		long heard0 = ForestAlarm.HEARD_BY_KIND[ForestAlarm.Kind.JAY_BIRD.ordinal()].sum();
		List<PolishAnimal<?>> animals = new ArrayList<>();
		sp.getServer().runOnServer(s -> {
			ServerLevel level = s.overworld();
			ServerPlayer p = level.players().getFirst();
			// Step FA9: the animals of the mod around are removed first, so that only the screech can alarm the trial animals
			// (in one run of 15 the far roe deer reacted although the screech is heard only 32 blocks away, most likely to the
			// bark of an animal of the spawners that heard the screech and fled).
			List<Entity> around = level.getEntities((Entity) null, p.getBoundingBox().inflate(ISOLATION, 64, ISOLATION),
					e -> e instanceof PolishAnimal<?>);
			around.forEach(Entity::discard);
			EurasianJay j = spawn(level, FaunaCensus.trialSpot(level, ModEntities.eurasianJay(), p.getBlockX() + 4, p.getBlockZ() + 4));
			animals.add(j);
			int jx = j.getBlockX();
			int jz = j.getBlockZ();
			animals.add(animal(level, ModEntities.roeDeer(), jx + ALARM_NEAR, jz));
			animals.add(animal(level, ModEntities.wildBoar(), jx - ALARM_NEAR, jz));
			animals.add(animal(level, ModEntities.roeDeer(), jx, jz + ALARM_FAR));
			animals.add(animal(level, ModEntities.wildBoar(), jx, jz - ALARM_FAR));
		});
		context.waitTicks(40);
		int heard = sp.getServer().computeOnServer(s -> ((EurasianJay) animals.getFirst()).mind().screech(s.overworld(), s.overworld().getGameTime()));
		boolean[] reacted = new boolean[animals.size()];
		for (int i = 0; i < 8; i++) {
			context.waitTicks(5);
			sp.getServer().runOnServer(s -> {
				for (int k = 1; k < animals.size(); k++) {
					PolishAnimal<?> a = animals.get(k);
					reacted[k] |= a.isAlive() && (a.isAlerted() || a.isFleeing() || a.action() == PolishAnimal.Action.ALERT);
				}
			});
		}
		long byKind = ForestAlarm.HEARD_BY_KIND[ForestAlarm.Kind.JAY_BIRD.ordinal()].sum() - heard0;
		census.line("%s jay screech (direct): heard by %d (JAY_BIRD alarms heard %d); roe deer %d blocks %s, boar %d blocks %s, roe deer %d"
				+ " blocks %s, boar %d blocks %s", label, heard, byKind, ALARM_NEAR, reacted[1], ALARM_NEAR, reacted[2], ALARM_FAR, reacted[3],
				ALARM_FAR, reacted[4]);
		if (!reacted[1] || !reacted[2] || reacted[3] || reacted[4] || byKind < 2) {
			census.fail("%s jay screech: near deer %s, near boar %s (both must react), far deer %s, far boar %s (neither), heard %d", label,
					reacted[1], reacted[2], reacted[3], reacted[4], byKind);
		}
		sp.getServer().runOnServer(s -> animals.forEach(Entity::discard));

		// The natural chain: a visible player sprinting near three jays, the ambience on.
		long[] since = sp.getServer().computeOnServer(s -> mark());
		long ambient0 = ForestAmbience.JAY_SCREECHES.sum();
		long heardBird0 = ForestAlarm.HEARD_BY_KIND[ForestAlarm.Kind.JAY_BIRD.ordinal()].sum();
		List<EurasianJay> jays = place(sp, 3, 6, 14);
		List<PolishAnimal<?>> deer = sp.getServer().computeOnServer(s -> {
			ServerLevel level = s.overworld();
			ServerPlayer p = level.players().getFirst();
			List<PolishAnimal<?>> out = new ArrayList<>();
			out.add(animal(level, ModEntities.roeDeer(), p.getBlockX() + 12, p.getBlockZ() - 6));
			out.add(animal(level, ModEntities.wildBoar(), p.getBlockX() - 12, p.getBlockZ() - 6));
			return out;
		});
		context.waitTicks(20);
		sp.getServer().runCommand("gamerule polishforests:forest_ambience true");
		sp.getServer().runCommand("effect clear @a minecraft:invisibility");
		sp.getServer().runCommand("gamemode survival @a");
		for (int t = 0; t < RUN_TICKS; t += 5) {
			sp.getServer().runOnServer(s -> s.overworld().players().getFirst().setSprinting(true));
			context.waitTicks(5);
		}
		sp.getServer().runOnServer(s -> s.overworld().players().getFirst().setSprinting(false));
		sp.getServer().runCommand("gamemode creative @a");
		sp.getServer().runCommand("effect give @a minecraft:invisibility infinite 0 true");
		long[] now = sp.getServer().computeOnServer(s -> mark());
		long screeches = now[8] - since[8];
		long ambient = sp.getServer().computeOnServer(s -> ForestAmbience.JAY_SCREECHES.sum()) - ambient0;
		long heardBird = sp.getServer().computeOnServer(s -> ForestAlarm.HEARD_BY_KIND[ForestAlarm.Kind.JAY_BIRD.ordinal()].sum()) - heardBird0;
		census.line("%s jay chain (sprinting %d ticks near 3 jays): screeches of the jays %d, JAY_BIRD alarms heard %d, screeches of the"
				+ " ambience %d (left to a jay %d); %s", label, RUN_TICKS, screeches, heardBird, ambient, now[14] - since[14], counters(since));
		if (screeches < 1 || heardBird < 1) {
			census.fail("%s jay chain: %d screeches of the jays heard by %d animals in %d ticks of sprinting (at least 1 each)", label, screeches,
					heardBird, RUN_TICKS);
		}
		if (ambient > 0) {
			census.fail("%s jay chain: %d screeches of the ambience with jays near (doubled)", label, ambient);
		}
		sp.getServer().runOnServer(s -> {
			jays.forEach(Entity::discard);
			deer.forEach(Entity::discard);
		});
		FaunaCensus.teleport(sp, x, z);
	}

	private static <T extends PolishAnimal<?>> PolishAnimal<?> animal(ServerLevel level, EntityType<T> type, int x, int z) {
		Vec3 at = FaunaCensus.trialSpot(level, type, x, z);
		T a = type.create(level, EntitySpawnReason.COMMAND);
		a.snapTo(at.x, at.y, at.z, 0, 0);
		level.addFreshEntity(a);
		return a;
	}

	// ------------------------------------------------------------------ MSPT

	void mspt(TestSingleplayerContext sp, String label, int x, int z) {
		int[] at = oakForest(sp, x, z);
		FaunaCensus.teleport(sp, at[0], at[1]);
		context.waitTicks(200);
		// Only jays tick in the timing: the other animals of the mod around are removed.
		sp.getServer().runOnServer(s -> {
			List<Entity> other = new ArrayList<>();
			s.overworld().getAllEntities().forEach(e -> {
				if (e instanceof PolishAnimal<?> && !(e instanceof EurasianJay)) {
					other.add(e);
				}
			});
			other.forEach(Entity::discard);
		});
		List<EurasianJay> jays = place(sp, 40, 4, 24);
		// Let them fly first: the timing covers jays in the air, perched and on the ground.
		context.waitTicks(400);
		String spread = sp.getServer().computeOnServer(s -> {
			Map<String, Integer> where = new TreeMap<>();
			jays.stream().filter(Entity::isAlive).forEach(j -> where.merge(where(s.overworld(), j), 1, Integer::sum));
			return where.toString();
		});
		sp.getServer().runOnServer(s -> PolishAnimal.setTiming(true));
		context.waitTicks(FaunaTrials.MSPT_TICKS);
		double[] t = sp.getServer().computeOnServer(s -> {
			PolishAnimal.setTiming(false);
			return new double[] {PolishAnimal.TICK_NANOS.sum() / 1e6, PolishAnimal.TICKS.sum()};
		});
		double perAnimal = t[1] == 0 ? Double.NaN : t[0] / t[1];
		long all = sp.getServer().computeOnServer(s -> (long) s.overworld().getEntities(ModEntities.eurasianJay(), Entity::isAlive).size());
		census.line("%s MSPT Eurasian jay: 40 trial jays (%d loaded; at the start %s), %.0f animal ticks in %d ticks: %.4f ms per animal and"
				+ " tick (budget %.2f)%s", label, all, spread, t[1], FaunaTrials.MSPT_TICKS, perAnimal, FaunaTrials.MSPT_BUDGET,
				REPORT_ONLY ? "; report only (-PtimingsReportOnly)" : "");
		if (!(perAnimal <= FaunaTrials.MSPT_BUDGET) && !REPORT_ONLY) {
			census.fail("%s MSPT %.4f ms per Eurasian jay and tick above %.2f", label, perAnimal, FaunaTrials.MSPT_BUDGET);
		}
		sp.getServer().runOnServer(s -> jays.forEach(Entity::discard));
	}
}
