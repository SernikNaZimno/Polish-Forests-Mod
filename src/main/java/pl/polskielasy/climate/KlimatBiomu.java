package pl.polskielasy.climate;

import java.util.List;
import net.minecraft.core.BlockPos;
import pl.polskielasy.worldgen.chunk.VerticalScale;

/**
 * Profil klimatu biomu w świecie "Polska", przypinany do obiektu {@code Biome} przez
 * {@link BiomeKlimat}. Biom bez profilu (Nether, End, światy wanilijne) liczy temperaturę po
 * wanilijnemu.
 *
 * @param skala       odwzorowanie pionowe świata (metry n.p.m. dla Y)
 * @param tBazowa     temperatura na poziomie morza, najwyżej {@link PolskaKlimat#MAKS_T_BAZOWA}
 * @param zamarzanie  zachowanie wody w biomie
 */
public record KlimatBiomu(VerticalScale skala, float tBazowa, Zamarzanie zamarzanie) {
	/** Temperatura bazowa nizin, gór i wód śródlądowych (ok. 10 °C). */
	public static final float T_NIZINY = 0.70F;
	/** Temperatura bazowa morza i wybrzeża (łagodniejszy klimat nadmorski). */
	public static final float T_MORZA = 0.72F;

	/**
	 * Biomy zastępcze z presetów świata (data/polskielasy/worldgen/world_preset), objęte tagiem
	 * {@code #polskielasy:klimat_polski} do czasu biomów moda (krok S5). Preset ma 12 pól, ale
	 * "forest" i "river" występują w nich dwa razy.
	 */
	public static final List<String> ZASTEPCZE = List.of(
			"minecraft:old_growth_pine_taiga",
			"minecraft:forest",
			"minecraft:plains",
			"minecraft:meadow",
			"minecraft:river",
			"minecraft:swamp",
			"minecraft:taiga",
			"minecraft:old_growth_spruce_taiga",
			"minecraft:ocean",
			"minecraft:beach");

	/** Zachowanie wody w biomie z profilem. */
	public enum Zamarzanie {
		/** Jak w wanilii: woda zamarza przy T < 0,15. */
		WANILIA,
		/** Rzeki i potoki: zamarzają dopiero przy T < {@link PolskaKlimat#PROG_ZAMARZANIA_RZEK}. */
		RZEKA,
		/** Morze (Bałtyk, decyzja M2-6) nigdy nie zamarza. */
		NIGDY
	}

	/** Temperatura w danym bloku (bez korekty pory roku, którą dodaje Serene Seasons). */
	public float temperatura(BlockPos pos) {
		return PolskaKlimat.temperatura(tBazowa, skala, pos.getX(), pos.getY(), pos.getZ());
	}

	/**
	 * Profil biomu z tagu {@code #polskielasy:klimat_polski} według identyfikatora. W krokach S1–S4
	 * są to biomy zastępcze: morze ma 0,72 i nie zamarza, rzeka zamarza jak rzeka, reszta ma 0,70.
	 * Biom dopisany do tagu przez paczkę danych dostaje profil nizinny.
	 */
	public static KlimatBiomu zastepczy(String id, VerticalScale skala) {
		return switch (id) {
			case "minecraft:ocean" -> new KlimatBiomu(skala, T_MORZA, Zamarzanie.NIGDY);
			case "minecraft:river" -> new KlimatBiomu(skala, T_NIZINY, Zamarzanie.RZEKA);
			default -> new KlimatBiomu(skala, T_NIZINY, Zamarzanie.WANILIA);
		};
	}
}
