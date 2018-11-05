from player import *
from lstm_adapter import *

def server_classify(player_ids, parent_classify_send, parent_classify_recv, remote_player_rates):
	activity_rates = {"Low":LOW_TICK, "Mid":MEDIUM_TICK, "High":HIGH_TICK}
	lstm = LSTM(player_ids)
	#max_rows = lstm.max_rows()
	# we loop for as long as the match goes for
	#seconds = int(max_rows / SAMPLE_PERIOD)
	#timeout = time.time() + 20 # seconds
	# we store a player count and list of player activities locally to identify any change that mandates a transmission
	player_count = len(player_ids)
	player_activities = [None for i in range(player_count)]
	print("Server Classify Process Started")
	# start our send and recv processes, which will in turn start the players
	parent_classify_recv.send(1)
	parent_classify_send.send(1)
	while True: # time.time() <= timeout:
		for player_id in player_ids:
			if player_activities[player_id] == "Finished":
				continue
			activity = lstm.classify(player_id)
			# if this was the last activity for the player, we can tell the send and recv threads to close their connections to these players, allowing the player processes to terminate
			if activity == "Finished":
				print("Player {} has left the game".format(player_id))
				player_activities[player_id] = activity
				player_count -= 1
				# tell recv and send to close down this player's connection
				parent_classify_recv.send((player_id, 0))
				parent_classify_send.send((player_id, 0))
			elif activity != player_activities[player_id]:
				# adjust the player/client's send rate remotely and locally
				remote_player_rates[player_id].value = activity_rates[activity]
				player_activities[player_id] = activity
				# tell send to adjust this player's connection
				parent_classify_send.send((player_id, activity_rates[activity]))
			# the only other condition is that there is no change in activity, so we do nothing
		if player_count <= 0:
			break
		# wait n milliseconds before predicting again
		time.sleep(SAMPLE_PERIOD) 
	# tell our send and recv processes to terminate
	parent_classify_send.send(0)
	parent_classify_recv.send(0)
	# collect our performance data and close our tensorflow session
	lstm.show_accuracy()
	lstm.close()

# send players a datagram packet containing required data		
def server_send(server_send_conns, child_classify_send):
	current_ticks = 0
	tickrates_high = []
	tickrates_mid = []
	tickrates_low = []
	tickrate_map = {HIGH_TICK: 0, MEDIUM_TICK: 0, LOW_TICK: 0}
	# wait for our signal to start from server_classify
	child_classify_send.recv()
	print("Server Send Process Started")
	# Tell all of our player send processes to start - we know everyone is in the middle queue first
	current_minute_start = current_milli_time()
	for player_conn, rate in server_send_conns.values():
		player_conn.send(current_minute_start)
	while True:
		# we need to poll our classify process to see if any player connections need to be closed or updated, or if we need to be terminated
		if child_classify_send.poll():
			message = child_classify_send.recv()
			if message == 0:
				# classify has sent us the terminate signal
				break
			elif len(message) == 2:
				player_id = message[0]
				rate = message[1]
				# if we received a packet to close down this player's connection
				if rate == 0:
					# find the player's queue and close their connection
					print("Closing player {}'s connection".format(player_id))
					server_send_conns[player_id][0].send(0)
					server_send_conns.pop(player_id)
				else:
					# update that player's rate 
					server_send_conns[player_id][1] = rate
		# if we are still ticking for the same minute
		if current_milli_time() - current_minute_start <= 1000:
			curr_time = current_milli_time()
			for player_conn, rate in server_send_conns.values():
				if current_ticks < rate:
					player_conn.send(curr_time)
					tickrate_map[rate] += 1
			current_ticks += 1
		else: # we have moved into the next minute and must reset some variables
			tickrates_low.append(int(tickrate_map[LOW_TICK] / len(server_send_conns)))
			tickrates_mid.append(int(tickrate_map[MEDIUM_TICK] / len(server_send_conns)))
			tickrates_high.append(int(tickrate_map[HIGH_TICK] / len(server_send_conns)))
			tickrate_map[LOW_TICK] = 0
			tickrate_map[MEDIUM_TICK] = 0
			tickrate_map[HIGH_TICK] = 0
			current_ticks = 0
			current_minute_start += 1000
	print("Send Server's low priority send tickrates: {}".format(tickrates_low))
	print("Send Server's mid priority send tickrates: {}".format(tickrates_mid))
	print("Send Server's high priority send tickrates: {}".format(tickrates_high))

# receive a datagram packet containing required data
def server_recv(server_recv_conns, child_classify_recv):
	# init metric data structures
	player_recv_tick_counts = {}
	player_recv_tickrates = {}
	for player_id, child_conn in server_recv_conns.items():
		player_recv_tick_counts[player_id] = 0
		player_recv_tickrates[player_id] = []
	# wait for our signal to start from server_classify
	child_classify_recv.recv()
	print("Server Receive Process Started")
	# Tell all of our player send processes to start
	current_minute_start = current_milli_time()
	for player_conn in server_recv_conns.values():
		player_conn.send(current_minute_start)
	while True:
		# we need to poll our classify process to see if any player connections need to be closed or we need to be terminated
		if child_classify_recv.poll():
			message = child_classify_recv.recv()
			if message == 0:
				# classify has sent us the terminate signal
				break
			elif len(message) == 2:
				# we received a packet to close down this player's connection
				player_id= message[0]
				if message[1] == 0:
					print("Closing player {}'s connection".format(player_id))
					server_recv_conns[player_id].send(0)
					server_recv_conns.pop(player_id)
		for player_id, child_conn in server_recv_conns.items():
			if child_conn.poll():
				send_ts = child_conn.recv()
				# update tickrate
				if current_milli_time() - current_minute_start <= 1000:
					player_recv_tick_counts[player_id] += 1
				else:
					player_recv_tickrates[player_id].append(player_recv_tick_counts[player_id])
					player_recv_tick_counts[player_id] = 0
					current_minute_start += 1000
	print("Server's recv tickrates: {}".format(player_recv_tickrates))
