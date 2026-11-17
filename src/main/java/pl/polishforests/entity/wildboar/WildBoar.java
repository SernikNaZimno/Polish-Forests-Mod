package pl.polishforests.entity.wildboar;

import com.geckolib.animatable.manager.AnimatableManager;
import com.geckolib.animation.AnimationController;
import com.geckolib.animation.RawAnimation;
import com.geckolib.animation.object.PlayState;
import com.geckolib.animation.state.AnimationTest;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
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
import pl.polishforests.entity.base.AnimalProfile.Activity;
import pl.polishforests.entity.base.AnimalProfile.Period;
import pl.polishforests.entity.base.AnimalProfile.Range;
import pl.polishforests.entity.base.DailyRoutine;
import pl.polishforests.entity.base.FleeFromPlayers;
import pl.polishforests.entity.base.FollowHerd;
import pl.polishforests.entity.base.ForestAlarm;
import pl.polishforests.entity.base.LeaveWater;
import pl.polishforests.entity.base.PolishAnimal;
import pl.polishforests.entity.spawn.FaunaHabitat;
import pl.polishforests.season.SubSeason;
import pl.polishforests.worldgen.habitat.HabitatBiome;

/**
 * The wild boar (step FA3, docs/04-pionowy-wycinek.md "Projekt FA3").
 *
 * <p><b>Look.</b> A boar (odyniec: an adult male, dark grey-brown with a dark mane and tusks), a sow (locha: a warmer brown,
 * no tusks shown) or a young one (warchlak: brown with pale stripes, vanilla's baby scale 0.5). The sex is saved in
 * {@value #TAG_MALE}, the rank in a sounder in {@value #TAG_SENIORITY} (both with defaults, so an animal saved without them
 * is a sow of rank 0). Textures {@code textures/entity/wild_boar/<boar|sow|piglet>.png}; the tusk bones of a sow and a young
 * one are not drawn.
 *
 * <p><b>Life.</b> Crepuscular and nocturnal (the schedule of {@link #PROFILE}): it rests by day in a thicket (barłóg),
 * feeds from the evening through the night and moves at dusk and before dawn. In the oak-hornbeam, beech, mixed and
 * riparian forests a feeding boar roots ({@link BoarRooting}); in the wet forests a rest may be in a wallow by the water.
 * The sows and their young live in sounders (watahy) of 3-8 led by the oldest sow (the highest {@link #seniority()}); the
 * adult males live alone ({@link #herds()}). A sounder flees in single file behind its leader ({@link FollowHerd}), with a
 * snort at the start of the flight (a vanilla sound by id until step FA4) that raises a {@link ForestAlarm.Kind#SNORT}
 * alarm for its neighbours. From March to June a sow with her young near {@linkplain #holdsGround() holds her ground}: a
 * player within {@value #WARN_DISTANCE} blocks gets a warning charge that stops short of him ({@link WarnCharge}); the boar
 * has no attack at all (C6: no damage).
 */
