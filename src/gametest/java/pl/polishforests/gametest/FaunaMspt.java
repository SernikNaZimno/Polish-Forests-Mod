package pl.polishforests.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import pl.polishforests.command.FaunaSites;
import pl.polishforests.entity.base.PolishAnimal;
import pl.polishforests.entity.spawn.FaunaSpawner;
import pl.polishforests.entity.spawn.FrogSpawner;
import pl.polishforests.worldgen.habitat.HabitatBiome;

/**
 * Step FA10 (KFA-6, ZW-8): the two MSPT scenes the steps FA2–FA6 did not have, run with {@code -Psites=mspt} after the
 * scenes of one species each (roe deer, wild boar, red squirrel, Eurasian jay):
 * <ul>
 * <li><b>common frog</b> ({@link #frogs}): {@value #FROGS} frogs in the alder carr nearest the place (the frogs of the
 * mod are vanilla frogs with the variant {@code polishforests:common_frog}; their ticks are timed by the game test mixin
 * {@code FrogTimingMixin}, {@link FrogTiming});</li>
 * <li><b>mixed scene</b> ({@link #mixed}): {@value #MIXED_DEER} roe deer, {@value #MIXED_BOARS} wild boars,
 * {@value #MIXED_SQUIRRELS} red squirrels, {@value #MIXED_JAYS} Eurasian jays and {@value #MIXED_FROGS} common frogs (40
 * animals) at the place, the ticks of all of them timed together.</li>
 * </ul>
 * Each scene: the other animals of the mod and the frogs around removed, the animals placed, 400 ticks to settle, then
 * {@value FaunaTrials#MSPT_TICKS} ticks timed; the verdict is at most {@value FaunaTrials#MSPT_BUDGET} ms of server tick
 * per animal and tick (with {@code -PtimingsReportOnly} a report only). The server's average tick before and after the
 * animals are placed is logged as a cross-check (it also holds the vanilla costs around an entity: collisions, tracking).
 */
final class FaunaMspt {
	static final int FROGS = 40;
	static final int MIXED_DEER = 10;
	static final int MIXED_BOARS = 8;
	static final int MIXED_SQUIRRELS = 8;
	static final int MIXED_JAYS = 8;
	static final int MIXED_FROGS = 6;
	private static final boolean REPORT_ONLY = Boolean.getBoolean("polishforests.timings.reportOnly");

	private final ClientGameTestContext context;
	private final FaunaCensus census;
	private final FaunaTrials trials;

	FaunaMspt(ClientGameTestContext context, FaunaCensus census, FaunaTrials trials) {
		this.context = context;
		this.census = census;
		this.trials = trials;
	}

	// ------------------------------------------------------------------ common frog

	void frogs(TestSingleplayerContext sp, String label, int x, int z) {
		double[] carr = sp.getServer().computeOnServer(s -> FaunaSites.locate(s.overworld(), HabitatBiome.ALDER_CARR, x, z));
		int[] at = carr == null ? new int[] {x, z} : new int[] {(int) Math.floor(carr[0]), (int) Math.floor(carr[1])};
		if (carr == null) {
			census.line("%s MSPT common frog: no alder carr found near %d %d, scene at the place", label, x, z);
		}
		FaunaCensus.teleport(sp, at[0], at[1]);
		context.waitTicks(200);
		clearAround(sp);
		double before = averageTick(sp);
		List<Frog> frogs = sp.getServer().computeOnServer(s -> placeFrogs(s.overworld(), FROGS, 4, 24));
		context.waitTicks(400);
		sp.getServer().runOnServer(s -> FrogTiming.set(true));
		context.waitTicks(FaunaTrials.MSPT_TICKS);
		double[] t = sp.getServer().computeOnServer(s -> {
			FrogTiming.set(false);
			return new double[] {FrogTiming.TICK_NANOS.sum() / 1e6, FrogTiming.TICKS.sum()};
		});
		double after = averageTick(sp);
		long[] alive = sp.getServer().computeOnServer(s -> new long[] {frogs.stream().filter(Entity::isAlive).count(), frogs.stream()
				.filter(f -> f.isAlive() && FrogSpawner.isCommonFrog(f)).count()});
		double perAnimal = t[1] == 0 ? Double.NaN : t[0] / t[1];
		census.line("%s MSPT common frog: %d trial frogs at %d %d (%d alive, %d of the variant common_frog), %.0f animal ticks in %d"
				+ " ticks: %.4f ms per animal and tick (budget %.2f); server average tick %.2f ms before, %.2f ms after (difference"
				+ " per frog %.4f ms)%s", label, frogs.size(), at[0], at[1], alive[0], alive[1], t[1], FaunaTrials.MSPT_TICKS, perAnimal,
				FaunaTrials.MSPT_BUDGET, before, after, (after - before) / Math.max(1, frogs.size()), reportOnly());
		judge(label, "common frog", perAnimal);
		sp.getServer().runOnServer(s -> frogs.forEach(Entity::discard));
	}

	// ------------------------------------------------------------------ mixed scene

