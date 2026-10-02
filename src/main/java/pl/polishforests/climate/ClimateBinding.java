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

/**
 * Przypina profile klimatu ({@link BiomeClimate}) do biomów z tagu {@code #polskielasy:klimat_polski}.
 *
 * <p>Profil wymaga skali pionowej świata, a tagi wczytują się wcześniej niż poziomy, dlatego:
 * <ul>
 * <li>serwer przypina profile w {@link PolandChunkGenerator#createState} (przed generacją) i ponownie
 * w {@code ServerLevelEvents.LOAD} dla overworldu z generatorem "Polska";</li>
 * <li>klient połączony z serwerem zdalnym przypina je w {@code ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE}
 * (klasa KlimatKlienta), bez zdejmowania profili innym biomom;</li>
 * <li>{@code CommonLifecycleEvents.TAGS_LOADED} (obie strony, także /reload) wylicza profile od nowa
 * według nowej zawartości tagu, ze skalą wziętą z już przypiętych profili.</li>
 * </ul>
 * Klasa nie ma stanu statycznego: wszystko siedzi w obiektach {@code Biome}, które każdy świat
 * tworzy od nowa. W grze jednoosobowej klient dzieli obiekty biomów z serwerem zintegrowanym
 * ({@code ClientConfigurationPacketListenerImpl.handleConfigurationFinished}), więc przy zmianie
 * wymiaru nic nie przypina: profile są już przypięte przez serwer. Profile zdejmuje tylko
 * przeliczenie po {@code TAGS_LOADED}, według zawartości tagu, która po obu stronach jest ta sama.
 */
public final class ClimateBinding {
	public static final TagKey<Biome> POLISH_CLIMATE = TagKey.create(Registries.BIOME, PolishForests.id("polish_climate"));

	private ClimateBinding() {
	}

	public static void register() {
		CommonLifecycleEvents.TAGS_LOADED.register((registries, client) -> onTagsLoaded(registries));
		ServerLevelEvents.LOAD.register((server, level) -> {
			if (level.dimension() == Level.OVERWORLD
					&& level.getChunkSource().getGenerator() instanceof PolandChunkGenerator generator) {
				attach(level.registryAccess(), generator.vertical());
			}
		});
	}

	/**
	 * Przypina profile do biomów źródła biomów, które należą do tagu. Wołane z
	 * {@code createState}, gdzie nie ma jeszcze dostępu do rejestrów, ale tagi są już związane.
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

	/** Wylicza profile wszystkich biomów rejestru: biomy z tagu dostają profil, pozostałe go tracą. */
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
		PolishForests.LOG.debug("Klimat: profil w {} biomach (skala {})", n, scaleName(scale));
		return n;
	}

	/**
	 * Przypina profile biomom rejestru z tagu i nie zdejmuje ich pozostałym biomom. Dla klienta
	 * połączonego z serwerem zdalnym przy zmianie wymiaru.
	 */
	public static int attachWithoutDetaching(RegistryAccess registries, VerticalScale scale) {
		return attach(registries.lookupOrThrow(Registries.BIOME).listElements().<Holder<Biome>>map(h -> h).toList(),
				scale);
	}

	/** Po wczytaniu tagów: jeśli świat ma już profile, wylicza je od nowa z tą samą skalą. */
	private static void onTagsLoaded(RegistryAccess registries) {
		VerticalScale scale = null;
		for (Holder.Reference<Biome> holder : registries.lookupOrThrow(Registries.BIOME).listElements().toList()) {
			BiomeClimate climate = BiomeClimateAccess.climate(holder.value());
			if (climate != null) {
				scale = climate.scale();
				break;
			}
		}
		if (scale != null) {
			attach(registries, scale);
		}
	}

	private static BiomeClimate profile(Holder<Biome> holder, VerticalScale scale) {
		String id = holder.unwrapKey().map(ResourceKey::identifier).map(Object::toString).orElse("");
		return BiomeClimate.placeholder(id, scale);
	}

	/** Nazwa skali do logów. */
	public static String scaleName(@Nullable VerticalScale scale) {
		for (PolandScale s : PolandScale.values()) {
			if (s.vertical() == scale) {
				return s.getSerializedName();
			}
		}
		return String.valueOf(scale);
	}
}
