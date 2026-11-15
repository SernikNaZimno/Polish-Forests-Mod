package pl.polishforests.gametest;

import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import pl.polishforests.PolishForests;
import pl.polishforests.climate.BiomeClimate;
import pl.polishforests.climate.BiomeClimateAccess;
import pl.polishforests.climate.ColumnClimate;
import pl.polishforests.climate.ColumnClimates;
import pl.polishforests.climate.PolandClimate;
import pl.polishforests.climate.VanillaClimate;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.vanilla.VanillaForestWorld;
import pl.polishforests.worldgen.vanilla.VanillaForestsBiomeSource;

/**
 * Step C2 (CP-D.11) in the game test {@code vanilla_forests}: snow and ice of the world type "Default + Polish Forests" by
 * the column climate (the vanilla biome a column had, with the vanilla height adjustment).
 *
 * <ol>
 * <li>Census of the forest columns (habitat biomes) of 6 × 6 chunks in three places: the former snowy taiga (now the
 * montane spruce forest), the former windswept forest (snow only higher up) and the oak-hornbeam forest (former forest,
 * warm). A cold dry column that can hold snow (air at the top of {@code MOTION_BLOCKING}, snow survives, block light
 * below 10) must hold it in at least {@link #SNOW_SHARE} of the columns of the former snowy taiga; a warm column never; a
 * cold water column must be frozen in at least {@link #SNOW_SHARE}.</li>
 * <li>The client and the server agree: the player stands in the former snowy taiga, and in a raster of points of the 3 × 3
 * chunks around him the client thread (its own chunks and their synchronized attachment) and the server thread give the
 * same column climate and the same rain or snow.</li>
 * <li>A chunk without the attachment (as in a W1 world from before step C2) keeps the biome's own temperature on the
 * server and on the client (the removal is synchronized too); the attachment is restored afterwards.</li>
 * </ol>
 */
final class VanillaSnowCheck {
	/** Least share of the cold dry snowable columns with snow, and of the cold water columns with ice. */
	static final double SNOW_SHARE = 0.9;
	/** Least number of cold dry snowable columns in the former snowy taiga (6 × 6 chunks hold 9216 columns). */
	static final int MIN_COLD_COLUMNS = 500;
	/** Square of the census, chunks. */
	static final int CHUNKS = 6;
	/** Search radius for the vanilla biomes, blocks, on a lattice of {@link #STEP}. */
	static final int SEARCH = 9600;
	static final int STEP = 64;

	private VanillaSnowCheck() {
	}

	static void run(ClientGameTestContext context, TestSingleplayerContext sp, StringBuilder report, List<String> failures) {
		BlockPos snowy = sp.getServer().computeOnServer(s -> find(s.overworld(), "snowy_taiga", HabitatBiome.MONTANE_SPRUCE_FOREST));
		BlockPos windswept = sp.getServer().computeOnServer(s -> find(s.overworld(), "windswept_forest",
				HabitatBiome.MONTANE_BEECH_FOREST));
		BlockPos warm = sp.getServer().computeOnServer(s -> find(s.overworld(), "forest", HabitatBiome.OAK_HORNBEAM_FOREST));
		if (snowy == null) {
			failures.add("snow: no former snowy taiga within " + SEARCH + " blocks");
			report.append("snow: no former snowy taiga within ").append(SEARCH).append(" blocks\n");
			return;
		}
		Census cold = sp.getServer().computeOnServer(s -> census(s.overworld(), snowy));
		line(report, "former snowy taiga", snowy, cold);
		if (cold.coldDry < MIN_COLD_COLUMNS || cold.snowy < SNOW_SHARE * cold.coldDry) {
			failures.add(String.format(Locale.ROOT, "snow: former snowy taiga %d of %d cold dry columns with snow (needs >= %d and %.0f%%)",
					cold.snowy, cold.coldDry, MIN_COLD_COLUMNS, 100 * SNOW_SHARE));
		}
		if (cold.formerSnowyTaiga == 0) {
			failures.add("snow: no column with the base temperature of the snowy taiga (-0.5) in its census");
		}
		for (Object[] place : new Object[][] {{"former windswept forest", windswept}, {"oak-hornbeam forest (former forest)", warm}}) {
			BlockPos at = (BlockPos) place[1];
			if (at == null) {
				report.append("snow: ").append(place[0]).append(" not found\n");
				continue;
			}
			Census c = sp.getServer().computeOnServer(s -> census(s.overworld(), at));
			line(report, (String) place[0], at, c);
			if (c.coldDry > 0 && c.snowy < SNOW_SHARE * c.coldDry) {
				failures.add(String.format(Locale.ROOT, "snow: %s %d of %d cold dry columns with snow", place[0], c.snowy, c.coldDry));
			}
			cold.add(c);
		}
		if (cold.warmSnowy > 0) {
			failures.add("snow: " + cold.warmSnowy + " warm forest columns with snow (" + cold.warmLowSnowy + " of them low)");
		}
		if (cold.coldWater >= 20 && cold.frozen < SNOW_SHARE * cold.coldWater) {
			failures.add(String.format(Locale.ROOT, "snow: %d of %d cold water columns frozen", cold.frozen, cold.coldWater));
		}
		agreement(context, sp, snowy, report, failures);
		withoutAttachment(context, sp, snowy, report, failures);
	}

