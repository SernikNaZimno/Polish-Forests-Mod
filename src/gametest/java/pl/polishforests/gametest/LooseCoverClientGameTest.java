package pl.polishforests.gametest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import pl.polishforests.PolishForests;
import pl.polishforests.client.screen.PolandPresetEditor;
import pl.polishforests.worldgen.chunk.LooseCover;
import pl.polishforests.worldgen.chunk.PolandChunkGenerator;
import pl.polishforests.worldgen.chunk.PolandScale;
import pl.polishforests.worldgen.landscape.ColumnSample;
import pl.polishforests.worldgen.landscape.LandscapeModel;
import pl.polishforests.worldgen.landscape.Substrate;
import pl.polishforests.worldgen.landscape.WaterKind;

/**
 * Loose cover above the rock (step K1, docs/04-pionowy-wycinek.md), {@code -Pgametest=cover} (only by name; about 15
 * minutes): in each scale ({@code -Pscales}) a world with the thin cover (the presets) and one with the realistic cover
 * (the cover of worlds from before K1), the same seed and places: a sandy lowland, a till lowland and a valley floor of
 * alluvium by a river. In each place a survey of 3 × 3 chunks before digging (cover thickness from the ground top to the
 * rock body: {@value #RUN} rock blocks in a row, so a boulder on the till is not the rock under the cover; ores within 16 blocks of the ground, voids under the ground such as cave openings, rock in side faces,
 * bare rock on top), then a pit of 32 × 32 blocks down to Y 0 and a screenshot of its north wall from inside the pit
 * ({@code cover_<scale>_<thin|realistic>_<place>}).
 */
public final class LooseCoverClientGameTest implements FabricClientGameTest {
	private static final String SEED = "20260927";
	private static final ResourceKey<WorldPreset> PRESET = ResourceKey.create(Registries.WORLD_PRESET,
			PolishForests.id("poland"));
	private static final ResourceKey<WorldPreset> PRESET_GAMEPLAY = ResourceKey.create(Registries.WORLD_PRESET,
			PolishForests.id("poland_gameplay"));
	/** Ground blocks of the surface plan (soil, deposits, beds) besides rock. */
	private static final Set<Block> LOOSE = Set.of(Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.PODZOL,
			Blocks.ROOTED_DIRT, Blocks.MUD, Blocks.MOSS_BLOCK, Blocks.SAND, Blocks.GRAVEL, Blocks.CLAY, Blocks.COBBLESTONE,
			Blocks.FARMLAND, Blocks.DIRT_PATH, pl.polishforests.block.ModBlocks.mossyPodzol());
	/** Rock blocks in a row that start the rock body under the cover (taller than the boulders of the till). */
	private static final int RUN = 4;
	/** Model cover above which the thin cover applies (SurfaceBuilder.THIN_FROM_METERS). */
	private static final double LOWLAND_COVER = 20;

	/** A place: name and the pit's chunk (the pit covers this chunk and the one north of it). */
	private record Place(String name, int cx, int cz) {
	}

