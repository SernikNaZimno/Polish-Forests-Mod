package pl.polskielasy.worldgen.chunk;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.Mth;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.chunk.Strategy;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.jspecify.annotations.Nullable;
import pl.polskielasy.climate.WiazanieKlimatu;
import pl.polskielasy.worldgen.landscape.ColumnSample;
import pl.polskielasy.worldgen.landscape.LandscapeModel;
import pl.polskielasy.worldgen.landscape.Noise;
import pl.polskielasy.worldgen.landscape.Substrate;

/**
 * Generator świata "Polska": teren 2,5D z proceduralnego modelu krajobrazu w skali 1:1
 * (docs/01-architektura.md, sekcja 3.1). Wypełnia kolumny blokami bez liczenia gęstości 3D,
 * co przy wysokości 3056 bloków jest wielokrotnie tańsze niż generator wanilijny.
 */
public final class PolskaChunkGenerator extends ChunkGenerator {
	public static final MapCodec<PolskaChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource),
			PolskaSettings.CODEC.optionalFieldOf("settings", PolskaSettings.DEFAULT).forGetter(g -> g.settings)
	).apply(i, i.stable(PolskaChunkGenerator::new)));

	private static final BlockState AIR = Blocks.AIR.defaultBlockState();
	private static final Strategy<BlockState> BLOCK_STRATEGY = Strategy.createForBlockStates(
			net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY);
	private static final BlockState WATER = Blocks.WATER.defaultBlockState();
	private static final BlockState BEDROCK = Blocks.BEDROCK.defaultBlockState();
	private static final BlockState STONE = Blocks.STONE.defaultBlockState();
	private static final BlockState DEEPSLATE = Blocks.DEEPSLATE.defaultBlockState();
	private static final BlockState ANDESITE = Blocks.ANDESITE.defaultBlockState();
	private static final BlockState GRASS = Blocks.GRASS_BLOCK.defaultBlockState();
	private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();
	private static final BlockState COARSE_DIRT = Blocks.COARSE_DIRT.defaultBlockState();
	private static final BlockState SAND = Blocks.SAND.defaultBlockState();
	private static final BlockState GRAVEL = Blocks.GRAVEL.defaultBlockState();
	private static final BlockState CLAY = Blocks.CLAY.defaultBlockState();
	private static final BlockState MUD = Blocks.MUD.defaultBlockState();

	/** Szum drobnych plam materiału (np. żwir na dnie koryta); niezależny od ziarna świata. */
	private static final Noise DETAIL = new Noise(0x5EED_DE7A_11L);

	private final PolskaSettings settings;
	private final VerticalScale vertical;
	private volatile @Nullable LandscapeModel model;
	private volatile long modelSeed;

	public PolskaChunkGenerator(BiomeSource biomeSource, PolskaSettings settings) {
		super(biomeSource);
		this.settings = settings;
		this.vertical = settings.scale().vertical();
	}

	public VerticalScale vertical() {
		return vertical;
	}

	public PolskaSettings settings() {
		return settings;
	}

	/** Model krajobrazu dla ziarna świata; tworzony raz i podpinany do źródła biomów. */
	public LandscapeModel model(long seed) {
		LandscapeModel m = model;
		if (m != null && modelSeed == seed) {
			return m;
		}
		synchronized (this) {
			m = model;
			if (m == null || modelSeed != seed) {
				m = new LandscapeModel(seed, settings.scale().landscape(), settings.regionScale());
				modelSeed = seed;
				model = m;
				if (biomeSource instanceof PolskaBiomeSource source) {
					source.bind(m, vertical);
				}
			}
			return m;
		}
	}

	@Override
	protected MapCodec<? extends ChunkGenerator> codec() {
		return CODEC;
	}

	@Override
	public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structureSets, RandomState randomState,
			long legacyLevelSeed) {
		model(randomState.seed());
		// Profil klimatu (temperatura z metrów) musi być przypięty przed generacją, bo śnieg
		// i lód stawia już freeze_top_layer.
		WiazanieKlimatu.przypnij(biomeSource.possibleBiomes(), vertical);
		return super.createState(structureSets, randomState, legacyLevelSeed);
	}

	@Override
	public CompletableFuture<ChunkAccess> createBiomes(RandomState randomState, Blender blender,
			StructureManager structureManager, ChunkAccess protoChunk) {
		model(randomState.seed());
		return super.createBiomes(randomState, blender, structureManager, protoChunk);
	}

	@Override
	public CompletableFuture<ChunkAccess> buildTerrain(ChunkAccess chunk, Blender blender, RandomState randomState,
			StructureManager structureManager, BiomeManager biomeManager, @Nullable WorldGenRegion carverBiomeRegion,
			Set<Holder<Biome>> possibleBiomes) {
		LandscapeModel m = model(randomState.seed());
		return CompletableFuture.supplyAsync(() -> {
			fill(chunk, m);
			return chunk;
		}, Util.backgroundExecutor().forName("polskielasy_buildTerrain"));
	}

	/** Liczniki diagnostyczne: czas próbkowania modelu i wypełniania bloków (ns), liczba chunków. */
	public static final java.util.concurrent.atomic.LongAdder SAMPLE_NANOS = new java.util.concurrent.atomic.LongAdder();
	public static final java.util.concurrent.atomic.LongAdder FILL_NANOS = new java.util.concurrent.atomic.LongAdder();
	public static final java.util.concurrent.atomic.LongAdder CHUNKS = new java.util.concurrent.atomic.LongAdder();

	private void fill(ChunkAccess chunk, LandscapeModel m) {
		long t0 = System.nanoTime();
		ChunkPos pos = chunk.getPos();
		int minX = pos.getMinBlockX();
		int minZ = pos.getMinBlockZ();
		int minY = chunk.getMinY();
		int maxY = chunk.getMaxY();
		ColumnSample[] columns = new ColumnSample[256];
		int highest = minY;
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				ColumnSample s = m.sample(minX + x, minZ + z);
				columns[x * 16 + z] = s;
				highest = Math.max(highest, Math.max(topY(s, minY, maxY), waterTopY(s, maxY)));
			}
		}
		long t1 = System.nanoTime();
		SAMPLE_NANOS.add(t1 - t0);
		int bottomSection = chunk.getSectionIndex(minY);
		int topSection = chunk.getSectionIndex(highest);
		LevelChunkSection[] sections = new LevelChunkSection[topSection - bottomSection + 1];
		for (int i = bottomSection; i <= topSection; i++) {
			sections[i - bottomSection] = chunk.getSection(i);
			sections[i - bottomSection].acquire();
		}
		Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
		Heightmap worldSurface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
		int[] tops = new int[256];
		int[] waterTops = new int[256];
		int[] bedrockTops = new int[256];
		for (int i = 0; i < 256; i++) {
			ColumnSample s = columns[i];
			int wx = minX + (i >> 4);
			int wz = minZ + (i & 15);
			tops[i] = topY(s, minY, maxY);
			waterTops[i] = waterTopY(s, maxY);
			bedrockTops[i] = minY + (int) (Noise.mix(wx * 341873128712L + wz * 132897987541L) >>> 62);
		}
		LevelChunkSection[] chunkSections = chunk.getSections();
		BlockState[] buffer = new BlockState[4096];
		int[] ids = new int[4096];
		try {
			for (int idx = bottomSection; idx <= topSection; idx++) {
				int y0 = chunk.getSectionYFromSectionIndex(idx) << 4;
				// Najpierw liczymy stany całej sekcji; sekcja jednolita dostaje paletę jednowartościową,
				// co jest wielokrotnie tańsze niż 4096 osobnych zapisów do palety.
				BlockState first = null;
				boolean uniform = true;
				for (int i = 0; i < 256; i++) {
					ColumnSample s = columns[i];
					int wx = minX + (i >> 4);
					int wz = minZ + (i & 15);
					int top = tops[i];
					int waterTop = waterTops[i];
					for (int dy = 0; dy < 16; dy++) {
						int y = y0 + dy;
						BlockState state = y <= top ? strata(s, y, top, waterTop, bedrockTops[i], wx, wz)
								: y <= waterTop ? WATER : AIR;
						// Kolejność indeksów jak w palecie sekcji: y, potem z, potem x.
						buffer[(dy << 8) | ((i & 15) << 4) | (i >> 4)] = state;
						if (first == null) {
							first = state;
						} else if (uniform && state != first) {
							uniform = false;
						}
					}
				}
				LevelChunkSection section = sections[idx - bottomSection];
				if (uniform) {
					if (first != AIR) {
						chunkSections[idx] = new LevelChunkSection(new PalettedContainer<>(first, BLOCK_STRATEGY),
								section.getBiomes());
					}
					continue;
				}
				PalettedContainer<BlockState> packed = pack(buffer, ids);
				if (packed != null) {
					chunkSections[idx] = new LevelChunkSection(packed, section.getBiomes());
					continue;
				}
				for (int k = 0; k < 4096; k++) {
					BlockState state = buffer[k];
					if (state != AIR) {
						section.setBlockState(k & 15, k >> 8, (k >> 4) & 15, state, false);
					}
				}
			}
			for (int i = 0; i < 256; i++) {
				int x = i >> 4;
				int z = i & 15;
				int top = tops[i];
				BlockState topState = chunkSections[chunk.getSectionIndex(top)].getBlockState(x, top & 15, z);
				oceanFloor.update(x, top, z, topState);
				worldSurface.update(x, top, z, topState);
				if (waterTops[i] > top) {
					worldSurface.update(x, waterTops[i], z, WATER);
				}
			}
		} finally {
			for (LevelChunkSection section : sections) {
				section.release();
			}
		}
		FILL_NANOS.add(System.nanoTime() - t1);
		CHUNKS.increment();
	}

	/**
	 * Buduje kontener palety z gotowych stanów sekcji (kolejność y, z, x). Dla najwyżej 16 różnych
	 * stanów format czterobitowy jest taki sam w pamięci i w danych, więc nie ma przepakowywania.
	 * Zwraca null, gdy stanów jest więcej i trzeba użyć zwykłego zapisu.
	 */
	private static @Nullable PalettedContainer<BlockState> pack(BlockState[] buffer, int[] ids) {
		List<BlockState> palette = new ArrayList<>(8);
		BlockState last = null;
		int lastId = -1;
		for (int k = 0; k < 4096; k++) {
			BlockState state = buffer[k];
			if (state != last) {
				lastId = palette.indexOf(state);
				if (lastId < 0) {
					if (palette.size() == 16) {
						return null;
					}
					palette.add(state);
					lastId = palette.size() - 1;
				}
				last = state;
			}
			ids[k] = lastId;
		}
		long[] raw = new SimpleBitStorage(4, 4096, ids).getRaw();
		return PalettedContainer.unpack(BLOCK_STRATEGY,
				new PalettedContainerRO.PackedData<>(palette, Optional.of(LongStream.of(raw)), 4)).getOrThrow();
	}

	private int topY(ColumnSample s, int minY, int maxY) {
		return Mth.clamp(vertical.topBlockY(s.surface()), minY + 1, maxY);
	}

	private int waterTopY(ColumnSample s, int maxY) {
		return s.hasWater() ? Math.min(vertical.topBlockY(s.waterLevel()), maxY) : Integer.MIN_VALUE;
	}

	/**
	 * Blok w kolumnie na wysokości {@code y} (od dołu: skała podłoża, utwory powierzchniowe,
	 * gleba). Wersja M1 używa bloków wanilijnych; własne gleby i skały dochodzą w M2.
	 */
	private static BlockState strata(ColumnSample s, int y, int top, int waterTop, int bedrockTop, int wx, int wz) {
		if (y <= bedrockTop) {
			return BEDROCK;
		}
		int depth = top - y;
		boolean underwater = waterTop > top;
		Substrate sub = s.substrate();
		if (depth < s.coverDepth()) {
			if (depth == 0 && !underwater) {
				return switch (sub) {
					case PEAT, LAKE_MUD -> MUD;
					case RIVERBED, BEACH_SAND -> SAND;
					default -> GRASS;
				};
			}
			return switch (sub) {
				case SAND, BEACH_SAND -> SAND;
				case RIVERBED -> depth < 3 && DETAIL.at(wx, wz, 7) > 0.15 ? GRAVEL : SAND;
				case LAKE_MUD -> depth < 2 ? MUD : CLAY;
				case PEAT -> depth < 3 ? MUD : CLAY;
				case ALLUVIUM -> depth < 4 ? DIRT : SAND;
				case GLACIAL_TILL -> depth < 3 ? DIRT : CLAY;
				case FLYSCH -> depth < 2 ? DIRT : COARSE_DIRT;
			};
		}
		if (y < PolskaDimension.DEEP_ROCK_Y + ((wx * 31 + wz * 17) & 7)) {
			return DEEPSLATE;
		}
		if (sub == Substrate.FLYSCH) {
			// Warstwowanie fliszu: ławice piaskowca i łupków, lekko nachylone.
			int band = Math.floorMod(y + (wx >> 5) - (wz >> 6), 9);
			return band < 3 ? ANDESITE : STONE;
		}
		return STONE;
	}

	@Override
	public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor heightAccessor,
			RandomState randomState) {
		ColumnSample s = model(randomState.seed()).sample(x, z);
		int top = topY(s, heightAccessor.getMinY(), heightAccessor.getMaxY());
		if (s.hasWater() && type.isOpaque().test(WATER)) {
			top = Math.max(top, waterTopY(s, heightAccessor.getMaxY()));
		}
		return top + 1;
	}

	@Override
	public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor heightAccessor, RandomState randomState) {
		ColumnSample s = model(randomState.seed()).sample(x, z);
		int minY = heightAccessor.getMinY();
		int top = topY(s, minY, heightAccessor.getMaxY());
		int waterTop = waterTopY(s, heightAccessor.getMaxY());
		BlockState[] states = new BlockState[heightAccessor.getHeight()];
		for (int i = 0; i < states.length; i++) {
			int y = minY + i;
			states[i] = y <= top ? STONE : y <= waterTop ? WATER : AIR;
		}
		return new NoiseColumn(minY, states);
	}

	@Override
	public void addDebugScreenInfo(List<String> result, RandomState randomState, BlockPos feetPos,
			SamplerContext samplerContext) {
		ColumnSample s = model(randomState.seed()).sample(feetPos.getX(), feetPos.getZ());
		result.add(String.format("Polska: %s, grunt %.1f m n.p.m., podłoże %s, wody %s",
				s.type(), s.surface(), s.substrate(), s.hasWater() ? s.waterKind() + " " + s.waterLevel() + " m" : "brak"));
		result.add(String.format(java.util.Locale.ROOT, "Wysokość stóp: %.0f m n.p.m.", vertical.metersAboveSea(feetPos.getY() - 1)));
	}

	@Override
	public void spawnOriginalMobs(WorldGenRegion worldGenRegion) {
		ChunkPos center = worldGenRegion.getCenter();
		BlockPos sourcePos = center.getWorldPosition().atY(worldGenRegion.getMaxY());
		WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));
		random.setDecorationSeed(worldGenRegion.getSeed(), center.getMinBlockX(), center.getMinBlockZ());
		NaturalSpawner.spawnMobsForChunkGeneration(worldGenRegion, sourcePos, center, random);
	}

	@Override
	public int getSpawnHeight(LevelHeightAccessor heightAccessor) {
		return vertical.seaLevelY() + (int) vertical.blocksForMeters(100);
	}

	@Override
	public int getGenDepth() {
		return vertical.height();
	}

	@Override
	public int getSeaLevel() {
		return vertical.seaLevelY();
	}

	@Override
	public int getMinY() {
		return vertical.minY();
	}
}
