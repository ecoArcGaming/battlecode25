from battlecode25.stubs import *
import random
import constants
import robot_info_codec
import map_info_codec
import s_type
import s_state
import communication
import helper
import math

"""
################################################################################################################
BEGIN GLOBAL VARIABLES
################################################################################################################
"""
# Initialization Variables
turn_count = 0
curr_grid = None
last8 = []  # Acts as queue with max size 16
last_tower = None
soldier_type = s_type.ADVANCE

# Pathfinding Variables
stuck_turn_count = 0
closest_path = -1
in_bug_nav = False
across_wall = None
prev_location = None

# Soldier state variables
soldier_state = s_state.EXPLORING
stored_state = s_state.EXPLORING
fill_empty = None
soldier_msg_cooldown = -1
num_turns_alive = 0  # Variable keeping track of how many turns alive for the soldier lifecycle

# Key Soldier Location variables
enemy_tile = None  # location of an enemy paint/tower for a develop/advance robot to report
ruin_to_fill = None  # location of a ruin that the soldier is filling in
wander_target = None  # target for advance robot to pathfind towards during exploration
enemy_tower = None  # location of enemy tower for attack soldiers to pathfind to
fill_tower_type = None
intermediate_target = None  # used to record short-term robot targets
prev_intermediate = None  # Copy of intermediate target
srp_location = None  # location of SRP robot before it went to get more paint

# Enemy Info variables
remove_paint = None
enemy_target = None  # location of enemy tower/tile for tower to tell

# Tower Spawning Variables
rounds_without_enemy = 0
spawn_queue = []
num_enemy_visits = 0
alert_robots = False
num_soldiers_spawned = 0
send_type_message = False
spawn_direction = None

# Navigation Variables
bot_round_num = 0
opposite_corner = None
seen_paint_tower = False

# Towers Broadcasting Variables
broadcast = False
alert_robots = False
alert_attack_soldiers = False

# BugNav Variables
is_tracing = False
smallest_distance = 10000000
closest_location = None
tracing_dir = None
stopped_location = None
tracing_turns = 0
bug1_turns = 0

# Splasher State Variables
is_low_paint = False
prev_loc_info = None

# Bytecode Tracker
round_num = 0

# Filling SRP State
srp_center = None

"""
################################################################################################################
END GLOBAL VARIABLES
BEGIN SENSING FUNCTIONS
################################################################################################################
"""
def find_nearest_lowest_hp():
    """Finds the opponent robots within actionRadius with the lowest HP and returns its RobotInfo"""
    nearby_robots = sense_nearby_robots(get_type().action_radius_squared, get_team().opponent())
    target_robot = None
    min_health = -1
    for robot in nearby_robots:
        robot_health = robot.get_health()
        if min_health == -1 or min_health > robot_health:
            target_robot = robot
            min_health = robot_health
    return target_robot

def can_build_tower(tower_location):
    """
    Given the MapLocation of a ruin, check if we can eventually build a tower at the ruin
    Returns False if there is enemy paint, or if there is a tower already existing
    Purpose: Check if we should go to this ruin to build on it
    """
    for pattern_tile in sense_nearby_map_infos(tower_location, 8):
        if pattern_tile.has_ruin():
            if can_sense_robot_at_location(pattern_tile.get_map_location()):
                return False
        elif pattern_tile.get_paint().is_enemy():
            return False
    return True

def find_any_ruin(robot_location, nearby_tiles):
    """
    Finds the closest ruin that fits the following criteria
    1. No tower at the ruin
    2. No ally robots directly adjacent to the ruin
    """
    cur_ruin = None
    min_dis = -1
    for tile in nearby_tiles:
        if tile.has_ruin():
            tile_location = tile.get_map_location()
            if (not can_sense_robot_at_location(tile_location) and 
                len(sense_nearby_robots(tile_location, 2, get_team())) < 1):
                ruin_distance = robot_location.distance_squared_to(tile_location)
                if min_dis == -1 or min_dis > ruin_distance:
                    cur_ruin = tile
                    min_dis = ruin_distance
    return cur_ruin

def find_best_ruin(robot_location, nearby_tiles):
    """
    Finds the closest ruin that fits the following criteria
    1. No enemy paint around the tower
    2. No tower at the ruin
    3. No ally robots directly adjacent to the ruin
    """
    cur_ruin = None
    min_dis = -1
    for tile in nearby_tiles:
        if tile.has_ruin():
            tile_location = tile.get_map_location()
            if (not can_sense_robot_at_location(tile_location) and 
                can_build_tower(tile_location) and
                len(sense_nearby_robots(tile_location, 2, get_team())) < 1):
                ruin_distance = robot_location.distance_squared_to(tile_location)
                if min_dis == -1 or min_dis > ruin_distance:
                    cur_ruin = tile
                    min_dis = ruin_distance
    return cur_ruin

def find_paintable_tile(location, range_squared):
    """
    Finds a paintable tile that is within a specific range of location and returns the MapInfo of that tile
    Paintable: empty paint or incorrect allied paint
    If none are found, return null
    """
    for pattern_tile in sense_nearby_map_infos(location, range_squared):
        if (can_paint(pattern_tile.get_map_location()) and
            (pattern_tile.get_paint() == PaintType.EMPTY or
             pattern_tile.get_mark() != pattern_tile.get_paint() and pattern_tile.get_mark() != PaintType.EMPTY)):
            return pattern_tile
    return None

def find_paintable_ruin_tile(ruin_location, ruin_pattern):
    """
    Finds a paintable tile that is within a specific range of tower and returns the MapInfo of that tile
    Paintable: tile with paint different than needed
    If none are found, return null
    """
    # Iterate through the 5x5 area around a ruin
    for i in range(-2, 3):
        for j in range(-2, 3):
            pattern_tile = ruin_location.translate(i, j)
            if can_paint(pattern_tile) and ruin_pattern[i+2][j+2] != sense_map_info(pattern_tile).get_paint():
                return [i, j]
    return None

def get_movable_empty_tiles():
    """
    Finds tiles adjacent to rc that
    1. Can be moved to
    2. Have no paint on them
    3. Hasn't been at this tile in the last 8 tiles it has moved to
    Returns a list of MapInfo for these tiles
    """
    adjacent_tiles = sense_nearby_map_infos(2)
    valid_adjacent = []
    for adjacent_tile in adjacent_tiles:
        if (adjacent_tile.get_paint() == PaintType.EMPTY and 
            adjacent_tile.is_passable() and
            adjacent_tile.get_map_location() not in last8):
            valid_adjacent.append(adjacent_tile)
    return valid_adjacent

def get_movable_painted_tiles():
    """
    Finds tiles adjacent to rc that
    1. Can be moved to
    2. Has paint on them
    3. Hasn't been at this tile in the last 8 tiles it has moved to
    Returns a list of MapInfo for these tiles
    """
    adjacent_tiles = sense_nearby_map_infos(2)
    valid_adjacent = []
    for adjacent_tile in adjacent_tiles:
        if (adjacent_tile.get_paint().is_ally() and 
            adjacent_tile.is_passable() and
            adjacent_tile.get_map_location() not in last8):
            valid_adjacent.append(adjacent_tile)
    return valid_adjacent

def tower_in_range(range_val, ally=None):
    """
    Returns RobotInfo of a tower if there is a tower with a range of radius
    ally = True: search for allied towers, and vice versa
    If ally is not passed, then we search for all towers
    Returns None if no tower is within range
    """
    if ally is None:
        robots_in_range = sense_nearby_robots(range_val)
    else:
        team = get_team() if ally else get_team().opponent()
        robots_in_range = sense_nearby_robots(range_val, team)
        
    for robot in robots_in_range:
        if robot.get_type().is_tower_type():
            return robot
    return None

def find_enemy_paint(nearby_tiles):
    """Returns map info of location of enemy paint"""
    for tile in nearby_tiles:
        if tile.get_paint().is_enemy():
            return tile
    return None

def count_empty_around(center):
    """Counts the number of empty, passable tiles in a 3x3 area centered at center, assuming it is all visible"""
    surrounding_tiles = sense_nearby_map_infos(center, 2)
    count = 0
    for surrounding_tile in surrounding_tiles:
        if (surrounding_tile.get_paint() == PaintType.EMPTY and 
            surrounding_tile.is_passable() and
            not can_sense_robot_at_location(surrounding_tile.get_map_location())):
            count += 1
    return count

def is_robot(robot_id):
    """Checks if a Robot is a robot by ID"""
    if can_sense_robot(robot_id):
        bot = sense_robot(robot_id)
        return bot.get_type().is_robot_type()
    return False

def is_tower(robot_id):
    """Checks if a Robot is a tower by ID"""
    if can_sense_robot(robot_id):
        bot = sense_robot(robot_id)
        return bot.get_type().is_tower_type()
    return False

def score_splasher_tiles():
    """Scores tiles that decides where a splasher should go"""
    nearby_tiles = sense_nearby_map_infos()
    # hash the tiles
    for tile in nearby_tiles:
        loc = tile.get_map_location()
        paint = tile.get_paint()
        if paint.is_enemy():
            return tile
    return None

def score_splash(tile):
    """Scores based on paintTypes of tiles within splasher radius"""
    out = 0
    loc = tile.get_map_location()
    x = loc.x
    y = loc.y
    up = get_map_height()
    right = get_map_width()

    # Check all tiles in splash radius
    splash_coords = [
        (x, y-2), (x-1, y-1), (x, y-1), (x+1, y-1),
        (x-2, y), (x-1, y), (x, y), (x+1, y), (x+2, y),
        (x-1, y+1), (x, y+1), (x+1, y+1), (x, y+2)
    ]

    for tx, ty in splash_coords:
        if 0 <= tx < right and 0 <= ty < up:
            out += curr_grid[tx][ty]

    return out

def is_in_defense_range(ruin_loc):
    """Checks whether a ruin is in the middle area of the map, in which a defense tower is built"""
    x_lower = int(get_map_width() * constants.DEFENSE_RANGE)
    y_lower = int(get_map_height() * constants.DEFENSE_RANGE)
    x_higher = int(get_map_width() * (1 - constants.DEFENSE_RANGE))
    y_higher = int(get_map_height() * (1 - constants.DEFENSE_RANGE))
    x = ruin_loc.x
    y = ruin_loc.y
    return x_lower <= x <= x_higher and y_lower <= y <= y_higher

def score_tile(tile, care_about_enemy):
    """Score a tile based on various factors"""
    surrounding_tiles = sense_nearby_map_infos(tile, 2)
    count = 30
    for surrounding_tile in surrounding_tiles:
        if surrounding_tile.get_paint().is_enemy() and care_about_enemy:
            count += 5
        if surrounding_tile.get_paint() == PaintType.EMPTY and surrounding_tile.is_passable():
            count += 3
        if not surrounding_tile.is_passable():
            count -= 2
        surrounding_location = surrounding_tile.get_map_location()
        if can_sense_robot_at_location(surrounding_location):
            if sense_robot_at_location(surrounding_location).get_team() == get_team():
                count -= 3
    return count

