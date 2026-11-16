package pl.polishforests.entity.spawn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;
import pl.polishforests.entity.ModEntities;
import pl.polishforests.entity.base.AnimalProfile;
import pl.polishforests.entity.base.PolishAnimal;
import pl.polishforests.season.Seasons;
import pl.polishforests.worldgen.chunk.ChunkHabitats;
import pl.polishforests.worldgen.chunk.PolandChunkGenerator;
import pl.polishforests.worldgen.vanilla.VanillaForestsBiomeSource;

/**
 * Where and when the animals of the mod appear (step FA1, G.62-b, KFA-4, KFA-5; docs/04-pionowy-wycinek.md "Projekt
 * FA1"). Two ways, both from the habitat of the column and the density of the species' {@link AnimalProfile}, both off
 * when the game rule {@code polishforests:forest_fauna} or vanilla's {@code spawn_mobs} is off, both only in the world types
 * of the mod (a "Poland" generator or the biome source of "Default + Polish Forests"):
 *
 * <ol>
 * <li><b>At generation</b> ({@link #spawnAtGeneration}): the last generation stage of a chunk ({@code spawnOriginalMobs})
 * reads the chunk's {@link ChunkHabitats} before it drops them, and after vanilla's own spawn
 * ({@code NaturalSpawner.spawnMobsForChunkGeneration}, unchanged, so it sees the same chunk as before) places groups whose
 * expected number is the target density times the weighted columns of the chunk (up to {@value #GENERATION_TRIES} columns
 * drawn per group). The draw has its own random source from the seed, the chunk and the new salt
 * {@link #GENERATION_SALT}: no block, no other random source and no vanilla spawn changes. These animals stay with their
 * chunk (they never despawn, as vanilla's animals of the chunk generation are persistent by their category).
 * Chunks generated before this step get no animals this way (KFA-1): the spawner fills them.
 * <li><b>The fauna spawner</b> ({@link #tick}): every {@value #INTERVAL} ticks, around each player (not spectators), the
 * target is the density times the weighted forest area within {@value #AREA_RADIUS} blocks (a lattice of
 * {@value #AREA_STEP} blocks over the loaded chunks), capped by the profile's own limit; when fewer animals of the species
 * are within {@value #COUNT_RADIUS} blocks, one group appears {@value #SPAWN_MIN}-{@value #SPAWN_MAX} blocks away from the
 * player (never closer than {@value #SPAWN_MIN} blocks to any player), on an entity-ticking chunk. These animals (wanderers,
 * {@code PolishAnimal#isWanderer}) despawn
 * beyond 128 blocks, and a limit of the level on them ({@code maxNearPlayer} times the players) stops their number from
 * growing with exploration.
 * </ol>
 *
 * <p><b>The place</b> ({@link #place}): the top of {@code MOTION_BLOCKING_NO_LEAVES} (so never on the crowns: leaves do not
 * stop that heightmap, and a branch or a trunk is no ground of the tag), the ground block in
 * {@link FaunaRules#SPAWNABLE_ON} (a snow layer or another thin block with collision on it allowed), no fluid at the feet, in the ground or in the hitbox (a dry
 * column), block light or sky light at least {@value #MIN_LIGHT} (the raw brightness in daylight, so the shade of the
 * canopy counts and the night does not), and room for the hitbox (a thin block such as a moss carpet or a snow layer at
 * the feet lifts the animal onto it).
 *
 * <p>The animals of the mod are outside vanilla's mob counts ({@link PolishAnimal#requiresCustomPersistence()}), so the
 * vanilla animals spawn as before (CP-D.3).
 */
