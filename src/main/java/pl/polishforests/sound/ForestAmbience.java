package pl.polishforests.sound;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.LongAdder;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import pl.polishforests.PolishForests;
import pl.polishforests.entity.base.ForestAlarm;
import pl.polishforests.entity.spawn.FaunaHabitat;
import pl.polishforests.entity.spawn.FaunaSpawner;
import pl.polishforests.season.Seasons;
import pl.polishforests.season.SubSeason;
import pl.polishforests.sound.AmbienceRules.Phase;
import pl.polishforests.worldgen.habitat.HabitatBiome;

/**
 * The sound of the forest (step FA4, decision PW-16, docs/04-pionowy-wycinek.md "Projekt FA4"): a server event for each
 * player every 10-40 s ({@link AmbienceRules#interval}), from a random tall trunk ({@value #MIN_TRUNK} or more logs)
 * {@value #MIN_DISTANCE}-{@value #MAX_DISTANCE} blocks from the player, in a forest habitat of the mod, with a voice chosen
 * by the forest, the sub-season and the time of day ({@link AmbienceRules#choose}), heard by that player only (stage 2: a
 * sound packet to the listener, so players together do not hear each other's schedules; category ambient, so the
 * "Ambient/Environment" slider sets its loudness). The cost is a few heightmap reads per event ({@link #tallTrunk}: at most
 * {@value #TREE_TRIES} patches of 5 x 5 columns), nothing every tick. It works in the world
 * types of the mod ("Poland" in both scales, "Default + Polish Forests"), with or without Serene Seasons (the season comes
 * from {@link Seasons#provider()}). Game rule {@code polishforests:forest_ambience} (default true) turns it off.
 *
 * <p>The jay: every {@value #JAY_CHECK} ticks a player running through a forest of the mod (sprinting, alive, visible, not
 * a spectator: {@link ForestAlarm#runningThreat}) may startle a jay ({@value #JAY_CHANCE} per check, at most once in
 * {@value #JAY_COOLDOWN}-{@value #JAY_COOLDOWN_MAX} ticks, not at night): a screech from a tall trunk
 * {@value #JAY_MIN_DISTANCE}-{@value #JAY_MAX_DISTANCE} blocks away and a {@link ForestAlarm.Kind#JAY} alarm with radius
 * {@value #JAY_RADIUS} that the roe deer and the wild boars hear.
 *
 * <p>The draws have their own random source per player from the seed and the new salt {@link #SALT} (KFA-1: no salt of the
 * world and no block changes). Counters for the census of the fauna game test ({@code -Psites=sound}).
 */
public final class ForestAmbience {
	public static final int MIN_DISTANCE = 24;
	public static final int MAX_DISTANCE = 64;
	/** Least number of logs in the trunk the voice comes from. */
	public static final int MIN_TRUNK = 6;
	/** Places tried for a trunk (each a 5 x 5 patch of columns). */
	static final int TREE_TRIES = 6;
	/** Largest distance from the player to the voice (blocks, in 3D; a player deep in a cave hears no birds). */
	static final double MAX_HEARD = 80;
	public static final int JAY_CHECK = 10;
	public static final float JAY_CHANCE = 0.35F;
	public static final int JAY_COOLDOWN = 200;
	public static final int JAY_COOLDOWN_MAX = 400;
	public static final int JAY_MIN_DISTANCE = 8;
	public static final int JAY_MAX_DISTANCE = 24;
	public static final double JAY_RADIUS = 32;
	public static final float JAY_STRENGTH = 1.0F;
	/** Salt of the draws of the ambience (new). */
	public static final long SALT = 0x5A4E_FA04_A3B1_E7C5L;

	/** Events played by part of the day and sub-season, voices by part of the day, silent events and misses. */
	public static final LongAdder[][] EVENTS = adders(Phase.values().length, SubSeason.values().length);
	public static final LongAdder[][] VOICES = adders(ForestVoice.values().length, Phase.values().length);
	public static final LongAdder[] SILENT = adders(1, Phase.values().length)[0];
	public static final LongAdder NO_TREE = new LongAdder();
	public static final LongAdder NOT_FOREST = new LongAdder();
	public static final LongAdder TOO_FAR = new LongAdder();
	public static final LongAdder CHORUS = new LongAdder();
	public static final LongAdder JAY_SCREECHES = new LongAdder();
	public static final LongAdder JAY_HEARD = new LongAdder();

	/**
	 * Divides the intervals and cooldowns (only the fauna game test sets it, to count many events in a short time; the
	 * proportions between the parts of the day stay).
	 */
	public static volatile int timeScale = 1;

