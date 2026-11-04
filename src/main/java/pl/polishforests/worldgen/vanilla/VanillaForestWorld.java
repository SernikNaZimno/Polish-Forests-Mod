package pl.polishforests.worldgen.vanilla;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import org.jspecify.annotations.Nullable;
import pl.polishforests.worldgen.chunk.ChunkHabitats;
import pl.polishforests.worldgen.chunk.ForestWorld;
import pl.polishforests.worldgen.chunk.LooseCover;
import pl.polishforests.worldgen.chunk.MaterialStates;
import pl.polishforests.worldgen.chunk.PolandChunkGenerator;
import pl.polishforests.worldgen.chunk.PolandScale;
import pl.polishforests.worldgen.chunk.PolandSettings;
import pl.polishforests.worldgen.feature.ChunkEcotones;
import pl.polishforests.worldgen.feature.ModFeatures;
import pl.polishforests.worldgen.feature.plan.ForestBlend;
import pl.polishforests.worldgen.habitat.Habitat;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.landscape.LandscapeScale;
import pl.polishforests.worldgen.landscape.Noise;
import pl.polishforests.worldgen.surface.Material;
import pl.polishforests.worldgen.surface.SoilBlend;
import pl.polishforests.worldgen.surface.StructureGround;
import pl.polishforests.worldgen.surface.SurfaceBuilder;
import pl.polishforests.worldgen.tree.ForestStandMap;

/**
 * The forests of the mod in the world type "Default + Polish Forests" (step W1 of docs/04-pionowy-wycinek.md): the
 * source of the data that the planners read from the landscape model in a "Poland" world.
 *
 * <p><b>Chunk habitats.</b> At the end of the TERRAIN stage of each chunk ({@code NoiseBasedChunkGeneratorMixin}), before
 * any feature of the chunk or of its neighbors writes into it, {@link #attach} stores the {@link ChunkHabitats} of the
 * chunk: the top ground block of each column (heightmap {@code OCEAN_FLOOR_WG}), the water above it (heightmap
 * {@code WORLD_SURFACE_WG} on a water block), the code of the column's biome (the biome the biome filter of the features
 * reads there: the vanilla zoom of the noise biomes at the top block, {@link VanillaForests}), the vegetation mask of the
 * structure pieces and the blocked columns (a vanilla biome, a slope of at least {@link #STEEP} blocks to a neighbor in the
 * chunk, a top that is not soil ({@link #SOIL_TAG}), sand or gravel). The dry columns of a habitat biome with a grass top get the top of the
 * biome's soil (podzol in the pine forests, the litter of the beech forest, muck in the alder carr; the soil patches of
 * {@link SurfaceBuilder#soilTop}). The values are pure functions of the world seed and the chunk's own terrain, so they do
 * not depend on the order in which chunks are generated.
 *
 * <p><b>Fields of the forest.</b> The scale of the objects is the gameplay scale's ({@link #localScale}), the managed share
 * the default of D9, the stand map has a seed of its own (salt {@value #STAND_SALT}), the soil patches too
 * ({@value #SOIL_SALT}), and the blend of the slice forests reads the habitat of the surface biome at each lattice point.
 */
public final class VanillaForestWorld implements ForestWorld {
	/** Salt of the stand map's seed (a new field, a new salt). */
	public static final String STAND_SALT = "vanilla_forests.stands";
	/** Salt of the seed of the soil patches (a new field, a new salt). */
	public static final String SOIL_SALT = "vanilla_forests.soil";
	/** Least height difference (blocks) to a neighbor of the chunk that makes a column too steep for the vegetation. */
	public static final int STEEP = 4;
	/** Block Y at which the surface biome of a column is read for the blend (above the terrain, so never a cave biome). */
	static final int SURFACE_BIOME_Y = 256;

	/** Chunks whose habitats were attached, and the soil tops changed (diagnostics of the game test). */
	public static final LongAdder ATTACHED = new LongAdder();
	public static final LongAdder SOIL_TOPS = new LongAdder();
	public static final LongAdder SOIL_BLENDED = new LongAdder();

