package pl.polishforests.worldgen.feature.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Locale;
import pl.polishforests.worldgen.feature.plan.HabitatMatch;
import pl.polishforests.worldgen.habitat.Association;
import pl.polishforests.worldgen.habitat.ForestSiteType;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.habitat.Soil;
import pl.polishforests.worldgen.habitat.Zone;

/**
 * Habitat condition of a palette rule and of the {@code polishforests:habitat} placement filter (docs/03-m2-biomy.md
 * §8.2, §8.3): lists of biomes, zones, forest site types, associations and soils, each empty for "any". In JSON the
 * values are the lower-case enum names ({@code "oak_hornbeam_forest"}, {@code "herb_fringe"}, {@code "riparian"},
 * {@code "salicetum_albae"}, {@code "brown_soil"}); empty lists are left out.
 */
public record HabitatCondition(List<HabitatBiome> biomes, List<Zone> zones, List<ForestSiteType> siteTypes,
		List<Association> associations, List<Soil> soils) {
	public static final Codec<HabitatBiome> BIOME = Codec.STRING.comapFlatMap(id -> {
		HabitatBiome b = HabitatBiome.byId(id);
		return b == null ? DataResult.error(() -> "Unknown habitat biome: " + id) : DataResult.success(b);
	}, HabitatBiome::id);
	public static final Codec<Zone> ZONE = enumCodec(Zone.class, Zone.values());
	public static final Codec<ForestSiteType> SITE_TYPE = enumCodec(ForestSiteType.class, ForestSiteType.values());
	public static final Codec<Association> ASSOCIATION = enumCodec(Association.class, Association.values());
	public static final Codec<Soil> SOIL = enumCodec(Soil.class, Soil.values());

	/** The condition's fields, to be embedded in a rule's codec. */
	public static final MapCodec<HabitatCondition> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			BIOME.listOf().optionalFieldOf("biomes", List.of()).forGetter(HabitatCondition::biomes),
			ZONE.listOf().optionalFieldOf("zones", List.of()).forGetter(HabitatCondition::zones),
			SITE_TYPE.listOf().optionalFieldOf("site_types", List.of()).forGetter(HabitatCondition::siteTypes),
			ASSOCIATION.listOf().optionalFieldOf("associations", List.of()).forGetter(HabitatCondition::associations),
			SOIL.listOf().optionalFieldOf("soils", List.of()).forGetter(HabitatCondition::soils)
	).apply(i, HabitatCondition::new));

	public static final HabitatCondition ANY = new HabitatCondition(List.of(), List.of(), List.of(), List.of(), List.of());

	public HabitatCondition {
		biomes = List.copyOf(biomes);
		zones = List.copyOf(zones);
		siteTypes = List.copyOf(siteTypes);
		associations = List.copyOf(associations);
		soils = List.copyOf(soils);
	}

	public static HabitatCondition biomes(HabitatBiome... biomes) {
		return new HabitatCondition(List.of(biomes), List.of(), List.of(), List.of(), List.of());
	}

	public static HabitatCondition zones(Zone... zones) {
		return new HabitatCondition(List.of(), List.of(zones), List.of(), List.of(), List.of());
	}

	public HabitatCondition withZones(Zone... zones) {
		return new HabitatCondition(biomes, List.of(zones), siteTypes, associations, soils);
	}

	public HabitatCondition withSiteTypes(ForestSiteType... siteTypes) {
		return new HabitatCondition(biomes, zones, List.of(siteTypes), associations, soils);
	}

	public HabitatCondition withAssociations(Association... associations) {
		return new HabitatCondition(biomes, zones, siteTypes, List.of(associations), soils);
	}

	public HabitatCondition withSoils(Soil... soils) {
		return new HabitatCondition(biomes, zones, siteTypes, associations, List.of(soils));
	}

	/** The condition as bit masks for the planners. */
	public HabitatMatch match() {
		return HabitatMatch.of(biomes, zones, siteTypes, associations, soils);
	}

	/** Codec of an enum by its lower-case constant name. */
	public static <E extends Enum<E>> Codec<E> enumCodec(Class<E> type, E[] values) {
		return Codec.STRING.comapFlatMap(id -> {
			for (E e : values) {
				if (e.name().toLowerCase(Locale.ROOT).equals(id)) {
					return DataResult.success(e);
				}
			}
			return DataResult.error(() -> "Unknown " + type.getSimpleName() + ": " + id);
		}, e -> e.name().toLowerCase(Locale.ROOT));
	}
}
