package pl.polishforests.worldgen.feature;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.jspecify.annotations.Nullable;
import pl.polishforests.PolishForests;
import pl.polishforests.worldgen.chunk.ChunkHabitats;
import pl.polishforests.worldgen.chunk.PolandChunkGenerator;
import pl.polishforests.worldgen.feature.plan.Ecotone;

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

	/** Time and placements of each dispatcher layer (§12.3: time of each layer). */
	public static final Map<BiomeDecoration.Dispatcher, LayerStats> STATS = new EnumMap<>(BiomeDecoration.Dispatcher.class);

	static {
		for (BiomeDecoration.Dispatcher d : BiomeDecoration.Dispatcher.values()) {
			STATS.put(d, new LayerStats());
		}
	}

	/** Time (ns), chunks and placed plants or trees of one dispatcher layer, summed over the process. */
	public static final class LayerStats {
		public final LongAdder nanos = new LongAdder();
		public final LongAdder chunks = new LongAdder();
		public final LongAdder placed = new LongAdder();

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
		Registry.register(BuiltInRegistries.PLACEMENT_MODIFIER_TYPE, PolishForests.id("habitat"), HabitatFilter.CODEC);
	}

	/**
	 * Habitat codes of a chunk and its 8 neighbors for the ecotones (rule Z10, step S8b; {@link Ecotone}), from the
	 * neighbors' attachments ({@link Ecotone#UNKNOWN} where a neighbor has none).
	 */
	public static Ecotone.Region ecotoneRegion(net.minecraft.world.level.WorldGenLevel level, ChunkAccess chunk,
			ChunkHabitats habitats, double k) {
		return VegetationColumns.region(level, chunk, habitats, k);
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
