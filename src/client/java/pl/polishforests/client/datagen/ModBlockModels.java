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
import net.minecraft.client.data.models.model.ModelTemplate;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.client.renderer.block.dispatch.Variant;
import net.minecraft.client.renderer.block.dispatch.VariantMutator;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import pl.polishforests.PolishForests;
import pl.polishforests.block.ModBlocks;
import pl.polishforests.block.TreeTrunk;
import pl.polishforests.block.TrunkBlock;

/**
 * Blockstates, block models and item models of the blocks of the mod (step V2: the thin trunks). A trunk has one model
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
			// D2: birch and spruce take the vanilla bark by id (no copy of the file).
			case SPRUCE -> List.of(vanilla("spruce_log"));
			case BIRCH -> List.of(vanilla("birch_log"));
			case OAK -> List.of(own("oak_bark"), own("oak_bark_2"));
			case HORNBEAM -> List.of(own("hornbeam_bark"), own("hornbeam_bark_2"));
			case LINDEN -> List.of(own("linden_bark"), own("linden_bark_2"), own("linden_bark_3"));
			case BEECH -> List.of(own("beech_bark"), own("beech_bark_2"));
			// The shared substitute bark of the broad-crowned archetype (§1.1).
			case ASH, NORWAY_MAPLE -> List.of(own("broadleaf_bark"));
		};
	}

	/**
	 * The end (rings) of a branch tip or a cut trunk: the top of the log the trunk drops ({@link TreeTrunk#log()}), by id;
	 * own ends for every diameter are M3 (docs/04 §1.2).
	 */
	static Identifier end(TreeTrunk t) {
		return switch (t) {
			case SCOTS_PINE, SCOTS_PINE_UPPER, SCOTS_PINE_TRANSITION, SPRUCE -> vanilla("spruce_log_top");
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