	private static final Map<UUID, Listener> LISTENERS = new HashMap<>();
	private static @Nullable GameRule<Boolean> rule;

	/** The state of one player: the next event, a pending chorus voice, the jay's cooldown, the random source. */
	private static final class Listener {
		long next;
		long chorusAt = -1;
		long nextJay;
		final RandomSource random;

		Listener(long seed, UUID id, long now) {
			long lo = RandomSupport.mixStafford13(seed ^ SALT);
			long hi = RandomSupport.mixStafford13(id.getMostSignificantBits() ^ id.getLeastSignificantBits() ^ SALT * 31);
			random = new XoroshiroRandomSource(lo, hi);
			next = now + scaled(AmbienceRules.MIN_INTERVAL + random.nextInt(AmbienceRules.MAX_INTERVAL - AmbienceRules.MIN_INTERVAL));
		}
	}

	private ForestAmbience() {
	}

	public static void register() {
		if (rule == null) {
			rule = GameRuleBuilder.forBoolean(true).category(GameRuleCategory.MISC).buildAndRegister(PolishForests.id("forest_ambience"));
		}
		ServerTickEvents.END_LEVEL_TICK.register(ForestAmbience::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LISTENERS.clear());
	}

	public static GameRule<Boolean> rule() {
		if (rule == null) {
			throw new IllegalStateException("The forest ambience game rule is not registered yet");
		}
		return rule;
	}

	/** Clears the counters (the fauna game test, before each count). */
	public static void resetCounters() {
		for (LongAdder[] row : EVENTS) {
			for (LongAdder a : row) {
				a.reset();
			}
		}
		for (LongAdder[] row : VOICES) {
			for (LongAdder a : row) {
				a.reset();
			}
		}
		for (LongAdder a : SILENT) {
			a.reset();
		}
		for (LongAdder a : new LongAdder[] {NO_TREE, NOT_FOREST, TOO_FAR, CHORUS, JAY_SCREECHES, JAY_HEARD}) {
			a.reset();
		}
	}

	/** Restarts the schedules of all players (the fauna game test, after it changes the time scale or the date). */
	public static void restart() {
		LISTENERS.clear();
	}

	private static int scaled(int ticks) {
		return Math.max(1, ticks / Math.max(1, timeScale));
	}

	private static void tick(ServerLevel level) {
		if (!FaunaSpawner.applies(level) || !level.getGameRules().get(rule())) {
			return;
		}
		long now = level.getGameTime();
		for (ServerPlayer p : level.players()) {
			if (p.isSpectator()) {
				continue;
			}
			Listener s = LISTENERS.computeIfAbsent(p.getUUID(), id -> new Listener(level.getSeed(), id, now));
			if (s.chorusAt >= 0 && now >= s.chorusAt) {
				s.chorusAt = -1;
				event(level, p, s, true);
			}
			if (now >= s.next) {
				s.next = now + scaled(AmbienceRules.interval(AmbienceRules.phase(level.getOverworldClockTime()), s.random));
				event(level, p, s, false);
			}
			if (Math.floorMod(now, JAY_CHECK) == 5 && ForestAlarm.runningThreat(p) && now >= s.nextJay
					&& AmbienceRules.phase(level.getOverworldClockTime()) != Phase.NIGHT
					&& AmbienceRules.kind(FaunaHabitat.surface(level, p.getBlockX(), p.getBlockZ())) != null
					&& s.random.nextFloat() < JAY_CHANCE) {
				s.nextJay = now + scaled(JAY_COOLDOWN + s.random.nextInt(JAY_COOLDOWN_MAX - JAY_COOLDOWN + 1));
				screech(level, p, s.random);
			}
		}
	}

	private static void event(ServerLevel level, ServerPlayer p, Listener s, boolean chorus) {
		SubSeason sub = Seasons.provider().subSeason(level);
		Phase phase = AmbienceRules.phase(level.getOverworldClockTime());
		BlockPos trunk = tallTrunk(level, p.getX(), p.getZ(), MIN_DISTANCE, MAX_DISTANCE, s.random);
		if (trunk == null) {
			NO_TREE.increment();
			return;
		}
		HabitatBiome habitat = FaunaHabitat.of(level.getBiome(trunk));
		if (AmbienceRules.kind(habitat) == null) {
			NOT_FOREST.increment();
			return;
		}
		Vec3 at = Vec3.atCenterOf(trunk).add(0, 1.5, 0);
		if (at.distanceTo(p.getEyePosition()) > MAX_HEARD) {
			TOO_FAR.increment();
			return;
		}
		ForestVoice voice = AmbienceRules.choose(habitat, sub, phase, s.random);
		if (voice == null) {
			SILENT[phase.ordinal()].increment();
			return;
		}
		play(level, p, at, voice, s.random);
		EVENTS[phase.ordinal()][sub.ordinal()].increment();
		VOICES[voice.ordinal()][phase.ordinal()].increment();
		if (chorus) {
			CHORUS.increment();
		} else if (AmbienceRules.chorus(phase, sub)) {
			s.chorusAt = level.getGameTime() + scaled(20 + s.random.nextInt(41));
		}
	}