	/** Survey of 3 × 3 chunks around a place. */
	private record Survey(int columns, double meanCover, int minCover, int maxCover, int[] hist, int ores, int voidColumns,
			int rockFaces, int bareRock, int groundY) {
		String describe() {
			return String.format(Locale.ROOT, "%d columns, cover mean %.1f min %d max %d, histogram %s; ores within 16 "
					+ "blocks %d; columns with voids under the ground %d; rock in side faces %d; bare rock tops %d; ground Y "
					+ "at the pit %d", columns, meanCover, minCover, maxCover, histogram(hist), ores, voidColumns, rockFaces,
					bareRock, groundY);
		}
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!"cover".equals(System.getProperty("polishforests.gametest", "all"))) {
			return;
		}
		String scales = System.getProperty("polishforests.scales", "");
		List<String> failures = new ArrayList<>();
		for (PolandScale scale : PolandScale.values()) {
			if (!scales.isBlank() && !scales.contains(scale.getSerializedName())) {
				continue;
			}
			List<Place> places = null;
			Map<String, Survey> thin = new LinkedHashMap<>();
			for (LooseCover cover : new LooseCover[] {LooseCover.THIN, LooseCover.REALISTIC}) {
				try (TestSingleplayerContext sp = context.worldBuilder()
						.adjustSettings(ui -> select(ui, scale == PolandScale.REALISTIC ? PRESET : PRESET_GAMEPLAY, cover))
						.create()) {
					prepare(sp, cover);
					if (places == null) {
						places = sp.getServer().computeOnServer(LooseCoverClientGameTest::findPlaces);
					}
					for (Place place : places) {
						String name = "cover_" + scale.getSerializedName() + "_" + cover.getSerializedName() + "_" + place.name();
						Survey survey = sp.getServer().computeOnServer(s -> survey(s, place));
						PolishForests.LOG.info("[cover] {} at chunk ({}, {}): {}", name, place.cx(), place.cz(), survey.describe());
						check(name, cover, survey, thin.get(place.name()), failures);
						if (cover == LooseCover.THIN) {
							thin.put(place.name(), survey);
						}
						int groundY = survey.groundY();
						dig(sp, place, groundY);
						shoot(context, sp, place, groundY, name);
					}
				}
			}
		}
		if (!failures.isEmpty()) {
			throw new AssertionError("Loose cover: " + String.join("; ", failures));
		}
	}

	private static void select(WorldCreationUiState ui, ResourceKey<WorldPreset> preset, LooseCover cover) {
		ui.setSeed(SEED);
		ui.setAllowCommands(true);
		ui.setWorldType(ui.getNormalPresetList().stream().filter(e -> e.preset().is(preset)).findFirst()
				.orElseThrow(() -> new AssertionError("Missing world type " + preset.identifier())));
		if (!(ui.getSettings().selectedDimensions().overworld() instanceof PolandChunkGenerator gen)) {
			throw new AssertionError("The preset " + preset.identifier() + " has no Poland generator");
		}
		if (gen.settings().looseCover() != LooseCover.THIN) {
			throw new AssertionError("The preset " + preset.identifier() + " has the loose cover " + gen.settings().looseCover());
		}
		// Step J1: the cover is measured without the caves (the presets have them on; caves would break the runs of rock
		// that end the cover and add voids under the ground in both settings alike).
		ui.updateDimensions(PolandPresetEditor.apply(gen.settings().withLooseCover(cover).withCaves(false)));
	}

	private static void prepare(TestSingleplayerContext sp, LooseCover cover) {
		sp.getServer().runCommand("gamerule advance_time false");
		sp.getServer().runCommand("gamerule advance_weather false");
		sp.getServer().runCommand("time set 6000");
		sp.getServer().runCommand("weather clear");
		sp.getServer().runCommand("gamemode spectator @a");
		sp.getServer().runCommand("effect give @a minecraft:night_vision infinite 0 true");
		LooseCover actual = sp.getServer().computeOnServer(
				s -> ((PolandChunkGenerator) s.overworld().getChunkSource().getGenerator()).settings().looseCover());
		if (actual != cover) {
			throw new AssertionError("The world has the loose cover " + actual + " instead of " + cover);
		}
	}

	/** The thin cover in the lowland: 4–10 blocks on most columns, no rock in side faces, not more voids than realistic. */
	private static void check(String name, LooseCover cover, Survey s, Survey thinSurvey, List<String> failures) {
		if (s.columns() < 1_500) {
			failures.add(name + ": only " + s.columns() + " lowland columns surveyed");
		}
		if (s.rockFaces() > 0) {
			failures.add(name + ": rock in " + s.rockFaces() + " side faces");
		}
		if (cover == LooseCover.THIN) {
			int inRange = 0;
			for (int c = 4; c <= 10; c++) {
				inRange += s.hist()[c];
			}
			if (inRange < 0.9 * s.columns() || s.meanCover() < 4.5 || s.meanCover() > 9.5) {
				failures.add(name + ": thin cover mean " + s.meanCover() + ", " + inRange + " of " + s.columns()
						+ " columns in 4-10 blocks");
			}
		} else if (thinSurvey != null) {
			if (s.meanCover() < thinSurvey.meanCover() + 5) {
				failures.add(name + ": realistic cover " + s.meanCover() + " not thicker than thin " + thinSurvey.meanCover());
			}
			if (thinSurvey.voidColumns() > s.voidColumns() + 16) {
				failures.add(name + ": " + thinSurvey.voidColumns() + " columns with voids under a thin cover, "
						+ s.voidColumns() + " under a realistic one");
			}
		}
	}

	/** Lowland places of the seed, searched in the model along a spiral (the same in both covers). */
	private static List<Place> findPlaces(MinecraftServer server) {
		ServerLevel level = server.overworld();
		LandscapeModel m = ((PolandChunkGenerator) level.getChunkSource().getGenerator()).model(level.getSeed());
		List<Place> places = new ArrayList<>();
		add(places, m, "sand", s -> s.substrate() == Substrate.SAND, false);
		add(places, m, "till", s -> s.substrate() == Substrate.GLACIAL_TILL, false);
		add(places, m, "valley", s -> s.substrate() == Substrate.ALLUVIUM, true);
		return places;
	}

	private static void add(List<Place> places, LandscapeModel m, String name, Predicate<ColumnSample> test,
			boolean byRiver) {
		for (int r = 0; r < 80_000; r += 64) {
			int samples = Math.max(16, r / 32);
			for (int k = 0; k < samples; k++) {
				double a = k * (2 * Math.PI / samples) + r * 0.37;
				int x = (int) (Math.cos(a) * r);
				int z = (int) (Math.sin(a) * r);
				ColumnSample s = m.sample(x, z);
				if (s.hasWater() || s.coverDepth() <= LOWLAND_COVER || !test.test(s)) {
					continue;
				}
				int cx = x >> 4;
				int cz = z >> 4;
				if (fits(m, cx, cz, test) && (!byRiver || riverNear(m, cx, cz))) {
					places.add(new Place(name, cx, cz));
					return;
				}
			}
		}
		PolishForests.LOG.warn("[cover] no place found for {}", name);
	}

	/** The pit (32 × 32 blocks) and 4 blocks around it: dry lowland, at least 60% of it on the substrate. */
	private static boolean fits(LandscapeModel m, int cx, int cz, Predicate<ColumnSample> test) {
		int on = 0;
		int n = 0;
		for (int x = (cx << 4) - 12; x <= (cx << 4) + 27; x += 4) {
			for (int z = (cz << 4) - 20; z <= (cz << 4) + 19; z += 4) {
				ColumnSample s = m.sample(x, z);
				if (s.hasWater() || s.coverDepth() <= LOWLAND_COVER) {
					return false;
				}
				on += test.test(s) ? 1 : 0;
				n++;
			}
		}
		return on >= 0.6 * n;
	}

	/** A river within 64 blocks of the pit. */
	private static boolean riverNear(LandscapeModel m, int cx, int cz) {
		for (int x = (cx << 4) - 64; x <= (cx << 4) + 80; x += 8) {
			for (int z = (cz << 4) - 80; z <= (cz << 4) + 80; z += 8) {
				if (m.sample(x, z).waterKind() == WaterKind.RIVER) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean rock(BlockState state) {
		return state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.ORES) || state.is(Blocks.BEDROCK);
	}

	/** Y of the ground top of a column (soil, deposits or rock; not plants, trees or water), or minY − 1. */
	private static int groundTop(ServerLevel level, int x, int z) {
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, 0, z);
		for (int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z); y >= level.getMinY(); y--) {
			BlockState state = level.getBlockState(p.setY(y));
			if (LOOSE.contains(state.getBlock()) || rock(state)) {
				return y;
			}
		}
		return level.getMinY() - 1;
	}

	/** Survey of the 3 × 3 chunks around the pit's chunk (lowland columns of the model only). */
	private static Survey survey(MinecraftServer server, Place place) {
		ServerLevel level = server.overworld();
		LandscapeModel m = ((PolandChunkGenerator) level.getChunkSource().getGenerator()).model(level.getSeed());
		int x0 = (place.cx() - 1) << 4;
		int z0 = (place.cz() - 1) << 4;
		int e = 48;
		for (int cx = place.cx() - 2; cx <= place.cx() + 2; cx++) {
			for (int cz = place.cz() - 2; cz <= place.cz() + 2; cz++) {
				level.getChunk(cx, cz);
			}
		}
		int[] top = new int[(e + 2) * (e + 2)];
		for (int a = -1; a <= e; a++) {
			for (int b = -1; b <= e; b++) {
				top[(a + 1) * (e + 2) + b + 1] = groundTop(level, x0 + a, z0 + b);
			}
		}
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		int columns = 0;
		long sum = 0;
		int min = Integer.MAX_VALUE;
		int max = 0;
		int[] hist = new int[64];
		int ores = 0;
		int voids = 0;
		int faces = 0;
		int bare = 0;
		for (int a = 0; a < e; a++) {
			for (int b = 0; b < e; b++) {
				int x = x0 + a;
				int z = z0 + b;
				if (m.sample(x, z).coverDepth() <= LOWLAND_COVER) {
					continue;
				}
				int t = top[(a + 1) * (e + 2) + b + 1];
				// The rock body starts with RUN rock blocks in a row: a boulder of the till (a feature on the soil) is not
				// the rock under the cover, and a blob of dirt or gravel ore in the stone does not end it.
				int rockY = Integer.MIN_VALUE;
				int run = 0;
				boolean hollow = false;
				for (int y = t; y >= Math.max(level.getMinY(), t - 120); y--) {
					BlockState state = level.getBlockState(p.set(x, y, z));
					if (y >= t - 40 && state.isAir()) {
						hollow = true;
					}
					run = rock(state) ? run + 1 : 0;
					if (rockY == Integer.MIN_VALUE && run == RUN) {
						rockY = y + RUN - 1;
					}
					if (y >= t - 16 && state.is(BlockTags.ORES)) {
						ores++;
					}
				}
				if (rockY == Integer.MIN_VALUE) {
					rockY = t - 121;
				}
				int cover = t - rockY;
				columns++;
				sum += cover;
				min = Math.min(min, cover);
				max = Math.max(max, cover);
				hist[Math.min(63, cover)]++;
				voids += hollow ? 1 : 0;
				bare += cover == 0 ? 1 : 0;
				int[][] nbs = {{a - 1, b}, {a + 1, b}, {a, b - 1}, {a, b + 1}};
				for (int[] nb : nbs) {
					if (rockY > top[(nb[0] + 1) * (e + 2) + nb[1] + 1]) {
						faces++;
					}
				}
			}
		}
		int groundY = top[(16 + 8 + 1) * (e + 2) + 16 + 1];
		return new Survey(columns, columns == 0 ? 0 : (double) sum / columns, min, max, hist, ores, voids, faces, bare,
				groundY);
	}

	/**
	 * Digs the pit down to Y 0: 32 blocks along x (centered on the place's chunk) and 32 along z (the place's chunk and the
	 * one north of it).
	 */
	private static void dig(TestSingleplayerContext sp, Place place, int groundY) {
		int x0 = (place.cx() << 4) - 8;
		int z0 = (place.cz() << 4) - 16;
		int high = groundY + 24;
		// 32 × 32 × 32 blocks per command: the default limit of a fill.
		for (int y = 0; y <= high; y += 32) {
			sp.getServer().runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d air", x0, y, z0, x0 + 31,
					Math.min(high, y + 31), z0 + 31));
		}
	}

	/** Screenshot of the north wall of the pit from 21 blocks in front of it, 8 blocks below the ground. */
	private static void shoot(ClientGameTestContext context, TestSingleplayerContext sp, Place place, int groundY,
			String name) {
		int x = (place.cx() << 4) + 8;
		int z = (place.cz() << 4) + 4;
		sp.getServer().runCommand(String.format(Locale.ROOT, "tp @a %d %d %d 180 0", x, groundY - 8, z));
		context.runOnClient(mc -> {
			if (!mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
		// Step T7: until the view is drawn (ViewReady) instead of a fixed 25 s.
		ViewReady.shot(context, sp, name, null, 25);
		context.runOnClient(mc -> {
			if (mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
	}

	private static String histogram(int[] hist) {
		StringBuilder b = new StringBuilder();
		for (int c = 0; c < hist.length; c++) {
			if (hist[c] > 0) {
				b.append(b.isEmpty() ? "" : " ").append(c == 63 ? "63+" : Integer.toString(c)).append(':').append(hist[c]);
			}
		}
		return b.toString();
	}
}
