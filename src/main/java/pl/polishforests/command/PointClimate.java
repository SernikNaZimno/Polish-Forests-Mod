package pl.polishforests.command;

import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import pl.polishforests.climate.BiomeClimate;
import pl.polishforests.climate.BiomeClimateAccess;
import pl.polishforests.climate.ColumnClimate;
import pl.polishforests.climate.ColumnClimates;
import pl.polishforests.climate.PolandClimate;
import pl.polishforests.climate.VanillaClimate;
import pl.polishforests.season.Seasons;

/**
 * Step C2: the temperature at a point of the world type "Default + Polish Forests" for {@code /polishforests here}.
 *
 * @param base        base temperature of the column (the vanilla biome it had, or the biome's own)
 * @param source      where the base comes from: {@code column} (column climate of the chunk), {@code own} (a habitat biome
 *                    in a chunk without one, generated before step C2), {@code vanilla} (a vanilla biome)
 * @param temperature temperature at the point without the season
 * @param inSeason    with the seasonal adjustment of Serene Seasons (equal without it)
 */
public record PointClimate(float base, String source, float temperature, float inSeason) {
	/** The temperature at {@code pos} of a level of that world type, whose biome there is {@code biome}. */
	public static PointClimate of(ServerLevel level, Holder<Biome> biome, BlockPos pos) {
		BiomeClimate climate = BiomeClimateAccess.climate(biome.value());
		float base;
		String source;
		if (climate != null && climate.vanillaColumns()) {
			ColumnClimate c = ColumnClimates.at(level, pos);
			base = c != null ? c.base(pos.getX(), pos.getZ()) : climate.baseTemperature();
			source = c != null ? "column" : "own";
		} else {
			// A vanilla biome (no temperature modifier in the forests' neighborhood; the frozen ocean's is ignored here).
			base = biome.value().getBaseTemperature();
			source = "vanilla";
		}
		float t = VanillaClimate.temperature(base, pos.getX(), pos.getY(), pos.getZ(), level.getSeaLevel());
		float season = Seasons.provider().temperatureInSeason(level, biome.value(), pos, t);
		return new PointClimate(base, source, t, season);
	}

	/** Whether it snows (and water freezes) at the point in this season. */
	public boolean snow() {
		return inSeason < PolandClimate.SNOW_THRESHOLD;
	}

	/** The line of {@code here} (lang {@code polishforests.command.vanilla_here_climate}). */
	public Component line() {
		return Component.translatable("polishforests.command.vanilla_here_climate", format(temperature), format(inSeason),
				Component.translatable("polishforests.command.vanilla_climate." + (snow() ? "snow" : "rain"))
						.withStyle(snow() ? ChatFormatting.AQUA : ChatFormatting.BLUE),
				format(base), Component.translatable("polishforests.command.vanilla_climate." + source));
	}

	private static String format(float t) {
		return String.format(Locale.ROOT, "%.3f", t);
	}
}
