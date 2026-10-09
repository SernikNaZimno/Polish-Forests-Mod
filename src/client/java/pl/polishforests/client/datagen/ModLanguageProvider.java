package pl.polishforests.client.datagen;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricLanguageProvider;
import net.minecraft.core.HolderLookup;
import pl.polishforests.command.PolishForestsCommands;
import pl.polishforests.worldgen.habitat.AltitudinalBelts;
import pl.polishforests.worldgen.habitat.HabitatBiome;

/**
 * Language files {@code en_us} and {@code pl_pl} (datagen, step S5): the biome names from {@link HabitatBiome} (Z8), the
 * names of the {@code /polishforests find} targets (every {@link PolishForestsCommands.Target} must have one; the
 * montane belts show the boundary {@link AltitudinalBelts#UPPER_MONTANE}) and the texts of the options screen and the
 * commands. The game shows English with a Polish translation.
 */
abstract class ModLanguageProvider extends FabricLanguageProvider {
	/** Display names of the {@code find} targets other than the montane belts: English and Polish. */
	private static final Map<String, String[]> TARGETS = new LinkedHashMap<>();

	static {
		TARGETS.put("beach", new String[] {"beach", "plaża"});
		TARGETS.put("beskids", new String[] {"Beskids", "Beskidy"});
		TARGETS.put("cliff", new String[] {"sea cliff", "klif"});
		TARGETS.put("coastal_dunes", new String[] {"coastal dunes", "wydmy nadmorskie"});
		TARGETS.put("coastland", new String[] {"coastal lowland", "pobrzeże"});
		TARGETS.put("end_moraine", new String[] {"end moraine ridge", "wał moreny czołowej"});
		TARGETS.put("foothills", new String[] {"foothills", "pogórze"});
		TARGETS.put("headwaters", new String[] {"headwaters", "źródło cieku"});
		TARGETS.put("inland_dunes", new String[] {"inland dunes", "wydmy"});
		TARGETS.put("kettle_pond", new String[] {"kettle pond", "oczko wodne"});
		TARGETS.put("lagoon", new String[] {"coastal lagoon", "zalew przymorski"});
		TARGETS.put("lake", new String[] {"lake", "jezioro"});
		TARGETS.put("moraine_plateau", new String[] {"moraine plateau", "wysoczyzna morenowa"});
		TARGETS.put("mountain_pass", new String[] {"mountain pass", "przełęcz"});
		TARGETS.put("mountain_valley", new String[] {"mountain valley", "dolina górska"});
		TARGETS.put("old_glacial_plain", new String[] {"old glacial plain", "równina staroglacjalna"});
		TARGETS.put("outwash_plain", new String[] {"outwash plain", "sandr"});
		TARGETS.put("oxbow_lake", new String[] {"oxbow lake", "starorzecze"});
		TARGETS.put("peatland", new String[] {"peatland (peat-filled kettle)", "torfowisko (oczko zatorfione)"});
		TARGETS.put("ridge", new String[] {"mountain ridge", "grzbiet górski"});
		TARGETS.put("river", new String[] {"river", "rzeka"});
		TARGETS.put("river_mouth", new String[] {"river mouth", "ujście rzeki do morza"});
		TARGETS.put("river_valley", new String[] {"river valley", "dolina rzeki"});
		TARGETS.put("sea", new String[] {"sea", "morze"});
		TARGETS.put("stream", new String[] {"stream", "potok"});
		TARGETS.put("summit", new String[] {"summit", "szczyt"});
		TARGETS.put("tunnel_valley", new String[] {"glacial tunnel valley", "rynna polodowcowa"});
		TARGETS.put("tunnel_valley_lake", new String[] {"tunnel valley lake", "jezioro rynnowe"});
		TARGETS.put("valley_slope", new String[] {"river valley slope", "zbocze doliny rzecznej"});
	}

	private final boolean polish;

	ModLanguageProvider(FabricPackOutput output, String code, CompletableFuture<HolderLookup.Provider> registries) {
		super(output, code, registries);
		this.polish = code.equals("pl_pl");
	}

	@Override
	public void generateTranslations(HolderLookup.Provider registries, TranslationBuilder builder) {
		for (HabitatBiome b : HabitatBiome.values()) {
			builder.add("biome.polishforests." + b.id(), polish ? b.polishName() : b.englishName());
		}
		String boundary = String.format(Locale.ROOT, "%.0f m", AltitudinalBelts.UPPER_MONTANE);
		for (PolishForestsCommands.Target t : PolishForestsCommands.Target.values()) {
			String name = switch (t) {
				case LOWER_MONTANE -> polish ? "regiel dolny (poniżej " + boundary + ")" : "lower montane belt (below " + boundary + ")";
				case UPPER_MONTANE -> polish ? "regiel górny (od " + boundary + ")" : "upper montane belt (from " + boundary + ")";
				default -> {
					String[] names = TARGETS.get(t.id());
					if (names == null) {
						throw new IllegalStateException("No display name for the target " + t.id());
					}
					yield names[polish ? 1 : 0];
				}
			};
			builder.add("polishforests.target." + t.id(), name);
		}
		if (polish) {
			polish(builder);
		} else {
			english(builder);
		}
	}

