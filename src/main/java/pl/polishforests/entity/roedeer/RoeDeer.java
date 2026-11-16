package pl.polishforests.entity.roedeer;

import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.animation.state.AnimationTest;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.tslat.smartbrainlib.api.core.behaviour.base.FirstApplicableBehaviour;
import net.tslat.smartbrainlib.api.core.behaviour.custom.look.LookAtTarget;
import net.tslat.smartbrainlib.api.core.behaviour.custom.misc.Idle;
import net.tslat.smartbrainlib.api.core.behaviour.custom.move.FloatToSurfaceOfFluid;
import net.tslat.smartbrainlib.api.core.behaviour.custom.move.MoveToWalkTarget;
import net.tslat.smartbrainlib.api.core.behaviour.custom.move.Panic;
import net.tslat.smartbrainlib.api.core.sensor.ExtendedSensor;
import net.tslat.smartbrainlib.api.core.sensor.vanilla.HurtBySensor;
import net.tslat.smartbrainlib.api.core.sensor.vanilla.NearbyLivingEntitySensor;
import org.jspecify.annotations.Nullable;
import pl.polishforests.entity.base.AnimalProfile;
import pl.polishforests.entity.base.AnimalProfile.Period;
import pl.polishforests.entity.base.AnimalProfile.Range;
import pl.polishforests.entity.base.DailyRoutine;
import pl.polishforests.entity.base.FleeFromPlayers;
import pl.polishforests.entity.base.ForestAlarm;
import pl.polishforests.entity.base.LeaveWater;
import pl.polishforests.entity.base.PolishAnimal;
import pl.polishforests.entity.spawn.FaunaHabitat;
import pl.polishforests.season.SubSeason;
import pl.polishforests.worldgen.habitat.HabitatBiome;

/**
 * The European roe deer (step V8: model; step FA1: the shared animal base, the basic brain and the spawn;
 * docs/04-pionowy-wycinek.md "Projekt FA1"; step FA2 "Projekt FA2": the daily rhythm, the groups by season, the coat and
 * antlers in the tick, the alarm, the bark and the flared rump patch, the young).
 *
 * <p><b>Look.</b> A doe or a buck (sex 50 : 50 at spawn, saved in {@value #TAG_BUCK}) in the summer (V-IX, red-brown) or
 * winter coat (X-IV, grey-brown; saved in {@value #TAG_WINTER_COAT}; the fields of V8 are unchanged: the registry snapshot
 * lists them). From FA2 the coat follows the calendar in the tick ({@link #onCalendar}), and the buck's antlers too
 * ({@link #antlers(SubSeason, boolean, boolean, long)}, not saved: they follow from the date): polished V-X, cast X-XI
 * (half of the bucks in October, all in November), in velvet XII-IV. A young one (koźlę) has a spotted coat and no
 * antlers. Textures {@code <doe|buck>_<summer|winter>}, {@code buck_winter_hard} (October: winter coat, polished antlers),
 * {@code kid}, and for the adults the same with {@code _flared}: the white rump patch puffed up while fleeing or alert
 * after an alarm (V8-t). A buck without antlers wears the buck texture with the antler bones hidden.
 *
 * <p><b>Brain</b> (SmartBrainLib): it flees from a player within {@link PolishAnimal#fleeDistance} (20 blocks sneaking, 30
 * walking, 40 sprinting) to 16 blocks beyond that, or from a {@link ForestAlarm} heard at the flight intensity
 * ({@link FleeFromPlayers}, by distance, without a line of sight); a new flight starts with the bark "bo-bo" (a vanilla
 * sound by id until step FA4) that raises a {@link ForestAlarm.Kind#BARK} alarm for its group and neighbours. It panics when
 * hurt, swims up in water, stands alert when a player comes close or after a weaker alarm, and otherwise lives by its
 * {@link DailyRoutine}: feeding at dawn, at midday and at dusk, resting in a thicket in the late morning, the afternoon and
 * at night (the shares of {@link #PROFILE}). Its walk targets lie in its habitats (a forest of weight above 0 or the forest
 * edge), so it does not wander far into the fields; out in the open it may walk anywhere, back to the forest.
 *
 * <p>The model, the animations and the textures come from {@code tools/fauna} (GeckoLib files in
 * {@code assets/polishforests/geckolib/}). {@link Display} lets the fauna game test hold one animation; in the game it is
 * {@link Display#AUTO}: running, walking or the animation of the brain's {@link PolishAnimal.Action}.
 */