public final class FaunaSpawner {
	/** Ticks between two rounds of the spawner. */
	public static final int INTERVAL = 300;
	/** Radius of the forest area of the target (blocks). */
	public static final int AREA_RADIUS = 64;
	/** Spacing of the lattice of the forest area (blocks). */
	public static final int AREA_STEP = 8;
	/** Radius within which the animals of a species near a player are counted (blocks). */
	public static final int COUNT_RADIUS = 80;
	/** Distance range of a new group from the player (blocks). */
	public static final int SPAWN_MIN = 24;
	public static final int SPAWN_MAX = 64;
	/** Least raw brightness (daylight) of the place. */
	public static final int MIN_LIGHT = 5;
	/** Largest height of a block at the feet the animal stands on (a carpet, a snow layer). */
	static final double THIN_BLOCK = 0.9;
	/** Spread of the members of a group around the first animal (blocks). */
	static final int GROUP_SPREAD = 4;
	/** Salt of the draw of the spawn at generation (a new random source, docs/04 "Projekt FA1"). */
	public static final long GENERATION_SALT = 0x5A4E_FA01_6E4E_5A17L;
	private static final int TRIES = 8;
	/** Columns drawn for one group at generation before it is given up (a trunk, a bush or a pool on the first). */
	static final int GENERATION_TRIES = 4;
	/**
	 * The habitats of a chunk of "Default + Polish Forests" held between the head and the return of its
	 * {@code spawnOriginalMobs} ({@code NoiseBasedChunkGeneratorMixin}), on the thread that generates it.
	 */
	public static final ThreadLocal<ChunkHabitats> HELD_HABITATS = new ThreadLocal<>();

	/** Why a place was refused (counted for the census of the fauna game test). */
	public enum Reason {
		PLACED, NO_CHUNK, NEAR_PLAYER, HABITAT, GROUND, WATER, DARK, BLOCKED
	}

	/** Counters of the census: places by reason, and animals placed at generation and by the spawner. */
	public static final LongAdder[] REASONS = adders(Reason.values().length);
	public static final LongAdder GENERATION_ANIMALS = new LongAdder();
	public static final LongAdder GENERATION_GROUPS = new LongAdder();
	public static final LongAdder SPAWNER_ANIMALS = new LongAdder();
	public static final LongAdder SPAWNER_GROUPS = new LongAdder();
	/** Ground blocks of the places refused for {@link Reason#GROUND}, by id (for the census). */
	public static final java.util.concurrent.ConcurrentHashMap<String, LongAdder> GROUND_REFUSED = new java.util.concurrent.ConcurrentHashMap<>();

	/** A species the spawners place: its entity type and its profile. */
	public record Species(EntityType<? extends PolishAnimal<?>> type, AnimalProfile profile) {
	}

	private FaunaSpawner() {
	}

	public static void register() {
		FaunaRules.register();
		ServerTickEvents.END_LEVEL_TICK.register(FaunaSpawner::tick);
	}

	/** Whether the level is of a world type of the mod. */
	public static boolean applies(ServerLevel level) {
		var generator = level.getChunkSource().getGenerator();
		return generator instanceof PolandChunkGenerator || generator.getBiomeSource() instanceof VanillaForestsBiomeSource;
	}

	// ------------------------------------------------------------------ the fauna spawner

