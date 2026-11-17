package pl.polishforests.entity.spawn;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.animal.frog.FrogVariant;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import pl.polishforests.PolishForests;
import pl.polishforests.season.Seasons;
import pl.polishforests.season.SubSeason;
import pl.polishforests.worldgen.habitat.HabitatBiome;

/**
 * The common frog (step FA4, G.62-f, docs/04-pionowy-wycinek.md "Projekt FA4"): the frog variant
 * {@code polishforests:common_frog} ({@code data/polishforests/frog_variant/common_frog.json}, texture of tools/fauna) is
 * chosen by vanilla's variant selection for every frog that appears in a biome of the tag
 * {@code #polishforests:spawns_common_frog} (the forests of the mod and the wet zones), with priority 2 over the vanilla
 * variants (priority 0 and 1), so in those biomes no vanilla variant appears: frogs of the biome spawn lists (alder carr,
 * peatlands, reedbed: unchanged, KFA-4), from tadpoles, spawn eggs and commands alike.
 *
 * <p>This spawner adds frogs where the biome lists have none: in the oak-hornbeam forests by the puddles, in the alder
 * carrs and the floodplain forests, on land columns of those forests within {@value #WATER_REACH} blocks of water
 * ({@link #weight}), from March to October (they hibernate from November to February). Every {@value #INTERVAL} ticks,
 * around each player, the target is {@value #DENSITY_PER_HA} frogs per hectare of the weighted wet forest area within
 * {@value #AREA_RADIUS} blocks (the real density near breeding waters, not multiplied: frogs are common), at most
 * {@value #MAX_NEAR} near a player; frogs within {@value #COUNT_RADIUS} blocks (any variant) count. A group of 1-3 appears
 * {@value #SPAWN_MIN}-{@value #SPAWN_MAX} blocks away. These frogs carry the entity tag {@value #TAG}: they are outside
 * vanilla's mob counts ({@code MobPersistenceMixin}, KFA-4) and this spawner removes them beyond {@value #DESPAWN} blocks
 * from every player, so their number does not grow with exploration. Off with {@code polishforests:forest_fauna} or
 * {@code spawn_mobs} like the other animals of the mod.
 */
public final class FrogSpawner {
	public static final String TAG = "polishforests.forest_frog";
	public static final ResourceKey<FrogVariant> COMMON_FROG = ResourceKey.create(Registries.FROG_VARIANT, PolishForests.id("common_frog"));
	public static final TagKey<Biome> SPAWNS_COMMON_FROG = TagKey.create(Registries.BIOME, PolishForests.id("spawns_common_frog"));
	public static final int INTERVAL = 400;
	public static final int AREA_RADIUS = 48;
	public static final int AREA_STEP = 8;
	public static final int COUNT_RADIUS = 64;
	public static final int SPAWN_MIN = 16;
	public static final int SPAWN_MAX = 48;
	public static final int MAX_NEAR = 6;
	public static final int WATER_REACH = 6;
	public static final int DESPAWN = 128;
	public static final double DENSITY_PER_HA = 20;
	private static final int TRIES = 10;

	public static final LongAdder SPAWNED = new LongAdder();
	public static final LongAdder REMOVED = new LongAdder();

	private FrogSpawner() {
	}

	public static void register() {
		ServerTickEvents.END_LEVEL_TICK.register(FrogSpawner::tick);
	}

	/** Weight of a habitat for the frogs of this spawner (0 = none). */
	public static float habitatWeight(@Nullable HabitatBiome b) {
		if (b == null) {
			return 0;
		}
		return switch (b) {
			case ALDER_CARR, ASH_ALDER_FOREST -> 1.0F;
			case WILLOW_POPLAR_FOREST, ELM_ASH_FOREST -> 0.8F;
			case GRAY_ALDER_FOREST -> 0.6F;
			case OAK_HORNBEAM_FOREST, BOG_WOODLAND -> 0.5F;
			case MOIST_PINE_FOREST, MIXED_FOREST -> 0.3F;
			case LOWLAND_BEECH_FOREST -> 0.2F;
			default -> 0;
		};
	}

	/** Whether frogs are about in a sub-season (March to October). */
	public static boolean active(SubSeason sub) {
		return sub.ordinal() <= SubSeason.MID_AUTUMN.ordinal();
	}

	/** Whether a frog is of the common frog variant. */
	public static boolean isCommonFrog(Frog frog) {
		return frog.getVariant().is(COMMON_FROG);
	}

	/** Weight of a column: the habitat weight on a dry land column within {@value #WATER_REACH} blocks of water, else 0. */
	public static float weight(LevelReader level, int x, int z) {
		float w = habitatWeight(FaunaHabitat.surface(level, x, z));
		return w > 0 && !water(level, x, z) && nearWater(level, x, z) ? w : 0;
	}

	private static boolean water(LevelReader level, int x, int z) {
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
		FluidState f = level.getFluidState(new BlockPos(x, y, z));
		return !f.isEmpty();
	}

