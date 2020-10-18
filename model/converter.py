import tensorflow as tf

with open('aedes.json', 'r') as json_file:
    json_savedModel= json_file.read()
#load the model architecture 
model_j = tf.keras.models.model_from_json(json_savedModel)
model_j.summary()

model_j.load_weights('aedes.h5')

model_j.compile(loss='sparse_categorical_crossentropy',
         optimizer='rmsprop',
         metrics=['accuracy'])

converter = tf.lite.TFLiteConverter.from_keras_model(model_j)
tflite_model = converter.convert()
open("aedes_converted.tflite", "wb").write(tflite_model)