def conflicts_srp():
    """Check for SRP conflicts"""
    all_tiles = sense_nearby_map_infos()
    for surrounding_tile in all_tiles:
        if surrounding_tile.get_mark().is_ally():
            south = surrounding_tile.get_map_location().add(Direction.SOUTH)
            southwest = south.add(Direction.WEST)
            if can_sense_location(south):
                if not sense_map_info(south).has_ruin():
                    if can_sense_location(southwest):
                        if not sense_map_info(southwest).has_ruin():
                            return True
                    else:
                        return True
            else:
                return True
    return False

"""
################################################################################################################
END SENSING FUNCTIONS
BEGIN PATHFINDING FUNCTIONS
################################################################################################################
"""

better_explore_directions = [[-2, -2], [-2, 0], [-2, 2], [0, -2], [0, 2], [2, -2], [2, 0], [2, 2]]


def less_original_pathfind(target):
    """
    Returns a Direction that brings rc closer to target
    Prioritizes distance first, then type of paint (ally tiles, then neutral tiles, then enemy tiles)
    Exception: does not move onto a tile if doing so will kill itself
    If the robot cannot move, return null
    """
    min_distance = -1
    best_paint_type = PaintType.EMPTY
    cur_location = get_location()
    best_location = None
    
    for dir in constants.directions:
        if can_move(dir):
            adj_location = sense_map_info(cur_location.add(dir))
            distance = adj_location.get_map_location().distance_squared_to(target)
            adj_type = adj_location.get_paint()
            
            if distance < min_distance or min_distance == -1:
                min_distance = distance
                best_paint_type = adj_type
                best_location = adj_location
            elif distance == min_distance:
                adj_paint_type = adj_location.get_paint()
                if ((best_paint_type.is_enemy() and not adj_paint_type.is_enemy()) or
                    (best_paint_type == PaintType.EMPTY and adj_paint_type.is_ally())):
                    best_paint_type = adj_location.get_paint()
                    best_location = adj_location
                    
    if min_distance != -1:
        return cur_location.direction_to(best_location.get_map_location())
    else:
        return None

def original_pathfind(target):
    """
    Returns a Direction that brings rc closer to target
    Prioritizes going along the three closest directions pointing to the target
    Then, it finds any painted tile adjacent to the robot
    Then, it just finds any tile adjacent to the robot that the robot can move on and null otherwise
    """
    curr_dir = get_location().direction_to(target)
    left = curr_dir.rotate_left()
    right = curr_dir.rotate_right()
    
    if can_move(curr_dir):
        return curr_dir
    elif can_move(left):
        return left
    elif can_move(right):
        return right

    all_directions = Direction.all_directions()
    for dir in all_directions:
        if can_move(dir) and get_location().add(curr_dir) not in last8:
            return dir

    for dir in all_directions:
        if can_move(dir):
            return dir

    return None

def painted_pathfind(target):
    """
    Returns a Direction that brings rc closer to target, going along painted areas
    Prioritizes going along the three closest directions pointing to the target
    Then, it finds any painted tile adjacent to the robot
    Then, it just finds any tile adjacent to the robot that the robot can move on and null otherwise
    """
    curr_dir = get_location().direction_to(target)
    left = curr_dir.rotate_left()
    right = curr_dir.rotate_right()

    if can_move(curr_dir) and sense_map_info(get_location().add(curr_dir)).get_paint().is_ally():
        return curr_dir
    elif can_move(left) and sense_map_info(get_location().add(left)).get_paint().is_ally():
        return left
    elif can_move(right) and sense_map_info(get_location().add(right)).get_paint().is_ally():
        return right

    all_directions = Direction.all_directions()
    for dir in all_directions:
        if can_move(dir):
            if (sense_map_info(get_location().add(dir)).get_paint().is_ally() and 
                get_location().add(curr_dir) not in last8):
                return dir

    for dir in all_directions:
        if can_move(dir):
            return dir

    return None

def return_to_tower():
    """Returns a Direction representing the direction to move to the closest tower in vision or the last one remembered"""
    if get_paint() < 6:
        return painted_pathfind(last_tower.get_map_location())
    return original_pathfind(last_tower.get_map_location())

def tiebreak_unpainted(valid_adjacent):
    """
    Given an ArrayList of tiles to move to, randomly chooses a tile, weighted by how many tiles are unpainted & unoccupied
    in the 3x3 area centered at the tile behind the tile (relative to the robot)
    Returns null if everything appears painted or if validAdjacent is empty
    """
    cum_sum = 0
    num_tiles = len(valid_adjacent)
    weighted_adjacent = [0] * num_tiles
    
    for i in range(num_tiles):
        adj_location = valid_adjacent[i].get_map_location()
        cum_sum = cum_sum + 5 * count_empty_around(adj_location.add(get_location().direction_to(adj_location)))
        weighted_adjacent[i] = cum_sum
            
    if cum_sum == 0:
        return None
    else:
        random_value = random.randint(0, cum_sum - 1)
        for i in range(num_tiles):
            if random_value < weighted_adjacent[i]:
                return valid_adjacent[i].get_map_location()
    return None

def explore_unpainted():
    """
    Returns a Direction representing the direction of an unpainted block
    Smartly chooses an optimal direction among adjacent, unpainted tiles using the method tiebreakUnpainted
    If all surrounding blocks are painted, looks past those blocks (ignoring passability of adjacent tiles)
    and pathfinds to a passable tile, chosen by tiebreakUnpainted
    """
    valid_adjacent = get_movable_empty_tiles()
    if not valid_adjacent:
        cur_loc = get_location()
        for dir in constants.directions:
            farther_location = cur_loc.add(dir)
            if on_the_map(farther_location):
                farther_info = sense_map_info(farther_location)
                if farther_info.is_passable():
                    valid_adjacent.append(farther_info)
                        
    best_location = tiebreak_unpainted(valid_adjacent)
    if best_location is None:
        return None
            
    move_dir = original_pathfind(best_location)
    if move_dir is not None:
        return move_dir
            
    return None

def better_explore(cur_location, target, care_about_enemy):
    """
    How we choose exploration weights:
    Check each of the 8 blocks around the robot
    +20 if block is closer to target than starting point
    +10 if block is equidistant to target than starting point
    For each block, check the 3x3 area centered at that block
    +3 for each paintable tile (including ruins)
    -3 for each tile with an ally robot (including towers)
    
    if care_about_enemy = true, +5 for enemy paint
    """
    global intermediate_target
    break_score = 0
    if intermediate_target is not None:
        potential_break = MapLocation(cur_location.x - 2, cur_location.y - 2)
        if on_the_map(potential_break):
            break_score = score_tile(potential_break, False)
            
        potential_break = MapLocation(cur_location.x + 2, cur_location.y - 2)
        if on_the_map(potential_break):
            break_score = max(break_score, score_tile(potential_break, False))
                
        potential_break = MapLocation(cur_location.x - 2, cur_location.y + 2)
        if on_the_map(potential_break):
            break_score = max(break_score, score_tile(potential_break, False))
                
        potential_break = MapLocation(cur_location.x + 2, cur_location.y + 2)
        if on_the_map(potential_break):
            break_score = max(break_score, score_tile(potential_break, False))
                
        if break_score > 45:
            intermediate_target = None
            reset_variables()
            
    # Only update intermediate target locations when we have reached one already or if we don't have one at all
    if (intermediate_target is None or 
        cur_location.equals(intermediate_target) or
        (cur_location.is_within_distance_squared(intermediate_target, 2) and
         not sense_map_info(intermediate_target).is_passable())):
        
        if cur_location.equals(intermediate_target):
            reset_variables()
            
        cum_sum = 0
        # Calculate a score for each target
        min_score = -1
        weighted_adjacent = [0] * 8
        cur_distance = cur_location.distance_squared_to(target)
        
        for i in range(8):
            score = 0
            possible_target = cur_location.translate(better_explore_directions[i][0], better_explore_directions[i][1])
            if on_the_map(possible_target):
                score = score_tile(possible_target, care_about_enemy)
                new_distance = possible_target.distance_squared_to(target)
                if cur_distance > new_distance:
                    score = score + 20
                elif cur_distance == new_distance:
                    score = score + 10
                    
            if min_score == -1 or score < min_score:
                min_score = score
            cum_sum = cum_sum + score
            weighted_adjacent[i] = cum_sum

        # Normalize by subtracting each score by the same amount so that one score is equal to 1
        if min_score != 0:
            min_score = min_score - 1
        weighted_adjacent[0] = weighted_adjacent[0] - min_score * 1
        weighted_adjacent[1] = weighted_adjacent[1] - min_score * 2
        weighted_adjacent[2] = weighted_adjacent[2] - min_score * 3
        weighted_adjacent[3] = weighted_adjacent[3] - min_score * 4
        weighted_adjacent[4] = weighted_adjacent[4] - min_score * 5
        weighted_adjacent[5] = weighted_adjacent[5] - min_score * 6
        weighted_adjacent[6] = weighted_adjacent[6] - min_score * 7
        weighted_adjacent[7] = weighted_adjacent[7] - min_score * 8

        if cum_sum != 0:
            random_value = random.randint(0, weighted_adjacent[7] - 1)
            for i in range(8):
                if random_value < weighted_adjacent[i]:
                    intermediate_target = cur_location.translate(
                        better_explore_directions[i][0], better_explore_directions[i][1])
                    break

    if intermediate_target is None:
        return None
        
    if (intermediate_target is not None and 
        prev_intermediate != intermediate_target):
        global stuck_turn_count
        stuck_turn_count = 0
            
    move_dir = pathfind(intermediate_target)
    if move_dir is not None:
        return move_dir
            
    return None

def random_walk():
    """Does a random walk"""
    all_directions = Direction.all_directions()
    for _ in range(5):
        dir = all_directions[int(random.random() * len(all_directions))]
        if can_move(dir) and get_location().add(dir) not in last8:
            return dir
    return None

def find_own_corner():
    """Find and move towards own corner"""
    global opposite_corner, prev_intermediate, intermediate_target
    set_indicator_string(f"GETTING UNSTUCK {opposite_corner}")
    if random.random() < constants.RANDOM_STEP_PROBABILITY:
        random_dir = random_walk()
        if random_dir is not None:
            return random_dir
                
    prev_intermediate = intermediate_target
    intermediate_target = None
        
    if (opposite_corner is None or 
        get_location().distance_squared_to(opposite_corner) <= 8):
        corner = random.random()
        x = get_location().x
        y = get_location().y
        target_x = target_y = 0
        
        if corner <= 0.333:
            if x < get_map_width() / 2:
                target_x = get_map_width()
            if y > get_map_height() / 2:
                target_y = get_map_height()
        if corner >= 0.666:
            if x > get_map_width() / 2:
                target_x = get_map_width()
            if y < get_map_height() / 2:
                target_y = get_map_height()
                       
        opposite_corner = MapLocation(target_x, target_y)
            
    return pathfind(opposite_corner)

