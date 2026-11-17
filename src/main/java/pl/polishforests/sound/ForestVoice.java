package pl.polishforests.sound;

import java.util.function.Supplier;
import net.minecraft.sounds.SoundEvent;
import pl.polishforests.season.SubSeason;
import pl.polishforests.sound.AmbienceRules.ForestKind;
import pl.polishforests.sound.AmbienceRules.Phase;

/**
 * The voices of the forest ambience (step FA4, decision PW-16, docs/04-pionowy-wycinek.md "Projekt FA4"): which sound,
 * in which months, how often in each part of the day and in each kind of forest. The weights are relative within one
 * draw of {@link AmbienceRules#choose}; a weight 0 means never. Months follow the phenology of the species in Poland
 * (field guides): the songbirds sing from March to July (the robin again in autumn), the tits and the goldcrest call the
 * whole year (the only songbirds heard in winter, PW-16), the cuckoo from mid-April to June, the great spotted woodpecker
 * drums from January to May (PW-16 "dzięcioł bębniący I–V"), the tawny owl hoots at night the whole year, the common
 * frog calls at its spawning in March and April at dusk and at night, only in wet forests. New voices only at the end
 * (the counters of the census use the ordinal).
 */
public enum ForestVoice {
	CHAFFINCH(() -> ModSounds.CHAFFINCH, false, false, months(3, 7), new float[] {1.0F, 0.6F, 0.35F, 0}, new float[] {1.0F, 1.0F, 0.8F}, 48),
	BLACKBIRD(() -> ModSounds.BLACKBIRD, false, false, months(3, 7), new float[] {1.0F, 0.25F, 0.9F, 0}, new float[] {1.0F, 0.8F, 0.3F}, 56),
	ROBIN(() -> ModSounds.ROBIN, false, false, months(3, 7) | months(9, 10), new float[] {0.9F, 0.3F, 0.8F, 0}, new float[] {1.0F, 1.0F, 0.6F}, 48),
	GREAT_TIT(() -> ModSounds.GREAT_TIT, false, false, months(1, 12), new float[] {0.8F, 0.6F, 0.25F, 0}, new float[] {1.0F, 1.0F, 0.5F}, 48),
	COAL_TIT(() -> ModSounds.COAL_TIT, false, false, months(1, 12), new float[] {0.7F, 0.6F, 0.2F, 0}, new float[] {0.1F, 0.6F, 1.0F}, 40),
	GOLDCREST(() -> ModSounds.GOLDCREST, false, false, months(1, 12), new float[] {0.5F, 0.5F, 0.2F, 0}, new float[] {0.05F, 0.5F, 1.0F}, 32),
	CUCKOO(() -> ModSounds.CUCKOO, false, false, months(4, 6), new float[] {0.6F, 0.45F, 0.3F, 0}, new float[] {1.0F, 1.0F, 1.0F}, 96),
	WOODPECKER_DRUM(() -> ModSounds.WOODPECKER_DRUM, false, false, months(1, 5), new float[] {0.7F, 0.5F, 0.1F, 0}, new float[] {1.0F, 1.0F, 0.7F}, 80),
	WOODPECKER_CALL(() -> ModSounds.WOODPECKER_CALL, false, false, months(1, 12), new float[] {0.3F, 0.35F, 0.15F, 0}, new float[] {1.0F, 1.0F, 0.8F}, 48),
	TAWNY_OWL(() -> ModSounds.TAWNY_OWL, true, false, months(1, 12), new float[] {0, 0, 0.1F, 1.0F}, new float[] {1.0F, 1.0F, 0.7F}, 80),
	JAY(() -> ModSounds.JAY, false, false, months(1, 12), new float[] {0.15F, 0.25F, 0.1F, 0}, new float[] {1.0F, 1.0F, 0.8F}, 56),
	COMMON_FROG(() -> ModSounds.COMMON_FROG_CROAK, true, true, months(3, 4), new float[] {0, 0.15F, 0.7F, 1.0F}, new float[] {1.0F, 1.0F, 1.0F}, 40);

	private final Supplier<SoundEvent> sound;
	private final boolean nocturnal;
	private final boolean wetOnly;
	private final int months;
	private final float[] phases;
	private final float[] kinds;
	private final int range;

	ForestVoice(Supplier<SoundEvent> sound, boolean nocturnal, boolean wetOnly, int months, float[] phases, float[] kinds, int range) {
		this.sound = sound;
		this.nocturnal = nocturnal;
		this.wetOnly = wetOnly;
		this.months = months;
		this.phases = phases;
		this.kinds = kinds;
		this.range = range;
	}

	/** A bit mask of the months from {@code first} to {@code last} (1 = January). */
	static int months(int first, int last) {
		int m = 0;
		for (int i = first; i <= last; i++) {
			m |= 1 << i;
		}
		return m;
	}

	public SoundEvent sound() {
		return sound.get();
	}

	/** A voice of the night: the only kind heard in {@link Phase#NIGHT}. */
	public boolean nocturnal() {
		return nocturnal;
	}

	/** Heard only in the wet forests (alder carr, floodplain forests, bog woodland). */
	public boolean wetOnly() {
		return wetOnly;
	}

	/** Whether the voice is heard in the month of a sub-season. */
	public boolean inSeason(SubSeason sub) {
		return (months & (1 << sub.month().getValue())) != 0;
	}

	/** The weight of the voice in a draw (0 when it is not heard then and there). */
	public float weight(Phase phase, ForestKind kind, boolean wet, SubSeason sub) {
		if (!inSeason(sub) || (wetOnly && !wet) || (phase == Phase.NIGHT && !nocturnal)) {
			return 0;
		}
		return phases[phase.ordinal()] * kinds[kind.ordinal()];
	}

	/** How far it carries (blocks): the server's volume makes the range at least this. */
	public int range() {
		return range;
	}
}
