package pl.polishforests.client;

import java.lang.ref.WeakReference;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;
import pl.polishforests.PolishForests;
import pl.polishforests.command.HabitatInfo;

/**
 * Step S9: the F3 entry {@code polishforests:habitat}, the habitat of the column at the camera's feet
 * ({@link HabitatInfo#debugLines}). It is shown with the F3 overlay without enabling anything in the debug options
 * (F3 + F6): {@code mixin/DebugScreenEntryListMixin} gives it the status "in overlay" in the default profile and in custom
 * profiles that do not mention it yet, and a player can switch it off there like any vanilla entry. Like the vanilla
 * entry {@code chunk_generation_stats} it needs the server level, so it shows nothing on a remote server.
 */
public final class HabitatDebugEntry implements DebugScreenEntry {
	public static final Identifier ID = PolishForests.id("habitat");
	private static final Identifier GROUP = PolishForests.id("habitat");

	private @Nullable BlockPos lastPos;
	/** Level of {@link #lastPos}; weak, so a closed world is not kept in memory. */
	private WeakReference<ServerLevel> lastLevel = new WeakReference<>(null);
	private List<String> lines = List.of();

	public static void register() {
		DebugScreenEntries.register(ID, new HabitatDebugEntry());
	}

	@Override
	public void display(DebugScreenDisplayer displayer, @Nullable Level serverOrClientLevel, @Nullable LevelChunk clientChunk,
			@Nullable LevelChunk serverChunk) {
		Entity camera = Minecraft.getInstance().getCameraEntity();
		if (camera == null || !(serverOrClientLevel instanceof ServerLevel level)) {
			return;
		}
		BlockPos feet = camera.blockPosition();
		// S9 review round 1: also recompute in another level (another world opened at the same block, e.g. at spawn).
		if (!feet.equals(lastPos) || lastLevel.get() != level) {
			lastPos = feet;
			lastLevel = new WeakReference<>(level);
			HabitatInfo info = HabitatInfo.of(level, feet.getX(), feet.getY(), feet.getZ());
			lines = info == null ? List.of() : info.debugLines();
		}
		if (!lines.isEmpty()) {
			displayer.addToGroup(GROUP, lines);
		}
	}
}
