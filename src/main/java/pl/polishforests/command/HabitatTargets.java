package pl.polishforests.command;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntPredicate;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;
import pl.polishforests.worldgen.chunk.PolandChunkGenerator;
import pl.polishforests.worldgen.habitat.AltitudinalBelts;
import pl.polishforests.worldgen.habitat.Habitat;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.habitat.HabitatClassifier;
import pl.polishforests.worldgen.habitat.Zone;
import pl.polishforests.worldgen.landscape.ColumnSample;
import pl.polishforests.worldgen.landscape.LandscapeModel;
import pl.polishforests.worldgen.landscape.LandscapeScale;

/**
 * Step S9: {@code /polishforests find biome <id>} and {@code find zone <id>}. Each biome and zone has a search plan
 * ({@link Plan}), chosen from where it occurs: most are found on a lattice around the player ({@link GridSearch}) whose
 * spacing grows with the distance, the coastal ones along the coastline first (as {@code find beach}), the narrow
 * belts of watercourses on transects from the bank outwards, and the high mountain ones only where the terrain is high
 * enough (a cheap test on the elevation of the land before the full sample). Rare biomes and zones get a larger radius.
 * The classification is the world's ({@link HabitatClassifier}), so the result is what the biome source and the
 * vegetation use.
 */
public final class HabitatTargets {
	/**
	 * The landscape model and the habitat classifier of a world (the classifier carries the scale and the mode).
	 */
	public record World(LandscapeModel model, HabitatClassifier classifier) {
		/** The world of a Poland level, or null for another generator. */
		public static @Nullable World of(ServerLevel level) {
			if (level.getChunkSource().getGenerator() instanceof PolandChunkGenerator gen) {
				long seed = level.getSeed();
				return new World(gen.model(seed), gen.classifier(seed));
			}
			return null;
		}

		/** Habitat code of the column at (x, z). */
		public int code(double x, double z) {
			ColumnSample s = model.sample(x, z);
			return classifier.classify(s, x, z);
		}

		boolean realistic() {
			return model.scale() == LandscapeScale.REALISTIC;
		}
	}

	/** How a target is searched. */
	enum Plan {
		/** Lattice with the wide phases: large and common habitats. */
		WIDE,
		/** Coastline stretches first, then a dense search around each (as the coastal targets of {@code find}). */
		COAST,
		/**
		 * Transects from the bank of a watercourse outwards: belts a few blocks wide along the water. S9 review round 1:
		 * every lattice point near a bank starts one, not only those of the mountain belt (foothills and Beskids weigh
		 * more than half), because the classifier gives the montane tall herbs to every class C stream, and a stream is
		 * class C also outside the belt at a gradient above {@code CLASS_GRADIENT} (3 ‰): in the realistic scale the
		 * nearest such place from (0, 0) of the test seed lies 5.7 km away (8.1 km in the belt), and from lowland origins
		 * 6.8–70 km away with none in the belt within 120 km.
		 */
		WATERCOURSE,
		/** Lattice with the wide phases, the full sample only where the land reaches {@link #highFrom}. */
		HIGH_MOUNTAINS,
		/**
		 * Around the large massifs only (nearest first), the full sample only where the land reaches {@link #highFrom}:
		 * dwarf pine, alpine grassland and the timberline need a large massif ({@code AltitudinalBelts.largeMassif}); in
		 * the realistic scale of the test seed the nearest one lies about 1040 km away.
		 */
		MASSIF
	}

	private HabitatTargets() {
	}

	// ------------------------------------------------------------------ occurrence

	/** Whether the biome can occur in the mode (meadows and fields only in the PRESENT_DAY mode, §3.4.1). */
	public static boolean occurs(HabitatBiome biome, HabitatClassifier.Mode mode) {
		return mode == HabitatClassifier.Mode.PRESENT_DAY || switch (biome) {
			case WET_MEADOW, HAY_MEADOW, ARABLE_LAND -> false;
			default -> true;
		};
	}

	/** Whether the zone can occur in the mode (the alder tree row only in the PRESENT_DAY mode); NONE is no target. */
	public static boolean occurs(Zone zone, HabitatClassifier.Mode mode) {
		return zone != Zone.NONE && (zone != Zone.TREE_ROW || mode == HabitatClassifier.Mode.PRESENT_DAY);
	}