	/** The nearest lattice column whose vanilla surface biome was {@code vanilla} and is now the habitat biome {@code now}. */
	private static BlockPos find(ServerLevel level, String vanilla, HabitatBiome now) {
		VanillaForestWorld world = ((VanillaForestsBiomeSource) level.getChunkSource().getGenerator().getBiomeSource()).world();
		for (int r = 0; r <= SEARCH; r += STEP) {
			for (int dx = -r; dx <= r; dx += STEP) {
				for (int dz = -r; dz <= r; dz += STEP) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					Holder<Biome> v = world.vanillaSurfaceBiome(dx, dz);
					if (v.unwrapKey().map(k -> k.identifier().getPath().equals(vanilla)).orElse(false)
							&& world.surfaceBiome(dx, dz).unwrapKey().map(k -> k.identifier().getPath().equals(now.id())).orElse(false)) {
						return new BlockPos(dx, 0, dz);
					}
				}
			}
		}
		return null;
	}

	/** Counts of the forest columns of a census. */
	static final class Census {
		int forest;
		int coldDry;
		int snowy;
		int coldUnsnowable;
		int warm;
		int warmSnowy;
		int warmLowSnowy;
		int coldWater;
		int frozen;
		int formerSnowyTaiga;
		int formerSnowyTaigaSnowy;
		int withoutAttachment;

		void add(Census c) {
			forest += c.forest;
			coldDry += c.coldDry;
			snowy += c.snowy;
			coldUnsnowable += c.coldUnsnowable;
			warm += c.warm;
			warmSnowy += c.warmSnowy;
			warmLowSnowy += c.warmLowSnowy;
			coldWater += c.coldWater;
			frozen += c.frozen;
			formerSnowyTaiga += c.formerSnowyTaiga;
			formerSnowyTaigaSnowy += c.formerSnowyTaigaSnowy;
			withoutAttachment += c.withoutAttachment;
		}
	}

	private static void line(StringBuilder report, String name, BlockPos at, Census c) {
		String line = String.format(Locale.ROOT, "snow: %s around (%d, %d): forest columns %d; cold dry snowable %d, with snow %d "
				+ "(%.1f%%), cold with a plant or block at the top %d; warm %d, with snow %d (low %d); cold water %d, frozen %d; "
				+ "base of the snowy taiga %d, with snow %d; forest columns without a column climate %d", name, at.getX(), at.getZ(),
				c.forest, c.coldDry, c.snowy, c.coldDry == 0 ? 0 : 100.0 * c.snowy / c.coldDry, c.coldUnsnowable, c.warm,
				c.warmSnowy, c.warmLowSnowy, c.coldWater, c.frozen, c.formerSnowyTaiga, c.formerSnowyTaigaSnowy, c.withoutAttachment);
		report.append(line).append('\n');
		PolishForests.LOG.info("[vanilla_forests] " + line);
	}

	/** The census of the forest columns of {@link #CHUNKS} × {@link #CHUNKS} chunks around a column (see the class). */
	private static Census census(ServerLevel level, BlockPos center) {
		int[] origin = VanillaForestsClientGameTest.loadSquare(level, center, CHUNKS);
		Census c = new Census();
		int seaLevel = level.getSeaLevel();
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		BlockState snow = Blocks.SNOW.defaultBlockState();
		for (int dx = 0; dx < CHUNKS * 16; dx++) {
			for (int dz = 0; dz < CHUNKS * 16; dz++) {
				int x = origin[0] + dx;
				int z = origin[1] + dz;
				int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
				BlockState top = level.getBlockState(at.set(x, y, z));
				BlockState below = level.getBlockState(at.set(x, y - 1, z));
				int py = below.is(Blocks.SNOW) ? y - 1 : y;
				BlockPos pos = new BlockPos(x, py, z);
				Holder<Biome> biome = level.getBiome(pos);
				if (!biome.unwrapKey().map(k -> k.identifier().getNamespace().equals(PolishForests.MOD_ID)).orElse(false)) {
					continue;
				}
				c.forest++;
				ColumnClimate climate = level.getChunk(x >> 4, z >> 4).getAttached(ColumnClimates.TYPE);
				float base = climate != null ? climate.base(x, z) : biome.value().getBaseTemperature();
				if (climate == null) {
					c.withoutAttachment++;
				}
				boolean water = below.is(Blocks.WATER) || below.is(Blocks.ICE);
				if (water) {
					if (VanillaClimate.temperature(base, x, y - 1, z, seaLevel) < PolandClimate.SNOW_THRESHOLD) {
						c.coldWater++;
						c.frozen += below.is(Blocks.ICE) ? 1 : 0;
					}
					continue;
				}
				float t = VanillaClimate.temperature(base, x, py, z, seaLevel);
				boolean hasSnow = top.is(Blocks.SNOW) || below.is(Blocks.SNOW);
				if (t < PolandClimate.SNOW_THRESHOLD) {
					boolean snowable = hasSnow || top.isAir() && snow.canSurvive(level, pos)
							&& level.getBrightness(LightLayer.BLOCK, pos) < 10;
					if (!snowable) {
						c.coldUnsnowable++;
						continue;
					}
					c.coldDry++;
					c.snowy += hasSnow ? 1 : 0;
					if (base == -0.5F) {
						c.formerSnowyTaiga++;
						c.formerSnowyTaigaSnowy += hasSnow ? 1 : 0;
					}
				} else {
					c.warm++;
					if (hasSnow) {
						c.warmSnowy++;
						c.warmLowSnowy += py <= seaLevel + VanillaClimate.SNOW_LEVEL_ABOVE_SEA ? 1 : 0;
					}
				}
			}
		}
		return c;
	}

	/** The points of the agreement raster: every 3rd block of the 3 × 3 chunks around a column, at the top. */
	private static BlockPos[] raster(ServerLevel level, BlockPos center) {
		int x0 = ((center.getX() >> 4) - 1) << 4;
		int z0 = ((center.getZ() >> 4) - 1) << 4;
		BlockPos[] points = new BlockPos[16 * 16];
		int n = 0;
		for (int dx = 0; dx < 48; dx += 3) {
			for (int dz = 0; dz < 48; dz += 3) {
				int x = x0 + dx;
				int z = z0 + dz;
				points[n++] = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
			}
		}
		return points;
	}

	/** Rain (true) or snow at a point as the calling thread sees it, and the column's base temperature. */
	private static float[] sample(Biome biome, BlockPos p, int seaLevel, ColumnClimate climate) {
		BiomeClimate profile = BiomeClimateAccess.climate(biome);
		float t = profile == null ? Float.NaN : profile.temperature(p, seaLevel);
		return new float[] {biome.warmEnoughToRain(p, seaLevel) ? 1 : 0, t, climate == null ? Float.NaN : climate.base(p.getX(), p.getZ())};
	}

	private static void agreement(ClientGameTestContext context, TestSingleplayerContext sp, BlockPos at, StringBuilder report,
			List<String> failures) {
		BlockPos[] points = sp.getServer().computeOnServer(s -> raster(s.overworld(), at));
		int cx = at.getX() >> 4;
		int cz = at.getZ() >> 4;
		sp.getServer().runCommand(String.format(Locale.ROOT, "tp @a %d %d %d", at.getX(), points[points.length / 2].getY() + 8,
				at.getZ()));
		// The client must hold the 3 × 3 chunks and their column climates (sent one packet after each chunk).
		boolean ready = false;
		for (int i = 0; i < 1200 && !ready; i++) {
			context.waitTick();
			ready = context.computeOnClient(mc -> {
				for (int dx = -1; dx <= 1; dx++) {
					for (int dz = -1; dz <= 1; dz++) {
						ChunkAccess c = mc.level.getChunkSource().getChunk(cx + dx, cz + dz, ChunkStatus.FULL, false);
						if (c == null) {
							return false;
						}
					}
				}
				return true;
			});
		}
		context.waitTicks(20);
		float[][] server = sp.getServer().computeOnServer(s -> {
			ServerLevel level = s.overworld();
			float[][] r = new float[points.length][];
			for (int i = 0; i < points.length; i++) {
				BlockPos p = points[i];
				r[i] = sample(level.getBiome(p).value(), p, level.getSeaLevel(),
						level.getChunk(p.getX() >> 4, p.getZ() >> 4).getAttached(ColumnClimates.TYPE));
			}
			return r;
		});
		float[][] client = context.computeOnClient(mc -> {
			float[][] r = new float[points.length][];
			for (int i = 0; i < points.length; i++) {
				BlockPos p = points[i];
				ChunkAccess c = mc.level.getChunkSource().getChunk(p.getX() >> 4, p.getZ() >> 4, ChunkStatus.FULL, false);
				r[i] = sample(mc.level.getBiome(p).value(), p, mc.level.getSeaLevel(), c == null ? null : c.getAttached(ColumnClimates.TYPE));
			}
			return r;
		});
		int differ = 0;
		int snowPoints = 0;
		int attached = 0;
		for (int i = 0; i < points.length; i++) {
			boolean same = server[i][0] == client[i][0] && Float.floatToIntBits(server[i][1]) == Float.floatToIntBits(client[i][1])
					&& Float.floatToIntBits(server[i][2]) == Float.floatToIntBits(client[i][2]);
			differ += same ? 0 : 1;
			snowPoints += client[i][0] == 0 ? 1 : 0;
			attached += Float.isNaN(client[i][2]) ? 0 : 1;
		}
		String line = String.format(Locale.ROOT, "snow: client and server at %d points of the 3 x 3 chunks around (%d, %d) (client "
				+ "ready %s): differ %d, snow on the client %d, column climate on the client %d", points.length, at.getX(), at.getZ(),
				ready, differ, snowPoints, attached);
		report.append(line).append('\n');
		PolishForests.LOG.info("[vanilla_forests] " + line);
		if (!ready || differ > 0 || attached == 0 || snowPoints == 0) {
			failures.add(line);
		}
	}

	private static void withoutAttachment(ClientGameTestContext context, TestSingleplayerContext sp, BlockPos at,
			StringBuilder report, List<String> failures) {
		int cx = at.getX() >> 4;
		int cz = at.getZ() >> 4;
		// A forest point of the chunk, its column climate removed.
		Object[] removed = sp.getServer().computeOnServer(s -> {
			ServerLevel level = s.overworld();
			LevelChunk chunk = level.getChunk(cx, cz);
			for (int dx = 0; dx < 16; dx++) {
				for (int dz = 0; dz < 16; dz++) {
					int x = (cx << 4) + dx;
					int z = (cz << 4) + dz;
					BlockPos p = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
					Biome b = level.getBiome(p).value();
					if (BiomeClimateAccess.climate(b) != null && BiomeClimateAccess.climate(b).vanillaColumns()) {
						return new Object[] {p, chunk.removeAttached(ColumnClimates.TYPE)};
					}
				}
			}
			return null;
		});
		if (removed == null || removed[1] == null) {
			failures.add("snow: no forest point with a column climate in chunk (" + cx + ", " + cz + ") for the check without it");
			return;
		}
		BlockPos p = (BlockPos) removed[0];
		context.waitTicks(10);
		float[] server = sp.getServer().computeOnServer(s -> {
			Biome b = s.overworld().getBiome(p).value();
			return new float[] {BiomeClimateAccess.climate(b).temperature(p, s.overworld().getSeaLevel()),
					VanillaClimate.temperature(b.getBaseTemperature(), p.getX(), p.getY(), p.getZ(), s.overworld().getSeaLevel())};
		});
		float[] client = context.computeOnClient(mc -> {
			Biome b = mc.level.getBiome(p).value();
			ChunkAccess c = mc.level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
			return new float[] {BiomeClimateAccess.climate(b).temperature(p, mc.level.getSeaLevel()),
					c != null && c.getAttached(ColumnClimates.TYPE) == null ? 1 : 0};
		});
		sp.getServer().runOnServer(s -> s.overworld().getChunk(cx, cz).setAttached(ColumnClimates.TYPE, (ColumnClimate) removed[1]));
		String line = String.format(Locale.ROOT, "snow: chunk (%d, %d) without its column climate: server %.4f, own biome %.4f, "
				+ "client %.4f, removal synchronized %s", cx, cz, server[0], server[1], client[0], client[1] == 1);
		report.append(line).append('\n');
		PolishForests.LOG.info("[vanilla_forests] " + line);
		if (server[0] != server[1] || client[0] != server[1] || client[1] != 1) {
			failures.add(line);
		}
	}
}
