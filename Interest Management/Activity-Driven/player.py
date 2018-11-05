import time
import random
from constants import *

current_milli_time = lambda: int(round(time.time() * 1000))

# Send the server a packet containing game data		
def player_send(id, player_parent_send, rate):
	current_ticks = 0
	tickrates = []
	# wait for the starting ts signal from server_recv to start sending packets
	current_minute_start = player_parent_send.recv()
	print("Player {} Send Process Started".format(id))
	while True:
		try:
			# we can periodically check if our send_rate has been updated (or just use rate.value always if we want to constantly check and the serialization cost isn't too bad)
			# if c % 7 == 0:
			# 	send_rate = rate.value
			# 	c = 0
			# print("Rate: {}".format(rate.value))
			# if we are still ticking for the same minute
			if current_milli_time() - current_minute_start <= 1000:
				# If we need to wait for the next minute to come before sending more updates
				if current_ticks >= rate.value:
					continue
				current_ticks += 1
				# send a packet (containing a timestamp) to the server
				player_parent_send.send(current_milli_time())
			else: # we have moved into the next minute and must reset some variables
				tickrates.append(current_ticks)
				current_ticks = 0
				current_minute_start += 1000
			# check if we need to shut down
			if player_parent_send.poll():
				m = player_parent_send.recv()
				if m == 0:
					break
		except BrokenPipeError: # the server's recv process has closed their end of the connection, signalling us to terminate
			break
	print("Player {}'s send tickrates: {}".format(id, tickrates))

# receive a packet containing required data
def player_recv(id, player_child_recv, latency_range):
	waiting_on = []
	tickrates = []
	current_ticks = 0
	# wait for the starting ts signal from server_send to start receiving packets
	current_minute_start = player_child_recv.recv()
	print("Player {} Receive Process Started".format(id))
	while True:
		try:
			# NOTE: will raise an EOFError if the server has closed this connection, so now we know to shut down
			send_ts = player_child_recv.recv()
			if send_ts == 0:
				break
			# simulate this player's latency
			latency = random.randint(latency_range[0], latency_range[1])
			# add latency to this packet's sent time to know when we theortically should receive it
			recv_ts = send_ts + latency
			# add this packet to our queue for checking later
			waiting_on.append(recv_ts)
			# update tickrate
			if current_milli_time() - current_minute_start <= 1000:
				prelen = len(waiting_on)
				# update list with all packets that should have arrived
				waiting_on[:] = [ts for ts in waiting_on if ts >= current_milli_time()]
				# update tick count by the amount of packets removed (processed)
				current_ticks += prelen - len(waiting_on)
			else:
				tickrates.append(current_ticks)
				current_ticks = 0
				current_minute_start += 1000
		except BrokenPipeError: # the server's send process has closed their end of the connection, signalling us to terminate
			break
	print("Player {}'s recv tickrates: {}".format(id, tickrates))