	/** Water at the surface 2, 4 or 6 blocks away in the 8 directions. */
	static boolean nearWater(LevelReader level, int x, int z) {
		for (int r = 2; r <= WATER_REACH; r += 2) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					if ((dx != 0 || dz != 0) && level.hasChunk((x + dx * r) >> 4, (z + dz * r) >> 4) && water(level, x + dx * r, z + dz * r)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private static void tick(ServerLevel level) {
		if (Math.floorMod(level.getGameTime() + 131, INTERVAL) != 0 || !FaunaSpawner.applies(level)) {
			return;
		}
		List<ServerPlayer> players = new ArrayList<>();
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator()) {
				players.add(p);
			}
		}
		removeFar(level, players);
		if (players.isEmpty() || !FaunaRules.spawning(level) || !active(Seasons.provider().subSeason(level))) {
			return;
		}
		RandomSource random = level.getRandom();
		int loaded = level.getEntities(EntityTypes.FROG, f -> f.entityTags().contains(TAG)).size();
		for (ServerPlayer player : players) {
			if (loaded >= MAX_NEAR * players.size()) {
				return;
			}
			double target = Math.min(MAX_NEAR, DENSITY_PER_HA / 10000.0 * wetArea(level, player.getBlockX(), player.getBlockZ()));
			int want = FaunaSpawner.stochasticRound(target, random) - frogsNear(level, player.position());
			if (want <= 0) {
				continue;
			}
			loaded += spawnNear(level, player, players, Math.min(want, 1 + random.nextInt(3)), random);
		}
	}

	/** The weighted wet forest area around a column (blocks). */
	public static double wetArea(ServerLevel level, int cx, int cz) {
		double sum = 0;
		for (int dx = -AREA_RADIUS; dx <= AREA_RADIUS; dx += AREA_STEP) {
			for (int dz = -AREA_RADIUS; dz <= AREA_RADIUS; dz += AREA_STEP) {
				int x = cx + dx;
				int z = cz + dz;
				if (dx * dx + dz * dz <= AREA_RADIUS * AREA_RADIUS && level.hasChunk(x >> 4, z >> 4)) {
					sum += weight(level, x, z);
				}
			}
		}
		return sum * AREA_STEP * AREA_STEP;
	}

	/** Frogs of any variant within {@value #COUNT_RADIUS} blocks. */
	public static int frogsNear(ServerLevel level, Vec3 at) {
		double r = COUNT_RADIUS;
		return level.getEntities(EntityTypes.FROG, new net.minecraft.world.phys.AABB(at.x - r, at.y - 48, at.z - r, at.x + r, at.y + 48, at.z + r),
				f -> f.distanceToSqr(at.x, f.getY(), at.z) <= r * r).size();
	}

	private static int spawnNear(ServerLevel level, ServerPlayer player, List<ServerPlayer> players, int size, RandomSource random) {
		for (int i = 0; i < TRIES; i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			double d = SPAWN_MIN + random.nextDouble() * (SPAWN_MAX - SPAWN_MIN);
			int x = Mth.floor(player.getX() + Math.cos(angle) * d);
			int z = Mth.floor(player.getZ() + Math.sin(angle) * d);
			if (!level.isPositionEntityTicking(new BlockPos(x, player.getBlockY(), z)) || nearAny(players, x, z)
					|| random.nextFloat() >= weight(level, x, z)) {
				continue;
			}
			int n = 0;
			for (int k = 0; k < size; k++) {
				int fx = k == 0 ? x : x + random.nextInt(5) - 2;
				int fz = k == 0 ? z : z + random.nextInt(5) - 2;
				Vec3 at = FaunaSpawner.placeUncounted(level, EntityTypes.FROG, fx, fz);
				if (at == null) {
					continue;
				}
				Frog frog = EntityTypes.FROG.create(level, EntitySpawnReason.NATURAL);
				if (frog == null) {
					continue;
				}
				frog.snapTo(at.x, at.y, at.z, random.nextFloat() * 360.0F, 0.0F);
				frog.finalizeSpawn(level, level.getCurrentDifficultyAt(frog.blockPosition()), EntitySpawnReason.NATURAL, null);
				frog.addTag(TAG);
				level.addFreshEntityWithPassengers(frog);
				n++;
			}
			SPAWNED.add(n);
			return n;
		}
		return 0;
	}

	private static boolean nearAny(List<ServerPlayer> players, int x, int z) {
		for (ServerPlayer p : players) {
			double dx = p.getX() - x - 0.5;
			double dz = p.getZ() - z - 0.5;
			if (dx * dx + dz * dz < SPAWN_MIN * SPAWN_MIN) {
				return true;
			}
		}
		return false;
	}

	/** Removes the frogs of this spawner that are farther than {@value #DESPAWN} blocks from every player (not named ones). */
	private static void removeFar(ServerLevel level, List<ServerPlayer> players) {
		for (Frog f : level.getEntities(EntityTypes.FROG, f -> f.entityTags().contains(TAG) && !f.hasCustomName() && !f.isLeashed())) {
			boolean near = false;
			for (ServerPlayer p : players) {
				if (p.distanceToSqr(f) <= (double) DESPAWN * DESPAWN) {
					near = true;
					break;
				}
			}
			if (!near) {
				f.discard();
				REMOVED.increment();
			}
		}
	}
}