public class WildBoar extends PolishAnimal<WildBoar> {
	/** The animations of the model ({@code tools/fauna/animations.py}, {@code boar_animations}). */
	public static final RawAnimation IDLE = RawAnimation.begin().thenLoop("misc.idle");
	public static final RawAnimation WALK = RawAnimation.begin().thenLoop("move.walk");
	public static final RawAnimation RUN = RawAnimation.begin().thenLoop("move.run");
	public static final RawAnimation EAT = RawAnimation.begin().thenLoop("misc.eat");
	public static final RawAnimation ALERT = RawAnimation.begin().thenPlayAndHold("misc.alert");
	public static final RawAnimation REST = RawAnimation.begin().thenLoop("misc.rest");
	public static final RawAnimation ROOT = RawAnimation.begin().thenLoop("misc.root");
	public static final RawAnimation WALLOW = RawAnimation.begin().thenLoop("misc.wallow");
	/** The fields the wild boar adds to its saved data (the registry snapshot lists them). */
	public static final String TAG_MALE = "Male";
	public static final String TAG_SENIORITY = "Seniority";
	public static final List<String> SAVED_FIELDS = List.of(TAG_MALE, TAG_SENIORITY);
	/** The texture variants (the files under {@code textures/entity/wild_boar/}, from tools/fauna). */
	public static final String BOAR = "boar";
	public static final String SOW = "sow";
	public static final String PIGLET = "piglet";
	public static final String TUSKS = "tusk_left,tusk_right";
	/** Speed above which a moving boar runs instead of walking (blocks per tick, horizontal). */
	private static final double RUN_SPEED = 0.12;
	/** How far a fleeing boar runs beyond its flight distance (blocks). */
	private static final float FLEE_BEYOND = 16;
	/** How far beyond the flight distance a player makes a boar stop and watch (blocks). */
	private static final float ALERT_BEYOND = 8;
	/** Distance (blocks) of a player at which a sow that holds her ground charges (instead of the flight distance). */
	public static final float WARN_DISTANCE = 10;
	/** Ticks between two warnings of one sow. */
	public static final int WARN_COOLDOWN = 600;
	/** Radius (blocks) within which her young make a sow hold her ground. */
	public static final double YOUNG_NEAR = 16;
	/** The sub-seasons in which a sow with young holds her ground (March to June, report 07 §3.4). */
	public static final Set<SubSeason> WARN_SEASONS = EnumSet.of(SubSeason.EARLY_SPRING, SubSeason.MID_SPRING, SubSeason.LATE_SPRING,
			SubSeason.EARLY_SUMMER);
	/** Ticks between two snorts with an alarm of one boar. */
	private static final int SNORT_COOLDOWN = 60;
	/** Radius (blocks) and strength of the alarm of a snort: its neighbours flee within about 9 blocks. */
	public static final double SNORT_RADIUS = 24;
	public static final float SNORT_STRENGTH = 0.8F;
	/** Snorts of the wild boar (for the census of the fauna game test). */
	public static final LongAdder SNORTS = new LongAdder();
	/** Share of the boars from a spawn egg or a command that are males (the spawners decide the sex of their groups). */
	private static final float EGG_MALE = 0.3F;

	/**
	 * The profile (docs/04 "Projekt FA3"). Real density about 0.03 per hectare of forest [Ź: GUS "Leśnictwo" and PZŁ, the
	 * years before the African swine fever culls (about 2014-2017: about 280 thousand on about 9.4 million ha of forest);
	 * 2025 about 0.007 after the culls; approximate], generation ×34 and spawner target ×50 (PW-15 (b), R16), so the boars
	 * come to about 4 to 10 roe deer (C3). Sounders of 3-8 all year (no winter groups), a quarter of the spawned groups a lone
	 * male, the young (warchlaki) III-VII with up to four per sow. The schedule after report 07 §3.4: crepuscular and
	 * nocturnal, resting by day in a thicket bed.
	 */
	public static final AnimalProfile PROFILE = new AnimalProfile("wild_boar", 0.9F, 0.95F, new Range(3, 8), new Range(3, 8),
			EnumSet.noneOf(SubSeason.class),
			List.of(new Period(0, 0.30F, 0.40F, 0.30F),          // 6:00 dawn: the last feeding, the way to the bed
					new Period(2_000, 0.05F, 0.90F, 0.05F),      // 8:00 day: resting in a thicket bed
					new Period(11_500, 0.55F, 0.10F, 0.35F),     // 17:30 dusk: up, out to feed
					new Period(14_000, 0.65F, 0.10F, 0.25F),     // 20:00 night: feeding and rooting
					new Period(21_000, 0.55F, 0.15F, 0.30F)),    // 3:00 before dawn: feeding, moving
			EnumSet.noneOf(SubSeason.class),
			EnumSet.of(SubSeason.EARLY_SPRING, SubSeason.MID_SPRING, SubSeason.LATE_SPRING, SubSeason.EARLY_SUMMER, SubSeason.MID_SUMMER),
			0.7F, habitats(), 0.4F, 0.03F, 34, 50, 10, 12, 30, 64, 0.25F, 4);

