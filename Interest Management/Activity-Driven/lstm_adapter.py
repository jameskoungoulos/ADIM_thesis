from rnn.config import Config
from rnn.load_data import load_X, load_Y
from rnn.lstm import init_lstm, classify
import numpy as np

class LSTM():

	def __init__(self, player_ids):
		self.player_ids = player_ids
		self.X_samples = load_X(player_ids)
		self.Y_samples = load_Y(player_ids)
		self.nn_vars = init_lstm(self.X_samples)
		self.idxs = [0 for i in range(len(player_ids))]
		self.correct = [0 for i in range(len(player_ids))]
		self.labelmap = {0:"Low", 1:"Mid", 2:"High"}

	def max_rows(self):
		mx = 0
		for player_samples in self.Y_samples:
			if len(player_samples) > mx:
				mx = player_samples.size
		return mx

	def classify(self, player_id):
		# if we haven't exhausted this player's samples
		if self.idxs[player_id] < len(self.X_samples[player_id]):
			prediction = classify(self.nn_vars, self.X_samples[player_id][self.idxs[player_id]])
			#print("Correct: {} Actual: {}".format(self.labelmap[prediction], self.labelmap[np.argmax(self.Y_samples[player_id][self.idxs[player_id]])]))
			if self.labelmap[prediction] == self.labelmap[np.argmax(self.Y_samples[player_id][self.idxs[player_id]])]:
				self.correct[player_id] += 1
			self.idxs[player_id] += 1
			return self.labelmap[prediction]
		else:
			return "Finished"

	# close the tf session
	def close(self):
		self.nn_vars[1].close()

	def show_accuracy(self):
		for player_id in self.player_ids:
			print("Accuracy for Player "+str(player_id)+": " + str(self.correct[player_id]/len(self.X_samples[player_id])))