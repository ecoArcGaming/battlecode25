from battlecode25.stubs import *
import constants
import helper
import pathfinding
import communication
import map_info_codec
import soldier_state
import soldier_type
import robot
import sensing
import random

def low_paint_behavior():
    """Method for soldier to do when low on paint"""
    robot.low_paint_behavior()
    if get_paint() > constants.LOW_PAINT_THRESHOLD:
        if globals()['soldier_state'] != globals()['stored_state']:
            globals()['soldier_state'] = globals()['stored_state']
        elif globals()['ruin_to_fill'] is not None:
            globals()['soldier_state'] = soldier_state.SoldierState.FILLINGTOWER
        else:
            globals()['soldier_state'] = soldier_state.SoldierState.STUCK
        reset_variables()

def paint_if_possible(paint_tile=None, paint_location=None):
    """
    Methods for soldiers painting, given a MapInfo and/or MapLocation
    Paints when there is no paint or if allied paint is incorrect
    """
    if paint_location is None and paint_tile is not None:
        paint_location = paint_tile.get_map_location()
    elif paint_tile is None and paint_location is not None:
        paint_tile = sensing.sense_map_info(get_location())
        
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
    # Looks at all incoming messages from the past round
    for message in read_messages(get_round_num() - 1):
        bytes = message.get_bytes()
        # Information is type of robot
        if bytes in [0, 1, 2]:
            if bytes == 0:
                if (random.random() <= constants.DEV_SRP_BOT_SPLIT or 
                    (get_map_width() <= constants.SRP_MAP_WIDTH and get_map_height() <= constants.SRP_MAP_HEIGHT)):
                    globals()['soldier_type'] = soldier_type.SoldierType.DEVELOP
                else:
                    globals()['soldier_type'] = soldier_type.SoldierType.SRP
                    globals()['soldier_state'] = soldier_state.SoldierState.FILLINGSRP
            elif bytes == 1:
                globals()['soldier_type'] = soldier_type.SoldierType.ADVANCE
            elif bytes == 2:
                globals()['soldier_type'] = soldier_type.SoldierType.ATTACK
        elif globals()['soldier_type'] in [soldier_type.SoldierType.ADVANCE, soldier_type.SoldierType.ATTACK]:
            tile = map_info_codec.MapInfoCodec.decode(bytes)
            if tile.has_ruin():
                globals()['enemy_tower'] = tile
                globals()['soldier_type'] = soldier_type.SoldierType.ATTACK
                reset_variables()
            globals()['wander_target'] = tile.get_map_location()

def update_enemy_tiles(nearby_tiles):
    """
    Returns the MapInfo of a nearby tower, and then a nearby tile if any are sensed
    Nearby tiles only updated at a maximum of once every 15 turns
    Returns null if none are sensed.
    """
    # Check if there are enemy paint or enemy towers sensed
    closest_enemy_tower = sensing.Sensing.tower_in_range(20, False)
    if closest_enemy_tower is not None:
        return sensing.Sensing.sense_map_info(closest_enemy_tower.get_location())
            
    # Find all Enemy Tiles and return one if one exists, but only care once every 15 rounds
    enemy_paint = sensing.Sensing.find_enemy_paint(nearby_tiles)
    if globals()['soldier_msg_cooldown'] == -1 and enemy_paint is not None:
        globals()['soldier_msg_cooldown'] = 30
        return enemy_paint
    return None

def update_enemy_towers(nearby_tiles):
    """
    Returns the MapInfo of a nearby tower
    Nearby towers only updated at a maximum of once every 30 turns
    Returns null if none are sensed.
    """
    # Check if there are enemy paint or enemy towers sensed
    closest_enemy_tower = sensing.Sensing.tower_in_range(20, False)
    if closest_enemy_tower is not None:
        return sensing.Sensing.sense_map_info(closest_enemy_tower.get_location())
    return None

