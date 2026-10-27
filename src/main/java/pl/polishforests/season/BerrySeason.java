package pl.polishforests.season;

import com.mojang.serialization.Codec;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import pl.polishforests.PolishForests;
import pl.polishforests.block.BerryShrubBlock;

/**
 * The fruit of the bilberry as a server state through the year (step V13 of docs/04-pionowy-wycinek.md, decision PW-8
 * (a), risk R13). While a chunk is ticked, the random tick of {@link BerryShrubBlock} ripens the fruit of the fertile
 * plants in MID_SUMMER and LATE_SUMMER and drops it afterwards. A chunk that was not ticked (unloaded, or generated, where
 * the fruit is always off: rule ZW-5) catches up when it is loaded: the chunk carries the sub-season it was last brought
 * up to date in (the attachment {@link #STAMP}); on {@code CHUNK_LOAD} it only goes into a queue, and the next server
 * ticks (never the load itself) set the fruit of every bilberry in it to the state of the current sub-season and stamp it,
 * within a time budget per tick ({@value #BUDGET_NANOS} ns), so a returning player sees the right state within a second.
 * Chunks without a bilberry get no stamp.
 */
public final class BerrySeason {
	/** The sub-season (ordinal of {@link SubSeason}) in which the fruit of a chunk was last brought up to date. */
	public static final AttachmentType<Integer> STAMP = AttachmentRegistry.create(PolishForests.id("berry_season"),
			b -> b.persistent(Codec.INT));
	/** The work budget of the catch-up per server tick. */
	static final long BUDGET_NANOS = 2_000_000L;

	private record Pending(ResourceKey<Level> level, long chunk) {
	}

	private static final Queue<Pending> QUEUE = new ConcurrentLinkedQueue<>();
	/** Chunks caught up and blocks changed since the start (tests and the log). */
	private static volatile long caughtUpChunks;
	private static volatile long changedBlocks;

	private BerrySeason() {
	}

	public static void register() {
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> QUEUE.add(new Pending(level.dimension(),
				chunk.getPos().pack())));
		ServerTickEvents.END_SERVER_TICK.register(BerrySeason::tick);
	}

	/** Chunks brought up to date by the catch-up since the start of the game. */
	public static long caughtUpChunks() {
		return caughtUpChunks;
	}

	/** Bilberry blocks whose fruit the catch-up changed since the start of the game. */
	public static long changedBlocks() {
		return changedBlocks;
	}

	/** Chunks waiting for the catch-up. */
	public static int pending() {
		return QUEUE.size();
	}

	private static void tick(MinecraftServer server) {
		if (QUEUE.isEmpty()) {
			return;
		}
		long until = System.nanoTime() + BUDGET_NANOS;
		Pending p;
		while (System.nanoTime() < until && (p = QUEUE.poll()) != null) {
			ServerLevel level = server.getLevel(p.level());
			if (level == null) {
				continue;
			}
			ChunkPos pos = ChunkPos.unpack(p.chunk());
			LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x(), pos.z());
			if (chunk != null) {
				catchUp(level, chunk);
			}
		}
	}

	/** Brings the fruit of the bilberries of a loaded chunk up to the current sub-season (once per sub-season). */
	static void catchUp(ServerLevel level, LevelChunk chunk) {
		SubSeason now = Seasons.provider().subSeason(level);
		Integer stamp = chunk.getAttached(STAMP);
		if (stamp != null && stamp == now.ordinal()) {
			return;
		}
		boolean season = GroundPhases.fruiting(now);
		long serial = level.getOverworldClockTime() / 24000L;
		int found = 0;
		int changed = 0;
		LevelChunkSection[] sections = chunk.getSections();
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int s = 0; s < sections.length; s++) {
			LevelChunkSection section = sections[s];
			if (section.hasOnlyAir() || !section.maybeHas(st -> st.getBlock() instanceof BerryShrubBlock)) {
				continue;
			}
			int y0 = chunk.getSectionYFromSectionIndex(s) << 4;
			for (int y = 0; y < 16; y++) {
				for (int z = 0; z < 16; z++) {
					for (int x = 0; x < 16; x++) {
						BlockState state = section.getBlockState(x, y, z);
						if (!(state.getBlock() instanceof BerryShrubBlock)) {
							continue;
						}
						found++;
						at.set(chunk.getPos().getMinBlockX() + x, y0 + y, chunk.getPos().getMinBlockZ() + z);
						boolean ripe = season && GroundPhases.fertile(at.getX(), at.getY(), at.getZ())
								&& GroundPhases.ripeAfterCatchUp(at.getX(), at.getY(), at.getZ(), serial);
						if (state.getValue(BerryShrubBlock.BERRIES) != ripe) {
							level.setBlock(at, state.setValue(BerryShrubBlock.BERRIES, ripe), Block.UPDATE_CLIENTS);
							changed++;
						}
					}
				}
			}
		}
		if (found > 0) {
			chunk.setAttached(STAMP, now.ordinal());
			caughtUpChunks++;
			changedBlocks += changed;
		}
	}
}
