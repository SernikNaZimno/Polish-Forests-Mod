package pl.polishforests.gametest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import pl.polishforests.PolishForests;
import pl.polishforests.command.FaunaSites;
import pl.polishforests.entity.ModEntities;
import pl.polishforests.entity.base.ForestAlarm;
import pl.polishforests.entity.base.PolishAnimal;
import pl.polishforests.entity.spawn.FaunaHabitat;
import pl.polishforests.entity.spawn.FrogSpawner;
import pl.polishforests.season.SubSeason;
import pl.polishforests.sound.AmbienceRules.Phase;
import pl.polishforests.sound.ForestAmbience;
import pl.polishforests.sound.ForestVoice;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.vanilla.VanillaForestsBiomeSource;

/**
 * The sound part of the fauna game test (step FA4, {@code -Pgametest=fauna -Psites=sound}; docs/04-pionowy-wycinek.md
 * "Projekt FA4"). Numbers only (G.36). Report {@code build/fauna/sound.txt}, log lines {@code [fauna]}. Worlds: "Poland" in
 * both scales (natural mode, the slice seed) and "Default + Polish Forests" ({@code -Pscales=realistic,gameplay,vanilla}
 * picks).
 *
 * <ol>
 * <li><b>Ambience counts</b>: at the oak-hornbeam place of the world (the player creative, invisible and standing), with
 * {@link ForestAmbience#timeScale} {@value #TIME_SCALE} (the intervals divided, their proportions kept), the time stopped
 * at dawn, noon, dusk and midnight of late spring and of January, {@value #SAMPLE_TICKS} ticks each
 * ({@code -PsoundTicks}): the server's counters by part of the day and sub-season. In the gameplay world all eight
 * samples and the thresholds of PW-16 (as {@code ForestAmbienceTest}): dawn at least {@value #DAWN_OVER_DAY} times and dusk
 * at least {@value #DUSK_OVER_DAY} times the events of noon, at night only nocturnal voices, January at most
 * {@value #WINTER_OVER_MAY} times late spring; in the other worlds the dawn of late spring only (events heard, the share of
 * events without a tall trunk).
 * <li><b>Jay</b>: roe deer and wild boars placed near the player (within {@value #NEAR} blocks, so within 32 blocks of every
 * trunk the jay can sit on) and far ({@value #FAR} blocks), then {@link ForestAmbience#screech}: the near animals must hear
 * the {@code JAY} alarm (alert or fleeing), the far ones not. Then the natural chain: the player visible and sprinting
 * through the forest for {@value #RUN_TICKS} ticks must startle at least one jay.
 * <li><b>Frogs</b>: a frog made in an alder carr (spawn egg reason, the variant drawn by vanilla's selection) must be the
 * common frog; then the frogs at an alder carr and an oak-hornbeam forest after {@value #FROG_TICKS} ticks
 * ({@code -PfrogTicks}), by variant and biome: at least one common frog in each, and no vanilla variant in a biome of
 * {@code #polishforests:spawns_common_frog}.
 * </ol>
 */
final class FaunaSound {
	static final int TIME_SCALE = 50;
	static final int SAMPLE_TICKS = 600;
	static final double DAWN_OVER_DAY = 1.5;
	static final double DUSK_OVER_DAY = 1.3;
	static final double WINTER_OVER_MAY = 0.6;
	static final int NEAR = 4;
	static final int FAR = 72;
	static final int RUN_TICKS = 400;
	static final int FROG_TICKS = 2000;
	static final int FROG_RADIUS = 96;
	/** Times of day of the samples: dawn 6:30, noon, dusk 18:30, midnight (as the activity trial of FA2). */
	private static final int[] TIMES = {500, 6000, 12500, 18000};
	private static final ResourceKey<WorldPreset> VANILLA = ResourceKey.create(Registries.WORLD_PRESET, PolishForests.id("default_polish_forests"));
	private static final String VANILLA_SEED = "20261104";

	private final ClientGameTestContext context;
	private final List<String> report = new ArrayList<>();
	private final List<String> failures = new ArrayList<>();
	private final String scales = System.getProperty("polishforests.scales", "");
	private final int sampleTicks = Integer.getInteger("polishforests.sound.ticks", SAMPLE_TICKS);
	private final int frogTicks = Integer.getInteger("polishforests.frog.ticks", FROG_TICKS);

	FaunaSound(ClientGameTestContext context, String sites) {
		this.context = context;
	}