def get_unstuck():
    """Finds the furthest corner and move towards it"""
    global opposite_corner
    if random.random() < constants.RANDOM_STEP_PROBABILITY:
        return random_walk()
    else:
        if (opposite_corner is None or 
            get_location().distance_squared_to(opposite_corner) <= 20):
            x = get_location().x
            y = get_location().y
            target_x = get_map_width() if x < get_map_width() / 2 else 0
            target_y = get_map_height() if y < get_map_height() / 2 else 0
            opposite_corner = MapLocation(target_x, target_y)
                
        return pathfind(opposite_corner)

def better_unstuck():
    """Better version of getting unstuck"""
    global opposite_corner, prev_intermediate, intermediate_target
    set_indicator_string(f"GETTING UNSTUCK {opposite_corner}")
    prev_intermediate = intermediate_target
    intermediate_target = None
        
    if (opposite_corner is None or 
        get_location().distance_squared_to(opposite_corner) <= 20):
        corner = random.random()
        x = get_location().x
        y = get_location().y
        target_x = target_y = 0
        
        if corner <= 0.666:
            if x < get_map_width() / 2:
                target_x = get_map_width()
            if y > get_map_height() / 2:
                target_y = get_map_height()
        if corner >= 0.333:
            if x > get_map_width() / 2:
                target_x = get_map_width()
            if y < get_map_height() / 2:
                target_y = get_map_height()
                
        opposite_corner = MapLocation(target_x, target_y)
            
    return pathfind(opposite_corner)

def bug1(target):
    """bug1 pathfinding algorithm"""
    global is_tracing, tracing_dir, bug1_turns, closest_location, smallest_distance
    if not is_tracing:
        # proceed as normal
        dir = get_location().direction_to(target)
        if can_move(dir):
            return dir
        else:
            is_tracing = True
            tracing_dir = dir
            bug1_turns = 0
    else:
        # tracing mode
        # need a stopping condition - this will be when we see the closestLocation again
        if ((get_location().equals(closest_location) and bug1_turns != 0) or 
            bug1_turns > 2 * (get_map_width() + get_map_height())):
            # returned to closest location along perimeter of the obstacle
            reset_variables()
        else:
            # keep tracing
            # update closestLocation and smallestDistance
            dist_to_target = get_location().distance_squared_to(target)
            if dist_to_target < smallest_distance:
                smallest_distance = dist_to_target
                closest_location = get_location()

            # go along perimeter of obstacle
            if can_move(tracing_dir):
                # move forward and try to turn right
                return_dir = tracing_dir
                tracing_dir = tracing_dir.rotate_right()
                tracing_dir = tracing_dir.rotate_right()
                bug1_turns = bug1_turns + 1
                return return_dir
            else:
                # turn left because we cannot proceed forward
                # keep turning left until we can move again
                for _ in range(8):
                    tracing_dir = tracing_dir.rotate_left()
                    if can_move(tracing_dir):
                        return_dir = tracing_dir
                        tracing_dir = tracing_dir.rotate_right()
                        tracing_dir = tracing_dir.rotate_right()
                        bug1_turns = bug1_turns + 1
                        return return_dir
    return None

def pathfind(target):
    """Main pathfinding method that combines different strategies"""
    global stuck_turn_count, closest_path, in_bug_nav, across_wall
    cur_location = get_location()
    dist = cur_location.distance_squared_to(target)
    if dist == 0:
        reset_variables()
        
    if stuck_turn_count < 5 and not in_bug_nav:
        if dist < closest_path:
            closest_path = dist
        elif closest_path != -1:
            stuck_turn_count = stuck_turn_count + 1
        else:
            closest_path = dist
        return less_original_pathfind(target)
        
    elif in_bug_nav:
        # If robot has made it across the wall to the other side
        # Then, just pathfind to the place we are going to
        if get_location().distance_squared_to(across_wall) == 0:
            reset_variables()
            return None
        # Otherwise, just call bugnav
        return bug1(across_wall)
        
    else:
        in_bug_nav = True
        stuck_turn_count = 0
        to_target = cur_location.direction_to(target)
        new_loc = cur_location.add(to_target)
        
        if can_sense_location(new_loc):
            if sense_map_info(new_loc).is_wall():
                new_loc = new_loc.add(to_target)
                if can_sense_location(new_loc):
                    if sense_map_info(new_loc).is_wall():
                        new_loc = new_loc.add(to_target)
                        if can_sense_location(new_loc):
                            if not sense_map_info(new_loc).is_wall():
                                across_wall = new_loc
                                return None
                    else:
                        across_wall = new_loc
                        return None
            else:
                across_wall = new_loc
                return None
                
        across_wall = target
        return None

def random_painted_walk():
    """Random walk along painted tiles"""
    all_directions = get_movable_painted_tiles()
    if not all_directions:
        return None
    dir = get_location().direction_to(all_directions[int(random.random() * len(all_directions))].get_map_location())
    if can_move(dir):
        return dir
    return None

"""
################################################################################################################
END PATHFINDING FUNCTIONS
BEGIN MONEY TOWER FUNCTIONS
################################################################################################################
"""
def money_tower_read_new_messages():
    """Reads new messages and does stuff"""
    global rounds_without_enemy, alert_robots, enemy_target, enemy_tower, broadcast, num_enemy_visits
    # Looks at all incoming messages
    for message in read_messages(get_round_num() - 1):
        bytes_msg = message.get_bytes()
        if communication.is_robot_info(bytes_msg):
            msg = robot_info_codec.decode(bytes_msg)
        else:
            msg = map_info_codec.decode(bytes_msg)
            # Check if message is enemy tower
            if msg.has_ruin():
                rounds_without_enemy = 0
                alert_robots = True
                enemy_target = msg
                enemy_tower = msg
            # Check if message is enemy paint
            elif msg.get_paint().is_enemy():
                rounds_without_enemy = 0
                if is_robot(message.get_sender_id()):
                    broadcast = True
                    num_enemy_visits = num_enemy_visits + 1  # Increases probability of spawning a splasher
                # If tower receives message from tower, just alert the surrounding bots
                alert_robots = True
                # Update enemy tile regardless
                enemy_target = msg

def tower_read_new_messages():
    """Reads new messages and does stuff"""
    global rounds_without_enemy, broadcast, alert_attack_soldiers, alert_robots, enemy_target, enemy_tower
    global spawn_queue, num_enemy_visits
    # Looks at all incoming messages
    for message in read_messages(get_round_num() - 1):
        bytes_msg = message.get_bytes()
        if communication.is_robot_info(bytes_msg):
            msg = robot_info_codec.decode(bytes_msg)
        else:
            msg = map_info_codec.decode(bytes_msg)
            # Check if message is enemy tower
            if msg.has_ruin():
                rounds_without_enemy = 0
                # If tower receives enemy message from robots, broadcast the information to other
                # towers. Additionally, spawn a splasher and a mopper
                if is_robot(message.get_sender_id()):
                    broadcast = True
                    alert_attack_soldiers = True
                    spawn_queue.append(3)  # Spawns a mopper
                    spawn_queue.append(4)  # Spawns a splasher
                    num_enemy_visits += 1  # Increases probability of spawning a splasher

                # If tower receives message from tower, just alert the surrounding bots to target the enemy paint
                alert_robots = True

                # Update enemy tile regardless
                enemy_target = msg
                enemy_tower = msg

            # Check if message is enemy paint
            elif msg.get_paint().is_enemy():
                rounds_without_enemy = 0
                # If tower receives enemy message from robots, broadcast the information to other
                # towers. Additionally, spawn a splasher and a mopper
                broadcast = True
                if random.random() <= 0.5:
                    spawn_queue.append(4)  # Spawns a splasher
                else:
                    spawn_queue.append(3)  # Spawns a mopper
                num_enemy_visits = num_enemy_visits + 1  # Increases probability of spawning a splasher

                # If tower receives message from tower, just alert the surrounding bots
                alert_robots = True

                # Update enemy tile regardless
                enemy_target = msg

def build_if_possible(robot_type, location):
    """Builds a robot of type robot_type at location"""
    if can_build_robot(robot_type, location):
        build_robot(robot_type, location)

def add_random_to_queue():
    """Builds an advance/develop soldier, weighted by how long it has been since the tower last saw a robot"""
    global num_enemy_visits, num_soldiers_spawned, spawn_queue
    if (random.random() < num_enemy_visits * 0.2 or 
        (num_soldiers_spawned > constants.SPLASHER_CUTOFF and random.random() < constants.SPLASHER_SOLDIER_SPLIT)):
        spawn_queue.append(4)
        num_enemy_visits = 0
    else:
        num_soldiers_spawned = num_soldiers_spawned + 1
        # odds of explore robot increases linearly from 30-70 to 60-40
        if random.random() < min((rounds_without_enemy + constants.INIT_PROBABILITY_DEVELOP) / constants.DEVELOP_BOT_PROB_SCALING,
                               constants.DEVELOP_BOT_PROBABILITY_CAP):
            spawn_queue.append(0)
        else:
            spawn_queue.append(1)

def fire_attack_if_possible(location):
    """Fires an attack at location if possible"""
    if can_attack(location):
        attack(location)

def attack_lowest_robot():
    """Attacks the robot with the lowest HP within attack range"""
    nearest_low_bot = find_nearest_lowest_hp()
    if nearest_low_bot is not None:
        fire_attack_if_possible(nearest_low_bot.get_location())

def aoe_attack_if_possible():
    """Does an AOE attack if possible"""
    if can_attack(None):
        attack(None)

def create_soldier():
    """Creates a soldier at location NORTH if possible"""
    added_dir = get_location().add(spawn_direction)
    if start_square_covered():
        if can_build_robot(UnitType.MOPPER, added_dir):
            build_robot(UnitType.MOPPER, added_dir)
            return
    if can_build_robot(UnitType.SOLDIER, added_dir):
        build_robot(UnitType.SOLDIER, added_dir)
        send_type_message = True

def create_mopper():
    """Creates a mopper at location NORTH if possible"""
    added_dir = get_location().add(spawn_direction)
    if can_build_robot(UnitType.MOPPER, added_dir):
        build_robot(UnitType.MOPPER, added_dir)
        send_type_message = True

def create_splasher():
    """Creates a splasher at the north"""
    added_dir = get_location().add(spawn_direction)
    if can_build_robot(UnitType.SPLASHER, added_dir):
        build_robot(UnitType.SPLASHER, added_dir)
        send_type_message = True

def send_type_message(robot_type):
    """Send message to the robot indicating what type of bot it is"""
    added_dir = get_location().add(spawn_direction)
    if can_sense_robot_at_location(added_dir) and can_send_message(added_dir):
        send_message(added_dir, robot_type)
        # If robot is an attack soldier or mopper, send enemy tile location as well
        if robot_type in [4, 3, 2]:
            communication.send_map_information(enemy_target, added_dir)
    send_type_message = False
    spawn_queue.pop(0)

