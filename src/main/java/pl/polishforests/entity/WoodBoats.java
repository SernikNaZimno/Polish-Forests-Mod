package pl.polishforests.entity;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.dispenser.BoatDispenseItemBehavior;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.entity.vehicle.boat.ChestBoat;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;
import org.jspecify.annotations.Nullable;
import pl.polishforests.PolishForests;
import pl.polishforests.block.WoodSet;

/**
 * The boats and chest boats of the wood sets (step FA8 of docs/04-pionowy-wycinek.md, option O3 of CP-D.2): for every
 * {@link WoodSet} the entity types {@code polishforests:<prefix>_boat} and {@code <prefix>_chest_boat} of vanilla's classes
 * ({@link Boat}, {@link ChestBoat}: the swimming, riding, the chest and its saved items are vanilla's, the mod adds no saved
 * field) with vanilla's size, tracking range and no loot table, and their items of the same ids ({@link BoatItem}, one to a
 * stack, the fuel of vanilla's boats). A broken boat drops its own item ({@code getDropItem} of vanilla's boat reads the
 * item supplier). The entity type and item ids go into the saved worlds (registry snapshot, PW-11), so they are registered
 * after all others of the mod (the end of {@link ModEntities#registerContent()}), in the order of {@link WoodSet}.
 *
 * <p>The client draws them with vanilla's boat model layers by id and an entity texture derived from vanilla's boat textures
 * of the base wood ({@code WoodBoatRenderer}, {@code DerivedSignScreens}).
 */
public final class WoodBoats {
	/** The boat and chest boat of one wood set with their items. */
	public record Set(EntityType<Boat> boat, EntityType<ChestBoat> chestBoat, Item boatItem, Item chestBoatItem) {
	}

	private static final Map<WoodSet, Set> SETS = new EnumMap<>(WoodSet.class);

	private WoodBoats() {
	}

	/** Registers the entity types and items of every wood set (called last by {@link ModEntities#registerContent()}). */
	static void registerContent() {
		for (WoodSet w : WoodSet.values()) {
			SETS.put(w, register(w));
		}
	}

	/** The dispenser places the boats as vanilla's (called by {@link ModEntities#register()}). */
	static void registerBehavior() {
		for (Set s : SETS.values()) {
			DispenserBlock.registerBehavior(s.boatItem(), new BoatDispenseItemBehavior(s.boat()));
			DispenserBlock.registerBehavior(s.chestBoatItem(), new BoatDispenseItemBehavior(s.chestBoat()));
		}
	}

	private static Set register(WoodSet w) {
		String boatId = w.prefix() + "_boat";
		String chestId = w.prefix() + "_chest_boat";
		// The item and the entity type need each other: the entity's drop item is read through a supplier.
		Item[] items = new Item[2];
		EntityType<Boat> boat = type(boatId, EntityType.Builder.<Boat>of((type, level) -> new Boat(type, level, () -> items[0]),
				MobCategory.MISC));
		EntityType<ChestBoat> chest = type(chestId, EntityType.Builder.<ChestBoat>of(
				(type, level) -> new ChestBoat(type, level, () -> items[1]), MobCategory.MISC));
		items[0] = item(boatId, boat);
		items[1] = item(chestId, chest);
		return new Set(boat, chest, items[0], items[1]);
	}

	/** Vanilla's boat: 1.375 × 0.5625 blocks, eyes at 0.5625, tracking range 10 chunks, no loot table. */
	private static <T extends net.minecraft.world.entity.Entity> EntityType<T> type(String path, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, PolishForests.id(path));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key,
				builder.noLootTable().sized(1.375F, 0.5625F).eyeHeight(0.5625F).clientTrackingRange(10).build(key));
	}

	private static Item item(String path, EntityType<? extends net.minecraft.world.entity.vehicle.boat.AbstractBoat> type) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, PolishForests.id(path));
		return Registry.register(BuiltInRegistries.ITEM, key, new BoatItem(type,
				new Item.Properties().cookingFuel(ContextIntProviders.COOKING_TIME_BOATS).stacksTo(1).setId(key)));
	}

	/** The boats of a wood set. */
	public static Set of(WoodSet w) {
		Set s = SETS.get(w);
		if (s == null) {
			throw new IllegalStateException("The boats are not registered yet");
		}
		return s;
	}

	public static Map<WoodSet, Set> all() {
		return Collections.unmodifiableMap(SETS);
	}

	/** Whether the entity type is a boat or chest boat of the mod. */
	public static boolean boat(@Nullable EntityType<?> type) {
		for (Set s : SETS.values()) {
			if (s.boat() == type || s.chestBoat() == type) {
				return true;
			}
		}
		return false;
	}

	/** Every boat and chest boat type in the order of registration. */
	public static List<EntityType<?>> types() {
		return SETS.values().stream().<EntityType<?>>mapMulti((s, out) -> {
			out.accept(s.boat());
			out.accept(s.chestBoat());
		}).toList();
	}
}
