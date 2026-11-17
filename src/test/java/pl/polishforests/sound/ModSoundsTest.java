package pl.polishforests.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.sounds.SoundEvent;
import org.junit.jupiter.api.Test;

/**
 * Step FA4 (docs/04-pionowy-wycinek.md "Projekt FA4", G.62-d): the sound events of {@link ModSounds}, the sounds.json and
 * the license manifest of tools/sound, and the subtitles of both languages are one list. checkSound (tools/sound/check.py)
 * compares the files with the generator; this test ties them to the Java side.
 */
class ModSoundsTest {
	private static final Path ASSETS = Path.of("src/main/resources/assets/polishforests");
	private static final Path LANG = Path.of("src/main/generated/assets/polishforests/lang");

	private static JsonObject read(Path p) throws IOException {
		return JsonParser.parseString(Files.readString(p)).getAsJsonObject();
	}

	@Test
	void eventsMatchSoundsJson() throws IOException {
		JsonObject sounds = read(ASSETS.resolve("sounds.json"));
		List<String> ids = new ArrayList<>();
		for (SoundEvent e : ModSounds.all()) {
			assertEquals("polishforests", e.location().getNamespace());
			ids.add(e.location().getPath());
		}
		assertEquals(new ArrayList<>(sounds.keySet()), ids, "the events of ModSounds and tools/sound/recipes.py, in order");
		assertEquals(ids.size(), new HashSet<>(ids).size());
		for (int i = 0; i < ids.size(); i++) {
			JsonObject event = sounds.getAsJsonObject(ids.get(i));
			assertEquals(ModSounds.subtitles().get(i).key(), event.get("subtitle").getAsString());
			assertTrue(event.getAsJsonArray("sounds").size() >= 2, ids.get(i) + ": at least two variants");
			assertSame(ModSounds.all().get(i), ModSounds.byPath(ids.get(i)));
		}
	}

	@Test
	void everyFileHasALicense() throws IOException {
		JsonObject sounds = read(ASSETS.resolve("sounds.json"));
		JsonObject manifest = read(ASSETS.resolve("sounds/manifest.json"));
		Set<String> listed = new HashSet<>();
		for (JsonElement el : manifest.getAsJsonArray("files")) {
			JsonObject f = el.getAsJsonObject();
			for (String field : List.of("file", "event", "source", "author", "license", "license_url")) {
				assertTrue(f.has(field) && !f.get(field).getAsString().isBlank(), f + ": " + field);
			}
			String license = f.get("license").getAsString();
			assertTrue(license.equals("CC BY 4.0") || license.equals("CC0 1.0"), f.get("file") + ": license " + license);
			listed.add(f.get("file").getAsString());
		}
		Set<String> files = new HashSet<>();
		for (String event : sounds.keySet()) {
			for (JsonElement s : sounds.getAsJsonObject(event).getAsJsonArray("sounds")) {
				String name = s.getAsString();
				assertTrue(name.startsWith("polishforests:"), name);
				String file = name.substring("polishforests:".length()) + ".ogg";
				files.add(file);
				assertTrue(Files.isRegularFile(ASSETS.resolve("sounds").resolve(file)), file + " exists (./gradlew generateSound)");
			}
		}
		assertEquals(files, listed, "the manifest lists exactly the files of sounds.json");
	}

	@Test
	void subtitlesInBothLanguages() throws IOException {
		JsonObject en = read(LANG.resolve("en_us.json"));
		JsonObject pl = read(LANG.resolve("pl_pl.json"));
		assertEquals(ModSounds.all().size(), ModSounds.subtitles().size());
		for (ModSounds.Subtitle s : ModSounds.subtitles()) {
			assertNotNull(en.get(s.key()), s.key() + " in en_us");
			assertNotNull(pl.get(s.key()), s.key() + " in pl_pl");
			assertEquals(s.english(), en.get(s.key()).getAsString());
			assertEquals(s.polish(), pl.get(s.key()).getAsString());
		}
		assertNotNull(en.get("gamerule.polishforests.forest_ambience"));
		assertNotNull(pl.get("gamerule.polishforests.forest_ambience"));
	}

	@Test
	void voicesUseTheEventsOfTheMod() {
		for (ForestVoice v : ForestVoice.values()) {
			assertTrue(ModSounds.all().contains(v.sound()), v + " plays an event of ModSounds");
		}
	}
}
