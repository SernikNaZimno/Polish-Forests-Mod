package pl.polishforests.entity.squirrel;

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
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.tslat.smartbrainlib.api.core.behaviour.custom.move.FloatToSurfaceOfFluid;
import net.tslat.smartbrainlib.api.core.behaviour.custom.move.MoveToWalkTarget;
import net.tslat.smartbrainlib.api.core.sensor.ExtendedSensor;
import net.tslat.smartbrainlib.api.core.sensor.vanilla.HurtBySensor;
import org.jspecify.annotations.Nullable;
import pl.polishforests.entity.base.AnimalProfile;
import pl.polishforests.entity.base.AnimalProfile.Period;
import pl.polishforests.entity.base.AnimalProfile.Range;
import pl.polishforests.entity.base.LeaveWater;
import pl.polishforests.entity.base.PolishAnimal;
import pl.polishforests.entity.spawn.FaunaHabitat;
import pl.polishforests.season.SubSeason;
import pl.polishforests.worldgen.habitat.HabitatBiome;

/**
 * The red squirrel, wiewiórka pospolita (step FA5, docs/04-pionowy-wycinek.md "Projekt FA5", option O1).
 *
 * <p><b>Look.</b> The model of {@code tools/fauna/smallmammal.py}: modelled at twice the resolution of the other animals
 * (the F3 enlargement ×2 of an animal under 30 cm) and drawn at half the size ({@code RedSquirrelRenderer}, GeckoLib's
 * {@code withScale(0.5)}), hitbox 0.4 × 0.45. Two colour morphs, the rufous one and the dark brown one ({@value #TAG_DARK},
 * saved; drawn at spawn: {@value #DARK_MOUNTAIN} in the montane spruce and the fir forests, {@value #DARK_ELSEWHERE}
 * elsewhere), each in a summer and a winter coat (the calendar, {@link #PROFILE}); the winter coat has the ear tufts (the
 * tuft bones are hidden in summer). While eating it holds a pine cone (in the conifer forests) or a hazelnut (elsewhere):
 * the other food bone and both outside eating are hidden. Textures {@code textures/entity/red_squirrel/<red|dark>_<summer|winter>.png}.
 *
 * <p><b>Life</b> ({@link SquirrelMind}): diurnal, with a morning and an afternoon peak in summer and a short day in winter
 * ({@link #schedule}); it feeds on the ground (on lying logs when there are some) and in the crowns (cones), moves through
 * the crowns by leaps between trunks, and rests and sleeps in the crown (its drey). It flees from a player, an alarm or a
 * hurt up the nearest trunk and higher, to the far side of its trunk, or into the next crown. Alone (no groups), no young
 * (C10).
 *
 * <p><b>Movement</b> ({@link TreeMotion}): on the ground the ground navigation of the mod (a step height of
 * {@value #STEP} blocks, so it runs onto a lying log of the mod); in the trees its own tree mode without the vanilla physics.
 * It may stand on leaves ({@link #staysOffLeaves()} false). A fall up to {@value #SAFE_FALL} blocks does not hurt it.
 *
 * <p><b>Spawn.</b> Category {@code AMBIENT}; like the other animals of the mod outside vanilla's mob counts, with its own
 * limit of the fauna spawner ({@link AnimalProfile#maxNearPlayer()}).
 */
public class RedSquirrel extends PolishAnimal<RedSquirrel> {
	public static final RawAnimation IDLE = RawAnimation.begin().thenLoop("misc.idle");
	public static final RawAnimation WALK = RawAnimation.begin().thenLoop("move.walk");
	public static final RawAnimation HOP = RawAnimation.begin().thenLoop("move.hop");
	public static final RawAnimation CLIMB = RawAnimation.begin().thenLoop("move.climb");
	public static final RawAnimation DESCEND = RawAnimation.begin().thenLoop("move.descend");
	public static final RawAnimation JUMP = RawAnimation.begin().thenLoop("move.jump");
	public static final RawAnimation EAT = RawAnimation.begin().thenLoop("misc.eat");
	public static final RawAnimation ALERT = RawAnimation.begin().thenPlayAndHold("misc.alert");
	public static final RawAnimation REST = RawAnimation.begin().thenLoop("misc.rest");
	/** The field the red squirrel adds to its saved data (the registry snapshot lists it). */
	public static final String TAG_DARK = "Dark";
	public static final List<String> SAVED_FIELDS = List.of(TAG_DARK);
	/** Bones the renderer hides: the winter ear tufts and the two foods. */
	public static final String TUFTS = "tuft_left,tuft_right";
	public static final String CONE = "food_cone";
	public static final String NUT = "food_nut";
	/** Share of the dark morph in the montane spruce and the fir forests, and elsewhere. */
	public static final float DARK_MOUNTAIN = 0.4F;
	public static final float DARK_ELSEWHERE = 0.15F;
	/** Fall (blocks) it takes without damage, and its step height (a lying log of radius 7 px is 0.94 blocks high). */
	public static final double SAFE_FALL = 10;
	public static final double STEP = 1.0;
	/** Speed above which a moving squirrel on the ground hops instead of walking (blocks per tick, horizontal). */
	private static final double HOP_SPEED = 0.09;
	/** Falls with damage (the fauna game test needs none). */
	public static final LongAdder FALL_HURT = new LongAdder();

