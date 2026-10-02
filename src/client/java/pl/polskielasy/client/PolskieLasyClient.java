package pl.polskielasy.client;

import net.fabricmc.api.ClientModInitializer;

public final class PolskieLasyClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		KlimatKlienta.register();
	}
}
