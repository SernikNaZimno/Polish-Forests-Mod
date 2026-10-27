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
 * twigs). The bare twigs ({@code facesToLeaves}) draw no face towards other bare twigs, so a bare crown is one screen of
 * twigs over its branches with the sky behind it (review round 1 of V13: the stacked faces of a crown 4-6 blocks deep let
 * almost no sky through; the faces into small hollows of the crown are dropped too, {@link #CROWN_GAP}), and keep their faces towards leaves in leaf (in the Fast graphics mode leaves skip those faces,
 * {@code LeavesBlock.skipRendering}, and the renderers cull a quad again by its cull face, so the faces pass the cull
 * test and lose their cull face). A leaf block in leaf next to bare twigs keeps its face towards them in the Fast mode too
 * ({@link #emitLeaves}; every leaf block of vanilla and of the mod is wrapped for it). The Fast mode would also move the
 * twigs to the solid layer, which the mixin {@code ModelBlockRendererMixin} prevents
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
	/** The widest gap of air inside a bare crown whose faces are dropped (blocks). */
	static final int CROWN_GAP = 2;
	/** Whether the block is a leaf block (the faces towards bare twigs in the Fast mode, {@link #emitLeaves}). */
	private final boolean leaves;

	private SeasonalModel(BlockStateModel wrapped, BlockState state, Look look, List<Alternative> alternatives,
			List<List<BlockStateModel>> baked) {
		super(wrapped);
		this.state = state;
		this.look = look;
		this.alternatives = alternatives;
		this.baked = baked;
		this.leaves = state.getBlock() instanceof LeavesBlock;
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
			if (leaves) {
				emitLeaves(emitter, level, pos, s, random, cullTest);
			} else {
				super.emitQuads(emitter, level, pos, s, random, cullTest);
			}
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
		// The bare twigs: no face towards other bare twigs (a bare crown is one screen of twigs over its branches, not a
		// stack of them, review round 1 of V13), the faces towards leaves in leaf kept (also in the Fast mode).
		int keep = 0;
		int drop = 0;
		for (Direction d : Direction.values()) {
			BlockPos at = pos.relative(d);
			BlockState n = level.getBlockState(at);
			if (n.getBlock() instanceof LeavesBlock) {
				if (ClientSeasonModels.showsTwigs(n, at)) {
					drop |= 1 << d.ordinal();
				} else {
					keep |= 1 << d.ordinal();
				}
			} else if (n.isAir() && gapInCrown(level, pos, d)) {
				drop |= 1 << d.ordinal();
			}
		}
		int k = keep;
		int x = drop;
		// The quads without a cull face are asked with a null direction: never culled here.
		Predicate<Direction> cull = d -> d == null ? cullTest.test(null)
				: (x & 1 << d.ordinal()) != 0 || (k & 1 << d.ordinal()) == 0 && cullTest.test(d);
		emitKeeping(emitter, k, () -> {
			for (BlockStateModelPart part : parts) {
				part.emitQuads(emitter, cull);
			}
		});
	}

	/**
	 * Whether the air next to bare twigs in direction {@code d} is a gap of at most {@value #CROWN_GAP} blocks inside a bare
	 * crown (bare twigs beyond it): its faces are dropped as those between two twig blocks, so the small hollows of a
	 * crown add no screens of twigs either (review round 1 of V13: a crown is one screen over its branches).
	 */
	private static boolean gapInCrown(BlockAndTintGetter level, BlockPos pos, Direction d) {
		for (int k = 2; k <= CROWN_GAP + 1; k++) {
			BlockPos at = pos.relative(d, k);
			BlockState n = level.getBlockState(at);
			if (n.getBlock() instanceof LeavesBlock) {
				return ClientSeasonModels.showsTwigs(n, at);
			}
			if (!n.isAir()) {
				return false;
			}
		}
		return false;
	}

	/**
	 * The model of a leaf block in leaf. In the Fast graphics mode leaves skip their faces towards every other leaf block
	 * ({@code LeavesBlock.skipRendering}); next to bare twigs, which are cut out, that would leave a hole through the crown,
	 * so the faces towards bare twigs are kept (review round 1 of V13). Vanilla leaves that the Fast mode keeps cut out for
	 * the twigs of some of their phases ({@link ClientSeasonModels#cutOutInFastMode}) keep all their faces towards other
	 * leaves, as in the Fancy mode, so a leafy crown is not a hollow shell. In the Fancy mode nothing changes.
	 */
	private void emitLeaves(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState s, RandomSource random,
			Predicate<Direction> cullTest) {
		if (!ClientSeasonModels.fastLeaves()) {
			super.emitQuads(emitter, level, pos, s, random, cullTest);
			return;
		}
		boolean all = ClientSeasonModels.cutOutInFastMode(s);
		int keep = 0;
		for (Direction d : Direction.values()) {
			BlockPos at = pos.relative(d);
			BlockState n = level.getBlockState(at);
			if (n.getBlock() instanceof LeavesBlock && (all || ClientSeasonModels.showsTwigs(n, at)) && cullTest.test(d)) {
				keep |= 1 << d.ordinal();
			}
		}
		if (keep == 0) {
			super.emitQuads(emitter, level, pos, s, random, cullTest);
			return;
		}
		int k = keep;
		emitKeeping(emitter, k, () -> super.emitQuads(emitter, level, pos, s, random,
				d -> cullTest.test(d) && (d == null || (k & 1 << d.ordinal()) == 0)));
	}

	/**
	 * Emits with the faces of the directions in {@code keep} (bits by {@code Direction.ordinal}) without their cull face: the
	 * renderers cull a quad again by its cull face, so a kept face must lose it.
	 */
	private static void emitKeeping(QuadEmitter emitter, int keep, Runnable emit) {
		if (keep == 0) {
			emit.run();
			return;
		}
		emitter.pushTransform(q -> {
			Direction face = q.cullFace();
			if (face != null && (keep & 1 << face.ordinal()) != 0) {
				q.cullFace(null);
			}
			return true;
		});
		emit.run();
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
