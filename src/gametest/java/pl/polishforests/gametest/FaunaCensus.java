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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import pl.polishforests.PolishForests;
import pl.polishforests.command.FaunaCommand;
import pl.polishforests.command.FaunaSites;
import pl.polishforests.entity.ModEntities;
import pl.polishforests.entity.base.PolishAnimal;
import pl.polishforests.entity.roedeer.RoeDeer;
import pl.polishforests.entity.spawn.FaunaSpawner;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.vanilla.VanillaForestsBiomeSource;

/**
 * The census of the fauna game test (step FA1, {@code -Pgametest=fauna}, default {@code -Psites=census}; docs/04
 * "Projekt FA1"). Numbers only, no screenshots (G.36 U-1). Report {@code build/fauna/census.txt}, lines {@code [fauna]}.
 *
 * <ol>
 * <li><b>Forests of the slice</b>: in the "Poland" worlds of both scales and both vegetation modes (seed of the slice,
 * places of {@code slice_sites.txt}: {@code oak_hornbeam_1} and {@code fresh_pine_1}, with {@code -Psites=census,all} all
 * four) and in "Default + Polish Forests" (an oak-hornbeam and a fresh pine forest found by the search of
 * {@code find biome}): the player (creative, invisible, so the deer do not flee from it) stands {@code minutes} (default
 * {@value #DEFAULT_MINUTES}, {@code -PfaunaMinutes}) at the place, at noon; every fifth of that time the roe deer within
 * {@value #RADIUS} blocks are counted, and every roe deer within 160 blocks is checked: none on leaves or above the ground
 * of {@code MOTION_BLOCKING_NO_LEAVES}, none in water, none inside blocks; the ground under them, the share under a canopy
 * and the counters of the spawners (places refused by reason) go to the report. Each place must have on average more than
 * 1 roe deer within {@value #RADIUS} blocks over the last three counts (KFA-5; FA1 asked for more than 0).
 * <li><b>Exploration</b> (gameplay scale, natural mode): {@value #TRIPS} places 512 blocks apart, {@value #TRIP_TICKS}
 * ticks each: the roe deer in the loaded chunks per loaded chunk must not grow (last place at most 1.5 times the first
 * two plus 0.02).
 * <li><b>Despawn</b>: a roe deer of the fauna spawner (spawn reason {@code NATURAL}) placed 140 blocks from the player
 * despawns unless named or persistent, one at 100 blocks stays; roe deer from a spawn egg, the chunk generation and a
 * command with NBT (no {@code finalizeSpawn}) placed 140 blocks away stay.
 * <li><b>Flight</b>: a roe deer placed 12 blocks from a visible standing player runs to beyond 20 blocks.
 * <li><b>Vanilla animals</b> (KFA-4): two fresh gameplay worlds of the same seed, one with {@code forest_fauna} off: wolves,
 * foxes and rabbits within 96 blocks of two places after the same time; the difference is reported, and fails only when
 * the world with the fauna has clearly fewer (less than half of the other minus 3).
 * <li><b>Behaviour</b> (step FA2, {@link FaunaTrials}): in every world, at its first place, after the census: activity by
 * time of day, daily range, groups in winter and summer, coat and antlers by sub-season (all 12 in the gameplay world of
 * the natural mode, 4 elsewhere), flight distance and the alarm. {@code -Psites=behaviour} runs only these (without the
 * census of the places and the trials of FA1), {@code -Psites=mspt} only the MSPT scene (KFA-6) in the gameplay world of
 * the natural mode.
 * </ol>
 *
 * <p>Step FA2 counts in each place also the groups and the looks of the roe deer (the census line of the fauna command), and
 * the census needs on average at least 1 roe deer within {@value #RADIUS} blocks over the last three counts.
 */
final class FaunaCensus {
	static final int RADIUS = 64;
	static final double DEFAULT_MINUTES = 5;
	static final int TRIPS = 8;
	static final int TRIP_TICKS = 600;
	private static final int CHECK_RADIUS = 160;
	/** Ticks after a teleport before a trial places its animals (the chunks around load). */
	private static final int TRIAL_SETTLE = 200;
	private static final ResourceKey<WorldPreset> VANILLA = ResourceKey.create(Registries.WORLD_PRESET,
			PolishForests.id("default_polish_forests"));
	private static final String VANILLA_SEED = "20261104";