	/**
	 * Whether the zone can occur in the scale. The montane tall herbs do not occur in the gameplay scale: there every
	 * stream is at least about 5 m wide at 1:1 ({@code wr} ≥ {@code C_WILLOW_SCRUB_WR}), so up to
	 * {@code C_WILLOW_SCRUB_H} the willow scrub takes its bank, and higher up the streams of the large massifs run on the
	 * slopes, not on a valley floor (S9: 0 of about 125 000 banks around the nearest large massif and none within 100 km
	 * of the test origin; a classifier change waits for the user's decision, docs/03-m2-biomy.md §11.1).
	 */
	public static boolean occurs(Zone zone, LandscapeScale scale) {
		return zone != Zone.MONTANE_TALL_HERBS || scale == LandscapeScale.REALISTIC;
	}

	// ------------------------------------------------------------------ plans

	static Plan plan(HabitatBiome biome) {
		return switch (biome) {
			case COASTAL_PINE_FOREST, BEACH, WHITE_DUNE, GRAY_DUNE, SEA, LAGOON -> Plan.COAST;
			case MONTANE_SPRUCE_FOREST -> Plan.HIGH_MOUNTAINS;
			case DWARF_PINE_SCRUB, ALPINE_GRASSLAND -> Plan.MASSIF;
			default -> Plan.WIDE;
		};
	}

	static Plan plan(Zone zone) {
		return switch (zone) {
			case STRANDLINE, EMBRYO_DUNE, CLIFF_FACE, CLIFF_TOP -> Plan.COAST;
			case MONTANE_TALL_HERBS -> Plan.WATERCOURSE;
			case TIMBERLINE -> Plan.MASSIF;
			default -> Plan.WIDE;
		};
	}

	/** Least land elevation (m) a column of a high mountain target can have (the belt thresholds less the lowering). */
	static double highFrom(HabitatBiome biome) {
		return switch (biome) {
			case ALPINE_GRASSLAND -> AltitudinalBelts.ALPINE_THRESHOLD - AltitudinalBelts.MAX_LOWERING
					- AltitudinalBelts.ALPINE_RAMP;
			case DWARF_PINE_SCRUB -> AltitudinalBelts.SUMMIT_FROM;
			// The montane spruce forest also grows as Abieti-Piceetum in the lower montane belt (cold aspects, ridges).
			case MONTANE_SPRUCE_FOREST -> AltitudinalBelts.LOWER_MONTANE - AltitudinalBelts.MAX_LOWERING;
			default -> Double.NEGATIVE_INFINITY;
		};
	}

	/** Least land elevation of the zones searched only high in the mountains. */
	static double highFrom(Zone zone) {
		return zone == Zone.TIMBERLINE ? AltitudinalBelts.SUMMIT_FROM : Double.NEGATIVE_INFINITY;
	}

	// ------------------------------------------------------------------ search

	/** The nearest place of the biome from (ox, oz), {x, z}, or null within {@link #radius}. */
	public static double[] locate(World world, HabitatBiome biome, double ox, double oz, BooleanSupplier cancelled) {
		IntPredicate test = code -> Habitat.biome(code) == biome;
		return locate(world, plan(biome), highFrom(biome), test, ox, oz, cancelled);
	}

	/** The nearest place of the zone from (ox, oz), {x, z}, or null within {@link #radius}. */
	public static double[] locate(World world, Zone zone, double ox, double oz, BooleanSupplier cancelled) {
		IntPredicate test = code -> Habitat.zone(code) == zone;
		return locate(world, plan(zone), highFrom(zone), test, ox, oz, cancelled);
	}

	/** Search radius of a plan (m), for the "not found" message. */
	public static double radius(World world, HabitatBiome biome) {
		return radius(world, plan(biome));
	}

	public static double radius(World world, Zone zone) {
		return radius(world, plan(zone));
	}

	static double radius(World world, Plan plan) {
		return switch (plan) {
			case COAST -> PolishForestsCommands.coastalRadius(world.model());
			case WATERCOURSE -> watercoursePhases(world).getLast().radius();
			case WIDE, HIGH_MOUNTAINS -> widePhases(world).getLast().radius();
			case MASSIF -> massifRadius(world);
		};
	}

