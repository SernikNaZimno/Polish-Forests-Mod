package pl.polishforests.worldgen.feature;

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

/**
 * Registration of the vegetation dispatcher types ({@code BuiltInRegistries.FEATURE_TYPE}, docs/03-m2-biomy.md §8.2)
 * and of the {@link ChunkHabitats} chunk attachment (§8.3).
 */
public final class ModFeatures {
	/**
	 * Habitats of a chunk between {@code fill()} and the last generation stage: a non-persistent attachment, so it is
	 * neither saved nor sent to clients. A proto-chunk saved between TERRAIN and FEATURES loses it (see {@link #habitats}).
	 */
	public static final AttachmentType<ChunkHabitats> CHUNK_HABITATS = AttachmentRegistry.create(
			PolishForests.id("chunk_habitats"));

	/** Number of chunks whose habitats were missing in a dispatcher and were computed again from the model (§3.6: < 0.5%). */
	public static final LongAdder HABITAT_MISS = new LongAdder();

	private ModFeatures() {
	}

	public static void register() {
		for (BiomeDecoration.Dispatcher d : BiomeDecoration.Dispatcher.values()) {
			Registry.register(BuiltInRegistries.FEATURE_TYPE, PolishForests.id(d.path()),
					d == BiomeDecoration.Dispatcher.TREE_STAND ? TreeStandFeature.CODEC : PendingFeature.codec(d));
		}
	}

	/**
	 * Habitats of the chunk: the attachment written in {@code fill()}, or, when it is missing (a proto-chunk saved
	 * between TERRAIN and FEATURES, e.g. on server shutdown or with C2ME), computed again from the model and counted
	 * in {@link #HABITAT_MISS}. Null for a generator other than the "Poland" one.
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
