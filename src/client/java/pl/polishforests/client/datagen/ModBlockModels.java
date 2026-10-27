package pl.polishforests.client.datagen;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.datagen.v1.provider.FabricModelProvider;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.MultiVariant;
import net.minecraft.client.data.models.blockstates.ConditionBuilder;
import net.minecraft.client.data.models.blockstates.MultiPartGenerator;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import pl.polishforests.block.BerryShrubBlock;
import pl.polishforests.block.GroundPlant;
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
		// Step I1: the root flares (the bark of the species' lower trunk) and the underground root (own root bark, the oak
		// log top as its rings, by id).
		for (pl.polishforests.block.RootFlare f : pl.polishforests.block.RootFlare.values()) {
			flare(gen, f);
		}
		trunk(gen, ModBlocks.treeRoot(), List.of(own("tree_root_bark")), vanilla("oak_log_top"));
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
		// Step V12: the herb layer and litter.
		MultiVariant moss = mossLayer(gen);
		for (GroundPlant g : GroundPlant.values()) {
			ground(gen, g, moss);
		}
		gen.registerSimpleItemModel(ModBlocks.bilberries(), gen.createFlatItemModel(ModBlocks.bilberries(),
				new Material(own(ModBlocks.BILBERRIES))));
		// Round 1 of the V12 review: the own moss carpet (the moss layer model, four turns) and the mossy podzol (the own
		// feather moss on top, the vanilla podzol side and dirt bottom by id, four turns of the top).
		gen.blockStateOutput.accept(MultiVariantGenerator.dispatch(ModBlocks.forestMossCarpet(), moss));
		gen.registerSimpleItemModel(ModBlocks.forestMossCarpet(), PolishForests.id("block/forest_moss_layer"));
		Identifier podzol = ModelTemplates.CUBE_BOTTOM_TOP.create(ModBlocks.mossyPodzol(), new TextureMapping()
				.put(TextureSlot.TOP, new Material(own("forest_moss"))).put(TextureSlot.SIDE, new Material(vanilla("podzol_side")))
				.put(TextureSlot.BOTTOM, new Material(vanilla("dirt"))), gen.modelOutput);
		gen.blockStateOutput.accept(MultiVariantGenerator.dispatch(ModBlocks.mossyPodzol(), BlockModelGenerators.variants(
				BlockModelGenerators.plainModel(podzol), BlockModelGenerators.plainModel(podzol).with(BlockModelGenerators.Y_ROT_90),
				BlockModelGenerators.plainModel(podzol).with(BlockModelGenerators.Y_ROT_180),
				BlockModelGenerators.plainModel(podzol).with(BlockModelGenerators.Y_ROT_270))));
		gen.registerSimpleItemModel(ModBlocks.mossyPodzol(), podzol);
	}

	/**
	 * The moss layer of a mossy block (PW-7): the vanilla carpet model by id with the own feather moss texture
	 * {@code forest_moss} (duller and greener than the vanilla moss carpet, V0 review), in four random rotations.
	 */
	private static MultiVariant mossLayer(BlockModelGenerators gen) {
		Identifier m = new ModelTemplate(Optional.of(Identifier.withDefaultNamespace("block/carpet")), Optional.empty(), TextureSlot.WOOL)
				.create(PolishForests.id("block/forest_moss_layer"), new TextureMapping().put(TextureSlot.WOOL, new Material(own("forest_moss"))),
						gen.modelOutput);
		return BlockModelGenerators.variants(BlockModelGenerators.plainModel(m), BlockModelGenerators.plainModel(m).with(BlockModelGenerators.Y_ROT_90),
				BlockModelGenerators.plainModel(m).with(BlockModelGenerators.Y_ROT_180),
				BlockModelGenerators.plainModel(m).with(BlockModelGenerators.Y_ROT_270));
	}
	/** The texture slot of the berries template. */
	static final TextureSlot BERRIES = TextureSlot.create("berries");

	/**
	 * A block of the herb layer (step V12). The dwarf shrub: the vanilla tinted cross with its texture, the berries as
	 * the untinted cross {@link GroundTemplates#BERRIES} when {@code berries} is on, the moss layer under it when
	 * {@code mossy} is on; the item flat and tinted with the grass color. A spring geophyte: the vanilla flowerbed models
	 * with its texture and the shared stem {@code forest_flower_stem}, segments as vanilla's flower beds. A litter: the
	 * vanilla leaf litter templates with its texture, or with {@code mossy} the templates one pixel higher
	 * ({@link GroundTemplates#mossyLitter}) over the moss layer; segments as vanilla's leaf litter. The tall fern: the
	 * vanilla tinted double plant with the textures {@code <id>_bottom} and {@code <id>_top}.
	 */
	private static void ground(BlockModelGenerators gen, GroundPlant g, MultiVariant moss) {
		Block block = ModBlocks.ground(g);
		switch (g.kind()) {
			case BERRY_SHRUB -> {
				Identifier shrub = ModelTemplates.TINTED_CROSS.create(block, TextureMapping.cross(new Material(own(g.id()))), gen.modelOutput);
				// Round 1 of the V12 review: the vanilla cross and the own turned and shifted crosses, each in four turns of
				// the block, drawn at random per position (a carpet of bilberry read as rows of a planted crop).
				List<Variant> shrubs = new ArrayList<>();
				for (int k = 0; k <= GroundTemplates.SHRUB_VARIANTS; k++) {
					Identifier m = k == 0 ? shrub : new ModelTemplate(Optional.of(PolishForests.id(GroundTemplates.shrubCross(k))),
							Optional.of("_" + k), TextureSlot.CROSS).create(block, TextureMapping.cross(new Material(own(g.id()))),
							gen.modelOutput);
					for (VariantMutator y : List.of(BlockModelGenerators.NOP, BlockModelGenerators.Y_ROT_90, BlockModelGenerators.Y_ROT_180,
							BlockModelGenerators.Y_ROT_270)) {
						shrubs.add(BlockModelGenerators.plainModel(m).with(y));
					}
				}
				ModelTemplate berriesTemplate = new ModelTemplate(Optional.of(PolishForests.id(GroundTemplates.BERRIES)), Optional.of("_berries"),
						BERRIES);
				Identifier berries = berriesTemplate.create(block, new TextureMapping().put(BERRIES, new Material(own(g.id() + "_berries"))),
						gen.modelOutput);
				// Step V13: the flowers over the shrub in May (a seasonal alternative of the client, not in the block state).
				berriesTemplate.create(pl.polishforests.client.ClientSeasonModels.flowers(g), new TextureMapping().put(BERRIES,
						new Material(own(g.id() + "_flowers"))), gen.modelOutput);
				gen.blockStateOutput.accept(MultiPartGenerator.multiPart(block)
						.with(BlockModelGenerators.variants(shrubs.toArray(Variant[]::new)))
						.with(BlockModelGenerators.condition().term(BerryShrubBlock.BERRIES, true), BlockModelGenerators.plainVariant(berries))
						.with(BlockModelGenerators.condition().term(BerryShrubBlock.MOSSY, true), moss));
				gen.createItemWithGrassTint(block);
			}
			case SEASONAL -> {
				IntegerProperty amount = BlockStateProperties.FLOWER_AMOUNT;
				TextureMapping textures = new TextureMapping().put(TextureSlot.FLOWERBED, new Material(own(g.id())))
						.put(TextureSlot.STEM, new Material(own("forest_flower_stem")));
				MultiVariant[] m = {
						BlockModelGenerators.plainVariant(ModelTemplates.FLOWERBED_1.create(block, textures, gen.modelOutput)),
						BlockModelGenerators.plainVariant(ModelTemplates.FLOWERBED_2.create(block, textures, gen.modelOutput)),
						BlockModelGenerators.plainVariant(ModelTemplates.FLOWERBED_3.create(block, textures, gen.modelOutput)),
						BlockModelGenerators.plainVariant(ModelTemplates.FLOWERBED_4.create(block, textures, gen.modelOutput))};
				// Step V13: the leaves without flowers (block/<id>_leaves_1 … 4), a seasonal alternative of the client.
				TextureMapping leaves = new TextureMapping().put(TextureSlot.FLOWERBED, new Material(own(g.id() + "_leaves")))
						.put(TextureSlot.STEM, new Material(own("forest_flower_stem")));
				for (ModelTemplate t : List.of(ModelTemplates.FLOWERBED_1, ModelTemplates.FLOWERBED_2, ModelTemplates.FLOWERBED_3,
						ModelTemplates.FLOWERBED_4)) {
					t.createWithSuffix(block, "_leaves", leaves, gen.modelOutput);
				}
				// As vanilla's flower beds: model k with k or more flowers.
				gen.createSegmentedBlock(block, m[0], c -> c.term(amount, 1, 2, 3, 4), m[1], c -> c.term(amount, 2, 3, 4),
						m[2], c -> c.term(amount, 3, 4), m[3], c -> c.term(amount, 4));
				gen.registerSimpleFlatItemModel(block);
			}
			case COVER -> {
				IntegerProperty amount = BlockStateProperties.SEGMENT_AMOUNT;
				TextureMapping textures = new TextureMapping().put(TextureSlot.TEXTURE, new Material(own(g.id())));
				ModelTemplate[] plain = {ModelTemplates.LEAF_LITTER_1, ModelTemplates.LEAF_LITTER_2, ModelTemplates.LEAF_LITTER_3,
						ModelTemplates.LEAF_LITTER_4};
				MultiPartGenerator parts = MultiPartGenerator.multiPart(block);
				// As vanilla's leaf litter: model 1 with one segment, 2 with two or three, 3 with three, 4 with four.
				int[][] amounts = {{1}, {2, 3}, {3}, {4}};
				for (int k = 1; k <= 4; k++) {
					Identifier low = plain[k - 1].create(block, textures, gen.modelOutput);
					Identifier high = new ModelTemplate(Optional.of(PolishForests.id(GroundTemplates.mossyLitter(k))), Optional.of("_mossy_" + k),
							TextureSlot.TEXTURE).create(block, textures, gen.modelOutput);
					for (boolean mossy : new boolean[] {false, true}) {
						MultiVariant model = BlockModelGenerators.plainVariant(mossy ? high : low);
						for (Direction d : Direction.Plane.HORIZONTAL) {
							ConditionBuilder c = BlockModelGenerators.condition().term(BlockStateProperties.HORIZONTAL_FACING, d)
									.term(BerryShrubBlock.MOSSY, mossy);
							c = amounts[k - 1].length == 1 ? c.term(amount, amounts[k - 1][0])
									: c.term(amount, amounts[k - 1][0], amounts[k - 1][1]);
							parts.with(c, model.with(facing(d)));
						}
					}
				}
				parts.with(BlockModelGenerators.condition().term(BerryShrubBlock.MOSSY, true), moss);
				gen.blockStateOutput.accept(parts);
				gen.registerSimpleFlatItemModel(block);
			}
			case TALL -> gen.createTintedDoublePlant(block);
		}
	}

	/** Rotation of a segment model drawn facing north, as vanilla's segmented blocks. */
	private static VariantMutator facing(Direction d) {
		return switch (d) {
			case EAST -> BlockModelGenerators.Y_ROT_90;
			case SOUTH -> BlockModelGenerators.Y_ROT_180;
			case WEST -> BlockModelGenerators.Y_ROT_270;
			default -> BlockModelGenerators.NOP;
		};
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
		if (!f.conifer()) {
			// Step V13 (T-D5): the bare twigs of the leafless crown, a seasonal alternative of the client (tint index 1).
			new ModelTemplate(Optional.of(PolishForests.id(GroundTemplates.TWIGS)), Optional.empty(), TextureSlot.ALL)
					.create(pl.polishforests.client.ClientSeasonModels.twigs(f), TextureMapping.cube(new Material(own(f.species().id()
							+ "_twigs"))), gen.modelOutput);
		}
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
		trunk(gen, ModBlocks.trunk(t), barks(t), end(t));
	}

	private static void trunk(BlockModelGenerators gen, TrunkBlock block, List<Identifier> barks, Identifier end) {
		PropertyDispatch.C1<MultiVariant, Integer> radii = PropertyDispatch.initial(TrunkBlock.RADIUS);
		for (int r = TrunkBlock.MIN_RADIUS; r <= TrunkBlock.MAX_RADIUS; r++) {
			ModelTemplate template = new ModelTemplate(Optional.of(PolishForests.id(TrunkTemplates.template(r))), Optional.empty(), BARK,
					TextureSlot.END);
			List<Variant> variants = new ArrayList<>();
			for (int k = 0; k < barks.size(); k++) {
				String suffix = "_r" + r + (k == 0 ? "" : "_" + (k + 1));
				Identifier model = template.createWithSuffix(block, suffix, textures(barks.get(k), end), gen.modelOutput);
				variants.add(BlockModelGenerators.plainModel(model));
			}
			radii.select(r, BlockModelGenerators.variants(variants.toArray(Variant[]::new)));
		}
		gen.blockStateOutput.accept(MultiVariantGenerator.dispatch(block).with(radii).with(PARENT_ROTATIONS));
		ModelTemplate item = new ModelTemplate(Optional.of(PolishForests.id(TrunkTemplates.ITEM_TEMPLATE)), Optional.of("_inventory"),
				BARK, TextureSlot.END);
		gen.registerSimpleItemModel(block, item.create(block, textures(barks.get(0), end), gen.modelOutput));
	}

	/** The rotation of a lying flare template (drawn with the parent to the north) to a horizontal parent. */
	private static VariantMutator flareRotation(Direction parent) {
		return switch (parent) {
			case EAST -> BlockModelGenerators.Y_ROT_90;
			case SOUTH -> BlockModelGenerators.Y_ROT_180;
			case WEST -> BlockModelGenerators.Y_ROT_270;
			default -> BlockModelGenerators.NOP;
		};
	}

	/** The rotation of the trunk template (the arm down) to a parent, as {@link #PARENT_ROTATIONS}. */
	private static VariantMutator rotation(Direction parent) {
		return switch (parent) {
			case DOWN -> BlockModelGenerators.NOP;
			case UP -> BlockModelGenerators.X_ROT_180;
			case NORTH -> BlockModelGenerators.X_ROT_270;
			case SOUTH -> BlockModelGenerators.X_ROT_270.then(BlockModelGenerators.Y_ROT_180);
			case WEST -> BlockModelGenerators.X_ROT_270.then(BlockModelGenerators.Y_ROT_270);
			case EAST -> BlockModelGenerators.X_ROT_270.then(BlockModelGenerators.Y_ROT_90);
		};
	}

	/**
	 * A root flare (step I1): the models of the species' trunk (same bark variants and end) for a vertical parent, the
	 * lying templates {@link TrunkTemplates#flareTemplate} for a horizontal one; each state takes its rotation directly (the
	 * model depends on the parent too, so the parent cannot be a second stage of the dispatch).
	 */
	private static void flare(BlockModelGenerators gen, pl.polishforests.block.RootFlare f) {
		Block block = ModBlocks.flare(f);
		List<Identifier> barks = barks(f.trunk());
		Identifier end = end(f.trunk());
		PropertyDispatch.C2<MultiVariant, Integer, Direction> states = PropertyDispatch.initial(TrunkBlock.RADIUS, TrunkBlock.PARENT);
		for (int r = TrunkBlock.MIN_RADIUS; r <= TrunkBlock.MAX_RADIUS; r++) {
			List<Variant> standing = new ArrayList<>();
			List<Variant> lying = new ArrayList<>();
			for (int lie = 0; lie <= 1; lie++) {
				ModelTemplate template = new ModelTemplate(Optional.of(PolishForests.id(lie == 1 ? TrunkTemplates.flareTemplate(f.flat(), r)
						: TrunkTemplates.template(r))), Optional.empty(), BARK, TextureSlot.END);
				for (int k = 0; k < barks.size(); k++) {
					String suffix = (lie == 1 ? "_lying" : "") + "_r" + r + (k == 0 ? "" : "_" + (k + 1));
					Identifier model = template.createWithSuffix(block, suffix, textures(barks.get(k), end), gen.modelOutput);
					(lie == 1 ? lying : standing).add(BlockModelGenerators.plainModel(model));
				}
			}
			for (Direction d : Direction.values()) {
				List<Variant> v = d.getAxis() == Direction.Axis.Y ? standing : lying;
				states.select(r, d, BlockModelGenerators.variants(v.toArray(Variant[]::new)).with(d.getAxis() == Direction.Axis.Y
						? rotation(d) : flareRotation(d)));
			}
		}
		gen.blockStateOutput.accept(MultiVariantGenerator.dispatch(block).with(states));
		ModelTemplate item = new ModelTemplate(Optional.of(PolishForests.id(TrunkTemplates.ITEM_TEMPLATE)), Optional.of("_inventory"),
				BARK, TextureSlot.END);
		gen.registerSimpleItemModel(block, item.create(block, textures(barks.get(0), end), gen.modelOutput));
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
