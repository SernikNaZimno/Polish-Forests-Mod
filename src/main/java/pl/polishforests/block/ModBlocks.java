package pl.polishforests.block;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;
import pl.polishforests.PolishForests;

/**
 * Blocks and block items of the mod (vertical slice, step V2: the thin trunks {@link TreeTrunk}). The ids, properties
 * and property values go into the saved worlds; {@code RegistrySnapshotTest} compares them with
 * {@code src/test/resources/registry_snapshot.txt} and they are frozen after checkpoint CP-B (PW-11, ZW-4).
 */
public final class ModBlocks {
	private static final Map<TreeTrunk, TrunkBlock> TRUNKS = new EnumMap<>(TreeTrunk.class);
	/** Hardness and blast resistance of a trunk: as a vanilla log. */
	private static final float STRENGTH = 2.0F;
	/** Fire: catches and burns like a vanilla log ({@code FireBlock}: logs 5, 5). */
	private static final int IGNITE_ODDS = 5;
	private static final int BURN_ODDS = 5;

	private ModBlocks() {
	}

	/** Registers the blocks and items, their flammability and their place in the creative tab. */
	public static void register() {
		registerContent();
		FlammableBlockRegistry fire = FlammableBlockRegistry.getDefaultInstance();
		for (TrunkBlock b : TRUNKS.values()) {
			fire.add(b, IGNITE_ODDS, BURN_ODDS);
		}
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.NATURAL_BLOCKS).register(out -> {
			Item previous = Items.PALE_OAK_LOG;
			for (TrunkBlock b : TRUNKS.values()) {
				out.insertAfter(previous, b);
				previous = b.asItem();
			}
		});
	}

	/**
	 * Only the registry entries (blocks and block items), without the Fabric API registries: {@link #register()} in the
	 * game, alone in the unit tests that compare the registry with the snapshot.
	 */
	public static void registerContent() {
		if (!TRUNKS.isEmpty()) {
			throw new IllegalStateException("The blocks of " + PolishForests.MOD_ID + " are already registered");
		}
		for (TreeTrunk t : TreeTrunk.values()) {
			TrunkBlock block = (TrunkBlock) block(t.id(), TrunkBlock::new, trunkProperties(t));
			item(t.id(), block, new Item.Properties().cookingFuel(ContextIntProviders.COOKING_TIME_WOOD_BLOCKS));
			TRUNKS.put(t, block);
		}
	}

	public static TrunkBlock trunk(TreeTrunk trunk) {
		TrunkBlock b = TRUNKS.get(trunk);
		if (b == null) {
			throw new IllegalStateException("The trunk blocks are not registered yet");
		}
		return b;
	}

	public static Map<TreeTrunk, TrunkBlock> trunks() {
		return Collections.unmodifiableMap(TRUNKS);
	}

	/** Properties of a trunk: as a vanilla log (hardness, sound, note block bass, burns in lava), without its full shape. */
	private static BlockBehaviour.Properties trunkProperties(TreeTrunk t) {
		return BlockBehaviour.Properties.of().mapColor(t.mapColor()).instrument(NoteBlockInstrument.BASS).strength(STRENGTH)
				.sound(SoundType.WOOD).ignitedByLava();
	}

	private static Block block(String path, java.util.function.Function<BlockBehaviour.Properties, Block> factory,
			BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, PolishForests.id(path));
		return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
	}

	private static Item item(String path, Block block, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, PolishForests.id(path));
		BlockItem item = new BlockItem(block, properties.setId(key).useBlockDescriptionPrefix());
		item.registerBlocks(Item.BY_BLOCK, item);
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}
}