	private final ClientGameTestContext context;
	private final List<String> report = new ArrayList<>();
	private final List<String> failures = new ArrayList<>();
	private final int ticks;
	private final boolean allSites;
	private final String scales = System.getProperty("polishforests.scales", "");
	private final String modes = System.getProperty("polishforests.modes", "");
	/** Step FA2: the census of the places with the trials of FA1, the behaviour trials, the MSPT scene. */
	private final boolean places;
	private final boolean behaviour;
	private final boolean mspt;
	private final FaunaTrials trials;
	/** The sub-seasons of the looks trial: all in the gameplay world of the natural mode, these four elsewhere. */
	static final List<pl.polishforests.season.SubSeason> LOOK_SEASONS = List.of(pl.polishforests.season.SubSeason.MID_SUMMER,
			pl.polishforests.season.SubSeason.MID_AUTUMN, pl.polishforests.season.SubSeason.LATE_AUTUMN,
			pl.polishforests.season.SubSeason.MID_WINTER);

	FaunaCensus(ClientGameTestContext context, String sites) {
		this.context = context;
		double minutes = Double.parseDouble(System.getProperty("polishforests.fauna.minutes", String.valueOf(DEFAULT_MINUTES)));
		this.ticks = Math.max(100, (int) Math.round(minutes * 1200));
		this.allSites = sites.contains("all");
		boolean only = sites.contains("behaviour") || sites.contains("mspt");
		this.places = !only;
		this.behaviour = !only || sites.contains("behaviour");
		this.mspt = sites.contains("mspt");
		this.trials = new FaunaTrials(context, this);
	}

	/** Runs every part and returns the failures (the report is written to build/fauna/census.txt). */
	List<String> run() {
		// Render and simulation distance 12 chunks: the despawn check places deer 140 blocks away on entity-ticking chunks.
		context.runOnClient(mc -> {
			mc.options.renderDistance().set(12);
			mc.options.simulationDistance().set(12);
		});
		line("census: %d ticks per place, radius %d, densities per block: generation %.6f (real %.3f/ha × %.0f), spawner %.6f (× %.0f);"
				+ " places %s, behaviour %s, mspt %s", ticks, RADIUS, RoeDeer.PROFILE.generationDensityPerBlock(), RoeDeer.PROFILE.realDensity(),
				RoeDeer.PROFILE.generationFactor(), RoeDeer.PROFILE.spawnerDensityPerBlock(), RoeDeer.PROFILE.spawnerFactor(), places, behaviour,
				mspt);
		if (mspt && !places && !behaviour) {
			msptWorld();
			write();
			return failures;
		}
		for (boolean real : new boolean[] {true, false}) {
			String scale = real ? "realistic" : "gameplay";
			if (!scales.isBlank() && !scales.contains(scale)) {
				continue;
			}
			for (boolean presentDay : new boolean[] {false, true}) {
				String mode = presentDay ? "present_day" : "natural";
				if (!modes.isBlank() && !modes.contains(mode)) {
					continue;
				}
				polandWorld(real, presentDay, scale + "/" + mode);
			}
		}
		if (scales.isBlank() || scales.contains("vanilla")) {
			vanillaWorld();
		}
			if (places && (scales.isBlank() || scales.contains("gameplay"))) {
			vanillaAnimals();
		}
		if (mspt) {
			msptWorld();
		}
		write();
		return failures;
	}

	// ------------------------------------------------------------------ worlds