def start_square_covered():
    """Checks to see if that spawning square is covered with enemy paint"""
    return sense_map_info(get_location().add(spawn_direction)).get_paint().is_enemy()

def spawn_direction():
    """Finds spawning direction for a given tower"""
    height = get_map_height()
    width = get_map_width()
    loc = get_location()
    
    # Prefer spawning towards center
    center_x = width // 2
    center_y = height // 2
    
    best_dir = None
    min_dist = float('inf')
    
    for dir in constants.DIRECTIONS:
        new_loc = loc.add(dir)
        if can_build_robot(UnitType.SOLDIER, new_loc):
            dist = abs(new_loc.x - center_x) + abs(new_loc.y - center_y)
            if dist < min_dist:
                min_dist = dist
                best_dir = dir
    
    spawn_direction = best_dir

"""
message all nearby robots about lastest enemyTile
"""
def broadcast_nearby_bots():
    """Message all nearby robots about latest enemy_tile"""
    global alert_robots, alert_attack_soldiers
    for bot in sense_nearby_robots():
        # Only sends messages to moppers and splashers
        if can_send_message(bot.get_location()) and is_attack_type(bot):
            send_message(bot.get_location(), map_info_codec.MapInfoCodec.encode(enemy_target))
    alert_robots = False
    alert_attack_soldiers = False

"""
message all nearby robots about latest enemyTower
"""
def broadcast_enemy_tower():
    """Message all nearby robots about latest enemy_tower"""
    for bot in sense_nearby_robots():
        # Only sends messages to moppers and splashers
        if can_send_message(bot.get_location()):
            send_message(bot.get_location(), map_info_codec.MapInfoCodec.encode(enemy_tower))

"""
Check if robot is an attack type (mopper, splasher, or soldier if alert_attack_soldiers is True)
"""
def is_attack_type(bot):
    """Check if robot is an attack type"""
    return (bot.get_type() == UnitType.MOPPER or 
            bot.get_type() == UnitType.SPLASHER or 
            (bot.get_type() == UnitType.SOLDIER and alert_attack_soldiers))

"""
Count number of tiles with enemy paint in sensing range
"""
def count_enemy_paint():
    """Count number of tiles with enemy paint"""
    count = 0
    for map_info in sense_nearby_map_infos():
        if map_info.get_paint().is_enemy():
            count += 1
    return count

"""
################################################################################################################
END TOWER FUNCTIONS
BEGIN ROBOT FUNCTIONS
################################################################################################################
"""
def low_paint_behavior():
    """Method for robot behavior when they are low on paint"""
    global is_low_paint, last_tower
    is_low_paint = True
    # If last tower is null, then just random walk on paint
    for enemy_robot in sense_nearby_robots(-1, get_team().opponent()):
        if enemy_robot.get_type().is_tower_type():
            if can_attack(enemy_robot.get_location()):
                attack(enemy_robot.get_location())
                break

    if last_tower is None:
        move_to = random_painted_walk()
        if move_to is not None and can_move(move_to):
            move(move_to)
        return

    dir = return_to_tower()
    if dir is not None:
        move(dir)

    # Otherwise, pathfind to the tower
    tower_location = last_tower.get_map_location()
    complete_ruin_if_possible(tower_location)
    amt_to_transfer = get_paint() - get_type().paint_capacity
    
    if can_sense_robot_at_location(tower_location):
        tower_paint = sense_robot_at_location(tower_location).paint_amount
        if get_paint() < 5 and can_transfer_paint(tower_location, -tower_paint) and tower_paint > constants.MIN_PAINT_GIVE:
            transfer_paint(tower_location, -tower_paint)

    if can_transfer_paint(tower_location, amt_to_transfer):
        transfer_paint(tower_location, amt_to_transfer)

def check_allied_tower(loc):
    """Given MapInfo loc, return True if there is an allied tower at loc"""
    location = loc.get_map_location()
    if loc.has_ruin() and can_sense_robot_at_location(location) and sense_robot_at_location(location).get_team() == get_team():
        return True
    return False

def update_last_paint_tower():
    """Updates the lastTower variable to any allied paint tower currently in range"""
    global last_tower, seen_paint_tower, get_location, get_team
    min_distance = -1
    local_last_tower = None
    for loc in sense_nearby_map_infos():
        if check_allied_tower(loc):
            tower_type = sense_robot_at_location(loc.get_map_location()).get_type()
            if tower_type.get_base_type() == UnitType.LEVEL_ONE_PAINT_TOWER.get_base_type():
                seen_paint_tower = True
                distance = loc.get_map_location().distance_squared_to(get_location())
                if min_distance == -1 or min_distance > distance:
                    local_last_tower = loc
                    min_distance = distance

    if min_distance != -1:
        last_tower = local_last_tower
    elif last_tower is not None and last_tower.get_map_location().is_within_distance_squared(get_location(), 20):
        last_tower = None

def has_low_paint(threshold):
    """Check if the robot  has less paint than the threshold"""
    return get_paint() < threshold

def gen_tower_type(ruin_location):
    """Returns a random tower with a Constants.TOWER_SPLIT split and defense tower only if in range"""
    if get_num_towers() <= 3:
        return UnitType.LEVEL_ONE_MONEY_TOWER

    prob_defense = min(1, (get_num_towers()) / (get_map_height() + get_map_width()) * 5)
    prob_from_center = 1 - 2.5 * (abs(get_map_width() / 2 - ruin_location.x) + abs(get_map_height() / 2 - ruin_location.y)) / (get_map_height() + get_map_width())
    haha = random.random()
    
    if haha < prob_defense * prob_from_center:
        return UnitType.LEVEL_ONE_DEFENSE_TOWER
            
    hehe = random.random()
    return (UnitType.LEVEL_ONE_PAINT_TOWER 
            if hehe < min((get_num_towers()) / math.sqrt(get_map_height() + get_map_width()), constants.PERCENT_PAINT) 
            else UnitType.LEVEL_ONE_MONEY_TOWER)

def complete_ruin_if_possible(ruin_location):
    """Completes the ruin at the given location if possible"""
    if can_complete_tower_pattern(UnitType.LEVEL_ONE_MONEY_TOWER, ruin_location):
        complete_tower_pattern(UnitType.LEVEL_ONE_MONEY_TOWER, ruin_location)
    if can_complete_tower_pattern(UnitType.LEVEL_ONE_PAINT_TOWER, ruin_location):
        complete_tower_pattern(UnitType.LEVEL_ONE_PAINT_TOWER, ruin_location)
    if can_complete_tower_pattern(UnitType.LEVEL_ONE_DEFENSE_TOWER, ruin_location):
        complete_tower_pattern(UnitType.LEVEL_ONE_DEFENSE_TOWER, ruin_location)

def reset_variables():
    """
    Resets pathfinding variables
    Meant to be called when the robot has found else to do
    """
    global is_tracing, smallest_distance, closest_location, tracing_dir, stuck_turn_count
    global closest_path, fill_tower_type, stopped_location, tracing_turns, bug1_turns
    global in_bug_nav, across_wall
    
    is_tracing = False
    smallest_distance = 10000000
    closest_location = None
    tracing_dir = None
    stuck_turn_count = 0
    closest_path = -1
    fill_tower_type = None
    stopped_location = None
    tracing_turns = 0
    bug1_turns = 0
    in_bug_nav = False
    across_wall = None

"""
END ROBOT FUNCTIONS
BEGIN SPLASHER FUNCTIONS
"""
def splasher_receive_last_message():
    """Receives and processes messages from the last round"""
    for message in read_messages(get_round_num() - 1):
        bytes = message.get_bytes()
        # Skip splasher type message
        if bytes == 4:
            continue
            
        if communication.is_robot_info(bytes):
            robot_info = robot_info_codec.RobotInfoCodec.decode(bytes)
            continue
        else:
            map_info = map_info_codec.MapInfoCodec.decode(bytes)
            # If enemy paint, store enemy paint
            if map_info.get_paint().is_enemy():
                robot_loc = get_location()
                if (remove_paint is None or 
                    robot_loc.distance_squared_to(map_info.get_map_location()) < 
                    robot_loc.distance_squared_to(remove_paint.get_map_location())):
                    remove_paint = map_info
                    reset_variables()
            # If enemy tower, go to enemy tower location
            elif map_info.has_ruin():
                if remove_paint is None:
                    remove_paint = map_info
                    reset_variables()
"""
END SPLASHER FUNCTIONS
BEGIN SOLDIER FUNCTIONS
"""
def soldier_low_paint_behavior():
    """Method for soldier to do when low on paint"""
    low_paint_behavior()
    if get_paint() > constants.LOW_PAINT_THRESHOLD:
        if soldier_state != stored_state:
            soldier_state = stored_state
        elif ruin_to_fill is not None:
            soldier_state = s_state.FILLINGTOWER
        else:
            soldier_state = s_state.STUCK
        reset_variables()

def paint_if_possible(paint_tile=None, paint_location=None):
    """
    Methods for soldiers painting, given a MapInfo and/or MapLocation
    Paints when there is no paint or if allied paint is incorrect
    """
    if paint_location is None and paint_tile is not None:
        paint_location = paint_tile.get_map_location()
    elif paint_tile is None and paint_location is not None:
        paint_tile = sense_map_info(get_location())
        
    if (paint_tile.get_paint() == PaintType.EMPTY and 
        can_attack(paint_location) and 
        paint_tile.get_mark() == PaintType.EMPTY):
        # If map size less than 30 by 30, then don't fill in SRP colors as wandering
        if get_map_width() <= constants.SRP_MAP_WIDTH and get_map_height() <= constants.SRP_MAP_HEIGHT:
            attack(paint_location, False)
        else:
            attack(paint_location, not helper.resource_pattern_grid(paint_location))

def read_new_messages():
    """Reads incoming messages and updates internal variables/state as necessary"""
    global enemy_target, enemy_tower, alert_robots, alert_attack_soldiers
    # Looks at all incoming messages from the past round
    for message in read_messages(get_round_num() - 1):
        bytes = message.get_bytes()
        # Information is type of robot
        if bytes in [0, 1, 2]:
            if bytes == 0:
                if (random.random() <= constants.DEV_SRP_BOT_SPLIT or 
                    (get_map_width() <= constants.SRP_MAP_WIDTH and get_map_height() <= constants.SRP_MAP_HEIGHT)):
                    soldier_type = s_type.DEVELOP
                else:
                    soldier_type = s_type.SRP
                    soldier_state = s_state.FILLINGSRP
            elif bytes == 1:
                soldier_type = s_type.ADVANCE
            elif bytes == 2:
                soldier_type = s_type.ATTACK
        elif soldier_type in [s_type.DEVELOP, s_type.ADVANCE]:
            tile = map_info_codec.MapInfoCodec.decode(bytes)
            if tile.has_ruin():
                enemy_tower = tile
                soldier_type = s_type.ATTACK
                reset_variables()
            wander_target = tile.get_map_location()

