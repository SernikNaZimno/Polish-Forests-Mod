package pl.polskielasy.gametest;

import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.InactivityFpsLimit;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import pl.polskielasy.PolskieLasy;
import pl.polskielasy.worldgen.chunk.PolskaChunkGenerator;

/**
 * Porównanie wydajności: średni FPS i czas wczytania nowego terenu w świecie wanilijnym
 * i w świecie "Polska", przy tych samych ustawieniach graficznych i tym samym ziarnie.
 * Uruchamiany, gdy {@code -Dpolskielasy.gametest} to {@code wydajnosc} lub {@code wszystko}.
 * Tryb {@code etapy} mierzy tylko czasy etapów generacji w trzech obszarach każdej skali (punkt
 * odniesienia M2, {@code docs/m2/pomiary-bazowe-m1.md}), bez pomiaru FPS i bez świata wanilijnego.
 */
public final class PerformanceClientGameTest implements FabricClientGameTest {
	private static final String SEED = "20260927";
	private static final int RENDER_DISTANCE = 12;
	private static final ResourceKey<WorldPreset> POLSKA = ResourceKey.create(Registries.WORLD_PRESET,
			PolskieLasy.id("polska"));
	private static final ResourceKey<WorldPreset> POLSKA_GAMEPLAY = ResourceKey.create(Registries.WORLD_PRESET,
			PolskieLasy.id("polska_rozgrywka"));

	@Override
	public void runTest(ClientGameTestContext context) {
		String mode = System.getProperty("polskielasy.gametest", "wszystko");
		if (!mode.equals("wydajnosc") && !mode.equals("wszystko") && !mode.equals("etapy")) {
			return;
		}
		boolean stagesOnly = mode.equals("etapy");
		context.runOnClient(mc -> {
			mc.options.renderDistance().set(RENDER_DISTANCE);
			mc.options.simulationDistance().set(8);
			mc.options.framerateLimit().set(260);
			mc.options.enableVsync().set(false);
			mc.options.inactivityFpsLimit().set(InactivityFpsLimit.MINIMIZED);
		});
		if (!stagesOnly) {
			measure(context, "wanilia", null, false);
		}
		measure(context, "polska", POLSKA, stagesOnly);
		measure(context, "polska_rozgrywka", POLSKA_GAMEPLAY, stagesOnly);
	}

