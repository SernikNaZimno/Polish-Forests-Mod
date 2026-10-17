package pl.polishforests.client.datagen;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricLanguageProvider;
import net.minecraft.core.HolderLookup;
import pl.polishforests.block.ModBlocks;
import pl.polishforests.block.TreeFoliage;
import pl.polishforests.block.TreeTrunk;
import pl.polishforests.command.PolishForestsCommands;
import pl.polishforests.worldgen.habitat.AltitudinalBelts;
import pl.polishforests.worldgen.habitat.Association;
import pl.polishforests.worldgen.habitat.HabitatBiome;
import pl.polishforests.worldgen.habitat.Soil;
import pl.polishforests.worldgen.habitat.Species;
import pl.polishforests.worldgen.habitat.Zone;

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
		TARGETS.put("peatland", new String[] {"peatland (raised bog or fen)", "torfowisko (wysokie, niskie lub przejściowe)"});
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

	/** Step S9: names of the zones ({@code find zone}, {@code here}): English and Polish. */
	private static final Map<String, String[]> ZONES = new LinkedHashMap<>();
	/** Step S9: names of the associations and forms ({@code here}). */
	private static final Map<String, String[]> ASSOCIATIONS = new LinkedHashMap<>();
	/** Step S9: names of the soils ({@code here}). */
	private static final Map<String, String[]> SOILS = new LinkedHashMap<>();
	/** Step S9: names of the tree and shrub species ({@code here}). */
	private static final Map<String, String[]> SPECIES = new LinkedHashMap<>();

	static {
		ZONES.put("none", new String[] {"none", "brak"});
		ZONES.put("channel", new String[] {"river or stream channel", "koryto cieku"});
		ZONES.put("submerged_plants", new String[] {"pondweed beds", "łąki podwodne rdestnic"});
		ZONES.put("floating_leaved_plants", new String[] {"water lilies", "grzybienie i grążele"});
		ZONES.put("reedbed", new String[] {"reedbed in the water", "szuwar w wodzie"});
		ZONES.put("shore_reedbed", new String[] {"sedge and reed canary grass beds on the shore", "szuwar turzycowy i mozgowy na brzegu"});
		ZONES.put("point_bar", new String[] {"point bar", "łacha na brzegu wypukłym"});
		ZONES.put("willow_scrub", new String[] {"riverside willow scrub", "wiklina nadrzeczna"});
		ZONES.put("herb_fringe", new String[] {"tall herb fringe", "okrajek ziołoroślowy"});
		ZONES.put("tall_herbs", new String[] {"riverside tall herbs", "ziołorośla nadrzeczne"});
		ZONES.put("riverside_willows", new String[] {"riverside white and crack willows", "wierzby nadrzeczne (biała i krucha)"});
		ZONES.put("willow_carr", new String[] {"willow carr", "łozowisko"});
		ZONES.put("gravel_bar", new String[] {"gravel bar", "kamieniec"});
		ZONES.put("montane_tall_herbs", new String[] {"montane streamside tall herbs", "ziołorośla górskie nad potokiem"});
		ZONES.put("spring_area", new String[] {"spring area", "źródlisko"});
		ZONES.put("floating_mat", new String[] {"floating mat", "pło"});
		ZONES.put("shore_alders", new String[] {"belt of alders or birches on the shore", "pas olszy lub brzozy przy brzegu"});
		ZONES.put("strandline", new String[] {"strandline", "pas kidziny"});
		ZONES.put("embryo_dune", new String[] {"embryo dune", "wydma inicjalna"});
		ZONES.put("cliff_face", new String[] {"cliff face", "ściana klifu"});
		ZONES.put("cliff_top", new String[] {"cliff top scrub", "zarośla na krawędzi klifu"});
		ZONES.put("timberline", new String[] {"timberline", "górna granica lasu"});
		ZONES.put("tree_row", new String[] {"row of alders along a stream", "szpaler olszy nad ciekiem"});

		ASSOCIATIONS.put("typical", new String[] {"typical", "postać typowa"});
		ASSOCIATIONS.put("salicetum_albae", new String[] {"white willow forest (Salicetum albae)", "łęg wierzbowy (Salicetum albae)"});
		ASSOCIATIONS.put("populetum_albae", new String[] {"white poplar forest (Populetum albae)", "łęg topolowy (Populetum albae)"});
		ASSOCIATIONS.put("carici_remotae_fraxinetum", new String[] {"submontane ash riparian forest (Carici remotae-Fraxinetum)",
				"podgórski łęg jesionowy (Carici remotae-Fraxinetum)"});
		ASSOCIATIONS.put("spring_fed", new String[] {"spring-fed form", "postać źródliskowa"});
		ASSOCIATIONS.put("spring_fen", new String[] {"swamp alder wood (Caltho laetae-Alnetum)", "bagienna olszyna górska (Caltho laetae-Alnetum)"});
		ASSOCIATIONS.put("sycamore_ravine_forest", new String[] {"sycamore ravine forest (Lunario-Aceretum)", "jaworzyna z miesiącznicą (Lunario-Aceretum)"});
		ASSOCIATIONS.put("gully_tall_herbs", new String[] {"tall herbs of gullies", "ziołorośla żlebowe"});
		ASSOCIATIONS.put("abieti_piceetum", new String[] {"fir-spruce forest (Abieti-Piceetum)", "dolnoreglowy bór jodłowo-świerkowy (Abieti-Piceetum)"});
		ASSOCIATIONS.put("acidophilous", new String[] {"acidophilous form", "postać kwaśna"});
		ASSOCIATIONS.put("stunted", new String[] {"stunted form", "postać karłowata"});
		ASSOCIATIONS.put("shingle", new String[] {"shingle beach", "plaża kamienista"});
		ASSOCIATIONS.put("windswept", new String[] {"windswept form", "postać wiatrowa"});
		ASSOCIATIONS.put("slope", new String[] {"slope form", "postać zboczowa"});
		ASSOCIATIONS.put("oxbow_lake", new String[] {"by an oxbow lake", "przy starorzeczu"});
		ASSOCIATIONS.put("spit", new String[] {"on a spit", "na mierzei"});
		ASSOCIATIONS.put("lobelia_lake", new String[] {"lobelia lake", "jezioro lobeliowe"});
		ASSOCIATIONS.put("backswamp", new String[] {"backswamp", "zastoisko w dnie doliny"});

		SOILS.put("initial_podzol", new String[] {"initial podzol", "gleba bielicowa inicjalna"});
		SOILS.put("podzol", new String[] {"podzol", "gleba bielicowa"});
		SOILS.put("rusty_soil", new String[] {"rusty soil", "gleba rdzawa"});
		SOILS.put("sandy_gleysol", new String[] {"sandy gley soil", "gleba glejowa piaszczysta"});
		SOILS.put("bog_peat", new String[] {"bog peat", "torf wysoki"});
		SOILS.put("fen_peat", new String[] {"fen peat", "torf niski"});
		SOILS.put("acid_brown_soil", new String[] {"acid brown soil", "gleba brunatna kwaśna"});
		SOILS.put("brown_soil", new String[] {"brown soil", "gleba brunatna"});
		SOILS.put("beech_brown_soil", new String[] {"brown soil of beech forests", "gleba brunatna buczyny"});
		SOILS.put("muck", new String[] {"muck soil", "gleba murszowa"});
		SOILS.put("light_alluvial_soil", new String[] {"light alluvial soil", "mada lekka"});
		SOILS.put("heavy_alluvial_soil", new String[] {"heavy alluvial soil", "mada ciężka"});
		SOILS.put("gravelly_alluvial_soil", new String[] {"gravelly alluvial soil", "mada żwirowa"});
		SOILS.put("mountain_brown_soil", new String[] {"mountain brown soil", "gleba brunatna górska"});
		SOILS.put("mountain_podzol", new String[] {"mountain podzol", "gleba bielicowa górska"});
		SOILS.put("ranker", new String[] {"ranker", "ranker"});
		SOILS.put("dune_sand", new String[] {"dune sand", "piasek wydmowy"});
		SOILS.put("beach_sand", new String[] {"beach sand", "piasek plażowy"});
		SOILS.put("till", new String[] {"bare till", "odsłonięta glina zwałowa"});
		SOILS.put("channel_bed", new String[] {"river bed", "dno koryta rzeki"});
		SOILS.put("stream_bed", new String[] {"stream bed", "dno potoku"});
		SOILS.put("lake_bed", new String[] {"lake bed", "dno jeziora"});
		SOILS.put("dystrophic_lake_bed", new String[] {"dystrophic lake bed", "dno jeziora dystroficznego"});
		SOILS.put("sea_bed", new String[] {"sea bed", "dno morza"});
		SOILS.put("lagoon_bed", new String[] {"lagoon bed", "dno zalewu"});

		SPECIES.put("scots_pine", new String[] {"Scots pine", "sosna zwyczajna"});
		SPECIES.put("spruce", new String[] {"Norway spruce", "świerk pospolity"});
		SPECIES.put("fir", new String[] {"silver fir", "jodła pospolita"});
		SPECIES.put("birch", new String[] {"birch", "brzoza"});
		SPECIES.put("oak", new String[] {"pedunculate oak", "dąb szypułkowy"});
		SPECIES.put("beech", new String[] {"European beech", "buk zwyczajny"});
		SPECIES.put("hornbeam", new String[] {"hornbeam", "grab zwyczajny"});
		SPECIES.put("linden", new String[] {"small-leaved linden", "lipa drobnolistna"});
		SPECIES.put("ash", new String[] {"European ash", "jesion wyniosły"});
		SPECIES.put("elm", new String[] {"elm", "wiąz"});
		SPECIES.put("norway_maple", new String[] {"Norway maple", "klon zwyczajny"});
		SPECIES.put("sycamore_maple", new String[] {"sycamore maple", "jawor"});
		SPECIES.put("black_alder", new String[] {"black alder", "olsza czarna"});
		SPECIES.put("gray_alder", new String[] {"gray alder", "olsza szara"});
		SPECIES.put("white_willow", new String[] {"white willow", "wierzba biała"});
		SPECIES.put("poplar", new String[] {"poplar", "topola"});
		SPECIES.put("rowan", new String[] {"rowan", "jarząb pospolity"});
		SPECIES.put("sessile_oak", new String[] {"sessile oak", "dąb bezszypułkowy"});
		SPECIES.put("larch", new String[] {"European larch", "modrzew europejski"});
		SPECIES.put("yew", new String[] {"yew", "cis pospolity"});
		SPECIES.put("dwarf_mountain_pine", new String[] {"dwarf mountain pine", "kosodrzewina"});
		SPECIES.put("osier", new String[] {"osier", "wierzba wiciowa"});
		SPECIES.put("hazel", new String[] {"hazel", "leszczyna"});
		SPECIES.put("juniper", new String[] {"juniper", "jałowiec"});
		SPECIES.put("ivy", new String[] {"ivy", "bluszcz"});
		SPECIES.put("alder_buckthorn", new String[] {"alder buckthorn", "kruszyna pospolita"});
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
		names(builder, "polishforests.zone.", ZONES, Arrays.stream(Zone.values()).map(Zone::id));
		names(builder, "polishforests.association.", ASSOCIATIONS,
				Arrays.stream(Association.values()).map(a -> a.name().toLowerCase(Locale.ROOT)));
		names(builder, "polishforests.soil.", SOILS, Arrays.stream(Soil.values()).map(x -> x.name().toLowerCase(Locale.ROOT)));
		names(builder, "polishforests.species.", SPECIES, Arrays.stream(Species.values()).map(Species::id));
		for (TreeTrunk t : TreeTrunk.values()) {
			builder.add(ModBlocks.trunk(t), polish ? t.polishName() : t.englishName());
		}
		// Step V7: leaves, saplings, potted saplings and the LITE Scots pine wood set.
		for (TreeFoliage f : TreeFoliage.values()) {
			builder.add(ModBlocks.leaves(f), polish ? (f.conifer() ? "Igły " : "Liście ") + f.polishGenitive() : f.englishName()
					+ (f.conifer() ? " Needles" : " Leaves"));
			builder.add(ModBlocks.sapling(f), polish ? "Sadzonka " + f.polishGenitive() : f.englishName() + " Sapling");
			builder.add(ModBlocks.potted(f), polish ? "Sadzonka " + f.polishGenitive() + " w doniczce" : "Potted " + f.englishName()
					+ " Sapling");
		}
		builder.add(ModBlocks.pineLog(), polish ? "Kłoda sosnowa" : "Scots Pine Log");
		builder.add(ModBlocks.pineWood(), polish ? "Drewno sosnowe" : "Scots Pine Wood");
		builder.add(ModBlocks.strippedPineLog(), polish ? "Okorowana kłoda sosnowa" : "Stripped Scots Pine Log");
		builder.add(ModBlocks.strippedPineWood(), polish ? "Okorowane drewno sosnowe" : "Stripped Scots Pine Wood");
		builder.add(ModBlocks.pinePlanks(), polish ? "Deski sosnowe" : "Scots Pine Planks");
		builder.add(tagKey(ModBlockTagProvider.PINE_FULL_LOGS.location().getPath()), polish ? "Kłody sosnowe" : "Scots Pine Logs");
		// Item tags (recipe viewers show their names): a species tag is named after the first trunk of the species.
		Map<Species, TreeTrunk> first = new LinkedHashMap<>();
		for (TreeTrunk t : TreeTrunk.values()) {
			first.putIfAbsent(t.species(), t);
		}
		ModBlockTagProvider.speciesTags().forEach((s, tag) -> builder.add(tagKey(tag.location().getPath()),
				polish ? first.get(s).polishName().replaceFirst("^Pień ", "Pnie ") : first.get(s).englishName() + "s"));
		builder.add(tagKey(ModBlockTagProvider.TRUNKS.location().getPath()), polish ? "Cienkie pnie" : "Thin Trunks");
		if (polish) {
			polish(builder);
		} else {
			english(builder);
		}
	}

	/** Translation key of an item tag of the mod. */
	private static String tagKey(String path) {
		return "tag.item.polishforests." + path;
	}

	/** Adds the name of every enum constant; a constant without a name is an error (enums are only appended to). */
	private void names(TranslationBuilder builder, String prefix, Map<String, String[]> names, Stream<String> ids) {
		ids.forEach(id -> {
			String[] n = names.get(id);
			if (n == null) {
				throw new IllegalStateException("No display name for " + prefix + id);
			}
			builder.add(prefix + id, n[polish ? 1 : 0]);
		});
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
				+ "The landscape mode already chooses the biomes, and the managed forest share the stands of the oak-hornbeam and "
				+ "pine forests; alien species will take effect in future versions of the mod.");
		b.add("polishforests.options.landscape.natural.tooltip", "The forest that would grow without people, on almost the "
				+ "whole land.");
		b.add("polishforests.options.landscape.today.tooltip", "About 30% forest, mostly pine forests on sand, with meadows "
				+ "and fallow fields; the river banks stay overgrown.");
		b.add("polishforests.command.searching", "Searching for %s…");
		b.add("polishforests.command.found", "Found %s %s m away: %s");
		b.add("polishforests.command.found_unconfirmed", "Found %s %s m away: %s (the search stopped after %s s, so a "
				+ "nearer place may exist)");
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
		b.add("polishforests.kind.biome", "Biomes (find biome)");
		b.add("polishforests.kind.zone", "Zones (find zone)");
		b.add("polishforests.command.unknown_biome", "Unknown biome: %s. Use /polishforests list to see all biomes");
		b.add("polishforests.command.unknown_zone", "Unknown zone: %s. Use /polishforests list to see all zones");
		b.add("polishforests.command.not_in_mode", "Not present in the %2$s mode: %1$s");
		b.add("polishforests.command.not_in_scale", "Not present in the gameplay scale: %s");
		b.add("polishforests.command.search_failed", "The search for %s failed (details in the log)");
		b.add("polishforests.command.search_timeout", "Gave up searching for %s after %s s");
		b.add("polishforests.command.here_habitat", "Habitat: %s, site type %s, %s, zone: %s, soil: %s, groundwater "
				+ "%s m deep, O %s, P %s, %s °C, trees: %s");
		b.add("polishforests.command.here_no_trees", "none");
		b.add("polishforests.command.here_placeholder", "%s (placeholder until M3)");
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
				+ "wybiera już biomy, a udział lasów gospodarczych drzewostany grądów i borów; gatunki obce zaczną działać "
				+ "w kolejnych wersjach moda.");
		b.add("polishforests.options.landscape.natural.tooltip", "Las, który rósłby bez ludzi, prawie na całym lądzie.");
		b.add("polishforests.options.landscape.today.tooltip", "Ok. 30% lasów, głównie bory na piaskach, łąki i pola "
				+ "odłogowane; brzegi rzek zostają zarośnięte.");
		b.add("polishforests.command.searching", "Szukam: %s…");
		b.add("polishforests.command.found", "Znaleziono: %s, odległość %s m: %s");
		b.add("polishforests.command.found_unconfirmed", "Znaleziono: %s, odległość %s m: %s (szukanie przerwane po %s s, "
				+ "więc może być bliższe miejsce)");
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
		b.add("polishforests.kind.biome", "Biomy (find biome)");
		b.add("polishforests.kind.zone", "Strefy (find zone)");
		b.add("polishforests.command.unknown_biome", "Nieznany biom: %s. Lista biomów: /polishforests list");
		b.add("polishforests.command.unknown_zone", "Nieznana strefa: %s. Lista stref: /polishforests list");
		b.add("polishforests.command.not_in_mode", "Nie występuje w trybie „%2$s”: %1$s");
		b.add("polishforests.command.not_in_scale", "Nie występuje w skali rozgrywki: %s");
		b.add("polishforests.command.search_failed", "Szukanie nie powiodło się: %s (szczegóły w logu)");
		b.add("polishforests.command.search_timeout", "Przerwano szukanie: %s po %s s");
		b.add("polishforests.command.here_habitat", "Siedlisko: %s, STL %s, %s, strefa: %s, gleba: %s, DGW %s m, "
				+ "O %s, P %s, %s °C, drzewa: %s");
		b.add("polishforests.command.here_no_trees", "brak");
		b.add("polishforests.command.here_placeholder", "%s (zastępstwo do M3)");
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