	private final VanillaForestsBiomeSource source;
	private final Map<Holder<Biome>, Integer> codes = new ConcurrentHashMap<>();
	private volatile @Nullable Binding binding;
	private volatile @Nullable ForestStandMap standMap;
	private volatile @Nullable ForestBlend forestBlend;

	/** The random state of the world and what is built from its seed. */
	private record Binding(RandomState randomState, long seed, SurfaceBuilder soil, ThreadLocal<BiomeResolver> resolvers) {
	}

	VanillaForestWorld(VanillaForestsBiomeSource source) {
		this.source = source;
	}

	/** The biome source of this world. */
	public VanillaForestsBiomeSource source() {
		return source;
	}

	/** Binds the world's random state (the server binds it when the level loads, the TERRAIN hook again). */
	public void bind(RandomState randomState) {
		Binding b = binding;
		if (b != null && b.randomState() == randomState) {
			return;
		}
		long seed = randomState.seed();
		SurfaceBuilder soil = new SurfaceBuilder(new Noise(seed).derive(SOIL_SALT).seed(), PolandScale.GAMEPLAY.vertical(), true,
				LooseCover.THIN, false);
		binding = new Binding(randomState, seed, soil,
				ThreadLocal.withInitial(() -> source.createUncachedResolver(randomState)));
	}

	private Binding binding() {
		Binding b = binding;
		if (b == null) {
			throw new IllegalStateException("The world type Default + Polish Forests is not bound to a world yet");
		}
		return b;
	}

	/** Whether a random state is bound (the commands need one). */
	public boolean bound() {
		return binding != null;
	}

	// ------------------------------------------------------------------ ForestWorld

	@Override
	public double localScale() {
		return LandscapeScale.GAMEPLAY.local();
	}

	@Override
	public double managedForestShare() {
		return PolandSettings.DEFAULT.managedForestShare();
	}

	@Override
	public long standSeed(long worldSeed) {
		return new Noise(worldSeed).derive(STAND_SALT).seed();
	}

	@Override
	public ForestStandMap standMap(long worldSeed) {
		ForestStandMap m = standMap;
		long s = standSeed(worldSeed);
		if (m != null && m.matches(s, localScale(), managedForestShare())) {
			return m;
		}
		m = new ForestStandMap(s, localScale(), managedForestShare());
		standMap = m;
		return m;
	}

	@Override
	public ForestBlend forestBlend(long worldSeed) {
		ForestBlend b = forestBlend;
		if (b != null && b.worldSeed() == worldSeed) {
			return b;
		}
		b = new ForestBlend(worldSeed, localScale(), this::surfaceCode);
		forestBlend = b;
		return b;
	}

	@Override
	public boolean caveFeatures() {
		return true;
	}

	@Override
	public boolean vanilla() {
		return true;
	}

	// ------------------------------------------------------------------ codes

	/** The habitat code of a column of this biome ({@link VanillaForests}). */
	public int code(Holder<Biome> biome) {
		Integer c = codes.get(biome);
		if (c == null) {
			c = computeCode(biome);
			codes.put(biome, c);
		}
		return c;
	}

	private static int computeCode(Holder<Biome> biome) {
		HabitatBiome habitat = biome.unwrapKey()
				.filter(k -> pl.polishforests.PolishForests.MOD_ID.equals(k.identifier().getNamespace()))
				.map(k -> HabitatBiome.byId(k.identifier().getPath())).orElse(null);
		if (habitat != null && VanillaForests.present(habitat)) {
			return VanillaForests.code(habitat);
		}
		String path = biome.unwrapKey().map(k -> k.identifier().getPath()).orElse("");
		return VanillaForests.foreignCode(VanillaForests.foreign(path, biome.is(BiomeTags.IS_RIVER), biome.is(BiomeTags.IS_OCEAN),
				biome.is(BiomeTags.IS_BEACH)));
	}

	/** Whether a code is the code of a habitat biome of this world (not of a foreign column). */
	public static boolean forest(int code) {
		return VanillaForests.present(Habitat.biome(code));
	}