	void mixed(TestSingleplayerContext sp, String label, int x, int z) {
		FaunaCensus.teleport(sp, x, z);
		context.waitTicks(200);
		clearAround(sp);
		double before = averageTick(sp);
		List<Entity> all = new ArrayList<>();
		all.addAll(trim(sp, trials.herd(sp), MIXED_DEER));
		all.addAll(trim(sp, trials.boars.sounders(sp), MIXED_BOARS));
		all.addAll(SquirrelTrials.place(sp, MIXED_SQUIRRELS));
		all.addAll(JayTrials.place(sp, MIXED_JAYS, 4, 24));
		all.addAll(sp.getServer().computeOnServer(s -> placeFrogs(s.overworld(), MIXED_FROGS, 4, 20)));
		context.waitTicks(400);
		sp.getServer().runOnServer(s -> {
			PolishAnimal.setTiming(true);
			FrogTiming.set(true);
		});
		context.waitTicks(FaunaTrials.MSPT_TICKS);
		double[] t = sp.getServer().computeOnServer(s -> {
			PolishAnimal.setTiming(false);
			FrogTiming.set(false);
			return new double[] {PolishAnimal.TICK_NANOS.sum() / 1e6, PolishAnimal.TICKS.sum(), FrogTiming.TICK_NANOS.sum() / 1e6,
					FrogTiming.TICKS.sum()};
		});
		double after = averageTick(sp);
		String alive = sp.getServer().computeOnServer(s -> {
			Map<String, Integer> by = new TreeMap<>();
			all.stream().filter(Entity::isAlive).forEach(e -> by.merge(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath(), 1,
					Integer::sum));
			return by.toString();
		});
		double ms = t[0] + t[2];
		double ticks = t[1] + t[3];
		double perAnimal = ticks == 0 ? Double.NaN : ms / ticks;
		census.line("%s", String.format(Locale.ROOT, "%s MSPT mixed scene: %d trial animals (%d roe deer, %d wild boars, %d red squirrels,"
				+ " %d Eurasian jays, %d common frogs; alive at the end %s), %.0f animal ticks in %d ticks: %.4f ms per animal and tick"
				+ " (budget %.2f; mod animals %.4f, frogs %.4f); server average tick %.2f ms before, %.2f ms after (difference per"
				+ " animal %.4f ms)%s", label, all.size(), MIXED_DEER, MIXED_BOARS, MIXED_SQUIRRELS, MIXED_JAYS, MIXED_FROGS, alive,
				ticks, FaunaTrials.MSPT_TICKS, perAnimal, FaunaTrials.MSPT_BUDGET, t[1] == 0 ? Double.NaN : t[0] / t[1], t[3] == 0
						? Double.NaN : t[2] / t[3], before, after, (after - before) / Math.max(1, all.size()), reportOnly()));
		judge(label, "animal of the mixed scene", perAnimal);
		sp.getServer().runOnServer(s -> all.forEach(Entity::discard));
	}

	// ------------------------------------------------------------------ helpers

	private void judge(String label, String what, double perAnimal) {
		if (!(perAnimal <= FaunaTrials.MSPT_BUDGET) && !REPORT_ONLY) {
			census.fail("%s MSPT %.4f ms per %s and tick above %.2f", label, perAnimal, what, FaunaTrials.MSPT_BUDGET);
		}
	}

	private static String reportOnly() {
		return REPORT_ONLY ? "; report only (-PtimingsReportOnly)" : "";
	}

	/** The first n of the animals; the rest discarded. */
	private static <T extends Entity> List<T> trim(TestSingleplayerContext sp, List<T> animals, int n) {
		List<T> extra = new ArrayList<>(animals.subList(Math.min(n, animals.size()), animals.size()));
		sp.getServer().runOnServer(s -> extra.forEach(Entity::discard));
		return new ArrayList<>(animals.subList(0, Math.min(n, animals.size())));
	}

	/** Removes the animals of the mod and the frogs of the level (the spawners are off during the scenes). */
	private static void clearAround(TestSingleplayerContext sp) {
		sp.getServer().runOnServer(s -> {
			List<Entity> other = new ArrayList<>();
			s.overworld().getAllEntities().forEach(e -> {
				if (e instanceof PolishAnimal<?> || e instanceof Frog) {
					other.add(e);
				}
			});
			other.forEach(Entity::discard);
		});
	}

	/** Places n frogs min-max blocks around the player on the places of the frog spawner (or on the ground). */
	private static List<Frog> placeFrogs(ServerLevel level, int n, int min, int max) {
		ServerPlayer p = level.players().getFirst();
		List<Frog> out = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			double angle = Math.PI * 2 * i / n;
			double d = min + (max - min) * (double) ((i * 7) % n) / n;
			Vec3 at = frogSpot(level, p.getBlockX() + (int) Math.round(Math.cos(angle) * d), p.getBlockZ() + (int) Math.round(Math.sin(
					angle) * d));
			Frog frog = EntityTypes.FROG.create(level, EntitySpawnReason.COMMAND);
			if (frog == null) {
				continue;
			}
			frog.snapTo(at.x, at.y, at.z, level.getRandom().nextFloat() * 360, 0);
			// The variant comes from the biome as at a natural spawn (common_frog in the biomes of #spawns_common_frog).
			frog.finalizeSpawn(level, level.getCurrentDifficultyAt(frog.blockPosition()), EntitySpawnReason.COMMAND, null);
			level.addFreshEntity(frog);
			out.add(frog);
		}
		return out;
	}

	private static Vec3 frogSpot(ServerLevel level, int x, int z) {
		for (int r = 0; r <= 8; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					level.getChunk((x + dx) >> 4, (z + dz) >> 4);
					Vec3 at = FaunaSpawner.placeUncounted(level, EntityTypes.FROG, x + dx, z + dz, BlockTags.FROGS_SPAWNABLE_ON);
					if (at != null) {
						return at;
					}
				}
			}
		}
		return new Vec3(x + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z + 0.5);
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
