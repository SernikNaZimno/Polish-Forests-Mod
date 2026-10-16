package pl.polishforests.gametest.probe;

import java.util.ArrayList;
import java.util.List;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * A block model wrapped by the model loading plugin (FRAPI, step V3): while {@code useAlternative} is false it draws the
 * model of the block state, otherwise the alternative model, or nothing when there is none (an "empty" plant). The phase
 * is read when a section is meshed, so a change of the phase needs a rebuild of the meshes ({@link ProbeSeason}).
 *
 * <p>The alternative (twigs) is emitted part by part, without the transform that the default {@code emitQuads} pushes for
 * a leaves block in the Fast graphics mode ({@code ModelBlockRenderer.forceOpaque}: every quad to the solid layer). Bare
 * twigs therefore stay cut out in the Fast mode too, instead of opaque cubes with a dark background.
 */
public final class SeasonalModel extends WrapperBlockStateModel {
	private final @Nullable BlockStateModel alternative;
	private final BooleanSupplier useAlternative;

	SeasonalModel(BlockStateModel wrapped, @Nullable BlockStateModel alternative, BooleanSupplier useAlternative) {
		super(wrapped);
		this.alternative = alternative;
		this.useAlternative = useAlternative;
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
		for (BlockStateModelPart part : parts) {
			part.emitQuads(emitter, cullTest);
		}
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

		Root(BlockStateModel.UnbakedRoot wrapped, @Nullable Identifier alternative, BooleanSupplier useAlternative) {
			super(wrapped);
			this.alternative = alternative == null ? null : new SingleVariant.Unbaked(new Variant(alternative));
			this.useAlternative = useAlternative;
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
			return new SeasonalModel(super.bake(state, baker), alternative == null ? null : alternative.bake(baker), useAlternative);
		}
	}
}
