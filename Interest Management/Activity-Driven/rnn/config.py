#--------------------------------------------
# Neural net's config.
#--------------------------------------------

class Config(object):
    """
    define a class to store parameters,
    the input should be feature mat of training and testing
    """

    def __init__(self, n_steps):
        # Data shaping
        self.n_steps = n_steps # each sample set has the same windowing applied
        self.n_classes = 3 # Final output classes

        # Training
        self.learning_rate = 0.001
        self.lambda_loss_amount = 0.005
        self.clip_gradients = 15.0
        self.gradient_noise_scale = None
        # Dropout is added on inputs and after each stacked layers (but not
        # between residual layers).
        self.keep_prob_for_dropout = 1.0 # since we are no longer training

        # Linear+relu structure
        self.bias_mean = 3.0
        # I would recommend between 0.1 and 1.0 or to change and use a xavier
        # initializer
        self.weights_stddev = 3.0

        ########
        # NOTE: I think that if any of the below parameters are changed,
        # the best is to readjust every parameters in the "Training" section
        # above to properly compare the architectures only once optimised.
        ########

        # LSTM structure
        self.n_inputs = 15
        self.n_hidden = 32 # nb of neurons inside the neural network
        # Use bidir in every LSTM cell, or not:
        self.use_bidirectionnal_cells = True

        # High-level deep architecture
        self.also_add_dropout_between_stacked_cells = True  # True
        # NOTE: values of exactly 1 (int) for those 2 high-level parameters below totally disables them and result in only 1 starting LSTM.
        self.n_layers_in_highway = 3 # Number of residual connections to the LSTMs (highway-style), this is did for each stacked block (inside them).
        self.n_stacked_layers = 3 # Stack multiple blocks of residual layers.