	private static double[] locate(World world, Plan plan, double highFrom, IntPredicate test, double ox, double oz,
			BooleanSupplier cancelled) {
		LandscapeModel model = world.model();
		return switch (plan) {
			case COAST -> PolishForestsCommands.searchCoastal(model, p -> test.test(world.code(p[0], p[1])), ox, oz, cancelled);
			case WIDE -> GridSearch.nearest(cell(world), widePhases(world), 0,
					p -> test.test(world.code(p[0], p[1])) ? p : null, ox, oz, cancelled);
			case HIGH_MOUNTAINS -> GridSearch.nearest(cell(world), widePhases(world), 0,
					p -> model.landElevation(p[0], p[1]) >= highFrom && test.test(world.code(p[0], p[1])) ? p : null,
					ox, oz, cancelled);
			case WATERCOURSE -> GridSearch.nearest(cell(world), watercoursePhases(world), bankBand(world) + transect(world),
					p -> Transect.fromBank(world, p, bankBand(world), transect(world), test),
					ox, oz, cancelled);
			case MASSIF -> aroundMassifs(world, highFrom, test, ox, oz, cancelled);
		};
	}

	/** Largest distance of a large massif center searched by {@link Plan#MASSIF} (m). */
	static double massifRadius(World world) {
		return world.realistic() ? 2_000_000 : 300_000;
	}

	/**
	 * {@link Plan#MASSIF}: the large massifs within {@link #massifRadius} in the order of the distance of their centers;
	 * around each, a lattice search from the center within the massif's reach. The first massif with a hit gives the
	 * result (the hit nearest to its center, so not always the nearest hit to the player).
	 */
	private static double[] aroundMassifs(World world, double highFrom, IntPredicate test, double ox, double oz,
			BooleanSupplier cancelled) {
		LandscapeModel model = world.model();
		double r = massifRadius(world);
		List<LandscapeModel.GreatMassif> massifs = new java.util.ArrayList<>(model.greatMassifs(ox - r, oz - r, ox + r, oz + r));
		massifs.sort(java.util.Comparator.comparingDouble(g -> Math.hypot(g.x() - ox, g.z() - oz)));
		double reach = model.greatMassifReach();
		double spacing = world.realistic() ? 32 : 8;
		List<GridSearch.Phase> phases = List.of(new GridSearch.Phase(spacing, reach));
		double cell = world.realistic() ? 2_048 : 256;
		for (LandscapeModel.GreatMassif g : massifs) {
			if (Math.hypot(g.x() - ox, g.z() - oz) > r || cancelled.getAsBoolean()) {
				break;
			}
			double[] hit = GridSearch.nearest(cell, phases, 0,
					p -> model.landElevation(p[0], p[1]) >= highFrom && test.test(world.code(p[0], p[1])) ? p : null,
					g.x(), g.z(), cancelled);
			if (hit != null) {
				return hit;
			}
		}
		return null;
	}

	/** Cell of the lattice (m): a multiple of every spacing of the phases. */
	static double cell(World world) {
		return world.realistic() ? 3_072 : 384;
	}

	/** Phases of the wide lattice: 600 km in the realistic scale, 150 km in the gameplay scale. */
	static List<GridSearch.Phase> widePhases(World world) {
		return world.realistic()
				? List.of(new GridSearch.Phase(32, 4_000), new GridSearch.Phase(96, 30_000),
						new GridSearch.Phase(256, 150_000), new GridSearch.Phase(768, 600_000))
				: List.of(new GridSearch.Phase(8, 1_000), new GridSearch.Phase(24, 8_000),
						new GridSearch.Phase(64, 40_000), new GridSearch.Phase(192, 150_000));
	}

	/** Phases of the lattice of the transects (each lattice point near a bank starts one). */
	static List<GridSearch.Phase> watercoursePhases(World world) {
		return world.realistic()
				? List.of(new GridSearch.Phase(96, 30_000), new GridSearch.Phase(256, 300_000))
				: List.of(new GridSearch.Phase(24, 8_000), new GridSearch.Phase(64, 100_000));
	}

	/** Largest distance from the bank of a lattice point that starts a transect (m). */
	static double bankBand(World world) {
		return world.realistic() ? 192 : 48;
	}

	/** Length of a transect from the bank (m). */
	static double transect(World world) {
		return world.realistic() ? 120 : 40;
	}
}
