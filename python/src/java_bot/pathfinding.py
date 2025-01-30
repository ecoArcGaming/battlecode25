from battlecode25.stubs import *
from .constants import Constants
from .sensing import Sensing
from .helper import Helper
from .bot import *

class Pathfinding:
    """
    Class for all movement & pathfinding-related methods
    All methods in this class should return a direction that a robot can move it (check sanity before returning)
    """
    # I think using stuff from other classes costs a ton of bytecode so im declaring this here
    directions = [[-2, -2], [-2, 0], [-2, 2], [0, -2], [0, 2], [2, -2], [2, 0], [2, 2]]

    @staticmethod
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
        
        for dir in Constants.directions:
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

    @staticmethod
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
            if can_move(dir) and get_location().add(curr_dir) not in globals()['last8']:
                return dir

        for dir in all_directions:
            if can_move(dir):
                return dir

        return None

    @staticmethod
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
                    get_location().add(curr_dir) not in globals()['last8']):
                    return dir

        for dir in all_directions:
            if can_move(dir):
                return dir

        return None

    @staticmethod
    def return_to_tower():
        """Returns a Direction representing the direction to move to the closest tower in vision or the last one remembered"""
        if get_paint() < 6:
            return Pathfinding.painted_pathfind(globals()['last_tower'].get_map_location())
        return Pathfinding.original_pathfind(globals()['last_tower'].get_map_location())

    @staticmethod
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
            cum_sum += 5 * Sensing.count_empty_around(adj_location.add(get_location().direction_to(adj_location)))
            weighted_adjacent[i] = cum_sum
            
        if cum_sum == 0:
            return None
        else:
            random_value = Constants.rng.randint(0, cum_sum - 1)
            for i in range(num_tiles):
                if random_value < weighted_adjacent[i]:
                    return valid_adjacent[i].get_map_location()
        return None

    @staticmethod
    def explore_unpainted():
        """
        Returns a Direction representing the direction of an unpainted block
        Smartly chooses an optimal direction among adjacent, unpainted tiles using the method tiebreakUnpainted
        If all surrounding blocks are painted, looks past those blocks (ignoring passability of adjacent tiles)
        and pathfinds to a passable tile, chosen by tiebreakUnpainted
        """
        valid_adjacent = Sensing.get_movable_empty_tiles()
        if not valid_adjacent:
            cur_loc = get_location()
            for dir in Constants.directions:
                farther_location = cur_loc.add(dir)
                if on_the_map(farther_location):
                    farther_info = sense_map_info(farther_location)
                    if farther_info.is_passable():
                        valid_adjacent.append(farther_info)
                        
        best_location = Pathfinding.tiebreak_unpainted(valid_adjacent)
        if best_location is None:
            return None
            
        move_dir = Pathfinding.original_pathfind(best_location)
        if move_dir is not None:
            return move_dir
            
        return None

    @staticmethod
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
        break_score = 0
        if globals()['intermediate_target'] is not None:
            potential_break = MapLocation(cur_location.x - 2, cur_location.y - 2)
            if on_the_map(potential_break):
                break_score = Sensing.score_tile(potential_break, False)
            
            potential_break = MapLocation(cur_location.x + 2, cur_location.y - 2)
            if on_the_map(potential_break):
                break_score = max(break_score, Sensing.score_tile(potential_break, False))
                
            potential_break = MapLocation(cur_location.x - 2, cur_location.y + 2)
            if on_the_map(potential_break):
                break_score = max(break_score, Sensing.score_tile(potential_break, False))
                
            potential_break = MapLocation(cur_location.x + 2, cur_location.y + 2)
            if on_the_map(potential_break):
                break_score = max(break_score, Sensing.score_tile(potential_break, False))
                
            if break_score > 45:
                globals()['intermediate_target'] = None
                from .soldier import Soldier
                Soldier.reset_variables()

        # Only update intermediate target locations when we have reached one already or if we don't have one at all
        if (globals()['intermediate_target'] is None or 
            cur_location.equals(globals()['intermediate_target']) or
            (cur_location.is_within_distance_squared(globals()['intermediate_target'], 2) and
             not sense_map_info(globals()['intermediate_target']).is_passable())):
            
            if cur_location.equals(globals()['intermediate_target']):
                from .soldier import Soldier
                Soldier.reset_variables()
                
            cum_sum = 0
            # Calculate a score for each target
            min_score = -1
            weighted_adjacent = [0] * 8
            cur_distance = cur_location.distance_squared_to(target)
            
            for i in range(8):
                score = 0
                possible_target = cur_location.translate(Pathfinding.directions[i][0], Pathfinding.directions[i][1])
                if on_the_map(possible_target):
                    score = Sensing.score_tile(possible_target, care_about_enemy)
                    new_distance = possible_target.distance_squared_to(target)
                    if cur_distance > new_distance:
                        score += 20
                    elif cur_distance == new_distance:
                        score += 10
                        
                if min_score == -1 or score < min_score:
                    min_score = score
                cum_sum += score
                weighted_adjacent[i] = cum_sum

            # Normalize by subtracting each score by the same amount so that one score is equal to 1
            if min_score != 0:
                min_score -= 1
            weighted_adjacent[0] -= min_score * 1
            weighted_adjacent[1] -= min_score * 2
            weighted_adjacent[2] -= min_score * 3
            weighted_adjacent[3] -= min_score * 4
            weighted_adjacent[4] -= min_score * 5
            weighted_adjacent[5] -= min_score * 6
            weighted_adjacent[6] -= min_score * 7
            weighted_adjacent[7] -= min_score * 8

            if cum_sum != 0:
                random_value = Constants.rng.randint(0, weighted_adjacent[7] - 1)
                for i in range(8):
                    if random_value < weighted_adjacent[i]:
                        globals()['intermediate_target'] = cur_location.translate(
                            Pathfinding.directions[i][0], Pathfinding.directions[i][1])
                        break

        if globals()['intermediate_target'] is None:
            return None
            
        if (globals()['prev_intermediate'] is not None and 
            globals()['prev_intermediate'] != globals()['intermediate_target']):
            globals()['stuck_turn_count'] = 0
            
        move_dir = Pathfinding.pathfind(globals()['intermediate_target'])
        if move_dir is not None:
            return move_dir
            
        return None

    @staticmethod
    def random_walk():
        """Does a random walk"""
        all_directions = Direction.all_directions()
        for _ in range(5):
            dir = all_directions[int(Constants.rng.random() * len(all_directions))]
            if can_move(dir) and get_location().add(dir) not in globals()['last8']:
                return dir
        return None

    @staticmethod
    def find_own_corner():
        """Find and move towards own corner"""
        set_indicator_string(f"GETTING UNSTUCK {globals()['opposite_corner']}")
        if Constants.rng.random() < Constants.RANDOM_STEP_PROBABILITY:
            random_dir = Pathfinding.random_walk()
            if random_dir is not None:
                return random_dir
                
        globals()['prev_intermediate'] = globals()['intermediate_target']
        globals()['intermediate_target'] = None
        
        if (globals()['opposite_corner'] is None or 
            get_location().distance_squared_to(globals()['opposite_corner']) <= 8):
            corner = Constants.rng.random()
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
                    
            globals()['opposite_corner'] = MapLocation(target_x, target_y)
            
        return Pathfinding.pathfind(globals()['opposite_corner'])

    @staticmethod
    def get_unstuck():
        """Finds the furthest corner and move towards it"""
        if Constants.rng.random() < Constants.RANDOM_STEP_PROBABILITY:
            return Pathfinding.random_walk()
        else:
            if (globals()['opposite_corner'] is None or 
                get_location().distance_squared_to(globals()['opposite_corner']) <= 20):
                x = get_location().x
                y = get_location().y
                target_x = get_map_width() if x < get_map_width() / 2 else 0
                target_y = get_map_height() if y < get_map_height() / 2 else 0
                globals()['opposite_corner'] = MapLocation(target_x, target_y)
                
            return Pathfinding.pathfind(globals()['opposite_corner'])

    @staticmethod
    def better_unstuck():
        """Better version of getting unstuck"""
        set_indicator_string(f"GETTING UNSTUCK {globals()['opposite_corner']}")
        globals()['prev_intermediate'] = globals()['intermediate_target']
        globals()['intermediate_target'] = None
        
        if (globals()['opposite_corner'] is None or 
            get_location().distance_squared_to(globals()['opposite_corner']) <= 20):
            corner = Constants.rng.random()
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
                    
            globals()['opposite_corner'] = MapLocation(target_x, target_y)
            
        return Pathfinding.pathfind(globals()['opposite_corner'])

    @staticmethod
    def bugidk(target):
        """bug(?) pathfinding algorithm"""
        if not globals()['is_tracing']:
            # proceed as normal
            dir = get_location().direction_to(target)
            if can_move(dir):
                return dir
            else:
                if can_sense_robot_at_location(get_location().add(dir)):
                    if Constants.rng.random() >= 0.8:
                        # treat robot as passable 20% of the time
                        return None
                globals()['is_tracing'] = True
                globals()['tracing_dir'] = dir
                globals()['stopped_location'] = get_location()
                globals()['tracing_turns'] = 0
        else:
            if ((Helper.is_between(get_location(), globals()['stopped_location'], target) and 
                globals()['tracing_turns'] != 0) or 
                globals()['tracing_turns'] > 2 * (get_map_width() + get_map_height())):
                from .soldier import Soldier
                Soldier.reset_variables()
            else:
                # go along perimeter of obstacle
                if can_move(globals()['tracing_dir']):
                    # move forward and try to turn right
                    return_dir = globals()['tracing_dir']
                    globals()['tracing_dir'] = globals()['tracing_dir'].rotate_right()
                    globals()['tracing_dir'] = globals()['tracing_dir'].rotate_right()
                    globals()['tracing_turns'] += 1
                    return return_dir
                else:
                    # turn left because we cannot proceed forward
                    # keep turning left until we can move again
                    for _ in range(8):
                        globals()['tracing_dir'] = globals()['tracing_dir'].rotate_left()
                        if can_move(globals()['tracing_dir']):
                            return_dir = globals()['tracing_dir']
                            globals()['tracing_dir'] = globals()['tracing_dir'].rotate_right()
                            globals()['tracing_dir'] = globals()['tracing_dir'].rotate_right()
                            globals()['tracing_turns'] += 1
                            return return_dir
        return None

    @staticmethod
    def bug1(target):
        """bug1 pathfinding algorithm"""
        if not globals()['is_tracing']:
            # proceed as normal
            dir = get_location().direction_to(target)
            if can_move(dir):
                return dir
            else:
                globals()['is_tracing'] = True
                globals()['tracing_dir'] = dir
                globals()['bug1_turns'] = 0
        else:
            # tracing mode
            # need a stopping condition - this will be when we see the closestLocation again
            if ((get_location().equals(globals()['closest_location']) and globals()['bug1_turns'] != 0) or 
                globals()['bug1_turns'] > 2 * (get_map_width() + get_map_height())):
                # returned to closest location along perimeter of the obstacle
                from .soldier import Soldier
                Soldier.reset_variables()
            else:
                # keep tracing
                # update closestLocation and smallestDistance
                dist_to_target = get_location().distance_squared_to(target)
                if dist_to_target < globals()['smallest_distance']:
                    globals()['smallest_distance'] = dist_to_target
                    globals()['closest_location'] = get_location()

                # go along perimeter of obstacle
                if can_move(globals()['tracing_dir']):
                    # move forward and try to turn right
                    return_dir = globals()['tracing_dir']
                    globals()['tracing_dir'] = globals()['tracing_dir'].rotate_right()
                    globals()['tracing_dir'] = globals()['tracing_dir'].rotate_right()
                    globals()['bug1_turns'] += 1
                    return return_dir
                else:
                    # turn left because we cannot proceed forward
                    # keep turning left until we can move again
                    for _ in range(8):
                        globals()['tracing_dir'] = globals()['tracing_dir'].rotate_left()
                        if can_move(globals()['tracing_dir']):
                            return_dir = globals()['tracing_dir']
                            globals()['tracing_dir'] = globals()['tracing_dir'].rotate_right()
                            globals()['tracing_dir'] = globals()['tracing_dir'].rotate_right()
                            globals()['bug1_turns'] += 1
                            return return_dir
        return None

    @staticmethod
    def pathfind(target):
        """Main pathfinding method that combines different strategies"""
        cur_location = get_location()
        dist = cur_location.distance_squared_to(target)
        if dist == 0:
            from .soldier import Soldier
            Soldier.reset_variables()
            
        if globals()['stuck_turn_count'] < 5 and not globals()['in_bug_nav']:
            if dist < globals()['closest_path']:
                globals()['closest_path'] = dist
            elif globals()['closest_path'] != -1:
                globals()['stuck_turn_count'] += 1
            else:
                globals()['closest_path'] = dist
            return Pathfinding.less_original_pathfind(target)
            
        elif globals()['in_bug_nav']:
            # If robot has made it across the wall to the other side
            # Then, just pathfind to the place we are going to
            if get_location().distance_squared_to(globals()['across_wall']) == 0:
                from .soldier import Soldier
                Soldier.reset_variables()
                return None
            # Otherwise, just call bugnav
            return Pathfinding.bug1(globals()['across_wall'])
            
        else:
            globals()['in_bug_nav'] = True
            globals()['stuck_turn_count'] = 0
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
                                    globals()['across_wall'] = new_loc
                                    return None
                        else:
                            globals()['across_wall'] = new_loc
                            return None
                else:
                    globals()['across_wall'] = new_loc
                    return None
                    
            globals()['across_wall'] = target
            return None

    @staticmethod
    def random_painted_walk():
        """Random walk along painted tiles"""
        all_directions = Sensing.get_movable_painted_tiles()
        if not all_directions:
            return None
        dir = get_location().direction_to(all_directions[int(Constants.rng.random() * len(all_directions))].get_map_location())
        if can_move(dir):
            return dir
        return None