public class RoeDeer extends PolishAnimal<RoeDeer> {
	/** The animations of the model ({@code tools/fauna/animations.py}). */
	public static final RawAnimation IDLE = RawAnimation.begin().thenLoop("misc.idle");
	public static final RawAnimation WALK = RawAnimation.begin().thenLoop("move.walk");
	public static final RawAnimation RUN = RawAnimation.begin().thenLoop("move.run");
	public static final RawAnimation EAT = RawAnimation.begin().thenLoop("misc.eat");
	public static final RawAnimation ALERT = RawAnimation.begin().thenPlayAndHold("misc.alert");
	public static final RawAnimation REST = RawAnimation.begin().thenLoop("misc.rest");
	/** The fields the roe deer adds to its saved data; they go into saved worlds (the registry snapshot lists them). */
	public static final String TAG_BUCK = "Buck";
	public static final String TAG_WINTER_COAT = "WinterCoat";
	public static final List<String> SAVED_FIELDS = List.of(TAG_BUCK, TAG_WINTER_COAT);
	/** Speed above which a moving deer runs instead of walking (blocks per tick, horizontal). */
	private static final double RUN_SPEED = 0.12;
	/** How far a fleeing deer runs beyond its flight distance (blocks). */
	private static final float FLEE_BEYOND = 16;
	/** How far beyond the flight distance a player makes a deer stop and watch (blocks). */
	private static final float ALERT_BEYOND = 10;
	/** Ticks between two barks of one deer. */
	private static final int BARK_COOLDOWN = 100;
	/** Radius (blocks) and strength of the alarm of a bark: its group and neighbours flee within about 12 blocks. */
	public static final double BARK_RADIUS = 32;
	public static final float BARK_STRENGTH = 0.8F;
	/** Barks of the roe deer (for the census of the fauna game test). */
	public static final LongAdder BARKS = new LongAdder();
	/** The texture of a young one (spotted coat, no antlers, no flared patch). */
	public static final String KID = "kid";

	/**
	 * The profile (step FA1; calibration in FA2, docs/04 "Projekt FA2"). Real density about 0.085 per hectare of forest [Ź:
	 * GUS "Leśnictwo" and PZŁ 2025 after report 07 §2: about 810 thousand roe deer on about 9.5 million ha of forest, 30-80
	 * per 1000 ha of forest with the field deer counted apart], generation ×30 and spawner target ×45 (PW-15 (b), R16). The
	 * schedule after report 07 §3.2 (activity peaks in the early morning, at midday and in the evening, feeding possible at
	 * night, bouts of feeding and rumination every 2-3 h); the winter groups (rudle) XI-III, the young V-VIII (born V-VI).
	 */
	public static final AnimalProfile PROFILE = new AnimalProfile("roe_deer", 0.7F, 1.1F, new Range(1, 3), new Range(2, 6),
			EnumSet.of(SubSeason.LATE_AUTUMN, SubSeason.EARLY_WINTER, SubSeason.MID_WINTER, SubSeason.LATE_WINTER, SubSeason.EARLY_SPRING),
			List.of(new Period(0, 0.70F, 0.10F, 0.20F),          // 6:00 dawn and early morning: feeding
					new Period(2_500, 0.15F, 0.70F, 0.15F),      // 8:30 late morning: resting in a thicket
					new Period(5_000, 0.35F, 0.50F, 0.15F),      // 11:00 midday: a shorter feeding bout among the rest
					new Period(7_000, 0.15F, 0.70F, 0.15F),      // 13:00 afternoon: resting
					new Period(11_000, 0.70F, 0.10F, 0.20F),     // 17:00 dusk and evening: feeding
					new Period(14_500, 0.20F, 0.65F, 0.15F),     // 20:30 night: mostly resting, some feeding
					new Period(22_000, 0.70F, 0.10F, 0.20F)),    // 4:00 dawn: feeding
			EnumSet.of(SubSeason.MID_AUTUMN, SubSeason.LATE_AUTUMN, SubSeason.EARLY_WINTER, SubSeason.MID_WINTER, SubSeason.LATE_WINTER,
					SubSeason.EARLY_SPRING, SubSeason.MID_SPRING),
			EnumSet.of(SubSeason.LATE_SPRING, SubSeason.EARLY_SUMMER, SubSeason.MID_SUMMER, SubSeason.LATE_SUMMER), 0.6F,
			habitats(), 0.6F, 0.085F, 30, 45, 8, 20, 40, 48);

