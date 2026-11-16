package pl.polishforests.entity;

import java.util.List;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import org.jspecify.annotations.Nullable;
import pl.polishforests.PolishForests;
import pl.polishforests.entity.roedeer.RoeDeer;
import pl.polishforests.entity.spawn.FaunaSpawner;

/**
 * Entity types of the mod and their spawn eggs (vertical slice: from step V8 the roe deer, for rendering only). The ids
 * go into saved worlds, so {@code RegistrySnapshotTest} compares them with {@code src/test/resources/registry_snapshot.txt}
 * like the blocks and items (frozen after checkpoint CP-B, PW-11). From step FA1 the animals spawn in the world types of the
 * mod ({@link #fauna()}, {@code spawn/FaunaSpawner}).
 */
public final class ModEntities {
	private static @Nullable EntityType<RoeDeer> roeDeer;
	private static @Nullable Item roeDeerSpawnEgg;

	private ModEntities() {
	}

	/** Registers the entity types, spawn eggs and attributes, and puts the spawn eggs in the creative tab. */
	public static void register() {
		registerContent();
		FaunaSpawner.register();
		FabricDefaultAttributeRegistry.register(roeDeer(), RoeDeer.createAttributes());
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(out -> out.insertAfter(Items.RAVAGER_SPAWN_EGG,
				roeDeerSpawnEgg()));
	}

	/**
	 * Only the registry entries (entity types and spawn eggs): {@link #register()} in the game, alone in the unit tests that
	 * compare the registry with the snapshot.
	 */
	public static void registerContent() {
		if (roeDeer != null) {
			throw new IllegalStateException("The entity types of " + PolishForests.MOD_ID + " are already registered");
		}
		// Roe deer: 0.7 x 1.1 blocks (docs/04 §0.3, C2), creature category; no loot (C8).
		roeDeer = type("roe_deer", EntityType.Builder.of(RoeDeer::new, MobCategory.CREATURE).sized(0.7F, 1.1F).eyeHeight(1.0F)
				.clientTrackingRange(10).noLootTable());
		roeDeerSpawnEgg = spawnEgg("roe_deer_spawn_egg", roeDeer);
	}

	public static EntityType<RoeDeer> roeDeer() {
		if (roeDeer == null) {
			throw new IllegalStateException("The entity types are not registered yet");
		}
		return roeDeer;
	}

	/**
	 * The names of the fields an entity type of the mod adds to the saved entity data (in the order it writes them); the
	 * registry snapshot lists them, so renaming a field, which breaks saved worlds after CP-B, fails its test.
	 */
	public static List<String> savedFields(EntityType<?> type) {
		return type == roeDeer ? RoeDeer.SAVED_FIELDS : List.of();
	}

	/** The species the fauna spawners place, with their profiles (step FA1). */
	public static List<FaunaSpawner.Species> fauna() {
		return List.of(new FaunaSpawner.Species(roeDeer(), RoeDeer.PROFILE));
	}

	public static Item roeDeerSpawnEgg() {
		if (roeDeerSpawnEgg == null) {
			throw new IllegalStateException("The spawn eggs are not registered yet");
		}
		return roeDeerSpawnEgg;
	}

	private static <T extends Entity> EntityType<T> type(String path, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, PolishForests.id(path));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	private static Item spawnEgg(String path, EntityType<?> type) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, PolishForests.id(path));
		return Registry.register(BuiltInRegistries.ITEM, key, new SpawnEggItem(new Item.Properties().spawnEgg(type).setId(key)));
	}
}
