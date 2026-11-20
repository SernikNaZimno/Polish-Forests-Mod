package pl.polishforests.gametest;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import pl.polishforests.command.FaunaSites;
import pl.polishforests.entity.base.AnimalProfile;
import pl.polishforests.entity.base.PolishAnimal;
import pl.polishforests.entity.jay.EurasianJay;
import pl.polishforests.entity.roedeer.RoeDeer;
import pl.polishforests.entity.spawn.FaunaHabitat;
import pl.polishforests.entity.squirrel.RedSquirrel;
import pl.polishforests.entity.wildboar.WildBoar;
import pl.polishforests.worldgen.habitat.HabitatBiome;

/**
 * Step FA9 (docs/04 "Projekt FA9"): the census of the mod's animals by habitat for KFA-5, {@code -Pgametest=fauna
 * -Psites=habitats} (report {@code build/fauna/habitats.txt}, lines {@code [fauna]}). Numbers only (G.36 U-1), no
 * failures: the verdicts are for the summary of the collective run.
 *
 * <p>In every world of the fauna census ("Poland" in the scales and modes of {@code -Pscales} and {@code -Pmodes}, and
 * "Default + Polish Forests") the test visits each habitat biome in which one of the four species lives (weight above 0 in
 * its {@link AnimalProfile#habitats()}): the nearest column of the biome from the first place of the world (the search of
 * {@code find biome}, {@link FaunaSites}), the player stands there {@value #SETTLE} ticks (the chunks around generate, with
 * the spawn at generation, and the fauna spawner tops up), then in the square of {@value #HALF} blocks around the place
 * the columns of the biome are counted (every {@value #STEP}th block in both directions) and the animals standing on such
 * columns, by species. The density is animals per hectare of the biome (a block is a meter for the animals in both
 * scales, KFA-3); its multiple of the real density is {@code density / (realDensity × weight of the biome)}, against
 * the band ×20–50 of KFA-5 (verdict {@code in}, {@code out}, or {@code few} below {@value #MIN_ANIMALS} animals). At the
 * end the counts are pooled over the worlds by scale and world type (both modes together), by habitat and for the species
 * as a whole (animals over the hectares weighted by the habitat weight).
 */
final class FaunaHabitatCensus {
	/** Half the side of the counted square (blocks). */
	static final int HALF = 96;
	/** Step of the column sample (blocks). */
	static final int STEP = 4;
	/** Ticks the player stands at a place before the count. */
	static final int SETTLE = 400;
	/** Fewer animals than this in a cell of the summary: verdict {@code few} (the count says little). */
	static final int MIN_ANIMALS = 5;
	/** The band of KFA-5: the mod's density is 20 to 50 times the real one. */
	static final double LOW = 20;
	static final double HIGH = 50;
	/** Smallest area of the biome in the square for a line of the census (ha). */
	static final double MIN_AREA = 0.2;

	static final List<AnimalProfile> PROFILES = List.of(RoeDeer.PROFILE, WildBoar.PROFILE, RedSquirrel.PROFILE,
			EurasianJay.PROFILE);

	private final ClientGameTestContext context;
	private final FaunaCensus census;
	/** Pooled counts: kind (scale or world type) → species → biome → {animals, hectares}. */
	private final Map<String, Map<String, Map<HabitatBiome, double[]>>> pooled = new LinkedHashMap<>();

	FaunaHabitatCensus(ClientGameTestContext context, FaunaCensus census) {
		this.context = context;
		this.census = census;
	}

	/** The habitat biomes in which one of the species lives, in the order of the enum. */
	static Set<HabitatBiome> habitats() {
		Set<HabitatBiome> out = EnumSet.noneOf(HabitatBiome.class);
		for (AnimalProfile p : PROFILES) {
			for (Map.Entry<HabitatBiome, Float> e : p.habitats().entrySet()) {
				if (e.getValue() > 0) {
					out.add(e.getKey());
				}
			}
		}
		return out;
	}

