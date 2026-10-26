package pl.polishforests.block;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.item.v1.BlockTransformerHelper;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.levelgen.feature.stateproviders.CopyPropertiesProvider;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;
import pl.polishforests.PolishForests;

/**
 * Blocks and block items of the mod (vertical slice, step V2: the thin trunks {@link TreeTrunk}; step V7: the leaves,
 * saplings and potted saplings of the species ({@link TreeFoliage}) and the LITE wood set of the Scots pine, decision D1
 * (b): log, wood, stripped log, stripped wood and planks, the log and the wood stripped with an axe; step I1: the root
 * flares of the species {@link RootFlare} and the underground root {@value #TREE_ROOT}). The ids, properties
 * and property values go into the saved worlds; {@code RegistrySnapshotTest} compares them with
 * {@code src/test/resources/registry_snapshot.txt} and they are frozen after checkpoint CP-B (PW-11, ZW-4).
 */
public final class ModBlocks {
	private static final Map<TreeTrunk, TrunkBlock> TRUNKS = new EnumMap<>(TreeTrunk.class);
	private static final Map<TreeFoliage, SpeciesLeavesBlock> LEAVES = new EnumMap<>(TreeFoliage.class);
	private static final Map<TreeFoliage, SpeciesSaplingBlock> SAPLINGS = new EnumMap<>(TreeFoliage.class);
	private static final Map<TreeFoliage, FlowerPotBlock> POTTED = new EnumMap<>(TreeFoliage.class);
	/** Step I1: the root flares of the species and the underground root. */
	private static final Map<RootFlare, RootFlareBlock> FLARES = new EnumMap<>(RootFlare.class);
	private static TrunkBlock treeRoot;
	/** Hardness and blast resistance of the underground root: as vanilla mangrove roots, quicker than a log. */
	private static final float ROOT_STRENGTH = 0.7F;
	/** Every block of the mod in registration order. */
	private static final List<Block> ALL = new ArrayList<>();
	/** Hardness and blast resistance of a trunk: as a vanilla log. */
	private static final float STRENGTH = 2.0F;
	/** Fire: catches and burns like a vanilla log ({@code FireBlock}: logs 5, 5). */
	private static final int IGNITE_ODDS = 5;
	private static final int BURN_ODDS = 5;
	/** Falling leaf particles per animate tick of a broadleaf leaf block (as vanilla oak) and of the needles. */
	private static final float LEAF_PARTICLES = 0.01F;
	private static final float NEEDLE_PARTICLES = 0.002F;

	/** The LITE wood set of the Scots pine (D1 b), set by {@link #registerContent()}. */
	private static Block pineLog;
	private static Block pineWood;
	private static Block strippedPineLog;
	private static Block strippedPineWood;
	private static Block pinePlanks;

	private ModBlocks() {
	}

