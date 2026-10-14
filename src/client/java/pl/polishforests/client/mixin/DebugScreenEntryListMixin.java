package pl.polishforests.client.mixin;

import java.util.Map;
import net.minecraft.client.gui.components.debug.DebugScreenEntryList;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.client.gui.components.debug.DebugScreenProfile;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.polishforests.client.HabitatDebugEntry;

/**
 * Step S9: the F3 entry {@code polishforests:habitat} is part of the overlay by default. The vanilla profiles are an
 * immutable map without mod entries, so whenever the list takes the statuses of a profile or of a saved custom profile,
 * the entry gets "in overlay" unless the statuses already name it (a player who switched it off in F3 + F6 keeps that).
 * The performance profile stays as it is.
 */
@Mixin(DebugScreenEntryList.class)
abstract class DebugScreenEntryListMixin {
	@Shadow
	@Final
	private Map<Identifier, DebugScreenEntryStatus> allStatuses;

	@Shadow
	private @Nullable DebugScreenProfile profile;

	@Inject(method = "resetStatuses", at = @At("TAIL"))
	private void polishforests$habitatInOverlay(Map<Identifier, DebugScreenEntryStatus> newEntries, CallbackInfo ci) {
		if (profile != DebugScreenProfile.PERFORMANCE) {
			allStatuses.putIfAbsent(HabitatDebugEntry.ID, DebugScreenEntryStatus.IN_OVERLAY);
		}
	}
}
