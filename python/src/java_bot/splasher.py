from battlecode25.stubs import *
from .robot import Robot
from .communication import Communication
from .robot_info_codec import RobotInfoCodec
from .map_info_codec import MapInfoCodec

class Splasher:
    """Class for handling splasher robot functionality"""
    
    @staticmethod
    def receive_last_message(rc):
        """Process the last received message for the splasher"""
        for msg in rc.read_messages(-1):
            bytes = msg.get_bytes()
            # Receives message of what type of splasher it is
            if bytes == 4:
                continue
                
            if Communication.is_robot_info(bytes):
                message = RobotInfoCodec.decode(bytes)
                continue
            else:
                message = MapInfoCodec.decode(bytes)
                # If enemy paint, then store enemy paint
                if message.get_paint().is_enemy():
                    robot_loc = rc.get_location()
                    if (globals()['remove_paint'] is None or 
                        robot_loc.distance_squared_to(message.get_map_location()) < 
                        robot_loc.distance_squared_to(globals()['remove_paint'].get_map_location())):
                        globals()['remove_paint'] = message
                        Robot.reset_variables()
                # If enemy tower, then go to enemy tower location
                elif message.has_ruin():
                    if globals()['remove_paint'] is None:
                        globals()['remove_paint'] = message
                        Robot.reset_variables()
