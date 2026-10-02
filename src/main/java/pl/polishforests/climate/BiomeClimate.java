package pl.polishforests.climate;

import java.util.List;
import net.minecraft.core.BlockPos;
import pl.polishforests.worldgen.chunk.VerticalScale;

/**
 * Profil klimatu biomu w świecie "Polska", przypinany do obiektu {@code Biome} przez
 * {@link BiomeClimateAccess}. Biom bez profilu (Nether, End, światy wanilijne) liczy temperaturę po
 * wanilijnemu.
 *
 * @param scale       odwzorowanie pionowe świata (metry n.p.m. dla Y)
 * @param baseTemperature     temperatura na poziomie morza, najwyżej {@link PolandClimate#MAX_BASE_TEMPERATURE}
 * @param freezeMode  zachowanie wody w biomie
 */
public record BiomeClimate(VerticalScale scale, float baseTemperature, FreezeMode freezeMode) {
	/** Temperatura bazowa nizin, gór i wód śródlądowych (ok. 10 °C). */
	public static final float T_LOWLAND = 0.70F;
	/** Temperatura bazowa morza i wybrzeża (łagodniejszy klimat nadmorski). */
	public static final float T_SEA = 0.72F;

	/**
	 * Biomy zastępcze z presetów świata (data/polskielasy/worldgen/world_preset), objęte tagiem
	 * {@code #polskielasy:klimat_polski} do czasu biomów moda (krok S5). Preset ma 12 pól, ale
	 * "forest" i "river" występują w nich dwa razy.
	 */
	public static final List<String> PLACEHOLDERS = List.of(
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
	public enum FreezeMode {
		/** Jak w wanilii: woda zamarza przy T < 0,15. */
		VANILLA,
		/** Rzeki i potoki: zamarzają dopiero przy T < {@link PolandClimate#RIVER_FREEZE_THRESHOLD}. */
		RIVER,
		/** Morze (Bałtyk, decyzja M2-6) nigdy nie zamarza. */
		NEVER
	}

	/** Temperatura w danym bloku (bez korekty pory roku, którą dodaje Serene Seasons). */
	public float temperature(BlockPos pos) {
		return PolandClimate.temperature(baseTemperature, scale, pos.getX(), pos.getY(), pos.getZ());
	}

	/**
	 * Profil biomu z tagu {@code #polskielasy:klimat_polski} według identyfikatora. W krokach S1–S4
	 * są to biomy zastępcze: morze ma 0,72 i nie zamarza, rzeka zamarza jak rzeka, reszta ma 0,70.
	 * Biom dopisany do tagu przez paczkę danych dostaje profil nizinny.
	 */
	public static BiomeClimate placeholder(String id, VerticalScale scale) {
		return switch (id) {
			case "minecraft:ocean" -> new BiomeClimate(scale, T_SEA, FreezeMode.NEVER);
			case "minecraft:river" -> new BiomeClimate(scale, T_LOWLAND, FreezeMode.RIVER);
			default -> new BiomeClimate(scale, T_LOWLAND, FreezeMode.VANILLA);
		};
	}
}
