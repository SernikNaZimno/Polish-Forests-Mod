package pl.polishforests.gametest.probe;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperUnbakedRootBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.block.dispatch.SingleVariant;
import net.minecraft.client.renderer.block.dispatch.Variant;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * A block model wrapped by the model loading plugin (FRAPI, step V3): while {@code useAlternative} is false it draws the
 * model of the block state, otherwise the alternative model, or nothing when there is none (an "empty" plant). The phase
 * is read when a section is meshed, so a change of the phase needs a rebuild of the meshes ({@link ProbeSeason}).
 *
 * <p>The Fast graphics mode (review round 1 of V3). Every renderer asks {@code ModelBlockRenderer.forceOpaque} whether a
 * leaves block goes to the solid layer; Sodium then moves every quad of the block there, also the quads emitted here, so
 * skipping the FRAPI transform alone is not enough (plain Sodium drew the twigs as opaque cubes). A block registered with
 * {@link #keepCutOutInFastMode} answers false there while its alternative is shown ({@code ModelBlockRendererMixin}), so the
 * twigs stay cut out in vanilla, Sodium and Nvidium. In Fast mode leaves also skip their faces towards other leaves
 * ({@code LeavesBlock.skipRendering}), which would leave only the outer shell of a bare crown; with {@code facesToLeaves}
 * the alternative keeps those faces, as in the Fancy mode.
 */
public final class SeasonalModel extends WrapperBlockStateModel {
	/** Blocks whose cut-out alternative must not go to the solid layer in Fast mode, with their phase (set on client init). */
	private static volatile Map<Block, BooleanSupplier> cutOutAlternatives = Map.of();

	private final @Nullable BlockStateModel alternative;
	private final BooleanSupplier useAlternative;
	private final boolean facesToLeaves;

	SeasonalModel(BlockStateModel wrapped, @Nullable BlockStateModel alternative, BooleanSupplier useAlternative,
			boolean facesToLeaves) {
		super(wrapped);
		this.alternative = alternative;
		this.useAlternative = useAlternative;
		this.facesToLeaves = facesToLeaves;
	}

	/** Keeps the alternative of a block cut out in Fast mode while {@code phase} shows it (client init only). */
	static synchronized void keepCutOutInFastMode(Block block, BooleanSupplier phase) {
		Map<Block, BooleanSupplier> map = new IdentityHashMap<>(cutOutAlternatives);
		map.put(block, phase);
		cutOutAlternatives = map;
	}

	/** True while the block shows a cut-out alternative that must stay cut out in Fast mode (any meshing thread). */
	public static boolean cutoutInFastMode(BlockState state) {
		BooleanSupplier phase = cutOutAlternatives.get(state.getBlock());
		return phase != null && phase.getAsBoolean();
	}

	@Override
	public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random,
			Predicate<Direction> cullTest) {
		if (!useAlternative.getAsBoolean()) {
			super.emitQuads(emitter, level, pos, state, random, cullTest);
			return;
		}
		if (alternative == null) {
			return;
		}
		List<BlockStateModelPart> parts = new ArrayList<>();
		alternative.collectParts(random, parts);
		if (!facesToLeaves) {
			for (BlockStateModelPart part : parts) {
				part.emitQuads(emitter, cullTest);
			}
			return;
		}
		// The faces towards other leaves: the part must emit them (the cull test), and the renderer must not cull them again
		// by their cull face (vanilla, Indigo and Sodium test the cull face of every emitted quad), so their cull face goes.
		boolean[] towardsLeaves = new boolean[6];
		for (Direction d : Direction.values()) {
			towardsLeaves[d.ordinal()] = level.getBlockState(pos.relative(d)).getBlock() instanceof LeavesBlock;
		}
		emitter.pushTransform(q -> {
			Direction face = q.cullFace();
			if (face != null && towardsLeaves[face.ordinal()]) {
				q.cullFace(null);
			}
			return true;
		});
		Predicate<Direction> cull = d -> cullTest.test(d) && !towardsLeaves[d.ordinal()];
		for (BlockStateModelPart part : parts) {
			part.emitQuads(emitter, cull);
		}
		emitter.popTransform();
	}

	/** The particles of breaking and of landing come from the shown model (twigs in the leafless phase). */
	@Override
	public Material.Baked particleMaterial() {
		return alternative != null && useAlternative.getAsBoolean() ? alternative.particleMaterial() : super.particleMaterial();
	}

	@Override
	public Material.Baked particleMaterial(BlockAndTintGetter level, BlockPos pos, BlockState state) {
		return alternative != null && useAlternative.getAsBoolean() ? alternative.particleMaterial(level, pos, state)
				: super.particleMaterial(level, pos, state);
	}

	/** Vanilla paths without FRAPI (moving blocks, block entities, breaking overlay) see the same phase. */
	@Override
	public void collectParts(RandomSource random, List<BlockStateModelPart> output) {
		if (!useAlternative.getAsBoolean()) {
			super.collectParts(random, output);
		} else if (alternative != null) {
			alternative.collectParts(random, output);
		}
	}

	/** The geometry depends on the phase of the year, so it must not be cached by a key of the wrapped model. */
	@Override
	public @Nullable Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state, RandomSource random) {
		return null;
	}

	/**
	 * The unbaked side: wraps the unbaked model of the block state when it is loaded ({@code modifyBlockModelOnLoad}), so the
	 * alternative model becomes a dependency and is loaded and baked with it.
	 */
	static final class Root extends WrapperUnbakedRootBlockStateModel {
		private final BlockStateModel.@Nullable Unbaked alternative;
		private final BooleanSupplier useAlternative;
		private final boolean facesToLeaves;

		Root(BlockStateModel.UnbakedRoot wrapped, @Nullable Identifier alternative, BooleanSupplier useAlternative,
				boolean facesToLeaves) {
			super(wrapped);
			this.alternative = alternative == null ? null : new SingleVariant.Unbaked(new Variant(alternative));
			this.useAlternative = useAlternative;
			this.facesToLeaves = facesToLeaves;
		}

		@Override
		public void resolveDependencies(ResolvableModel.Resolver resolver) {
			super.resolveDependencies(resolver);
			if (alternative != null) {
				alternative.resolveDependencies(resolver);
			}
		}

		@Override
		public BlockStateModel bake(BlockState state, ModelBaker baker) {
			return new SeasonalModel(super.bake(state, baker), alternative == null ? null : alternative.bake(baker), useAlternative,
					facesToLeaves);
		}
	}
}