	/** The sub-seasons of the winter coat with the ear tufts (moult in autumn and in spring; report 07, Lurz et al. 2005). */
	public static final Set<SubSeason> WINTER_COAT = EnumSet.of(SubSeason.MID_AUTUMN, SubSeason.LATE_AUTUMN, SubSeason.EARLY_WINTER,
			SubSeason.MID_WINTER, SubSeason.LATE_WINTER, SubSeason.EARLY_SPRING);
	/** The sub-seasons of the short winter day ({@link #WINTER_SCHEDULE}). */
	public static final Set<SubSeason> SHORT_DAYS = EnumSet.of(SubSeason.LATE_AUTUMN, SubSeason.EARLY_WINTER, SubSeason.MID_WINTER,
			SubSeason.LATE_WINTER);
	/**
	 * The summer day (the profile's schedule): active from dawn with a morning and an afternoon peak of feeding, a rest at
	 * midday, the night in the drey (report 07; Wauters and Dhondt: two activity peaks in summer).
	 */
	public static final List<Period> SUMMER_SCHEDULE = List.of(new Period(0, 0.60F, 0.10F, 0.30F),   // 6:00 the morning peak
			new Period(4_000, 0.35F, 0.35F, 0.30F),                                                   // 10:00
			new Period(6_500, 0.25F, 0.55F, 0.20F),                                                   // 12:30 the midday rest
			new Period(8_500, 0.60F, 0.10F, 0.30F),                                                   // 14:30 the afternoon peak
			new Period(12_500, 0.03F, 0.95F, 0.02F),                                                  // 18:30 the night in the drey
			new Period(22_500, 0.10F, 0.80F, 0.10F));                                                 // 4:30 before dawn
	/** The winter day: one bout of activity around noon, the rest of the day in the drey. */
	public static final List<Period> WINTER_SCHEDULE = List.of(new Period(0, 0.05F, 0.90F, 0.05F),   // 6:00 still in the drey
			new Period(2_000, 0.65F, 0.15F, 0.20F),                                                   // 8:00 out to feed
			new Period(6_000, 0.55F, 0.20F, 0.25F),                                                   // 12:00
			new Period(8_500, 0.03F, 0.95F, 0.02F));                                                  // 14:30 back in the drey

	/**
	 * The profile (docs/04 "Projekt FA5"). Real density about 0.25 per hectare of forest [Ź: Lurz, Gurnell and Magris 2005,
	 * Mammalian Species 769 (0.1-1.5 per ha in conifer and mixed forests); Wauters and Dhondt 1990 (Belgium, 0.3-1.1 per ha
	 * in mixed forest, less in pine plantations); approximate], generation ×20 and spawner target ×24 (KFA-5: 20-50), alone
	 * all year, no young (C10), flight from 4 blocks (sneaking) to 12 (sprinting), its own limit of 10 near a player.
	 */
	public static final AnimalProfile PROFILE = new AnimalProfile("red_squirrel", 0.4F, 0.45F, new Range(1, 1), new Range(1, 1),
			EnumSet.noneOf(SubSeason.class), SUMMER_SCHEDULE, WINTER_COAT, EnumSet.noneOf(SubSeason.class), 0.0F, habitats(), 0.0F,
			0.25F, 20, 24, 10, 4, 12, 40, 0.0F, 1);

