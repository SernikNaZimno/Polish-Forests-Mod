"""Species and test models of tools/fauna.

ROE_DEER: the European roe deer (Capreolus capreolus), adults from docs/research/07-fauna-polski-siedliska-zachowania.md
§3.2 and §13: withers 60-90 cm (75 on average), the rump higher than the withers, trunk length about 1.6 x the withers
height with the head, legs about 55% of the withers height, large ears, antlers of the buck 25-30 cm with 3 tines each
(30 cm: 5 px, so the beam ends above the back tine). The neck is carried steeply (58 degrees) and the muzzle clearly
lowered (38 degrees below the horizontal), not level as in a llama. Hitbox 0.7 x 1.1 blocks (docs/04 §0.3, C2).

WILD_BOAR: the wild boar (Sus scrofa), a Polish adult from docs/research/07-fauna-polski-siedliska-zachowania.md §3.4 and
§13 (step FA3): withers 85 cm, the rump lower (76 cm: the wedge profile, high withers and low rump), trunk about 1.4 x the
withers height without the head, legs about 45% of the withers height (short), no visible neck (a short thick neck
carried nearly level, the head and the nape one wedge), a long head (skull and snout 53 cm) carried low, 45 degrees
below the horizontal, the snout ending in a flat disc; small upright ears, a crest of bristles along the withers, a
straight tail of 25 cm hanging down, and the tusks of the boar (the renderer hides them for a sow and a young one).
Hitbox 0.9 x 0.95 blocks (docs/04 "Projekt FA3"); the young (warchlak) are the vanilla young at half the scale.

RED_SQUIRREL: the red squirrel (Sciurus vulgaris), step FA5, the small mammal archetype (smallmammal.py) enlarged twice
(F3, G.3: animals under 30 cm × 2): head and body 20-22 cm (trunk 15, head 4.5 + muzzle 2.5), the tail 17-18 cm of long
hair (about 7 cm wide where it is bushiest), short front legs, large haunches with long hind feet, ears 2.8 cm with
winter tufts up to 3.5 cm (Lurz et al. 2005, Mammalian Species 769; report 07). The tail curls up over the back in an S.
Hitbox 0.4 x 0.45 blocks (docs/04 "Projekt FA5").

EURASIAN_JAY: the Eurasian jay (Garrulus glandarius), step FA6, the bird archetype (bird.py) at real size (34 cm long, so
the F3 enlargement does not apply): body 14 cm long and about 9 cm deep, head 6 cm with a 3 cm stout bill and the erectile
crown feathers, the folded wing about 16 cm with the primaries 8 cm beyond it (wingspan 52-58 cm), the tail 12.5 cm, the
tarsus about 4.5 cm (field guides; report 07). Hitbox 0.4 x 0.3 blocks (docs/04 "Projekt FA6").

AXIS_GAUGE: the calibration model of the fauna game test (step V8): arms turned by known angles about each axis, a chain
of two turned bones, a bone with a rest rotation that an animation moves and turns further (with a turned child that
must follow it), and markers on the left side (+X) and the front, each with a locator at its tip. It lives only in the game test mod (namespace polishforests_probe).
"""
from model import Bone, Cube, Model
from quadruped import Quadruped, build
import bird
import smallmammal

ROE_DEER_SHAPE = Quadruped(
    id='roe_deer',
    withers=75, rump=81, trunk_length=80, chest_share=0.55, chest_depth=34, flank_depth=31, width=28,
    neck_length=44, neck_thickness=18, neck_width=12.5, neck_angle=58,
    head_length=19, head_height=16, head_width=18, muzzle_length=12.5, muzzle_height=12.5, muzzle_width=12.5,
    head_angle=38,
    ear_length=17, ear_width=11, ear_spread=14,
    leg_width=12.5, tail_length=2, antler_length=30, hitbox=(0.7, 1.1))


def roe_deer():
    return build(ROE_DEER_SHAPE, (64, 64))


WILD_BOAR_SHAPE = Quadruped(
    id='wild_boar',
    withers=85, rump=76, trunk_length=115, chest_share=0.55, chest_depth=50, flank_depth=44, width=38,
    neck_length=31, neck_thickness=38, neck_width=27, neck_angle=8,
    head_length=28, head_height=27, head_width=30, muzzle_length=25, muzzle_height=15, muzzle_width=17,
    head_angle=45,
    ear_length=13, ear_width=8, ear_spread=18,
    leg_width=12.5, tail_length=25, antler_length=0, hitbox=(0.9, 0.95), tusk_length=10, mane_height=6.25)


