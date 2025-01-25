package v3;

import battlecode.common.*;

import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.HashSet;
import static java.util.Map.entry;

public class Constants {
    public static final Direction[] directions = {
            Direction.NORTH,
            Direction.NORTHEAST,
            Direction.EAST,
            Direction.SOUTHEAST,
            Direction.SOUTH,
            Direction.SOUTHWEST,
            Direction.WEST,
            Direction.NORTHWEST,
    };
    public static final Map<PaintType, Integer> paintLossValues = Map.ofEntries(
            entry(PaintType.ALLY_PRIMARY, 0),
            entry(PaintType.ALLY_SECONDARY, 0),
            entry(PaintType.EMPTY, -1),
            entry(PaintType.ENEMY_PRIMARY, -2),
            entry(PaintType.ENEMY_SECONDARY, -2)
    );
    public static final Random rng = new Random(3407);
    public static final double PERCENT_COIN = 0.45;
    public static final int RESIGN_AFTER = 2005;
    public static final int lowPaintThreshold = 20;
    public static final double INIT_PROBABILITY_DEVELOP = 100;
    public static final double RANDOM_STEP_PROBABILITY = 0.5;
    public static final double DEVELOP_BOT_PROBABILITY_CAP = 0.6;
    public static final double DEVELOP_BOT_PROB_SCALING = 200;
    public static final double DEFENSE_RANGE = 0.3;
    public static final double SRP_DEV_BOT_SPLIT = 0.25;

    public static final int DEV_LIFE_CYCLE_TURNS = 30;
    public static final int SRP_LIFE_CYCLE_TURNS = 50;
    public static final int MIN_PAINT_GIVE = 50;


    public static final Set<HashableCoords> primarySRP = Set.of(new HashableCoords(2,0),
            new HashableCoords(1,1),new HashableCoords(2,1),new HashableCoords(3,1),
            new HashableCoords(0,2),new HashableCoords(1,2),new HashableCoords(3,2),
            new HashableCoords(1,3),new HashableCoords(2,3), new HashableCoords(3,3)

    );


    public static final PaintType[][] paintTowerPattern =
                    {{PaintType.ALLY_SECONDARY, PaintType.ALLY_PRIMARY, PaintType.ALLY_PRIMARY, PaintType.ALLY_PRIMARY, PaintType.ALLY_SECONDARY},
                    {PaintType.ALLY_PRIMARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_PRIMARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_PRIMARY},
                    {PaintType.ALLY_PRIMARY, PaintType.ALLY_PRIMARY, PaintType.EMPTY, PaintType.ALLY_PRIMARY, PaintType.ALLY_PRIMARY},
                    {PaintType.ALLY_PRIMARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_PRIMARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_PRIMARY},
                    {PaintType.ALLY_SECONDARY, PaintType.ALLY_PRIMARY, PaintType.ALLY_PRIMARY, PaintType.ALLY_PRIMARY, PaintType.ALLY_SECONDARY}};

    public static final PaintType[][] moneyTowerPattern =
                    {{PaintType.ALLY_PRIMARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_PRIMARY},
                    {PaintType.ALLY_SECONDARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_PRIMARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_SECONDARY},
                    {PaintType.ALLY_SECONDARY, PaintType.ALLY_PRIMARY, PaintType.EMPTY, PaintType.ALLY_PRIMARY, PaintType.ALLY_SECONDARY},
                    {PaintType.ALLY_SECONDARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_PRIMARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_SECONDARY},
                    {PaintType.ALLY_PRIMARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_PRIMARY}};

    public static final PaintType[][] defenseTowerPattern =
            {{PaintType.ALLY_PRIMARY, PaintType.ALLY_PRIMARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_PRIMARY, PaintType.ALLY_PRIMARY},
                    {PaintType.ALLY_PRIMARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_PRIMARY},
                    {PaintType.ALLY_SECONDARY, PaintType.ALLY_SECONDARY, PaintType.EMPTY, PaintType.ALLY_SECONDARY, PaintType.ALLY_SECONDARY},
                    {PaintType.ALLY_PRIMARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_PRIMARY},
                    {PaintType.ALLY_PRIMARY, PaintType.ALLY_PRIMARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_PRIMARY, PaintType.ALLY_PRIMARY}};
}
