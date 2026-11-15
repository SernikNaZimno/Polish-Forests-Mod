package pl.polishforests.climate;

import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.jspecify.annotations.Nullable;
import pl.polishforests.PolishForests;
import pl.polishforests.worldgen.chunk.PolandChunkGenerator;
import pl.polishforests.worldgen.chunk.PolandScale;
import pl.polishforests.worldgen.chunk.VerticalScale;
import pl.polishforests.worldgen.vanilla.VanillaForestsBiomeSource;

/**
 * Attaches climate profiles ({@link BiomeClimate}) to the biomes in the {@code #polishforests:polish_climate} tag.
 *
 * <p>A profile needs the vertical scale of the world, and tags load before levels, so:
 * <ul>
 * <li>the server attaches profiles in {@link PolandChunkGenerator#createState} (before generation) and again
 * in {@code ServerLevelEvents.LOAD} for an overworld with the "Poland" generator;</li>
 * <li>a client connected to a remote server attaches them in {@code ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE}
 * (class ClientClimate), without removing profiles from other biomes;</li>
 * <li>{@code CommonLifecycleEvents.TAGS_LOADED} (both sides, including /reload) recomputes the profiles
 * from the new tag contents, with the scale taken from the profiles already attached.</li>
 * </ul>
 * The class has no static state: everything lives in the {@code Biome} objects, which every world
 * creates anew. In single player the client shares biome objects with the integrated server
 * ({@code ClientConfigurationPacketListenerImpl.handleConfigurationFinished}), so it attaches nothing
 * on a dimension change: the profiles are already attached by the server. Profiles are removed only by
 * the recomputation after {@code TAGS_LOADED}, according to the tag contents, which are the same on both sides.
 *
 * <p>Step C2 (decision CP-D.11): in the world type "Default + Polish Forests" the biomes of the tag get the profile
 * {@link BiomeClimate#vanillaColumns} instead (the column's vanilla biome with the vanilla height adjustment,
 * {@link VanillaClimate}): the server attaches it when the overworld loads and before the first chunk is decorated
 * ({@code VanillaForestWorld}), a client of a remote server when it enters an overworld that is not a "Poland" world
 * ({@code ClientClimate}), and {@code TAGS_LOADED} recomputes it like the "Poland" profiles. The "Poland" profiles are
 * unchanged.
 */
public final class ClimateBinding {
	public static final TagKey<Biome> POLISH_CLIMATE = TagKey.create(Registries.BIOME, PolishForests.id("polish_climate"));

	private ClimateBinding() {
	}

	public static void register() {
		ColumnClimates.register();
		CommonLifecycleEvents.TAGS_LOADED.register((registries, client) -> onTagsLoaded(registries));
		ServerLevelEvents.LOAD.register((server, level) -> {
			if (level.dimension() != Level.OVERWORLD) {
				return;
			}
			if (level.getChunkSource().getGenerator() instanceof PolandChunkGenerator generator) {
				attach(level.registryAccess(), generator.vertical());
			} else if (level.getChunkSource().getGenerator().getBiomeSource() instanceof VanillaForestsBiomeSource) {
				attachVanillaColumns(level.registryAccess());
				ColumnClimates.bindServer(level);
			}
		});
	}

	/**
	 * Step C2: the profile {@link BiomeClimate#vanillaColumns} for the biomes of the tag among the given ones (the biome
	 * source's, before the first chunk of the world type "Default + Polish Forests" is decorated), without detaching any.
	 */
	public static int attachVanillaColumns(Iterable<Holder<Biome>> biomes) {
		int n = 0;
		for (Holder<Biome> holder : biomes) {
			if (holder.is(POLISH_CLIMATE)) {
				BiomeClimateAccess.set(holder.value(), BiomeClimate.vanillaColumns(holder.value().getBaseTemperature()));
				n++;
			}
		}
		return n;
	}