	private static void tick(ServerLevel level) {
		if (Math.floorMod(level.getGameTime() + 7, INTERVAL) != 0 || !applies(level) || !FaunaRules.spawning(level)) {
			return;
		}
		List<ServerPlayer> players = new ArrayList<>();
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator()) {
				players.add(p);
			}
		}
		if (players.isEmpty()) {
			return;
		}
		RandomSource random = level.getRandom();
		for (Species s : ModEntities.fauna()) {
			spawnAround(level, s, players, random);
		}
	}

	private static void spawnAround(ServerLevel level, Species s, List<ServerPlayer> players, RandomSource random) {
		AnimalProfile profile = s.profile();
		// The level's limit counts the animals that can despawn (the spawner's own): those of the spawn at generation belong to
		// their chunks (they never despawn, like vanilla's animals), so their number follows the loaded chunks and does not
		// grow with exploration; those from eggs, spawners and commands are the players' own.
		int loaded = level.getEntities(s.type(), e -> e.removeWhenFarAway(Double.MAX_VALUE)).size();
		int cap = profile.maxNearPlayer() * players.size();
		List<ServerPlayer> order = new ArrayList<>(players);
		Collections.shuffle(order, new java.util.Random(random.nextLong()));
		for (ServerPlayer player : order) {
			if (loaded >= cap) {
				return;
			}
			double target = Math.min(profile.maxNearPlayer(), profile.densityPerBlock() * forestArea(level, profile, player.getBlockX(),
					player.getBlockZ()));
			int want = stochasticRound(target, random) - near(level, s.type(), player.position(), COUNT_RADIUS);
			if (want <= 0) {
				continue;
			}
			Vec3 spot = spot(level, s, player, players, random);
			if (spot == null) {
				continue;
			}
			int size = Math.min(want, profile.group(Seasons.provider().subSeason(level)).sample(random::nextInt));
			int n = spawnGroup(level, s, spot, size, EntitySpawnReason.NATURAL, random, null);
			if (n > 0) {
				SPAWNER_GROUPS.increment();
				SPAWNER_ANIMALS.add(n);
				loaded += n;
			}
		}
	}

	/** The weighted forest area around a column (blocks): the sum of the column weights on the lattice times its cell. */
	public static double forestArea(ServerLevel level, AnimalProfile profile, int cx, int cz) {
		double sum = 0;
		int r = AREA_RADIUS;
		for (int dx = -r; dx <= r; dx += AREA_STEP) {
			for (int dz = -r; dz <= r; dz += AREA_STEP) {
				if (dx * dx + dz * dz > r * r) {
					continue;
				}
				int x = cx + dx;
				int z = cz + dz;
				if (level.hasChunk(x >> 4, z >> 4)) {
					sum += FaunaHabitat.weight(level, profile, x, z);
				}
			}
		}
		return sum * AREA_STEP * AREA_STEP;
	}

	/** Animals of a type within a horizontal radius (and 64 blocks up and down). */
	public static int near(ServerLevel level, EntityType<? extends PolishAnimal<?>> type, Vec3 at, double radius) {
		AABB box = new AABB(at.x - radius, at.y - 64, at.z - radius, at.x + radius, at.y + 64, at.z + radius);
		double r2 = radius * radius;
		return level.getEntities(type, box, e -> {
			double dx = e.getX() - at.x;
			double dz = e.getZ() - at.z;
			return dx * dx + dz * dz <= r2;
		}).size();
	}

	/** The integer part of a value plus one with the probability of its fraction. */
	public static int stochasticRound(double value, RandomSource random) {
		int whole = (int) Math.floor(value);
		return whole + (random.nextDouble() < value - whole ? 1 : 0);
	}

	private static @Nullable Vec3 spot(ServerLevel level, Species s, ServerPlayer player, List<ServerPlayer> players, RandomSource random) {
		for (int i = 0; i < TRIES; i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double d = SPAWN_MIN + random.nextDouble() * (SPAWN_MAX - SPAWN_MIN);
			int x = Mth.floor(player.getX() + Math.cos(angle) * d);
			int z = Mth.floor(player.getZ() + Math.sin(angle) * d);
			if (!level.isPositionEntityTicking(new BlockPos(x, player.getBlockY(), z))) {
				count(Reason.NO_CHUNK);
				continue;
			}
			if (nearPlayer(players, x + 0.5, z + 0.5)) {
				count(Reason.NEAR_PLAYER);
				continue;
			}
			if (random.nextFloat() >= FaunaHabitat.weight(level, s.profile(), x, z)) {
				count(Reason.HABITAT);
				continue;
			}
			Vec3 at = place(level, s.type(), x, z);
			if (at != null) {
				return at;
			}
		}
		return null;
	}

	private static boolean nearPlayer(List<ServerPlayer> players, double x, double z) {
		for (ServerPlayer p : players) {
			double dx = p.getX() - x;
			double dz = p.getZ() - z;
			if (dx * dx + dz * dz < SPAWN_MIN * SPAWN_MIN) {
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------ spawn at generation

	/**
	 * The spawn at generation of one chunk (see the class comment). Called by {@code PolandChunkGenerator.spawnOriginalMobs}
	 * and for "Default + Polish Forests" by {@code NoiseBasedChunkGeneratorMixin}, after vanilla's spawn, with the habitats
	 * the chunk held before that stage dropped them.
	 */
	public static void spawnAtGeneration(WorldGenRegion region, @Nullable ChunkHabitats habitats) {
		if (habitats == null) {
			return;
		}
		ServerLevel level = region.getLevel();
		if (!FaunaRules.spawning(level)) {
			return;
		}
		ChunkPos c = region.getCenter();
		RandomSource random = generationRandom(region.getSeed(), c.x(), c.z());
		for (Species s : ModEntities.fauna()) {
			AnimalProfile profile = s.profile();
			float[] w = FaunaHabitat.columnWeights(habitats, profile);
			double sum = 0;
			for (float v : w) {
				sum += v;
			}
			AnimalProfile.Range group = profile.group(Seasons.provider().subSeason(level));
			int groups = stochasticRound(expectedGroups(profile, group, sum), random);
			for (int g = 0; g < groups; g++) {
				Vec3 at = null;
				for (int t = 0; t < GENERATION_TRIES && at == null; t++) {
					int column = pickColumn(w, random);
					if (column >= 0) {
						at = place(region, s.type(), c.getMinBlockX() + (column >> 4), c.getMinBlockZ() + (column & 15));
					}
				}
				if (at == null) {
					continue;
				}
				int n = spawnGroup(region, s, at, group.sample(random::nextInt), EntitySpawnReason.CHUNK_GENERATION, random, c);
				if (n > 0) {
					GENERATION_GROUPS.increment();
					GENERATION_ANIMALS.add(n);
				}
			}
		}
	}

	/** Expected groups in a chunk: the target density times the weighted columns, over the mean group size. */
	public static double expectedGroups(AnimalProfile profile, AnimalProfile.Range group, double weightedColumns) {
		return profile.densityPerBlock() * weightedColumns / ((group.min() + group.max()) / 2.0);
	}

	/** The random source of the spawn at generation of a chunk (new salt {@link #GENERATION_SALT}). */
	public static RandomSource generationRandom(long seed, int chunkX, int chunkZ) {
		long lo = RandomSupport.mixStafford13(seed ^ GENERATION_SALT);
		long hi = RandomSupport.mixStafford13(ChunkPos.pack(chunkX, chunkZ) ^ GENERATION_SALT * 31);
		return new XoroshiroRandomSource(lo, hi);
	}

	/** A column drawn with the probability of its weight (rejection, at most 32 draws), or -1. */
	public static int pickColumn(float[] weights, RandomSource random) {
		for (int i = 0; i < 32; i++) {
			int column = random.nextInt(weights.length);
			if (weights[column] > 0 && random.nextFloat() < weights[column]) {
				return column;
			}
		}
		return -1;
	}

	// ------------------------------------------------------------------ place and group

	/**
	 * The feet position of an animal of the type on the column, or null with the reason counted (see the class comment).
	 */
	public static @Nullable Vec3 place(ServerLevelAccessor level, EntityType<?> type, int x, int z) {
		Verdict v = check(level, type, x, z);
		count(v.reason());
		if (v.reason() == Reason.GROUND && v.ground() != null) {
			GROUND_REFUSED.computeIfAbsent(BuiltInRegistries.BLOCK.getKey(v.ground().getBlock()).toString(), k -> new LongAdder()).increment();
		}
		return v.reason() == Reason.PLACED ? new Vec3(x + 0.5, v.feet(), z + 0.5) : null;
	}

	/** Like {@link #place} but without counting the reason (the trials of the fauna game test). */
	public static @Nullable Vec3 placeUncounted(ServerLevelAccessor level, EntityType<?> type, int x, int z) {
		Verdict v = check(level, type, x, z);
		return v.reason() == Reason.PLACED ? new Vec3(x + 0.5, v.feet(), z + 0.5) : null;
	}

	/** The verdict on a column: the reason ({@link Reason#PLACED} when valid), the feet height and the ground block. */
	private record Verdict(Reason reason, double feet, @Nullable BlockState ground) {
		static Verdict of(Reason reason, @Nullable BlockState ground) {
			return new Verdict(reason, 0, ground);
		}
	}

	/** A block at the top of the column the animal stands on rather than in: a snow layer or a thin block with collision. */
	private static boolean thinTop(ServerLevelAccessor level, BlockPos pos, BlockState state) {
		if (state.is(Blocks.SNOW)) {
			return true;
		}
		VoxelShape shape = state.getCollisionShape(level, pos);
		return !shape.isEmpty() && shape.max(Direction.Axis.Y) <= THIN_BLOCK;
	}

	private static Verdict check(ServerLevelAccessor level, EntityType<?> type, int x, int z) {
		if (!level.hasChunk(x >> 4, z >> 4)) {
			return Verdict.of(Reason.NO_CHUNK, null);
		}
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		BlockPos pos = new BlockPos(x, y, z);
		BlockPos groundPos = pos.below();
		BlockState ground = level.getBlockState(groundPos);
		// A thin block with collision (a snow layer, a litter or moss layer of the mod) is the top of the heightmap: the
		// animal stands on it, and the block under it is the ground.
		if (!ground.is(FaunaRules.SPAWNABLE_ON) && thinTop(level, groundPos, ground)) {
			pos = groundPos;
			groundPos = pos.below();
			ground = level.getBlockState(groundPos);
		}
		if (!ground.is(FaunaRules.SPAWNABLE_ON)) {
			return Verdict.of(level.getFluidState(groundPos).isEmpty() ? Reason.GROUND : Reason.WATER, ground);
		}
		if (!level.getFluidState(groundPos).isEmpty() || !level.getFluidState(pos).isEmpty()) {
			return Verdict.of(Reason.WATER, ground);
		}
		double feet = pos.getY();
		BlockState at = level.getBlockState(pos);
		VoxelShape shape = at.getCollisionShape(level, pos);
		if (!shape.isEmpty()) {
			double top = shape.max(Direction.Axis.Y);
			if (top > THIN_BLOCK) {
				return Verdict.of(Reason.BLOCKED, ground);
			}
			feet += top;
		}
		if (level.getRawBrightness(pos, 0) < MIN_LIGHT) {
			return Verdict.of(Reason.DARK, ground);
		}
		AABB box = type.getSpawnAABB(x + 0.5, feet, z + 0.5);
		if (level.containsAnyLiquid(box)) {
			return Verdict.of(Reason.WATER, ground);
		}
		if (!level.noCollision(box)) {
			return Verdict.of(Reason.BLOCKED, ground);
		}
		return new Verdict(Reason.PLACED, feet, ground);
	}

	/**
	 * Spawns a group of up to {@code size} animals: the first at {@code first}, the others on valid places within
	 * {@value #GROUP_SPREAD} blocks (inside {@code chunk} when given). Returns how many appeared.
	 */
	static int spawnGroup(ServerLevelAccessor level, Species s, Vec3 first, int size, EntitySpawnReason reason, RandomSource random,
			@Nullable ChunkPos chunk) {
		int n = 0;
		SpawnGroupData data = null;
		for (int i = 0; i < size; i++) {
			Vec3 at = first;
			if (i > 0) {
				at = null;
				for (int t = 0; t < 4 && at == null; t++) {
					int x = Mth.floor(first.x) + random.nextInt(2 * GROUP_SPREAD + 1) - GROUP_SPREAD;
					int z = Mth.floor(first.z) + random.nextInt(2 * GROUP_SPREAD + 1) - GROUP_SPREAD;
					if (chunk != null) {
						x = Mth.clamp(x, chunk.getMinBlockX(), chunk.getMaxBlockX());
						z = Mth.clamp(z, chunk.getMinBlockZ(), chunk.getMaxBlockZ());
					}
					at = place(level, s.type(), x, z);
				}
				if (at == null) {
					continue;
				}
			}
			PolishAnimal<?> animal = s.type().create(level.getLevel(), reason);
			if (animal == null) {
				continue;
			}
			animal.snapTo(at.x, at.y, at.z, random.nextFloat() * 360.0F, 0.0F);
			// NATURAL makes a wanderer that despawns beyond 128 blocks; the animals of the chunk generation never despawn
			// (PolishAnimal.finalizeSpawn), like vanilla's: else those of the chunks generated 128-192 blocks ahead of a
			// walking player would despawn before he comes.
			data = animal.finalizeSpawn(level, level.getCurrentDifficultyAt(animal.blockPosition()), reason, data);
			level.addFreshEntityWithPassengers(animal);
			n++;
		}
		return n;
	}

	private static void count(Reason reason) {
		REASONS[reason.ordinal()].increment();
	}

	private static LongAdder[] adders(int n) {
		LongAdder[] a = new LongAdder[n];
		for (int i = 0; i < n; i++) {
			a[i] = new LongAdder();
		}
		return a;
	}
}