def update_enemy_tiles(nearby_tiles):
    """
    Returns the MapInfo of a nearby tower, and then a nearby tile if any are sensed
    Nearby tiles only updated at a maximum of once every 15 turns
    Returns null if none are sensed.
    """
    # Check if there are enemy paint or enemy towers sensed
    closest_enemy_tower = tower_in_range(20, False)
    if closest_enemy_tower is not None:
        return sense_map_info(closest_enemy_tower.get_location())
            
    # Find all Enemy Tiles and return one if one exists, but only care once every 15 rounds
    enemy_paint = find_enemy_paint(nearby_tiles)
    if soldier_msg_cooldown == -1 and enemy_paint is not None:
        soldier_msg_cooldown = 30
        return enemy_paint
    return None

def update_enemy_towers(nearby_tiles):
    """
    Returns the MapInfo of a nearby tower
    Nearby towers only updated at a maximum of once every 30 turns
    Returns null if none are sensed.
    """
    # Check if there are enemy paint or enemy towers sensed
    closest_enemy_tower = tower_in_range(20, False)
    if closest_enemy_tower is not None:
        return sense_map_info(closest_enemy_tower.get_location())
    return None

def update_state(cur_location, nearby_tiles):
    """
    Updates the robot state according to its paint level (LOWONPAINT),
    nearby enemy paint (DELIVERINGMESSAGE), or nearby ruins (FILLING TOWER)
    """
    if (has_low_paint(constants.LOW_PAINT_THRESHOLD) and 
        (get_money() < constants.LOW_PAINT_MONEY_THRESHOLD or soldier_state == s_state.FILLINGTOWER)):
        if soldier_state != s_state.LOWONPAINT:
            stored_state = soldier_state
            soldier_state = s_state.LOWONPAINT
    elif soldier_state not in [s_state.DELIVERINGMESSAGE, s_state.LOWONPAINT]:
        # Update enemy tile as necessary
        enemy_tile = update_enemy_tiles(nearby_tiles)
        if enemy_tile is not None and last_tower is not None:
            if soldier_state == s_state.EXPLORING:
                prev_location = get_location()
                reset_variables()
            else:
                intermediate_target = None
                reset_variables()
            stored_state = soldier_state
            soldier_state = s_state.DELIVERINGMESSAGE
        # Check for nearby buildable ruins if we are not currently building one
        elif soldier_state != s_state.FILLINGTOWER:
            best_ruin = find_best_ruin(cur_location, nearby_tiles)
            if best_ruin is not None:
                ruin_to_fill = best_ruin.get_map_location()
                soldier_state = s_state.FILLINGTOWER
                reset_variables()

def update_state_osama(cur_location, nearby_tiles):
    """
    Updates the robot state according to its paint level (LOWONPAINT) or nearby ruins (FILLING TOWER)
    Only cares about enemy paint if the round number is larger than the map length + map width
    """
    if has_low_paint(constants.LOW_PAINT_THRESHOLD):
        if soldier_state != s_state.LOWONPAINT:
            stored_state = soldier_state
            soldier_state = s_state.LOWONPAINT
    elif soldier_state not in [s_state.DELIVERINGMESSAGE, s_state.LOWONPAINT]:
        # Update enemy towers as necessary
        enemy_tile = update_enemy_towers(nearby_tiles)
        if enemy_tile is not None and last_tower is not None:
            soldier_type = s_type.ADVANCE
            reset_variables()
        if soldier_state != s_state.FILLINGTOWER:
            best_ruin = find_any_ruin(cur_location, nearby_tiles)
            if best_ruin is not None:
                if not can_build_tower(best_ruin.get_map_location()):
                    soldier_type = s_type.ADVANCE
                    reset_variables()
                else:
                    ruin_to_fill = best_ruin.get_map_location()
                    soldier_state = s_state.FILLINGTOWER
                    reset_variables()
        # Turn into an advance bot if they see an enemy paint that prevents tower building
        elif soldier_state == s_state.FILLINGTOWER:
            if not can_build_tower(ruin_to_fill):
                soldier_type = s_type.ADVANCE
                reset_variables()

def update_srp_state(cur_location, nearby_tiles):
    """Update state for SRP (Strategic Resource Pattern) soldiers"""
    if get_location() == srp_location:
        srp_location = None
            
    if (soldier_state != s_state.LOWONPAINT and 
        has_low_paint(constants.LOW_PAINT_THRESHOLD)):
        if soldier_state != s_state.STUCK:
            srp_location = get_location()
        reset_variables()
        stored_state = soldier_state
        soldier_state = s_state.LOWONPAINT
    elif soldier_state == s_state.STUCK:
        # If less than 30, check 5x5 area for empty or ally primary tiles and mark center
        if (get_map_width() <= constants.SRP_MAP_WIDTH and 
            get_map_height() <= constants.SRP_MAP_HEIGHT and 
            not sense_map_info(cur_location).get_mark().is_ally()):
            poss_srp = sense_nearby_map_infos(8)
            can_build_srp = True
            for map_info in poss_srp:
                # If we can travel to tile and the paint is ally primary or empty, then build an srp
                if not map_info.is_passable() or map_info.get_paint().is_enemy():
                    can_build_srp = False
                    break
            # Check if srp is within build range
            if can_build_srp and len(poss_srp) == 25 and not conflicts_srp():
                reset_variables()
                soldier_state = s_state.FILLINGSRP
                srp_center = get_location()
                mark(get_location(), False)
            elif has_low_paint(constants.LOW_PAINT_THRESHOLD):
                for map_info in nearby_tiles:
                    if (map_info.get_paint().is_ally() and 
                        map_info.get_paint() != helper.resource_pattern_type(map_info.get_map_location())):
                        reset_variables()
                        soldier_state = s_state.FILLINGSRP

def fill_srp():
    """Creates SRP on small maps by placing marker to denote the center and painting around the marker"""
    if get_location() != srp_center:
        dir = pathfind(srp_center)
        if dir is not None and can_move(dir):
            move(dir)
    else:
        finished = True
        srp_complete = True
        for i in range(5):
            for j in range(5):
                if not on_the_map(get_location().translate(i - 2, j - 2)):
                    continue
                srp_loc = sense_map_info(get_location().translate(i - 2, j - 2))
                is_primary = (i, j) in constants.PRIMARY_SRP
                if ((srp_loc.get_paint() == PaintType.ALLY_PRIMARY and is_primary) or 
                    (srp_loc.get_paint() == PaintType.ALLY_SECONDARY and not is_primary)):
                    continue
                srp_complete = False
                if not can_attack(srp_loc.get_map_location()):
                    continue
                # If paint is empty or ally paint doesnt match, then paint proper color
                if srp_loc.get_paint() == PaintType.EMPTY:
                    attack(srp_loc.get_map_location(), not is_primary)
                    finished = False
                    break
                elif srp_loc.get_paint() == PaintType.ALLY_PRIMARY and not is_primary:
                    attack(srp_loc.get_map_location(), True)
                    finished = False
                    break
                elif srp_loc.get_paint() == PaintType.ALLY_SECONDARY and is_primary:
                    attack(srp_loc.get_map_location(), False)
                    finished = False
                    break
            if not finished:
                break
                    
        if finished:
            if srp_complete:
                soldier_state = s_state.STUCK
                srp_center = None
                num_turns_alive = 0
            if can_complete_resource_pattern(get_location()):
                complete_resource_pattern(get_location())
                soldier_state = s_state.STUCK
                srp_center = None
                num_turns_alive = 0

def fill_srp_large_map(rc):
    """
    Handles SRP filling behavior for large maps. Attempts to paint tiles according to the resource pattern.
    If no paintable tiles are found nearby, moves to find new areas to paint.
    
    Args:
        rc: The RobotController instance
    """
    has_painted = False
        
    # Try to paint nearby tiles that need SRP pattern
    if rc.get_action_cooldown_turns() < 10:
        for nearby_tile in rc.sense_nearby_map_infos(20):
            paint = helper.resource_pattern_type(nearby_tile.get_map_location())
            if ((nearby_tile.get_paint() == PaintType.EMPTY and nearby_tile.is_passable()) or
                (nearby_tile.get_paint().is_ally() and paint != nearby_tile.get_paint())):
                if rc.can_attack(nearby_tile.get_map_location()):
                    rc.attack(nearby_tile.get_map_location(), paint == PaintType.ALLY_SECONDARY)
                    has_painted = True
                    break
        
    # If we haven't painted, look for tiles further away to paint
    if not has_painted:
        cur_location = rc.get_location()
        for nearby_tile in rc.sense_nearby_map_infos():
            if cur_location.is_within_distance_squared(nearby_tile.get_map_location(), 20):
                continue
                    
            nearby_location = nearby_tile.get_map_location()
            paint = helper.resource_pattern_type(nearby_location)
            if ((nearby_tile.get_paint() == PaintType.EMPTY and nearby_tile.is_passable()) or
                (nearby_tile.get_paint().is_ally() and paint != nearby_tile.get_paint())):
                direction = pathfind(rc, nearby_location)
                if direction is not None and rc.can_move(direction):
                    rc.move(direction)
                    has_painted = True
                break
        
        # If we have a stored SRP location, try to move towards it
        if srp_location is not None:
            direction = pathfind(rc, srp_location)
            if direction is not None and rc.can_move(direction):
                rc.move(direction)
        # If we haven't painted anything and can't move to SRP location, we're stuck
        elif not has_painted:
            soldierState = s_state.STUCK

def msg_tower():
    """Pathfinds towards the last known paint tower and try to message it"""
    global soldier_state, stored_state, enemy_tile, ruin_to_fill, intermediate_target, prev_location
    for enemy_robot in sense_nearby_robots(-1, get_team().opponent()):
        if enemy_robot.get_type().is_tower_type():
            if can_attack(enemy_robot.get_map_location()):
                attack(enemy_robot.get_map_location())
                break
                    
    tower_location = last_tower.get_map_location()
    if can_sense_robot_at_location(tower_location) and can_send_message(tower_location):
        communication.Communication.send_map_information(enemy_tile, tower_location)
        enemy_tile = None
        if soldier_state != stored_state:
            soldier_state = stored_state
        elif ruin_to_fill is not None:
            soldier_state = s_state.FILLINGTOWER
        else:
            soldier_state = s_state.STUCK
        reset_variables()
        if prev_location is not None:
            intermediate_target = prev_location
            prev_location = None
        return
            
    dir = return_to_tower()
    if dir is not None:
        move(dir)

    # Otherwise, pathfind to the tower
    tower_location = last_tower.get_map_location()
    complete_ruin_if_possible(tower_location)
    amt_to_transfer = get_paint() - get_type().paint_capacity
    
    if can_sense_robot_at_location(tower_location):
        tower_paint = sense_robot_at_location(tower_location).paint_amount
        if get_paint() < 5 and can_transfer_paint(tower_location, -tower_paint) and tower_paint > constants.MIN_PAINT_GIVE:
            transfer_paint(tower_location, -tower_paint)

    if can_transfer_paint(tower_location, amt_to_transfer):
        transfer_paint(tower_location, amt_to_transfer)

