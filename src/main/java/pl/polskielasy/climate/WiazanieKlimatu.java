package pl.polskielasy.climate;

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
import pl.polskielasy.PolskieLasy;
import pl.polskielasy.worldgen.chunk.PolskaChunkGenerator;
import pl.polskielasy.worldgen.chunk.PolskaScale;
import pl.polskielasy.worldgen.chunk.VerticalScale;

/**
 * Przypina profile klimatu ({@link KlimatBiomu}) do biomów z tagu {@code #polskielasy:klimat_polski}.
 *
 * <p>Profil wymaga skali pionowej świata, a tagi wczytują się wcześniej niż poziomy, dlatego:
 * <ul>
 * <li>serwer przypina profile w {@link PolskaChunkGenerator#createState} (przed generacją) i ponownie
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
public final class WiazanieKlimatu {
	public static final TagKey<Biome> KLIMAT_POLSKI = TagKey.create(Registries.BIOME, PolskieLasy.id("klimat_polski"));

	private WiazanieKlimatu() {
	}

	public static void register() {
		CommonLifecycleEvents.TAGS_LOADED.register((registries, client) -> poZaladowaniuTagow(registries));
		ServerLevelEvents.LOAD.register((server, level) -> {
			if (level.dimension() == Level.OVERWORLD
					&& level.getChunkSource().getGenerator() instanceof PolskaChunkGenerator generator) {
				przypnij(level.registryAccess(), generator.vertical());
			}
		});
	}

	/**
	 * Przypina profile do biomów źródła biomów, które należą do tagu. Wołane z
	 * {@code createState}, gdzie nie ma jeszcze dostępu do rejestrów, ale tagi są już związane.
	 */
	public static int przypnij(Iterable<Holder<Biome>> biomy, VerticalScale skala) {
		int n = 0;
		for (Holder<Biome> holder : biomy) {
			if (holder.is(KLIMAT_POLSKI)) {
				BiomeKlimat.ustaw(holder.value(), profil(holder, skala));
				n++;
			}
		}
		return n;
	}

	/** Wylicza profile wszystkich biomów rejestru: biomy z tagu dostają profil, pozostałe go tracą. */
	public static int przypnij(RegistryAccess registries, VerticalScale skala) {
		int n = 0;
		for (Holder.Reference<Biome> holder : registries.lookupOrThrow(Registries.BIOME).listElements().toList()) {
			if (holder.is(KLIMAT_POLSKI)) {
				BiomeKlimat.ustaw(holder.value(), profil(holder, skala));
				n++;
			} else if (BiomeKlimat.klimat(holder.value()) != null) {
				BiomeKlimat.ustaw(holder.value(), null);
			}
		}
		PolskieLasy.LOG.debug("Klimat: profil w {} biomach (skala {})", n, nazwa(skala));
		return n;
	}

	/**
	 * Przypina profile biomom rejestru z tagu i nie zdejmuje ich pozostałym biomom. Dla klienta
	 * połączonego z serwerem zdalnym przy zmianie wymiaru.
	 */
	public static int przypnijBezZdejmowania(RegistryAccess registries, VerticalScale skala) {
		return przypnij(registries.lookupOrThrow(Registries.BIOME).listElements().<Holder<Biome>>map(h -> h).toList(),
				skala);
	}

	/** Po wczytaniu tagów: jeśli świat ma już profile, wylicza je od nowa z tą samą skalą. */
	private static void poZaladowaniuTagow(RegistryAccess registries) {
		VerticalScale skala = null;
		for (Holder.Reference<Biome> holder : registries.lookupOrThrow(Registries.BIOME).listElements().toList()) {
			KlimatBiomu klimat = BiomeKlimat.klimat(holder.value());
			if (klimat != null) {
				skala = klimat.skala();
				break;
			}
		}
		if (skala != null) {
			przypnij(registries, skala);
		}
	}

	private static KlimatBiomu profil(Holder<Biome> holder, VerticalScale skala) {
		String id = holder.unwrapKey().map(ResourceKey::identifier).map(Object::toString).orElse("");
		return KlimatBiomu.zastepczy(id, skala);
	}

	/** Nazwa skali do logów. */
	public static String nazwa(@Nullable VerticalScale skala) {
		for (PolskaScale s : PolskaScale.values()) {
			if (s.vertical() == skala) {
				return s.getSerializedName();
			}
		}
		return String.valueOf(skala);
	}
}
