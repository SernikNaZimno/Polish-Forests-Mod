package pl.polishforests.client.datagen;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.datagen.v1.provider.FabricModelProvider;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.MultiVariant;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;
import net.minecraft.client.data.models.model.ItemModelUtils;
import net.minecraft.client.data.models.model.ModelTemplate;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.client.renderer.block.dispatch.Variant;
import net.minecraft.client.renderer.block.dispatch.VariantMutator;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import pl.polishforests.PolishForests;
import pl.polishforests.block.ModBlocks;
import pl.polishforests.block.TreeFoliage;
import pl.polishforests.block.TreeTrunk;
import pl.polishforests.block.TrunkBlock;
import pl.polishforests.season.FoliageCurve;

/**
 * Blockstates, block models and item models of the blocks of the mod (step V2: the thin trunks; step V7: the LITE Scots
 * pine set, the leaves, saplings and potted saplings of the species). A trunk has one model
 * per radius and bark texture ({@code <trunk>_r<radius>}, a second bark variant {@code _r<radius>_2}) made from the
 * templates of {@link TrunkTemplates}; the blockstate picks the model by {@code radius} (random among the bark variants,
 * like vanilla's weighted variants) and rotates it to the {@code parent} ({@code PropertyDispatch.modify(PARENT)}).
 * {@code waterlogged} does not change the model.
 */
final class ModBlockModels extends FabricModelProvider {
	static final TextureSlot BARK = TextureSlot.create("bark");

	ModBlockModels(FabricPackOutput output) {
		super(output);
	}

	/**
	 * The template is the arm pointing down; rotations to the parent (vanilla's {@code ROTATIONS_COLUMN_WITH_FACING}
	 * turned upside down: x 270 brings the down arm to the north).
	 */
	static final PropertyDispatch<VariantMutator> PARENT_ROTATIONS = PropertyDispatch.modify(TrunkBlock.PARENT)
			.select(Direction.DOWN, BlockModelGenerators.NOP)
			.select(Direction.UP, BlockModelGenerators.X_ROT_180)
			.select(Direction.NORTH, BlockModelGenerators.X_ROT_270)
			.select(Direction.SOUTH, BlockModelGenerators.X_ROT_270.then(BlockModelGenerators.Y_ROT_180))
			.select(Direction.WEST, BlockModelGenerators.X_ROT_270.then(BlockModelGenerators.Y_ROT_270))
			.select(Direction.EAST, BlockModelGenerators.X_ROT_270.then(BlockModelGenerators.Y_ROT_90));

	/** Bark textures of a trunk (one to three variants with the same top and bottom rows). */
	static List<Identifier> barks(TreeTrunk t) {
		return switch (t) {
			case SCOTS_PINE -> List.of(own("scots_pine_bark"), own("scots_pine_bark_2"));
			case SCOTS_PINE_UPPER -> List.of(own("scots_pine_upper_bark"), own("scots_pine_upper_bark_2"));
			case SCOTS_PINE_TRANSITION -> List.of(own("scots_pine_transition_bark"), own("scots_pine_transition_bark_2"));
			case SCOTS_PINE_TRANSITION_UPPER ->
					List.of(own("scots_pine_transition_upper_bark"), own("scots_pine_transition_upper_bark_2"));
			// D2: birch and spruce take the vanilla bark by id (no copy of the file).
			case SPRUCE -> List.of(vanilla("spruce_log"));
			case BIRCH -> List.of(vanilla("birch_log"));
			case OAK -> List.of(own("oak_bark"), own("oak_bark_2"));
			case HORNBEAM -> List.of(own("hornbeam_bark"), own("hornbeam_bark_2"));
			case LINDEN -> List.of(own("linden_bark"), own("linden_bark_2"), own("linden_bark_3"));
			case BEECH -> List.of(own("beech_bark"), own("beech_bark_2"), own("beech_bark_3"));
			// The shared substitute bark of the broad-crowned archetype (§1.1).
			case ASH, NORWAY_MAPLE -> List.of(own("broadleaf_bark"));
		};
	}

	/**
	 * The end (rings) of a branch tip or a cut trunk: the top of the log the trunk drops ({@link TreeTrunk#log()}): the
	 * own Scots pine log top (step V7), the others by id; own ends for every diameter are M3 (docs/04 §1.2).
	 */
	static Identifier end(TreeTrunk t) {
		return switch (t) {
			case SCOTS_PINE, SCOTS_PINE_UPPER, SCOTS_PINE_TRANSITION, SCOTS_PINE_TRANSITION_UPPER -> own("scots_pine_log_top");
			case SPRUCE -> vanilla("spruce_log_top");
			case BIRCH -> vanilla("birch_log_top");
			case OAK, LINDEN, ASH, NORWAY_MAPLE -> vanilla("oak_log_top");
			case HORNBEAM, BEECH -> vanilla("pale_oak_log_top");
		};
	}