	List<String> run() {
		context.runOnClient(mc -> {
			mc.options.renderDistance().set(10);
			mc.options.simulationDistance().set(10);
		});
		line("sound: time scale %d, %d ticks per sample, frogs after %d ticks; thresholds dawn %.1f, dusk %.1f, January %.1f of the day / late spring",
				TIME_SCALE, sampleTicks, frogTicks, DAWN_OVER_DAY, DUSK_OVER_DAY, WINTER_OVER_MAY);
		try {
			for (boolean real : new boolean[] {false, true}) {
				String scale = real ? "realistic" : "gameplay";
				if (scales.isBlank() || scales.contains(scale)) {
					polandWorld(real, scale + "/natural");
				}
			}
			if (scales.isBlank() || scales.contains("vanilla")) {
				vanillaWorld();
			}
		} finally {
			ForestAmbience.timeScale = 1;
			ForestAmbience.restart();
		}
		write();
		return failures;
	}

	// ------------------------------------------------------------------ worlds

	private void polandWorld(boolean real, String label) {
		ResourceKey<WorldPreset> preset = ResourceKey.create(Registries.WORLD_PRESET, PolishForests.id(real ? "poland" : "poland_gameplay"));
		SliceSites.Place grad = SliceSites.of(real, false).stream().filter(p -> !p.transect() && p.name().equals("oak_hornbeam_1")).findFirst()
				.orElseThrow(() -> new AssertionError("no place oak_hornbeam_1 in slice_sites.txt"));
		try (TestSingleplayerContext sp = context.worldBuilder().adjustSettings(ui -> SliceClientGameTest.select(ui, preset, false)).create()) {
			world(sp, label, grad.x(), grad.z(), !real);
		}
	}

	private void vanillaWorld() {
		try (TestSingleplayerContext sp = context.worldBuilder().adjustSettings(this::selectVanilla).create()) {
			double[] at = sp.getServer().computeOnServer(s -> FaunaSites.locate(s.overworld(), HabitatBiome.OAK_HORNBEAM_FOREST, 0, 0));
			if (at == null) {
				fail("default_polish_forests: no oak-hornbeam forest found");
				return;
			}
			world(sp, "default_polish_forests", (int) Math.floor(at[0]), (int) Math.floor(at[1]), false);
		}
	}

	private void selectVanilla(WorldCreationUiState ui) {
		ui.setSeed(VANILLA_SEED);
		ui.setAllowCommands(true);
		ui.setGenerateStructures(true);
		ui.setWorldType(ui.getNormalPresetList().stream().filter(e -> e.preset() != null && e.preset().is(VANILLA)).findFirst()
				.orElseThrow(() -> new AssertionError("Missing world type " + VANILLA.identifier())));
		if (!(ui.getSettings().selectedDimensions().overworld().getBiomeSource() instanceof VanillaForestsBiomeSource)) {
			throw new AssertionError("The preset " + VANILLA.identifier() + " has no biome source polishforests:vanilla_forests");
		}
	}

	private void world(TestSingleplayerContext sp, String label, int x, int z, boolean full) {
		prepare(sp);
		SeasonDates.checkNewWorldStart(sp, "sound " + label);
		ambience(sp, label, x, z, full);
		jay(sp, label, x, z);
		frogs(sp, label);
	}

	/**
	 * Time stopped, clear weather, mob spawning on (the frogs), the fauna and the ambience on, the player creative and
	 * invisible (the deer do not flee from it, and it raises no running alarm).
	 */
	private static void prepare(TestSingleplayerContext sp) {
		for (String c : new String[] {"gamerule spawn_mobs true", "gamerule advance_time false", "gamerule advance_weather false", "weather clear",
				"difficulty easy", "gamemode creative @a", "gamerule send_command_feedback false", "gamerule polishforests:forest_fauna true",
				"gamerule polishforests:forest_ambience true", "effect give @a minecraft:invisibility infinite 0 true"}) {
			sp.getServer().runCommand(c);
		}
	}

	// ------------------------------------------------------------------ ambience

