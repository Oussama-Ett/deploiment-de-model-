package com.example.food_classification

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.label.TensorLabel
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import android.util.Log

class page2 : AppCompatActivity() {

    private val targetClasses = listOf("steak", "tacos", "pizza")
    private lateinit var interpreter: Interpreter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_page2)

        val imageView = findViewById<ImageView>(R.id.imageView)
        val textView = findViewById<TextView>(R.id.textView)
        val predictButton = findViewById<Button>(R.id.predictButton)

        // Charger une image d'exemple
        val bitmap = BitmapFactory.decodeResource(resources, R.drawable.steak)
        //val bitmap = BitmapFactory.decodeFile("/path/to/your/image/sample_image.jpg")
        imageView.setImageBitmap(bitmap)

        // Charger le modèle .tflite
        interpreter = Interpreter(loadModelFile("model.tflite"))

        // Prédiction à l'appui du bouton
        predictButton.setOnClickListener {
            val result = predict(bitmap)
            textView.text = "Classe prédite : $result"
        }
    }

    // Charger le fichier modèle .tflite
    private fun loadModelFile(modelFileName: String): MappedByteBuffer {
        val assetFileDescriptor = assets.openFd(modelFileName)
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun resizeBitmap(bitmap: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    fun normalizeBitmap(bitmap: Bitmap): TensorImage {
        // Redimensionner l'image
        val resizedBitmap = resizeBitmap(bitmap, 224, 224)
        Log.d("ImageSize", "Width: ${resizedBitmap.width}, Height: ${resizedBitmap.height}")

        // Créer un TensorImage et charger le bitmap redimensionné
        val tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(resizedBitmap)

        // Normalisation (diviser par 255.0 pour obtenir des valeurs entre 0 et 1)
        val tensorBuffer = tensorImage.tensorBuffer
        val floatArray = tensorBuffer.floatArray.map { it / 255.0f }.toFloatArray()

        // Afficher les deux premiers pixels après normalisation
        if (floatArray.size >= 2) {
            Log.d("NormalizedPixels", "Pixel 1: ${floatArray[0]}, Pixel 2: ${floatArray[1]} hh")
            println("Normalized Pixels: Pixel 1: ${floatArray[0]}, Pixel 2: ${floatArray[1]} hh")
        } else {
            Log.e("Error", "Array does not have enough pixels")
        }

        // Charger les données normalisées dans un TensorBuffer
        val normalizedBuffer = TensorBuffer.createFixedSize(intArrayOf(1, 224, 224, 3), DataType.FLOAT32)
        normalizedBuffer.loadArray(floatArray)

        // Recharger le TensorImage avec le TensorBuffer normalisé
        tensorImage.load(normalizedBuffer)

        return tensorImage
    }



    fun preprocessImage(bitmap: Bitmap): TensorImage {
        // Normaliser et retourner l'image sous forme de TensorImage
        return normalizeBitmap(bitmap)
    }

    private fun predict(bitmap: Bitmap): String {
        // Traiter l'image avec prétraitement
        val tensorImage = preprocessImage(bitmap)

        // Préparer le buffer d'entrée pour TensorFlow Lite
        val inputBuffer = tensorImage.buffer
        val outputBuffer = TensorBuffer.createFixedSize(intArrayOf(1, targetClasses.size), DataType.FLOAT32)

        // Exécuter le modèle
        interpreter.run(inputBuffer, outputBuffer.buffer.rewind())

        // Extraire les résultats
        val tensorLabel = TensorLabel(targetClasses, outputBuffer)
        val results = tensorLabel.mapWithFloatValue

        // Récupérer la classe la plus probable
        return results.maxByOrNull { it.value }?.key ?: "Classe inconnue"
    }

    override fun onDestroy() {
        super.onDestroy()
        interpreter.close() // Libérer les ressources
    }
}