def update_state(cur_location, nearby_tiles):
    """
    Updates the robot state according to its paint level (LOWONPAINT),
    nearby enemy paint (DELIVERINGMESSAGE), or nearby ruins (FILLING TOWER)
    """
    if (robot.has_low_paint(constants.LOW_PAINT_THRESHOLD) and 
        (get_money() < constants.LOW_PAINT_MONEY_THRESHOLD or globals()['soldier_state'] == soldier_state.SoldierState.FILLINGTOWER)):
        if globals()['soldier_state'] != soldier_state.SoldierState.LOWONPAINT:
            globals()['intermediate_target'] = None
            reset_variables()
            globals()['stored_state'] = globals()['soldier_state']
            globals()['soldier_state'] = soldier_state.SoldierState.LOWONPAINT
    elif globals()['soldier_state'] not in [soldier_state.SoldierState.DELIVERINGMESSAGE, soldier_state.SoldierState.LOWONPAINT]:
        # Update enemy tile as necessary
        globals()['enemy_tile'] = update_enemy_tiles(nearby_tiles)
        if globals()['enemy_tile'] is not None and globals()['last_tower'] is not None:
            if globals()['soldier_state'] == soldier_state.SoldierState.EXPLORING:
                globals()['prev_location'] = get_location()
                reset_variables()
            else:
                globals()['intermediate_target'] = None
                reset_variables()
            globals()['stored_state'] = globals()['soldier_state']
            globals()['soldier_state'] = soldier_state.SoldierState.DELIVERINGMESSAGE
        # Check for nearby buildable ruins if we are not currently building one
        elif globals()['soldier_state'] != soldier_state.SoldierState.FILLINGTOWER:
            best_ruin = sensing.Sensing.find_best_ruin(cur_location, nearby_tiles)
            if best_ruin is not None:
                globals()['ruin_to_fill'] = best_ruin.get_map_location()
                globals()['soldier_state'] = soldier_state.SoldierState.FILLINGTOWER
                reset_variables()

def update_state_osama(cur_location, nearby_tiles):
    """
    Updates the robot state according to its paint level (LOWONPAINT) or nearby ruins (FILLING TOWER)
    Only cares about enemy paint if the round number is larger than the map length + map width
    """
    if robot.has_low_paint(constants.LOW_PAINT_THRESHOLD):
        if globals()['soldier_state'] != soldier_state.SoldierState.LOWONPAINT:
            globals()['intermediate_target'] = None
            reset_variables()
            globals()['stored_state'] = globals()['soldier_state']
            globals()['soldier_state'] = soldier_state.SoldierState.LOWONPAINT
    elif globals()['soldier_state'] not in [soldier_state.SoldierState.DELIVERINGMESSAGE, soldier_state.SoldierState.LOWONPAINT]:
        # Update enemy towers as necessary
        globals()['enemy_tile'] = update_enemy_towers(nearby_tiles)
        if globals()['enemy_tile'] is not None and globals()['last_tower'] is not None:
            globals()['soldier_type'] = soldier_type.SoldierType.ADVANCE
            reset_variables()
        if globals()['soldier_state'] != soldier_state.SoldierState.FILLINGTOWER:
            best_ruin = sensing.Sensing.find_any_ruin(cur_location, nearby_tiles)
            if best_ruin is not None:
                if not sensing.Sensing.can_build_tower(best_ruin.get_map_location()):
                    globals()['soldier_type'] = soldier_type.SoldierType.ADVANCE
                    reset_variables()
                else:
                    globals()['ruin_to_fill'] = best_ruin.get_map_location()
                    globals()['soldier_state'] = soldier_state.SoldierState.FILLINGTOWER
                    reset_variables()
        # Turn into an advance bot if they see an enemy paint that prevents tower building
        elif globals()['soldier_state'] == soldier_state.SoldierState.FILLINGTOWER:
            if not sensing.Sensing.can_build_tower(globals()['ruin_to_fill']):
                globals()['soldier_type'] = soldier_type.SoldierType.ADVANCE
                reset_variables()

def update_srp_state(cur_location, nearby_tiles):
    """Update state for SRP (Strategic Resource Pattern) soldiers"""
    if get_location() == globals()['srp_location']:
        globals()['srp_location'] = None
            
    if (globals()['soldier_state'] != soldier_state.SoldierState.LOWONPAINT and 
        robot.has_low_paint(constants.LOW_PAINT_THRESHOLD)):
        if globals()['soldier_state'] != soldier_state.SoldierState.STUCK:
            globals()['srp_location'] = get_location()
        reset_variables()
        globals()['stored_state'] = globals()['soldier_state']
        globals()['soldier_state'] = soldier_state.SoldierState.LOWONPAINT
    elif globals()['soldier_state'] == soldier_state.SoldierState.STUCK:
        # If less than 30, check 5x5 area for empty or ally primary tiles and mark center
        if (get_map_width() <= constants.SRP_MAP_WIDTH and 
            get_map_height() <= constants.SRP_MAP_HEIGHT and 
            not sensing.Sensing.sense_map_info(cur_location).get_mark().is_ally()):
            poss_srp = sensing.Sensing.sense_nearby_map_infos(8)
            can_build_srp = True
            for map_info in poss_srp:
                # If we can travel to tile and the paint is ally primary or empty, then build an srp
                if not map_info.is_passable() or map_info.get_paint().is_enemy():
                    can_build_srp = False
                    break
            # Check if srp is within build range
            if can_build_srp and len(poss_srp) == 25 and not sensing.Sensing.conflicts_srp():
                reset_variables()
                globals()['soldier_state'] = soldier_state.SoldierState.FILLINGSRP
                globals()['srp_center'] = get_location()
                mark(get_location(), False)
            elif robot.has_low_paint(constants.LOW_PAINT_THRESHOLD):
                for map_info in nearby_tiles:
                    if (map_info.get_paint().is_ally() and 
                        map_info.get_paint() != helper.resource_pattern_type(map_info.get_map_location())):
                        reset_variables()
                        globals()['soldier_state'] = soldier_state.SoldierState.FILLINGSRP

