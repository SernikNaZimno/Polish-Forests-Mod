package pl.polishforests.sound;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import org.jspecify.annotations.Nullable;
import pl.polishforests.PolishForests;

/**
 * The sound events of the mod (step FA4, docs/04-pionowy-wycinek.md "Projekt FA4", G.62-d). Every event has its files,
 * variants and subtitle in {@code assets/polishforests/sounds.json}, made with the files themselves by tools/sound
 * (synthesized voices, CC BY 4.0, license manifest {@code sounds/manifest.json}); the ids here and the events of
 * tools/sound/recipes.py must be the same list ({@code ModSoundsTest}). Sound events are not saved in worlds, so the
 * registry snapshot does not list them.
 */
public final class ModSounds {
	private static final List<SoundEvent> ALL = new ArrayList<>();
	private static final List<Subtitle> SUBTITLES = new ArrayList<>();

	/** The subtitle of an event (key {@code subtitles.polishforests.<event>}) in English and Polish, for the language files. */
	public record Subtitle(String key, String english, String polish) {
	}

	public static final SoundEvent CHAFFINCH = event("ambient.forest.chaffinch", "Chaffinch sings", "Zięba śpiewa");
	public static final SoundEvent BLACKBIRD = event("ambient.forest.blackbird", "Blackbird sings", "Kos śpiewa");
	public static final SoundEvent ROBIN = event("ambient.forest.robin", "Robin sings", "Rudzik śpiewa");
	public static final SoundEvent GREAT_TIT = event("ambient.forest.great_tit", "Great tit sings", "Bogatka śpiewa");
	public static final SoundEvent COAL_TIT = event("ambient.forest.coal_tit", "Coal tit sings", "Sosnówka śpiewa");
	public static final SoundEvent GOLDCREST = event("ambient.forest.goldcrest", "Goldcrest sings", "Mysikrólik śpiewa");
	public static final SoundEvent CUCKOO = event("ambient.forest.cuckoo", "Cuckoo calls", "Kukułka kuka");
	public static final SoundEvent WOODPECKER_DRUM = event("ambient.forest.woodpecker_drum", "Woodpecker drums", "Dzięcioł bębni");
	public static final SoundEvent WOODPECKER_CALL = event("ambient.forest.woodpecker_call", "Woodpecker calls", "Dzięcioł się odzywa");
	public static final SoundEvent TAWNY_OWL = event("ambient.forest.tawny_owl", "Tawny owl hoots", "Puszczyk pohukuje");
	public static final SoundEvent JAY = event("ambient.forest.jay", "Jay screeches", "Sójka skrzeczy");
	public static final SoundEvent COMMON_FROG_CROAK = event("entity.common_frog.croak", "Common frog croaks", "Żaba trawna rechocze");
	public static final SoundEvent ROE_DEER_BARK = event("entity.roe_deer.bark", "Roe deer barks", "Sarna szczeka");
	public static final SoundEvent WILD_BOAR_GRUNT = event("entity.wild_boar.grunt", "Wild boar grunts", "Dzik chrząka");
	public static final SoundEvent WILD_BOAR_SNORT = event("entity.wild_boar.snort", "Wild boar snorts", "Dzik prycha");

	private static boolean registered;

	private ModSounds() {
	}

	private static SoundEvent event(String path, String english, String polish) {
		SoundEvent e = SoundEvent.createVariableRangeEvent(PolishForests.id(path));
		ALL.add(e);
		SUBTITLES.add(new Subtitle("subtitles.polishforests." + path, english, polish));
		return e;
	}

	/** The subtitles of every event, in the order of {@link #all()}. */
	public static List<Subtitle> subtitles() {
		return Collections.unmodifiableList(SUBTITLES);
	}

	/** Registers the sound events in the game registry (once, at mod initialization). */
	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		for (SoundEvent e : ALL) {
			Registry.register(BuiltInRegistries.SOUND_EVENT, e.location(), e);
		}
	}

	/** Every sound event of the mod, in the order of tools/sound/recipes.py. */
	public static List<SoundEvent> all() {
		return Collections.unmodifiableList(ALL);
	}

	/** The event with an id path, or null. */
	public static @Nullable SoundEvent byPath(String path) {
		Identifier id = PolishForests.id(path);
		for (SoundEvent e : ALL) {
			if (e.location().equals(id)) {
				return e;
			}
		}
		return null;
	}
}
