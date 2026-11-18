package pl.polishforests.gametest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import pl.polishforests.entity.ModEntities;
import pl.polishforests.entity.base.PolishAnimal;

import pl.polishforests.entity.wildboar.BoarRooting;
import pl.polishforests.entity.wildboar.WarnCharge;
import pl.polishforests.entity.wildboar.WildBoar;
import pl.polishforests.season.SubSeason;

/**
 * The behaviour trials of the wild boar in the fauna game test (step FA3, docs/04-pionowy-wycinek.md "Projekt FA3"),
 * numbers only (G.36).
 *
 * <ol>
 * <li><b>Sounders and activity</b> ({@link #sounders}, sampled with the roe deer of the activity trial of
 * {@link FaunaTrials}): {@value #SOUNDERS} sounders (a leading sow, two sows, two young) and {@value #LONE} lone males
 * 16-40 blocks around the player; at dawn, noon, dusk and midnight the share of active boars (feeding, rooting, moving)
 * must be at least {@value #ACTIVITY_MARGIN} higher at night than at noon, and over all samples at least
 * {@value #COHESION} of the members of the sounders must be within {@value #COHESION_RADIUS} blocks of their leader.
 * <li><b>Rooting</b> ({@link #rooting}): {@value #ROOTERS} boars at night at the grassiest rooting place near the census
 * place, {@value #ROOTING_TICKS} ticks each with the game rule {@code polishforests:boar_rooting} off and with vanilla's
 * {@code mob_griefing} off, then with both on until they rooted (at most twice as long): no patch anywhere and no new
 * coarse or rooted dirt within {@value #SCAN} blocks in the first two; in the last at least one patch of the trial boars,
 * its blocks coarse or rooted dirt, no half of a double plant left over it.
 * <li><b>Warning</b> ({@link #warning}): in late spring a sow with two young at her feet, 14 blocks from a visible survival
 * player who walks up to her (where she is then) to {@value #APPROACH_TO} blocks, stopping when she warns; the sow must warn
 * (a charge), come no closer than 1 block, and the player loses no health.
 * <li><b>MSPT</b> ({@link #mspt}): 40 boars (the roe deer removed), the server ticks of the animals timed: at most
 * {@value FaunaTrials#MSPT_BUDGET} ms per boar and tick (report only with {@code -PtimingsReportOnly}).
 * </ol>
 */
final class BoarTrials {
	static final int SOUNDERS = 3;
	static final int LONE = 2;
	static final double ACTIVITY_MARGIN = 0.2;
	static final double COHESION = 0.8;
	static final int COHESION_RADIUS = 16;
	static final int ROOTERS = 6;
	static final int ROOTING_TICKS = 1200;
	static final int SCAN = 32;
	/** The search radius and the counting radius (blocks) of the place of the rooting trial. */
	static final int SPOT_SEARCH = 48;
	static final int SPOT_RADIUS = 4;
	/** How close (blocks) the player of the warning trial walks up to the sow, and his step (blocks per 10 ticks). */
	static final double APPROACH_TO = 5;
	static final double STEP = 1.5;
	private static final boolean REPORT_ONLY = Boolean.getBoolean("polishforests.timings.reportOnly");

	private final ClientGameTestContext context;
	private final FaunaCensus census;
	/** Samples of the cohesion: members within the radius of their leader, and members. */
	private int membersNear;
	private int members;

	BoarTrials(ClientGameTestContext context, FaunaCensus census) {
		this.context = context;
		this.census = census;
	}

	// ------------------------------------------------------------------ sounders, activity and cohesion

