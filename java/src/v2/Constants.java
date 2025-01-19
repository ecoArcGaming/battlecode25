package v2;

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
    public static final Random rng = new Random(6147);
    public static final double PERCENT_COIN = 0.5;
    public static final int RESIGN_AFTER = 2500;
    public static final int lowPaintThreshold = 20;
    public static final double MOPPER_SPLIT = 0.4;
    public static final int START_PAINTING_ROUND = 50;

    public static final Set<HashableCoords> primarySRP = Set.of(new HashableCoords(2,0),
            new HashableCoords(1,1),new HashableCoords(2,1),new HashableCoords(3,1),
            new HashableCoords(0,2),new HashableCoords(1,2),new HashableCoords(3,2),
            new HashableCoords(1,3),new HashableCoords(2,3), new HashableCoords(3,3)

    );
}
