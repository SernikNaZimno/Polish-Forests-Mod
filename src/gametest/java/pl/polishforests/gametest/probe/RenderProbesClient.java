package pl.polishforests.gametest.probe;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ColorResolverRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.color.block.BlockTintSources;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.FoliageColor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import pl.polishforests.PolishForests;

/**
 * Client side of the render probes (step V3), active only with {@code -Pgametest=probes}: the own color resolver
 * {@link ProbeSeason#RAW_FOLIAGE} in Fabric's {@code ColorResolverRegistry}, the tint of the probe leaves (trial color curve
 * on the raw biome foliage color), the grass tint of the probe plant, the model loading plugin that wraps both probe models
 * ({@link SeasonalModel}), the color step on every client tick and the frame time recorder.
 *
 * <p>FRAPI and model loading signatures used here (Fabric API 0.161.0+26.3, written down in docs/04 "Stan po V3"):
 * {@code ModelLoadingPlugin.register(ModelLoadingPlugin)}, {@code Context.modifyBlockModelOnLoad()} with
 * {@code ModelModifier.OnLoadBlock.modifyModelOnLoad(BlockStateModel.UnbakedRoot, Context)} ({@code Context.state()}),
 * {@code WrapperUnbakedRootBlockStateModel}, {@code WrapperBlockStateModel.emitQuads(QuadEmitter, BlockAndTintGetter,
 * BlockPos, BlockState, RandomSource, Predicate<Direction>)}, {@code BlockStateModelPart.emitQuads(QuadEmitter,
 * Predicate<Direction>)}, {@code ColorResolverRegistry.register(ColorResolver)}, {@code BlockColorRegistry.register(
 * List<BlockTintSource>, Block...)}.
 */
public final class RenderProbesClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		if (!RenderProbes.enabled()) {
			return;
		}
		ColorResolverRegistry.register(ProbeSeason.RAW_FOLIAGE);
		BlockColorRegistry.register(List.of(new ProbeFoliageTint()), RenderProbes.leaves());
		BlockColorRegistry.register(List.of(BlockTintSources.grass()), RenderProbes.plant());
		// Bare twigs stay cut out in the Fast graphics mode in every renderer (review round 1, ModelBlockRendererMixin).
		SeasonalModel.keepCutOutInFastMode(RenderProbes.leaves(), ProbeSeason::leafless);
		ModelLoadingPlugin.register(ctx -> ctx.modifyBlockModelOnLoad().register((model, context) -> {
			BlockState state = context.state();
			if (state.is(RenderProbes.leaves())) {
				return new SeasonalModel.Root(model, RenderProbes.id("block/probe_twigs"), ProbeSeason::leafless, true);
			}
			if (state.is(RenderProbes.plant())) {
				return new SeasonalModel.Root(model, null, ProbeSeason::plantEmpty, false);
			}
			return model;
		}));
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			ProbeSeason.tick(mc);
			FrameRecorder.tick(mc);
		});
		PolishForests.LOG.info("[probes] client: raw foliage resolver, probe tints, model wrapping and color steps registered");
	}

	/**
	 * The raw foliage color of each probe leaf as the section mesher saw it (review round 1 of V3, the biome blend of the
	 * own resolver): recorded only while {@link #recordMeshedRaw} is on, and only from the meshing view of the level
	 * (vanilla's section region or Sodium's level slice with its own color cache), by block position. Written on the
	 * meshing threads.
	 */
	public static final Map<Long, Integer> MESHED_RAW = new ConcurrentHashMap<>();
	public static volatile boolean recordMeshedRaw;

	/** Tint of the probe leaves: the trial curve of {@link ProbeSeason} on the raw foliage color of the biome blend. */
	static final class ProbeFoliageTint implements BlockTintSource {
		@Override
		public int color(BlockState state) {
			return ProbeSeason.color(ProbeSeason.step(), FoliageColor.FOLIAGE_DEFAULT);
		}

		@Override
		public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
			int raw = level.getBlockTint(pos, ProbeSeason.RAW_FOLIAGE);
			if (recordMeshedRaw && level != Minecraft.getInstance().level) {
				MESHED_RAW.put(pos.asLong(), raw);
			}
			return ProbeSeason.color(ProbeSeason.step(), raw);
		}

		@Override
		public Set<Property<?>> relevantProperties() {
			return Set.of();
		}
	}
}
