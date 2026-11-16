package pl.polishforests.command;

import java.util.function.IntPredicate;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.vanilla.VanillaForestWorld;

/**
 * Step FA1: the place of a habitat biome in either world type of the mod, for the census of the fauna game test (the
 * search of {@code /polishforests find biome}: {@link HabitatTargets} in a "Poland" world, {@link VanillaTargets} in
 * "Default + Polish Forests").
 */
public final class FaunaSites {
	private FaunaSites() {
	}

	/** The nearest column of the biome from (x, z), {x, z}, or null (none in reach, or a level of another world type). */
	public static double @Nullable [] locate(ServerLevel level, HabitatBiome biome, double x, double z) {
		HabitatTargets.World poland = HabitatTargets.World.of(level);
		if (poland != null) {
			return HabitatTargets.locate(poland, biome, x, z, () -> false);
		}
		VanillaForestWorld world = VanillaTargets.of(level);
		IntPredicate test = VanillaTargets.biome(biome);
		return world == null || test == null ? null : VanillaTargets.locate(world, test, x, z, () -> false);
	}
}