	/** Registers the blocks and items, their flammability, axe stripping and their place in the creative tabs. */
	public static void register() {
		registerContent();
		FlammableBlockRegistry fire = FlammableBlockRegistry.getDefaultInstance();
		for (TrunkBlock b : TRUNKS.values()) {
			fire.add(b, IGNITE_ODDS, BURN_ODDS);
		}
		for (RootFlareBlock b : FLARES.values()) {
			fire.add(b, IGNITE_ODDS, BURN_ODDS);
		}
		fire.add(treeRoot, IGNITE_ODDS, BURN_ODDS);
		// As the vanilla FireBlock: leaves 30 / 60, logs 5 / 5, planks 5 / 20.
		for (SpeciesLeavesBlock b : LEAVES.values()) {
			fire.add(b, 30, 60);
		}
		for (Block b : List.of(pineLog, pineWood, strippedPineLog, strippedPineWood)) {
			fire.add(b, IGNITE_ODDS, BURN_ODDS);
		}
		fire.add(pinePlanks, 5, 20);
		// Axe stripping of the full log and wood, keeping the axis (the thin trunks are not stripped until M3).
		BlockTransformerHelper.registerStripping(pineLog, new CopyPropertiesProvider(strippedPineLog));
		BlockTransformerHelper.registerStripping(pineWood, new CopyPropertiesProvider(strippedPineWood));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.NATURAL_BLOCKS).register(out -> {
			Item previous = Items.PALE_OAK_LOG;
			for (TrunkBlock b : TRUNKS.values()) {
				out.insertAfter(previous, b);
				previous = b.asItem();
			}
			out.insertAfter(previous, pineLog);
			previous = pineLog.asItem();
			for (RootFlareBlock b : FLARES.values()) {
				out.insertAfter(previous, b);
				previous = b.asItem();
			}
			out.insertAfter(previous, treeRoot);
			previous = Items.PALE_OAK_LEAVES;
			for (SpeciesLeavesBlock b : LEAVES.values()) {
				out.insertAfter(previous, b);
				previous = b.asItem();
			}
			previous = Items.PALE_OAK_SAPLING;
			for (SpeciesSaplingBlock b : SAPLINGS.values()) {
				out.insertAfter(previous, b);
				previous = b.asItem();
			}
		});
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.BUILDING_BLOCKS).register(out -> {
			Item previous = Items.PALE_OAK_BUTTON;
			for (Block b : List.of(pineLog, pineWood, strippedPineLog, strippedPineWood, pinePlanks)) {
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
			item(t.id(), block, woodFuel());
			TRUNKS.put(t, block);
		}
		pineLog = block("scots_pine_log", RotatedPillarBlock::new, Blocks.logProperties(MapColor.WOOD, MapColor.PODZOL, SoundType.WOOD));
		item("scots_pine_log", pineLog, woodFuel());
		pineWood = block("scots_pine_wood", RotatedPillarBlock::new, Blocks.logProperties(MapColor.PODZOL, MapColor.PODZOL, SoundType.WOOD));
		item("scots_pine_wood", pineWood, woodFuel());
		strippedPineLog = block("stripped_scots_pine_log", RotatedPillarBlock::new,
				Blocks.logProperties(MapColor.WOOD, MapColor.WOOD, SoundType.WOOD));
		item("stripped_scots_pine_log", strippedPineLog, woodFuel());
		strippedPineWood = block("stripped_scots_pine_wood", RotatedPillarBlock::new,
				Blocks.logProperties(MapColor.WOOD, MapColor.WOOD, SoundType.WOOD));
		item("stripped_scots_pine_wood", strippedPineWood, woodFuel());
		pinePlanks = block("scots_pine_planks", Block::new, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
				.instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F).sound(SoundType.WOOD).ignitedByLava());
		item("scots_pine_planks", pinePlanks, woodFuel());
		for (TreeFoliage f : TreeFoliage.values()) {
			float particles = f.conifer() ? NEEDLE_PARTICLES : LEAF_PARTICLES;
			SpeciesLeavesBlock leaves = (SpeciesLeavesBlock) block(f.leavesId(), p -> f.marcescent()
					? new MarcescentLeavesBlock(f, particles, p) : new SpeciesLeavesBlock(f, particles, p),
					Blocks.leavesProperties(SoundType.GRASS));
			item(f.leavesId(), leaves, new Item.Properties().compostable(ContextIntProviders.COMPOSTABLE_LOW));
			LEAVES.put(f, leaves);
			SpeciesSaplingBlock sapling = (SpeciesSaplingBlock) block(f.saplingId(), p -> new SpeciesSaplingBlock(f, p),
					BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollision().randomTicks().instabreak()
							.sound(SoundType.GRASS).pushReaction(PushReaction.POPPED));
			item(f.saplingId(), sapling, new Item.Properties().compostable(ContextIntProviders.COMPOSTABLE_LOW)
					.cookingFuel(ContextIntProviders.COOKING_TIME_DRY_PLANTS));
			SAPLINGS.put(f, sapling);
			POTTED.put(f, (FlowerPotBlock) block(f.pottedId(), p -> new FlowerPotBlock(sapling, p), Blocks.flowerPotProperties()));
		}
		// Step I1 (roots), registered after the frozen blocks of CP-B: a root flare per species and the underground root.
		for (RootFlare f : RootFlare.values()) {
			RootFlareBlock block = (RootFlareBlock) block(f.id(), RootFlareBlock::new, trunkProperties(f.trunk()));
			item(f.id(), block, woodFuel());
			FLARES.put(f, block);
		}
		treeRoot = (TrunkBlock) block(TREE_ROOT, TrunkBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.DIRT)
				.instrument(NoteBlockInstrument.BASS).strength(ROOT_STRENGTH).sound(SoundType.MANGROVE_ROOTS).ignitedByLava());
		item(TREE_ROOT, treeRoot, woodFuel());
	}

	/** Block and item path of the underground root (step I1). */
	public static final String TREE_ROOT = "tree_root";

	private static Item.Properties woodFuel() {
		return new Item.Properties().cookingFuel(ContextIntProviders.COOKING_TIME_WOOD_BLOCKS);
	}

	public static TrunkBlock trunk(TreeTrunk trunk) {
		return registered(TRUNKS.get(trunk));
	}

	public static Map<TreeTrunk, TrunkBlock> trunks() {
		return Collections.unmodifiableMap(TRUNKS);
	}

	/** The root flare of a species (step I1). */
	public static RootFlareBlock flare(RootFlare f) {
		return registered(FLARES.get(f));
	}

	public static Map<RootFlare, RootFlareBlock> flares() {
		return Collections.unmodifiableMap(FLARES);
	}

	/** The underground root (step I1): a thin trunk block with the root bark, not a log. */
	public static TrunkBlock treeRoot() {
		return registered(treeRoot);
	}

	public static SpeciesLeavesBlock leaves(TreeFoliage f) {
		return registered(LEAVES.get(f));
	}

	public static SpeciesSaplingBlock sapling(TreeFoliage f) {
		return registered(SAPLINGS.get(f));
	}

	public static FlowerPotBlock potted(TreeFoliage f) {
		return registered(POTTED.get(f));
	}

	public static Block pineLog() {
		return registered(pineLog);
	}

	public static Block pineWood() {
		return registered(pineWood);
	}

	public static Block strippedPineLog() {
		return registered(strippedPineLog);
	}

	public static Block strippedPineWood() {
		return registered(strippedPineWood);
	}

	public static Block pinePlanks() {
		return registered(pinePlanks);
	}

	/** Every block of the mod, in registration order. */
	public static List<Block> all() {
		return Collections.unmodifiableList(ALL);
	}

	private static <B> B registered(B b) {
		if (b == null) {
			throw new IllegalStateException("The blocks of " + PolishForests.MOD_ID + " are not registered yet");
		}
		return b;
	}

	/** Properties of a trunk: as a vanilla log (hardness, sound, note block bass, burns in lava), without its full shape. */
	private static BlockBehaviour.Properties trunkProperties(TreeTrunk t) {
		return BlockBehaviour.Properties.of().mapColor(t.mapColor()).instrument(NoteBlockInstrument.BASS).strength(STRENGTH)
				.sound(SoundType.WOOD).ignitedByLava();
	}

	private static Block block(String path, java.util.function.Function<BlockBehaviour.Properties, Block> factory,
			BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, PolishForests.id(path));
		Block b = Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
		ALL.add(b);
		return b;
	}

	private static Item item(String path, Block block, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, PolishForests.id(path));
		BlockItem item = new BlockItem(block, properties.setId(key).useBlockDescriptionPrefix());
		item.registerBlocks(Item.BY_BLOCK, item);
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}
}