	private static void english(TranslationBuilder b) {
		b.add("generator.polishforests.poland", "Poland (1:1 Scale)");
		b.add("generator.polishforests.poland_gameplay", "Poland (Gameplay Scale)");
		b.add("polishforests.options.title", "Poland World Customization");
		b.add("polishforests.options.scale", "Scale");
		b.add("polishforests.options.scale.realistic", "Real (1:1)");
		b.add("polishforests.options.scale.gameplay", "Gameplay-Friendly");
		b.add("polishforests.options.region_scale", "Region Size: %s%% (about %s km)");
		b.add("polishforests.options.landscape", "Landscape");
		b.add("polishforests.options.landscape.today", "Present-Day Poland");
		b.add("polishforests.options.landscape.natural", "Natural Vegetation");
		b.add("polishforests.options.managed_share", "Managed Forests: %s%%");
		b.add("polishforests.options.alien_species", "Alien & Invasive Species");
		b.add("polishforests.options.note", "Real scale: landscapes at their true size and elevation. Gameplay scale: "
				+ "landscapes about 1.4 km across, lower mountains and a shorter world, which is much easier on your computer. "
				+ "The landscape mode already chooses the biomes; managed forests and alien species will take effect in future "
				+ "versions of the mod.");
		b.add("polishforests.options.landscape.natural.tooltip", "The forest that would grow without people, on almost the "
				+ "whole land.");
		b.add("polishforests.options.landscape.today.tooltip", "About 30% forest, mostly pine forests on sand, with meadows "
				+ "and fallow fields; the river banks stay overgrown.");
		b.add("polishforests.command.searching", "Searching for %s…");
		b.add("polishforests.command.found", "Found %s %s m away: %s");
		b.add("polishforests.command.not_found", "No %s found within %s km");
		b.add("polishforests.command.here", "Here: %s, ground at %s m above sea level, substrate: %s, water: %s, landforms: %s");
		b.add("polishforests.command.not_poland", "This command only works in a Poland world");
		b.add("polishforests.command.unknown_target", "Unknown target: %s. Use /polishforests list to see all targets");
		b.add("polishforests.command.elevation_range", "terrain at %s–%s m above sea level");
		b.add("polishforests.command.highest_name", "highest point within %s km");
		b.add("polishforests.command.highest_found", "highest point (%s m above sea level)");
		b.add("polishforests.kind.landscape", "Landscapes");
		b.add("polishforests.kind.belt", "Mountain belts");
		b.add("polishforests.kind.water", "Water and wetlands");
		b.add("polishforests.kind.coast", "Coast");
		b.add("polishforests.kind.landform", "Landforms");
	}

	private static void polish(TranslationBuilder b) {
		b.add("generator.polishforests.poland", "Polska (skala 1:1)");
		b.add("generator.polishforests.poland_gameplay", "Polska (skala rozgrywki)");
		b.add("polishforests.options.title", "Opcje generowania: Polska");
		b.add("polishforests.options.scale", "Skala");
		b.add("polishforests.options.scale.realistic", "Rzeczywista (1:1)");
		b.add("polishforests.options.scale.gameplay", "Przyjazna rozgrywce");
		b.add("polishforests.options.region_scale", "Rozmiar regionów: %s%% (ok. %s km)");
		b.add("polishforests.options.landscape", "Krajobraz");
		b.add("polishforests.options.landscape.today", "Dzisiejsza Polska");
		b.add("polishforests.options.landscape.natural", "Roślinność naturalna");
		b.add("polishforests.options.managed_share", "Lasy gospodarcze: %s%%");
		b.add("polishforests.options.alien_species", "Gatunki obce i inwazyjne");
		b.add("polishforests.options.note", "Skala rzeczywista: krajobrazy w prawdziwych rozmiarach i wysokościach. Skala "
				+ "rozgrywki: krajobrazy ok. 1,4 km, niższe góry i niższy świat, co wyraźnie odciąża komputer. Tryb krajobrazu "
				+ "wybiera już biomy; lasy gospodarcze i gatunki obce zaczną działać w kolejnych wersjach moda.");
		b.add("polishforests.options.landscape.natural.tooltip", "Las, który rósłby bez ludzi, prawie na całym lądzie.");
		b.add("polishforests.options.landscape.today.tooltip", "Ok. 30% lasów, głównie bory na piaskach, łąki i pola "
				+ "odłogowane; brzegi rzek zostają zarośnięte.");
		b.add("polishforests.command.searching", "Szukam: %s…");
		b.add("polishforests.command.found", "Znaleziono: %s, odległość %s m: %s");
		b.add("polishforests.command.not_found", "Nie znaleziono: %s w promieniu %s km");
		b.add("polishforests.command.here", "Tutaj: %s, grunt %s m n.p.m., podłoże %s, wody: %s, formy: %s");
		b.add("polishforests.command.not_poland", "Ta komenda działa tylko w świecie typu Polska");
		b.add("polishforests.command.unknown_target", "Nieznany cel: %s. Lista celów: /polishforests list");
		b.add("polishforests.command.elevation_range", "teren %s–%s m n.p.m.");
		b.add("polishforests.command.highest_name", "najwyższy punkt w promieniu %s km");
		b.add("polishforests.command.highest_found", "najwyższy punkt (%s m n.p.m.)");
		b.add("polishforests.kind.landscape", "Krajobrazy");
		b.add("polishforests.kind.belt", "Piętra górskie");
		b.add("polishforests.kind.water", "Wody i mokradła");
		b.add("polishforests.kind.coast", "Wybrzeże morskie");
		b.add("polishforests.kind.landform", "Formy terenu");
		b.add("modmenu.descriptionTranslation.polishforests", "Proceduralne krajobrazy Polski w skali 1:1 z przyrodniczo "
				+ "wiernymi lasami, florą i fauną oraz integracją z porami roku Serene Seasons.");
	}

	@Override
	public String getName() {
		return "Polish Forests language (" + (polish ? "pl_pl" : "en_us") + ")";
	}

	static final class English extends ModLanguageProvider {
		English(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
			super(output, "en_us", registries);
		}
	}

	static final class Polish extends ModLanguageProvider {
		Polish(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
			super(output, "pl_pl", registries);
		}
	}
}
