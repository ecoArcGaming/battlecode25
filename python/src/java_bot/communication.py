from battlecode25.stubs import *
import RobotInfoCodec
import MapInfoCodec

def send_robot_information(robot_info, target_loc):
    """Sends an encoded robotInfo to targetLoc"""
    encoded_info = RobotInfoCodec.encode(robot_info)
    if can_send_message(target_loc, encoded_info):
        send_message(target_loc, encoded_info)

def send_map_information(map_info, target_loc):
    """Send Map information to targetLoc"""
    if map_info is None:
        return
    encoded_info = MapInfoCodec.encode(map_info)
    if can_send_message(target_loc, encoded_info):
        send_message(target_loc, encoded_info)

def is_robot_info(msg):
    """Checks to see if input message is a robot info or map info"""
    return msg >> 21 > 0
