package pl.polskielasy.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.material.Fluids;
import pl.polskielasy.PolskieLasy;
import pl.polskielasy.client.KlimatKlienta;
import pl.polskielasy.climate.BiomeKlimat;
import pl.polskielasy.climate.KlimatBiomu;
import pl.polskielasy.climate.PolskaKlimat;
import pl.polskielasy.command.PolskaCommands;
import pl.polskielasy.season.Seasons;
import pl.polskielasy.worldgen.chunk.PolskaChunkGenerator;
import pl.polskielasy.worldgen.chunk.PolskaDimension;
import pl.polskielasy.worldgen.chunk.PolskaScale;
import pl.polskielasy.worldgen.chunk.VerticalScale;
import pl.polskielasy.worldgen.landscape.ColumnSample;
import pl.polskielasy.worldgen.landscape.LandscapeModel;
import pl.polskielasy.worldgen.landscape.LandscapeType;
import pl.polskielasy.worldgen.landscape.WaterKind;

/**
 * Test w prawdziwym kliencie: tworzy świat "Polska", mierzy szybkość generacji pełnych chunków
 * i robi zrzuty ekranu w charakterystycznych miejscach wskazanych przez model krajobrazu
 * (tryb {@code widoki}). Tryb {@code klimat} sprawdza temperaturę z metrów w obu skalach świata:
 * brak letniego śniegu na sandrze i w Beskidach, z Serene Seasons zimowy śnieg na nizinie, zgodność
 * opadu klienta z serwerem i tryby zamarzania wody.
 */
public final class PolskaWorldClientGameTest implements FabricClientGameTest {
	private static final String SEED = "20260927";
	private static final ResourceKey<WorldPreset> PRESET = ResourceKey.create(Registries.WORLD_PRESET,
			PolskieLasy.id("polska"));
	private static final ResourceKey<WorldPreset> PRESET_ROZGRYWKA = ResourceKey.create(Registries.WORLD_PRESET,
			PolskieLasy.id("polska_rozgrywka"));
	/** Czarna lista Serene Seasons (rzeka, plaża, oceany): bez korekty pory roku. */
	private static final TagKey<Biome> SS_CZARNA_LISTA = TagKey.create(Registries.BIOME,
			Identifier.fromNamespaceAndPath("sereneseasons", "blacklisted_biomes"));

	/** Miejsce do obejrzenia: współrzędne, wysokość kamery nad gruntem, kierunek i nachylenie. */
	private record Site(String name, int x, int z, int cameraAboveGround, float yaw, float pitch) {
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		String mode = System.getProperty("polskielasy.gametest", "wszystko");
		boolean views = mode.equals("widoki") || mode.equals("wszystko");
		boolean climate = mode.equals("klimat") || mode.equals("wszystko");
		if (!views && !climate) {
			return;
		}
		context.runOnClient(mc -> {
			mc.options.renderDistance().set(10);
		});
		try (TestSingleplayerContext sp = context.worldBuilder()
				.adjustSettings(ui -> selectPolska(ui, PRESET))
				.create()) {
			prepare(sp, PolskaScale.REALISTYCZNA);
			if (views) {
				views(context, sp);
			}
			// Klimat po pomiarze i zrzutach: teleport do Beskidów zostawia w tle generację chunków,
			// która zawyżyłaby pomiar ms/chunk.
			if (climate) {
				checkClimate(context, sp);
			}
		}
		if (climate) {
			// Druga skala: typ wymiaru polska_rozgrywka, nieliniowe metry n.p.m. i rozpoznanie skali u klienta.
			try (TestSingleplayerContext sp = context.worldBuilder()
					.adjustSettings(ui -> selectPolska(ui, PRESET_ROZGRYWKA))
					.create()) {
				prepare(sp, PolskaScale.ROZGRYWKA);
				checkClimate(context, sp);
			}
		}
	}

	/** Stały czas i pogoda, gracz w trybie obserwatora; sprawdza generator i jego skalę. */
	private static void prepare(TestSingleplayerContext sp, PolskaScale scale) {
		sp.getServer().runCommand("gamerule advance_time false");
		sp.getServer().runCommand("gamerule advance_weather false");
		sp.getServer().runCommand("time set 6000");
		sp.getServer().runCommand("weather clear");
		sp.getServer().runCommand("gamemode spectator @a");

		boolean isPolska = sp.getServer().computeOnServer(
				s -> s.overworld().getChunkSource().getGenerator() instanceof PolskaChunkGenerator gen
						&& gen.vertical() == scale.vertical());
		if (!isPolska) {
			throw new AssertionError("Świat testowy nie używa generatora Polska w skali " + scale.getSerializedName());
		}
	}

