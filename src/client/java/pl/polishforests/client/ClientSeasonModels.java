package pl.polishforests.client;

import com.mojang.math.Quadrant;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.renderer.block.dispatch.Variant;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FlowerBedBlock;
import net.minecraft.world.level.block.state.BlockState;
import pl.polishforests.PolishForests;
import pl.polishforests.block.GroundPlant;
import pl.polishforests.block.MarcescentLeavesBlock;
import pl.polishforests.block.ModBlocks;
import pl.polishforests.block.SpeciesLeavesBlock;
import pl.polishforests.block.TreeFoliage;
import pl.polishforests.season.FoliageCurve;
import pl.polishforests.season.GroundPhases;
import pl.polishforests.worldgen.habitat.Species;

/**
 * The seasonal looks of the blocks of the vertical slice on the client (step V13 of docs/04-pionowy-wycinek.md, decisions
 * PW-8 (a) and T-D5): the model loading plugin that wraps their models ({@link SeasonalModel}) and the phases they show at
 * the color step of {@link ClientSeasonPhase} (the season itself, also when Serene Seasons does not change the foliage
 * colors: the switch is about colors, decision V13-c).
 * <ul>
 * <li>the leaves of the broadleaf species show bare twigs in their leafless window ({@link FoliageCurve#leafless}: by
 * species and the tree's phase; the marcescent leaves of young oaks, hornbeams and beeches keep their dry brown leaves):
 * the model {@code polishforests:block/<species>_twigs}, in {@value #TWIG_VARIANTS} turns drawn per position, tinted by
 * the twig color of the species (tint index 1);
 * <li>the wood anemone shows its leaves without flowers after the flowering, and nothing while it is withdrawn
 * underground; the liverleaf its leaves outside the flowering ({@link GroundPhases#look}): the vanilla flower bed models
 * with the textures {@code <plant>_leaves}, as many and turned as the bed;
 * <li>the bilberry shows its flowers ({@code polishforests:block/bilberry_flowers} over the shrub) in May.
 * </ul>
 * In the Polska world the vanilla oak, dark oak, jungle and birch leaves (the shrubs of the slice and the M2 placeholder
 * trees) show the twigs of the oak, hornbeam or birch in their leafless window too (V13-i); elsewhere they are unchanged.
 * The conifers, the bracken (only its color changes, {@code ClientGroundPlants}) and the litter have no seasonal model.
 */
public final class ClientSeasonModels {
	/** Turns of the twig cube (x, y), drawn per position. */
	private static final Quadrant[][] TWIG_TURNS = {{Quadrant.R0, Quadrant.R0}, {Quadrant.R0, Quadrant.R90}, {Quadrant.R0, Quadrant.R180},
			{Quadrant.R0, Quadrant.R270}, {Quadrant.R90, Quadrant.R0}, {Quadrant.R90, Quadrant.R90}};
	static final int TWIG_VARIANTS = 6;

	private ClientSeasonModels() {
	}

	/** The model id of the bare twigs of a broadleaf species (datagen writes it). */
	public static Identifier twigs(TreeFoliage f) {
		return PolishForests.id("block/" + f.species().id() + "_twigs");
	}

	/** The model id of the leaves (no flowers) of a flower bed of {@code k} plants (1–4; datagen writes them). */
	public static Identifier bedLeaves(GroundPlant plant, int k) {
		return PolishForests.id("block/" + plant.id() + "_leaves_" + k);
	}

	/** The model id of the bilberry flowers (datagen writes it). */
	public static Identifier flowers(GroundPlant plant) {
		return PolishForests.id("block/" + plant.id() + "_flowers");
	}

	/** The color step the phases use: the season's (before the first client tick, the client level's). */
	private static int step() {
		return ClientSeasonPhase.seasonStep();
	}

	/**
	 * The vanilla leaves that show bare twigs in the Polska world (V13-i: the shrubs of the slice, hazel and rowan on oak
	 * leaves, alder buckthorn on jungle leaves, and the M2 placeholder trees), by the species whose curve and twigs they
	 * follow, as their falling leaves ({@link ClientLeafFall}).
	 */
	private static final java.util.Map<Block, TreeFoliage> VANILLA_TWIGS = java.util.Map.of(Blocks.OAK_LEAVES, foliage(Species.OAK),
			Blocks.DARK_OAK_LEAVES, foliage(Species.OAK), Blocks.JUNGLE_LEAVES, foliage(Species.HORNBEAM), Blocks.BIRCH_LEAVES,
			foliage(Species.BIRCH));

	private static TreeFoliage foliage(Species species) {
		for (TreeFoliage f : TreeFoliage.values()) {
			if (f.species() == species && !f.conifer()) {
				return f;
			}
		}
		throw new IllegalStateException("No broadleaf foliage of " + species);
	}

