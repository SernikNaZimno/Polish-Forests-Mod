package pl.polishforests.worldgen.chunk;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.Mth;
import net.minecraft.util.SimpleBitStorage;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeResolver;
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
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import org.jspecify.annotations.Nullable;
import pl.polishforests.climate.ClimateBinding;
import pl.polishforests.worldgen.feature.ModFeatures;
import pl.polishforests.worldgen.habitat.HabitatClassifier;
import pl.polishforests.worldgen.landscape.ColumnSample;
import pl.polishforests.worldgen.landscape.LandscapeModel;
import pl.polishforests.worldgen.surface.ChunkSurface;
import pl.polishforests.worldgen.surface.Material;
import pl.polishforests.worldgen.surface.SoilBlend;
import pl.polishforests.worldgen.surface.StructureGround;
import pl.polishforests.worldgen.surface.SurfaceBuilder;

/**
 * Generator of the "Poland" world: 2.5D terrain from a procedural 1:1 scale landscape model
 * (docs/01-architektura.md, section 3.1). It fills columns with blocks without computing 3D density,
 * which at a height of 3056 blocks is many times cheaper than the vanilla generator.
 */
public final class PolandChunkGenerator extends ChunkGenerator {
	/** The settings are always written; a world without them is an M1 world ({@link PolandSettings#GENERATOR_FIELD}). */
	public static final MapCodec<PolandChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource),
			PolandSettings.GENERATOR_FIELD.forGetter(g -> g.settings)
	).apply(i, i.stable(PolandChunkGenerator::new)));

	private static final BlockState AIR = Blocks.AIR.defaultBlockState();
	private static final Strategy<BlockState> BLOCK_STRATEGY = Strategy.createForBlockStates(
			net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY);
	private static final BlockState WATER = Blocks.WATER.defaultBlockState();
	private static final BlockState STONE = Blocks.STONE.defaultBlockState();

	private final PolandSettings settings;
	private final VerticalScale vertical;
	private volatile @Nullable LandscapeModel model;
	private volatile @Nullable HabitatClassifier classifier;
	private volatile @Nullable SurfaceBuilder surfaceBuilder;
	private volatile long modelSeed;

	public PolandChunkGenerator(BiomeSource biomeSource, PolandSettings settings) {
		super(biomeSource);
		this.settings = settings;
		this.vertical = settings.scale().vertical();
	}

	public VerticalScale vertical() {
		return vertical;
	}

	public PolandSettings settings() {
		return settings;
	}

	/**
	 * Landscape model for the world seed; created once, together with the habitat classifier (mode from the settings),
	 * and bound to the biome source.
	 */
	public LandscapeModel model(long seed) {
		LandscapeModel m = model;
		if (m != null && modelSeed == seed) {
			return m;
		}
		synchronized (this) {
			m = model;
			if (m == null || modelSeed != seed) {
				m = new LandscapeModel(seed, settings.scale().landscape(), settings.regionScale());
				HabitatClassifier k = new HabitatClassifier(seed, settings.scale().landscape(), settings.mode());
				classifier = k;
				surfaceBuilder = new SurfaceBuilder(seed, vertical, settings.coverInBlocks(),
						SurfaceBuilder.debugFromSystem());
				modelSeed = seed;
				model = m;
				if (biomeSource instanceof PolandBiomeSource source) {
					source.bind(seed, m, k, vertical);
				}
			}
			return m;
		}
	}

	/** Habitat classifier for the world seed (the same instance the biome source uses). */
	public HabitatClassifier classifier(long seed) {
		model(seed);
		synchronized (this) {
			return classifier;
		}
	}

	/** Whether the world is in the "present-day Poland" mode (PRESENT_DAY). */
	public boolean presentDay() {
		return settings.mode() == HabitatClassifier.Mode.PRESENT_DAY;
	}

	/**
	 * Biome check of a structure start (round 1 of the S8 review, decision M2-17): in the PRESENT_DAY mode a village
	 * ({@code #minecraft:village}) does not start in a forest biome ({@link ModBiomeKeys#FORESTS}); other structures and
	 * the natural mode keep the biome tags of the structure. Used by {@code ChunkGeneratorStructureMixin} (generation) and,
	 * since round 2, by {@code StructureCheckMixin} (the cheap check of {@code /locate} and explorer maps), so both paths
	 * apply the same rule. Returns {@code biomes} itself when nothing changes.
	 */
	public Predicate<Holder<Biome>> structureBiomes(Holder<Structure> structure, Predicate<Holder<Biome>> biomes) {
		if (!presentDay() || !structure.is(StructureTags.VILLAGE)) {
			return biomes;
		}
		return biome -> biomes.test(biome) && !biome.is(ModBiomeKeys.FORESTS);
	}

	/**
	 * Whether the structure can start anywhere in this world under {@link #structureBiomes}: in the PRESENT_DAY mode a
	 * village whose biomes outside the forests are not among the biomes of the source cannot (all biomes of
	 * {@code has_structure/village_taiga} are forests, so there are no taiga villages; round 2 of the S8 review).
	 */
	boolean canStart(Holder<Structure> structure) {
		if (!presentDay() || !structure.is(StructureTags.VILLAGE)) {
			return true;
		}
		Set<Holder<Biome>> possible = biomeSource.possibleBiomes();
		for (Holder<Biome> biome : structure.value().biomes()) {
			if (!biome.is(ModBiomeKeys.FORESTS) && possible.contains(biome)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Search for the nearest structure ({@code /locate}, explorer maps, eyes of ender) without the structures that cannot
	 * start in this world ({@link #canStart}; round 2 of the S8 review). The vanilla search only drops structures whose
	 * biome tags miss the biome source, so a taiga village in the present-day mode passed and a search with the radius
	 * 100 of a cartographer's map scanned about 40,000 grid cells with no possible start. If no wanted structure can start,
	 * the result is {@code null} at once.
	 */
	@Override
	public @Nullable Pair<BlockPos, Holder<Structure>> findNearestMapStructure(ServerLevel level, HolderSet<Structure> wantedStructures,
			BlockPos pos, int maxSearchRadius, boolean createReference) {
		if (presentDay()) {
			List<Holder<Structure>> kept = new ArrayList<>();
			boolean dropped = false;
			for (Holder<Structure> structure : wantedStructures) {
				if (canStart(structure)) {
					kept.add(structure);
				} else {
					dropped = true;
				}
			}
			if (kept.isEmpty()) {
				return null;
			}
			if (dropped) {
				wantedStructures = HolderSet.direct(kept);
			}
		}
		return super.findNearestMapStructure(level, wantedStructures, pos, maxSearchRadius, createReference);
	}

	@Override
	protected MapCodec<? extends ChunkGenerator> codec() {
		return CODEC;
	}

	@Override
	public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structureSets, RandomState randomState,
			long legacyLevelSeed) {
		model(randomState.seed());
		// The climate profile (temperature from meters) must be attached before generation, because
		// freeze_top_layer already places snow and ice.
		ClimateBinding.attach(biomeSource.possibleBiomes(), vertical);
		return super.createState(structureSets, randomState, legacyLevelSeed);
	}

	/**
	 * Biomes of the chunk. With the mod's biome source the biome does not depend on Y (Z2), so the 16 columns are
	 * classified once ({@code createResolverForChunk}), written into one section's biome container, and every section
	 * gets a copy of it, instead of the vanilla 64 writes per section (about 8400 in the realistic scale). The time is
	 * counted in {@link #BIOME_NANOS}, the classification alone in {@link #BIOME_CLASSIFY_NANOS} (budget §3.6: at most
	 * 0.2 ms per chunk).
	 */
	@Override
	public CompletableFuture<ChunkAccess> createBiomes(RandomState randomState, Blender blender,
			StructureManager structureManager, ChunkAccess protoChunk) {
		model(randomState.seed());
		if (!(biomeSource instanceof PolandBiomeSource source)) {
			return super.createBiomes(randomState, blender, structureManager, protoChunk);
		}
		return CompletableFuture.supplyAsync(() -> {
			long t0 = System.nanoTime();
			ChunkPos pos = protoChunk.getPos();
			int quartX = QuartPos.fromBlock(pos.getMinBlockX());
			int quartZ = QuartPos.fromBlock(pos.getMinBlockZ());
			BiomeResolver resolver = source.createResolverForChunk(null, quartX, QuartPos.fromBlock(protoChunk.getMinY()),
					quartZ, 4, QuartPos.fromBlock(protoChunk.getHeight()), 4);
			long t1 = System.nanoTime();
			LevelChunkSection[] sections = protoChunk.getSections();
			PalettedContainer<Holder<Biome>> column = sections[0].getBiomes().recreate();
			for (int x = 0; x < 4; x++) {
				for (int z = 0; z < 4; z++) {
					Holder<Biome> biome = resolver.getNoiseBiome(quartX + x, 0, quartZ + z);
					for (int y = 0; y < 4; y++) {
						column.getAndSetUnchecked(x, y, z, biome);
					}
				}
			}
			for (int i = 0; i < sections.length; i++) {
				sections[i] = new LevelChunkSection(sections[i].getStates(), column.copy());
			}
			long t2 = System.nanoTime();
			BIOME_CLASSIFY_NANOS.add(t1 - t0);
			BIOME_NANOS.add(t2 - t0);
			BIOME_CHUNKS.increment();
			return protoChunk;
		}, Util.backgroundExecutor().forName("polishforests_createBiomes"));
	}

	@Override
	public CompletableFuture<ChunkAccess> buildTerrain(ChunkAccess chunk, Blender blender, RandomState randomState,
			StructureManager structureManager, BiomeManager biomeManager, @Nullable WorldGenRegion carverBiomeRegion,
			Set<Holder<Biome>> possibleBiomes) {
		LandscapeModel m = model(randomState.seed());
		HabitatClassifier k = classifier(randomState.seed());
		return CompletableFuture.supplyAsync(() -> {
			fill(chunk, m, k, structureManager);
			return chunk;
		}, Util.backgroundExecutor().forName("polishforests_buildTerrain"));
	}

	/** Diagnostic counters: time spent sampling the model and filling blocks (ns), number of chunks. */
	public static final java.util.concurrent.atomic.LongAdder SAMPLE_NANOS = new java.util.concurrent.atomic.LongAdder();
	public static final java.util.concurrent.atomic.LongAdder FILL_NANOS = new java.util.concurrent.atomic.LongAdder();
	/**
	 * Time of the decoration step ({@code applyBiomeDecoration}, the FEATURES stage of one chunk without the generation
	 * of its neighbors) and the number of decorated chunks; the budget FEATURES of §3.6 (S7) compares them with the base.
	 */
	public static final java.util.concurrent.atomic.LongAdder DECORATION_NANOS = new java.util.concurrent.atomic.LongAdder();
	public static final java.util.concurrent.atomic.LongAdder DECORATION_CHUNKS = new java.util.concurrent.atomic.LongAdder();

	@Override
	public void applyBiomeDecoration(net.minecraft.world.level.WorldGenLevel level, net.minecraft.world.level.chunk.ChunkAccess chunk,
			StructureManager structureManager) {
		long t0 = System.nanoTime();
		boolean columnBiomes = biomeSource instanceof PolandBiomeSource;
		COLUMN_BIOMES.set(columnBiomes);
		try {
			if (columnBiomes) {
				blendSoil(level, chunk);
			}
			super.applyBiomeDecoration(level, chunk, structureManager);
		} finally {
			if (columnBiomes) {
				COLUMN_BIOMES.set(false);
			}
		}
		DECORATION_NANOS.add(System.nanoTime() - t0);
		DECORATION_CHUNKS.increment();
	}

	/**
	 * Soil ecotones (rule Z10, step S8b, {@link SoilBlend}): before the decoration of the chunk, the top block of each dry
	 * column outside structure pieces takes the soil of a column across a habitat border within the belt of the pair, where
	 * the world still holds the top of the column's own soil. Not in the diagnostic mode (its top shows the zones).
	 */
	private void blendSoil(net.minecraft.world.level.WorldGenLevel level, net.minecraft.world.level.chunk.ChunkAccess chunk) {
		SurfaceBuilder builder = surfaceBuilder;
		ChunkHabitats habitats = chunk.getAttached(pl.polishforests.worldgen.feature.ModFeatures.CHUNK_HABITATS);
		if (builder == null || builder.debug() || habitats == null) {
			return;
		}
		boolean[] skip = new boolean[256];
		for (int i = 0; i < 256; i++) {
			skip[i] = habitats.hasWater(i) || habitats.pieceDistance(i) <= 1;
		}
		int[] tops = SoilBlend.tops(pl.polishforests.worldgen.feature.ModFeatures.ecotoneRegion(level, chunk, habitats,
				settings.scale().landscape().local()), level.getSeed(), builder, skip);
		ChunkPos pos = chunk.getPos();
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		Material[] materials = Material.values();
		for (int i = 0; i < 256; i++) {
			if (tops[i] == SoilBlend.KEEP) {
				continue;
			}
			at.set(pos.getMinBlockX() + (i >> 4), habitats.top()[i], pos.getMinBlockZ() + (i & 15));
			if (chunk.getBlockState(at) == MaterialStates.of(materials[tops[i] >> 8])) {
				chunk.setBlockState(at, MaterialStates.of(materials[tops[i] & 0xFF]), 0);
				SOIL_BLENDED.increment();
			}
		}
	}

	/** Number of top blocks the soil ecotones changed (diagnostics). */
	public static final java.util.concurrent.atomic.LongAdder SOIL_BLENDED = new java.util.concurrent.atomic.LongAdder();

	/**
	 * Whether the decoration running on this thread is of a chunk whose sections all hold the same column biomes
	 * ({@link PolandBiomeSource}: {@link #createBiomes} copies them into every section), so the decoration reads the
	 * biomes of one section ({@code ChunkGeneratorDecorationMixin}, R11).
	 */
	private static final ThreadLocal<Boolean> COLUMN_BIOMES = ThreadLocal.withInitial(() -> false);

	/** See {@link #COLUMN_BIOMES}. */
	public static boolean decoratingColumnBiomes() {
		return COLUMN_BIOMES.get();
	}

	public static final java.util.concurrent.atomic.LongAdder CHUNKS = new java.util.concurrent.atomic.LongAdder();
	/** Time of the BIOMES stage with the mod's biome source (ns) and the number of chunks (budget §3.6). */
	public static final java.util.concurrent.atomic.LongAdder BIOME_NANOS = new java.util.concurrent.atomic.LongAdder();
	public static final java.util.concurrent.atomic.LongAdder BIOME_CHUNKS = new java.util.concurrent.atomic.LongAdder();
	/** Part of {@link #BIOME_NANOS} spent sampling and classifying the 16 quart columns. */
	public static final java.util.concurrent.atomic.LongAdder BIOME_CLASSIFY_NANOS = new java.util.concurrent.atomic.LongAdder();
	/** Time of the habitat classification in {@code fill()} (ns, included in {@link #SAMPLE_NANOS}). */
	public static final java.util.concurrent.atomic.LongAdder CLASSIFY_NANOS = new java.util.concurrent.atomic.LongAdder();
	/** Time of the surface plan (soil, shelf, micro-relief) in {@code fill()} (ns, included in {@link #FILL_NANOS}). */
	public static final java.util.concurrent.atomic.LongAdder SURFACE_NANOS = new java.util.concurrent.atomic.LongAdder();
	/**
	 * Sections with more than one block state written by {@link #pack} ({@link #PACKED_SECTIONS}), and those that did not
	 * fit in a palette of at most 256 states and were written block by block ({@link #PACK_FALLBACKS}; budget §3.6: below
	 * 1% of the sections).
	 */
	public static final java.util.concurrent.atomic.LongAdder PACKED_SECTIONS = new java.util.concurrent.atomic.LongAdder();
	public static final java.util.concurrent.atomic.LongAdder PACK_FALLBACKS = new java.util.concurrent.atomic.LongAdder();

	private static final int MATERIALS = Material.values().length;
	private static final int AIR_ID = Material.AIR.ordinal();

	private void fill(ChunkAccess chunk, LandscapeModel m, HabitatClassifier classifier, StructureManager structureManager) {
		long t0 = System.nanoTime();
		ChunkPos pos = chunk.getPos();
		int minX = pos.getMinBlockX();
		int minZ = pos.getMinBlockZ();
		int minY = chunk.getMinY();
		int maxY = chunk.getMaxY();
		ColumnSample[] columns = sampleColumns(m, minX, minZ);
		long tc = System.nanoTime();
		int[] codes = classify(columns, classifier, minX, minZ);
		long t1 = System.nanoTime();
		CLASSIFY_NANOS.add(t1 - tc);
		SAMPLE_NANOS.add(t1 - t0);
		ChunkSurface surface = surfaceBuilder().build(columns, codes, minX, minZ, minY, maxY, PolandDimension.DEEP_ROCK_Y,
				m::sample);
		byte[] structures = StructureGround.apply(surface, structurePieces(structureManager, pos), minX, minZ);
		chunk.setAttached(ModFeatures.CHUNK_HABITATS, habitats(columns, codes, surface,
				structures != null ? structures : ChunkHabitats.NO_STRUCTURES));
		SURFACE_NANOS.add(System.nanoTime() - t1);
		int highest = Math.max(minY, surface.highest());
		int bottomSection = chunk.getSectionIndex(minY);
		int topSection = chunk.getSectionIndex(highest);
		LevelChunkSection[] sections = new LevelChunkSection[topSection - bottomSection + 1];
		for (int i = bottomSection; i <= topSection; i++) {
			sections[i - bottomSection] = chunk.getSection(i);
			sections[i - bottomSection].acquire();
		}
		Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
		Heightmap worldSurface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
		BlockState[] states = MaterialStates.all();
		LevelChunkSection[] chunkSections = chunk.getSections();
		int[] buffer = new int[4096];
		int[] ids = new int[4096];
		int[] slots = new int[MATERIALS];
		try {
			for (int idx = bottomSection; idx <= topSection; idx++) {
				int y0 = chunk.getSectionYFromSectionIndex(idx) << 4;
				// First compute the materials of the whole section; a uniform section gets a single-value palette,
				// which is many times cheaper than 4096 separate palette writes.
				int first = -1;
				boolean uniform = true;
				for (int i = 0; i < 256; i++) {
					// Index order as in the section palette: y, then z, then x.
					int column = ((i & 15) << 4) | (i >> 4);
					for (int dy = 0; dy < 16; dy++) {
						int material = surface.material(i, y0 + dy).ordinal();
						buffer[(dy << 8) | column] = material;
						if (first < 0) {
							first = material;
						} else if (uniform && material != first) {
							uniform = false;
						}
					}
				}
				LevelChunkSection section = sections[idx - bottomSection];
				if (uniform) {
					if (first != AIR_ID) {
						chunkSections[idx] = new LevelChunkSection(new PalettedContainer<>(states[first], BLOCK_STRATEGY),
								section.getBiomes());
					}
					continue;
				}
				PACKED_SECTIONS.increment();
				PalettedContainer<BlockState> packed = pack(buffer, ids, slots, states);
				if (packed != null) {
					chunkSections[idx] = new LevelChunkSection(packed, section.getBiomes());
					continue;
				}
				PACK_FALLBACKS.increment();
				for (int k = 0; k < 4096; k++) {
					if (buffer[k] != AIR_ID) {
						section.setBlockState(k & 15, k >> 8, (k >> 4) & 15, states[buffer[k]], false);
					}
				}
			}
			// Heightmaps after the shelf, the hummocks and the puddles (§7.1).
			for (int i = 0; i < 256; i++) {
				int x = i >> 4;
				int z = i & 15;
				int top = surface.top(i);
				BlockState topState = chunkSections[chunk.getSectionIndex(top)].getBlockState(x, top & 15, z);
				oceanFloor.update(x, top, z, topState);
				worldSurface.update(x, top, z, topState);
				if (surface.wet(i)) {
					worldSurface.update(x, surface.waterTop(i), z, WATER);
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
	 * Structure pieces near the chunk (within the vanilla beard reach, as {@code Beardifier.forStructuresInChunk}) for
	 * {@link StructureGround}: the starts of every structure that references the chunk. Rigid pool elements and pieces
	 * that are not pool elements are buildings; the beard adapts the ground under them when the structure's terrain
	 * adaptation is {@code beard_thin} or {@code beard_box} (the terrain-matching streets follow the ground anyway).
	 */
	private static List<StructureGround.Piece> structurePieces(StructureManager structureManager, ChunkPos pos) {
		List<StructureStart> starts = structureManager.startsForStructure(pos.x(), pos.z(), s -> true);
		if (starts.isEmpty()) {
			return List.of();
		}
		List<StructureGround.Piece> pieces = new ArrayList<>();
		for (StructureStart start : starts) {
			if (!start.isValid()) {
				continue;
			}
			TerrainAdjustment adaptation = start.getStructure().terrainAdaptation();
			boolean beard = adaptation == TerrainAdjustment.BEARD_THIN || adaptation == TerrainAdjustment.BEARD_BOX;
			for (StructurePiece piece : start.getPieces()) {
				if (!piece.isCloseToChunk(pos, StructureGround.BEARD_RADIUS)) {
					continue;
				}
				BoundingBox b = piece.getBoundingBox();
				boolean building = true;
				int ground = b.minY();
				if (piece instanceof PoolElementStructurePiece pool) {
					building = pool.getElement().getProjection() == StructureTemplatePool.Projection.RIGID;
					ground += pool.getGroundLevelDelta();
				}
				pieces.add(new StructureGround.Piece(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ(), ground,
						beard && building, building));
			}
		}
		return pieces;
	}

	/**
	 * Samples of the 256 columns of a chunk (index {@code x * 16 + z}): the samples that the surface plans of neighboring
	 * chunks already took for the bank shelf come from their shared cache, the rest from the model.
	 */
	private ColumnSample[] sampleColumns(LandscapeModel m, int minX, int minZ) {
		SurfaceBuilder b = surfaceBuilder();
		ColumnSample[] columns = new ColumnSample[256];
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				ColumnSample s = b.reuse(minX + x, minZ + z);
				columns[x * 16 + z] = s != null ? s : m.sample(minX + x, minZ + z);
			}
		}
		return columns;
	}

	/** Surface builder of the bound world seed (created together with the model). */
	private SurfaceBuilder surfaceBuilder() {
		return surfaceBuilder;
	}

	/**
	 * Habitat codes of the 256 columns of a chunk (index {@code x * 16 + z}), at the block coordinates of each column (the
	 * same point at which the biome source classifies a quart column through its center).
	 */
	private static int[] classify(ColumnSample[] columns, HabitatClassifier k, int minX, int minZ) {
		int[] codes = new int[256];
		for (int i = 0; i < 256; i++) {
			codes[i] = k.classify(columns[i], minX + (i >> 4), minZ + (i & 15));
		}
		return codes;
	}

	/**
	 * Habitats of a chunk: the codes, the top ground block and the water surface of the surface plan (after the shelf and
	 * the micro-relief, so trees stand on hummocks and not in puddles), and O and P at the chunk center.
	 */
	private static ChunkHabitats habitats(ColumnSample[] columns, int[] codes, ChunkSurface surface, byte[] structures) {
		short[] tops = new short[256];
		short[] water = new short[256];
		for (int i = 0; i < 256; i++) {
			tops[i] = (short) surface.top(i);
			water[i] = surface.wet(i) ? (short) surface.waterTop(i) : ChunkHabitats.NO_WATER;
		}
		ColumnSample center = columns[8 * 16 + 8];
		return new ChunkHabitats(codes, tops, water, (float) center.region().oceanicity(),
				(float) center.region().mountainInfluence(), structures);
	}

	/**
	 * Surface plan of a chunk computed again from the model, exactly as {@code fill()} computes it (for game tests), with
	 * the ground of the structure pieces ({@link StructureGround}) from the structure manager of the level.
	 *
	 * @param codes receives the habitat codes of the columns when not null
	 */
	public ChunkSurface surface(ChunkPos pos, int minY, int maxY, long seed, int @Nullable [] codes,
			StructureManager structureManager) {
		LandscapeModel m = model(seed);
		ColumnSample[] columns = new ColumnSample[256];
		for (int i = 0; i < 256; i++) {
			columns[i] = m.sample(pos.getMinBlockX() + (i >> 4), pos.getMinBlockZ() + (i & 15));
		}
		int[] c = classify(columns, classifier(seed), pos.getMinBlockX(), pos.getMinBlockZ());
		if (codes != null) {
			System.arraycopy(c, 0, codes, 0, 256);
		}
		ChunkSurface surface = surfaceBuilder().build(columns, c, pos.getMinBlockX(), pos.getMinBlockZ(), minY, maxY,
				PolandDimension.DEEP_ROCK_Y, m::sample);
		StructureGround.apply(surface, structurePieces(structureManager, pos), pos.getMinBlockX(), pos.getMinBlockZ());
		return surface;
	}

	/**
	 * Habitats of a chunk computed again from the model, for a chunk that lost its attachment (proto-chunk saved
	 * between TERRAIN and FEATURES; counted by {@code ModFeatures.HABITAT_MISS}). Without the structure pieces: no beard
	 * and no vegetation mask ({@link ChunkHabitats#NO_STRUCTURES}).
	 */
	public ChunkHabitats computeHabitats(ChunkPos pos, int minY, int maxY, long seed) {
		LandscapeModel m = model(seed);
		ColumnSample[] columns = new ColumnSample[256];
		for (int i = 0; i < 256; i++) {
			columns[i] = m.sample(pos.getMinBlockX() + (i >> 4), pos.getMinBlockZ() + (i & 15));
		}
		int[] codes = classify(columns, classifier(seed), pos.getMinBlockX(), pos.getMinBlockZ());
		ChunkSurface surface = surfaceBuilder().build(columns, codes, pos.getMinBlockX(), pos.getMinBlockZ(), minY, maxY,
				PolandDimension.DEEP_ROCK_Y, m::sample);
		return habitats(columns, codes, surface, ChunkHabitats.NO_STRUCTURES);
	}

	/**
	 * Builds a palette container from precomputed section materials (order y, z, x): a linear palette for at most 16
	 * states (4 bits), a hash map palette for 17–256 states (5–8 bits, §7.1). The data is written in the format the
	 * container keeps in memory, so there is no repacking. Returns null when there are more states and regular writes
	 * must be used ({@link #PACK_FALLBACKS}).
	 */
	private static @Nullable PalettedContainer<BlockState> pack(int[] buffer, int[] ids, int[] slots, BlockState[] states) {
		java.util.Arrays.fill(slots, -1);
		List<BlockState> palette = new ArrayList<>(16);
		for (int k = 0; k < 4096; k++) {
			int material = buffer[k];
			int id = slots[material];
			if (id < 0) {
				if (palette.size() == 256) {
					return null;
				}
				id = palette.size();
				slots[material] = id;
				palette.add(states[material]);
			}
			ids[k] = id;
		}
		int bits = Math.max(4, Mth.ceillog2(palette.size()));
		long[] raw = new SimpleBitStorage(bits, 4096, ids).getRaw();
		return PalettedContainer.unpack(BLOCK_STRATEGY,
				new PalettedContainerRO.PackedData<>(palette, Optional.of(LongStream.of(raw)), bits)).getOrThrow();
	}

	private int topY(ColumnSample s, int minY, int maxY) {
		return Mth.clamp(vertical.topBlockY(s.surface()), minY + 1, maxY);
	}

	private int waterTopY(ColumnSample s, int maxY) {
		return s.hasWater() ? Math.min(vertical.topBlockY(s.waterLevel()), maxY) : Integer.MIN_VALUE;
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
		result.add(String.format(java.util.Locale.ROOT, "Poland: %s, ground %.1f m a.s.l., substrate %s, water %s",
				s.type(), s.surface(), s.substrate(), s.hasWater() ? s.waterKind() + " " + s.waterLevel() + " m" : "none"));
		result.add(String.format(java.util.Locale.ROOT, "Feet elevation: %.0f m a.s.l.", vertical.metersAboveSea(feetPos.getY() - 1)));
	}

	@Override
	public void spawnOriginalMobs(WorldGenRegion worldGenRegion) {
		ChunkPos center = worldGenRegion.getCenter();
		// The last generation stage: the chunk habitats are no longer needed (§8.3).
		worldGenRegion.getChunk(center.x(), center.z()).removeAttached(ModFeatures.CHUNK_HABITATS);
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