	/** Places the sounders and the lone males around the player (the activity trial samples them with the roe deer). */
	List<WildBoar> sounders(TestSingleplayerContext sp) {
		membersNear = 0;
		members = 0;
		return sp.getServer().computeOnServer(s -> {
			ServerLevel level = s.overworld();
			ServerPlayer p = level.players().getFirst();
			List<WildBoar> out = new ArrayList<>();
			for (int g = 0; g < SOUNDERS + LONE; g++) {
				double angle = Math.PI * 2 * (g + 0.5) / (SOUNDERS + LONE);
				double d = 18 + 20 * ((g * 3) % (SOUNDERS + LONE)) / (double) (SOUNDERS + LONE);
				int cx = (int) Math.floor(p.getX() + Math.cos(angle) * d);
				int cz = (int) Math.floor(p.getZ() + Math.sin(angle) * d);
				if (g >= SOUNDERS) {
					WildBoar male = create(level, cx, cz);
					male.becomeSolitary();
					level.addFreshEntity(male);
					out.add(male);
					continue;
				}
				WildBoar leader = create(level, cx, cz);
				leader.becomeHerdLeader();
				leader.becomeMother();
				level.addFreshEntity(leader);
				out.add(leader);
				for (int i = 1; i < 5; i++) {
					WildBoar b = create(level, cx + i, cz + i % 2);
					b.followLeader(leader);
					if (i <= 2) {
						b.becomeMember();
					} else {
						b.setBaby(true);
						b.setMother(leader);
					}
					level.addFreshEntity(b);
					out.add(b);
				}
			}
			return out;
		});
	}

	private static WildBoar create(ServerLevel level, int x, int z) {
		Vec3 at = FaunaCensus.trialSpot(level, ModEntities.wildBoar(), x, z);
		WildBoar b = ModEntities.wildBoar().create(level, EntitySpawnReason.COMMAND);
		b.snapTo(at.x, at.y, at.z, level.getRandom().nextFloat() * 360, 0);
		b.finalizeSpawn(level, level.getCurrentDifficultyAt(b.blockPosition()), EntitySpawnReason.COMMAND, null);
		return b;
	}

	/** The share of the live boars active (feeding, rooting, moving or fleeing), and counts the cohesion of the sounders. */
	double sample(ServerLevel level, List<WildBoar> boars) {
		int n = 0;
		int active = 0;
		for (WildBoar b : boars) {
			if (!b.isAlive()) {
				continue;
			}
			n++;
			active += switch (b.state()) {
				case GRAZING, ROOTING, MOVING, FLEEING -> 1;
				default -> 0;
			};
			PolishAnimal<?> leader = b.leader();
			if (!b.isHerdLeader()) {
				members++;
				if (leader != null && b.distanceTo(leader) <= COHESION_RADIUS) {
					membersNear++;
				}
			}
		}
		return active / (double) Math.max(1, n);
	}

	/** The verdict of the activity (active shares at dawn, noon, dusk, night) and the cohesion. */
	void verdict(String label, double[] active, List<WildBoar> boars, TestSingleplayerContext sp) {
		String groups = sp.getServer().computeOnServer(s -> groups(boars));
		double cohesion = membersNear / (double) Math.max(1, members);
		boolean activityOk = active[3] >= active[1] + ACTIVITY_MARGIN && active[2] >= active[1];
		census.line("%s wild boar activity (active share): dawn %.2f, noon %.2f, dusk %.2f, night %.2f; %s; sounders %s; members within %d"
				+ " blocks of their leader %.2f (%d of %d samples, at least %.2f)", label, active[0], active[1], active[2], active[3],
				activityOk ? "nocturnal" : "NOT nocturnal", groups, COHESION_RADIUS, cohesion, membersNear, members, COHESION);
		if (!activityOk) {
			census.fail("%s wild boar activity: night %.2f not %.2f above noon %.2f, or dusk %.2f below noon", label, active[3], ACTIVITY_MARGIN,
					active[1], active[2]);
		}
		if (cohesion < COHESION) {
			census.fail("%s wild boar sounders: %.2f of the members within %d blocks of their leader (at least %.2f)", label, cohesion,
					COHESION_RADIUS, COHESION);
		}
	}

	/** The groups of the trial boars by leader: sizes, and whether a lone male leads itself. */
	private static String groups(List<WildBoar> boars) {
		Map<UUID, Integer> sizes = new HashMap<>();
		int loneMales = 0;
		for (WildBoar b : boars) {
			if (b.isAlive()) {
				sizes.merge(b.herdId(), 1, Integer::sum);
				if (b.isMale() && !b.isBaby() && b.isHerdLeader() && b.herdSize() == 1) {
					loneMales++;
				}
			}
		}
		return sizes.values().stream().sorted().toList() + ", lone males " + loneMales;
	}