	/** Step C2: the profile {@link BiomeClimate#vanillaColumns} for the registry's biomes in the tag, detached from the others. */
	public static int attachVanillaColumns(RegistryAccess registries) {
		int n = 0;
		for (Holder.Reference<Biome> holder : registries.lookupOrThrow(Registries.BIOME).listElements().toList()) {
			if (holder.is(POLISH_CLIMATE)) {
				BiomeClimateAccess.set(holder.value(), BiomeClimate.vanillaColumns(holder.value().getBaseTemperature()));
				n++;
			} else if (BiomeClimateAccess.climate(holder.value()) != null) {
				BiomeClimateAccess.set(holder.value(), null);
			}
		}
		PolishForests.LOG.debug("Climate: column profile in {} biomes (Default + Polish Forests)", n);
		return n;
	}

	/** Step C2: the column profile for the registry's biomes in the tag without detaching others (a remote client). */
	public static int attachVanillaColumnsWithoutDetaching(RegistryAccess registries) {
		return attachVanillaColumns(registries.lookupOrThrow(Registries.BIOME).listElements().<Holder<Biome>>map(h -> h).toList());
	}

	/**
	 * Attaches profiles to the biome source's biomes that belong to the tag. Called from
	 * {@code createState}, where the registries are not accessible yet but the tags are already bound.
	 */
	public static int attach(Iterable<Holder<Biome>> biomes, VerticalScale scale) {
		int n = 0;
		for (Holder<Biome> holder : biomes) {
			if (holder.is(POLISH_CLIMATE)) {
				BiomeClimateAccess.set(holder.value(), profile(holder, scale));
				n++;
			}
		}
		return n;
	}

	/** Computes the profiles of all biomes in the registry: biomes in the tag get a profile, the others lose it. */
	public static int attach(RegistryAccess registries, VerticalScale scale) {
		int n = 0;
		for (Holder.Reference<Biome> holder : registries.lookupOrThrow(Registries.BIOME).listElements().toList()) {
			if (holder.is(POLISH_CLIMATE)) {
				BiomeClimateAccess.set(holder.value(), profile(holder, scale));
				n++;
			} else if (BiomeClimateAccess.climate(holder.value()) != null) {
				BiomeClimateAccess.set(holder.value(), null);
			}
		}
		PolishForests.LOG.debug("Climate: profile in {} biomes (scale {})", n, scaleName(scale));
		return n;
	}

	/**
	 * Attaches profiles to the registry's biomes in the tag without removing them from the other biomes.
	 * For a client connected to a remote server, on a dimension change.
	 */
	public static int attachWithoutDetaching(RegistryAccess registries, VerticalScale scale) {
		return attach(registries.lookupOrThrow(Registries.BIOME).listElements().<Holder<Biome>>map(h -> h).toList(),
				scale);
	}

	/** After tags load: if the world already has profiles, recomputes them with the same scale (or kind, step C2). */
	private static void onTagsLoaded(RegistryAccess registries) {
		BiomeClimate found = null;
		for (Holder.Reference<Biome> holder : registries.lookupOrThrow(Registries.BIOME).listElements().toList()) {
			BiomeClimate climate = BiomeClimateAccess.climate(holder.value());
			if (climate != null) {
				found = climate;
				break;
			}
		}
		if (found == null) {
			return;
		}
		VerticalScale scale = found.scale();
		if (found.vanillaColumns() || scale == null) {
			attachVanillaColumns(registries);
		} else {
			attach(registries, scale);
		}
	}

	private static BiomeClimate profile(Holder<Biome> holder, VerticalScale scale) {
		String id = holder.unwrapKey().map(ResourceKey::identifier).map(Object::toString).orElse("");
		return BiomeClimate.profile(id, scale);
	}

	/** Scale name for logs. */
	public static String scaleName(@Nullable VerticalScale scale) {
		for (PolandScale s : PolandScale.values()) {
			if (s.vertical() == scale) {
				return s.getSerializedName();
			}
		}
		return String.valueOf(scale);
	}
}