	private void ambience(TestSingleplayerContext sp, String label, int x, int z, boolean full) {
		FaunaCensus.teleport(sp, x, z);
		context.waitTicks(60);
		ForestAmbience.timeScale = TIME_SCALE;
		List<SubSeason> subs = full ? List.of(SubSeason.LATE_SPRING, SubSeason.MID_WINTER) : List.of(SubSeason.LATE_SPRING);
		long[][] events = new long[subs.size()][TIMES.length];
		long[] dayVoicesAtNight = new long[1];
		for (int si = 0; si < subs.size(); si++) {
			SubSeason sub = subs.get(si);
			SeasonDates.set(sp, sub, 10, "sound " + label);
			for (int ti = 0; ti < TIMES.length; ti++) {
				if (!full && ti > 0) {
					break;
				}
				SeasonDates.setTimeOfDay(sp, TIMES[ti], "sound " + label);
				Phase phase = pl.polishforests.sound.AmbienceRules.phase(TIMES[ti]);
				sp.getServer().runOnServer(s -> {
					ForestAmbience.resetCounters();
					ForestAmbience.restart();
				});
				context.waitTicks(sampleTicks);
				final int fs = si;
				final int ft = ti;
				String detail = sp.getServer().computeOnServer(s -> {
					events[fs][ft] = ForestAmbience.EVENTS[phase.ordinal()][sub.ordinal()].sum();
					Map<String, Long> voices = new TreeMap<>();
					dayVoicesAtNight[0] = 0;
					for (ForestVoice v : ForestVoice.values()) {
						long n = ForestAmbience.VOICES[v.ordinal()][phase.ordinal()].sum();
						if (n > 0) {
							voices.put(v.name().toLowerCase(Locale.ROOT), n);
						}
						if (phase == Phase.NIGHT && !v.nocturnal()) {
							dayVoicesAtNight[0] += n;
						}
					}
					return String.format(Locale.ROOT, "events %d, silent %d, chorus %d, no tree %d, not forest %d, too far %d, day voices at night %d; voices %s",
							events[fs][ft], ForestAmbience.SILENT[phase.ordinal()].sum(), ForestAmbience.CHORUS.sum(), ForestAmbience.NO_TREE.sum(),
							ForestAmbience.NOT_FOREST.sum(), ForestAmbience.TOO_FAR.sum(), dayVoicesAtNight[0], voices);
				});
				line("%s ambience %s %s (time %d): %s", label, sub, phase, TIMES[ti], detail);
				if (phase == Phase.NIGHT && dayVoicesAtNight[0] > 0) {
					fail("%s ambience %s: %d voices of the day at night", label, sub, dayVoicesAtNight[0]);
				}
			}
		}
		ForestAmbience.timeScale = 1;
		sp.getServer().runOnServer(s -> ForestAmbience.restart());
		long dawn = events[0][0];
		if (dawn <= 0) {
			fail("%s ambience: no forest voice at dawn of late spring", label);
		}
		if (!full) {
			return;
		}
		long day = events[0][1];
		long dusk = events[0][2];
		long may = sum(events[0]);
		long january = sum(events[1]);
		line("%s ambience: late spring dawn %d, day %d, dusk %d, night %d (dawn / day %.2f, dusk / day %.2f); January %d against %d (%.2f)", label,
				dawn, day, dusk, events[0][3], dawn / (double) Math.max(1, day), dusk / (double) Math.max(1, day), january, may,
				january / (double) Math.max(1, may));
		if (day <= 0 || dawn < DAWN_OVER_DAY * day || dusk < DUSK_OVER_DAY * day) {
			fail("%s ambience: dawn %d and dusk %d against day %d (at least %.1f and %.1f times)", label, dawn, dusk, day, DAWN_OVER_DAY, DUSK_OVER_DAY);
		}
		if (january > WINTER_OVER_MAY * may || january <= 0) {
			fail("%s ambience: January %d against late spring %d (more than 0, at most %.1f times)", label, january, may, WINTER_OVER_MAY);
		}
		// Back to the start of the world: late spring, noon.
		SeasonDates.set(sp, SubSeason.LATE_SPRING, 10, "sound " + label);
	}

	private static long sum(long[] a) {
		long s = 0;
		for (long v : a) {
			s += v;
		}
		return s;
	}

	// ------------------------------------------------------------------ jay