	/** The antlers of a buck (not saved: they follow from the date; a doe and a young one have none). */
	public enum Antlers {
		NONE, VELVET, HARD, CAST;

		public String id() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}

		/** Whether the antler bones are drawn. */
		public boolean shown() {
			return this == VELVET || this == HARD;
		}
	}

	private static final EntityDataAccessor<Boolean> DATA_BUCK = SynchedEntityData.defineId(RoeDeer.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> DATA_WINTER_COAT = SynchedEntityData.defineId(RoeDeer.class,
			EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> DATA_DISPLAY = SynchedEntityData.defineId(RoeDeer.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_ANTLERS = SynchedEntityData.defineId(RoeDeer.class, EntityDataSerializers.INT);
	private long lastBark = Long.MIN_VALUE / 2;

	/** Which animation the model shows. Not saved: only the game tests set anything else than {@link #AUTO}. */
	public enum Display {
		/** In the game: run, walk, or the animation of the brain's action (idle when it does nothing). */
		AUTO,
		/** No animation: the rest pose of the model, as in the .geo.json (the 4-side check of the fauna game test). */
		NONE,
		IDLE,
		WALK,
		RUN,
		EAT,
		ALERT,
		REST
	}

	public RoeDeer(EntityType<? extends RoeDeer> type, Level level) {
		super(type, level);
		// No path through water (KFA-4: no deer in water); one that ends up in water walks out (LeaveWater).
		setPathfindingMalus(PathType.WATER, -1.0F);
	}

	/**
	 * Weights of the habitats (step FA2, docs/04 "Projekt FA2"): the roe deer is a browser of the edge and of the forests
	 * with undergrowth and young stands (report 07 §3.2: the ecotone forest - field, young stands, thickets, valleys), so the
	 * oak-hornbeam and mixed forests and the riparian forests carry the most, the pine forests less (the mixed, fresh and
	 * moist more than the dry and the boggy), the beech and fir forests with little undergrowth about half, the alder carr,
	 * the montane spruce forest and the dwarf pine little; the forest edge (open land within 8 blocks of a forest) 0.6.
	 */
	private static Map<HabitatBiome, Float> habitats() {
		Map<HabitatBiome, Float> w = new java.util.EnumMap<>(HabitatBiome.class);
		for (HabitatBiome b : HabitatBiome.values()) {
			if (b.isForest()) {
				w.put(b, 1.0F);
			}
		}
		w.put(HabitatBiome.ASH_ALDER_FOREST, 0.9F);
		w.put(HabitatBiome.ELM_ASH_FOREST, 0.9F);
		w.put(HabitatBiome.WILLOW_POPLAR_FOREST, 0.9F);
		w.put(HabitatBiome.MIXED_PINE_FOREST, 0.9F);
		w.put(HabitatBiome.FRESH_PINE_FOREST, 0.8F);
		w.put(HabitatBiome.MOIST_PINE_FOREST, 0.8F);
		w.put(HabitatBiome.DRY_PINE_FOREST, 0.6F);
		w.put(HabitatBiome.COASTAL_PINE_FOREST, 0.6F);
		w.put(HabitatBiome.BOG_WOODLAND, 0.3F);
		w.put(HabitatBiome.LOWLAND_BEECH_FOREST, 0.6F);
		w.put(HabitatBiome.MONTANE_BEECH_FOREST, 0.5F);
		w.put(HabitatBiome.UPLAND_FIR_FOREST, 0.5F);
		w.put(HabitatBiome.GRAY_ALDER_FOREST, 0.7F);
		w.put(HabitatBiome.ALDER_CARR, 0.4F);
		w.put(HabitatBiome.MONTANE_SPRUCE_FOREST, 0.35F);
		w.put(HabitatBiome.DWARF_PINE_SCRUB, 0.15F);
		return w;
	}

	/** Roe deer: about 25 kg, a light and fast runner; it sees a player from 40 blocks (the largest flight distance). */
	public static AttributeSupplier.Builder createAttributes() {
		return Animal.createAnimalAttributes().add(Attributes.MAX_HEALTH, 10.0).add(Attributes.MOVEMENT_SPEED, 0.25)
				.add(Attributes.FOLLOW_RANGE, PROFILE.fleeMax());
	}

	@Override
	public AnimalProfile profile() {
		return PROFILE;
	}

	// ------------------------------------------------------------------ look

	@Override
	public String variant() {
		return isBaby() ? KID : variant(isBuck(), hasWinterCoat(), antlers(), flared());
	}

	/** The texture variant of a sex and coat (antlers as the coat gives them outside X-XI, rump patch not flared). */
	public static String variant(boolean buck, boolean winter) {
		return (buck ? "buck" : "doe") + "_" + (winter ? "winter" : "summer");
	}

	/**
	 * The texture variant of an adult: {@code <doe|buck>_<summer|winter>}, {@code buck_winter_hard} for polished antlers
	 * with the winter coat (October), and {@code _flared} at the end with the rump patch puffed up.
	 */
	public static String variant(boolean buck, boolean winter, Antlers antlers, boolean flared) {
		String v = variant(buck, winter);
		if (buck && winter && antlers == Antlers.HARD) {
			v += "_hard";
		}
		return flared ? v + "_flared" : v;
	}

	/** Every texture variant of the roe deer (the files under {@code textures/entity/roe_deer/}, from tools/fauna). */
	public static List<String> variants() {
		List<String> out = new ArrayList<>();
		for (String base : List.of("doe_summer", "doe_winter", "buck_summer", "buck_winter", "buck_winter_hard")) {
			out.add(base);
			out.add(base + "_flared");
		}
		out.add(KID);
		return out;
	}

	@Override
	public String hiddenBones() {
		return isBuck() && !isBaby() && antlers().shown() ? "" : "antler_left,antler_right";
	}

	@Override
	public List<String> looks() {
		List<String> out = new ArrayList<>(super.looks());
		out.add(hasWinterCoat() ? "winter_coat" : "summer_coat");
		if (isBuck() && !isBaby()) {
			out.add("antlers_" + antlers().id());
		}
		return out;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_BUCK, false);
		builder.define(DATA_WINTER_COAT, false);
		builder.define(DATA_DISPLAY, Display.AUTO.ordinal());
		builder.define(DATA_ANTLERS, Antlers.HARD.ordinal());
	}

	public boolean isBuck() {
		return entityData.get(DATA_BUCK);
	}

	public void setBuck(boolean buck) {
		entityData.set(DATA_BUCK, buck);
	}

	public boolean hasWinterCoat() {
		return entityData.get(DATA_WINTER_COAT);
	}

	/** Sets the coat, and the antlers that coat has outside October and November (polished or in velvet). */
	public void setWinterCoat(boolean winter) {
		entityData.set(DATA_WINTER_COAT, winter);
		setAntlers(winter ? Antlers.VELVET : Antlers.HARD);
	}

	/** The antlers of a buck in its current state (a doe's value is not used). */
	public Antlers antlers() {
		int i = entityData.get(DATA_ANTLERS);
		Antlers[] all = Antlers.values();
		return i >= 0 && i < all.length ? all[i] : Antlers.HARD;
	}

	public void setAntlers(Antlers antlers) {
		entityData.set(DATA_ANTLERS, antlers.ordinal());
	}

	public Display display() {
		int i = entityData.get(DATA_DISPLAY);
		Display[] all = Display.values();
		return i >= 0 && i < all.length ? all[i] : Display.AUTO;
	}

	public void setDisplay(Display display) {
		entityData.set(DATA_DISPLAY, display.ordinal());
	}

	/** Winter coat from October to April (report 07 §13: summer coat V-IX). */
	public static boolean winterCoat(SubSeason subSeason) {
		return PROFILE.winterCoat(subSeason);
	}

	/**
	 * The antlers of a roe deer in a sub-season (report 07 §3.2: cast from the end of October to November, regrown in
	 * velvet, cleaned at the end of April - beginning of May): polished V-IX, in October polished or cast (half of the
	 * bucks by {@code individual}, a stable draw from the animal's id), cast in November, in velvet XII-IV; none for a doe
	 * and a young one.
	 */
	public static Antlers antlers(SubSeason subSeason, boolean buck, boolean young, long individual) {
		if (!buck || young) {
			return Antlers.NONE;
		}
		return switch (subSeason) {
			case LATE_SPRING, EARLY_SUMMER, MID_SUMMER, LATE_SUMMER, EARLY_AUTUMN -> Antlers.HARD;
			case MID_AUTUMN -> earlyShedder(individual) ? Antlers.CAST : Antlers.HARD;
			case LATE_AUTUMN -> Antlers.CAST;
			case EARLY_WINTER, MID_WINTER, LATE_WINTER, EARLY_SPRING, MID_SPRING -> Antlers.VELVET;
		};
	}

	/** Whether a buck casts its antlers already in October (about half of them, by a mix of its id). */
	public static boolean earlyShedder(long individual) {
		return (RandomSupport.mixStafford13(individual ^ 0x5A4E_FA02_A47E_105DL) & 1) == 0;
	}

	/** The stable draw of this animal for its antlers (from its id). */
	public long individual() {
		return getUUID().getLeastSignificantBits() ^ getUUID().getMostSignificantBits();
	}

	/** Whether the coat and the antlers of this deer are those of a sub-season (the census checks every deer). */
	public boolean looksAsIn(SubSeason subSeason) {
		return hasWinterCoat() == winterCoat(subSeason)
				&& (!isBuck() || isBaby() || antlers() == antlers(subSeason, true, false, individual()));
	}

	@Override
	protected void onCalendar(SubSeason subSeason) {
		boolean winter = winterCoat(subSeason);
		if (winter != hasWinterCoat()) {
			entityData.set(DATA_WINTER_COAT, winter);
		}
		Antlers a = antlers(subSeason, isBuck(), isBaby(), individual());
		if (isBuck() && !isBaby() && a != antlers()) {
			setAntlers(a);
		}
	}

	/** A mother of a group with young: a doe. */
	@Override
	public void becomeMother() {
		setBuck(false);
	}

	/** Only a grown doe mothers the young (FA2: a young one without a mother takes the nearest doe). */
	@Override
	protected boolean canMother() {
		return super.canMother() && !isBuck();
	}

	// ------------------------------------------------------------------ alarm

	/** The bark "bo-bo" of a new flight (not of a flight from another deer's bark): a sound and an alarm for the group. */
	@Override
	protected void onFlightStart(boolean warns) {
		long now = level().getGameTime();
		if (!warns || isBaby() || now - lastBark < BARK_COOLDOWN || !(level() instanceof ServerLevel server)) {
			return;
		}
		lastBark = now;
		BARKS.increment();
		// Placeholder until step FA4 (own sound of tools/sound): the vanilla fox screech by id, lower.
		playSound(SoundEvents.FOX_SCREECH, 1.6F, 0.55F + getRandom().nextFloat() * 0.1F);
		ForestAlarm.raise(server, new ForestAlarm(position(), BARK_RADIUS, BARK_STRENGTH, ForestAlarm.Kind.BARK, this));
	}

	// ------------------------------------------------------------------ spawn and saved data

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
			@Nullable SpawnGroupData groupData) {
		setBuck(getRandom().nextBoolean());
		// PolishAnimal.finalizeSpawn sets the coat and the antlers of the sub-season (onCalendar).
		return super.finalizeSpawn(level, difficulty, reason, groupData);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean(TAG_BUCK, isBuck());
		output.putBoolean(TAG_WINTER_COAT, hasWinterCoat());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		setBuck(input.getBooleanOr(TAG_BUCK, false));
		// The antlers follow from the date at the next calendar update; until then those of the saved coat.
		setWinterCoat(input.getBooleanOr(TAG_WINTER_COAT, false));
	}

	// ------------------------------------------------------------------ brain (SmartBrainLib)

	@Override
	public List<? extends ExtendedSensor<? extends RoeDeer>> getSensors(RoeDeer deer) {
		return List.of(new NearbyLivingEntitySensor<RoeDeer>().setRadius(PROFILE.fleeMax() + ALERT_BEYOND, 12)
				.setPredicate((self, e) -> e instanceof Player p && !p.isSpectator()).scanRate(d -> 10), new HurtBySensor<>());
	}

	@Override
	public List<? extends BehaviorControl<? super RoeDeer>> getAlwaysRunningBehaviours(RoeDeer deer) {
		return List.of(new FloatToSurfaceOfFluid<>(), new LeaveWater<RoeDeer>(1.2F),
				new Panic<RoeDeer>().speedModifier(2.4F).whenStarting(d -> d.markFleeing(100)),
				new FleeFromPlayers<RoeDeer>(2.4F, FLEE_BEYOND),
				new LookAtTarget<>(), new MoveToWalkTarget<>());
	}

	@Override
	public List<? extends BehaviorControl<? super RoeDeer>> getIdleBehaviours(RoeDeer deer) {
		return List.of(new FirstApplicableBehaviour<RoeDeer>(
				new Idle<RoeDeer>().startCondition(RoeDeer::watchful).runFor(d -> 30 + d.getRandom().nextInt(40))
						.whenStarting(d -> d.setAction(Action.ALERT)).whenStopping(d -> d.setAction(Action.NONE)),
				new DailyRoutine<RoeDeer>(RoeDeer::watchful, RoeDeer::walkTargetOk)));
	}

	/** It stands and watches: a player near, or after an alarm below the flight intensity. */
	private static boolean watchful(RoeDeer d) {
		return d.isAlerted() || playerNear(d);
	}

	/** A player is within the flight distance plus {@value #ALERT_BEYOND} blocks (and the deer is not running). */
	private static boolean playerNear(RoeDeer d) {
		if (d.isFleeing()) {
			return false;
		}
		for (Player p : d.level().players()) {
			if (!p.isSpectator() && p.isAlive() && d.distanceTo(p) < d.fleeDistance(p) + ALERT_BEYOND) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Walk targets stay in the deer's habitats: a column of weight above 0 (forest or forest edge), unless the deer itself
	 * stands where it has none (out in the fields it may walk anywhere, the next targets take it back); never on leaves
	 * (the navigation does not walk on them either, {@code ForestGroundNavigation}) and never in water.
	 */
	private static boolean walkTargetOk(RoeDeer d, Vec3 target) {
		Level level = d.level();
		net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(target);
		if (level.getBlockState(pos.below()).is(net.minecraft.tags.BlockTags.LEAVES) || !level.getFluidState(pos).isEmpty()
				|| !level.getFluidState(pos.below()).isEmpty()) {
			return false;
		}
		if (FaunaHabitat.weight(level, PROFILE, (int) Math.floor(target.x), (int) Math.floor(target.z)) > 0) {
			return true;
		}
		return FaunaHabitat.weight(level, PROFILE, d.getBlockX(), d.getBlockZ()) <= 0;
	}

	// ------------------------------------------------------------------ GeckoLib

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>("main", 5, this::animate));
	}

	private PlayState animate(AnimationTest<RoeDeer> test) {
		RawAnimation animation = switch (display()) {
			case NONE -> null;
			case IDLE -> IDLE;
			case WALK -> WALK;
			case RUN -> RUN;
			case EAT -> EAT;
			case ALERT -> ALERT;
			case REST -> REST;
			case AUTO -> test.isMoving() ? getDeltaMovement().horizontalDistance() > RUN_SPEED ? RUN : WALK : switch (action()) {
				case EAT -> EAT;
				case REST -> REST;
				case ALERT -> ALERT;
				case NONE -> IDLE;
			};
		};
		return animation == null ? PlayState.STOP : test.setAndContinue(animation);
	}
}