	/** Pomiar generacji i zrzuty ekranu w charakterystycznych miejscach. */
	private static void views(ClientGameTestContext context, TestSingleplayerContext sp) {
		benchmark(sp);

		List<Site> sites = sp.getServer().computeOnServer(PolskaWorldClientGameTest::findSites);
		String only = System.getProperty("polskielasy.miejsca", "");
		if (!only.isBlank()) {
			List<String> names = List.of(only.split(","));
			sites = sites.stream().filter(site -> names.contains(site.name())).toList();
		}
		for (Site site : sites) {
			int groundY = sp.getServer().computeOnServer(s -> surfaceY(s, site.x(), site.z()));
			int camY = groundY + site.cameraAboveGround();
			PolskieLasy.LOG.info("[test] {}: x={} z={} grunt Y={} ({} m n.p.m.)", site.name(), site.x(), site.z(),
					groundY, PolskaDimension.metersAboveSea(groundY - 1));
			sp.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @a %d %d %d %.1f %.1f", site.x(), camY, site.z(),
					site.yaw(), site.pitch()));
			for (int step = 0; step < 2; step++) {
				context.waitTicks(20 * 20);
				int loaded = sp.getServer().computeOnServer(s -> s.overworld().getChunkSource().getLoadedChunksCount());
				String client = context.computeOnClient(mc -> mc.level.getChunkSource().gatherStats() + ", fps " + mc.getFps());
				PolskieLasy.LOG.info("[test] {} po {} s: serwer {} chunków; klient: {}", site.name(), (step + 1) * 20,
						loaded, client);
			}
			context.takeScreenshot("polska_" + site.name());
		}
	}

	/** Wysokość, poniżej której latem nie może leżeć śnieg (docs/03-m2-biomy.md, sekcja 12.3). */
	private static final double NO_SUMMER_SNOW_BELOW = 1_900;

	/**
	 * Temperatura z metrów: latem nie ma śniegu na sandrze ani w Beskidach poniżej 1900 m (także
	 * z korektą pory roku Serene Seasons), z SS zimą pada śnieg na nizinie, biomy Netheru i Endu nie
	 * mają profilu, klient liczy opad tak samo jak serwer (20 punktów wokół gracza), a tryby
	 * zamarzania działają jak w profilu.
	 */
	private static void checkClimate(ClientGameTestContext context, TestSingleplayerContext sp) {
		boolean ss = FabricLoader.getInstance().isModLoaded("sereneseasons");
		if (ss) {
			if (!Seasons.provider().name().equals("Serene Seasons")) {
				throw new AssertionError("Serene Seasons wczytany, ale dostawcą pory roku jest " + Seasons.provider().name());
			}
			sp.getServer().runCommand("season set mid_summer");
		}
		int[] p = sp.getServer().computeOnServer(s -> {
			PolskaChunkGenerator gen = (PolskaChunkGenerator) s.overworld().getChunkSource().getGenerator();
			return highestBeskidy(gen.model(s.overworld().getSeed()));
		});
		String summer = sp.getServer().computeOnServer(s -> checkSummer(s, p));
		PolskieLasy.LOG.info("[test] klimat latem: {}", summer);

		int groundY = sp.getServer().computeOnServer(s -> surfaceY(s, p[0], p[1]));
		sp.getServer().runCommand(String.format(Locale.ROOT, "tp @a %d %d %d", p[0], groundY + 30, p[1]));
		context.waitTicks(20 * 15);

		List<BlockPos> points = sp.getServer().computeOnServer(s -> {
			VerticalScale v = ((PolskaChunkGenerator) s.overworld().getChunkSource().getGenerator()).vertical();
			List<BlockPos> list = new ArrayList<>();
			for (int k = 0; k < 5; k++) {
				int x = p[0] + (k % 3 - 1) * 24;
				int z = p[1] + (k / 3 * 2 - 1) * 24;
				for (double meters : new double[] {0, 1_000, 1_950, 2_100}) {
					list.add(new BlockPos(x, Math.min(v.topBlockY(meters) + 1, v.maxY()), z));
				}
			}
			return list;
		});
		List<String> server = sp.getServer().computeOnServer(s -> {
			ServerLevel level = s.overworld();
			List<String> out = new ArrayList<>();
			for (BlockPos pos : points) {
				out.add(level.getBiome(pos).value().getPrecipitationAt(pos, level.getSeaLevel()).name());
			}
			return out;
		});
		VerticalScale serverScale = sp.getServer().computeOnServer(
				s -> ((PolskaChunkGenerator) s.overworld().getChunkSource().getGenerator()).vertical());
		List<String> client = context.computeOnClient(mc -> {
			if (KlimatKlienta.skala(mc.level) != serverScale) {
				throw new AssertionError("Klient rozpoznał inną skalę pionową niż serwer");
			}
			List<String> out = new ArrayList<>();
			for (BlockPos pos : points) {
				if (!mc.level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
					throw new AssertionError("Klient nie ma chunka w punkcie " + pos.toShortString());
				}
				Biome biome = mc.level.getBiome(pos).value();
				if (BiomeKlimat.klimat(biome) == null) {
					throw new AssertionError("Biom klienta bez profilu klimatu w " + pos.toShortString());
				}
				out.add(biome.getPrecipitationAt(pos, mc.level.getSeaLevel()).name());
			}
			return out;
		});
		if (!server.equals(client)) {
			throw new AssertionError("Opad klienta " + client + " różni się od serwera " + server);
		}
		PolskieLasy.LOG.info("[test] klimat: opad klienta zgodny z serwerem w {} punktach: {}", points.size(), client);

		if (ss) {
			sp.getServer().runCommand("season set mid_winter");
			String winter = sp.getServer().computeOnServer(PolskaWorldClientGameTest::checkWinter);
			PolskieLasy.LOG.info("[test] klimat zimą (Serene Seasons): {}", winter);
			sp.getServer().runCommand("season set mid_summer");
		}
		// Na końcu: test zamarzania na chwilę podmienia profil biomu wody.
		String freeze = sp.getServer().computeOnServer(PolskaWorldClientGameTest::checkFreezeModes);
		PolskieLasy.LOG.info("[test] klimat, zamarzanie: {}", freeze);
	}

	/**
	 * Latem po stronie serwera: brak profili w Netherze i Endzie, brak śniegu na sandrze i w Beskidach
	 * wokół najwyższego miejsca {@code szczyt} oraz w powietrzu na 1500 i 1850 m n.p.m. nad Beskidami.
	 */
	private static String checkSummer(MinecraftServer server, int[] szczyt) {
		ServerLevel level = server.overworld();
		PolskaChunkGenerator gen = (PolskaChunkGenerator) level.getChunkSource().getGenerator();
		LandscapeModel m = gen.model(level.getSeed());
		VerticalScale v = gen.vertical();
		for (ResourceKey<Level> dimension : List.of(Level.NETHER, Level.END)) {
			ServerLevel other = server.getLevel(dimension);
			if (other == null) {
				throw new AssertionError("Brak wymiaru " + dimension.identifier());
			}
			for (Holder<Biome> biome : other.getChunkSource().getGenerator().getBiomeSource().possibleBiomes()) {
				if (BiomeKlimat.klimat(biome.value()) != null) {
					throw new AssertionError("Biom wymiaru " + dimension.identifier() + " z profilem klimatu: "
							+ biome.getRegisteredName());
				}
			}
		}
		StringBuilder report = new StringBuilder();
		int[] sandr = spiral(m, s -> s.type() == LandscapeType.SANDR);
		if (sandr == null) {
			throw new AssertionError("Brak sandru");
		}
		checkSummerSnow(level, m, v, LandscapeType.SANDR, sandr, "sandr", report);
		double highest = checkSummerSnow(level, m, v, LandscapeType.BESKIDY, szczyt, "beskidy", report);
		// Ziarno testu: najwyżej ok. 1311 m (REAL) i 1125 m (GAMEPLAY), bo w skali rozgrywki Beskidy
		// prawie nie przekraczają 1150 m (docs/03-m2-biomy.md, S0). Wyższe partie sprawdzamy w powietrzu.
		double minimum = v == PolskaScale.REALISTYCZNA.vertical() ? 1_200 : 1_000;
		if (highest < minimum) {
			throw new AssertionError(String.format(Locale.ROOT,
					"Beskidy sprawdzone tylko do %.0f m n.p.m. (wymagane co najmniej %.0f m)", highest, minimum));
		}
		int checked = 0;
		for (int k = 0; k < 25; k++) {
			for (double meters : new double[] {1_500, 1_850}) {
				BlockPos pos = new BlockPos(szczyt[0] + (k % 5 - 2) * 200, v.topBlockY(meters) + 1, szczyt[1] + (k / 5 - 2) * 200);
				if (v.metersAboveSea(pos.getY()) >= NO_SUMMER_SNOW_BELOW) {
					throw new AssertionError("Wysokość testowa ponad 1900 m: " + pos.toShortString());
				}
				Biome biome = level.getBiome(pos).value();
				KlimatBiomu klimat = BiomeKlimat.klimat(biome);
				if (klimat == null) {
					throw new AssertionError("Biom bez profilu klimatu w " + pos.toShortString());
				}
				float t = Seasons.provider().temperatureInSeason(level, biome, pos, klimat.temperatura(pos));
				if (biome.coldEnoughToSnow(pos, level.getSeaLevel()) || t < PolskaKlimat.PROG_SNIEGU) {
					throw new AssertionError(String.format(Locale.ROOT, "Latem śnieg na %.0f m n.p.m. (T z porą roku %.3f, %s)",
							meters, t, pos.toShortString()));
				}
				checked++;
			}
		}
		report.append(String.format(Locale.ROOT, "powietrze nad Beskidami: %d punktów na 1500 i 1850 m bez śniegu; ",
				checked));
		checkSnowBlocks(level, v, szczyt, false, "beskidy", report);
		return report.toString();
	}

	/**
	 * Kolumny typu {@code type} w siatce 48 × 48 co 40 m wokół {@code p}, poniżej 1900 m: brak śniegu
	 * w wanilijnej ścieżce (bufor i mixin temperatury) i w temperaturze z porą roku (hak SS, gdy jest).
	 *
	 * @return najwyższa sprawdzona kolumna w metrach n.p.m.
	 */
	private static double checkSummerSnow(ServerLevel level, LandscapeModel m, VerticalScale v, LandscapeType type,
			int[] p, String name, StringBuilder report) {
		int checked = 0;
		double highest = 0;
		float coldest = Float.MAX_VALUE;
		for (int i = -24; i < 24; i++) {
			for (int j = -24; j < 24; j++) {
				int x = p[0] + i * 40;
				int z = p[1] + j * 40;
				ColumnSample s = m.sample(x, z);
				if (s.type() != type || s.surface() >= NO_SUMMER_SNOW_BELOW) {
					continue;
				}
				BlockPos pos = new BlockPos(x, v.topBlockY(s.surface()) + 1, z);
				Biome biome = level.getBiome(pos).value();
				KlimatBiomu klimat = BiomeKlimat.klimat(biome);
				if (klimat == null) {
					throw new AssertionError("Biom bez profilu klimatu w " + pos.toShortString());
				}
				if (biome.coldEnoughToSnow(pos, level.getSeaLevel())) {
					throw new AssertionError(String.format(Locale.ROOT, "Latem śnieg w %s na %.0f m n.p.m. (%s)", name,
							s.surface(), pos.toShortString()));
				}
				float t = Seasons.provider().temperatureInSeason(level, biome, pos, klimat.temperatura(pos));
				if (t < PolskaKlimat.PROG_SNIEGU) {
					throw new AssertionError(String.format(Locale.ROOT,
							"Latem (z porą roku) T %.3f, czyli śnieg, w %s na %.0f m n.p.m. (%s)", t, name, s.surface(),
							pos.toShortString()));
				}
				coldest = Math.min(coldest, t);
				checked++;
				highest = Math.max(highest, s.surface());
			}
		}
		if (checked < 100) {
			throw new AssertionError("Za mało kolumn typu " + name + ": " + checked);
		}
		report.append(String.format(Locale.ROOT, "%s: %d kolumn bez śniegu, najwyżej %.0f m, najniższa T %.3f; ", name,
				checked, highest, coldest));
		return highest;
	}

	/** Zimą z Serene Seasons: na sandrze i wysoczyźnie morenowej pada śnieg (biomy spoza czarnej listy SS). */
	private static String checkWinter(MinecraftServer server) {
		ServerLevel level = server.overworld();
		PolskaChunkGenerator gen = (PolskaChunkGenerator) level.getChunkSource().getGenerator();
		LandscapeModel m = gen.model(level.getSeed());
		VerticalScale v = gen.vertical();
		StringBuilder report = new StringBuilder();
		int[] sandr = null;
		for (LandscapeType type : List.of(LandscapeType.SANDR, LandscapeType.WYSOCZYZNA_MORENOWA)) {
			int[] p = spiral(m, s -> s.type() == type);
			if (p == null) {
				throw new AssertionError("Brak miejsca typu " + type);
			}
			if (sandr == null) {
				sandr = p;
			}
			int checked = 0;
			int skipped = 0;
			float warmest = -Float.MAX_VALUE;
			for (int i = -24; i < 24; i++) {
				for (int j = -24; j < 24; j++) {
					int x = p[0] + i * 40;
					int z = p[1] + j * 40;
					ColumnSample s = m.sample(x, z);
					if (s.type() != type) {
						continue;
					}
					BlockPos pos = new BlockPos(x, v.topBlockY(s.surface()) + 1, z);
					Holder<Biome> holder = level.getBiome(pos);
					KlimatBiomu klimat = BiomeKlimat.klimat(holder.value());
					if (klimat == null) {
						throw new AssertionError("Biom bez profilu klimatu w " + pos.toShortString());
					}
					if (holder.is(SS_CZARNA_LISTA)) {
						skipped++;
						continue;
					}
					float t = Seasons.provider().temperatureInSeason(level, holder.value(), pos, klimat.temperatura(pos));
					if (t >= PolskaKlimat.PROG_SNIEGU) {
						throw new AssertionError(String.format(Locale.ROOT,
								"Zimą z SS deszcz (T %.3f) na nizinie %s, %.0f m n.p.m. (%s, %s)", t, type, s.surface(),
								pos.toShortString(), holder.getRegisteredName()));
					}
					warmest = Math.max(warmest, t);
					checked++;
				}
			}
			if (checked < 100) {
				throw new AssertionError("Za mało kolumn typu " + type + " poza czarną listą SS: " + checked);
			}
			report.append(String.format(Locale.ROOT, "%s: %d kolumn ze śniegiem (pominięte z czarnej listy SS: %d), "
					+ "najwyższa T %.3f; ", type, checked, skipped, warmest));
		}
		checkSnowBlocks(level, v, sandr, true, "sandr", report);
		return report.toString();
	}

	/**
	 * Opad śniegu na prawdziwych blokach: w obszarze 64 × 64 bloków wokół {@code p} (chunki ładowane
	 * synchronicznie) dla każdej kolumny, w której śnieg mógłby leżeć (powietrze nad podłożem
	 * utrzymującym warstwę śniegu, jak w {@code ServerLevel.tickPrecipitation}), {@code Biome.shouldSnow}
	 * (z hakiem SS na początku metody) musi dać {@code expectSnow}. Latem pod 1900 m nie może też leżeć
	 * śnieg z generacji ({@code freeze_top_layer} bez pory roku).
	 */
	private static void checkSnowBlocks(ServerLevel level, VerticalScale v, int[] p, boolean expectSnow, String name,
			StringBuilder report) {
		int checked = 0;
		for (int i = 0; i < 8; i++) {
			for (int j = 0; j < 8; j++) {
				int x = p[0] - 32 + i * 8 + 3;
				int z = p[1] - 32 + j * 8 + 3;
				level.getChunk(x >> 4, z >> 4);
				BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(x, 0, z));
				Holder<Biome> holder = level.getBiome(pos);
				if (expectSnow ? holder.is(SS_CZARNA_LISTA) : v.metersAboveSea(pos.getY()) >= NO_SUMMER_SNOW_BELOW) {
					continue;
				}
				BlockState state = level.getBlockState(pos);
				if (!expectSnow && (state.is(Blocks.SNOW) || level.getBlockState(pos.below()).is(Blocks.SNOW))) {
					throw new AssertionError(String.format(Locale.ROOT, "Latem śnieg z generacji w %s na %.0f m n.p.m. (%s)",
							name, v.metersAboveSea(pos.getY()), pos.toShortString()));
				}
				if (!state.isAir() || level.getBrightness(LightLayer.BLOCK, pos) >= 10
						|| !Blocks.SNOW.defaultBlockState().canSurvive(level, pos)) {
					continue;
				}
				if (holder.value().shouldSnow(level, pos) != expectSnow) {
					throw new AssertionError(String.format(Locale.ROOT, "%s: shouldSnow = %b w %s na %.0f m n.p.m. (%s)",
							expectSnow ? "Zimą" : "Latem", !expectSnow, name, v.metersAboveSea(pos.getY()),
							pos.toShortString()));
				}
				checked++;
			}
		}
		if (checked < 8) {
			throw new AssertionError("Za mało kolumn do sprawdzenia opadu na blokach w " + name + ": " + checked);
		}
		report.append(String.format(Locale.ROOT, "%s: shouldSnow = %b w %d kolumnach; ", name, expectSnow, checked));
	}

	/**
	 * Tryby zamarzania ({@code BiomeFreezeMixin}): na wodzie morza na chwilę podmienia profil biomu
	 * na testowy i sprawdza {@code shouldFreeze} bez sąsiadów. Przy T ok. 0,10 (z porą roku) NIGDY
	 * i RZEKA nie zamarzają, a WANILIA tak; przy T ok. -1 zamarzają RZEKA i WANILIA. Na końcu
	 * przywraca profil. Wanilijny bufor temperatury (na wątek, 1024 pozycje) zapełniamy innymi
	 * pozycjami przed każdym pomiarem i po nim, bo inaczej zwracałby temperaturę poprzedniego profilu.
	 */
	private static String checkFreezeModes(MinecraftServer server) {
		ServerLevel level = server.overworld();
		PolskaChunkGenerator gen = (PolskaChunkGenerator) level.getChunkSource().getGenerator();
		LandscapeModel m = gen.model(level.getSeed());
		VerticalScale v = gen.vertical();
		int[] p = spiral(m, s -> s.type() == LandscapeType.MORZE && s.waterKind() == WaterKind.SEA);
		if (p == null) {
			throw new AssertionError("Brak morza do testu zamarzania");
		}
		BlockPos water = null;
		for (int k = 0; k < 16 && water == null; k++) {
			int x = p[0] + k * 16;
			level.getChunk(x >> 4, p[1] >> 4);
			BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, p[1]) - 1, p[1]);
			if (level.getFluidState(pos).is(Fluids.WATER) && level.getBlockState(pos).getBlock() instanceof LiquidBlock
					&& level.getBrightness(LightLayer.BLOCK, pos) < 10) {
				water = pos;
			}
		}
		if (water == null) {
			throw new AssertionError("Brak powierzchni wody morza w okolicy " + p[0] + ", " + p[1]);
		}
		Biome biome = level.getBiome(water).value();
		KlimatBiomu original = BiomeKlimat.klimat(biome);
		if (original == null || original.zamarzanie() != KlimatBiomu.Zamarzanie.NIGDY) {
			throw new AssertionError("Biom morza bez profilu NIGDY w " + water.toShortString() + ": " + original);
		}
		StringBuilder report = new StringBuilder();
		try {
			// T z porą roku ok. 0,10: między progiem rzek (0,05) a progiem wanilii (0,15).
			float korekta = 0.10F - seasonalTemperature(level, biome, water,
					new KlimatBiomu(v, 0.10F, KlimatBiomu.Zamarzanie.WANILIA));
			checkFreeze(level, biome, water, v, 0.10F + korekta, false, false, true, report);
			checkFreeze(level, biome, water, v, -1.0F, false, true, true, report);
			BiomeKlimat.ustaw(biome, original);
			flushTemperatureCache(biome, level.getSeaLevel());
			if (biome.shouldFreeze(level, water, false)) {
				throw new AssertionError("Morze z przywróconym profilem zamarza w " + water.toShortString());
			}
		} finally {
			BiomeKlimat.ustaw(biome, original);
			flushTemperatureCache(biome, level.getSeaLevel());
		}
		report.append("woda ").append(water.toShortString());
		return report.toString();
	}

	private static void checkFreeze(ServerLevel level, Biome biome, BlockPos water, VerticalScale v, float base,
			boolean nigdy, boolean rzeka, boolean wanilia, StringBuilder report) {
		KlimatBiomu.Zamarzanie[] modes = KlimatBiomu.Zamarzanie.values();
		boolean[] expected = new boolean[modes.length];
		expected[KlimatBiomu.Zamarzanie.NIGDY.ordinal()] = nigdy;
		expected[KlimatBiomu.Zamarzanie.RZEKA.ordinal()] = rzeka;
		expected[KlimatBiomu.Zamarzanie.WANILIA.ordinal()] = wanilia;
		for (KlimatBiomu.Zamarzanie mode : modes) {
			KlimatBiomu test = new KlimatBiomu(v, base, mode);
			float t = seasonalTemperature(level, biome, water, test);
			// Strażnik: oczekiwania zakładają T po tej samej stronie progów co temperatura bazowa.
			boolean cold = base < 0;
			if (cold ? t >= PolskaKlimat.PROG_ZAMARZANIA_RZEK
					: t < PolskaKlimat.PROG_ZAMARZANIA_RZEK + 0.01F || t >= PolskaKlimat.PROG_SNIEGU - 0.01F) {
				throw new AssertionError(String.format(Locale.ROOT, "Test zamarzania: T %.3f poza zakresem dla bazy %.3f",
						t, base));
			}
			BiomeKlimat.ustaw(biome, test);
			flushTemperatureCache(biome, level.getSeaLevel());
			boolean frozen = biome.shouldFreeze(level, water, false);
			if (frozen != expected[mode.ordinal()]) {
				throw new AssertionError(String.format(Locale.ROOT, "Zamarzanie %s przy T %.3f: %b, oczekiwano %b (%s)",
						mode, t, frozen, expected[mode.ordinal()], water.toShortString()));
			}
			report.append(String.format(Locale.ROOT, "%s przy T %.3f: %b; ", mode, t, frozen));
		}
	}

	/** Temperatura z porą roku dla profilu {@code test} (podpina go na czas pomiaru). */
	private static float seasonalTemperature(ServerLevel level, Biome biome, BlockPos pos, KlimatBiomu test) {
		KlimatBiomu before = BiomeKlimat.klimat(biome);
		BiomeKlimat.ustaw(biome, test);
		flushTemperatureCache(biome, level.getSeaLevel());
		try {
			return Seasons.provider().temperatureInSeason(level, biome, pos, test.temperatura(pos));
		} finally {
			BiomeKlimat.ustaw(biome, before);
			flushTemperatureCache(biome, level.getSeaLevel());
		}
	}

	/** Wypycha z wanilijnego bufora temperatury biomu (bieżący wątek) wszystkie wcześniejsze pozycje. */
	private static void flushTemperatureCache(Biome biome, int seaLevel) {
		for (int i = 0; i < 1_100; i++) {
			biome.coldEnoughToSnow(new BlockPos(-29_000_000 + i, 0, -29_000_000), seaLevel);
		}
	}

	/**
	 * Najwyższa kolumna Beskidów poniżej 1900 m wokół pierwszego miejsca Beskidów na spirali: siatka
	 * ±60 km co 1,5 km, potem ±2 km co 250 m i ±300 m co 50 m wokół najlepszej. Spirala z warunkiem
	 * wysokości byłaby za droga (w REAL pierwsze miejsce powyżej 1150 m leży ok. 1000 km od środka).
	 */
	private static int[] highestBeskidy(LandscapeModel m) {
		int[] p = spiral(m, s -> s.type() == LandscapeType.BESKIDY);
		if (p == null) {
			throw new AssertionError("Brak Beskidów");
		}
		int[] best = p;
		double bestMeters = -1;
		int[][] passes = {{1_500, 40}, {250, 8}, {50, 6}};
		for (int[] pass : passes) {
			int[] c = best;
			for (int i = -pass[1]; i <= pass[1]; i++) {
				for (int j = -pass[1]; j <= pass[1]; j++) {
					int x = c[0] + i * pass[0];
					int z = c[1] + j * pass[0];
					ColumnSample s = m.sample(x, z);
					if (s.type() == LandscapeType.BESKIDY && s.surface() < NO_SUMMER_SNOW_BELOW
							&& s.surface() > bestMeters) {
						best = new int[] {x, z};
						bestMeters = s.surface();
					}
				}
			}
		}
		return best;
	}

	private void selectPolska(WorldCreationUiState ui, ResourceKey<WorldPreset> preset) {
		ui.setSeed(SEED);
		ui.setAllowCommands(true);
		for (WorldCreationUiState.WorldTypeEntry entry : ui.getNormalPresetList()) {
			if (entry.preset().is(preset)) {
				ui.setWorldType(entry);
				return;
			}
		}
		throw new AssertionError("Brak presetu świata " + preset.identifier() + " na liście typów świata");
	}

	private static int surfaceY(MinecraftServer server, int x, int z) {
		ServerLevel level = server.overworld();
		return level.getChunkSource().getGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level,
				level.getChunkSource().randomState());
	}

	/** Generuje synchronicznie obszar 8 × 8 chunków do stanu pełnego i zapisuje czas w logu. */
	private static void benchmark(TestSingleplayerContext sp) {
		sp.getServer().runOnServer(server -> {
			ServerLevel level = server.overworld();
			int ox = 20_000 >> 4;
			int oz = -20_000 >> 4;
			long t0 = System.nanoTime();
			int n = 0;
			for (int cx = 0; cx < 8; cx++) {
				for (int cz = 0; cz < 8; cz++) {
					level.getChunk(ox + cx, oz + cz);
					n++;
				}
			}
			double ms = (System.nanoTime() - t0) / 1e6;
			PolskaChunkGenerator gen = (PolskaChunkGenerator) level.getChunkSource().getGenerator();
			LandscapeModel m = gen.model(level.getSeed());
			long t1 = System.nanoTime();
			double sink = 0;
			for (int x = 0; x < 128; x++) {
				for (int z = 0; z < 128; z++) {
					sink += m.sample(40_000 + x, 40_000 + z).surface();
				}
			}
			double modelMs = (System.nanoTime() - t1) / 1e6;
			PolskieLasy.LOG.info("[test] Model krajobrazu: {} ms na 64 chunki (16 384 kolumny), suma kontrolna {}",
					Math.round(modelMs), Math.round(sink));
			PolskieLasy.LOG.info("[test] Generacja {} pełnych chunków: {} ms ({} ms/chunk)", n, Math.round(ms),
					Math.round(ms / n));
		});
	}

	/** Wybiera miejsca do zrzutów ekranu, przeszukując model krajobrazu na spirali. */
	private static List<Site> findSites(MinecraftServer server) {
		ServerLevel level = server.overworld();
		PolskaChunkGenerator gen = (PolskaChunkGenerator) level.getChunkSource().getGenerator();
		LandscapeModel m = gen.model(level.getSeed());
		List<Site> sites = new ArrayList<>();
		int[] p = spiral(m, s -> s.type() == LandscapeType.SANDR && s.waterKind() == WaterKind.LAKE);
		if (p != null) {
			sites.add(new Site("sandr_jezioro", p[0] + 180, p[1] - 180, 45, 45f, 20f));
		}
		p = spiral(m, s -> s.type() == LandscapeType.WYSOCZYZNA_MORENOWA && s.waterKind() == WaterKind.LAKE);
		if (p != null) {
			sites.add(new Site("morena_oczka", p[0] + 120, p[1] - 120, 35, 45f, 22f));
		}
		p = spiral(m, s -> s.waterKind() == WaterKind.RIVER);
		if (p != null) {
			sites.add(new Site("dolina_rzeki", p[0], p[1], 60, 0f, 25f));
		}
		p = spiral(m, s -> s.type() == LandscapeType.BESKIDY && s.surface() > 1_150);
		if (p != null) {
			sites.add(new Site("beskidy", p[0], p[1], 25, 30f, 12f));
		}
		p = spiral(m, s -> s.type() == LandscapeType.POGORZE);
		if (p != null) {
			sites.add(new Site("pogorze", p[0], p[1], 40, 60f, 15f));
		}
		// Rzeki, doliny i morze.
		p = spiral(m, s -> s.waterKind() == WaterKind.RIVER && s.type().isLowland() && s.surface() - s.waterLevel() < -2.5);
		if (p != null) {
			sites.add(new Site("meandry", p[0], p[1], 70, 20f, 50f));
		}
		addTarget(sites, m, PolskaCommands.Target.STARORZECZE, "starorzecze", 45, 45f);
		addTarget(sites, m, PolskaCommands.Target.ZRODLO, "zrodlo", 30, 20f);
		addCoast(sites, m, PolskaCommands.Target.PLAZA, "plaza", 20);
		addCoast(sites, m, PolskaCommands.Target.WYDMY_NADMORSKIE, "wydmy_nadmorskie", 25);
		addCoast(sites, m, PolskaCommands.Target.KLIF, "klif", 30);
		addTarget(sites, m, PolskaCommands.Target.ZALEW, "zalew", 40, 25f);
		addTarget(sites, m, PolskaCommands.Target.UJSCIE, "ujscie", 60, 35f);
		return sites;
	}

	private static void addTarget(List<Site> sites, LandscapeModel m, PolskaCommands.Target target, String name,
			int above, float pitch) {
		double[] p = PolskaCommands.locate(m, target, 0, 0);
		if (p != null) {
			sites.add(new Site(name, (int) p[0] - 40, (int) p[1] - 40, above, -45f, pitch));
		}
	}

	/** Miejsce na wybrzeżu oglądane z morza, ok. 80 m od brzegu, w stronę lądu. */
	private static void addCoast(List<Site> sites, LandscapeModel m, PolskaCommands.Target target, String name,
			int above) {
		double[] p = PolskaCommands.locate(m, target, 0, 0);
		if (p == null) {
			return;
		}
		// Kierunek ku morzu: spadek odległości od linii brzegowej.
		double e = 50;
		double gx = m.coastDistance(p[0] + e, p[1]) - m.coastDistance(p[0] - e, p[1]);
		double gz = m.coastDistance(p[0], p[1] + e) - m.coastDistance(p[0], p[1] - e);
		double l = Math.max(1e-9, Math.hypot(gx, gz));
		double sx = -gx / l;
		double sz = -gz / l;
		// Yaw w Minecrafcie: 0 = +Z, 90 = -X; kamera patrzy przeciwnie do kierunku ku morzu.
		float yaw = (float) Math.toDegrees(Math.atan2(sx, -sz));
		sites.add(new Site(name, (int) (p[0] + sx * 80), (int) (p[1] + sz * 80), above, yaw, 12f));
	}

	private static int[] spiral(LandscapeModel m, java.util.function.Predicate<ColumnSample> test) {
		for (int r = 0; r < 1_500; r += 2) {
			int samples = Math.max(24, r);
			for (int k = 0; k < samples; k++) {
				double a = k * (2 * Math.PI / samples) + r * 0.37;
				int x = (int) (Math.cos(a) * r * 1_000);
				int z = (int) (Math.sin(a) * r * 1_000);
				if (test.test(m.sample(x, z))) {
					return new int[] {x, z};
				}
			}
		}
		return null;
	}
}
