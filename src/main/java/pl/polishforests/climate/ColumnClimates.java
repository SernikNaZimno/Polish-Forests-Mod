package pl.polishforests.climate;

import java.util.concurrent.atomic.LongAdder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.jspecify.annotations.Nullable;
import pl.polishforests.PolishForests;

/**
 * Step C2 (decision CP-D.11, G.58-b): the column climates ({@link ColumnClimate}) as a chunk attachment of the world
 * type "Default + Polish Forests", and the way {@code Biome#getTemperature(pos)}, which has no level, finds the chunk.
 *
 * <p><b>Attachment.</b> Written at the end of the TERRAIN stage next to the chunk habitats ({@code VanillaForestWorld}),
 * persistent (proto-chunks and full chunks; Fabric moves it from the proto-chunk to the level chunk) and synchronized with
 * every player tracking the chunk (Fabric sends it right after the chunk packet). Only chunks with a habitat biome on the
 * surface get one; the "Poland" world never writes it.
 *
 * <p><b>Lookup</b> (pure reads, no chunk loading, no sampling of the vanilla biome source), in this order:
 * <ol>
 * <li>a thread inside the decoration of a chunk of that world (the FEATURES stage, {@code freeze_top_layer}) reads the
 * chunks of its generation region ({@link #decorating}, set by {@code ChunkGeneratorVanillaForestsMixin});</li>
 * <li>the server thread reads the loaded level chunks of the bound overworld ({@code getChunkNow}, which answers only on
 * the server thread: precipitation ticks, freezing, Serene Seasons, commands);</li>
 * <li>the client thread reads the chunks of the client level (bound by the client initializer: rain and snow drawn by
 * the client, its precipitation sounds and particles).</li>
 * </ol>
 * In single player the client shares the biome objects with the integrated server, so the thread decides whose chunks are
 * read; both hold the same attachment for the same chunk. Any other thread, or a chunk without the attachment, gets null,
 * and the biome keeps its own base temperature (the temperature before step C2).
 */
public final class ColumnClimates {
	/** The attachment: persistent and synchronized with the client. */
	public static final AttachmentType<ColumnClimate> TYPE = AttachmentRegistry.create(PolishForests.id("column_climate"),
			b -> b.persistent(ColumnClimate.CODEC).syncWith(ColumnClimate.STREAM_CODEC, AttachmentSyncPredicate.all()));

	/** Chunks that got a column climate (diagnostics of the game test). */
	public static final LongAdder ATTACHED = new LongAdder();

	/** The client's chunk lookup: null unless called on the client thread. */
	@FunctionalInterface
	public interface ClientChunks {
		@Nullable ChunkAccess chunk(int chunkX, int chunkZ);
	}

	private static final ThreadLocal<@Nullable LevelReader> DECORATING = new ThreadLocal<>();
	private static volatile @Nullable ServerLevel server;
	private static volatile @Nullable ClientChunks client;

	private ColumnClimates() {
	}

	/** Binds the lookup and the server events (the mod's initializer). */
	public static void register() {
		VanillaClimate.bind(ColumnClimates::find);
		ServerLevelEvents.UNLOAD.register((s, level) -> {
			if (server == level) {
				server = null;
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> server = null);
	}

	/** Binds the overworld of the world type "Default + Polish Forests" (its level load). */
	public static void bindServer(@Nullable ServerLevel level) {
		server = level;
	}

	/** Binds the client's chunk lookup (the client initializer). */
	public static void bindClient(@Nullable ClientChunks chunks) {
		client = chunks;
	}

	/**
	 * Marks the calling thread as decorating a chunk of the world type "Default + Polish Forests" in the given generation
	 * region; returns the previous region of the thread, which {@link #done} restores.
	 */
	public static @Nullable LevelReader decorating(LevelReader region) {
		LevelReader previous = DECORATING.get();
		DECORATING.set(region);
		return previous;
	}

	/** Ends {@link #decorating}. */
	public static void done(@Nullable LevelReader previous) {
		if (previous == null) {
			DECORATING.remove();
		} else {
			DECORATING.set(previous);
		}
	}

	/** The column climate of chunk (chunkX, chunkZ) as the calling thread sees it, or null. */
	static @Nullable ColumnClimate find(int chunkX, int chunkZ) {
		LevelReader region = DECORATING.get();
		if (region != null) {
			// A generation region throws for a chunk outside it, whatever the flag says; hasChunk first.
			ChunkAccess chunk = region.hasChunk(chunkX, chunkZ) ? region.getChunk(chunkX, chunkZ, ChunkStatus.EMPTY, false) : null;
			return chunk == null ? null : chunk.getAttached(TYPE);
		}
		ServerLevel s = server;
		if (s != null) {
			LevelChunk chunk = s.getChunkSource().getChunkNow(chunkX, chunkZ);
			if (chunk != null) {
				return chunk.getAttached(TYPE);
			}
		}
		ClientChunks c = client;
		if (c != null) {
			ChunkAccess chunk = c.chunk(chunkX, chunkZ);
			if (chunk != null) {
				return chunk.getAttached(TYPE);
			}
		}
		return null;
	}

	/** The column climate of the chunk holding {@code pos} in a level (server thread: may load the chunk), or null. */
	public static @Nullable ColumnClimate at(ServerLevel level, BlockPos pos) {
		return level.getChunkAt(pos).getAttached(TYPE);
	}
}
