package pl.polishforests.command;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntPredicate;
import java.util.stream.Collectors;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;
import pl.polishforests.worldgen.habitat.Habitat;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.vanilla.VanillaForestWorld;
import pl.polishforests.worldgen.vanilla.VanillaForests;
import pl.polishforests.worldgen.vanilla.VanillaForestsBiomeSource;

/**
 * Step W1: {@code /polishforests find} in the world type "Default + Polish Forests". A habitat biome is searched by the
 * surface biome of the world's biome source (the vanilla climate with the forests replaced, {@link VanillaForestWorld#surfaceCode})
 * on the lattice of the gameplay scale ({@link HabitatTargets#widePhases}); of the other targets only those that are a
 * vanilla biome there (the sea, a river, a beach); the zones and the landscape targets exist only in a "Poland" world.
 */
final class VanillaTargets {
	private VanillaTargets() {
	}

	/** The forests of a level of that world type, or null. */
	static @Nullable VanillaForestWorld of(ServerLevel level) {
		if (level.getChunkSource().getGenerator().getBiomeSource() instanceof VanillaForestsBiomeSource source) {
			VanillaForestWorld world = source.world();
			if (!world.bound()) {
				world.bind(level.getChunkSource().randomState());
			}
			return world;
		}
		return null;
	}

	/** Cell of the lattice (blocks), as {@link HabitatTargets#cell} in the gameplay scale. */
	static final double CELL = 384;
	/** Phases of the lattice: those of the gameplay scale, to 150 km. */
	static final List<GridSearch.Phase> PHASES = List.of(new GridSearch.Phase(8, 1_000), new GridSearch.Phase(24, 8_000),
			new GridSearch.Phase(64, 40_000), new GridSearch.Phase(192, 150_000));

	/** Search radius (blocks), for the "not found" message. */
	static double radius() {
		return PHASES.getLast().radius();
	}

	/** The nearest column from (ox, oz) whose surface code passes the test, {x, z}, or null. */
	static double @Nullable [] locate(VanillaForestWorld world, IntPredicate test, double ox, double oz, BooleanSupplier cancelled) {
		return GridSearch.nearest(CELL, PHASES, 0,
				p -> test.test(world.surfaceCode((int) Math.floor(p[0]), (int) Math.floor(p[1]))) ? p : null, ox, oz, cancelled);
	}

	/** The test of a habitat biome, or null when the biome does not grow in that world type. */
	static @Nullable IntPredicate biome(HabitatBiome biome) {
		return VanillaForests.present(biome) ? code -> Habitat.biome(code) == biome : null;
	}

	/** The test of a target that is a vanilla biome in that world type (sea, river, beach), or null. */
	static @Nullable IntPredicate target(PolishForestsCommands.Target target) {
		VanillaForests.Foreign kind = switch (target) {
			case SEA -> VanillaForests.Foreign.SEA;
			case RIVER -> VanillaForests.Foreign.RIVER;
			case BEACH -> VanillaForests.Foreign.BEACH;
			default -> null;
		};
		if (kind == null) {
			return null;
		}
		int code = VanillaForests.foreignCode(kind);
		return c -> c == code;
	}

	/** Ids of the habitat biomes of that world type, for the messages. */
	static String presentIds() {
		return VanillaForests.REPLACEMENTS.values().stream().distinct().map(HabitatBiome::id).collect(Collectors.joining(", "));
	}
}
