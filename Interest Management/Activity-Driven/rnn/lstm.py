__author__ = 'gchevalier'

import os
import tensorflow as tf
from sklearn import metrics
from sklearn.utils import shuffle
import numpy as np
from rnn.config import Config

# hide all logs
os.environ['TF_CPP_MIN_LOG_LEVEL']='3'
tf.logging.set_verbosity(tf.logging.ERROR)

def batch_norm(input_tensor, config, i):
    # Implementing batch normalisation: this is used out of the residual layers
    # to normalise those output neurons by mean and standard deviation.

    if config.n_layers_in_highway == 0:
        # There is no residual layers, no need for batch_norm:
        return input_tensor

    with tf.variable_scope("batch_norm") as scope:
        if i != 0:
            # Do not create extra variables for each time step
            scope.reuse_variables()

        # Mean and variance normalisation simply crunched over all axes
        axes = list(range(len(input_tensor.get_shape())))

        mean, variance = tf.nn.moments(input_tensor, axes=axes, shift=None, name=None, keep_dims=False)
        stdev = tf.sqrt(variance+0.001)

        # Rescaling
        bn = input_tensor - mean
        bn /= stdev
        # Learnable extra rescaling

        # tf.get_variable("relu_fc_weights", initializer=tf.random_normal(mean=0.0, stddev=0.0)
        bn *= tf.get_variable("a_noreg", initializer=tf.random_normal([1], mean=0.5, stddev=0.0))
        bn += tf.get_variable("b_noreg", initializer=tf.random_normal([1], mean=0.0, stddev=0.0))
        # bn *= tf.Variable(0.5, name=(scope.name + "/a_noreg"))
        # bn += tf.Variable(0.0, name=(scope.name + "/b_noreg"))

    return bn

def relu_fc(input_2D_tensor_list, features_len, new_features_len, config):
    """make a relu fully-connected layer, mainly change the shape of tensor
       both input and output is a list of tensor
        argument:
            input_2D_tensor_list: list shape is [batch_size,feature_num]
            features_len: int the initial features length of input_2D_tensor
            new_feature_len: int the final features length of output_2D_tensor
            config: Config used for weights initializers
        return:
            output_2D_tensor_list lit shape is [batch_size,new_feature_len]
    """

    W = tf.get_variable(
        "relu_fc_weights",
        initializer=tf.random_normal(
            [features_len, new_features_len],
            mean=0.0,
            stddev=float(config.weights_stddev)
        )
    )
    b = tf.get_variable(
        "relu_fc_biases_noreg",
        initializer=tf.random_normal(
            [new_features_len],
            mean=float(config.bias_mean),
            stddev=float(config.weights_stddev)
        )
    )

    # intra-timestep multiplication:
    output_2D_tensor_list = [
        tf.nn.relu(tf.matmul(input_2D_tensor, W) + b)
            for input_2D_tensor in input_2D_tensor_list
    ]

    return output_2D_tensor_list


def single_LSTM_cell(input_hidden_tensor, n_outputs):
    """ define the basic LSTM layer
        argument:
            input_hidden_tensor: list a list of tensor,
                                 shape: time_steps*[batch_size,n_inputs]
            n_outputs: int num of LSTM layer output
        return:
            outputs: list a time_steps list of tensor,
                     shape: time_steps*[batch_size,n_outputs]
    """
    with tf.variable_scope("lstm_cell"):
        lstm_cell = tf.nn.rnn_cell.BasicLSTMCell(n_outputs, state_is_tuple=True, forget_bias=0.999)
        outputs, _ = tf.contrib.rnn.static_rnn(lstm_cell, input_hidden_tensor, dtype=tf.float32)
    return outputs