	/** The census of one world: {@code kind} is the pooling key (the scale, or the world type). */
	void world(TestSingleplayerContext sp, String label, String kind, int ox, int oz) {
		for (HabitatBiome b : habitats()) {
			double[] at = sp.getServer().computeOnServer(s -> FaunaSites.locate(s.overworld(), b, ox, oz));
			if (at == null) {
				census.line("%s habitats %s: not found in reach", label, b.id());
				continue;
			}
			int x = (int) Math.floor(at[0]);
			int z = (int) Math.floor(at[1]);
			FaunaCensus.teleport(sp, x, z);
			context.waitTicks(SETTLE);
			Count c = sp.getServer().computeOnServer(s -> count(s.overworld(), b, x, z));
			if (c.hectares < MIN_AREA) {
				census.line("%s habitats %s at %d %d: only %.2f ha of the biome in the square, not counted", label, b.id(), x, z,
						c.hectares);
				continue;
			}
			List<String> parts = new ArrayList<>();
			for (AnimalProfile p : PROFILES) {
				float w = p.weight(b);
				if (w <= 0) {
					continue;
				}
				int n = c.animals.getOrDefault(p.species(), 0);
				pooled.computeIfAbsent(kind, k -> new LinkedHashMap<>()).computeIfAbsent(p.species(), k -> new LinkedHashMap<>())
						.computeIfAbsent(b, k -> new double[2]);
				double[] cell = pooled.get(kind).get(p.species()).get(b);
				cell[0] += n;
				cell[1] += c.hectares;
				parts.add(String.format(Locale.ROOT, "%s %d (%.2f/ha, ×%.0f of real %.3f×%.2f)", p.species(), n, n / c.hectares,
						multiple(p, w, n, c.hectares), p.realDensity(), w));
			}
			census.line("%s habitats %s at %d %d: %.2f ha of the biome in the square of %d blocks after %d ticks; %s; on other columns"
					+ " of the square %d", label, b.id(), x, z, c.hectares, 2 * HALF, SETTLE, String.join(", ", parts), c.elsewhere);
		}
	}

	/** Multiple of the real density of the species in a habitat of weight w. */
	static double multiple(AnimalProfile p, float w, double animals, double hectares) {
		return hectares <= 0 || w <= 0 ? 0 : animals / hectares / (p.realDensity() * w);
	}

	static String verdict(double animals, double multiple) {
		if (animals < MIN_ANIMALS) {
			return "few";
		}
		return multiple >= LOW && multiple <= HIGH ? "in" : "out";
	}

	/** The pooled summary: by kind, species and habitat, then the species as a whole. */
	void summary() {
		census.line("habitats summary (KFA-5 band ×%.0f–%.0f of the real density; %s below %d animals):", LOW, HIGH, "few",
				MIN_ANIMALS);
		for (Map.Entry<String, Map<String, Map<HabitatBiome, double[]>>> k : pooled.entrySet()) {
			for (AnimalProfile p : PROFILES) {
				Map<HabitatBiome, double[]> cells = k.getValue().get(p.species());
				if (cells == null) {
					continue;
				}
				double animals = 0;
				double weighted = 0;
				double hectares = 0;
				List<String> parts = new ArrayList<>();
				for (Map.Entry<HabitatBiome, double[]> e : cells.entrySet()) {
					float w = p.weight(e.getKey());
					double n = e.getValue()[0];
					double ha = e.getValue()[1];
					double m = multiple(p, w, n, ha);
					animals += n;
					weighted += ha * w;
					hectares += ha;
					parts.add(String.format(Locale.ROOT, "%s %.0f on %.1f ha ×%.0f %s", e.getKey().id(), n, ha, m, verdict(n, m)));
				}
				double m = weighted <= 0 ? 0 : animals / weighted / p.realDensity();
				census.line("habitats summary %s %s: %.0f animals on %.1f ha (%.1f ha weighted), ×%.1f of the real density %s"
						+ " (generation ×%.0f, spawner ×%.0f); %s", k.getKey(), p.species(), animals, hectares, weighted, m,
						verdict(animals, m), p.generationFactor(), p.spawnerFactor(), String.join("; ", parts));
			}
		}
	}

	private static final class Count {
		double hectares;
		final Map<String, Integer> animals = new LinkedHashMap<>();
		int elsewhere;
	}

	private static Count count(ServerLevel level, HabitatBiome b, int x, int z) {
		Count c = new Count();
		int columns = 0;
		for (int dx = -HALF; dx < HALF; dx += STEP) {
			for (int dz = -HALF; dz < HALF; dz += STEP) {
				if (FaunaHabitat.surface(level, x + dx + STEP / 2, z + dz + STEP / 2) == b) {
					columns++;
				}
			}
		}
		c.hectares = columns * (double) (STEP * STEP) / AnimalProfile.BLOCKS_PER_HECTARE;
		AABB box = new AABB(x - HALF, level.getMinY(), z - HALF, x + HALF, level.getMaxY(), z + HALF);
		for (PolishAnimal<?> a : level.getEntitiesOfClass(PolishAnimal.class, box, e -> e.isAlive())) {
			if (FaunaHabitat.surface(level, (int) Math.floor(a.getX()), (int) Math.floor(a.getZ())) == b) {
				c.animals.merge(a.profile().species(), 1, Integer::sum);
			} else {
				c.elsewhere++;
			}
		}
		return c;
	}
}
