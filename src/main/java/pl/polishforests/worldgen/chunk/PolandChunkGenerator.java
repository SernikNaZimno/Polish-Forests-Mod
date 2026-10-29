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
import pl.polishforests.worldgen.cave.CaveBiomes;
import pl.polishforests.worldgen.cave.CaveCarving;
import pl.polishforests.worldgen.cave.PolandCaves;
import pl.polishforests.worldgen.cave.SulfurBands;
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
	/** The vanilla caves of the world (step J1), built on first use from the registries of the world; null without caves. */
	private volatile @Nullable PolandCaves caves;

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
				HabitatClassifier k = new HabitatClassifier(seed, m, settings.mode());
				classifier = k;
				surfaceBuilder = new SurfaceBuilder(seed, vertical, settings.coverInBlocks(), settings.looseCover(),
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

	private volatile pl.polishforests.worldgen.feature.plan.@Nullable ForestBlend forestBlend;
	private volatile pl.polishforests.worldgen.tree.@Nullable ForestStandMap standMap;

	/**
	 * The stand map of the world (step V9; shared by the dead wood of step V11): managed or natural stands and their age
	 * classes for the world seed, the scale and the managed share of the settings.
	 */
	public pl.polishforests.worldgen.tree.ForestStandMap standMap(long seed) {
		pl.polishforests.worldgen.tree.ForestStandMap m = standMap;
		double k = settings.scale().landscape().local();
		double managed = settings.managedForestShare();
		if (m != null && m.matches(seed, k, managed)) {
			return m;
		}
		m = new pl.polishforests.worldgen.tree.ForestStandMap(seed, k, managed);
		standMap = m;
		return m;
	}

	/**
	 * The wide blend of the forests of the vertical slice for the world seed (step Z1 of docs/04-pionowy-wycinek.md): the
	 * habitat lattice from this generator's landscape model and classifier, shared by the decoration of all chunks.
	 */
	public pl.polishforests.worldgen.feature.plan.ForestBlend forestBlend(long seed) {
		pl.polishforests.worldgen.feature.plan.ForestBlend b = forestBlend;
		if (b != null && b.worldSeed() == seed) {
			return b;
		}
		LandscapeModel m = model(seed);
		HabitatClassifier k = classifier(seed);
		b = new pl.polishforests.worldgen.feature.plan.ForestBlend(seed, settings.scale().landscape().local(),
				(x, z) -> k.classify(m.sample(x, z), x, z));
		forestBlend = b;
		return b;
	}

	/** Habitat classifier for the world seed (the same instance the biome source uses). */
	public HabitatClassifier classifier(long seed) {
		model(seed);
		synchronized (this) {
			return classifier;
		}
	}

	/**
	 * The caves of the world (step J1) when the settings have them: built once from {@code registries}, or from the
	 * registries of the running server when the caller has none ({@link PolandCaves#serverRegistries}). Null without
	 * caves, and null while no registries are known (a base height asked before the server started).
	 */
	public @Nullable PolandCaves caves(net.minecraft.core.HolderLookup.@Nullable Provider registries) {
		if (!settings.caves()) {
			return null;
		}
		PolandCaves c = caves;
		if (c != null) {
			return c;
		}
		net.minecraft.core.HolderLookup.Provider r = registries != null ? registries : PolandCaves.serverRegistries();
		if (r == null) {
			return null;
		}
		synchronized (this) {
			if (caves == null) {
				caves = PolandCaves.create(r, vertical);
			}
			return caves;
		}
	}

	/** The cave biomes of the world (step J2) and the random state they were compiled for; null without caves. */
	private volatile @Nullable CaveBiomes caveBiomes;
	private volatile @Nullable RandomState caveBiomesState;

	/**
	 * The cave biomes of the world (step J2) when the settings have caves (and the diagnostic switch
	 * {@link CaveBiomes#enabledFromSystem} does not turn them off): compiled once for the world's random state from
	 * {@code registries}, or from the registries of the running server when the caller has none, and bound to the biome
	 * source. Null without caves, and null while no registries are known.
	 */
	public @Nullable CaveBiomes caveBiomes(net.minecraft.core.HolderLookup.@Nullable Provider registries, RandomState randomState) {
		if (!settings.caves() || !CaveBiomes.enabledFromSystem()) {
			return null;
		}
		CaveBiomes c = caveBiomes;
		if (c != null && caveBiomesState == randomState) {
			return c;
		}
		net.minecraft.core.HolderLookup.Provider r = registries != null ? registries : PolandCaves.serverRegistries();
		if (r == null) {
			return null;
		}
		synchronized (this) {
			if (caveBiomes == null || caveBiomesState != randomState) {
				caveBiomes = CaveBiomes.create(r, randomState);
				caveBiomesState = randomState;
				if (biomeSource instanceof PolandBiomeSource source) {
					source.bindCaves(caveBiomes);
				}
			}
			return caveBiomes;
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
		if (!settings.caves() || !CaveBiomes.enabledFromSystem()) {
			// Step J2: a structure only of the cave biomes (the ancient city of the deep dark) needs a world with caves.
			boolean onlyCaves = true;
			for (Holder<Biome> biome : structure.value().biomes()) {
				if (!(biomeSource instanceof PolandBiomeSource source && source.isCaveBiome(biome))
						&& biomeSource.possibleBiomes().contains(biome)) {
					onlyCaves = false;
					break;
				}
			}
			if (onlyCaves) {
				return false;
			}
		}
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
		{
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

	/** Binds the caves to the registries of the world before the structures ask for base heights (step J1). */
	@Override
	public void createStructures(net.minecraft.core.RegistryAccess registryAccess, ChunkGeneratorStructureState state,
			StructureManager structureManager, ChunkAccess centerChunk,
			net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager structureTemplateManager,
			net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> level) {
		caves(registryAccess);
		caveBiomes(registryAccess, state.randomState());
		super.createStructures(registryAccess, state, structureManager, centerChunk, structureTemplateManager, level);
	}

	@Override
	protected MapCodec<? extends ChunkGenerator> codec() {
		return CODEC;
	}

	@Override
	public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structureSets, RandomState randomState,
			long legacyLevelSeed) {
		model(randomState.seed());
		// The cave biomes (step J2) before the first structure or biome query, when the server registries are known.
		caveBiomes(null, randomState);
		// The climate profile (temperature from meters) must be attached before generation, because
		// freeze_top_layer already places snow and ice.
		ClimateBinding.attach(biomeSource.possibleBiomes(), vertical);
		return super.createState(structureSets, randomState, legacyLevelSeed);
	}

	/**
	 * Biomes of the chunk. With the mod's biome source the habitat does not depend on Y (Z2), so the 16 columns are
	 * classified once ({@code createResolverForChunk}), written into one section's biome container, and every section
	 * gets a copy of it, instead of the vanilla 64 writes per section (about 8400 in the realistic scale). In a world with
	 * caves (step J2) a section below Y {@link CaveBiomes#MAX_Y} also gets the cave biomes of its quart cells. The time is
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
		CaveBiomes caveBiomes = caveBiomes(structureManager.registryAccess(), randomState);
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
			// The habitat of each column: its biome at the top of the world, above every cave biome.
			int topQuart = QuartPos.fromBlock(protoChunk.getMaxY());
			for (int x = 0; x < 4; x++) {
				for (int z = 0; z < 4; z++) {
					Holder<Biome> biome = resolver.getNoiseBiome(quartX + x, topQuart, quartZ + z);
					for (int y = 0; y < 4; y++) {
						column.getAndSetUnchecked(x, y, z, biome);
					}
				}
			}
			long caveCells = 0;
			for (int i = 0; i < sections.length; i++) {
				PalettedContainer<Holder<Biome>> biomes = column.copy();
				int y0 = protoChunk.getSectionYFromSectionIndex(i) << 4;
				if (caveBiomes != null && y0 < CaveBiomes.MAX_Y) {
					int quartY = QuartPos.fromBlock(y0);
					for (int y = 0; y < 4; y++) {
						for (int x = 0; x < 4; x++) {
							for (int z = 0; z < 4; z++) {
								Holder<Biome> biome = resolver.getNoiseBiome(quartX + x, quartY + y, quartZ + z);
								if (biome != column.get(x, y, z)) {
									biomes.getAndSetUnchecked(x, y, z, biome);
									caveCells++;
								}
							}
						}
					}
				}
				sections[i] = new LevelChunkSection(sections[i].getStates(), biomes);
			}
			CAVE_BIOME_CELLS.add(caveCells);
			long t2 = System.nanoTime();
			BIOME_CLASSIFY_NANOS.add(t1 - t0);
			BIOME_NANOS.add(t2 - t0);
			BIOME_CHUNKS.increment();
			return protoChunk;
		}, Util.backgroundExecutor().forName("polishforests_createBiomes"));
	}

	/** Quart cells that got a cave biome (step J2; diagnostics). */
	public static final java.util.concurrent.atomic.LongAdder CAVE_BIOME_CELLS = new java.util.concurrent.atomic.LongAdder();

	@Override
	public CompletableFuture<ChunkAccess> buildTerrain(ChunkAccess chunk, Blender blender, RandomState randomState,
			StructureManager structureManager, BiomeManager biomeManager, @Nullable WorldGenRegion carverBiomeRegion,
			Set<Holder<Biome>> possibleBiomes) {
		LandscapeModel m = model(randomState.seed());
		HabitatClassifier k = classifier(randomState.seed());
		PolandCaves c = caves(structureManager.registryAccess());
		SulfurBands bands = c != null && caveBiomes(structureManager.registryAccess(), randomState) != null
				&& biomeSource instanceof PolandBiomeSource source ? SulfurBands.of(source, randomState) : null;
		return CompletableFuture.supplyAsync(() -> {
			fill(chunk, m, k, structureManager, randomState, c, bands);
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
		// Sections whose biomes the decoration reads: one (every section holds the same column habitats), and with the cave
		// biomes of step J2 every section below Y CaveBiomes.MAX_Y.
		COLUMN_BIOME_SECTIONS.set(!columnBiomes ? 0 : ((PolandBiomeSource) biomeSource).caves() == null ? 1
				: chunk.getSectionIndex(CaveBiomes.MAX_Y - 1) + 1);
		try {
			if (columnBiomes) {
				blendSoil(level, chunk);
			}
			super.applyBiomeDecoration(level, chunk, structureManager);
		} finally {
			if (columnBiomes) {
				COLUMN_BIOME_SECTIONS.set(0);
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
		int[] tops = SoilBlend.tops(pl.polishforests.worldgen.feature.ChunkEcotones.of(level, chunk, habitats,
				settings.scale().landscape().local(), forestBlend(level.getSeed())).region(), level.getSeed(), builder, skip);
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

	/**
	 * Soil ecotones of a chunk ({@link SoilBlend#tops}) from the habitat codes of the chunk and its 8 neighbors
	 * ({@code codes[(dx + 1) * 3 + dz + 1]}), for the game tests, which compare the ground of full chunks with the plan.
	 */
	public int[] soilBlend(ChunkPos pos, int[][] codes, long seed) {
		model(seed);
		return SoilBlend.tops(pl.polishforests.worldgen.feature.plan.Ecotone.Region.of(pos.x(), pos.z(), codes,
				settings.scale().landscape().local()), seed, surfaceBuilder, null);
	}

	/** Number of top blocks the soil ecotones changed (diagnostics). */
	public static final java.util.concurrent.atomic.LongAdder SOIL_BLENDED = new java.util.concurrent.atomic.LongAdder();

	/**
	 * How many of the lowest sections hold every biome of the chunk whose decoration runs on this thread, or 0 for the
	 * vanilla loop over all sections. With {@link PolandBiomeSource} every section holds the same column habitats
	 * ({@link #createBiomes} copies them), so the decoration reads the biomes of one section ({@code ChunkGeneratorDecorationMixin},
	 * R11); with the cave biomes (step J2) also the sections below Y {@link CaveBiomes#MAX_Y}, the only ones that can hold them.
	 */
	private static final ThreadLocal<Integer> COLUMN_BIOME_SECTIONS = ThreadLocal.withInitial(() -> 0);

	/** See {@link #COLUMN_BIOME_SECTIONS}. */
	public static int decoratingColumnBiomeSections() {
		return COLUMN_BIOME_SECTIONS.get();
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

	private void fill(ChunkAccess chunk, LandscapeModel m, HabitatClassifier classifier, StructureManager structureManager,
			RandomState randomState, @Nullable PolandCaves caves, @Nullable SulfurBands bands) {
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
		SURFACE_NANOS.add(System.nanoTime() - t1);
		// The caves (step J1): a separate stage over the final plan, as vanilla carves its noise terrain.
		CaveCarving carving = caves == null ? null : caves.carve(pos, surface, minY, maxY, randomState,
				caveColumns(m, columns, minX, minZ, minY, maxY), edgeNear(columns, surface, minY, maxY),
				net.minecraft.world.level.levelgen.Beardifier.forStructuresInChunk(structureManager, pos),
				new net.minecraft.world.level.levelgen.WorldGenerationContext(this, chunk.getHeightAccessorForGeneration()));
		chunk.setAttached(ModFeatures.CHUNK_HABITATS, habitats(columns, codes, surface,
				structures != null ? structures : ChunkHabitats.NO_STRUCTURES, opened(carving, surface)));
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
		int[] rock = surface.rockBounds();
		// The rock bands of the sulfur caves (step J2), per section, or null where the chunk has none.
		SulfurBands.Chunk sulfur = bands == null ? null : bands.chunk(chunk);
		try {
			for (int idx = bottomSection; idx <= topSection; idx++) {
				int y0 = chunk.getSectionYFromSectionIndex(idx) << 4;
				byte[] cave = carving == null ? null : carving.section((y0 - minY) >> 4);
				// Deep sections wholly in one rock kind: the same single-value palette as below, without 4096 material calls.
				Material uniformRock = ChunkSurface.uniformRock(y0, rock);
				boolean banded = sulfur != null && sulfur.section(idx);
				if (uniformRock != null && cave == null && !banded) {
					chunkSections[idx] = new LevelChunkSection(new PalettedContainer<>(states[uniformRock.ordinal()], BLOCK_STRATEGY),
							sections[idx - bottomSection].getBiomes());
					continue;
				}
				// First compute the materials of the whole section; a uniform section gets a single-value palette,
				// which is many times cheaper than 4096 separate palette writes. A rock section with caves (step J1) is its
				// rock with the cave blocks over it, without the 4096 material calls.
				int first = -1;
				boolean uniform = true;
				int rockId = uniformRock != null ? uniformRock.ordinal() : -1;
				for (int i = 0; i < 256; i++) {
					// Index order as in the section palette: y, then z, then x.
					int column = ((i & 15) << 4) | (i >> 4);
					for (int dy = 0; dy < 16; dy++) {
						int k = (dy << 8) | column;
						int material = cave != null && cave[k] != 0 ? cave[k] - 1
								: rockId >= 0 ? rockId : surface.material(i, y0 + dy).ordinal();
						if (banded) {
							material = sulfur.material(i >> 4, y0 + dy, i & 15, material);
						}
						buffer[k] = material;
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
				int top = carving != null ? carving.solidTop(i) : surface.top(i);
				BlockState topState = chunkSections[chunk.getSectionIndex(top)].getBlockState(x, top & 15, z);
				oceanFloor.update(x, top, z, topState);
				worldSurface.update(x, top, z, topState);
				if (carving != null && carving.fluidTop(i) > top) {
					int f = carving.fluidTop(i);
					worldSurface.update(x, f, z, chunkSections[chunk.getSectionIndex(f)].getBlockState(x, f & 15, z));
				}
				if (surface.wet(i)) {
					worldSurface.update(x, surface.waterTop(i), z, WATER);
				}
			}
		} finally {
			for (LevelChunkSection section : sections) {
				section.release();
			}
		}
		if (carving != null) {
			BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
			for (int k = 0; k < carving.markCount(); k++) {
				chunk.markPosForPostProcessing(at.set(carving.mark(k)));
			}
		}
		FILL_NANOS.add(System.nanoTime() - t1);
		CHUNKS.increment();
	}

	/**
	 * The model columns for the caves of a chunk (step J1): the samples of the chunk for its own columns, the summaries
	 * shared by the chunks of the seed beyond it ({@link SurfaceBuilder#cachedSummary}).
	 *
	 * @param chunkColumns samples of the chunk's columns, or null to read every column from the shared summaries
	 */
	private PolandCaves.Columns caveColumns(LandscapeModel m, ColumnSample @Nullable [] chunkColumns, int minX, int minZ,
			int minY, int maxY) {
		SurfaceBuilder b = surfaceBuilder();
		return new PolandCaves.Columns() {
			private long lastKey = Long.MIN_VALUE;
			private int last;

			private int summary(int x, int z) {
				long key = (long) x << 32 | (z & 0xFFFFFFFFL);
				if (key == lastKey) {
					return last;
				}
				int dx = x - minX;
				int dz = z - minZ;
				int s = chunkColumns != null && dx >= 0 && dx < 16 && dz >= 0 && dz < 16
						? b.summary(chunkColumns[dx * 16 + dz], minY, maxY) : b.cachedSummary(x, z, m::sample, minY, maxY);
				lastKey = key;
				last = s;
				return s;
			}

			@Override
			public int top(int x, int z) {
				return SurfaceBuilder.summaryTop(summary(x, z));
			}

			@Override
			public int water(int x, int z) {
				return SurfaceBuilder.summaryWater(summary(x, z));
			}
		};
	}

	/**
	 * Columns of a chunk whose neighbors beyond the chunk may hold water (step J1, guard of the water beds): wet columns,
	 * columns whose model fields put water near, and low columns by the sea.
	 */
	private boolean[] edgeNear(ColumnSample[] columns, ChunkSurface surface, int minY, int maxY) {
		SurfaceBuilder b = surfaceBuilder();
		boolean[] near = new boolean[256];
		for (int i = 0; i < 256; i++) {
			int x = i >> 4;
			int z = i & 15;
			if (x != 0 && x != 15 && z != 0 && z != 15) {
				continue;
			}
			near[i] = surface.wet(i) || SurfaceBuilder.summaryNear(b.summary(columns[i], minY, maxY))
					|| surface.top(i) <= vertical.seaLevelY() + 3;
		}
		return near;
	}

	/** The caves of the world when the settings have them and registries are known (for the base heights). */
	private @Nullable PolandCaves activeCaves() {
		return settings.caves() ? caves(null) : null;
	}

	/**
	 * Highest Y a cave may reach in model column (x, z) ({@link PolandCaves#guardBelow}): below the lowest water bed of
	 * the column and its 8 neighbors.
	 */
	private static int caveGuard(PolandCaves.Columns cols, int x, int z) {
		int lowest = Integer.MAX_VALUE;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (cols.water(x + dx, z + dz) != ChunkSurface.NO_WATER) {
					lowest = Math.min(lowest, cols.top(x + dx, z + dz));
				}
			}
		}
		return PolandCaves.guardBelow(lowest);
	}

	/**
	 * Y of the highest block of column (x, z) that counts for {@code opaque}, down from the model top through the caves
	 * (the noise caves and aquifers without the carvers, as the vanilla base height); {@code top} when the top stays.
	 */
	private int caveGround(PolandCaves c, LandscapeModel m, int x, int z, int top, int minY, int maxY,
			Predicate<BlockState> opaque, RandomState randomState) {
		PolandCaves.Columns cols = caveColumns(m, null, x, z, minY, maxY);
		int guard = caveGuard(cols, x, z);
		if (guard < top) {
			return top;
		}
		for (int hi = top; hi > minY; ) {
			int lo = Math.max(minY, hi - 31);
			BlockState[] states = c.column(x, z, lo, hi, guard, randomState, cols);
			for (int y = hi; y >= lo; y--) {
				BlockState st = states[y - lo];
				if (st == null || !st.isAir() && opaque.test(st)) {
					return y;
				}
			}
			hi = lo - 1;
		}
		return minY;
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
	/** The columns whose plan top the caves carved (step J1, {@link ChunkHabitats#caveOpened}). */
	private static long[] opened(@Nullable CaveCarving carving, ChunkSurface surface) {
		if (carving == null) {
			return ChunkHabitats.NO_CAVES;
		}
		long[] opened = null;
		for (int i = 0; i < 256; i++) {
			if (carving.carved(i, surface.top(i))) {
				if (opened == null) {
					opened = new long[4];
				}
				opened[i >> 6] |= 1L << (i & 63);
			}
		}
		return opened == null ? ChunkHabitats.NO_CAVES : opened;
	}

	private static ChunkHabitats habitats(ColumnSample[] columns, int[] codes, ChunkSurface surface, byte[] structures,
			long[] caveOpened) {
		short[] tops = new short[256];
		short[] water = new short[256];
		for (int i = 0; i < 256; i++) {
			tops[i] = (short) surface.top(i);
			water[i] = surface.wet(i) ? (short) surface.waterTop(i) : ChunkHabitats.NO_WATER;
		}
		ColumnSample center = columns[8 * 16 + 8];
		return new ChunkHabitats(codes, tops, water, (float) center.region().oceanicity(),
				(float) center.region().mountainInfluence(), structures, caveOpened);
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
	 * The columns of a chunk whose plan top the caves took away ({@link ChunkHabitats#caveOpened} layout, 4 longs), computed
	 * again exactly as {@code fill()} computes them (for game tests that compare the world with the surface plan, step J1);
	 * {@link ChunkHabitats#NO_CAVES} in a world without caves.
	 */
	public long[] caveOpened(ChunkPos pos, ChunkAccess chunk, long seed, RandomState randomState,
			StructureManager structureManager) {
		PolandCaves c = caves(structureManager.registryAccess());
		if (c == null) {
			return ChunkHabitats.NO_CAVES;
		}
		int minY = chunk.getMinY();
		int maxY = chunk.getMaxY();
		LandscapeModel m = model(seed);
		ColumnSample[] columns = new ColumnSample[256];
		for (int i = 0; i < 256; i++) {
			columns[i] = m.sample(pos.getMinBlockX() + (i >> 4), pos.getMinBlockZ() + (i & 15));
		}
		ChunkSurface surface = surface(pos, minY, maxY, seed, null, structureManager);
		CaveCarving carving = c.carve(pos, surface, minY, maxY, randomState,
				caveColumns(m, columns, pos.getMinBlockX(), pos.getMinBlockZ(), minY, maxY), edgeNear(columns, surface, minY, maxY),
				net.minecraft.world.level.levelgen.Beardifier.forStructuresInChunk(structureManager, pos),
				new net.minecraft.world.level.levelgen.WorldGenerationContext(this, chunk.getHeightAccessorForGeneration()));
		return opened(carving, surface);
	}

	/**
	 * Habitats of a chunk computed again from the model, for a chunk that lost its attachment (proto-chunk saved
	 * between TERRAIN and FEATURES; counted by {@code ModFeatures.HABITAT_MISS}). Without the structure pieces: no beard
	 * and no vegetation mask ({@link ChunkHabitats#NO_STRUCTURES}); without the caves ({@link ChunkHabitats#NO_CAVES}).
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
		return habitats(columns, codes, surface, ChunkHabitats.NO_STRUCTURES, ChunkHabitats.NO_CAVES);
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
		LandscapeModel m = model(randomState.seed());
		ColumnSample s = m.sample(x, z);
		int top = topY(s, heightAccessor.getMinY(), heightAccessor.getMaxY());
		PolandCaves c = activeCaves();
		if (c != null) {
			top = caveGround(c, m, x, z, top, heightAccessor.getMinY(), heightAccessor.getMaxY(), type.isOpaque(), randomState);
		}
		if (s.hasWater() && type.isOpaque().test(WATER)) {
			top = Math.max(top, waterTopY(s, heightAccessor.getMaxY()));
		}
		return top + 1;
	}

	@Override
	public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor heightAccessor, RandomState randomState) {
		LandscapeModel m = model(randomState.seed());
		ColumnSample s = m.sample(x, z);
		int minY = heightAccessor.getMinY();
		int top = topY(s, minY, heightAccessor.getMaxY());
		int waterTop = waterTopY(s, heightAccessor.getMaxY());
		BlockState[] states = new BlockState[heightAccessor.getHeight()];
		for (int i = 0; i < states.length; i++) {
			int y = minY + i;
			states[i] = y <= top ? STONE : y <= waterTop ? WATER : AIR;
		}
		PolandCaves c = activeCaves();
		if (c != null) {
			// The caves of the column as in the fill, without the carvers (as the vanilla base column).
			PolandCaves.Columns cols = caveColumns(m, null, x, z, minY, heightAccessor.getMaxY());
			BlockState[] caves = c.column(x, z, minY, top, caveGuard(cols, x, z), randomState, cols);
			for (int y = minY; y <= top; y++) {
				if (caves[y - minY] != null) {
					states[y - minY] = caves[y - minY];
				}
			}
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