	/**
	 * Plays a voice to its listener only (stage 2): each player has a schedule of their own, so a voice sent to everyone in
	 * range would make players standing together hear the events of all of them (n players, n times the events).
	 */
	private static void play(ServerLevel level, ServerPlayer p, Vec3 at, ForestVoice voice, RandomSource random) {
		double d = at.distanceTo(p.getEyePosition());
		float volume = (float) Math.max(voice.range(), d + 8) / 16.0F;
		float pitch = 0.94F + random.nextFloat() * 0.12F;
		p.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(voice.sound()), SoundSource.AMBIENT, at.x, at.y,
				at.z, volume, pitch, random.nextLong()));
	}

	/**
	 * A jay startled by a player: the screech from a tall trunk near the player and the jay's alarm (see the class
	 * comment). Returns how many animals heard the alarm, or -1 when no tall trunk was found. The fauna game test calls it
	 * directly for the chain jay - alarm - roe deer.
	 */
	public static int screech(ServerLevel level, ServerPlayer p, RandomSource random) {
		BlockPos trunk = tallTrunk(level, p.getX(), p.getZ(), JAY_MIN_DISTANCE, JAY_MAX_DISTANCE, random);
		if (trunk == null) {
			NO_TREE.increment();
			return -1;
		}
		Vec3 at = Vec3.atCenterOf(trunk).add(0, 1.5, 0);
		level.playSound(null, at.x, at.y, at.z, ModSounds.JAY, SoundSource.AMBIENT, (float) Math.max(56, at.distanceTo(p.position()) + 8) / 16.0F,
				0.94F + random.nextFloat() * 0.12F);
		JAY_SCREECHES.increment();
		VOICES[ForestVoice.JAY.ordinal()][AmbienceRules.phase(level.getOverworldClockTime()).ordinal()].increment();
		int heard = ForestAlarm.raise(level, new ForestAlarm(at, JAY_RADIUS, JAY_STRENGTH, ForestAlarm.Kind.JAY, null));
		JAY_HEARD.add(heard);
		return heard;
	}

	/**
	 * A tall trunk {@code min}-{@code max} blocks from a point: up to {@value #TREE_TRIES} random places in that ring, at
	 * each the tallest trunk of a 5 x 5 patch whose top (the top of {@code MOTION_BLOCKING_NO_LEAVES}, under the crown) is a
	 * log with at least {@value #MIN_TRUNK} logs below; only loaded chunks. Returns the top log, or null.
	 */
	public static @Nullable BlockPos tallTrunk(ServerLevel level, double px, double pz, int min, int max, RandomSource random) {
		for (int t = 0; t < TREE_TRIES; t++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double d = min + random.nextDouble() * (max - min);
			int cx = Mth.floor(px + Math.cos(angle) * d);
			int cz = Mth.floor(pz + Math.sin(angle) * d);
			BlockPos best = null;
			int bestHeight = 0;
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					int x = cx + dx;
					int z = cz + dz;
					if (!level.hasChunk(x >> 4, z >> 4)) {
						continue;
					}
					BlockPos top = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
					if (!level.getBlockState(top).is(BlockTags.LOGS)) {
						continue;
					}
					int h = 1;
					while (h < 48 && level.getBlockState(top.below(h)).is(BlockTags.LOGS)) {
						h++;
					}
					if (h >= MIN_TRUNK && h > bestHeight) {
						best = top;
						bestHeight = h;
					}
				}
			}
			if (best != null) {
				return best;
			}
		}
		return null;
	}

	private static LongAdder[][] adders(int rows, int columns) {
		LongAdder[][] a = new LongAdder[rows][columns];
		for (int i = 0; i < rows; i++) {
			for (int j = 0; j < columns; j++) {
				a[i][j] = new LongAdder();
			}
		}
		return a;
	}
}
