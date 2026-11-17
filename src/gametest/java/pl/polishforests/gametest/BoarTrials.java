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
import net.minecraft.world.level.block.state.BlockState;
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
 * <li><b>Rooting</b> ({@link #rooting}): {@value #ROOTERS} boars at night at the place for {@value #ROOTING_TICKS} ticks
 * each with the game rule {@code polishforests:boar_rooting} off, with vanilla's {@code mob_griefing} off, and with both on:
 * no patch and no new coarse or rooted dirt within {@value #SCAN} blocks in the first two, at least one patch in the last.
 * <li><b>Warning</b> ({@link #warning}): in late spring a sow with two young, 14 blocks from a visible survival player who
 * comes to 7 blocks; the sow must warn (a charge), come no closer than 1 block, and the player loses no health.
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
		SeasonDates.setTimeOfDay(sp, 18_000, label);
		String[][] phases = {{"boar_rooting off", "gamerule polishforests:boar_rooting false", "gamerule mob_griefing true"},
				{"mob_griefing off", "gamerule polishforests:boar_rooting true", "gamerule mob_griefing false"},
				{"both on", "gamerule polishforests:boar_rooting true", "gamerule mob_griefing true"}};
		for (String[] phase : phases) {
			sp.getServer().runCommand(phase[1]);
			sp.getServer().runCommand(phase[2]);
			int dirtBefore = sp.getServer().computeOnServer(s -> dirt(s.overworld(), x, z));
			long patches0 = BoarRooting.PATCHES.sum();
			long blocks0 = BoarRooting.BLOCKS.sum();
			long refused0 = BoarRooting.REFUSED.sum();
			List<WildBoar> boars = sp.getServer().computeOnServer(s -> {
				ServerLevel level = s.overworld();
				List<WildBoar> out = new ArrayList<>();
				for (int i = 0; i < ROOTERS; i++) {
					double angle = Math.PI * 2 * i / ROOTERS;
					WildBoar b = create(level, x + (int) Math.round(Math.cos(angle) * 10), z + (int) Math.round(Math.sin(angle) * 10));
					b.setMale(true);
					level.addFreshEntity(b);
					out.add(b);
				}
				return out;
			});
			context.waitTicks(ROOTING_TICKS);
			int dirtAfter = sp.getServer().computeOnServer(s -> dirt(s.overworld(), x, z));
			long patches = BoarRooting.PATCHES.sum() - patches0;
			long blocks = BoarRooting.BLOCKS.sum() - blocks0;
			long refused = BoarRooting.REFUSED.sum() - refused0;
			census.line("%s wild boar rooting, %s: %d boars for %d ticks at night: patches %d (%d blocks), refused %d; coarse and rooted dirt"
					+ " within %d blocks %d before, %d after", label, phase[0], ROOTERS, ROOTING_TICKS, patches, blocks, refused, SCAN, dirtBefore,
					dirtAfter);
			boolean on = phase[0].equals("both on");
			if (on && patches < 1) {
				census.fail("%s wild boar rooting with both rules on: no patch in %d ticks", label, ROOTING_TICKS);
			}
			if (!on && (patches > 0 || dirtAfter > dirtBefore)) {
				census.fail("%s wild boar rooting with %s: %d patches, dirt %d -> %d (expected none)", label, phase[0], patches, dirtBefore,
						dirtAfter);
			}
			sp.getServer().runOnServer(s -> boars.forEach(Entity::discard));
		}
		sp.getServer().runCommand("gamerule mob_griefing true");
		sp.getServer().runCommand("gamerule polishforests:boar_rooting true");
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
			for (int i = 0; i < 2; i++) {
				WildBoar young = create(level, x + side[0] * (15 + i), z + side[1] * (15 + i));
				young.setBaby(true);
				young.setMother(mother);
				young.followLeader(mother);
				level.addFreshEntity(young);
			}
			return mother;
		});
		// Her calendar and her young: the sow holds her ground once she knows the sub-season and sees her young near.
		context.waitTicks(120);
		boolean holds = sp.getServer().computeOnServer(s -> sow.holdsGround());
		for (String c : new String[] {"gamemode survival @a", "effect clear @a minecraft:invisibility"}) {
			sp.getServer().runCommand(c);
		}
		float health0 = sp.getServer().computeOnServer(s -> s.overworld().players().getFirst().getHealth());
		double minDist = Double.MAX_VALUE;
		double startDist = sp.getServer().computeOnServer(s -> (double) sow.distanceTo(s.overworld().players().getFirst()));
		// The player walks up from the place to 7 blocks from where the sow stood, one block every 10 ticks, then stands.
		for (int step = 0; step <= 14; step++) {
			if (step <= 7) {
				int k = step;
				int px = x + side[0] * k;
				int pz = z + side[1] * k;
				int py = sp.getServer().computeOnServer(s -> s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, px, pz));
				sp.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @a %d.5 %d %d.5", px, py, pz));
			}
			for (int t = 0; t < 10; t += 2) {
				context.waitTicks(2);
				double d = sp.getServer().computeOnServer(s -> sow.isAlive() ? (double) sow.distanceTo(s.overworld().players().getFirst())
						: Double.MAX_VALUE);
				minDist = Math.min(minDist, d);
			}
		}
		float health1 = sp.getServer().computeOnServer(s -> s.overworld().players().getFirst().getHealth());
		long warnings = WarnCharge.WARNINGS.sum() - warnings0;
		census.line("%s wild boar warning: sow holding her ground %s, player from %.1f blocks to 7; warnings %d, closest %.2f blocks; player"
				+ " health %.1f -> %.1f", label, holds, startDist, warnings, minDist, health0, health1);
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