	private static final EntityDataAccessor<Boolean> DATA_DARK = SynchedEntityData.defineId(RedSquirrel.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> DATA_WINTER = SynchedEntityData.defineId(RedSquirrel.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> DATA_CONE = SynchedEntityData.defineId(RedSquirrel.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> DATA_MOTION = SynchedEntityData.defineId(RedSquirrel.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_DISPLAY = SynchedEntityData.defineId(RedSquirrel.class, EntityDataSerializers.INT);

	/** Which animation the model shows. Not saved: only the game tests set anything else than {@link #AUTO}. */
	public enum Display {
		AUTO, NONE, IDLE, WALK, HOP, CLIMB, DESCEND, JUMP, EAT, ALERT, REST
	}

	private final TreeMotion motion = new TreeMotion(this);
	private final SquirrelMind mind = new SquirrelMind(this);
	private @Nullable SubSeason subSeason;

	public RedSquirrel(EntityType<? extends RedSquirrel> type, Level level) {
		super(type, level);
		setPathfindingMalus(PathType.WATER, -1.0F);
	}

	/**
	 * Weights of the habitats (docs/04 "Projekt FA5"): the red squirrel lives where the trees bear seeds all year, the
	 * conifer and mixed forests (the mixed pine forest, the mixed forest and the montane spruce forest 1, the fresh pine
	 * forest and the fir forest 0.8-0.9), the oak-hornbeam forest with its hazel and the beech forests (0.7-0.8), less the
	 * poor and the wet forests, not the dwarf pine; no open land (no forest edge).
	 */
	private static Map<HabitatBiome, Float> habitats() {
		Map<HabitatBiome, Float> w = new java.util.EnumMap<>(HabitatBiome.class);
		w.put(HabitatBiome.MIXED_PINE_FOREST, 1.0F);
		w.put(HabitatBiome.MIXED_FOREST, 1.0F);
		w.put(HabitatBiome.MONTANE_SPRUCE_FOREST, 1.0F);
		w.put(HabitatBiome.FRESH_PINE_FOREST, 0.9F);
		w.put(HabitatBiome.UPLAND_FIR_FOREST, 0.8F);
		w.put(HabitatBiome.OAK_HORNBEAM_FOREST, 0.8F);
		w.put(HabitatBiome.LOWLAND_BEECH_FOREST, 0.7F);
		w.put(HabitatBiome.MONTANE_BEECH_FOREST, 0.7F);
		w.put(HabitatBiome.MOIST_PINE_FOREST, 0.7F);
		w.put(HabitatBiome.DRY_PINE_FOREST, 0.5F);
		w.put(HabitatBiome.COASTAL_PINE_FOREST, 0.5F);
		w.put(HabitatBiome.ELM_ASH_FOREST, 0.5F);
		w.put(HabitatBiome.ASH_ALDER_FOREST, 0.4F);
		w.put(HabitatBiome.BOG_WOODLAND, 0.3F);
		w.put(HabitatBiome.ALDER_CARR, 0.3F);
		w.put(HabitatBiome.WILLOW_POPLAR_FOREST, 0.3F);
		w.put(HabitatBiome.GRAY_ALDER_FOREST, 0.3F);
		return w;
	}

	/** The habitats with cones to eat (the conifer forests and the mixed ones): a feeding squirrel holds a cone there. */
	public static boolean coneForest(@Nullable HabitatBiome b) {
		return b != null && switch (b) {
			case DRY_PINE_FOREST, FRESH_PINE_FOREST, COASTAL_PINE_FOREST, MOIST_PINE_FOREST, BOG_WOODLAND, MIXED_PINE_FOREST, MIXED_FOREST,
					UPLAND_FIR_FOREST, MONTANE_SPRUCE_FOREST -> true;
			default -> false;
		};
	}

	/** The share of the dark morph in a habitat. */
	public static float darkShare(@Nullable HabitatBiome b) {
		return b == HabitatBiome.MONTANE_SPRUCE_FOREST || b == HabitatBiome.UPLAND_FIR_FOREST ? DARK_MOUNTAIN : DARK_ELSEWHERE;
	}

	/** The daily schedule of a sub-season: the short winter day in {@link #SHORT_DAYS}, else the profile's summer day. */
	public static List<Period> schedule(@Nullable SubSeason subSeason) {
		return subSeason != null && SHORT_DAYS.contains(subSeason) ? WINTER_SCHEDULE : SUMMER_SCHEDULE;
	}

	/** Red squirrel: 250-350 g; quick on the ground, falls of up to {@value #SAFE_FALL} blocks harmless. */
	public static AttributeSupplier.Builder createAttributes() {
		return Animal.createAnimalAttributes().add(Attributes.MAX_HEALTH, 4.0).add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.FOLLOW_RANGE, 16.0).add(Attributes.SAFE_FALL_DISTANCE, SAFE_FALL).add(Attributes.STEP_HEIGHT, STEP);
	}

	@Override
	public AnimalProfile profile() {
		return PROFILE;
	}

	public TreeMotion motion() {
		return motion;
	}

	SquirrelMind mind() {
		return mind;
	}

	/** The sub-season of its last calendar tick (null before the first). */
	@Nullable SubSeason subSeason() {
		return subSeason;
	}

	// ------------------------------------------------------------------ look

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_DARK, false);
		builder.define(DATA_WINTER, false);
		builder.define(DATA_CONE, true);
		builder.define(DATA_MOTION, TreeMotion.Kind.GROUND.ordinal());
		builder.define(DATA_DISPLAY, Display.AUTO.ordinal());
	}

	public boolean isDark() {
		return entityData.get(DATA_DARK);
	}

	public void setDark(boolean dark) {
		entityData.set(DATA_DARK, dark);
	}

	public boolean hasWinterCoat() {
		return entityData.get(DATA_WINTER);
	}

	public void setWinterCoat(boolean winter) {
		entityData.set(DATA_WINTER, winter);
	}

	/** Whether the food it eats is a cone (else a hazelnut). */
	public boolean eatsCone() {
		return entityData.get(DATA_CONE);
	}

	void setCone(boolean cone) {
		if (cone != eatsCone()) {
			entityData.set(DATA_CONE, cone);
		}
	}

	/** The kind of the leg of the tree mode (synchronized; {@link TreeMotion.Kind#GROUND} on the ground). */
	public TreeMotion.Kind motionKind() {
		int i = entityData.get(DATA_MOTION);
		TreeMotion.Kind[] all = TreeMotion.Kind.values();
		return i >= 0 && i < all.length ? all[i] : TreeMotion.Kind.GROUND;
	}

	void syncMotion(TreeMotion.Kind kind) {
		if (entityData.get(DATA_MOTION) != kind.ordinal()) {
			entityData.set(DATA_MOTION, kind.ordinal());
		}
	}

	/** Whether it is in the tree mode (both sides: from the synchronized kind). */
	public boolean inTree() {
		return motionKind() != TreeMotion.Kind.GROUND;
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
	public String variant() {
		return variant(isDark(), hasWinterCoat());
	}

	/** The texture variant of a morph and a coat. */
	public static String variant(boolean dark, boolean winter) {
		return (dark ? "dark" : "red") + "_" + (winter ? "winter" : "summer");
	}

	/** Every texture variant of the red squirrel. */
	public static List<String> variants() {
		return List.of(variant(false, false), variant(false, true), variant(true, false), variant(true, true));
	}

	@Override
	public String hiddenBones() {
		boolean eating = action() == Action.EAT || display() == Display.EAT;
		List<String> hidden = new ArrayList<>(4);
		if (!hasWinterCoat()) {
			hidden.add(TUFTS);
		}
		if (!eating || !eatsCone()) {
			hidden.add(CONE);
		}
		if (!eating || eatsCone()) {
			hidden.add(NUT);
		}
		return String.join(",", hidden);
	}

	@Override
	public List<String> looks() {
		List<String> out = new ArrayList<>(super.looks());
		out.add(isDark() ? "dark_morph" : "red_morph");
		out.add(hasWinterCoat() ? "winter_coat" : "summer_coat");
		return out;
	}

	@Override
	protected void onCalendar(SubSeason subSeason) {
		this.subSeason = subSeason;
		boolean winter = PROFILE.winterCoat(subSeason);
		if (winter != hasWinterCoat()) {
			setWinterCoat(winter);
		}
	}

	// ------------------------------------------------------------------ life

	/** Alone all year: no group takes it. */
	@Override
	public boolean herds() {
		return false;
	}

	/** It lives in the crowns: it may stand on leaves. */
	@Override
	protected boolean staysOffLeaves() {
		return false;
	}

	@Override
	public State state() {
		if (!inTree()) {
			return super.state();
		}
		return switch (motionKind()) {
			case CLIMB, DESCEND -> State.CLIMBING;
			case RUN, LEAP -> isFleeing() ? State.FLEEING : State.MOVING;
			default -> switch (action()) {
				case EAT -> State.GRAZING;
				case REST -> State.RESTING;
				case ALERT -> State.ALERT;
				default -> State.IN_TREE;
			};
		};
	}

	@Override
	protected void onServerTick(ServerLevel level) {
		if (subSeason == null) {
			onCalendar(pl.polishforests.season.Seasons.provider().subSeason(level));
		}
		if (!motion.active()) {
			motion.tryCatch();
		}
		motion.tick();
		mind.tick(level);
	}

	/** In the tree mode no vanilla movement (no gravity, no walk control): {@link TreeMotion} sets the position. */
	@Override
	public void travel(Vec3 input) {
		if (!motion.active()) {
			super.travel(input);
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide() && inTree()) {
			// The client turns the body after the horizontal movement; on the bark the squirrel faces where the server says.
			yBodyRot = getYRot();
			yBodyRotO = yRotO;
			yHeadRot = getYRot();
			yHeadRotO = yRotO;
		}
	}

	@Override
	public boolean isPushable() {
		return !inTree() && super.isPushable();
	}

	@Override
	public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
		boolean hurt = super.causeFallDamage(fallDistance, damageModifier, damageSource);
		if (hurt) {
			FALL_HURT.increment();
		}
		return hurt;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		boolean hurt = super.hurtServer(level, source, damage);
		if (hurt) {
			mind.hurt(source.getSourcePosition());
		}
		return hurt;
	}

	// ------------------------------------------------------------------ spawn and saved data

	@Override
	public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason reason,
			@Nullable SpawnGroupData groupData) {
		HabitatBiome b = FaunaHabitat.surface(level, getBlockX(), getBlockZ());
		setDark(getRandom().nextFloat() < darkShare(b));
		setCone(coneForest(b));
		return super.finalizeSpawn(level, difficulty, reason, groupData);
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean(TAG_DARK, isDark());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		setDark(input.getBooleanOr(TAG_DARK, false));
	}

