package pl.polishforests.client;

import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperBlockStateModel;
import net.fabricmc.fabric.api.client.model.loading.v1.wrapper.WrapperUnbakedRootBlockStateModel;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import pl.polishforests.block.FloorCulling;
import pl.polishforests.block.ModBlocks;

/**
 * The model of the underground root ({@code tree_root}, step I1) with the culling of step O2 ({@link FloorCulling}): a
 * root block that no camera can see draws nothing ({@link FloorCulling#hiddenRoot}, read from the blocks around it when
 * its section is meshed). Registered for every state of the root block by {@link #register}.
 */
public final class BuriedRootModel extends WrapperBlockStateModel {
	private BuriedRootModel(BlockStateModel wrapped) {
		super(wrapped);
	}

	@Override
	public void emitQuads(QuadEmitter emitter, BlockAndTintGetter level, BlockPos pos, BlockState s, RandomSource random,
			Predicate<@Nullable Direction> cullTest) {
		if (hidden(level, pos)) {
			return;
		}
		super.emitQuads(emitter, level, pos, s, random, cullTest);
	}

	/** The geometry depends on the neighbors, so it must not be cached by a key of the wrapped model. */
	@Override
	public @Nullable Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState s, RandomSource random) {
		return null;
	}

	/** Whether the root block at pos is hidden from every camera ({@link FloorCulling#hiddenRoot}). */
	static boolean hidden(BlockAndTintGetter level, BlockPos pos) {
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		return FloorCulling.hiddenRoot((dx, dy, dz) -> {
			BlockState n = level.getBlockState(at.setWithOffset(pos, dx, dy, dz));
			if (n.getBlock() == ModBlocks.treeRoot()) {
				return FloorCulling.Neighbor.ROOT;
			}
			return n.isSolidRender() ? FloorCulling.Neighbor.SOLID : FloorCulling.Neighbor.OPEN;
		});
	}

	/** Wraps the models of the root block's states when they are loaded. */
	static void register() {
		ModelLoadingPlugin.register(ctx -> ctx.modifyBlockModelOnLoad().register((model, context) ->
				context.state().getBlock() == ModBlocks.treeRoot() ? new Root(model) : model));
	}

	private static final class Root extends WrapperUnbakedRootBlockStateModel {
		Root(BlockStateModel.UnbakedRoot wrapped) {
			super(wrapped);
		}

		@Override
		public BlockStateModel bake(BlockState state, ModelBaker baker) {
			return new BuriedRootModel(super.bake(state, baker));
		}
	}
}