def fill_srp():
    """Creates SRP on small maps by placing marker to denote the center and painting around the marker"""
    if get_location() != globals()['srp_center']:
        dir = pathfinding.pathfind(globals()['srp_center'])
        if dir is not None and can_move(dir):
            move(dir)
    else:
        finished = True
        srp_complete = True
        for i in range(5):
            for j in range(5):
                if not on_the_map(get_location().translate(i - 2, j - 2)):
                    continue
                srp_loc = sensing.Sensing.sense_map_info(get_location().translate(i - 2, j - 2))
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
                globals()['soldier_state'] = soldier_state.SoldierState.STUCK
                globals()['srp_center'] = None
                globals()['num_turns_alive'] = 0
            if can_complete_resource_pattern(get_location()):
                complete_resource_pattern(get_location())
                globals()['soldier_state'] = soldier_state.SoldierState.STUCK
                globals()['srp_center'] = None
                globals()['num_turns_alive'] = 0

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
                direction = pathfinding.Pathfinding.pathfind(rc, nearby_location)
                if direction is not None and rc.can_move(direction):
                    rc.move(direction)
                    has_painted = True
                break
        
        # If we have a stored SRP location, try to move towards it
        if globals().get('SRPLocation') is not None:
            direction = pathfinding.Pathfinding.pathfind(rc, globals()['SRPLocation'])
            if direction is not None and rc.can_move(direction):
                rc.move(direction)
        # If we haven't painted anything and can't move to SRP location, we're stuck
        elif not has_painted:
            globals()['soldierState'] = soldier_state.SoldierState.STUCK

def msg_tower():
    """Pathfinds towards the last known paint tower and try to message it"""
    for enemy_robot in sense_nearby_robots(-1, get_team().opponent()):
        if enemy_robot.get_type().is_tower_type():
            if can_attack(enemy_robot.get_location()):
                attack(enemy_robot.get_map_location())
                break
                    
    tower_location = globals()['last_tower'].get_map_location()
    if can_sense_robot_at_location(tower_location) and can_send_message(tower_location):
        communication.Communication.send_map_information(globals()['enemy_tile'], tower_location)
        globals()['enemy_tile'] = None
        if globals()['soldier_state'] != globals()['stored_state']:
            globals()['soldier_state'] = globals()['stored_state']
        elif globals()['ruin_to_fill'] is not None:
            globals()['soldier_state'] = soldier_state.SoldierState.FILLINGTOWER
        else:
            globals()['soldier_state'] = soldier_state.SoldierState.STUCK
        reset_variables()
        if globals()['prev_location'] is not None:
            globals()['intermediate_target'] = globals()['prev_location']
            globals()['prev_location'] = None
        return
            
    dir = pathfinding.Pathfinding.return_to_tower()
    if dir is not None:
        move(dir)

def complete_ruin_if_possible(ruin_location):
    """Soldier version of completeRuinIfPossible"""
    robot.complete_ruin_if_possible(ruin_location)
    if can_sense_robot_at_location(ruin_location):
        globals()['soldier_state'] = soldier_state.SoldierState.LOWONPAINT
        globals()['stored_state'] = soldier_state.SoldierState.EXPLORING
        globals()['ruin_to_fill'] = None
        globals()['fill_tower_type'] = None