	@Override
	public void generateBlockStateModels(BlockModelGenerators gen) {
		for (TreeTrunk t : TreeTrunk.values()) {
			trunk(gen, t);
		}
		// Step V7: the LITE Scots pine set (the log side is the lower pine bark, as the thin trunk).
		Material bark = new Material(own("scots_pine_bark"));
		gen.new WoodProvider(TextureMapping.column(bark, new Material(own("scots_pine_log_top")))).logWithHorizontal(ModBlocks.pineLog())
				.wood(ModBlocks.pineWood());
		gen.new WoodProvider(TextureMapping.column(new Material(own("stripped_scots_pine_log")),
				new Material(own("stripped_scots_pine_log_top")))).logWithHorizontal(ModBlocks.strippedPineLog())
				.wood(ModBlocks.strippedPineWood());
		gen.createTrivialCube(ModBlocks.pinePlanks());
		for (TreeFoliage f : TreeFoliage.values()) {
			leaves(gen, f);
			sapling(gen, f);
		}
	}

	/** The leaves texture of a species (D2, V7): own needles and broadleaf leaves, birch and spruce from vanilla by id. */
	static Identifier leavesTexture(TreeFoliage f) {
		return switch (f) {
			case SCOTS_PINE -> own("scots_pine_leaves");
			case SPRUCE -> vanilla("spruce_leaves");
			case BIRCH -> vanilla("birch_leaves");
			default -> own("broadleaf_leaves");
		};
	}

	/** The sapling texture of a species: own, except oak, birch and spruce from vanilla by id (D2). */
	static Identifier saplingTexture(TreeFoliage f) {
		return switch (f) {
			case OAK -> vanilla("oak_sapling");
			case BIRCH -> vanilla("birch_sapling");
			case SPRUCE -> vanilla("spruce_sapling");
			default -> own(f.saplingId());
		};
	}

	/**
	 * Leaves: vanilla's leaves template (cutout, {@code tintindex} 0) for every state (phase, distance, persistence and
	 * water do not change the model); the item is tinted with the species' summer color over vanilla's default foliage
	 * color ({@link FoliageCurve#summer}).
	 */
	private static void leaves(BlockModelGenerators gen, TreeFoliage f) {
		Block block = ModBlocks.leaves(f);
		Identifier model = ModelTemplates.LEAVES.create(block, TextureMapping.cube(new Material(leavesTexture(f))), gen.modelOutput);
		gen.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(block, BlockModelGenerators.plainVariant(model)));
		gen.registerSimpleTintedItemModel(block, model,
				ItemModelUtils.constantTint(0xFF000000 | FoliageCurve.summer(f.species(), FoliageCurve.DEFAULT_FOLIAGE)));
	}

	/** A sapling (cross, not tinted), its flat item model and its flower pot, as vanilla's saplings. */
	private static void sapling(BlockModelGenerators gen, TreeFoliage f) {
		Material texture = new Material(saplingTexture(f));
		Block sapling = ModBlocks.sapling(f);
		Block potted = ModBlocks.potted(f);
		gen.createCrossBlock(sapling, BlockModelGenerators.PlantType.NOT_TINTED, TextureMapping.cross(texture));
		gen.registerSimpleItemModel(sapling.asItem(), gen.createFlatItemModel(sapling.asItem(), texture));
		Identifier pot = ModelTemplates.FLOWER_POT_CROSS.create(potted, TextureMapping.plant(texture), gen.modelOutput);
		gen.blockStateOutput.accept(BlockModelGenerators.createSimpleBlock(potted, BlockModelGenerators.plainVariant(pot)));
	}

	@Override
	public void generateItemModels(ItemModelGenerators gen) {
		// The block items get their models with the blocks (registerSimpleItemModel).
	}

	private static void trunk(BlockModelGenerators gen, TreeTrunk t) {
		TrunkBlock block = ModBlocks.trunk(t);
		List<Identifier> barks = barks(t);
		PropertyDispatch.C1<MultiVariant, Integer> radii = PropertyDispatch.initial(TrunkBlock.RADIUS);
		for (int r = TrunkBlock.MIN_RADIUS; r <= TrunkBlock.MAX_RADIUS; r++) {
			ModelTemplate template = new ModelTemplate(Optional.of(PolishForests.id(TrunkTemplates.template(r))), Optional.empty(), BARK,
					TextureSlot.END);
			List<Variant> variants = new ArrayList<>();
			for (int k = 0; k < barks.size(); k++) {
				String suffix = "_r" + r + (k == 0 ? "" : "_" + (k + 1));
				Identifier model = template.createWithSuffix(block, suffix, textures(barks.get(k), end(t)), gen.modelOutput);
				variants.add(BlockModelGenerators.plainModel(model));
			}
			radii.select(r, BlockModelGenerators.variants(variants.toArray(Variant[]::new)));
		}
		gen.blockStateOutput.accept(MultiVariantGenerator.dispatch(block).with(radii).with(PARENT_ROTATIONS));
		ModelTemplate item = new ModelTemplate(Optional.of(PolishForests.id(TrunkTemplates.ITEM_TEMPLATE)), Optional.of("_inventory"),
				BARK, TextureSlot.END);
		gen.registerSimpleItemModel(block, item.create(block, textures(barks.get(0), end(t)), gen.modelOutput));
	}

	private static TextureMapping textures(Identifier bark, Identifier end) {
		return new TextureMapping().put(BARK, new Material(bark)).put(TextureSlot.END, new Material(end));
	}

	private static Identifier own(String name) {
		return PolishForests.id("block/" + name);
	}

	private static Identifier vanilla(String name) {
		return Identifier.withDefaultNamespace("block/" + name);
	}

	@Override
	public String getName() {
		return "Polish Forests block models";
	}
}
