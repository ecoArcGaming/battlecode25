package v1;

import battlecode.common.*;

import java.util.Map;
import java.util.Random;
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
    public static final double TOWER_SPLIT = 0.3;
    public static final int RESIGN_AFTER = 2500;
}
