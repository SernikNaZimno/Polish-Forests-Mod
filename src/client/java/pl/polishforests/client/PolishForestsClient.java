package pl.polishforests.client;

import net.fabricmc.api.ClientModInitializer;

public final class PolishForestsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientClimate.register();
	}
}
