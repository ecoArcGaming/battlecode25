from battlecode25.stubs import *
from .robot import Robot
from .communication import Communication
from .robot_info_codec import RobotInfoCodec
from .map_info_codec import MapInfoCodec
from .pathfinding import Pathfinding
import random

class Mopper(Robot):
    """Class for handling mopper robot functionality"""
    
    @staticmethod
    def receive_last_message():
        """Process the last received message for the mopper"""
        for msg in read_messages(-1):
            bytes = msg.get_bytes()
            # Receives what type of mopper the bot is
            if bytes == 3:
                continue
                
            if Communication.is_robot_info(bytes):
                message = RobotInfoCodec.decode(bytes)
            else:
                message = MapInfoCodec.decode(bytes)
                if message.get_paint().is_enemy():
                    robot_loc = get_location()
                    if (globals()['remove_paint'] is None or 
                        robot_loc.distance_squared_to(message.get_map_location()) < 
                        robot_loc.distance_squared_to(globals()['remove_paint'].get_map_location())):
                        globals()['remove_paint'] = message
                        Robot.reset_variables()
                # If enemy tower, then go to enemy tower location
                elif message.has_ruin():
                    robot_loc = get_location()
                    if (globals()['remove_paint'] is None or 
                        robot_loc.distance_squared_to(message.get_map_location()) < 
                        robot_loc.distance_squared_to(globals()['remove_paint'].get_map_location())):
                        globals()['remove_paint'] = message
                        Robot.reset_variables()

    @staticmethod
    def remove_paint(enemy_paint):
        """Remove enemy paint at the specified location"""
        enemy_loc = enemy_paint.get_map_location()
        if can_attack(enemy_loc) and enemy_paint.get_paint().is_enemy():
            attack(enemy_loc)
            globals()['remove_paint'] = None
            Robot.reset_variables()
        else:
            move_dir = Pathfinding.pathfind(enemy_loc)
            if move_dir is not None:
                move(move_dir)

    @staticmethod
    def mopper_scoring():
        """Score nearby tiles for mopper movement"""
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

    @staticmethod
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

    @staticmethod
    def mopper_walk():
        """Random walk for mopper on safe tiles"""
        safe = []
        for map_info in sense_nearby_map_infos(2):
            if map_info.get_paint().is_ally() and map_info.get_map_location() not in globals()['last8']:
                safe.append(map_info)
                
        if not safe:
            return None
            
        map_info = random.choice(safe)
        return get_location().direction_to(map_info.get_map_location())