	/** Whether a vanilla leaf block at a position shows bare twigs now (the Polska world only, V13-i). */
	public static boolean vanillaLeafless(BlockState state, BlockPos pos) {
		TreeFoliage f = VANILLA_TWIGS.get(state.getBlock());
		return f != null && ClientSeasonPhase.polishWorld()
				&& FoliageCurve.leafless(f.species(), step(), ClientLeafFall.vanillaPhase(pos), false);
	}

	/** Whether a block state shows its bare twigs now (leaves of a broadleaf species in their leafless window). */
	public static boolean leafless(BlockState state) {
		return state.getBlock() instanceof SpeciesLeavesBlock leaves && !leaves.foliage().conifer()
				&& FoliageCurve.leafless(leaves.foliage().species(), step(), state.getValue(SpeciesLeavesBlock.PHASE),
						MarcescentLeavesBlock.marcescent(state));
	}

	/**
	 * Whether a block must stay cut out in the Fast graphics mode ({@code ModelBlockRendererMixin}): the bare twigs, which
	 * the Fast mode would otherwise move to the solid layer as opaque cubes (V3-b).
	 */
	public static boolean cutOutInFastMode(BlockState state) {
		if (leafless(state)) {
			return true;
		}
		// Vanilla leaves (no position here): cut out in the Polska world while any of their phases is leafless.
		TreeFoliage f = VANILLA_TWIGS.get(state.getBlock());
		if (f == null || !ClientSeasonPhase.polishWorld()) {
			return false;
		}
		int s = step();
		for (int phase = 0; phase < 4; phase++) {
			if (FoliageCurve.leafless(f.species(), s, phase, false)) {
				return true;
			}
		}
		return false;
	}

	static void register() {
		ModelLoadingPlugin.register(ctx -> ctx.modifyBlockModelOnLoad().register((model, context) -> {
			BlockState state = context.state();
			Block block = state.getBlock();
			TreeFoliage vanilla = VANILLA_TWIGS.get(block);
			if (vanilla != null) {
				List<Variant> twigs = twigVariants(vanilla);
				return new SeasonalModel.Root(model, (s, pos) -> vanillaLeafless(s, pos) ? 1 : SeasonalModel.Look.BASE,
						List.of(new SeasonalModel.Alternative(s -> twigs, true, false, true)), twigs);
			}
			if (block instanceof SpeciesLeavesBlock leaves && !leaves.foliage().conifer()) {
				List<Variant> twigs = twigVariants(leaves.foliage());
				return new SeasonalModel.Root(model, (s, pos) -> leafless(s) ? 1 : SeasonalModel.Look.BASE,
						List.of(new SeasonalModel.Alternative(s -> twigs, true, false, true)), twigs);
			}
			for (GroundPlant g : GroundPlant.values()) {
				if (block != ModBlocks.ground(g)) {
					continue;
				}
				if (g.kind() == GroundPlant.Kind.SEASONAL) {
					List<Variant> deps = new ArrayList<>();
					for (int k = 1; k <= 4; k++) {
						deps.add(new Variant(bedLeaves(g, k)));
					}
					return new SeasonalModel.Root(model, (s, pos) -> switch (GroundPhases.look(g, step(), pos.getX(), pos.getZ())) {
						case NONE -> SeasonalModel.Look.NOTHING;
						case LEAVES -> 1;
						default -> SeasonalModel.Look.BASE;
					}, List.of(new SeasonalModel.Alternative(s -> bedLeaves(g, s), false, false, false)), deps);
				}
				if (g.kind() == GroundPlant.Kind.BERRY_SHRUB) {
					List<Variant> flowers = List.of(new Variant(flowers(g)));
					return new SeasonalModel.Root(model, (s, pos) -> GroundPhases.look(g, step(), pos.getX(), pos.getZ())
							== GroundPhases.Look.FLOWERING ? 1 : SeasonalModel.Look.BASE,
							List.of(new SeasonalModel.Alternative(s -> flowers, false, true, false)), flowers);
				}
			}
			return model;
		}));
	}

	/** The turned twig cubes of a species. */
	private static List<Variant> twigVariants(TreeFoliage f) {
		List<Variant> out = new ArrayList<>();
		for (Quadrant[] t : TWIG_TURNS) {
			out.add(new Variant(twigs(f)).withXRot(t[0]).withYRot(t[1]));
		}
		return List.copyOf(out);
	}

	/** The leaves models of a flower bed state: models 1 … amount, turned as the bed faces (as vanilla's segmented beds). */
	private static List<Variant> bedLeaves(GroundPlant g, BlockState s) {
		int amount = s.getValue(FlowerBedBlock.AMOUNT);
		Quadrant turn = switch (s.getValue(FlowerBedBlock.FACING)) {
			case EAST -> Quadrant.R90;
			case SOUTH -> Quadrant.R180;
			case WEST -> Quadrant.R270;
			default -> Quadrant.R0;
		};
		List<Variant> out = new ArrayList<>();
		for (int k = 1; k <= amount; k++) {
			out.add(new Variant(bedLeaves(g, k)).withYRot(turn));
		}
		return out;
	}
}