def bi_LSTM_cell(input_hidden_tensor, n_inputs, n_outputs, config):
    """build bi-LSTM, concatenating the two directions in an inner manner.
        argument:
            input_hidden_tensor: list a time_steps series of tensor, shape: [sample_num, n_inputs]
            n_inputs: int units of input tensor
            n_outputs: int units of output tensor, each bi-LSTM will have half those internal units
            config: Config used for the relu_fc
        return:
            layer_hidden_outputs: list a time_steps series of tensor, shape: [sample_num, n_outputs]
    """
    n_outputs = int(n_outputs/2)

    # print ("bidir:")

    with tf.variable_scope('pass_forward') as scope2:
        hidden_forward = relu_fc(input_hidden_tensor, n_inputs, n_outputs, config)
        forward = single_LSTM_cell(hidden_forward, n_outputs)

    # print (len(hidden_forward), str(hidden_forward[0].get_shape()))

    # Backward pass is as simple as surrounding the cell with a double inversion:
    with tf.variable_scope('pass_backward') as scope2:
        hidden_backward = relu_fc(input_hidden_tensor, n_inputs, n_outputs, config)
        backward = list(reversed(single_LSTM_cell(list(reversed(hidden_backward)), n_outputs)))

    with tf.variable_scope('bidir_concat') as scope:
        # Simply concatenating cells' outputs at each timesteps on the innermost
        # dimension, like if the two cells acted as one cell
        # with twice the n_hidden size:
        layer_hidden_outputs = [
            tf.concat([f, b], len(f.get_shape()) - 1)
                for f, b in zip(forward, backward)]

    return layer_hidden_outputs


def residual_bidirectional_LSTM_layers(input_hidden_tensor, n_input, n_output, layer_level, config, keep_prob_for_dropout):
    """This architecture is only enabled if "config.n_layers_in_highway" has a
    value only greater than int(0). The arguments are same than for bi_LSTM_cell.
    arguments:
        input_hidden_tensor: list a time_steps series of tensor, shape: [sample_num, n_inputs]
        n_inputs: int units of input tensor
        n_outputs: int units of output tensor, each bi-LSTM will have half those internal units
        config: Config used for determining if there are residual connections and if yes, their number and with some batch_norm.
    return:
        layer_hidden_outputs: list a time_steps series of tensor, shape: [sample_num, n_outputs]
    """
    with tf.variable_scope('layer_{}'.format(layer_level)) as scope:

        if config.use_bidirectionnal_cells:
            get_lstm = lambda input_tensor: bi_LSTM_cell(input_tensor, n_input, n_output, config)
        else:
            get_lstm = lambda input_tensor: single_LSTM_cell(relu_fc(input_tensor, n_input, n_output, config), n_output)
        def add_highway_redisual(layer, residual_minilayer):
            return [a + b for a, b in zip(layer, residual_minilayer)]

        hidden_LSTM_layer = get_lstm(input_hidden_tensor)
        # Adding K new (residual bidir) connections to this first layer:
        for i in range(config.n_layers_in_highway - 1):
            with tf.variable_scope('LSTM_residual_{}'.format(i)) as scope2:
                hidden_LSTM_layer = add_highway_redisual(
                    hidden_LSTM_layer,
                    get_lstm(input_hidden_tensor)
                )

        if config.also_add_dropout_between_stacked_cells:
            hidden_LSTM_layer = [tf.nn.dropout(out, keep_prob_for_dropout) for out in hidden_LSTM_layer]

        return [batch_norm(out, config, i) for i, out in enumerate(hidden_LSTM_layer)]


