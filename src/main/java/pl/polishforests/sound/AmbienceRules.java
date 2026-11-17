package pl.polishforests.sound;

import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;
import pl.polishforests.season.SubSeason;
import pl.polishforests.worldgen.habitat.HabitatBiome;

/**
 * The rules of the forest ambience (step FA4, decision PW-16, docs/04-pionowy-wycinek.md "Projekt FA4"), pure functions
 * of the forest, the sub-season, the time of day and a random source, so the unit tests can count thousands of events
 * ({@code ForestAmbienceTest}) and the server ({@link ForestAmbience}) only adds the places and the playing.
 *
 * <ul>
 * <li>Cadence: one event every {@value #MIN_INTERVAL}-{@value #MAX_INTERVAL} ticks (10-40 s), shorter at dawn and dusk
 * ({@link #interval}); at dawn of April to June (the dawn chorus, PW-16) a second voice follows within 1-3 s
 * ({@link #chorus}).
 * <li>Silence: an event can stay silent, more often in autumn and most in winter ({@link #silence}); the voices
 * themselves thin out in winter (only the tits, the goldcrest, the woodpecker, the jay and the owl, {@link ForestVoice}).
 * <li>Night: only the nocturnal voices (the tawny owl, the common frog at its spawning).
 * </ul>
 */
public final class AmbienceRules {
	public static final int MIN_INTERVAL = 200;
	public static final int MAX_INTERVAL = 800;

	/** The parts of the day (ticks of the day: 0 is 6:00, 6000 noon, 18000 midnight). */
	public enum Phase {
		/** 4:30-8:00. */
		DAWN,
		/** 8:00-16:30. */
		DAY,
		/** 16:30-19:30. */
		DUSK,
		/** 19:30-4:30. */
		NIGHT
	}

	/** The kind of forest the voice is in. */
	public enum ForestKind {
		BROADLEAF, MIXED, CONIFER
	}

	private AmbienceRules() {
	}

	/** The part of the day at a time of day (ticks, any day). */
	public static Phase phase(long timeOfDay) {
		int t = (int) Math.floorMod(timeOfDay, 24000L);
		if (t >= 22500 || t < 2000) {
			return Phase.DAWN;
		}
		if (t < 10500) {
			return Phase.DAY;
		}
		return t < 13500 ? Phase.DUSK : Phase.NIGHT;
	}

	/** The kind of a forest habitat of the mod, or null for a habitat that is no forest. */
	public static @Nullable ForestKind kind(@Nullable HabitatBiome b) {
		if (b == null || !b.isForest()) {
			return null;
		}
		return switch (b) {
			case MIXED_FOREST, MIXED_PINE_FOREST, UPLAND_FIR_FOREST -> ForestKind.MIXED;
			case DRY_PINE_FOREST, FRESH_PINE_FOREST, COASTAL_PINE_FOREST, MOIST_PINE_FOREST, BOG_WOODLAND, MONTANE_SPRUCE_FOREST,
					DWARF_PINE_SCRUB -> ForestKind.CONIFER;
			default -> ForestKind.BROADLEAF;
		};
	}

	/** A wet forest: alder carr, the floodplain forests, the gray alder forest, the bog woodland and the moist pine forest. */
	public static boolean wet(@Nullable HabitatBiome b) {
		return b != null && switch (b) {
			case ALDER_CARR, ASH_ALDER_FOREST, WILLOW_POPLAR_FOREST, ELM_ASH_FOREST, GRAY_ALDER_FOREST, BOG_WOODLAND, MOIST_PINE_FOREST -> true;
			default -> false;
		};
	}

	/** Ticks to the next event: dawn 200-360, dusk 240-480, day 400-800, night 480-800. */
	public static int interval(Phase phase, RandomSource random) {
		return switch (phase) {
			case DAWN -> 200 + random.nextInt(161);
			case DUSK -> 240 + random.nextInt(241);
			case DAY -> 400 + random.nextInt(401);
			case NIGHT -> 480 + random.nextInt(321);
		};
	}

	/**
	 * The chance that an event stays silent: December 0.55, January 0.6, February 0.45 (the tits begin to sing), November
	 * 0.3, September and October 0.15, August 0.1, else 0.
	 */
	public static float silence(SubSeason sub) {
		return switch (sub) {
			case EARLY_WINTER -> 0.55F;
			case MID_WINTER -> 0.6F;
			case LATE_WINTER -> 0.45F;
			case LATE_AUTUMN -> 0.3F;
			case EARLY_AUTUMN, MID_AUTUMN -> 0.15F;
			case LATE_SUMMER -> 0.1F;
			default -> 0;
		};
	}

	/** Whether a second voice follows (the dawn chorus of April to June, PW-16). */
	public static boolean chorus(Phase phase, SubSeason sub) {
		return phase == Phase.DAWN && (sub == SubSeason.MID_SPRING || sub == SubSeason.LATE_SPRING || sub == SubSeason.EARLY_SUMMER);
	}

	/**
	 * The voice of one event in a forest habitat, or null for silence (no forest, the silence draw, or no voice of the
	 * season, part of the day and forest).
	 */
	public static @Nullable ForestVoice choose(@Nullable HabitatBiome habitat, SubSeason sub, Phase phase, RandomSource random) {
		ForestKind kind = kind(habitat);
		if (kind == null) {
			return null;
		}
		if (random.nextFloat() < silence(sub)) {
			return null;
		}
		boolean wet = wet(habitat);
		float total = 0;
		for (ForestVoice v : ForestVoice.values()) {
			total += v.weight(phase, kind, wet, sub);
		}
		if (total <= 0) {
			return null;
		}
		float r = random.nextFloat() * total;
		ForestVoice last = null;
		for (ForestVoice v : ForestVoice.values()) {
			float w = v.weight(phase, kind, wet, sub);
			if (w <= 0) {
				continue;
			}
			last = v;
			r -= w;
			if (r < 0) {
				return v;
			}
		}
		return last;
	}
}