def fill_in_ruin(ruin_location):
    """
    Marks ruins
    Pathfinds to the ruins and fills in the area around the ruin if we can build a tower there
    If ignoreAlly is true, then we ignore the ruin if ally robots are already in proximity
    """
    # Mark the pattern we need to draw to build a tower here if we haven't already.
    # If robot has seen a paint tower, mark random tower
    if not sensing.Sensing.can_build_tower(ruin_location):
        if (can_sense_robot_at_location(ruin_location) and 
            sense_robot_at_location(ruin_location).get_type() == UnitType.LEVEL_ONE_PAINT_TOWER):
            globals()['soldier_state'] = soldier_state.SoldierState.LOWONPAINT
            globals()['stored_state'] = soldier_state.SoldierState.EXPLORING
            globals()['fill_tower_type'] = None
            globals()['ruin_to_fill'] = None
        else:
            globals()['soldier_state'] = soldier_state.SoldierState.EXPLORING
            globals()['fill_tower_type'] = None
            globals()['ruin_to_fill'] = None
    # Check to see if we know the type of tower to fill in
    elif globals()['fill_tower_type'] is not None:
        # Paint the tile at a location
        ruin_pattern = (constants.PAINT_TOWER_PATTERN if globals()['fill_tower_type'] == UnitType.LEVEL_ONE_PAINT_TOWER else 
                      constants.MONEY_TOWER_PATTERN if globals()['fill_tower_type'] == UnitType.LEVEL_ONE_MONEY_TOWER else 
                      constants.DEFENSE_TOWER_PATTERN)
        tile_to_paint = sensing.Sensing.find_paintable_ruin_tile(ruin_location, ruin_pattern)
        if tile_to_paint is not None:
            tile = ruin_location.translate(tile_to_paint[0], tile_to_paint[1])
            if can_paint(tile) and can_attack(tile):
                attack(tile, ruin_pattern[tile_to_paint[0]+2][tile_to_paint[1]+2] == PaintType.ALLY_SECONDARY)
        # Move to the ruin
        move_dir = pathfinding.Pathfinding.pathfind(ruin_location)
        if move_dir is not None:
            move(move_dir)
        # Tries to complete the ruin
        complete_ruin_if_possible(ruin_location)
    else:
        # Determine the marking of the tower and mark if no marking present
        north_tower = ruin_location.add(Direction.NORTH)
        if can_sense_location(north_tower):
            tower_marking = sensing.Sensing.sense_map_info(north_tower).get_mark()
            # If mark type is 1, then ruin is a paint ruin
            if tower_marking == PaintType.ALLY_PRIMARY:
                globals()['fill_tower_type'] = UnitType.LEVEL_ONE_PAINT_TOWER
            # If no mark, then check to see if there is a marking on east for defense tower
            elif tower_marking == PaintType.EMPTY:
                defense_mark_loc = north_tower.add(Direction.EAST)
                if can_sense_location(defense_mark_loc):
                    if sensing.Sensing.sense_map_info(defense_mark_loc).get_mark() == PaintType.ALLY_PRIMARY:
                        globals()['fill_tower_type'] = UnitType.LEVEL_ONE_DEFENSE_TOWER
                    # If can sense location but no mark, then figure out tower type
                    else:
                        tower_type = robot.gen_tower_type(ruin_location)
                        if tower_type == UnitType.LEVEL_ONE_DEFENSE_TOWER and can_mark(defense_mark_loc):
                            # Mark defense tower at north east
                            mark(defense_mark_loc, False)
                            globals()['fill_tower_type'] = UnitType.LEVEL_ONE_DEFENSE_TOWER
                        # If can mark tower, then mark it
                        elif can_mark(north_tower) and tower_type != UnitType.LEVEL_ONE_DEFENSE_TOWER:
                            if globals()['seen_paint_tower']:
                                mark(north_tower, tower_type == UnitType.LEVEL_ONE_MONEY_TOWER)
                                globals()['fill_tower_type'] = tower_type
                            else:
                                # Otherwise, mark a paint tower
                                mark(north_tower, False)
                                globals()['fill_tower_type'] = UnitType.LEVEL_ONE_PAINT_TOWER
                        # Otherwise, pathfind towards location until can mark it
                        else:
                            move_dir = pathfinding.Pathfinding.pathfind(ruin_location)
                            if move_dir is not None:
                                move(move_dir)
            # Otherwise, ruin is a money ruin
            else:
                globals()['fill_tower_type'] = UnitType.LEVEL_ONE_MONEY_TOWER
        # Otherwise, pathfind to the tower
        else:
            move_dir = pathfinding.Pathfinding.pathfind(ruin_location)
            if move_dir is not None:
                move(move_dir)

def stuck_behavior():
    """Stuck behavior method"""
    if globals()['soldier_type'] in [soldier_type.SoldierType.DEVELOP, soldier_type.SoldierType.SRP]:
        new_dir = pathfinding.Pathfinding.find_own_corner()
    else:
        new_dir = pathfinding.Pathfinding.get_unstuck()
            
    if new_dir is not None:
        move(new_dir)
        paint_if_possible(get_location())

def reset_variables():
    """Reset variables for soldier"""
    globals()['enemy_target'] = None
    globals()['enemy_tower'] = None
    globals()['alert_robots'] = False
    globals()['alert_attack_soldiers'] = False
    globals()['broadcast'] = False
    globals()['ruin_to_fill'] = None
    globals()['fill_tower_type'] = None
    globals()['rounds_without_enemy'] = globals().get('rounds_without_enemy', 0) + 1
