package pl.polishforests.block;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import net.minecraft.SharedConstants;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The vanilla registries in a unit test, with the blocks and items of the mod registered the way the game does it.
 * Unit tests run without the Fabric loader, so no mixin unfreezes the registries after the vanilla bootstrap: the block
 * and item registries are opened by reflection (as Fabric's registry sync does with its mixins), the content of the mod
 * is registered by {@link ModBlocks#registerContent()} and the registries are closed again (the vanilla tags stay, the new
 * entries get bound, empty tag lists as in the game before the data packs load).
 */
final class GameRegistries {
	private static boolean ready;

	private GameRegistries() {
	}

	/** Bootstraps the game registries and registers the blocks and items of the mod, once per test JVM. */
	static synchronized void bootstrap() {
		if (ready) {
			return;
		}
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		unfreeze(BuiltInRegistries.BLOCK);
		unfreeze(BuiltInRegistries.ITEM);
		ModBlocks.registerContent();
		// As vanilla does for its blocks in Blocks.<clinit> and Fabric for registered ones: state ids and state caches.
		for (TrunkBlock b : ModBlocks.trunks().values()) {
			for (BlockState state : b.getStateDefinition().getPossibleStates()) {
				Block.BLOCK_STATE_REGISTRY.add(state);
				state.initCache();
			}
		}
		refreeze(BuiltInRegistries.BLOCK);
		refreeze(BuiltInRegistries.ITEM);
		ready = true;
	}

	private static void unfreeze(Registry<?> registry) {
		set(registry, true);
	}

	private static void refreeze(Registry<?> registry) {
		set(registry, false);
	}

	private static void set(Registry<?> registry, boolean open) {
		try {
			Field frozen = MappedRegistry.class.getDeclaredField("frozen");
			frozen.setAccessible(true);
			Field holders = MappedRegistry.class.getDeclaredField("unregisteredIntrusiveHolders");
			holders.setAccessible(true);
			if (open) {
				frozen.setBoolean(registry, false);
				holders.set(registry, new IdentityHashMap<>());
				return;
			}
			if (!((java.util.Map<?, ?>) holders.get(registry)).isEmpty()) {
				throw new IllegalStateException("Unregistered intrusive holders in " + registry.key());
			}
			holders.set(registry, null);
			frozen.setBoolean(registry, true);
			Method tags = MappedRegistry.class.getDeclaredMethod("refreshTagsInHolders");
			tags.setAccessible(true);
			tags.invoke(registry);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Cannot " + (open ? "open" : "close") + " the registry " + registry.key() + " for the test", e);
		}
	}
}