	private void polandWorld(boolean real, boolean presentDay, String label) {
		ResourceKey<WorldPreset> preset = ResourceKey.create(Registries.WORLD_PRESET, PolishForests.id(real ? "poland" : "poland_gameplay"));
		List<SliceSites.Place> places = SliceSites.of(real, presentDay).stream().filter(p -> !p.transect())
				.filter(p -> allSites || p.name().endsWith("_1")).toList();
		resetCounters();
		try (TestSingleplayerContext sp = context.worldBuilder().adjustSettings(ui -> SliceClientGameTest.select(ui, preset, presentDay))
				.create()) {
			prepare(sp, true);
			if (this.places) {
				for (SliceSites.Place p : places) {
					site(sp, label + " " + p.name() + " (" + p.biome() + ")", p.x(), p.z());
				}
			}
			SliceSites.Place first = places.getFirst();
			if (behaviour) {
				// All 12 sub-seasons of the looks in the gameplay world of the natural mode, 4 elsewhere.
				trials.all(sp, label, first.x(), first.z(), !real && !presentDay ? List.of(pl.polishforests.season.SubSeason.values())
						: LOOK_SEASONS);
			}
			if (this.places && !real && !presentDay) {
				despawn(sp, first.x(), first.z());
				flight(sp, first.x(), first.z());
				exploration(sp, first.x(), first.z());
			}
			counters(label);
		}
	}