	private static final EntityDataAccessor<Boolean> DATA_MALE = SynchedEntityData.defineId(WildBoar.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> DATA_DISPLAY = SynchedEntityData.defineId(WildBoar.class, EntityDataSerializers.INT);

	/** Which animation the model shows. Not saved: only the game tests set anything else than {@link #AUTO}. */
	public enum Display {
		AUTO, NONE, IDLE, WALK, RUN, EAT, ALERT, REST, ROOT, WALLOW
	}

	private int seniority;
	private @Nullable SubSeason subSeason;
	private boolean youngNear;
	private boolean warning;
	private long lastWarn = Long.MIN_VALUE / 2;
	private long lastSnort = Long.MIN_VALUE / 2;
	/** Ticks of the current rooting bout until the patch is dug (-1: not drawn yet), and whether it was dug. */
	private int rootTimer = -1;
	private boolean rootDug;
	/** Whether the rest being walked to is a wallow (set by {@link #restTarget}, taken by {@link #settle}). */
	private boolean wallowNext;

	public WildBoar(EntityType<? extends WildBoar> type, Level level) {
		super(type, level);
		// No path through water (KFA-4); one that ends up in water walks out (LeaveWater). A wallow is on the shore.
		setPathfindingMalus(PathType.WATER, -1.0F);
	}

	/**
	 * Weights of the habitats (docs/04 "Projekt FA3", report 07 §3.4): the wild boar likes the fertile broadleaf forests
	 * with mast (oak-hornbeam and beech forests 1, the mixed forest 0.9) and the wet riparian forests and alder carrs with
	 * mud (0.8-0.9), less the mixed pine forest (0.7) and the fir forest (0.6), little the poor pine forests and the bog
	 * woodland, nothing the dwarf pine; the forest edge (the fields it raids at night) 0.4.
	 */
	private static Map<HabitatBiome, Float> habitats() {
		Map<HabitatBiome, Float> w = new java.util.EnumMap<>(HabitatBiome.class);
		w.put(HabitatBiome.OAK_HORNBEAM_FOREST, 1.0F);
		w.put(HabitatBiome.LOWLAND_BEECH_FOREST, 1.0F);
		w.put(HabitatBiome.MONTANE_BEECH_FOREST, 0.8F);
		w.put(HabitatBiome.UPLAND_FIR_FOREST, 0.6F);
		w.put(HabitatBiome.MIXED_FOREST, 0.9F);
		w.put(HabitatBiome.MIXED_PINE_FOREST, 0.7F);
		w.put(HabitatBiome.ALDER_CARR, 0.8F);
		w.put(HabitatBiome.ASH_ALDER_FOREST, 0.9F);
		w.put(HabitatBiome.ELM_ASH_FOREST, 0.9F);
		w.put(HabitatBiome.WILLOW_POPLAR_FOREST, 0.8F);
		w.put(HabitatBiome.GRAY_ALDER_FOREST, 0.5F);
		w.put(HabitatBiome.FRESH_PINE_FOREST, 0.3F);
		w.put(HabitatBiome.MOIST_PINE_FOREST, 0.4F);
		w.put(HabitatBiome.DRY_PINE_FOREST, 0.1F);
		w.put(HabitatBiome.COASTAL_PINE_FOREST, 0.15F);
		w.put(HabitatBiome.BOG_WOODLAND, 0.2F);
		w.put(HabitatBiome.MONTANE_SPRUCE_FOREST, 0.15F);
		return w;
	}

	/** Wild boar: 50-150 kg, strong and fast; no attack damage (C6: it never hurts a player). */
	public static AttributeSupplier.Builder createAttributes() {
		return Animal.createAnimalAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.MOVEMENT_SPEED, 0.25)
				.add(Attributes.FOLLOW_RANGE, PROFILE.fleeMax());
	}

	@Override
	public AnimalProfile profile() {
		return PROFILE;
	}

	// ------------------------------------------------------------------ look

	@Override
	public String variant() {
		return variant(isMale(), isBaby());
	}

	/** The texture variant of a sex and age. */
	public static String variant(boolean male, boolean young) {
		return young ? PIGLET : male ? BOAR : SOW;
	}

