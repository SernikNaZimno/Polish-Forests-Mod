package pl.polishforests.client;

import net.fabricmc.api.ClientModInitializer;

public final class PolishForestsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientClimate.register();
		// Step V7: the phase of the year, the species foliage colors and the wave rebuild of the meshes.
		ClientSeasonPhase.register();
		// Step V12: the tints of the herb layer.
		ClientGroundPlants.register();
		// Step V13: the seasonal models (bare twigs, the phases of the herb layer).
		ClientSeasonModels.register();
		// Step O2: buried roots draw nothing.
		BuriedRootModel.register();
		// Step S9: the habitat in the F3 overlay.
		HabitatDebugEntry.register();
	}
}
