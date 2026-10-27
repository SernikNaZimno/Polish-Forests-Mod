package pl.polishforests.client;

import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ColorResolverRegistry;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.color.block.BlockTintSources;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.GrassColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Property;
import pl.polishforests.block.GroundPlant;
import pl.polishforests.block.ModBlocks;
import pl.polishforests.season.GroundPhases;

/**
 * Tints of the herb layer (step V12), as vanilla's plants: the dwarf shrub as the bush (grass color, tint index 0 of the
 * cross; the berries and the moss carpet have none), the flower beds as vanilla's wildflowers (the flower not tinted,
 * the stem with the grass color, tint index 1), the tall fern as the large fern; the litter is not tinted. Step V13: the
 * bilberry and the bracken follow their season ({@link GroundPhases#bilberryColor}: fresh green in spring, red in
 * October, dull green-brown twigs in winter; {@link GroundPhases#brackenColor}: brown from mid-autumn until the new
 * fronds) over the raw grass color of the biome ({@link #RAW_GRASS}, the own resolver, so nothing is seasoned twice by
 * Serene Seasons, as the leaves: decision B6 (a)); with the foliage colors of Serene Seasons switched off they keep the
 * summer.
 */
public final class ClientGroundPlants {
	/** The raw grass color of the biome (with the biome's own modifier), without a seasonal overlay. */
	public static final ColorResolver RAW_GRASS = (biome, x, z) -> biome.getGrassColor(x, z);

	private ClientGroundPlants() {
	}

	static void register() {
		ColorResolverRegistry.register(RAW_GRASS);
		for (GroundPlant g : GroundPlant.values()) {
			switch (g.kind()) {
				case BERRY_SHRUB -> BlockColorRegistry.register(List.of(new SeasonalGrass(false)), ModBlocks.ground(g));
				case SEASONAL -> BlockColorRegistry.register(List.of(BlockTintSources.constant(-1), BlockTintSources.grass()),
						ModBlocks.ground(g));
				case TALL -> BlockColorRegistry.register(List.of(new SeasonalGrass(true)), ModBlocks.ground(g));
				case COVER -> {
				}
			}
		}
		// Review round 1 of V13: the leaves model of the vanilla lily of the valley in Polska (tint index 0; the vanilla
		// flowering model has no tint index, so it is unchanged).
		BlockColorRegistry.register(List.of(new LilyTint()), Blocks.LILY_OF_THE_VALLEY);
	}

	/** The seasonal tint of the leaves of the lily of the valley ({@link GroundPhases#lilyColor}). */
	static final class LilyTint implements BlockTintSource {
		@Override
		public int color(BlockState state) {
			return 0xFF000000 | GroundPhases.lilyColor(ClientSeasonPhase.curveStep(), 0, 0, GrassColor.getDefaultColor());
		}

		@Override
		public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
			return 0xFF000000 | GroundPhases.lilyColor(ClientSeasonPhase.curveStep(), pos.getX(), pos.getZ(),
					level.getBlockTint(pos, RAW_GRASS));
		}

		@Override
		public Set<Property<?>> relevantProperties() {
			return Set.of();
		}
	}

	/** The seasonal tint of the bilberry ({@code bracken} false) or of the bracken (both halves from the lower block). */
	static final class SeasonalGrass implements BlockTintSource {
		private final boolean bracken;

		SeasonalGrass(boolean bracken) {
			this.bracken = bracken;
		}

		private int color(int raw, BlockPos pos) {
			int step = ClientSeasonPhase.curveStep();
			return 0xFF000000 | (bracken ? GroundPhases.brackenColor(step, pos.getX(), pos.getZ(), raw)
					: GroundPhases.bilberryColor(step, pos.getX(), pos.getZ(), raw));
		}

		@Override
		public int color(BlockState state) {
			return color(GrassColor.getDefaultColor(), BlockPos.ZERO);
		}

		@Override
		public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
			BlockPos at = bracken && state.hasProperty(DoublePlantBlock.HALF) && state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER
					? pos.below() : pos;
			return color(level.getBlockTint(at, RAW_GRASS), at);
		}

		@Override
		public Set<Property<?>> relevantProperties() {
			return bracken ? Set.of(DoublePlantBlock.HALF) : Set.of();
		}
	}
}
