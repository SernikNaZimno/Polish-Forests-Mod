package pl.polishforests.client;

import net.fabricmc.api.ClientModInitializer;
import pl.polishforests.client.entity.RoeDeerRenderer;

public final class PolishForestsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientClimate.register();
		// Step S9: the habitat in the F3 overlay.
		HabitatDebugEntry.register();
		// Step V8: the roe deer model (GeckoLib).
		RoeDeerRenderer.register();
	}
}
