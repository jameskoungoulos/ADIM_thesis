from server import * 
from multiprocessing import Process, Manager, Event, Pipe
import sys
import ctypes

if __name__ == '__main__':

	manager = Manager()

	# load player network profiles (just latency ranges)
	profiles = [(15,17),(30,34),(60,65),(85,95),(120,135),(180,200),(220,250)]
	
	# the server_send process is given the following dictionary (id,proxy) of proxies for each player's connection in that category
	server_send_conns = {}
	# we also need a map of connections the server must poll for receiving player packets
	server_recv_conns = {}
	# and a map of player rates for the classifier to change when needed
	player_rates = {}
	# list of objects required for players
	player_data = []
	player_ids = []

	# each player gets a sequential integer id
	for id in range(int(sys.argv[1])):
		# create 1 unidirectional and 1 bidirectional pipe - one for the server to send through and one for the server to receive the player's packets
		server_parent_send, player_child_recv = Pipe() # our server_send will send via parent_send, our player_recv will poll on player_child_recv
		player_parent_send, server_child_recv = Pipe(duplex=True) # our server_recv will loop through all of its server_child_recv conns for incoming data, our player_send will send through player_parent_send
		server_send_conns[id] = [server_parent_send, MEDIUM_TICK]
		server_recv_conns[id] = server_child_recv
		# also start off with a medium send rate
		player_rate = manager.Value('d', MEDIUM_TICK, False)
		player_rates[id] = player_rate
		player_data.append((id, player_parent_send, player_child_recv, player_rate))
		player_ids.append(id)


	player_pools = []

	# create each player's two processes (Note: we give each recv process a random latency profile)
	for id, player_parent_send, player_child_recv, player_rate in player_data:
		player_pools.append([ 
			Process(target=player_send, args=(id, player_parent_send, player_rate), name="Player {} Send".format(id)),
			Process(target=player_recv, args=(id, player_child_recv, random.choice(profiles)), name="Player {} Receive".format(id))
		])
	
	# create unidirectional pipes between the classifcation process and send, recv processes
	# the classification process will use this to inform the send and recv processes of changes to their priortity groups in the format of a tuple (player_id, new_priority)
	# it will also eventually send a single shutdown signal to both processes after it has processed all data rows at the specified rate of time (e.g. if the trace match was 20 minutes, then the samples are processed evenly over that time)
	parent_classify_send, child_classify_send = Pipe()
	parent_classify_recv, child_classify_recv = Pipe()

	server_processes = [
		Process(target=server_classify,args=(player_ids, parent_classify_send, parent_classify_recv, player_rates), name="Server Classify"),
		Process(target=server_send,args=(server_send_conns, child_classify_send), name="Server Send"),
		Process(target=server_recv,args=(server_recv_conns, child_classify_recv), name="Server Receive")
	]

	# start threads for both server and players
	for p in server_processes:
		p.start()
	for process_pool in player_pools:
		for p in process_pool:
			p.start()

	stime = time.time()

	# wait for server to terminate once players have left
	for p in server_processes:
		print("Waiting for {} to finish".format(p.name))
		p.join()
		
	# wait for all players to finish
	c = 0
	for process_pool in player_pools:
		print("Waiting for Player {} to finish".format(c))
		c += 1
		for p in process_pool:
			p.join()
			
	print("Time taken: {}".format(time.time() - stime))
	print("Game Over")