	// ------------------------------------------------------------------ rooting

	void rooting(TestSingleplayerContext sp, String label, int x, int z) {
		FaunaCensus.teleport(sp, x, z);
		context.waitTicks(200);
		// The boars root grass blocks only: the trial takes the grassiest rooting place near the census place (the forest
		// floor of the mod is mostly litter and coarse dirt, and a place with little grass gave no patch, FA3 stage 2), and
		// the player stands 24 blocks off it (an invisible player still makes a boar within about 9 blocks stand and watch).
		int[] spot = sp.getServer().computeOnServer(s -> grassiestSpot(s.overworld(), x, z));
		int sx = spot[0];
		int sz = spot[1];
		FaunaCensus.teleport(sp, sx + 24, sz);
		context.waitTicks(100);
		census.line("%s wild boar rooting place: %d %d, %d of %d columns around it with grass a boar can root", label, sx, sz, spot[2],
				(2 * SPOT_RADIUS + 1) * (2 * SPOT_RADIUS + 1));
		String[][] phases = {{"boar_rooting off", "gamerule polishforests:boar_rooting false", "gamerule mob_griefing true"},
				{"mob_griefing off", "gamerule polishforests:boar_rooting true", "gamerule mob_griefing false"},
				{"both on", "gamerule polishforests:boar_rooting true", "gamerule mob_griefing true"}};
		for (String[] phase : phases) {
			boolean on = phase[0].equals("both on");
			sp.getServer().runCommand(phase[1]);
			sp.getServer().runCommand(phase[2]);
			SeasonDates.setTimeOfDay(sp, 18_000, label);
			int dirtBefore = sp.getServer().computeOnServer(s -> dirt(s.overworld(), sx, sz));
			long patches0 = BoarRooting.PATCHES.sum();
			long blocks0 = BoarRooting.BLOCKS.sum();
			long refused0 = BoarRooting.REFUSED.sum();
			long bare0 = BoarRooting.BARE.sum();
			List<WildBoar> boars = sp.getServer().computeOnServer(s -> {
				ServerLevel level = s.overworld();
				List<WildBoar> out = new ArrayList<>();
				for (int i = 0; i < ROOTERS; i++) {
					double angle = Math.PI * 2 * i / ROOTERS;
					WildBoar b = create(level, sx + (int) Math.round(Math.cos(angle) * 4), sz + (int) Math.round(Math.sin(angle) * 4));
					b.setMale(true);
					level.addFreshEntity(b);
					out.add(b);
				}
				return out;
			});
			// Off: the full phase (nothing may change). On: until the trial boars rooted a patch, at most twice as long.
			int limit = on ? 2 * ROOTING_TICKS : ROOTING_TICKS;
			int waited = 0;
			while (waited < limit) {
				context.waitTicks(100);
				waited += 100;
				if (on && sp.getServer().computeOnServer(s -> boars.stream().mapToInt(WildBoar::rootedPatches).sum()) > 0) {
					break;
				}
			}
			int dirtAfter = sp.getServer().computeOnServer(s -> dirt(s.overworld(), sx, sz));
			long patches = BoarRooting.PATCHES.sum() - patches0;
			long blocks = BoarRooting.BLOCKS.sum() - blocks0;
			long refused = BoarRooting.REFUSED.sum() - refused0;
			long bare = BoarRooting.BARE.sum() - bare0;
			// The patches of the trial boars: their blocks must now be coarse or rooted dirt with no half of a double plant above.
			int[] own = sp.getServer().computeOnServer(s -> ownPatches(s.overworld(), boars));
			census.line("%s wild boar rooting, %s: %d boars for %d ticks at night: patches %d (%d blocks; of the trial boars %d, %d blocks"
					+ " now dirt, %d not, %d half plants), refused %d, no grass %d; coarse and rooted dirt within %d blocks %d before, %d after",
					label, phase[0], ROOTERS, waited, patches, blocks, own[0], own[1], own[2], own[3], refused, bare, SCAN, dirtBefore, dirtAfter);
			if (on && (own[0] < 1 || own[2] > 0 || own[3] > 0)) {
				census.fail("%s wild boar rooting with both rules on: %d patches of the trial boars in %d ticks (at least 1), %d blocks not"
						+ " dirt, %d half plants", label, own[0], waited, own[2], own[3]);
			}
			if (!on && (patches > 0 || blocks > 0 || dirtAfter > dirtBefore)) {
				census.fail("%s wild boar rooting with %s: %d patches, dirt %d -> %d (expected none)", label, phase[0], patches, dirtBefore,
						dirtAfter);
			}
			sp.getServer().runOnServer(s -> boars.forEach(Entity::discard));
		}
		sp.getServer().runCommand("gamerule mob_griefing true");
		sp.getServer().runCommand("gamerule polishforests:boar_rooting true");
	}