	// ------------------------------------------------------------------ brain (SmartBrainLib): only the ground walks

	@Override
	public List<? extends ExtendedSensor<? extends RedSquirrel>> getSensors(RedSquirrel squirrel) {
		return List.of(new HurtBySensor<>());
	}

	@Override
	public List<? extends BehaviorControl<? super RedSquirrel>> getAlwaysRunningBehaviours(RedSquirrel squirrel) {
		return List.of(new FloatToSurfaceOfFluid<>(), new LeaveWater<RedSquirrel>(1.4F), new MoveToWalkTarget<>());
	}

	@Override
	public List<? extends BehaviorControl<? super RedSquirrel>> getIdleBehaviours(RedSquirrel squirrel) {
		return List.of();
	}

	// ------------------------------------------------------------------ GeckoLib

	@Override
	public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
		controllers.add(new AnimationController<>("main", 3, this::animate));
	}

	private PlayState animate(AnimationTest<RedSquirrel> test) {
		TreeMotion.Kind kind = motionKind();
		test.setControllerSpeed(kind == TreeMotion.Kind.CLING && display() == Display.AUTO ? 0.0F : 1.0F);
		RawAnimation animation = switch (display()) {
			case NONE -> null;
			case IDLE -> IDLE;
			case WALK -> WALK;
			case HOP -> HOP;
			case CLIMB -> CLIMB;
			case DESCEND -> DESCEND;
			case JUMP -> JUMP;
			case EAT -> EAT;
			case ALERT -> ALERT;
			case REST -> REST;
			case AUTO -> switch (kind) {
				case CLIMB, CLING -> CLIMB;
				case DESCEND -> DESCEND;
				case LEAP -> JUMP;
				case RUN -> HOP;
				case GROUND -> test.isMoving() ? getDeltaMovement().horizontalDistance() > HOP_SPEED ? HOP : WALK : switch (action()) {
					case EAT -> EAT;
					case REST -> REST;
					case ALERT -> ALERT;
					default -> IDLE;
				};
			};
		};
		return animation == null ? PlayState.STOP : test.setAndContinue(animation);
	}
}
