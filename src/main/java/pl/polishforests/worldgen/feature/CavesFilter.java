package pl.polishforests.worldgen.feature;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import pl.polishforests.worldgen.cave.CaveBiomes;
import pl.polishforests.worldgen.chunk.PolandChunkGenerator;

/**
 * Placement filter {@code polishforests:caves} (step J2 of docs/04-pionowy-wycinek.md): passes only in a "Poland" world
 * with caves (setting {@code caves}, J1) and cave biomes ({@link CaveBiomes#enabledFromSystem}). It gates the vanilla
 * underground features that the habitat biomes get with the caves (glow lichen, springs, the underground lava lakes,
 * the magma under water, the fossils): the biomes are the same in worlds without caves, whose generation must not
 * change. It never draws a random number, so it does not change the features after it.
 */
public final class CavesFilter implements PlacementFilter {
	public static final CavesFilter INSTANCE = new CavesFilter();
	public static final MapCodec<CavesFilter> CODEC = MapCodec.unit(() -> INSTANCE);

	private CavesFilter() {
	}

	@Override
	public boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos origin) {
		return context.generator() instanceof PolandChunkGenerator poland && poland.settings().caves()
				&& CaveBiomes.enabledFromSystem();
	}

	@Override
	public MapCodec<CavesFilter> codec() {
		return CODEC;
	}
}