	private void vanillaWorld() {
		resetCounters();
		try (TestSingleplayerContext sp = context.worldBuilder().adjustSettings(this::selectVanilla).create()) {
			prepare(sp, true);
			boolean first = true;
			for (HabitatBiome b : List.of(HabitatBiome.OAK_HORNBEAM_FOREST, HabitatBiome.FRESH_PINE_FOREST)) {
				double[] at = sp.getServer().computeOnServer(s -> FaunaSites.locate(s.overworld(), b, 0, 0));
				if (at == null) {
					fail("default_polish_forests: %s not found", b.id());
					continue;
				}
				// The nearest column of the biome on the search lattice (find biome): the count circle covers its forest.
				int x = (int) Math.floor(at[0]);
				int z = (int) Math.floor(at[1]);
				if (places) {
					site(sp, "default_polish_forests " + b.id(), x, z);
				}
				if (behaviour && first) {
					trials.all(sp, "default_polish_forests " + b.id(), x, z, LOOK_SEASONS);
				}
				first = false;
			}
			counters("default_polish_forests");
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

	/**
	 * Time stopped at noon, clear weather, the player creative and invisible (the deer see no threat in it), and mob
	 * spawning on: the consistent settings of the Fabric test world builder turn {@code spawn_mobs} off, which stops
	 * vanilla's spawns (also at chunk generation) and the fauna spawners of the mod alike.
	 */
	private static void prepare(TestSingleplayerContext sp, boolean fauna) {
		for (String c : new String[] {"gamerule spawn_mobs true", "gamerule advance_time false", "gamerule advance_weather false", "time set 6000", "weather clear",
				"difficulty easy", "gamemode creative @a", "gamerule send_command_feedback false",
				"gamerule polishforests:forest_fauna " + fauna, "effect give @a minecraft:invisibility infinite 0 true"}) {
			sp.getServer().runCommand(c);
		}
	}

	static void teleport(TestSingleplayerContext sp, int x, int z) {
		// Load the chunk first: the height of a column in a chunk that is not loaded is the bottom of the world, and a player
		// teleported there stands 200 blocks under the deer (and makes every one of them despawn).
		int y = sp.getServer().computeOnServer(s -> {
			s.overworld().getChunk(x >> 4, z >> 4);
			return s.overworld().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		});
		sp.getServer().runCommand(String.format(Locale.ROOT, "tp @a %d.5 %d %d.5", x, y + 1, z));
	}

	// ------------------------------------------------------------------ one place

	private void site(TestSingleplayerContext sp, String label, int x, int z) {
		teleport(sp, x, z);
		context.waitTicks(40);
		int samples = 5;
		int[] counts = new int[samples];
		Map<String, Integer> grounds = new TreeMap<>();
		int checked = 0;
		int underCanopy = 0;
		for (int i = 0; i < samples; i++) {
			context.waitTicks(ticks / samples);
			Sample s = sp.getServer().computeOnServer(server -> sample(server.overworld(), new Vec3(x + 0.5, 0, z + 0.5)));
			counts[i] = s.near;
			checked += s.checked;
			underCanopy += s.underCanopy;
			s.grounds.forEach((k, v) -> grounds.merge(k, v, Integer::sum));
			for (String bad : s.bad) {
				fail("%s: %s", label, bad);
			}
		}
		double lastMean = (counts[samples - 1] + counts[samples - 2] + counts[samples - 3]) / 3.0;
		String census = sp.getServer().computeOnServer(server -> censusLine(server.overworld(), x, z));
		line("%s at %d %d: roe deer within %d blocks %s, mean of the last three %.2f; checked %d, under a canopy %d; ground %s; %s",
				label, x, z, RADIUS, java.util.Arrays.toString(counts), lastMean, checked, underCanopy, grounds, census);
		if (lastMean < 1) {
			fail("%s: on average %.2f roe deer within %d blocks after %d ticks (at least 1)", label, lastMean, RADIUS, ticks);
		}
	}

	/** What a sample counts: deer near the place, the checked deer, the bad ones, the ground under them. */
	private static final class Sample {
		int near;
		int checked;
		int underCanopy;
		final List<String> bad = new ArrayList<>();
		final Map<String, Integer> grounds = new TreeMap<>();
	}

	private static Sample sample(ServerLevel level, Vec3 at) {
		Sample s = new Sample();
		AABB box = new AABB(at.x - CHECK_RADIUS, level.getMinY(), at.z - CHECK_RADIUS, at.x + CHECK_RADIUS, level.getMaxY(),
				at.z + CHECK_RADIUS);
		for (RoeDeer d : level.getEntities(ModEntities.roeDeer(), box, e -> e.isAlive())) {
			double dx = d.getX() - at.x;
			double dz = d.getZ() - at.z;
			if (dx * dx + dz * dz <= RADIUS * RADIUS) {
				s.near++;
			}
			s.checked++;
			BlockPos feet = d.blockPosition();
			BlockPos below = BlockPos.containing(d.getX(), d.getY() - 0.2, d.getZ());
			int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, feet.getX(), feet.getZ());
			String where = String.format(Locale.ROOT, "roe deer at %.1f %.1f %.1f", d.getX(), d.getY(), d.getZ());
			if (level.getBlockState(below).is(BlockTags.LEAVES) || d.getY() > ground + 1.5) {
				s.bad.add(where + " on leaves or above the ground (ground Y " + ground + ")");
			}
			if (d.isInWater()) {
				s.bad.add(where + " in water");
			}
			if (d.isInWall()) {
				s.bad.add(where + " inside a block");
			}
			if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, feet.getX(), feet.getZ()) > feet.getY() + 2) {
				s.underCanopy++;
			}
			s.grounds.merge(BuiltInRegistries.BLOCK.getKey(level.getBlockState(below).getBlock()).getPath(), 1, Integer::sum);
		}
		return s;
	}

	/** The fauna command's census around the place: mod and vanilla animals by species and state. */
	private static String censusLine(ServerLevel level, int x, int z) {
		FaunaCommand.Census c = FaunaCommand.census(level, new Vec3(x + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z),
				z + 0.5), RADIUS);
		StringBuilder b = new StringBuilder("fauna " + RADIUS + ": " + c.total);
		c.lines.forEach((type, line) -> b.append(", ").append(BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath()).append(' ')
				.append(line.count).append(line.states.isEmpty() ? "" : " " + line.states)
				.append(line.mod ? " groups " + line.groupSizes() + " looks " + line.looks : ""));
		return b.toString();
	}

	// ------------------------------------------------------------------ exploration, despawn, flight

	private void exploration(TestSingleplayerContext sp, int x0, int z0) {
		double[] perChunk = new double[TRIPS];
		int[] loaded = new int[TRIPS];
		for (int i = 0; i < TRIPS; i++) {
			teleport(sp, x0 + 512 * (i + 1), z0);
			context.waitTicks(TRIP_TICKS);
			int[] n = sp.getServer().computeOnServer(s -> new int[] {s.overworld().getEntities(ModEntities.roeDeer(), e -> e.isAlive()).size(),
					s.overworld().getChunkSource().getLoadedChunksCount()});
			loaded[i] = n[0];
			perChunk[i] = n[0] / (double) Math.max(1, n[1]);
		}
		line("exploration: %d places 512 blocks apart, %d ticks each: loaded roe deer %s, per loaded chunk %s", TRIPS, TRIP_TICKS,
				java.util.Arrays.toString(loaded), fmt(perChunk));
		double start = Math.max(perChunk[0], perChunk[1]);
		if (perChunk[TRIPS - 1] > 1.5 * start + 0.02) {
			fail("exploration: roe deer per loaded chunk grew from %.3f to %.3f", start, perChunk[TRIPS - 1]);
		}
	}

	private void despawn(TestSingleplayerContext sp, int x, int z) {
		teleport(sp, x, z);
		// The previous place is far away: the chunks within 140 blocks must load (and get their simulation ticket) first.
		context.waitTicks(TRIAL_SETTLE);
		List<RoeDeer> deer = sp.getServer().computeOnServer(s -> {
			ServerLevel level = s.overworld();
			ServerPlayer p = level.players().getFirst();
			List<RoeDeer> out = new ArrayList<>();
			// Spawner deer: plain, named and persistent at 140 blocks, plain at 100; then a spawn egg, the chunk generation and
			// a command with NBT (no finalizeSpawn) at 140 blocks.
			int[][] offsets = {{140, 0}, {0, 140}, {-140, 0}, {100, 0}, {0, -140}, {99, 99}, {-99, -99}};
			EntitySpawnReason[] reasons = {EntitySpawnReason.NATURAL, EntitySpawnReason.NATURAL, EntitySpawnReason.NATURAL,
					EntitySpawnReason.NATURAL, EntitySpawnReason.SPAWN_ITEM_USE, EntitySpawnReason.CHUNK_GENERATION, null};
			for (int i = 0; i < offsets.length; i++) {
				RoeDeer d = ModEntities.roeDeer().create(level, reasons[i] == null ? EntitySpawnReason.COMMAND : reasons[i]);
				Vec3 at = trialSpot(level, p.getBlockX() + offsets[i][0], p.getBlockZ() + offsets[i][1]);
				d.snapTo(at.x, at.y, at.z, 0, 0);
				if (reasons[i] != null) {
					d.finalizeSpawn(level, level.getCurrentDifficultyAt(d.blockPosition()), reasons[i], null);
				}
				if (i == 1) {
					d.setCustomName(Component.literal("named"));
				} else if (i == 2) {
					d.setPersistenceRequired();
				}
				level.addFreshEntity(d);
				out.add(d);
			}
			return out;
		});
		context.waitTicks(60);
		StringBuilder why = new StringBuilder();
		boolean[] removed = sp.getServer().computeOnServer(s -> {
			ServerPlayer p = s.overworld().players().getFirst();
			boolean[] r = new boolean[deer.size()];
			for (int i = 0; i < r.length; i++) {
				RoeDeer d = deer.get(i);
				r[i] = d.isRemoved();
				why.append(String.format(Locale.ROOT, " [%.0f blocks, %s, ticking %s, health %.0f]", d.distanceTo(p),
						d.getRemovalReason() == null ? "present" : d.getRemovalReason().name().toLowerCase(Locale.ROOT),
						s.overworld().isPositionEntityTicking(d.blockPosition()), d.getHealth()));
			}
			return r;
		});
		line("despawn details:%s", why);
		line("despawn: spawner deer plain at 140 removed %s, named at 140 removed %s, persistent at 140 removed %s, plain at 100"
				+ " removed %s; at 140 egg removed %s, chunk generation removed %s, command with NBT removed %s", removed[0], removed[1],
				removed[2], removed[3], removed[4], removed[5], removed[6]);
		boolean ok = removed[0];
		for (int i = 1; i < removed.length; i++) {
			ok &= !removed[i];
		}
		if (!ok) {
			fail("despawn: expected only the plain spawner deer at 140 blocks to despawn, removed %s", java.util.Arrays.toString(removed));
		}
		sp.getServer().runOnServer(s -> deer.forEach(Entity::discard));
	}

	private void flight(TestSingleplayerContext sp, int x, int z) {
		teleport(sp, x, z);
		context.waitTicks(TRIAL_SETTLE);
		sp.getServer().runCommand("effect clear @a minecraft:invisibility");
		RoeDeer deer = sp.getServer().computeOnServer(s -> {
			ServerLevel level = s.overworld();
			ServerPlayer p = level.players().getFirst();
			Vec3 at = trialSpot(level, p.getBlockX() + 12, p.getBlockZ());
			RoeDeer d = ModEntities.roeDeer().create(level, EntitySpawnReason.COMMAND);
			d.snapTo(at.x, at.y, at.z, 0, 0);
			level.addFreshEntity(d);
			return d;
		});
		boolean[] fled = {false};
		double[] max = {0};
		for (int i = 0; i < 10; i++) {
			context.waitTicks(10);
			sp.getServer().runOnServer(s -> {
				ServerPlayer p = s.overworld().players().getFirst();
				fled[0] |= deer.state() == PolishAnimal.State.FLEEING;
				max[0] = Math.max(max[0], deer.distanceTo(p));
			});
		}
		String seen = sp.getServer().computeOnServer(s -> {
			ServerPlayer p = s.overworld().players().getFirst();
			var visible = deer.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES);
			return String.format(Locale.ROOT, "player %s seen %s, deer at %.1f %.1f %.1f, state %s, removed %s", p.gameMode().getName(),
					visible.map(v -> v.contains(p)).orElse(false), deer.getX(), deer.getY(), deer.getZ(), deer.state().id(), deer.isRemoved());
		});
		line("flight: a roe deer 12 blocks from a visible standing player: fleeing seen %s, largest distance %.1f in 100 ticks; %s",
				fled[0], max[0], seen);
		if (!fled[0] || max[0] < 20) {
			fail("flight: the roe deer did not flee (fleeing %s, largest distance %.1f)", fled[0], max[0]);
		}
		sp.getServer().runOnServer(s -> deer.discard());
		sp.getServer().runCommand("effect give @a minecraft:invisibility infinite 0 true");
	}

	/**
	 * A valid place of a roe deer (the predicate of the spawners, not counted) on the nearest column to the given one within
	 * 16 blocks, so a trial animal never stands on a crown or in water; the column itself when there is none.
	 */
	static Vec3 trialSpot(ServerLevel level, int x, int z) {
		for (int r = 0; r <= 16; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					level.getChunk((x + dx) >> 4, (z + dz) >> 4);
					Vec3 at = FaunaSpawner.placeUncounted(level, ModEntities.roeDeer(), x + dx, z + dz);
					if (at != null) {
						return at;
					}
				}
			}
		}
		return new Vec3(x + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z + 0.5);
	}

	// ------------------------------------------------------------------ vanilla animals with and without the fauna

	private void vanillaAnimals() {
		List<SliceSites.Place> places = SliceSites.of(false, false).stream().filter(p -> !p.transect() && p.name().endsWith("_1")).toList();
		ResourceKey<WorldPreset> preset = ResourceKey.create(Registries.WORLD_PRESET, PolishForests.id("poland_gameplay"));
		int vanillaTicks = Math.max(100, ticks / 2);
		int[] totals = new int[2];
		for (int k = 0; k < 2; k++) {
			boolean fauna = k == 0;
			try (TestSingleplayerContext sp = context.worldBuilder().adjustSettings(ui -> SliceClientGameTest.select(ui, preset, false))
					.create()) {
				prepare(sp, fauna);
				StringBuilder b = new StringBuilder();
				for (SliceSites.Place p : places) {
					teleport(sp, p.x(), p.z());
					context.waitTicks(vanillaTicks);
					Map<String, Integer> n = sp.getServer().computeOnServer(s -> vanillaCount(s.overworld(), p.x(), p.z()));
					b.append(' ').append(p.name()).append(' ').append(n);
					totals[k] += n.values().stream().mapToInt(Integer::intValue).sum();
				}
				line("vanilla animals within 96 blocks, forest_fauna %s, %d ticks per place:%s", fauna, vanillaTicks, b);
			}
		}
		line("vanilla animals: with the fauna %d, without %d (difference %d)", totals[0], totals[1], totals[0] - totals[1]);
		if (totals[0] < totals[1] / 2.0 - 3) {
			fail("vanilla animals: clearly fewer with the fauna of the mod (%d against %d)", totals[0], totals[1]);
		}
	}

	private static Map<String, Integer> vanillaCount(ServerLevel level, int x, int z) {
		Map<String, Integer> out = new TreeMap<>();
		AABB box = new AABB(x - 96, level.getMinY(), z - 96, x + 96, level.getMaxY(), z + 96);
		for (EntityType<?> t : List.of(EntityTypes.WOLF, EntityTypes.FOX, EntityTypes.RABBIT, EntityTypes.FROG)) {
			out.put(BuiltInRegistries.ENTITY_TYPE.getKey(t).getPath(), level.getEntities(t, box, e -> e.isAlive()).size());
		}
		return out;
	}

	// ------------------------------------------------------------------ counters and report

	private static void resetCounters() {
		for (var a : FaunaSpawner.REASONS) {
			a.reset();
		}
		FaunaSpawner.GENERATION_ANIMALS.reset();
		FaunaSpawner.GENERATION_GROUPS.reset();
		FaunaSpawner.SPAWNER_ANIMALS.reset();
		FaunaSpawner.SPAWNER_GROUPS.reset();
		FaunaSpawner.YOUNG_GROUPS.reset();
		FaunaSpawner.GROUND_REFUSED.clear();
		pl.polishforests.entity.base.ForestAlarm.RAISED.reset();
		pl.polishforests.entity.base.ForestAlarm.HEARD.reset();
		RoeDeer.BARKS.reset();
	}

	private void counters(String label) {
		StringBuilder b = new StringBuilder();
		for (FaunaSpawner.Reason r : FaunaSpawner.Reason.values()) {
			b.append(' ').append(r.name().toLowerCase(Locale.ROOT)).append(' ').append(FaunaSpawner.REASONS[r.ordinal()].sum());
		}
		Map<String, Long> refused = new TreeMap<>();
		FaunaSpawner.GROUND_REFUSED.forEach((k, v) -> refused.put(k, v.sum()));
		line("%s spawners: at generation %d animals in %d groups, spawner %d animals in %d groups, %d groups with young; places:%s;"
				+ " ground refused %s; alarms raised %d, heard %d, barks %d", label, FaunaSpawner.GENERATION_ANIMALS.sum(),
				FaunaSpawner.GENERATION_GROUPS.sum(), FaunaSpawner.SPAWNER_ANIMALS.sum(), FaunaSpawner.SPAWNER_GROUPS.sum(),
				FaunaSpawner.YOUNG_GROUPS.sum(), b, refused, pl.polishforests.entity.base.ForestAlarm.RAISED.sum(),
				pl.polishforests.entity.base.ForestAlarm.HEARD.sum(), RoeDeer.BARKS.sum());
	}

	private static String fmt(double[] v) {
		StringBuilder b = new StringBuilder("[");
		for (int i = 0; i < v.length; i++) {
			b.append(i > 0 ? ", " : "").append(String.format(Locale.ROOT, "%.3f", v[i]));
		}
		return b.append(']').toString();
	}

	/** The MSPT scene (step FA2, KFA-6) in a gameplay world of the natural mode at the first oak-hornbeam place. */
	private void msptWorld() {
		ResourceKey<WorldPreset> preset = ResourceKey.create(Registries.WORLD_PRESET, PolishForests.id("poland_gameplay"));
		SliceSites.Place first = SliceSites.of(false, false).stream().filter(p -> !p.transect() && p.name().endsWith("_1")).findFirst()
				.orElseThrow();
		try (TestSingleplayerContext sp = context.worldBuilder().adjustSettings(ui -> SliceClientGameTest.select(ui, preset, false))
				.create()) {
			prepare(sp, true);
			// The spawners off during the timing (only the 40 trial deer and those of the generation tick).
			sp.getServer().runCommand("gamerule polishforests:forest_fauna false");
			trials.mspt(sp, "gameplay/natural " + first.name(), first.x(), first.z());
		}
	}

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
			Files.writeString(out.resolve("census.txt"), String.join("\n", report) + "\n", StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new java.io.UncheckedIOException(e);
		}
	}
}
