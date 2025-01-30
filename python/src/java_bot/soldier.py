from battlecode25.stubs import *
from .robot import Robot
from .constants import *
from .sensing import Sensing
from .helper import Helper
from .pathfinding import Pathfinding
from .communication import Communication
from .robot_info_codec import RobotInfoCodec
from .map_info_codec import MapInfoCodec
from .hashable_coords import HashableCoords
from .soldier_state import SoldierState
from .soldier_type import SoldierType
import random

class Soldier(Robot):
    """Class for all methods that a soldier will do"""
    
    @staticmethod
    def low_paint_behavior(rc):
        """Method for soldier to do when low on paint"""
        Robot.low_paint_behavior(rc)
        if rc.get_paint() > LOW_PAINT_THRESHOLD:
            if globals()['soldier_state'] != globals()['stored_state']:
                globals()['soldier_state'] = globals()['stored_state']
            elif globals()['ruin_to_fill'] is not None:
                globals()['soldier_state'] = SoldierState.FILLINGTOWER
            else:
                globals()['soldier_state'] = SoldierState.STUCK
            Soldier.reset_variables()

    @staticmethod
    def paint_if_possible(rc, paint_tile=None, paint_location=None):
        """
        Methods for soldiers painting, given a MapInfo and/or MapLocation
        Paints when there is no paint or if allied paint is incorrect
        """
        if paint_location is None and paint_tile is not None:
            paint_location = paint_tile.get_map_location()
        elif paint_tile is None and paint_location is not None:
            paint_tile = rc.sense_map_info(rc.get_location())
            
        if (paint_tile.get_paint() == PaintType.EMPTY and 
            rc.can_attack(paint_location) and 
            paint_tile.get_mark() == PaintType.EMPTY):
            # If map size less than 30 by 30, then don't fill in SRP colors as wandering
            if rc.get_map_width() <= SRP_MAP_WIDTH and rc.get_map_height() <= SRP_MAP_HEIGHT:
                rc.attack(paint_location, False)
            else:
                rc.attack(paint_location, not Helper.resource_pattern_grid(rc, paint_location))

    @staticmethod
    def read_new_messages(rc):
        """Reads incoming messages and updates internal variables/state as necessary"""
        # Looks at all incoming messages from the past round
        for message in rc.read_messages(rc.get_round_num() - 1):
            bytes = message.get_bytes()
            # Information is type of robot
            if bytes in [0, 1, 2]:
                if bytes == 0:
                    if (random.random() <= DEV_SRP_BOT_SPLIT or 
                        (rc.get_map_width() <= SRP_MAP_WIDTH and rc.get_map_height() <= SRP_MAP_HEIGHT)):
                        globals()['soldier_type'] = SoldierType.DEVELOP
                    else:
                        globals()['soldier_type'] = SoldierType.SRP
                        globals()['soldier_state'] = SoldierState.FILLINGSRP
                elif bytes == 1:
                    globals()['soldier_type'] = SoldierType.ADVANCE
                elif bytes == 2:
                    globals()['soldier_type'] = SoldierType.ATTACK
            elif globals()['soldier_type'] in [SoldierType.ADVANCE, SoldierType.ATTACK]:
                tile = MapInfoCodec.decode(bytes)
                if tile.has_ruin():
                    globals()['enemy_tower'] = tile
                    globals()['soldier_type'] = SoldierType.ATTACK
                    Soldier.reset_variables()
                globals()['wander_target'] = tile.get_map_location()

    @staticmethod
    def update_enemy_tiles(rc, nearby_tiles):
        """
        Returns the MapInfo of a nearby tower, and then a nearby tile if any are sensed
        Nearby tiles only updated at a maximum of once every 15 turns
        Returns null if none are sensed.
        """
        # Check if there are enemy paint or enemy towers sensed
        closest_enemy_tower = Sensing.tower_in_range(rc, 20, False)
        if closest_enemy_tower is not None:
            return rc.sense_map_info(closest_enemy_tower.get_location())
            
        # Find all Enemy Tiles and return one if one exists, but only care once every 15 rounds
        enemy_paint = Sensing.find_enemy_paint(rc, nearby_tiles)
        if globals()['soldier_msg_cooldown'] == -1 and enemy_paint is not None:
            globals()['soldier_msg_cooldown'] = 30
            return enemy_paint
        return None

    @staticmethod
    def update_enemy_towers(rc, nearby_tiles):
        """
        Returns the MapInfo of a nearby tower
        Nearby towers only updated at a maximum of once every 30 turns
        Returns null if none are sensed.
        """
        # Check if there are enemy paint or enemy towers sensed
        closest_enemy_tower = Sensing.tower_in_range(rc, 20, False)
        if closest_enemy_tower is not None:
            return rc.sense_map_info(closest_enemy_tower.get_location())
        return None

    @staticmethod
    def update_state(rc, cur_location, nearby_tiles):
        """
        Updates the robot state according to its paint level (LOWONPAINT),
        nearby enemy paint (DELIVERINGMESSAGE), or nearby ruins (FILLING TOWER)
        """
        if (Soldier.has_low_paint(rc, LOW_PAINT_THRESHOLD) and 
            (rc.get_money() < LOW_PAINT_MONEY_THRESHOLD or globals()['soldier_state'] == SoldierState.FILLINGTOWER)):
            if globals()['soldier_state'] != SoldierState.LOWONPAINT:
                globals()['intermediate_target'] = None
                Soldier.reset_variables()
                globals()['stored_state'] = globals()['soldier_state']
                globals()['soldier_state'] = SoldierState.LOWONPAINT
        elif globals()['soldier_state'] not in [SoldierState.DELIVERINGMESSAGE, SoldierState.LOWONPAINT]:
            # Update enemy tile as necessary
            globals()['enemy_tile'] = Soldier.update_enemy_tiles(rc, nearby_tiles)
            if globals()['enemy_tile'] is not None and globals()['last_tower'] is not None:
                if globals()['soldier_state'] == SoldierState.EXPLORING:
                    globals()['prev_location'] = rc.get_location()
                    Soldier.reset_variables()
                else:
                    globals()['intermediate_target'] = None
                    Soldier.reset_variables()
                globals()['stored_state'] = globals()['soldier_state']
                globals()['soldier_state'] = SoldierState.DELIVERINGMESSAGE
            # Check for nearby buildable ruins if we are not currently building one
            elif globals()['soldier_state'] != SoldierState.FILLINGTOWER:
                best_ruin = Sensing.find_best_ruin(rc, cur_location, nearby_tiles)
                if best_ruin is not None:
                    globals()['ruin_to_fill'] = best_ruin.get_map_location()
                    globals()['soldier_state'] = SoldierState.FILLINGTOWER
                    Soldier.reset_variables()

    @staticmethod
    def update_state_osama(rc, cur_location, nearby_tiles):
        """
        Updates the robot state according to its paint level (LOWONPAINT) or nearby ruins (FILLING TOWER)
        Only cares about enemy paint if the round number is larger than the map length + map width
        """
        if Soldier.has_low_paint(rc, LOW_PAINT_THRESHOLD):
            if globals()['soldier_state'] != SoldierState.LOWONPAINT:
                globals()['intermediate_target'] = None
                Soldier.reset_variables()
                globals()['stored_state'] = globals()['soldier_state']
                globals()['soldier_state'] = SoldierState.LOWONPAINT
        elif globals()['soldier_state'] not in [SoldierState.DELIVERINGMESSAGE, SoldierState.LOWONPAINT]:
            # Update enemy towers as necessary
            globals()['enemy_tile'] = Soldier.update_enemy_towers(rc, nearby_tiles)
            if globals()['enemy_tile'] is not None and globals()['last_tower'] is not None:
                globals()['soldier_type'] = SoldierType.ADVANCE
                Soldier.reset_variables()
            if globals()['soldier_state'] != SoldierState.FILLINGTOWER:
                best_ruin = Sensing.find_any_ruin(rc, cur_location, nearby_tiles)
                if best_ruin is not None:
                    if not Sensing.can_build_tower(rc, best_ruin.get_map_location()):
                        globals()['soldier_type'] = SoldierType.ADVANCE
                        Soldier.reset_variables()
                    else:
                        globals()['ruin_to_fill'] = best_ruin.get_map_location()
                        globals()['soldier_state'] = SoldierState.FILLINGTOWER
                        Soldier.reset_variables()
            # Turn into an advance bot if they see an enemy paint that prevents tower building
            elif globals()['soldier_state'] == SoldierState.FILLINGTOWER:
                if not Sensing.can_build_tower(rc, globals()['ruin_to_fill']):
                    globals()['soldier_type'] = SoldierType.ADVANCE
                    Soldier.reset_variables()

    @staticmethod
    def update_srp_state(rc, cur_location, nearby_tiles):
        """Update state for SRP (Strategic Resource Pattern) soldiers"""
        if rc.get_location() == globals()['srp_location']:
            globals()['srp_location'] = None
            
        if (globals()['soldier_state'] != SoldierState.LOWONPAINT and 
            Soldier.has_low_paint(rc, LOW_PAINT_THRESHOLD)):
            if globals()['soldier_state'] != SoldierState.STUCK:
                globals()['srp_location'] = rc.get_location()
            Soldier.reset_variables()
            globals()['stored_state'] = globals()['soldier_state']
            globals()['soldier_state'] = SoldierState.LOWONPAINT
        elif globals()['soldier_state'] == SoldierState.STUCK:
            # If less than 30, check 5x5 area for empty or ally primary tiles and mark center
            if (rc.get_map_width() <= SRP_MAP_WIDTH and 
                rc.get_map_height() <= SRP_MAP_HEIGHT and 
                not rc.sense_map_info(cur_location).get_mark().is_ally()):
                poss_srp = rc.sense_nearby_map_infos(8)
                can_build_srp = True
                for map_info in poss_srp:
                    # If we can travel to tile and the paint is ally primary or empty, then build an srp
                    if not map_info.is_passable() or map_info.get_paint().is_enemy():
                        can_build_srp = False
                        break
                # Check if srp is within build range
                if can_build_srp and len(poss_srp) == 25 and not Sensing.conflicts_srp(rc):
                    Soldier.reset_variables()
                    globals()['soldier_state'] = SoldierState.FILLINGSRP
                    globals()['srp_center'] = rc.get_location()
                    rc.mark(rc.get_location(), False)
            elif Soldier.has_low_paint(rc, LOW_PAINT_THRESHOLD):
                for map_info in nearby_tiles:
                    if (map_info.get_paint().is_ally() and 
                        map_info.get_paint() != Helper.resource_pattern_type(rc, map_info.get_map_location())):
                        Soldier.reset_variables()
                        globals()['soldier_state'] = SoldierState.FILLINGSRP

    @staticmethod
    def fill_srp(rc):
        """Creates SRP on small maps by placing marker to denote the center and painting around the marker"""
        if rc.get_location() != globals()['srp_center']:
            dir = Pathfinding.pathfind(rc, globals()['srp_center'])
            if dir is not None and rc.can_move(dir):
                rc.move(dir)
        else:
            finished = True
            srp_complete = True
            for i in range(5):
                for j in range(5):
                    if not rc.on_the_map(rc.get_location().translate(i - 2, j - 2)):
                        continue
                    srp_loc = rc.sense_map_info(rc.get_location().translate(i - 2, j - 2))
                    is_primary = HashableCoords(i, j) in PRIMARY_SRP
                    if ((srp_loc.get_paint() == PaintType.ALLY_PRIMARY and is_primary) or 
                        (srp_loc.get_paint() == PaintType.ALLY_SECONDARY and not is_primary)):
                        continue
                    srp_complete = False
                    if not rc.can_attack(srp_loc.get_map_location()):
                        continue
                    # If paint is empty or ally paint doesnt match, then paint proper color
                    if srp_loc.get_paint() == PaintType.EMPTY:
                        rc.attack(srp_loc.get_map_location(), not is_primary)
                        finished = False
                        break
                    elif srp_loc.get_paint() == PaintType.ALLY_PRIMARY and not is_primary:
                        rc.attack(srp_loc.get_map_location(), True)
                        finished = False
                        break
                    elif srp_loc.get_paint() == PaintType.ALLY_SECONDARY and is_primary:
                        rc.attack(srp_loc.get_map_location(), False)
                        finished = False
                        break
                if not finished:
                    break
                    
            if finished:
                if srp_complete:
                    globals()['soldier_state'] = SoldierState.STUCK
                    globals()['srp_center'] = None
                    globals()['num_turns_alive'] = 0
                if rc.can_complete_resource_pattern(rc.get_location()):
                    rc.complete_resource_pattern(rc.get_location())
                    globals()['soldier_state'] = SoldierState.STUCK
                    globals()['srp_center'] = None
                    globals()['num_turns_alive'] = 0

    @staticmethod
    def msg_tower(rc):
        """Pathfinds towards the last known paint tower and try to message it"""
        for enemy_robot in rc.sense_nearby_robots(-1, rc.get_team().opponent()):
            if enemy_robot.get_type().is_tower_type():
                if rc.can_attack(enemy_robot.get_location()):
                    rc.attack(enemy_robot.get_location())
                    break
                    
        tower_location = globals()['last_tower'].get_map_location()
        if rc.can_sense_robot_at_location(tower_location) and rc.can_send_message(tower_location):
            Communication.send_map_information(rc, globals()['enemy_tile'], tower_location)
            globals()['enemy_tile'] = None
            if globals()['soldier_state'] != globals()['stored_state']:
                globals()['soldier_state'] = globals()['stored_state']
            elif globals()['ruin_to_fill'] is not None:
                globals()['soldier_state'] = SoldierState.FILLINGTOWER
            else:
                globals()['soldier_state'] = SoldierState.STUCK
            Soldier.reset_variables()
            if globals()['prev_location'] is not None:
                globals()['intermediate_target'] = globals()['prev_location']
                globals()['prev_location'] = None
            return
            
        dir = Pathfinding.return_to_tower(rc)
        if dir is not None:
            rc.move(dir)

    @staticmethod
    def complete_ruin_if_possible(rc, ruin_location):
        """Soldier version of completeRuinIfPossible"""
        Robot.complete_ruin_if_possible(rc, ruin_location)
        if rc.can_sense_robot_at_location(ruin_location):
            globals()['soldier_state'] = SoldierState.LOWONPAINT
            globals()['stored_state'] = SoldierState.EXPLORING
            globals()['ruin_to_fill'] = None
            globals()['fill_tower_type'] = None

    @staticmethod
    def fill_in_ruin(rc, ruin_location):
        """
        Marks ruins
        Pathfinds to the ruins and fills in the area around the ruin if we can build a tower there
        If ignoreAlly is true, then we ignore the ruin if ally robots are already in proximity
        """
        # Mark the pattern we need to draw to build a tower here if we haven't already.
        # If robot has seen a paint tower, mark random tower
        if not Sensing.can_build_tower(rc, ruin_location):
            if (rc.can_sense_robot_at_location(ruin_location) and 
                rc.sense_robot_at_location(ruin_location).get_type() == UnitType.LEVEL_ONE_PAINT_TOWER):
                globals()['soldier_state'] = SoldierState.LOWONPAINT
                globals()['stored_state'] = SoldierState.EXPLORING
                globals()['fill_tower_type'] = None
                globals()['ruin_to_fill'] = None
            else:
                globals()['soldier_state'] = SoldierState.EXPLORING
                globals()['fill_tower_type'] = None
                globals()['ruin_to_fill'] = None
        # Check to see if we know the type of tower to fill in
        elif globals()['fill_tower_type'] is not None:
            # Paint the tile at a location
            ruin_pattern = (PAINT_TOWER_PATTERN if globals()['fill_tower_type'] == UnitType.LEVEL_ONE_PAINT_TOWER else 
                          MONEY_TOWER_PATTERN if globals()['fill_tower_type'] == UnitType.LEVEL_ONE_MONEY_TOWER else 
                          DEFENSE_TOWER_PATTERN)
            tile_to_paint = Sensing.find_paintable_ruin_tile(rc, ruin_location, ruin_pattern)
            if tile_to_paint is not None:
                tile = ruin_location.translate(tile_to_paint[0], tile_to_paint[1])
                if rc.can_paint(tile) and rc.can_attack(tile):
                    rc.attack(tile, ruin_pattern[tile_to_paint[0]+2][tile_to_paint[1]+2] == PaintType.ALLY_SECONDARY)
            # Move to the ruin
            move_dir = Pathfinding.pathfind(rc, ruin_location)
            if move_dir is not None:
                rc.move(move_dir)
            # Tries to complete the ruin
            Soldier.complete_ruin_if_possible(rc, ruin_location)
        else:
            # Determine the marking of the tower and mark if no marking present
            north_tower = ruin_location.add(Direction.NORTH)
            if rc.can_sense_location(north_tower):
                tower_marking = rc.sense_map_info(north_tower).get_mark()
                # If mark type is 1, then ruin is a paint ruin
                if tower_marking == PaintType.ALLY_PRIMARY:
                    globals()['fill_tower_type'] = UnitType.LEVEL_ONE_PAINT_TOWER
                # If no mark, then check to see if there is a marking on east for defense tower
                elif tower_marking == PaintType.EMPTY:
                    defense_mark_loc = north_tower.add(Direction.EAST)
                    if rc.can_sense_location(defense_mark_loc):
                        if rc.sense_map_info(defense_mark_loc).get_mark() == PaintType.ALLY_PRIMARY:
                            globals()['fill_tower_type'] = UnitType.LEVEL_ONE_DEFENSE_TOWER
                        # If can sense location but no mark, then figure out tower type
                        else:
                            tower_type = Robot.gen_tower_type(rc, ruin_location)
                            if tower_type == UnitType.LEVEL_ONE_DEFENSE_TOWER and rc.can_mark(defense_mark_loc):
                                # Mark defense tower at north east
                                rc.mark(defense_mark_loc, False)
                                globals()['fill_tower_type'] = UnitType.LEVEL_ONE_DEFENSE_TOWER
                            # If can mark tower, then mark it
                            elif rc.can_mark(north_tower) and tower_type != UnitType.LEVEL_ONE_DEFENSE_TOWER:
                                if globals()['seen_paint_tower']:
                                    rc.mark(north_tower, tower_type == UnitType.LEVEL_ONE_MONEY_TOWER)
                                    globals()['fill_tower_type'] = tower_type
                                else:
                                    # Otherwise, mark a paint tower
                                    rc.mark(north_tower, False)
                                    globals()['fill_tower_type'] = UnitType.LEVEL_ONE_PAINT_TOWER
                            # Otherwise, pathfind towards location until can mark it
                            else:
                                move_dir = Pathfinding.pathfind(rc, ruin_location)
                                if move_dir is not None:
                                    rc.move(move_dir)
                    # Otherwise, pathfind to ruin location since we can't sense the location of the ruin
                    else:
                        move_dir = Pathfinding.pathfind(rc, ruin_location)
                        if move_dir is not None:
                            rc.move(move_dir)
                # Otherwise, ruin is a money ruin
                else:
                    globals()['fill_tower_type'] = UnitType.LEVEL_ONE_MONEY_TOWER
            # Otherwise, pathfind to the tower
            else:
                move_dir = Pathfinding.pathfind(rc, ruin_location)
                if move_dir is not None:
                    rc.move(move_dir)

    @staticmethod
    def stuck_behavior(rc):
        """Stuck behavior method"""
        if globals()['soldier_type'] in [SoldierType.DEVELOP, SoldierType.SRP]:
            new_dir = Pathfinding.find_own_corner(rc)
        else:
            new_dir = Pathfinding.get_unstuck(rc)
            
        if new_dir is not None:
            rc.move(new_dir)
            Soldier.paint_if_possible(rc, rc.get_location())