	private void jay(TestSingleplayerContext sp, String label, int x, int z) {
		FaunaCensus.teleport(sp, x, z);
		SeasonDates.setTimeOfDay(sp, 6000, "sound " + label);
		context.waitTicks(100);
		long heard0 = ForestAlarm.HEARD_BY_KIND[ForestAlarm.Kind.JAY.ordinal()].sum();
		List<PolishAnimal<?>> animals = sp.getServer().computeOnServer(s -> {
			ServerLevel level = s.overworld();
			ServerPlayer p = level.players().getFirst();
			List<PolishAnimal<?>> out = new ArrayList<>();
			out.add(place(level, ModEntities.roeDeer(), p.getBlockX() + NEAR, p.getBlockZ()));
			out.add(place(level, ModEntities.wildBoar(), p.getBlockX() - NEAR, p.getBlockZ()));
			out.add(place(level, ModEntities.roeDeer(), p.getBlockX() + FAR, p.getBlockZ()));
			out.add(place(level, ModEntities.wildBoar(), p.getBlockX(), p.getBlockZ() + FAR));
			return out;
		});
		context.waitTicks(40);
		int heard = sp.getServer().computeOnServer(s -> {
			ServerLevel level = s.overworld();
			int n = -1;
			for (int i = 0; i < 5 && n < 0; i++) {
				n = ForestAmbience.screech(level, level.players().getFirst(), RandomSource.create(4242 + i));
			}
			return n;
		});
		boolean[] reacted = new boolean[animals.size()];
		for (int i = 0; i < 8; i++) {
			context.waitTicks(5);
			sp.getServer().runOnServer(s -> {
				for (int k = 0; k < animals.size(); k++) {
					PolishAnimal<?> a = animals.get(k);
					reacted[k] |= a.isAlive() && (a.isAlerted() || a.isFleeing() || a.action() == PolishAnimal.Action.ALERT);
				}
			});
		}
		long byKind = ForestAlarm.HEARD_BY_KIND[ForestAlarm.Kind.JAY.ordinal()].sum() - heard0;
		line("%s jay screech: heard by %d (JAY alarms heard %d); near roe deer %s, near boar %s, far roe deer (%d blocks) %s, far boar %s", label,
				heard, byKind, reacted[0], reacted[1], FAR, reacted[2], reacted[3]);
		if (heard < 0) {
			fail("%s jay: no tall trunk within %d-%d blocks", label, ForestAmbience.JAY_MIN_DISTANCE, ForestAmbience.JAY_MAX_DISTANCE);
		} else if (!reacted[0] || !reacted[1] || reacted[2] || reacted[3] || byKind < 2) {
			fail("%s jay: near deer %s, near boar %s (both must react), far deer %s, far boar %s (neither), heard %d", label, reacted[0], reacted[1],
					reacted[2], reacted[3], byKind);
		}
		sp.getServer().runOnServer(s -> animals.forEach(Entity::discard));

		// The natural chain: a visible player sprinting through the forest startles a jay.
		long screeches0 = ForestAmbience.JAY_SCREECHES.sum();
		sp.getServer().runCommand("effect clear @a minecraft:invisibility");
		sp.getServer().runCommand("gamemode survival @a");
		for (int t = 0; t < RUN_TICKS; t += 5) {
			sp.getServer().runOnServer(s -> s.overworld().players().getFirst().setSprinting(true));
			context.waitTicks(5);
		}
		sp.getServer().runOnServer(s -> s.overworld().players().getFirst().setSprinting(false));
		sp.getServer().runCommand("gamemode creative @a");
		sp.getServer().runCommand("effect give @a minecraft:invisibility infinite 0 true");
		long screeches = ForestAmbience.JAY_SCREECHES.sum() - screeches0;
		line("%s jay chain: %d screeches in %d ticks of sprinting (chance %.2f every %d ticks, cooldown %d-%d)", label, screeches, RUN_TICKS,
				ForestAmbience.JAY_CHANCE, ForestAmbience.JAY_CHECK, ForestAmbience.JAY_COOLDOWN, ForestAmbience.JAY_COOLDOWN_MAX);
		if (screeches < 1) {
			fail("%s jay chain: no screech in %d ticks of sprinting through the forest", label, RUN_TICKS);
		}
	}

	private static <T extends PolishAnimal<?>> PolishAnimal<?> place(ServerLevel level, EntityType<T> type, int x, int z) {
		Vec3 at = FaunaCensus.trialSpot(level, type, x, z);
		T a = type.create(level, EntitySpawnReason.COMMAND);
		a.snapTo(at.x, at.y, at.z, 0, 0);
		level.addFreshEntity(a);
		return a;
	}

	// ------------------------------------------------------------------ frogs

