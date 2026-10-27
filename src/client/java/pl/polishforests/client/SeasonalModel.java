package pl.polishforests.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
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
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * A block model wrapped by the model loading plugin of the seasons (step V13 of docs/04-pionowy-wycinek.md, decisions
 * PW-8 (a) and T-D5; the trial of step V3, {@code gametest/probe/SeasonalModel}, moved here): what it draws depends on
 * the phase of the year on the client ({@link Look}): the model of the block state, an alternative, the model with an
 * alternative over it, or nothing. The phase is read when a section is meshed, so a change of the phase needs a rebuild
 * of the meshes (the wave of {@link ClientSeasonPhase}).
 *
 * <p>An alternative is a list of model parts built from the block state ({@link Alternative}): all of them (the leaves
 * of a flower bed of 1–4 plants, turned as the bed) or one drawn at random per position (the turned variants of the bare
 * twigs). The bare twigs keep their faces towards other leaves ({@code facesToLeaves}: in the Fast graphics mode leaves
 * skip those faces, {@code LeavesBlock.skipRendering}, and the renderers cull a quad again by its cull face, so the faces
 * pass the cull test and lose their cull face), so a bare crown is as dense in the Fast mode as in the Fancy mode; the
 * Fast mode would also move the twigs to the solid layer, which the mixin {@code ModelBlockRendererMixin} prevents
 * ({@link ClientSeasonModels#cutOutInFastMode}). The particles of breaking and landing come from the shown model.
 * {@code createGeometryKey} is null (V3-c): the geometry depends on the season.
 */
public final class SeasonalModel extends WrapperBlockStateModel {
	/** What a seasonal block draws now: {@link #NOTHING}, {@link #BASE} or the index (1 …) of an alternative. */
	@FunctionalInterface
	public interface Look {
		int NOTHING = -1;
		int BASE = 0;

		int look(BlockState state, BlockPos pos);
	}

	/**
	 * An alternative of a block state: its parts ({@code parts}, from the state), all of them or one at random
	 * ({@code oneOf}), over the model of the state or instead of it ({@code withBase}), with the faces towards other
	 * leaves kept ({@code facesToLeaves}).
	 */
	public record Alternative(Function<BlockState, List<Variant>> parts, boolean oneOf, boolean withBase, boolean facesToLeaves) {
	}

	private final BlockState state;
	private final Look look;
	private final List<Alternative> alternatives;
	private final List<List<BlockStateModel>> baked;

	private SeasonalModel(BlockStateModel wrapped, BlockState state, Look look, List<Alternative> alternatives,
			List<List<BlockStateModel>> baked) {
		super(wrapped);
		this.state = state;
		this.look = look;
		this.alternatives = alternatives;
		this.baked = baked;
	}

	private int lookAt(BlockState s, @Nullable BlockPos pos) {
		int l = look.look(s, pos == null ? BlockPos.ZERO : pos);
		return l > alternatives.size() ? Look.BASE : l;
	}

	@Override
	public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState s, RandomSource random,
			Predicate<Direction> cullTest) {
		int l = lookAt(s, pos);
		if (l == Look.BASE) {
			super.emitQuads(emitter, level, pos, s, random, cullTest);
			return;
		}
		if (l == Look.NOTHING) {
			return;
		}
		Alternative alt = alternatives.get(l - 1);
		if (alt.withBase()) {
			super.emitQuads(emitter, level, pos, s, random, cullTest);
		}
		List<BlockStateModelPart> parts = parts(l, random);
		if (!alt.facesToLeaves()) {
			for (BlockStateModelPart part : parts) {
				part.emitQuads(emitter, cullTest);
			}
			return;
		}
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

	/** The parts of alternative {@code l} (1 …): all of them, or one by the position's random. */
	private List<BlockStateModelPart> parts(int l, RandomSource random) {
		List<BlockStateModel> models = baked.get(l - 1);
		List<BlockStateModelPart> parts = new ArrayList<>();
		if (models.isEmpty()) {
			return parts;
		}
		if (alternatives.get(l - 1).oneOf()) {
			models.get(models.size() == 1 ? 0 : random.nextInt(models.size())).collectParts(random, parts);
		} else {
			for (BlockStateModel m : models) {
				m.collectParts(random, parts);
			}
		}
		return parts;
	}

	@Override
	public Material.Baked particleMaterial() {
		return shownParticles(lookAt(state, null), super.particleMaterial());
	}

	@Override
	public Material.Baked particleMaterial(BlockAndTintGetter level, BlockPos pos, BlockState s) {
		return shownParticles(lookAt(s, pos), super.particleMaterial(level, pos, s));
	}

	private Material.Baked shownParticles(int l, Material.Baked base) {
		if (l <= Look.BASE || alternatives.get(l - 1).withBase() || baked.get(l - 1).isEmpty()) {
			return base;
		}
		return baked.get(l - 1).getFirst().particleMaterial();
	}

	/** Paths without FRAPI (moving blocks, the breaking overlay) see the same phase. */
	@Override
	public void collectParts(RandomSource random, List<BlockStateModelPart> output) {
		int l = lookAt(state, null);
		if (l == Look.BASE) {
			super.collectParts(random, output);
			return;
		}
		if (l == Look.NOTHING) {
			return;
		}
		if (alternatives.get(l - 1).withBase()) {
			super.collectParts(random, output);
		}
		output.addAll(parts(l, random));
	}

	/** The geometry depends on the phase of the year, so it must not be cached by a key of the wrapped model (V3-c). */
	@Override
	public @Nullable Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState s, RandomSource random) {
		return null;
	}

	/**
	 * The unbaked side: wraps the unbaked model of the block state when it is loaded ({@code modifyBlockModelOnLoad}), so
	 * the models of the alternatives become dependencies and are loaded and baked with it ({@code dependencies}: every
	 * model any state can use).
	 */
	static final class Root extends WrapperUnbakedRootBlockStateModel {
		private final Look look;
		private final List<Alternative> alternatives;
		private final List<Variant> dependencies;
		/** The baked alternative models of this block, shared by its states (the twigs of 112 leaf states). */
		private final java.util.Map<Variant, BlockStateModel> bakedVariants = new java.util.concurrent.ConcurrentHashMap<>();

		Root(BlockStateModel.UnbakedRoot wrapped, Look look, List<Alternative> alternatives, List<Variant> dependencies) {
			super(wrapped);
			this.look = look;
			this.alternatives = List.copyOf(alternatives);
			this.dependencies = List.copyOf(dependencies);
		}

		@Override
		public void resolveDependencies(ResolvableModel.Resolver resolver) {
			super.resolveDependencies(resolver);
			for (Variant v : dependencies) {
				v.resolveDependencies(resolver);
			}
		}

		@Override
		public BlockStateModel bake(BlockState state, ModelBaker baker) {
			List<List<BlockStateModel>> baked = new ArrayList<>();
			for (Alternative alt : alternatives) {
				List<BlockStateModel> models = new ArrayList<>();
				for (Variant v : alt.parts().apply(state)) {
					models.add(bakedVariants.computeIfAbsent(v, k -> new SingleVariant.Unbaked(k).bake(baker)));
				}
				baked.add(List.copyOf(models));
			}
			return new SeasonalModel(super.bake(state, baker), state, look, alternatives, List.copyOf(baked));
		}
	}
}