def complete_ruin_if_possible(ruin_location):
    """Soldier version of completeRuinIfPossible"""
    global soldier_state, stored_state, ruin_to_fill, fill_tower_type
    complete_ruin_if_possible(ruin_location)
    if can_sense_robot_at_location(ruin_location):
        soldier_state = s_state.LOWONPAINT
        stored_state = s_state.EXPLORING
        ruin_to_fill = None
        fill_tower_type = None

def fill_in_ruin(ruin_location):
    """
    Marks ruins
    Pathfinds to the ruins and fills in the area around the ruin if we can build a tower there
    If ignoreAlly is true, then we ignore the ruin if ally robots are already in proximity
    """
    global soldier_state, stored_state, fill_tower_type, ruin_to_fill
    # Mark the pattern we need to draw to build a tower here if we haven't already.
    # If robot has seen a paint tower, mark random tower
    if not can_build_tower(ruin_location):
        if (can_sense_robot_at_location(ruin_location) and 
            sense_robot_at_location(ruin_location).get_type() == UnitType.LEVEL_ONE_PAINT_TOWER):
            soldier_state = s_state.LOWONPAINT
            stored_state = s_state.EXPLORING
            fill_tower_type = None
            ruin_to_fill = None
        # Otherwise, ruin is a money ruin
        else:
            soldier_state = s_state.EXPLORING
            fill_tower_type = None
            ruin_to_fill = None
    # Check to see if we know the type of tower to fill in
    elif fill_tower_type is not None:
        # Paint the tile at a location
        ruin_pattern = (constants.PAINT_TOWER_PATTERN if fill_tower_type == UnitType.LEVEL_ONE_PAINT_TOWER else 
                      constants.MONEY_TOWER_PATTERN if fill_tower_type == UnitType.LEVEL_ONE_MONEY_TOWER else 
                      constants.DEFENSE_TOWER_PATTERN)
        tile_to_paint = find_paintable_ruin_tile(ruin_location, ruin_pattern)
        if tile_to_paint is not None:
            tile = ruin_location.translate(tile_to_paint[0], tile_to_paint[1])
            if can_paint(tile) and can_attack(tile):
                attack(tile, ruin_pattern[tile_to_paint[0]+2][tile_to_paint[1]+2] == PaintType.ALLY_SECONDARY)
        # Move to the ruin
        move_dir = pathfind(ruin_location)
        if move_dir is not None:
            move(move_dir)
        # Tries to complete the ruin
        complete_ruin_if_possible(ruin_location)
    else:
        # Determine the marking of the tower and mark if no marking present
        north_tower = ruin_location.add(Direction.NORTH)
        if can_sense_location(north_tower):
            tower_marking = sense_map_info(north_tower).get_mark()
            # If mark type is 1, then ruin is a paint ruin
            if tower_marking == PaintType.ALLY_PRIMARY:
                fill_tower_type = UnitType.LEVEL_ONE_PAINT_TOWER
            # If no mark, then check to see if there is a marking on east for defense tower
            elif tower_marking == PaintType.EMPTY:
                defense_mark_loc = north_tower.add(Direction.EAST)
                if can_sense_location(defense_mark_loc):
                    if sense_map_info(defense_mark_loc).get_mark() == PaintType.ALLY_PRIMARY:
                        fill_tower_type = UnitType.LEVEL_ONE_DEFENSE_TOWER
                    # If can sense location but no mark, then figure out tower type
                    else:
                        tower_type = gen_tower_type(ruin_location)
                        if tower_type == UnitType.LEVEL_ONE_DEFENSE_TOWER and can_mark(defense_mark_loc):
                            # Mark defense tower at north east
                            mark(defense_mark_loc, False)
                            fill_tower_type = UnitType.LEVEL_ONE_DEFENSE_TOWER
                        # If can mark tower, then mark it
                        elif can_mark(north_tower) and tower_type != UnitType.LEVEL_ONE_DEFENSE_TOWER:
                            if seen_paint_tower:
                                mark(north_tower, tower_type == UnitType.LEVEL_ONE_MONEY_TOWER)
                                fill_tower_type = tower_type
                            else:
                                # Otherwise, mark a paint tower
                                mark(north_tower, False)
                                fill_tower_type = UnitType.LEVEL_ONE_PAINT_TOWER
                        # Otherwise, pathfind towards location until can mark it
                        else:
                            move_dir = pathfind(ruin_location)
                            if move_dir is not None:
                                move(move_dir)
            # Otherwise, ruin is a money ruin
            else:
                fill_tower_type = UnitType.LEVEL_ONE_MONEY_TOWER
        # Otherwise, pathfind to the tower
        else:
            move_dir = pathfind(ruin_location)
            if move_dir is not None:
                move(move_dir)

def stuck_behavior():
    """Stuck behavior method"""
    if soldier_type in [s_type.DEVELOP, s_type.SRP]:
        new_dir = find_own_corner()
    else:
        new_dir = get_unstuck()
            
    if new_dir is not None:
        move(new_dir)
        paint_if_possible(get_location())

"""
END SOLDIER FUNCTIONS
BEGIN MOPPER FUNCTIONS
"""
def mopper_receive_last_message():
    """Process the last received message for the mopper"""
    global remove_paint
    for msg in read_messages(-1):
        bytes = msg.get_bytes()
        # Receives what type of mopper the bot is
        if bytes == 3:
            continue
            
        if communication.is_robot_info(bytes):
            message = robot_info_codec.decode(bytes)
        else:
            message = map_info_codec.decode(bytes)
            if message.get_paint().is_enemy():
                robot_loc = get_location()
                if (remove_paint is None or 
                    robot_loc.distance_squared_to(message.get_map_location()) < 
                    robot_loc.distance_squared_to(remove_paint.get_map_location())):
                    remove_paint = message
                    reset_variables()
            # If enemy tower, then go to enemy tower location
            elif message.has_ruin():
                robot_loc = get_location()
                if (remove_paint is None or 
                    robot_loc.distance_squared_to(message.get_map_location()) < 
                    robot_loc.distance_squared_to(remove_paint.get_map_location())):
                    remove_paint = message
                    reset_variables()

def remove_paint(enemy_paint):
    """Remove enemy paint at the specified location"""
    global remove_paint
    enemy_loc = enemy_paint.get_map_location()
    if can_attack(enemy_loc) and enemy_paint.get_paint().is_enemy():
        attack(enemy_loc)
        remove_paint = None
        reset_variables()
    else:
        move_dir = pathfind(enemy_loc)
        if move_dir is not None:
            move(move_dir)

def mopper_scoring():
    """Score nearby tiles for mopper movement"""
    global best, best_score
    nearby_tiles = sense_nearby_map_infos()
    best = None
    best_score = float('-inf')
    for map_info in nearby_tiles:
        curr = 0
        bot = sense_robot_at_location(map_info.get_map_location())
        if bot is not None:
            if not bot.team.is_player():
                if bot.type.is_robot_type():
                    curr += 100
                if bot.type.is_tower_type():
                    curr -= 100
        if curr > best_score:
            best = map_info.get_map_location()
            best_score = curr
    return best

def try_swing():
    """Try to swing the mop if there are enemy bots nearby"""
    if get_action_cooldown_turns() > 10:
        return
            
    north = east = south = west = 0
    loc = get_location()

    for enemy in sense_nearby_robots(2, get_team().opponent()):
        direction = loc.direction_to(enemy.get_location())
        if direction == Direction.NORTH:
            north += 1
        elif direction == Direction.SOUTH:
            south += 1
        elif direction == Direction.WEST:
            west += 1
        elif direction == Direction.EAST:
            east += 1
        elif direction == Direction.NORTHWEST:
            north += 1
            west += 1
        elif direction == Direction.NORTHEAST:
            north += 1
            east += 1
        elif direction == Direction.SOUTHWEST:
            south += 1
            west += 1
        elif direction == Direction.SOUTHEAST:
            south += 1
            east += 1

    if north > 1 and north > east and north > south and north > west:
        if can_mop_swing(Direction.NORTH):
            mop_swing(Direction.NORTH)
        return
    if south > 1 and south > east and south > west:
        if can_mop_swing(Direction.SOUTH):
            mop_swing(Direction.SOUTH)
        return
    if east > 1 and east > west:
        if can_mop_swing(Direction.EAST):
            mop_swing(Direction.EAST)
        return
    if west > 1:
        if can_mop_swing(Direction.WEST):
            mop_swing(Direction.WEST)

def mopper_walk():
    """Random walk for mopper on safe tiles"""
    safe = []
    for map_info in sense_nearby_map_infos(2):
        if map_info.get_paint().is_ally() and map_info.get_map_location() not in last8:
            safe.append(map_info)
                
    if not safe:
        return None
            
    map_info = random.choice(safe)
    return get_location().direction_to(map_info.get_map_location())

"""
END MOPPER FUNCTIONS
"""