	private void frogs(TestSingleplayerContext sp, String label) {
		for (HabitatBiome b : List.of(HabitatBiome.ALDER_CARR, HabitatBiome.OAK_HORNBEAM_FOREST)) {
			double[] at = sp.getServer().computeOnServer(s -> FaunaSites.locate(s.overworld(), b, 0, 0));
			if (at == null) {
				fail("%s frogs: no %s found", label, b.id());
				continue;
			}
			int x = (int) Math.floor(at[0]);
			int z = (int) Math.floor(at[1]);
			FaunaCensus.teleport(sp, x, z);
			context.waitTicks(60);
			String made = sp.getServer().computeOnServer(s -> madeFrog(s.overworld(), x, z));
			line("%s frogs %s at %d %d: a frog made here is %s", label, b.id(), x, z, made);
			if (b == HabitatBiome.ALDER_CARR && !made.startsWith(FrogSpawner.COMMON_FROG.identifier().toString())) {
				fail("%s frogs: a frog made in %s is %s, not the common frog", label, b.id(), made);
			}
			long spawned0 = FrogSpawner.SPAWNED.sum();
			context.waitTicks(frogTicks);
			FrogCount c = sp.getServer().computeOnServer(s -> count(s.overworld(), new Vec3(x + 0.5, 0, z + 0.5)));
			line("%s frogs %s after %d ticks within %d blocks: by variant %s, by biome %s; vanilla variants in the tagged biomes %d; spawned by the"
					+ " frog spawner %d", label, b.id(), frogTicks, FROG_RADIUS, c.byVariant, c.byBiome, c.vanillaInTag, FrogSpawner.SPAWNED.sum() - spawned0);
			if (c.common < 1) {
				fail("%s frogs: no common frog within %d blocks of %s after %d ticks", label, FROG_RADIUS, b.id(), frogTicks);
			}
			if (c.vanillaInTag > 0) {
				fail("%s frogs: %d frogs of a vanilla variant in biomes of #%s", label, c.vanillaInTag, FrogSpawner.SPAWNS_COMMON_FROG.location());
			}
		}
	}

	/** The variant and biome of a frog made at a column the way a spawn egg makes it (vanilla's variant selection). */
	private static String madeFrog(ServerLevel level, int x, int z) {
		Vec3 at = FaunaCensus.trialSpot(level, EntityTypes.FROG, x, z);
		Frog f = EntityTypes.FROG.create(level, EntitySpawnReason.SPAWN_ITEM_USE);
		f.snapTo(at.x, at.y, at.z, 0, 0);
		f.finalizeSpawn(level, level.getCurrentDifficultyAt(f.blockPosition()), EntitySpawnReason.SPAWN_ITEM_USE, null);
		String variant = variant(f);
		String biome = biome(level, f.blockPosition());
		f.discard();
		return variant + " in " + biome;
	}

	private record FrogCount(Map<String, Integer> byVariant, Map<String, Integer> byBiome, int common, int vanillaInTag) {
	}

	private static FrogCount count(ServerLevel level, Vec3 at) {
		Map<String, Integer> byVariant = new TreeMap<>();
		Map<String, Integer> byBiome = new TreeMap<>();
		int common = 0;
		int vanillaInTag = 0;
		double r = FROG_RADIUS;
		for (Frog f : level.getEntities(EntityTypes.FROG, new AABB(at.x - r, level.getMinY(), at.z - r, at.x + r, level.getMaxY(), at.z + r),
				f -> f.distanceToSqr(at.x, f.getY(), at.z) <= r * r)) {
			String v = variant(f);
			byVariant.merge(v, 1, Integer::sum);
			byBiome.merge(biome(level, f.blockPosition()) + " " + v.substring(v.indexOf(':') + 1), 1, Integer::sum);
			boolean isCommon = FrogSpawner.isCommonFrog(f);
			if (isCommon) {
				common++;
			} else if (level.getBiome(f.blockPosition()).is(FrogSpawner.SPAWNS_COMMON_FROG)) {
				vanillaInTag++;
			}
		}
		return new FrogCount(byVariant, byBiome, common, vanillaInTag);
	}

	private static String variant(Frog f) {
		return f.getVariant().unwrapKey().map(k -> k.identifier().toString()).orElse("?");
	}

	private static String biome(ServerLevel level, BlockPos pos) {
		@Nullable HabitatBiome h = FaunaHabitat.of(level.getBiome(pos));
		return h != null ? h.id() : level.getBiome(pos).unwrapKey().map(k -> k.identifier().toString()).orElse("?");
	}

	// ------------------------------------------------------------------ report

	void line(String format, Object... args) {
		String s = String.format(Locale.ROOT, format, args);
		report.add(s);
		PolishForests.LOG.info("[fauna] " + s);
	}

	void fail(String format, Object... args) {
		String s = String.format(Locale.ROOT, format, args);
		failures.add(s);
		line("FAIL " + s);
	}

	private void write() {
		Path out = Path.of(System.getProperty("polishforests.fauna.out", "build/fauna"));
		try {
			Files.createDirectories(out);
			Files.writeString(out.resolve("sound.txt"), String.join("\n", report) + "\n", StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
	}
}
