package pl.polishforests.command;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;
import pl.polishforests.PolishForests;
import pl.polishforests.climate.PolandClimate;
import pl.polishforests.worldgen.chunk.PolandChunkGenerator;
import pl.polishforests.worldgen.feature.TreeStandFeature;
import pl.polishforests.worldgen.habitat.Habitat;
import pl.polishforests.worldgen.habitat.HabitatClassifier;
import pl.polishforests.worldgen.habitat.Species;
import pl.polishforests.worldgen.landscape.ColumnSample;

/**
 * Step S9: the habitat of one column as {@code /polishforests here} (second line, translated) and the F3 entry
 * {@code polishforests:habitat} (English, like the vanilla debug lines) show it: biome, forest site type (STL),
 * association, zone, soil, depth to groundwater, oceanicity O, mountain influence P, mean annual temperature (°C, from
 * the base temperature of the biome and the elevation, without the seasons), the mode, and the tree species of the stand
 * rule of the column ({@link pl.polishforests.worldgen.feature.config.TreePalette#species}); the species whose vanilla
 * stand-in has the pale oak bark (§8.4, risk R12) are marked as placeholders until M3.
 *
 * @param habitat     habitat of the column
 * @param dgw         depth to groundwater (m)
 * @param oceanicity  O
 * @param mountain    P
 * @param celsius     mean annual temperature at the given height (°C)
 * @param mode        vegetation mode of the world
 * @param trees       tree species of the stand rule (empty without trees)
 */
public record HabitatInfo(Habitat habitat, double dgw, double oceanicity, double mountain, double celsius,
		HabitatClassifier.Mode mode, List<Species> trees) {
	/** Species whose M2 stand-in has the gray bark of {@code pale_oak_log} (§8.4), which misleads players (R12). */
	public static final Set<Species> PALE_BARK_PLACEHOLDERS = EnumSet.of(Species.FIR, Species.BEECH, Species.HORNBEAM,
			Species.GRAY_ALDER);

	/** Habitat of the column (x, z) with the temperature at height y, or null outside a Poland world. */
	public static @Nullable HabitatInfo of(ServerLevel level, int x, int y, int z) {
		if (!(level.getChunkSource().getGenerator() instanceof PolandChunkGenerator gen)) {
			return null;
		}
		long seed = level.getSeed();
		ColumnSample s = gen.model(seed).sample(x, z);
		HabitatClassifier classifier = gen.classifier(seed);
		int code = classifier.classify(s, x, z);
		Habitat h = Habitat.of(code);
		double t = PolandClimate.temperature(h.biome().temperature(), gen.vertical(), x, y, z);
		List<Species> trees = level.registryAccess().lookupOrThrow(Registries.FEATURE)
				.getOptional(ResourceKey.create(Registries.FEATURE, PolishForests.id("tree_stand")))
				.filter(TreeStandFeature.class::isInstance).map(f -> ((TreeStandFeature) f).palette().species(code))
				.orElse(List.of());
		return new HabitatInfo(h, classifier.dgw(s, x, z), s.region().oceanicity(), s.region().mountainInfluence(),
				PolandClimate.celsius(t), classifier.mode(), trees);
	}

	/** The second line of {@code here} (lang {@code polishforests.command.here_habitat}). */
	public Component line() {
		Habitat h = habitat;
		MutableComponent trees;
		if (this.trees.isEmpty()) {
			trees = Component.translatable("polishforests.command.here_no_trees");
		} else {
			trees = Component.empty();
			for (int i = 0; i < this.trees.size(); i++) {
				Species sp = this.trees.get(i);
				if (i > 0) {
					trees.append(", ");
				}
				trees.append(PALE_BARK_PLACEHOLDERS.contains(sp)
						? Component.translatable("polishforests.command.here_placeholder", species(sp))
								.withStyle(ChatFormatting.ITALIC)
						: species(sp));
			}
		}
		return Component.translatable("polishforests.command.here_habitat",
				Component.translatable("biome.polishforests." + h.biome().id()).withStyle(ChatFormatting.GREEN),
				h.siteType().code(), association(h), zone(h.zone().id()), soil(h),
				format("%.1f", dgw), format("%.2f", oceanicity), format("%.2f", mountain), format("%.1f", celsius), trees);
	}

	/** The lines of the F3 entry (English, like the vanilla debug screen). */
	public List<String> debugLines() {
		Habitat h = habitat;
		String stand = trees.isEmpty() ? "none" : trees.stream()
				.map(sp -> PALE_BARK_PLACEHOLDERS.contains(sp) ? sp.id() + "*" : sp.id()).collect(Collectors.joining(", "));
		return List.of(
				String.format(Locale.ROOT, "Habitat: %s", PolishForests.id(h.biome().id())),
				String.format(Locale.ROOT, "Site %s, zone %s, %s, soil %s", h.siteType().code(), h.zone().id(),
						lower(h.association()), lower(h.soil())),
				String.format(Locale.ROOT, "DGW %.1f m, O %.2f, P %.2f, %.1f °C (%s mode)", dgw, oceanicity, mountain, celsius,
						lower(mode)),
				"Trees: " + stand + (stand.contains("*") ? " (* placeholder until M3)" : ""));
	}

	public static Component zone(String id) {
		return Component.translatable("polishforests.zone." + id);
	}

	private static Component association(Habitat h) {
		return Component.translatable("polishforests.association." + lower(h.association()));
	}

	private static Component soil(Habitat h) {
		return Component.translatable("polishforests.soil." + lower(h.soil()));
	}

	private static Component species(Species sp) {
		return Component.translatable("polishforests.species." + sp.id());
	}

	private static String lower(Enum<?> e) {
		return e.name().toLowerCase(Locale.ROOT);
	}

	private static String format(String pattern, double v) {
		return String.format(Locale.ROOT, pattern, v);
	}
}