def run_soldier():
    """
    Run a single turn for a Soldier.
    This code is wrapped inside the infinite loop in run(), so it is called once per turn.
    """
    global soldier_type, soldier_state, num_turns_alive, intermediate_target
    global enemy_tower, wander_target, srp_location, srp_center
    global ruin_to_fill, fill_tower_type, seen_paint_tower

    # BINLADEN type
    if soldier_type == s_type.BINLADEN:
        if get_round_num() >= (get_map_height() + get_map_width()) / 2:
            soldier_type = s_type.ADVANCE
            return
        
        update_state_osama(get_location(), sense_nearby_map_infos())
        
        if soldier_state == s_state.LOWONPAINT:
            set_indicator_string("LOWONPAINT")
            low_paint_behavior()
        
        elif soldier_state == s_state.DELIVERINGMESSAGE:
            set_indicator_string("DELIVERINGMESSAGE")
            msg_tower()
        
        elif soldier_state == s_state.FILLINGTOWER:
            set_indicator_string(f"FILLINGTOWER {ruin_to_fill}")
            fill_in_ruin(ruin_to_fill)
        
        elif soldier_state == s_state.EXPLORING:
            set_indicator_string("EXPLORING")
            if wander_target is not None:
                direction = better_explore(get_location(), wander_target, False)
                if direction is not None:
                    move(direction)
                    paint_if_possible(paint_location=get_location())
            else:
                intermediate_target = None
                soldier_state = s_state.STUCK
                reset_variables()
            
            if intermediate_target is not None:
                set_indicator_string(f"EXPLORING {intermediate_target}")
        
        elif soldier_state == s_state.STUCK:
            set_indicator_string("STUCK")
            stuck_behavior()
        
        set_indicator_dot(get_location(), 255, 255, 255)

    # DEVELOP type
    elif soldier_type == s_type.DEVELOP:
        update_state(get_location(), sense_nearby_map_infos())
        helper.try_complete_resource_pattern()
        
        # Check for nearby enemies
        sees_enemy = False
        for nearby_bot in sense_nearby_robots():
            if nearby_bot.get_team() == get_team().opponent():
                sees_enemy = True
                break
        
        if sees_enemy:
            num_turns_alive = 0
            soldier_type = s_type.ADVANCE
            soldier_state = s_state.EXPLORING
            reset_variables()
        
        if num_turns_alive > constants.DEV_LIFE_CYCLE_TURNS and soldier_state == s_state.STUCK:
            num_turns_alive = 0
            if get_map_width() <= constants.SRP_MAP_WIDTH and get_map_height() <= constants.SRP_MAP_HEIGHT:
                soldier_state = s_state.STUCK
            else:
                soldier_state = s_state.FILLINGSRP
            
            soldier_type = s_type.SRP
            reset_variables()
            return
        
        if soldier_state == s_state.LOWONPAINT:
            set_indicator_string("LOWONPAINT")
            low_paint_behavior()
        
        elif soldier_state == s_state.DELIVERINGMESSAGE:
            set_indicator_string("DELIVERINGMESSAGE")
            msg_tower()
        
        elif soldier_state == s_state.FILLINGTOWER:
            set_indicator_string("FILLINGTOWER")
            fill_in_ruin(ruin_to_fill)
        
        elif soldier_state == s_state.EXPLORING:
            set_indicator_string("EXPLORING")
            direction = explore_unpainted()
            if direction is not None:
                move(direction)
                paint_if_possible(paint_location=get_location())
            elif get_movement_cooldown_turns() < 10:
                soldier_state = s_state.STUCK
                reset_variables()
        
        elif soldier_state == s_state.STUCK:
            set_indicator_string("STUCK")
            stuck_behavior()
            if find_paintable_tile(get_location(), 20) is not None:
                soldier_state = s_state.EXPLORING
                reset_variables()
        
        set_indicator_dot(get_location(), 0, 255, 0)
        return

    # ADVANCE type
    elif soldier_type == s_type.ADVANCE:
        update_state(get_location(), sense_nearby_map_infos())
        
        if soldier_state == s_state.LOWONPAINT:
            set_indicator_string("LOWONPAINT")
            low_paint_behavior()
        
        elif soldier_state == s_state.DELIVERINGMESSAGE:
            set_indicator_string("DELIVERINGMESSAGE")
            msg_tower()
        
        elif soldier_state == s_state.FILLINGTOWER:
            set_indicator_string(f"FILLINGTOWER {ruin_to_fill}")
            fill_in_ruin(ruin_to_fill)
        
        elif soldier_state == s_state.EXPLORING:
            set_indicator_string("EXPLORING")
            if wander_target is not None:
                direction = better_explore(get_location(), wander_target, False)
                if direction is not None:
                    move(direction)
                    paint_if_possible(paint_location=get_location())
            else:
                intermediate_target = None
                soldier_state = s_state.STUCK
                reset_variables()
            
            if intermediate_target is not None:
                set_indicator_string(f"EXPLORING {intermediate_target}")
        
        elif soldier_state == s_state.STUCK:
            set_indicator_string("STUCK")
            stuck_behavior()
        
        set_indicator_dot(get_location(), 0, 0, 255)
        return

    # ATTACK type
    elif soldier_type == s_type.ATTACK:
        if enemy_tower is None:
            soldier_type = s_type.ADVANCE
            soldier_state = s_state.EXPLORING
            reset_variables()
        else:
            # Check for enemy towers in vision
            for nearby_tile in sense_nearby_map_infos():
                nearby_location = nearby_tile.get_map_location()
                if (nearby_tile.has_ruin() and 
                    can_sense_robot_at_location(nearby_location) and 
                    sense_robot_at_location(nearby_location).get_team() != get_team()):
                    enemy_tower = nearby_tile
                    set_indicator_dot(get_location(), 255, 0, 0)
                    return
            
            # Attack or move towards assigned enemy tower
            enemy_tower_loc = enemy_tower.get_map_location()
            if can_sense_robot_at_location(enemy_tower_loc) and can_attack(enemy_tower_loc):
                attack(enemy_tower_loc)
                back = enemy_tower_loc.direction_to(get_location())
                if can_move(back):
                    move(back)
                else:
                    # Try moving back in other directions
                    left = back.rotate_left()
                    if can_move(left):
                        move(left)
                    else:
                        right = back.rotate_right()
                        if can_move(right):
                            move(right)
            else:
                direction = pathfind(enemy_tower_loc)
                if direction is not None:
                    move(direction)
                    if can_attack(enemy_tower_loc):
                        attack(enemy_tower_loc)
                
                # Clear enemy_tower if it's gone when we see its location
                if can_sense_location(enemy_tower_loc) and not can_sense_robot_at_location(enemy_tower_loc):
                    enemy_tower = None
        
        set_indicator_dot(get_location(), 255, 0, 0)

    # SRP type
    elif soldier_type == s_type.SRP:
        update_srp_state(get_location(), sense_nearby_map_infos())
        helper.try_complete_resource_pattern()
        
        # Check for nearby enemies
        sees_enemy = False
        for nearby_bot in sense_nearby_robots():
            if nearby_bot.get_team() == get_team().opponent():
                sees_enemy = True
                break
        
        if sees_enemy or (num_turns_alive > constants.SRP_LIFE_CYCLE_TURNS and soldier_state == s_state.STUCK):
            soldier_type = s_type.ADVANCE
            soldier_state = s_state.EXPLORING
            num_turns_alive = 0
            reset_variables()
        
        if soldier_state == s_state.LOWONPAINT:
            set_indicator_string("LOWONPAINT")
            low_paint_behavior()
        
        elif soldier_state == s_state.FILLINGSRP:
            set_indicator_string("FILLING SRP")
            if get_map_width() <= constants.SRP_MAP_WIDTH and get_map_height() <= constants.SRP_MAP_HEIGHT:
                fill_srp()
            else:
                # Paint over mismatched SRP grid tiles
                has_painted = False
                if get_action_cooldown_turns() < 10:
                    for attackable_tile in sense_nearby_map_infos(20):
                        nearby_location = attackable_tile.get_map_location()
                        paint = helper.resource_pattern_type(nearby_location)
                        if ((attackable_tile.get_paint() == PaintType.EMPTY and attackable_tile.is_passable()) or
                            (attackable_tile.get_paint().is_ally() and paint != attackable_tile.get_paint())):
                            if can_attack(nearby_location):
                                attack(nearby_location, paint == PaintType.ALLY_SECONDARY)
                                has_painted = True
                                break
                
                if not has_painted:
                    cur_location = get_location()
                    for nearby_tile in sense_nearby_map_infos():
                        if cur_location.is_within_distance_squared(nearby_tile.get_map_location(), 20):
                            continue
                        nearby_location = nearby_tile.get_map_location()
                        paint = helper.resource_pattern_type(nearby_location)
                        if ((nearby_tile.get_paint() == PaintType.EMPTY and nearby_tile.is_passable()) or
                            (nearby_tile.get_paint().is_ally() and paint != nearby_tile.get_paint())):
                            direction = pathfind(nearby_location)
                            if direction is not None and can_move(direction):
                                move(direction)
                                has_painted = True
                            break
                
                if srp_location is not None:
                    direction = pathfind(srp_location)
                    if direction is not None and can_move(direction):
                        move(direction)
                elif not has_painted:
                    soldier_state = s_state.STUCK
        
        elif soldier_state == s_state.STUCK:
            set_indicator_string("STUCK")
            stuck_behavior()
            
            if not (get_map_width() <= constants.SRP_MAP_WIDTH and get_map_height() <= constants.SRP_MAP_HEIGHT):
                for nearby_tile in sense_nearby_map_infos():
                    paint = helper.resource_pattern_type(nearby_tile.get_map_location())
                    if ((nearby_tile.get_paint() == PaintType.EMPTY and nearby_tile.is_passable()) or
                        (nearby_tile.get_paint().is_ally() and paint != nearby_tile.get_paint())):
                        reset_variables()
                        soldier_state = s_state.FILLINGSRP
                        num_turns_alive = 0
                        break
            
            elif (sense_map_info(get_location()).get_mark().is_ally() and 
                  not can_complete_resource_pattern(get_location())):
                turn_to_srp = True
                all_same = True
                
                for i in range(5):
                    for j in range(5):
                        loc = get_location().translate(i - 2, j - 2)
                        if not on_the_map(loc):
                            turn_to_srp = False
                            break
                        
                        srp_loc = sense_map_info(loc)
                        if srp_loc.has_ruin() or srp_loc.get_paint().is_enemy():
                            turn_to_srp = False
                            break
                        
                        is_primary = (i, j) in constants.primary_srp
                        if ((srp_loc.get_paint() == PaintType.ALLY_PRIMARY and is_primary) or 
                            (srp_loc.get_paint() == PaintType.ALLY_SECONDARY and not is_primary)):
                            all_same = False
                    
                    if not turn_to_srp:
                        break
                
                if turn_to_srp and not all_same:
                    reset_variables()
                    soldier_state = s_state.FILLINGSRP
                    srp_center = get_location()
                    num_turns_alive = 0
        
        set_indicator_dot(get_location(), 255, 255, 0)
    
    else:
        set_indicator_dot(get_location(), 0, 0, 0)