def LSTM_network(feature_mat, config, keep_prob_for_dropout):
    """model a LSTM Network,
      it stacks 2 LSTM layers, each layer has n_hidden=32 cells
       and 1 output layer, it is a full connet layer
      argument:
        feature_mat: ndarray fature matrix, shape=[batch_size,time_steps,n_inputs]
        config: class containing config of network
      return:
              : ndarray  output shape [batch_size, n_classes]
    """

    with tf.variable_scope('LSTM_network') as scope:  # TensorFlow graph naming

        feature_mat = tf.nn.dropout(feature_mat, keep_prob_for_dropout)

        # Exchange dim 1 and dim 0
        feature_mat = tf.transpose(feature_mat, [1, 0, 2])
        # print ( feature_mat.get_shape())
        # New feature_mat's shape: [time_steps, batch_size, n_inputs]

        # Temporarily crush the feature_mat's dimensions
        feature_mat = tf.reshape(feature_mat, [-1, config.n_inputs])
        # print ( feature_mat.get_shape())
        # New feature_mat's shape: [time_steps*batch_size, n_inputs]

        # Split the series because the rnn cell needs time_steps features, each of shape:
        hidden = tf.split(feature_mat, config.n_steps, 0)
        # print (len(hidden), str(hidden[0].get_shape()))
        # New shape: a list of lenght "time_step" containing tensors of shape [batch_size, n_hidden]

        # Stacking LSTM cells, at least one is stacked:
        # print ( "\nCreating hidden #1:")
        hidden = residual_bidirectional_LSTM_layers(hidden, config.n_inputs, config.n_hidden, 1, config, keep_prob_for_dropout)
        # print (len(hidden), str(hidden[0].get_shape()))

        for stacked_hidden_index in range(config.n_stacked_layers - 1):
            # If the config permits it, we stack more lstm cells:
            # print ( "\nCreating hidden #{}:".format(stacked_hidden_index+2))
            hidden = residual_bidirectional_LSTM_layers(hidden, config.n_hidden, config.n_hidden, stacked_hidden_index+2, config, keep_prob_for_dropout)
            # print (len(hidden), str(hidden[0].get_shape()))

        # print ( "" )

        # Final fully-connected activation logits
        # Get the last output tensor of the inner loop output series, of shape [batch_size, n_classes]
        last_hidden = tf.nn.dropout(hidden[-1], keep_prob_for_dropout)
        last_logits = relu_fc(
            [last_hidden],
            config.n_hidden, config.n_classes, config
        )[0]
        return last_logits

def init_lstm(X_samples):
    tf.reset_default_graph()  # To enable to run multiple things in a loop

    #-----------------------------------
    # Define parameters for model
    #-----------------------------------
    config = Config(len(X_samples[0][0]))

    #------------------------------------------------------
    # Let's get serious and build the neural network
    #------------------------------------------------------
    with tf.device("/cpu:0"):  # Remove this line to use GPU. If you have a too small GPU, it crashes.
        X = tf.placeholder(tf.float32, [None, config.n_steps, config.n_inputs], name="X")

        # is_train for dropout control:
        is_train = tf.placeholder(tf.bool, name="is_train")
        keep_prob_for_dropout = tf.cond(is_train,
            lambda: tf.constant(
                config.keep_prob_for_dropout,
                name="keep_prob_for_dropout"
            ),
            lambda: tf.constant(
                1.0,
                name="keep_prob_for_dropout"
            )
        )

        lstm = LSTM_network(X, config, keep_prob_for_dropout)

        # Softmax loss with L2 and L1 layer-wise regularisation
        l2 = config.lambda_loss_amount * sum(
            tf.nn.l2_loss(tf_var)
                for tf_var in tf.trainable_variables()
                if not ("noreg" in tf_var.name or "Bias" in tf_var.name)
        )

    sessconfig = tf.ConfigProto(log_device_placement=False)
    session = tf.InteractiveSession(config=sessconfig)
    #writer = tf.summary.FileWriter("lstm_graph", session.graph)
    #writer.close()
    tf.initialize_all_variables().run()
    saver = tf.train.Saver()
    saver.restore(session, "C:\\Users\\james\\Documents\\GitHub\\ADIM\\Interest Management\\Activity-Driven\\rnn\\trained_checkpoint\\trained_model")  
    print("LSTM initialized") 
    return [lstm, session, X, is_train]
        
def classify(nn_vars, sample):
    pred_out = nn_vars[1].run(
        [nn_vars[0]],
        feed_dict={
            nn_vars[2]: [sample],
            nn_vars[3]: False
        }
    )
    return np.argmax(pred_out)