	/** Every texture variant of the wild boar. */
	public static List<String> variants() {
		return List.of(BOAR, SOW, PIGLET);
	}

	@Override
	public String hiddenBones() {
		return isMale() && !isBaby() ? "" : TUSKS;
	}

	@Override
	public List<String> looks() {
		List<String> out = new ArrayList<>(super.looks());
		if (!isBaby()) {
			out.add(isMale() ? "boar_male" : "sow");
		}
		return out;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_MALE, false);
		builder.define(DATA_DISPLAY, Display.AUTO.ordinal());
	}

	public boolean isMale() {
		return entityData.get(DATA_MALE);
	}

	public void setMale(boolean male) {
		entityData.set(DATA_MALE, male);
	}

	public Display display() {
		int i = entityData.get(DATA_DISPLAY);
		Display[] all = Display.values();
		return i >= 0 && i < all.length ? all[i] : Display.AUTO;
	}

	public void setDisplay(Display display) {
		entityData.set(DATA_DISPLAY, display.ordinal());
	}

	@Override
	protected void onCalendar(SubSeason subSeason) {
		this.subSeason = subSeason;
	}

	// ------------------------------------------------------------------ groups

	/** The adult male lives alone; the sows, the yearlings and the young live in sounders. */
	@Override
	public boolean herds() {
		return !isMale() || isBaby();
	}

	/** The rank in a sounder (the oldest sow leads); a young one ranks below every grown animal. */
	@Override
	public int seniority() {
		return isBaby() ? -1 : seniority;
	}

	public void setSeniority(int seniority) {
		this.seniority = seniority;
	}

	@Override
	public void becomeSolitary() {
		setMale(true);
	}

	/** The leader of a spawned sounder: the oldest sow (a rank above the 0-9 of the others). */
	@Override
	public void becomeHerdLeader() {
		setMale(false);
		seniority = 10 + getRandom().nextInt(10);
	}

	@Override
	public void becomeMember() {
		setMale(false);
	}

	@Override
	public void becomeMother() {
		setMale(false);
	}

	/** Only a grown sow mothers the young. */
	@Override
	protected boolean canMother() {
		return super.canMother() && !isMale();
	}

	// ------------------------------------------------------------------ flight, snort and warning

	/**
	 * Whether this sow holds her ground (C6): a grown sow with her young within {@value #YOUNG_NEAR} blocks, March to June,
	 * at least {@value #WARN_COOLDOWN} ticks after her last warning.
	 */
	public boolean holdsGround() {
		return !isMale() && !isBaby() && subSeason != null && WARN_SEASONS.contains(subSeason) && youngNear
				&& level().getGameTime() - lastWarn >= WARN_COOLDOWN;
	}

	/** A sow that holds her ground lets a player come to {@value #WARN_DISTANCE} blocks, then warns instead of fleeing. */
	@Override
	public float fleeDistance(Player player) {
		return holdsGround() ? Math.min(WARN_DISTANCE, super.fleeDistance(player)) : super.fleeDistance(player);
	}

	/** Whether the warning charge runs ({@link WarnCharge}). */
	public boolean isWarning() {
		return warning;
	}

	void setWarning(boolean warning) {
		this.warning = warning;
	}

	/** Marks the end of a warning: the sow flees as usual until the cooldown is over. */
	void warned() {
		lastWarn = level().getGameTime();
	}

	/** A member of a fleeing sounder follows in single file; nobody flees while warning. */
	@Override
	public boolean fleesAlone() {
		if (warning) {
			return false;
		}
		PolishAnimal<?> leader = leader();
		return leader == null || !leader.isFleeing() || distanceTo(leader) > 2 * DailyRoutine.LEADER_REACH;
	}

	/** A new flight (not one from another animal's warning) starts with a snort; a leader puts its sounder in single file. */
	@Override
	protected void onFlightStart(boolean warns) {
		if (!(level() instanceof ServerLevel server)) {
			return;
		}
		if (warns && !isBaby()) {
			snort(true);
		}
		if (isHerdLeader()) {
			formFile(server);
		}
	}

	/**
	 * A snort (placeholder until step FA4, the own sound of tools/sound: the vanilla hoglin's angry sound by id, lower); with
	 * {@code alarm} and at most once in {@value #SNORT_COOLDOWN} ticks also a {@link ForestAlarm.Kind#SNORT} alarm.
	 */
	void snort(boolean alarm) {
		if (!(level() instanceof ServerLevel server)) {
			return;
		}
		long now = level().getGameTime();
		if (alarm && now - lastSnort < SNORT_COOLDOWN) {
			return;
		}
		SNORTS.increment();
		playSound(SoundEvents.HOGLIN_ANGRY, 1.2F, (isBaby() ? 1.3F : 0.75F) + getRandom().nextFloat() * 0.1F);
		if (alarm) {
			lastSnort = now;
			ForestAlarm.raise(server, new ForestAlarm(position(), SNORT_RADIUS, SNORT_STRENGTH, ForestAlarm.Kind.SNORT, this));
		}
	}

	// ------------------------------------------------------------------ daily routine: rooting, beds, wallows

	@Override
	public Action settle(Activity activity) {
		boolean wallow = wallowNext;
		wallowNext = false;
		return switch (activity) {
			case FEED -> {
				rootTimer = -1;
				rootDug = false;
				yield BoarRooting.rootsAt(level(), blockPosition()) ? Action.ROOT : Action.EAT;
			}
			case REST -> {
				if (wallow && level() instanceof ServerLevel server) {
					BoarRooting.wallow(server, blockPosition(), getRandom());
					yield Action.WALLOW;
				}
				yield Action.REST;
			}
			case MOVE -> Action.NONE;
		};
	}

	/** In the wet forests a rest may be in a wallow by the water; otherwise a thicket bed. */
	@Override
	public @Nullable Vec3 restTarget(int radius, RandomSource random) {
		if (BoarRooting.wallowsAt(level(), blockPosition()) && random.nextFloat() < BoarRooting.WALLOW_CHANCE) {
			Vec3 spot = BoarRooting.wallowSpot(level(), blockPosition(), random);
			if (spot != null) {
				wallowNext = true;
				return spot;
			}
		}
		return super.restTarget(radius, random);
	}

	@Override
	protected void onServerTick(ServerLevel level) {
		if (action() == Action.ROOT && !isBaby()) {
			if (rootTimer < 0 && !rootDug) {
				rootTimer = BoarRooting.DIG_MIN + getRandom().nextInt(BoarRooting.DIG_MAX - BoarRooting.DIG_MIN + 1);
			} else if (rootTimer > 0) {
				rootTimer--;
			} else if (rootTimer == 0 && !rootDug) {
				rootDug = true;
				rootTimer = -1;
				if (getRandom().nextFloat() < BoarRooting.PATCH_CHANCE) {
					BoarRooting.dig(level, blockPosition(), getRandom());
				}
			}
		}
		if ((tickCount + getId()) % 20 == 0) {
			youngNear = !isMale() && !isBaby() && youngNear(level);
		}
	}

	/** Whether one of her young is within {@value #YOUNG_NEAR} blocks. */
	private boolean youngNear(ServerLevel level) {
		java.util.UUID id = getUUID();
		for (Entity e : level.getEntities(this, getBoundingBox().inflate(YOUNG_NEAR, 8, YOUNG_NEAR), e -> e.getType() == getType())) {
			if (e instanceof WildBoar b && b.isBaby() && b.isAlive() && id.equals(b.motherId()) && distanceTo(b) <= YOUNG_NEAR) {
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------ spawn and saved data

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
			@Nullable SpawnGroupData groupData) {
		setMale(getRandom().nextFloat() < EGG_MALE);
		seniority = getRandom().nextInt(10);
		return super.finalizeSpawn(level, difficulty, reason, groupData);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean(TAG_MALE, isMale());
		output.putInt(TAG_SENIORITY, seniority);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		setMale(input.getBooleanOr(TAG_MALE, false));
		seniority = input.getIntOr(TAG_SENIORITY, 0);
	}

	// ------------------------------------------------------------------ brain (SmartBrainLib)

	@Override
	public List<? extends ExtendedSensor<? extends WildBoar>> getSensors(WildBoar boar) {
		return List.of(new NearbyLivingEntitySensor<WildBoar>().setRadius(PROFILE.fleeMax() + ALERT_BEYOND, 12)
				.setPredicate((self, e) -> e instanceof Player p && !p.isSpectator()).scanRate(b -> 10), new HurtBySensor<>());
	}

	@Override
	public List<? extends BehaviorControl<? super WildBoar>> getAlwaysRunningBehaviours(WildBoar boar) {
		return List.of(new FloatToSurfaceOfFluid<>(), new LeaveWater<WildBoar>(1.2F),
				new Panic<WildBoar>().speedModifier(2.2F).whenStarting(b -> b.markFleeing(100)),
				new WarnCharge(),
				new FollowHerd<WildBoar>().startCondition(b -> !b.isWarning()).stopIf(WildBoar::isWarning),
				new FleeFromPlayers<WildBoar>(2.3F, FLEE_BEYOND),
				new LookAtTarget<>(), new MoveToWalkTarget<>());
	}

	@Override
	public List<? extends BehaviorControl<? super WildBoar>> getIdleBehaviours(WildBoar boar) {
		return List.of(new FirstApplicableBehaviour<WildBoar>(
				new Idle<WildBoar>().startCondition(WildBoar::watchful).runFor(b -> 30 + b.getRandom().nextInt(40))
						.whenStarting(b -> b.setAction(Action.ALERT)).whenStopping(b -> b.setAction(Action.NONE)),
				new DailyRoutine<WildBoar>(b -> watchful(b) || FollowHerd.wantsToFollow(b), WildBoar::walkTargetOk)));
	}

	/** It stands and watches: a player near, or after an alarm below the flight intensity. */
	private static boolean watchful(WildBoar b) {
		return b.isAlerted() || playerNear(b);
	}

	/** A player is within the flight distance plus {@value #ALERT_BEYOND} blocks (and the boar is not running or warning). */
	private static boolean playerNear(WildBoar b) {
		if (b.isFleeing() || b.isWarning()) {
			return false;
		}
		for (Player p : b.level().players()) {
			if (!p.isSpectator() && p.isAlive() && b.distanceTo(p) < b.fleeDistance(p) + ALERT_BEYOND) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Walk targets stay in the boar's habitats (a column of weight above 0, unless it stands where it has none); never on
	 * leaves and never in water.
	 */
	private static boolean walkTargetOk(WildBoar b, Vec3 target) {
		Level level = b.level();
		BlockPos pos = BlockPos.containing(target);
		if (level.getBlockState(pos.below()).is(BlockTags.LEAVES) || !level.getFluidState(pos).isEmpty()
				|| !level.getFluidState(pos.below()).isEmpty()) {
			return false;
		}
		if (FaunaHabitat.weight(level, PROFILE, (int) Math.floor(target.x), (int) Math.floor(target.z)) > 0) {
			return true;
		}
		return FaunaHabitat.weight(level, PROFILE, b.getBlockX(), b.getBlockZ()) <= 0;
	}

	// ------------------------------------------------------------------ GeckoLib

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>("main", 5, this::animate));
	}

	private PlayState animate(AnimationTest<WildBoar> test) {
		RawAnimation animation = switch (display()) {
			case NONE -> null;
			case IDLE -> IDLE;
			case WALK -> WALK;
			case RUN -> RUN;
			case EAT -> EAT;
			case ALERT -> ALERT;
			case REST -> REST;
			case ROOT -> ROOT;
			case WALLOW -> WALLOW;
			case AUTO -> test.isMoving() ? getDeltaMovement().horizontalDistance() > RUN_SPEED ? RUN : WALK : switch (action()) {
				case EAT -> EAT;
				case REST -> REST;
				case ALERT -> ALERT;
				case ROOT -> ROOT;
				case WALLOW -> WALLOW;
				case NONE -> IDLE;
			};
		};
		return animation == null ? PlayState.STOP : test.setAndContinue(animation);
	}
}