	/**
	 * The place of the rooting trial: of the columns within {@value #SPOT_SEARCH} blocks (every 4th) where a boar roots, the
	 * one with the most columns within {@value #SPOT_RADIUS} blocks that have grass a boar can root; {x, z, count}.
	 */
	private static int[] grassiestSpot(ServerLevel level, int x, int z) {
		int[] best = {x, z, -1};
		for (int dx = -SPOT_SEARCH; dx <= SPOT_SEARCH; dx += 4) {
			for (int dz = -SPOT_SEARCH; dz <= SPOT_SEARCH; dz += 4) {
				int cx = x + dx;
				int cz = z + dz;
				level.getChunk(cx >> 4, cz >> 4);
				BlockPos feet = new BlockPos(cx, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz), cz);
				if (!BoarRooting.rootsAt(level, feet)) {
					continue;
				}
				int n = 0;
				for (int ox = -SPOT_RADIUS; ox <= SPOT_RADIUS; ox++) {
					for (int oz = -SPOT_RADIUS; oz <= SPOT_RADIUS; oz++) {
						int px = cx + ox;
						int pz = cz + oz;
						level.getChunk(px >> 4, pz >> 4);
						int py = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, px, pz);
						if (BoarRooting.grassAt(level, new BlockPos(px, py, pz))) {
							n++;
						}
					}
				}
				if (n > best[2]) {
					best = new int[] {cx, cz, n};
				}
			}
		}
		return best;
	}

	/**
	 * The patches of the trial boars: {patches, blocks of their last patches that are coarse or rooted dirt, blocks that are
	 * not, halves of a double plant left without their other half above them}.
	 */
	private static int[] ownPatches(ServerLevel level, List<WildBoar> boars) {
		int[] out = new int[4];
		for (WildBoar b : boars) {
			out[0] += b.rootedPatches();
			for (BlockPos ground : b.lastPatch()) {
				BlockState st = level.getBlockState(ground);
				if (st.is(Blocks.COARSE_DIRT) || st.is(Blocks.ROOTED_DIRT)) {
					out[1]++;
				} else {
					out[2]++;
				}
				for (int dy = 1; dy <= 2; dy++) {
					BlockPos at = ground.above(dy);
					BlockState plant = level.getBlockState(at);
					if (plant.getBlock() instanceof DoublePlantBlock && plant.hasProperty(DoublePlantBlock.HALF)) {
						BlockPos other = plant.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER ? at.below() : at.above();
						if (!level.getBlockState(other).is(plant.getBlock())) {
							out[3]++;
						}
					}
				}
			}
		}
		return out;
	}

	/** Coarse and rooted dirt at the top of the columns within {@value #SCAN} blocks. */
	private static int dirt(ServerLevel level, int x, int z) {
		int n = 0;
		for (int dx = -SCAN; dx <= SCAN; dx++) {
			for (int dz = -SCAN; dz <= SCAN; dz++) {
				int cx = x + dx;
				int cz = z + dz;
				if (!level.hasChunk(cx >> 4, cz >> 4)) {
					continue;
				}
				int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
				for (int dy = -2; dy <= 0; dy++) {
					BlockState st = level.getBlockState(new BlockPos(cx, y + dy, cz));
					if (st.is(Blocks.COARSE_DIRT) || st.is(Blocks.ROOTED_DIRT)) {
						n++;
					}
				}
			}
		}
		return n;
	}

	// ------------------------------------------------------------------ warning of a sow with young

	void warning(TestSingleplayerContext sp, String label, int x, int z) {
		FaunaCensus.teleport(sp, x, z);
		context.waitTicks(200);
		SeasonDates.set(sp, SubSeason.LATE_SPRING, 0, label);
		SeasonDates.setTimeOfDay(sp, 6_000, label);
		int[] side = sp.getServer().computeOnServer(s -> FaunaCensus.openSide(s.overworld(), x, z));
		long warnings0 = WarnCharge.WARNINGS.sum();
		WildBoar sow = sp.getServer().computeOnServer(s -> {
			ServerLevel level = s.overworld();
			WildBoar mother = create(level, x + side[0] * 14, z + side[1] * 14);
			mother.becomeHerdLeader();
			mother.becomeMother();
			level.addFreshEntity(mother);
			// The young at her feet (a place of their own could be up to 16 blocks off hers, beyond what keeps her holding).
			for (int i = 0; i < 2; i++) {
				WildBoar young = create(level, x + side[0] * 14, z + side[1] * 14);
				young.snapTo(mother.getX() + (i == 0 ? 0.7 : -0.7), mother.getY(), mother.getZ() + 0.4, young.getYRot(), 0);
				young.setBaby(true);
				young.setMother(mother);
				young.followLeader(mother);
				level.addFreshEntity(young);
			}
			return mother;
		});
		// Her calendar and her young: the sow holds her ground once she knows the sub-season (every 100 ticks) and sees her
		// young near (every 20 ticks); waited for, at most 400 ticks.
		boolean holds = false;
		for (int t = 0; t < 400 && !holds; t += 20) {
			context.waitTicks(20);
			holds = sp.getServer().computeOnServer(s -> sow.holdsGround());
		}
		String state = sp.getServer().computeOnServer(s -> sow.warnState());
		// Step FA4: without fall damage, so the health shows only harm from the sow (the teleport steps follow the top of
		// MOTION_BLOCKING_NO_LEAVES, which may be a branch: the FA4 census saw 20.0 -> 15.9 from a drop of about 7 blocks).
		for (String c : new String[] {"gamemode survival @a", "effect clear @a minecraft:invisibility", "gamerule fall_damage false"}) {
			sp.getServer().runCommand(c);
		}
		float health0 = sp.getServer().computeOnServer(s -> s.overworld().players().getFirst().getHealth());
		double minDist = Double.MAX_VALUE;
		double startDist = sp.getServer().computeOnServer(s -> (double) sow.distanceTo(s.overworld().players().getFirst()));
		// The player walks up to the sow where she is now (she may have gone to her bed meanwhile), 1.5 blocks every 10 ticks,
		// until 5 blocks from her or until she warns; then he stands (one who walked on into the charge could come closer
		// than the charge stops).
		for (int step = 0; step < 40; step++) {
			boolean warned = WarnCharge.WARNINGS.sum() > warnings0;
			double[] move = sp.getServer().computeOnServer(s -> {
				ServerPlayer p = s.overworld().players().getFirst();
				Vec3 to = new Vec3(sow.getX() - p.getX(), 0, sow.getZ() - p.getZ());
				double d = to.length();
				if (d <= APPROACH_TO + STEP || !sow.isAlive()) {
					return null;
				}
				Vec3 at = p.position().add(to.scale(STEP / d));
				int bx = (int) Math.floor(at.x);
				int bz = (int) Math.floor(at.z);
				return new double[] {at.x, s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz), at.z};
			});
			if (move != null && !warned) {
				sp.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @a %.2f %.0f %.2f", move[0], move[1], move[2]));
			}
			for (int t = 0; t < 10; t += 2) {
				context.waitTicks(2);
				double d = sp.getServer().computeOnServer(s -> sow.isAlive() ? (double) sow.distanceTo(s.overworld().players().getFirst())
						: Double.MAX_VALUE);
				minDist = Math.min(minDist, d);
			}
		}
		float health1 = sp.getServer().computeOnServer(s -> s.overworld().players().getFirst().getHealth());
		sp.getServer().runCommand("gamerule fall_damage true");
		long warnings = WarnCharge.WARNINGS.sum() - warnings0;
		census.line("%s wild boar warning: sow holding her ground %s (%s), player from %.1f blocks to %.0f; warnings %d, closest %.2f blocks;"
				+ " player health %.1f -> %.1f", label, holds, state, startDist, APPROACH_TO, warnings, minDist,
				health0, health1);
		if (warnings < 1 || minDist < 1.0 || health1 < health0) {
			census.fail("%s wild boar warning: warnings %d, closest %.2f (at least 1), health %.1f -> %.1f (no loss)", label, warnings, minDist,
					health0, health1);
		}
		for (String c : new String[] {"gamemode creative @a", "effect give @a minecraft:invisibility infinite 0 true"}) {
			sp.getServer().runCommand(c);
		}
		sp.getServer().runOnServer(s -> {
			List<Entity> trial = new ArrayList<>(s.overworld().getEntities(ModEntities.wildBoar(), e -> e == sow || sow.getUUID().equals(
					e.motherId())));
			trial.forEach(Entity::discard);
		});
		SeasonDates.set(sp, SubSeason.MID_SUMMER, 0, label);
	}

	// ------------------------------------------------------------------ MSPT

	void mspt(TestSingleplayerContext sp, String label, int x, int z) {
		FaunaCensus.teleport(sp, x, z);
		context.waitTicks(200);
		// Only boars tick in the timing: the roe deer around are removed.
		sp.getServer().runOnServer(s -> new ArrayList<>(s.overworld().getEntities(ModEntities.roeDeer(), Entity::isAlive))
				.forEach(Entity::discard));
		List<WildBoar> boars = new ArrayList<>();
		while (boars.size() < 40) {
			boars.addAll(sounders(sp));
		}
		List<WildBoar> extra = new ArrayList<>(boars.subList(40, boars.size()));
		sp.getServer().runOnServer(s -> extra.forEach(Entity::discard));
		context.waitTicks(200);
		sp.getServer().runOnServer(s -> PolishAnimal.setTiming(true));
		context.waitTicks(FaunaTrials.MSPT_TICKS);
		double[] t = sp.getServer().computeOnServer(s -> {
			PolishAnimal.setTiming(false);
			return new double[] {PolishAnimal.TICK_NANOS.sum() / 1e6, PolishAnimal.TICKS.sum()};
		});
		double perAnimal = t[1] == 0 ? Double.NaN : t[0] / t[1];
		long all = sp.getServer().computeOnServer(s -> (long) s.overworld().getEntities(ModEntities.wildBoar(), Entity::isAlive).size());
		long deer = sp.getServer().computeOnServer(s -> (long) s.overworld().getEntities(ModEntities.roeDeer(), Entity::isAlive).size());
		census.line("%s MSPT wild boar: 40 trial boars (%d loaded, %d roe deer), %.0f animal ticks in %d ticks: %.4f ms per animal and tick"
				+ " (budget %.2f)%s", label, all, deer, t[1], FaunaTrials.MSPT_TICKS, perAnimal, FaunaTrials.MSPT_BUDGET,
				REPORT_ONLY ? "; report only (-PtimingsReportOnly)" : "");
		if (!(perAnimal <= FaunaTrials.MSPT_BUDGET) && !REPORT_ONLY) {
			census.fail("%s MSPT %.4f ms per wild boar and tick above %.2f", label, perAnimal, FaunaTrials.MSPT_BUDGET);
		}
		sp.getServer().runOnServer(s -> boars.subList(0, 40).forEach(Entity::discard));
	}
}
