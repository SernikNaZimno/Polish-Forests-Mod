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
import pl.polishforests.entity.squirrel.RedSquirrel;
import pl.polishforests.entity.wildboar.WildBoar;

/**
 * Entity types of the mod and their spawn eggs (vertical slice: from step V8 the roe deer, for rendering only). The ids
 * go into saved worlds, so {@code RegistrySnapshotTest} compares them with {@code src/test/resources/registry_snapshot.txt}
 * like the blocks and items (frozen after checkpoint CP-B, PW-11). From step FA1 the animals spawn in the world types of the
 * mod ({@link #fauna()}, {@code spawn/FaunaSpawner}). Step FA3: the wild boar (new ids appended, PW-11); step FA5 the red
 * squirrel (appended).
 */
public final class ModEntities {
	private static @Nullable EntityType<RoeDeer> roeDeer;
	private static @Nullable Item roeDeerSpawnEgg;
	private static @Nullable EntityType<WildBoar> wildBoar;
	private static @Nullable Item wildBoarSpawnEgg;
	private static @Nullable EntityType<RedSquirrel> redSquirrel;
	private static @Nullable Item redSquirrelSpawnEgg;

	private ModEntities() {
	}

	/** Registers the entity types, spawn eggs and attributes, and puts the spawn eggs in the creative tab. */
	public static void register() {
		registerContent();
		FaunaSpawner.register();
		FabricDefaultAttributeRegistry.register(roeDeer(), RoeDeer.createAttributes());
		FabricDefaultAttributeRegistry.register(wildBoar(), WildBoar.createAttributes());
		FabricDefaultAttributeRegistry.register(redSquirrel(), RedSquirrel.createAttributes());
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(out -> {
			out.insertAfter(Items.RAVAGER_SPAWN_EGG, roeDeerSpawnEgg());
			out.insertAfter(roeDeerSpawnEgg(), wildBoarSpawnEgg());
			out.insertAfter(wildBoarSpawnEgg(), redSquirrelSpawnEgg());
		});
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
		// Step FA3: the wild boar, 0.9 x 0.95 blocks (the young at vanilla's baby scale), creature category; no loot (C8).
		wildBoar = type("wild_boar", EntityType.Builder.of(WildBoar::new, MobCategory.CREATURE).sized(0.9F, 0.95F).eyeHeight(0.7F)
				.clientTrackingRange(10).noLootTable());
		wildBoarSpawnEgg = spawnEgg("wild_boar_spawn_egg", wildBoar);
		// Step FA5: the red squirrel, 0.4 x 0.45 blocks, ambient category (outside vanilla's counts like the others, its own
		// limit of the fauna spawner); position updates every 2 ticks for the climbing; no loot (C8).
		redSquirrel = type("red_squirrel", EntityType.Builder.of(RedSquirrel::new, MobCategory.AMBIENT).sized(0.4F, 0.45F).eyeHeight(0.35F)
				.clientTrackingRange(8).updateInterval(2).noLootTable());
		redSquirrelSpawnEgg = spawnEgg("red_squirrel_spawn_egg", redSquirrel);
	}

	public static EntityType<RedSquirrel> redSquirrel() {
		if (redSquirrel == null) {
			throw new IllegalStateException("The entity types are not registered yet");
		}
		return redSquirrel;
	}

	public static Item redSquirrelSpawnEgg() {
		if (redSquirrelSpawnEgg == null) {
			throw new IllegalStateException("The spawn eggs are not registered yet");
		}
		return redSquirrelSpawnEgg;
	}

	public static EntityType<WildBoar> wildBoar() {
		if (wildBoar == null) {
			throw new IllegalStateException("The entity types are not registered yet");
		}
		return wildBoar;
	}

	public static Item wildBoarSpawnEgg() {
		if (wildBoarSpawnEgg == null) {
			throw new IllegalStateException("The spawn eggs are not registered yet");
		}
		return wildBoarSpawnEgg;
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
		if (type == roeDeer) {
			return RoeDeer.SAVED_FIELDS;
		}
		if (type == redSquirrel) {
			return RedSquirrel.SAVED_FIELDS;
		}
		return type == wildBoar ? WildBoar.SAVED_FIELDS : List.of();
	}

	/**
	 * The species the fauna spawners place, with their profiles (step FA1; step FA3 the wild boar after the roe deer, so the
	 * draws of the roe deer at generation stay those of FA2; step FA5 the red squirrel last, so those of the roe deer and the
	 * wild boar stay those of FA3).
	 */
	public static List<FaunaSpawner.Species> fauna() {
		return List.of(new FaunaSpawner.Species(roeDeer(), RoeDeer.PROFILE), new FaunaSpawner.Species(wildBoar(), WildBoar.PROFILE),
				new FaunaSpawner.Species(redSquirrel(), RedSquirrel.PROFILE));
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
