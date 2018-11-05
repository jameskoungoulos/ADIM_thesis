from config import Config
from load_data import load_X, load_Y
from lstm import init_lstm, predict

import numpy as np

X_test = load_X()
Y_test = load_Y()

config = Config(X_test)
nn_vars = init_lstm(Config, X_test)

correct = 0
#predmap = {0:"Travelling", 1:"Looting", 2:"ShortCombat", 3:"CityCombat", 4:"LongCombat", 5:"Hiding"}
predmap = {0:"Low", 1:"Low", 2:"High", 3:"Mid", 4:"Mid", 5:"Low"}
#predmap = {0:"Low", 1:"Mid", 2:"High"}
for i in range(0, len(X_test)):
	prediction = predict(nn_vars, X_test[i])
	#print("Predicted: {} Actual: {}".format(predmap[prediction], predmap[np.argmax(Y_test[idx])]))
	if predmap[prediction] == predmap[np.argmax(Y_test[i])]:
		# if prediction == 2:
		# 	print("correct High!")
		correct += 1

# close the tf session
nn_vars[1].close()

print("Test Accuracy: " + str(correct/len(X_test)))