def run_mopper():
    """Run a single turn for a Mopper unit."""
    global remove_paint, opposite_corner, bot_round_num
    global get_location, get_team, get_round_num, get_paint, get_money
    # Set indicator string based on remove_paint target
    if remove_paint is not None:
        set_indicator_string(str(remove_paint))
    else:
        set_indicator_string("null")

    # When spawning in, check tile to see if it needs to be cleared
    if bot_round_num == 3 and sense_map_info(get_location()).get_paint().is_enemy():
        attack(get_location())
        return

    # Read all incoming messages
    mopper_receive_last_message()
    helper.try_complete_resource_pattern()
    
    all_tiles = sense_nearby_map_infos()
    
    # Avoid enemy towers with the highest priority
    for nearby_tile in all_tiles:
        bot = sense_robot_at_location(nearby_tile.get_map_location())
        if (bot is not None and bot.get_type().is_tower_type() and 
            not bot.get_team().equals(get_team())):
            if (remove_paint is not None and 
                remove_paint.get_map_location().distance_squared_to(bot.get_location()) <= 9):
                remove_paint = None  # ignore target in tower range

            # Move around the tower by rotating 135 degrees
            direction = get_location().direction_to(nearby_tile.get_map_location()).rotate_right().rotate_right().rotate_right()
            if can_move(direction):
                move(direction)
                break

    # Stay safe, stay on ally paint if possible
    if not sense_map_info(get_location()).get_paint().is_ally():
        direction = mopper_walk()
        if direction is not None and can_move(direction):
            move(direction)

    curr_paint = None
    # Check around the Mopper's attack radius for bots
    for tile in sense_nearby_map_infos(2):
        bot = sense_robot_at_location(tile.get_map_location())
        if bot is not None:
            if (bot.get_type().is_robot_type() and not bot.get_team().equals(get_team()) and 
                bot.get_paint_amount() > 0):
                if tile.get_paint().is_enemy() and can_attack(tile.get_map_location()):
                    attack(tile.get_map_location())
                direction = get_location().direction_to(bot.get_location())
                if direction in [Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST]:
                    if can_mop_swing(direction):
                        mop_swing(direction)
                        opposite_corner = None
                else:
                    if can_mop_swing(direction.rotate_right()):
                        mop_swing(direction.rotate_right())
                        opposite_corner = None
                return

        if tile.get_paint().is_enemy():
            if can_attack(tile.get_map_location()):
                curr_paint = tile.get_map_location()
            opposite_corner = None

    # Move towards opponent bot in vision range
    for tile in sense_nearby_map_infos():
        # First check for enemy tile, store it
        if tile.get_paint().is_enemy():
            opposite_corner = None
            if curr_paint is None:
                curr_paint = tile.get_map_location()

        bot = sense_robot_at_location(tile.get_map_location())
        if bot is not None:
            if (bot.get_type().is_robot_type() and not bot.get_team().equals(get_team()) and 
                not tile.get_paint().is_enemy()):
                enemy_dir = pathfind(tile.get_map_location())
                if enemy_dir is not None:
                    opposite_corner = None
                    move(enemy_dir)
                    break

    # Attack nearest paint if exists with lower priority
    if curr_paint is not None:
        if can_attack(curr_paint):
            opposite_corner = None
            attack(curr_paint)
            return
        elif is_action_ready():
            direction = pathfind(curr_paint)
            if direction is not None:
                opposite_corner = None
                move(direction)
                if can_attack(curr_paint):
                    attack(curr_paint)
        return

    elif remove_paint is not None:
        opposite_corner = None
        remove_paint(remove_paint)
    else:
        # Attack adjacent tiles if possible
        explore_dir = pathfinding.get_unstuck()
        if explore_dir is not None:
            robots.move(explore_dir)

def run_splasher():
    """Run a single turn for a Splasher unit."""
    global remove_paint, last_tower, is_low_paint, in_bug_nav, across_wall, prev_loc_info, bot_round_num
    global get_location, get_team, get_round_num, get_paint, get_money, get_action_cooldown_turns
    # Read input messages for information on enemy tile location
    splasher_receive_last_message()

    # Check if last tower is still valid
    if (last_tower is not None and 
        can_sense_location(last_tower.get_map_location())):
        if not can_sense_robot_at_location(last_tower.get_map_location()):
            last_tower = None

    # Update last paint tower location
    update_last_paint_tower()

    # If paint is low, go back to refill
    if (has_low_paint(75) and 
        get_money() < constants.LOW_PAINT_MONEY_THRESHOLD):
        if not is_low_paint:
            in_bug_nav = False
            across_wall = None
            prev_loc_info = sense_map_info(get_location())
        low_paint_behavior()
        return

    elif is_low_paint:
        if remove_paint is None:
            remove_paint = prev_loc_info
        prev_loc_info = None
        in_bug_nav = False
        across_wall = None
        is_low_paint = False

    # Move perpendicular to enemy towers if any exists in range
    for bot in sense_nearby_robots():
        if bot.get_type().is_tower_type() and not bot.get_team().equals(get_team()):
            direction = get_location().direction_to(bot.get_location()).rotate_right().rotate_right().rotate_right()
            if can_move(direction):
                move(direction)
            if (remove_paint is not None and 
                remove_paint.get_map_location().distance_squared_to(bot.get_location()) <= 9):
                remove_paint = None  # ignore target in tower range

    enemies = score_splasher_tiles()

    # Check to see if assigned tile is already filled in with our paint
    # Prevents splasher from painting already painted tiles
    if (remove_paint is not None and 
        can_sense_location(remove_paint.get_map_location()) and 
        sense_map_info(remove_paint.get_map_location()).get_paint().is_ally()):
        remove_paint = None

    # splash assigned tile or move towards it
    if enemies is not None and can_attack(enemies.get_map_location()):
        attack(enemies.get_map_location())
        return
    elif enemies is not None:
        if remove_paint is None:
            remove_paint = enemies

        direction = pathfind(enemies.get_map_location())
        if direction is not None:
            move(direction)
        return

    elif remove_paint is not None:
        if can_attack(remove_paint.get_map_location()):
            attack(remove_paint.get_map_location())
            return
        direction = pathfind(remove_paint.get_map_location())
        if get_action_cooldown_turns() < 10 and direction is not None:
            move(direction)
        return

    if bot_round_num > 1:
        direction = better_unstuck()
        if direction is not None and can_move(direction):
            move(direction)

def run_tower():
    """Run a single turn for a Tower unit."""
    global spawn_direction, rounds_without_enemy, spawn_queue, broadcast, enemy_target, alert_robots, enemy_tower, send_type_message
    global get_type, get_round_num, get_money, get_paint, get_map_width, get_map_height, get_location
    # Sets spawn direction of each tower when created
    if spawn_direction is None:
        spawn_direction = tower.spawn_direction()

    rounds_without_enemy = rounds_without_enemy + 1  # Update rounds without enemy
    
    # Handle different tower types and their messages
    if (get_type().get_base_type() == UnitType.LEVEL_ONE_MONEY_TOWER or 
        get_type().get_base_type() == UnitType.LEVEL_TWO_MONEY_TOWER):
        read_new_messages()
        if get_paint() == 500:
            spawn_queue.append(0)
    else:
        read_new_messages()

    # Starting condition
    if get_round_num() == 1:
        build_robot(UnitType.SOLDIER, get_location().add(spawn_direction))
    elif get_round_num() == 2:
        center = map_info_codec.MapLocation(get_map_width() // 2, get_map_height() // 2)
        if not get_location().is_within_distance_squared(center, 150):
            enemy_tiles = count_enemy_paint()
            if ((get_type() == UnitType.LEVEL_ONE_MONEY_TOWER or 
                 get_type() == UnitType.LEVEL_TWO_MONEY_TOWER) and 
                enemy_tiles > 3):
                spawn_queue.append(3)
            else:
                build_robot(UnitType.SOLDIER, get_location().add(spawn_direction.rotate_right()))
        else:
            enemy_tiles = count_enemy_paint()
            if enemy_tiles == 0 or enemy_tiles > 20:
                build_robot(UnitType.SPLASHER, get_location().add(spawn_direction.rotate_right()))
            else:
                build_robot(UnitType.MOPPER, get_location().add(spawn_direction.rotate_right()))
                if (len(spawn_queue) == 0 and 
                    (get_type() == UnitType.LEVEL_ONE_MONEY_TOWER or 
                     get_type() == UnitType.LEVEL_TWO_MONEY_TOWER)):
                    spawn_queue.append(3)
    else:
        if broadcast:
            broadcast_message(map_info_codec.MapInfoCodec.encode(enemy_target))
            broadcast = False

        # If unit has been spawned and communication hasn't happened yet
        if send_type_message:
            send_type_message(spawn_queue[0])

        # Otherwise, if the spawn queue isn't empty, spawn the required unit
        elif (len(spawn_queue) > 0 and 
              (get_money() > 400 or 
               (get_type() != UnitType.LEVEL_ONE_PAINT_TOWER and 
                get_type() != UnitType.LEVEL_TWO_PAINT_TOWER and 
                get_type() != UnitType.LEVEL_THREE_PAINT_TOWER))):
            unit_type = spawn_queue[0]
            if unit_type in [0, 1, 2]:
                create_soldier()
            elif unit_type == 3:
                create_mopper()
            elif unit_type == 4:
                create_splasher()
        elif get_money() > 1200 and get_paint() > 200 and len(spawn_queue) < 3:
            tower.add_random_to_queue()

    # Handle broadcasting and enemy tower alerts
    if enemy_target is not None and alert_robots:
        broadcast_nearby_bots()

    if enemy_tower is not None and get_round_num() % 50 == 0:
        broadcast_enemy_tower()

    # Handle tower upgrades based on money
    if get_type() == UnitType.LEVEL_ONE_PAINT_TOWER and get_money() > 5000:
        upgrade_tower(get_location())
    if get_type() == UnitType.LEVEL_ONE_MONEY_TOWER and get_money() > 7500:
        upgrade_tower(get_location())
    if get_type() == UnitType.LEVEL_TWO_PAINT_TOWER and get_money() > 7500:
        upgrade_tower(get_location())
    if get_type() == UnitType.LEVEL_TWO_MONEY_TOWER and get_money() > 10000:
        upgrade_tower(get_location())
    if get_type() == UnitType.LEVEL_ONE_DEFENSE_TOWER and get_money() > 5000:
        upgrade_tower(get_location())
    if get_type() == UnitType.LEVEL_TWO_DEFENSE_TOWER and get_money() > 7500:
        upgrade_tower(get_location())

    # Attack enemies
    attack_lowest_robot()
    aoe_attack_if_possible()


def turn():
    """
    turn() is the method that is called when a robot is instantiated in the Battlecode world.
    It is like the main function for your robot. If this method returns, the robot dies!
    
    Args:
        : The RobotController object. You use it to perform actions from this robot, and to get
            information on its current status. Essentially your portal to interacting with the world.
    """
    global turn_count, num_turns_alive, round_num, bot_round_num, soldier_msg_cooldown, last8
    
    while True:
        # This code runs during the entire lifespan of the robot, which is why it is in an infinite
        # loop. If we ever leave this loop and return from run(), the robot dies! At the end of the
        # loop, we call Clock.yield(), signifying that we've done everything we want to do.
        turn_count = turn_count + 1  # We have now been alive for one more turn!
        num_turns_alive = num_turns_alive + 1
        
        if turn_count == constants.RESIGN_AFTER:
            resign()
 
        # The same run() function is called for every robot on your team, even if they are
        # different types. Here, we separate the control depending on the UnitType, so we can
        # use different strategies on different robots.
        
        # Update round number and cooldowns
        round_num = get_round_num()
        bot_round_num = bot_round_num + 1
        if soldier_msg_cooldown != -1:
            soldier_msg_cooldown = soldier_msg_cooldown - 1

        # Run the appropriate behavior based on robot type
        if get_type() == UnitType.SOLDIER:
            run_soldier()
        elif get_type() == UnitType.MOPPER:
            run_mopper()
        elif get_type() == UnitType.SPLASHER:
            run_splasher()
        else:
            run_tower()
            
        # Check if we went over bytecode limit
        if round_num != get_round_num():
            log("I WENT OVER BYTECODE LIMIT BRUH")
            
        # Update the last eight locations list
        if len(last8) >= 16:
            last8.pop(0)  # Remove oldest element
        last8.append(get_location())