def wild_boar():
    return build(WILD_BOAR_SHAPE, (64, 64))


RED_SQUIRREL_SHAPE = smallmammal.SmallMammal(
    id='red_squirrel', up=2.0,
    trunk_length=15, chest_depth=6.5, hind_depth=7.5, chest_width=6.5, hind_width=8, front_height=4,
    head_length=4.5, head_height=5, head_width=3.5, muzzle_length=2.5, muzzle_height=3, muzzle_width=3, head_angle=6,
    ear_height=3, ear_spread=10, tuft_height=4.5,
    tail_segments=((5, 4.5), (7, 8), (6, 6)), tail_angles=(45, 40, 55), hitbox=(0.4, 0.45))


def red_squirrel():
    return smallmammal.build(RED_SQUIRREL_SHAPE, (64, 64))


EURASIAN_JAY_SHAPE = bird.Bird(
    id='eurasian_jay', body_length=14, body_depth=9.4, body_width=7.8, leg_height=4.7,
    head_length=6.25, head_height=6.25, head_width=5, bill_length=3, crest_length=4.7,
    wing_length=15.6, wing_height=7.8, primaries_length=7.8, primaries_height=4.7,
    tail_length=12.5, tail_width=6.25, hitbox=(0.4, 0.3))


def eurasian_jay():
    return bird.build(EURASIAN_JAY_SHAPE, (64, 64))


# The pose the axis gauge animation 'calib.pose' holds (the game test checks the bone 'anim' in it).
AXIS_GAUGE_ANIM = {'anim': {'rotation': (25.0, -35.0, 15.0), 'position': (1.0, 2.0, -3.0)}}


def axis_gauge():
    bones = [
        Bone('base', (0, 0, 0), cubes=[Cube((-3, 0, -3), (6, 1, 6), 'base')]),
        # X: the front of the arm (-Z) turns down for a positive angle.
        Bone('pitch', (0, 4, 0), 'base', rotation=(30, 0, 0), cubes=[Cube((-0.5, 3.5, -6), (1, 1, 6), 'pitch')],
             locators={'pitch_tip': (0, 4, -6)}),
        # Y: the front turns towards the right side (-X) for a positive angle.
        Bone('yaw', (0, 8, 0), 'base', rotation=(0, 30, 0), cubes=[Cube((-0.5, 7.5, -6), (1, 1, 6), 'yaw')],
             locators={'yaw_tip': (0, 8, -6)}),
        # Z: the top turns towards the left side (+X) for a positive angle.
        Bone('roll', (0, 12, 0), 'base', rotation=(0, 0, 30), cubes=[Cube((-0.5, 12, -0.5), (1, 6, 1), 'roll')],
             locators={'roll_tip': (0, 18, 0)}),
        # All three axes at once and a turned child: the order of the rotations and the child frames.
        Bone('chain', (0, 16, 4), 'base', rotation=(20, 30, 40), cubes=[Cube((-0.5, 15.5, 4), (1, 1, 6), 'chain')],
             locators={'chain_tip': (0, 16, 10)}),
        Bone('chain_child', (0, 16, 10), 'chain', rotation=(-10, 15, -25), cubes=[Cube((-0.5, 15.5, 10), (1, 1, 5), 'chain_child')],
             locators={'chain_child_tip': (0, 16, 15)}),
        # Turned at rest, then moved and turned further by the animation (the animation angles add to the rest angles);
        # its turned child must follow the animated parent.
        Bone('anim', (6, 4, 0), 'base', rotation=(10, 20, -15), cubes=[Cube((5.5, 3.5, -6), (1, 1, 6), 'anim')],
             locators={'anim_tip': (6, 4, -6)}),
        Bone('anim_child', (6, 4, -6), 'anim', rotation=(0, -30, 20), cubes=[Cube((5.5, 3.5, -10), (1, 1, 4), 'anim_child')],
             locators={'anim_child_tip': (6, 4, -10)}),
        # The left side of the model (+X); in the world it must be on the left of the entity.
        Bone('left_marker', (8, 0, 0), 'base', cubes=[Cube((7, 0, -1), (2, 3, 2), 'left')], locators={'left_marker_tip': (8, 3, 0)}),
        # The front of the model (-Z); in the world it must point where the entity looks.
        Bone('front_marker', (0, 0, -8), 'base', cubes=[Cube((-1, 0, -9), (2, 2, 2), 'front')], locators={'front_marker_tip': (0, 2, -8)}),
    ]
    return Model('axis_gauge', bones, (64, 64), (0.6, 1.2))