	/** The surface biome of column (x, z) (no zoom; the quart at {@link #SURFACE_BIOME_Y}). */
	public Holder<Biome> surfaceBiome(int x, int z) {
		return binding().resolvers().get().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(SURFACE_BIOME_Y),
				QuartPos.fromBlock(z));
	}

	/** The habitat code of the surface biome of column (x, z): the sampler of the blend and of the commands. */
	public int surfaceCode(int x, int z) {
		return code(surfaceBiome(x, z));
	}

	// ------------------------------------------------------------------ chunk habitats

	/** Computes and attaches the habitats of a chunk at the end of its TERRAIN stage, and writes the soil tops. */
	public void attach(ChunkAccess chunk, RandomState randomState, StructureManager structureManager) {
		bind(randomState);
		chunk.setAttached(ModFeatures.CHUNK_HABITATS, habitats(chunk, structureManager, true));
		ATTACHED.increment();
	}

	/**
	 * The habitats of a chunk that holds its terrain only (see the class description); with {@code soil} the dry tops of the
	 * habitat biomes are turned into their soil.
	 */
	ChunkHabitats habitats(ChunkAccess chunk, StructureManager structureManager, boolean soil) {
		Binding b = binding();
		ChunkPos pos = chunk.getPos();
		int x0 = pos.getMinBlockX();
		int z0 = pos.getMinBlockZ();
		int[] codes = new int[256];
		short[] tops = new short[256];
		short[] water = new short[256];
		long[] blocked = new long[4];
		boolean anyBlocked = false;
		BiomeManager zoom = new BiomeManager(resolver(chunk, b), BiomeManager.obfuscateSeed(b.seed()));
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		boolean[] ground = new boolean[256];
		for (int i = 0; i < 256; i++) {
			int x = x0 + (i >> 4);
			int z = z0 + (i & 15);
			int top = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
			int surface = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
			tops[i] = (short) top;
			water[i] = surface > top && chunk.getFluidState(at.set(x, surface, z)).is(FluidTags.WATER) ? (short) surface
					: ChunkHabitats.NO_WATER;
			codes[i] = code(zoom.getBiome(at.set(x, top, z)));
			BlockState state = chunk.getBlockState(at.set(x, top, z));
			ground[i] = soil(state);
		}
		for (int i = 0; i < 256; i++) {
			if (!forest(codes[i]) || !ground[i] || steep(tops, i)) {
				blocked[i >> 6] |= 1L << (i & 63);
				anyBlocked = true;
			}
		}
		byte[] structures = StructureGround.mask(tops, PolandChunkGenerator.structurePieces(structureManager, pos), x0, z0);
		ChunkHabitats h = new ChunkHabitats(codes, tops, water, VanillaForests.OCEANICITY, VanillaForests.MOUNTAIN_INFLUENCE,
				structures == null ? ChunkHabitats.NO_STRUCTURES : structures, ChunkHabitats.NO_CAVES,
				anyBlocked ? blocked : ChunkHabitats.NONE_BLOCKED);
		if (soil) {
			soilTops(chunk, h, b.soil(), at);
		}
		return h;
	}

	/**
	 * The ground blocks of the vegetation besides sand and gravel: in 26.3 {@code #minecraft:dirt} holds only dirt, coarse
	 * dirt and rooted dirt, the grass block, podzol and mycelium are {@code #minecraft:grass_blocks}, and
	 * {@code #minecraft:substrate_overworld} joins them with mud and moss (round 1 of stage 2 of W1: with {@code #dirt} every
	 * grass column was blocked).
	 */
	public static final TagKey<Block> SOIL_TAG = BlockTags.SUBSTRATE_OVERWORLD;

	/** Whether a top block carries vegetation: soil, sand or gravel (not rock, ice or snow). */
	public static boolean soil(BlockState state) {
		return state.is(SOIL_TAG) || state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL);
	}

	/** Whether column i differs by at least {@link #STEEP} blocks from a neighbor column of the chunk. */
	public static boolean steep(short[] tops, int i) {
		int x = i >> 4;
		int z = i & 15;
		int t = tops[i];
		return x > 0 && Math.abs(t - tops[i - 16]) >= STEEP || x < 15 && Math.abs(t - tops[i + 16]) >= STEEP
				|| z > 0 && Math.abs(t - tops[i - 1]) >= STEEP || z < 15 && Math.abs(t - tops[i + 1]) >= STEEP;
	}

	/**
	 * The noise biomes for the zoom: the chunk's own quarts, and around it the biome source's (the same biomes the
	 * neighbors hold, memoized for the call).
	 */
	private static BiomeResolver resolver(ChunkAccess chunk, Binding b) {
		ChunkPos pos = chunk.getPos();
		int qx0 = QuartPos.fromBlock(pos.getMinBlockX());
		int qz0 = QuartPos.fromBlock(pos.getMinBlockZ());
		int minQy = QuartPos.fromBlock(chunk.getMinY());
		int maxQy = minQy + QuartPos.fromBlock(chunk.getHeight()) - 1;
		BiomeResolver outside = b.resolvers().get();
		Long2ObjectOpenHashMap<Holder<Biome>> memo = new Long2ObjectOpenHashMap<>();
		return (qx, qy, qz) -> {
			if (qx >= qx0 && qx < qx0 + 4 && qz >= qz0 && qz < qz0 + 4) {
				return chunk.getNoiseBiome(qx, qy, qz);
			}
			int y = Math.clamp(qy, minQy, maxQy);
			long key = ((long) qx & 0x1FFFFFL) << 42 | ((long) y & 0x1FFFFFL) << 21 | (long) qz & 0x1FFFFFL;
			Holder<Biome> h = memo.get(key);
			if (h == null) {
				h = outside.getNoiseBiome(qx, y, qz);
				memo.put(key, h);
			}
			return h;
		};
	}

	/** The dry grass tops of the habitat biomes' columns become the top of the biome's soil. */
	private static void soilTops(ChunkAccess chunk, ChunkHabitats h, SurfaceBuilder soil, BlockPos.MutableBlockPos at) {
		ChunkPos pos = chunk.getPos();
		for (int i = 0; i < 256; i++) {
			if (h.blocked(i) || h.hasWater(i) || h.pieceDistance(i) <= 1) {
				continue;
			}
			int x = pos.getMinBlockX() + (i >> 4);
			int z = pos.getMinBlockZ() + (i & 15);
			at.set(x, h.top()[i], z);
			if (!chunk.getBlockState(at).is(Blocks.GRASS_BLOCK)) {
				continue;
			}
			Material top = soil.soilTop(h.codes()[i], x, z);
			if (top != Material.GRASS_BLOCK) {
				chunk.setBlockState(at, MaterialStates.of(top), 0);
				SOIL_TOPS.increment();
			}
		}
	}

	/**
	 * Soil ecotones (rule Z10) before the decoration of a chunk, as in a "Poland" world ({@link SoilBlend}): the top of
	 * each dry, unblocked column of a habitat biome within the belt of a border takes the soil of the other side, where the
	 * world still holds the top of its own soil.
	 */
	public void blendSoil(WorldGenLevel level, ChunkAccess chunk) {
		ChunkHabitats habitats = chunk.getAttached(ModFeatures.CHUNK_HABITATS);
		Binding b = binding;
		if (habitats == null || b == null) {
			return;
		}
		boolean[] skip = new boolean[256];
		boolean any = false;
		for (int i = 0; i < 256; i++) {
			skip[i] = habitats.hasWater(i) || habitats.pieceDistance(i) <= 1 || habitats.blocked(i);
			any |= !skip[i];
		}
		if (!any) {
			return;
		}
		int[] tops = SoilBlend.tops(ChunkEcotones.of(level, chunk, habitats, localScale(), forestBlend(level.getSeed())).region(),
				level.getSeed(), b.soil(), skip);
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

	/** The vanilla biomes this world replaces, by path (for the commands). */
	public List<String> replacedPaths() {
		return List.copyOf(VanillaForests.REPLACEMENTS.keySet());
	}
}
