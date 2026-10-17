package pl.polishforests.worldgen.feature;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.jspecify.annotations.Nullable;
import pl.polishforests.PolishForests;
import pl.polishforests.worldgen.chunk.ChunkHabitats;
import pl.polishforests.worldgen.chunk.PolandChunkGenerator;
import pl.polishforests.worldgen.habitat.Species;

/**
 * Registration of the vegetation dispatcher types ({@code BuiltInRegistries.FEATURE_TYPE}, docs/03-m2-biomy.md §8.2),
 * of the placement filter {@code polishforests:habitat} ({@link HabitatFilter}) and of the {@link ChunkHabitats} chunk
 * attachment (§8.3).
 */
public final class ModFeatures {
	/**
	 * Habitats of a chunk between {@code fill()} and the last generation stage: a persistent attachment (not sent to
	 * clients), so a proto-chunk saved between TERRAIN and FEATURES keeps it; removed in {@code spawnOriginalMobs}, so
	 * full chunks are saved without it. Missing only in a proto-chunk saved by an older version (see {@link #habitats}).
	 */
	public static final AttachmentType<ChunkHabitats> CHUNK_HABITATS = AttachmentRegistry.create(
			PolishForests.id("chunk_habitats"), builder -> builder.persistent(ChunkHabitats.CODEC));

	/** Number of chunks whose habitats were missing in a dispatcher and were computed again from the model (§3.6: < 0.5%). */
	public static final LongAdder HABITAT_MISS = new LongAdder();

	private ModFeatures() {
	}

	/**
	 * Observer of the woody plants the dispatchers place (step V4, the census of the norms card in the game test
	 * {@code slice}): the species of each tree ({@link TreeStandFeature}) and of each shrub or tree feature of a column
	 * layer ({@link PlantLayerFeature}, by the path of the placed feature), at the block where the feature was placed.
	 * The M2 trees are vanilla placeholders (an oak log is oak, linden, ash or maple), so the census takes their species
	 * from here. Null outside that test; reading it costs one volatile read per placed tree and does not change what is
	 * generated. Called on the generation threads, so the observer must be thread-safe.
	 */
	public interface WoodyPlacements {
		void placed(BlockPos at, Species species);
	}

	public static volatile @Nullable WoodyPlacements WOODY_PLACEMENTS;

	/** Reports a placed woody plant to {@link #WOODY_PLACEMENTS}, if set. */
	static void woodyPlaced(BlockPos at, @Nullable Species species) {
		WoodyPlacements w = WOODY_PLACEMENTS;
		if (w != null && species != null) {
			w.placed(at.immutable(), species);
		}
	}

	/**
	 * Species of a placed feature {@code polishforests:tree/<species>}, {@code tree_substitute/<species>[_<variant>]} or
	 * {@code shrub/<species>}, e.g. {@code tree_substitute/scots_pine_low} is the Scots pine; null for other features
	 * (fallen trees, flowers) and direct holders.
	 */
	static @Nullable Species woodySpecies(Holder<PlacedFeature> feature) {
		String path = feature.unwrapKey().map(k -> k.identifier().getPath()).orElse("");
		String rest = path.startsWith("tree/") ? path.substring(5) : path.startsWith("tree_substitute/") ? path.substring(16)
				: path.startsWith("shrub/") ? path.substring(6) : null;
		if (rest == null) {
			return null;
		}
		Species best = null;
		for (Species s : Species.values()) {
			if ((rest.equals(s.id()) || rest.startsWith(s.id() + "_")) && (best == null || s.id().length() > best.id().length())) {
				best = s;
			}
		}
		return best;
	}

	/** Time and placements of each dispatcher layer (§12.3: time of each layer). */
	public static final Map<BiomeDecoration.Dispatcher, LayerStats> STATS = new EnumMap<>(BiomeDecoration.Dispatcher.class);

	static {
		for (BiomeDecoration.Dispatcher d : BiomeDecoration.Dispatcher.values()) {
			STATS.put(d, new LayerStats());
		}
	}

	/**
	 * Time (ns), chunks and placed plants or trees of one dispatcher layer, summed over the process; for the tree stand
	 * also the wood blocks and leaves its trees placed (step V9).
	 */
	public static final class LayerStats {
		public final LongAdder nanos = new LongAdder();
		public final LongAdder chunks = new LongAdder();
		public final LongAdder placed = new LongAdder();
		public final LongAdder wood = new LongAdder();
		public final LongAdder leaves = new LongAdder();

		void add(long nanos, int placed) {
			this.nanos.add(nanos);
			this.chunks.increment();
			this.placed.add(placed);
		}
	}

	public static void register() {
		for (BiomeDecoration.Dispatcher d : BiomeDecoration.Dispatcher.values()) {
			Registry.register(BuiltInRegistries.FEATURE_TYPE, PolishForests.id(d.path()),
					d == BiomeDecoration.Dispatcher.TREE_STAND ? TreeStandFeature.CODEC : PlantLayerFeature.codec(d));
		}
		Registry.register(BuiltInRegistries.FEATURE_TYPE, PolishForests.id("tree"), FastTreeFeature.CODEC);
		Registry.register(BuiltInRegistries.FEATURE_TYPE, PolishForests.id("procedural_tree"), ProceduralTreeFeature.CODEC);
		Registry.register(BuiltInRegistries.PLACEMENT_MODIFIER_TYPE, PolishForests.id("habitat"), HabitatFilter.CODEC);
		Registry.register(BuiltInRegistries.TRUNK_PLACER_TYPE, PolishForests.id("clump"), ClumpTrunkPlacer.TYPE);
	}

	/**
	 * Habitats of the chunk: the attachment written in {@code fill()}, or, when it is missing (a proto-chunk saved
	 * between TERRAIN and FEATURES by a version with a non-persistent attachment, or a generation path that skips
	 * {@code fill()}), computed again from the model and counted in {@link #HABITAT_MISS}. Null for a generator other than the "Poland" one.
	 */
	public static @Nullable ChunkHabitats habitats(ChunkAccess chunk, ChunkGenerator generator, long seed) {
		ChunkHabitats habitats = chunk.getAttached(CHUNK_HABITATS);
		if (habitats != null) {
			return habitats;
		}
		if (!(generator instanceof PolandChunkGenerator poland)) {
			return null;
		}
		HABITAT_MISS.increment();
		habitats = poland.computeHabitats(chunk.getPos(), chunk.getMinY(), chunk.getMaxY(), seed);
		chunk.setAttached(CHUNK_HABITATS, habitats);
		return habitats;
	}
}