	private void measure(ClientGameTestContext context, String name, ResourceKey<WorldPreset> preset,
			boolean stagesOnly) {
		try (TestSingleplayerContext sp = context.worldBuilder().adjustSettings(ui -> configure(ui, preset)).create()) {
			sp.getServer().runCommand("gamerule advance_time false");
			sp.getServer().runCommand("gamerule advance_weather false");
			sp.getServer().runCommand("gamerule spawn_mobs false");
			sp.getServer().runCommand("time set 6000");
			sp.getServer().runCommand("weather clear");
			sp.getServer().runCommand("gamemode spectator @a");

			profileStages(sp, name, stagesOnly);
			if (stagesOnly) {
				return;
			}

			// Teleport w nowe miejsce 3 km od startu, 30 bloków nad gruntem, widok poziomy.
			int x = 3_000;
			int z = 3_000;
			int ground = sp.getServer().computeOnServer(s -> {
				ServerLevel level = s.overworld();
				return level.getChunkSource().getGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG,
						level, level.getChunkSource().randomState());
			});
			long t0 = System.nanoTime();
			sp.getServer().runCommand(String.format(Locale.ROOT, "tp @a %d %d %d 45.0 8.0", x, ground + 30, z));
			// Czekamy, aż klient wczyta 90% chunków w zasięgu widzenia (najwyżej 4 minuty).
			int target = (int) (0.9 * (2 * RENDER_DISTANCE + 1) * (2 * RENDER_DISTANCE + 1));
			int loaded = 0;
			for (int i = 0; i < 240 && loaded < target; i++) {
				context.waitTicks(20);
				loaded = context.computeOnClient(mc -> mc.level.getChunkSource().getLoadedChunksCount());
			}
			double loadSeconds = (System.nanoTime() - t0) / 1e9;
			PolskieLasy.LOG.info("[wydajnosc] {}: klient ma {} z {} chunków po {} s", name, loaded, target,
					String.format(Locale.ROOT, "%.1f", loadSeconds));
			// Czas na zbudowanie siatek geometrii.
			context.waitTicks(20 * 15);
			double sum = 0;
			int min = Integer.MAX_VALUE;
			int samples = 15;
			for (int i = 0; i < samples; i++) {
				context.waitTicks(20);
				int fps = context.computeOnClient(mc -> mc.getFps());
				sum += fps;
				min = Math.min(min, fps);
			}
			PolskieLasy.LOG.info("[wydajnosc] {}: wczytanie nowego terenu {} s, FPS średnio {}, minimum {}", name,
					String.format(Locale.ROOT, "%.1f", loadSeconds), Math.round(sum / samples), min);
			context.takeScreenshot("wydajnosc_" + name);
		}
	}

	/**
	 * Obszary 8 × 8 chunków do pomiaru etapów: północno-zachodni róg w blokach, ziarno {@link #SEED}. Pierwszy,
	 * „nizina”, to ten sam punkt co w M1 (w obu skalach płaska nizina bez rzek), więc wyniki dają się
	 * porównać z dawnymi. Tryb {@code etapy} mierzy też wnętrze Beskidów i dużą rzekę nizinną: tam M2
	 * dokłada najwięcej pracy (piętra, strefy nadwodne, półka brzegowa). Środki z łat „beskidy”
	 * i „wielka_rzeka” w {@code src/test/resources/zloty_teren_m1.txt}, daleko od miejsca startu.
	 */
	private static final int[][] SPOTS_REAL = {{-40_000, 25_000}, {154_834 - 64, 1_058_738 - 64},
			{-19_484 - 64, 11_253 - 64}};
	private static final int[][] SPOTS_GAMEPLAY = {{-40_000, 25_000}, {27_609 - 64, 3_254 - 64},
			{-1_851 - 64, 6_022 - 64}};
	private static final String[] SPOT_NAMES = {"nizina", "beskidy", "rzeka"};

	/**
	 * Czas generacji 64 chunków do kolejnych etapów, na wątku serwera, w nowym obszarze. Liczniki
	 * modelu i wypełniania ({@code PolskaChunkGenerator.SAMPLE_NANOS}, {@code FILL_NANOS}, {@code CHUNKS})
	 * są statyczne i rosną przez cały proces (oba światy, start świata), więc logujemy tylko przyrost
	 * w czasie pomiaru danego obszaru. Przyrost obejmuje też sąsiednie chunki potrzebne do dekoracji
	 * i światła oraz chunki wczytywane w tym czasie w tle wokół gracza.
	 */
	private static void profileStages(TestSingleplayerContext sp, String name, boolean allSpots) {
		boolean real = name.equals("polska");
		int[][] spots = real ? SPOTS_REAL : SPOTS_GAMEPLAY;
		for (int i = 0; i < (allSpots ? spots.length : 1); i++) {
			int[] spot = spots[i];
			String label = i == 0 ? name : name + " / " + SPOT_NAMES[i];
			sp.getServer().runOnServer(server -> profileSpot(server.overworld(), label, spot[0] >> 4, spot[1] >> 4));
		}
	}

	private static void profileSpot(ServerLevel level, String name, int ox, int oz) {
		ChunkStatus[] stages = {ChunkStatus.STRUCTURE_STARTS, ChunkStatus.BIOMES, ChunkStatus.TERRAIN,
				ChunkStatus.FEATURES, ChunkStatus.LIGHT, ChunkStatus.FULL};
		long sample0 = PolskaChunkGenerator.SAMPLE_NANOS.sum();
		long fill0 = PolskaChunkGenerator.FILL_NANOS.sum();
		long chunks0 = PolskaChunkGenerator.CHUNKS.sum();
		StringBuilder sb = new StringBuilder();
		for (ChunkStatus stage : stages) {
			long t0 = System.nanoTime();
			for (int cx = 0; cx < 8; cx++) {
				for (int cz = 0; cz < 8; cz++) {
					level.getChunkSource().getChunk(ox + cx, oz + cz, stage, true);
				}
			}
			sb.append(stage.getName()).append('=').append(Math.round((System.nanoTime() - t0) / 1e6)).append(" ms ");
		}
		PolskieLasy.LOG.info("[wydajnosc] {}: etapy dla 64 chunków (przyrostowo): {}", name, sb);
		long n = PolskaChunkGenerator.CHUNKS.sum() - chunks0;
		if (n > 0) {
			PolskieLasy.LOG.info("[wydajnosc] {}: teren {} chunków w czasie pomiaru, próbkowanie {} ms/chunk, wypełnianie {} ms/chunk",
					name, n,
					String.format(Locale.ROOT, "%.2f", (PolskaChunkGenerator.SAMPLE_NANOS.sum() - sample0) / 1e6 / n),
					String.format(Locale.ROOT, "%.2f", (PolskaChunkGenerator.FILL_NANOS.sum() - fill0) / 1e6 / n));
		} else if (name.startsWith("polska")) {
			PolskieLasy.LOG.warn("[wydajnosc] {}: obszar był już wygenerowany, pomiar nieważny", name);
		}
	}

	private static void configure(WorldCreationUiState ui, ResourceKey<WorldPreset> preset) {
		ui.setSeed(SEED);
		ui.setAllowCommands(true);
		if (preset == null) {
			return;
		}
		for (WorldCreationUiState.WorldTypeEntry entry : ui.getNormalPresetList()) {
			if (entry.preset().is(preset)) {
				ui.setWorldType(entry);
				return;
			}
		}
		throw new AssertionError("Brak presetu " + preset.identifier());
	}
}
