package com.example.food_classification

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.label.TensorLabel
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.File
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.text.SimpleDateFormat
import java.util.*
import android.media.ExifInterface
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import com.opencsv.CSVReader
import java.io.FileReader
import java.io.InputStreamReader

class page2 : AppCompatActivity() {

    private val targetClasses = listOf("steak", "tacos", "pizza", "donuts", "tiramisu")
    private lateinit var interpreter: Interpreter
    private lateinit var selectedImageBitmap: Bitmap
    private var currentPhotoPath: String? = null // Modifier pour qu'il puisse être null

    // Liste des images à afficher successivement
    private val imageList = listOf("PIZZA.png", "STEAK.png", "TIRAMISU.png", "DONUTS.png", "TACOS.png")
    private var currentImageIndex = 0
    private var isImageSelected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_page2)

        val imageView = findViewById<ImageView>(R.id.imageView)
        val textView = findViewById<TextView>(R.id.textView)
        val predictButton = findViewById<Button>(R.id.predictButton)
        val chooseImageButton = findViewById<Button>(R.id.chooseImageButton)
        val captureImageButton = findViewById<Button>(R.id.captureImageButton)

        // Charger le modèle .tflite
        interpreter = Interpreter(loadModelFile("model3.tflite"))

        // Request permissions for camera and storage if not granted
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE), 1)
        }

        // Choisir une image à partir de la galerie
        chooseImageButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(intent, 100)
        }

        // Capturer une image avec la caméra
        captureImageButton.setOnClickListener {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            val photoFile = createImageFile()
            val photoURI = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                photoFile
            )
            intent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
            startActivityForResult(intent, 200)
        }

        // Prédiction à l'appui du bouton
        predictButton.setOnClickListener {
            if (::selectedImageBitmap.isInitialized) {
                val result = predict(selectedImageBitmap)
                textView.text = result
                textView.visibility = TextView.VISIBLE
            } else {
                textView.text = "Veuillez choisir ou capturer une image d'abord."
                textView.visibility = TextView.VISIBLE
            }
        }

        // Début de la séquence d'images
        startImageSequence(imageView)
    }

    // Fonction pour démarrer la séquence d'images toutes les 5 secondes
    private fun startImageSequence(imageView: ImageView) {
        val handler = android.os.Handler()
        val imageRunnable = object : Runnable {
            override fun run() {
                if (!isImageSelected) {
                    val imageName = imageList[currentImageIndex]
                    val assetManager = assets
                    val inputStream = assetManager.open(imageName)
                    val bitmap = BitmapFactory.decodeStream(inputStream)

                    imageView.setImageBitmap(bitmap)

                    // Passer à l'image suivante
                    currentImageIndex = (currentImageIndex + 1) % imageList.size

                    // Relancer après 5 secondes
                    handler.postDelayed(this, 5000)
                }
            }
        }
        handler.post(imageRunnable)
    }

    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = cacheDir
        val photoFile = File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir)
        currentPhotoPath = photoFile.absolutePath // Initialiser correctement le chemin de la photo
        return photoFile
    }

    private fun loadModelFile(modelFileName: String): MappedByteBuffer {
        val assetFileDescriptor = assets.openFd(modelFileName)
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun preprocessImage(bitmap: Bitmap): TensorImage {
        // Redimensionner l'image
        val resizedBitmap = resizeBitmap(bitmap, 224, 224)

        // Créer un TensorImage et charger l'image redimensionnée
        val tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(resizedBitmap)

        // Normalisation : diviser par 255.0 pour obtenir des valeurs entre 0 et 1
        val tensorBuffer = tensorImage.tensorBuffer
        val floatArray = tensorBuffer.floatArray.map { it / 255.0f }.toFloatArray()

        // Charger les données normalisées dans un TensorBuffer
        val normalizedBuffer = TensorBuffer.createFixedSize(intArrayOf(1, 224, 224, 3), DataType.FLOAT32)
        normalizedBuffer.loadArray(floatArray)

        // Recharger le TensorImage avec le TensorBuffer normalisé
        tensorImage.load(normalizedBuffer)

        return tensorImage
    }

    // Redimensionner l'image à 224x224
    private fun resizeBitmap(bitmap: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    private fun predict(bitmap: Bitmap): String {

        val tensorImage = preprocessImage(bitmap)
        val inputBuffer = tensorImage.buffer
        val outputBuffer = TensorBuffer.createFixedSize(intArrayOf(1, targetClasses.size), DataType.FLOAT32)
        interpreter.run(inputBuffer, outputBuffer.buffer.rewind())

        val tensorLabel = TensorLabel(targetClasses, outputBuffer)
        val warningFlag = "\u26A0\uFE0F"
        // Récupérer la classe avec la probabilité la plus élevée et sa probabilité
        val maxEntry = tensorLabel.mapWithFloatValue.maxByOrNull { it.value }
        val maxClass = maxEntry?.key ?: "$warningFlag Classe inconnue"
        val maxProbability = maxEntry?.value ?: 0f

        // Vérifier si la probabilité maximale est supérieure à 0.7
        if (maxProbability <= 0.75f) {
            return "$warningFlag Classe inconnue"
        }

        // Charger les plats depuis le fichier CSV
        val plats = lirePlatsDepuisCSV("plats1.csv")

        // Trouver le plat correspondant à la classe prédite
        val platPredicted = plats.find { it.nom.equals(maxClass, ignoreCase = true) }

        // Créer la sortie formatée
        //val italieflag = "\uD83C\uDDEE\uD83C\uDDF9"
        //val biscuits = "biscuits"

        return if (platPredicted != null) {
            val ingredients = platPredicted.ingredients.joinToString(", ")
            val result = """
            Classe prédite : ${platPredicted.nom}
            
            Année : ${platPredicted.annee}
            
            Pays : ${platPredicted.pays} 
            
            Ingrédients : $ingredients 
        """.trimIndent()

            result
        } else {
            "$warningFlag Classe inconnue"
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        interpreter.close() // Libérer les ressources de l'interpréteur
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Vérifier si currentPhotoPath est initialisée avant de l'enregistrer
        if (currentPhotoPath != null) {
            outState.putString("photoPath", currentPhotoPath)
        }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        // Récupérer le chemin de l'image restauré si disponible
        currentPhotoPath = savedInstanceState.getString("photoPath")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        val imageView = findViewById<ImageView>(R.id.imageView)

        when (requestCode) {
            100 -> { // Choisir une image
                if (resultCode == RESULT_OK && data != null) {
                    val selectedImageUri: Uri = data.data!!
                    selectedImageBitmap = MediaStore.Images.Media.getBitmap(contentResolver, selectedImageUri)
                    imageView.setImageBitmap(selectedImageBitmap)
                    isImageSelected = true // Arrêter la séquence d'images
                }
            }
            200 -> { // Capturer une image
                if (resultCode == RESULT_OK && currentPhotoPath != null) {
                    val file = File(currentPhotoPath!!)
                    var bitmap = BitmapFactory.decodeFile(file.absolutePath)

                    // Corriger l'orientation de l'image capturée
                    bitmap = rotateImageIfRequired(bitmap, currentPhotoPath!!)

                    selectedImageBitmap = bitmap
                    imageView.setImageBitmap(selectedImageBitmap)
                    isImageSelected = true // Arrêter la séquence d'images
                }
            }
        }
    }

    // Fonction pour faire la rotation de l'image en fonction des informations EXIF
    private fun rotateImageIfRequired(bitmap: Bitmap, imagePath: String): Bitmap {
        val exifInterface = ExifInterface(imagePath)
        val orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)

        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270f)
            else -> bitmap
        }
    }

    // Fonction pour appliquer une rotation à l'image
    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = android.graphics.Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    data class Plat(
        val nom: String,
        val annee: Int,
        val pays: String,
        val ingredients: List<String>
    )

    fun lirePlatsDepuisCSV(fichierCSV: String): List<Plat> {
        val plats = mutableListOf<Plat>()
        val assetManager = assets
        val inputStream = assetManager.open(fichierCSV)
        val csvReader = CSVReader(InputStreamReader(inputStream))

        // Lire l'entête
        csvReader.readNext()

        var line: Array<String>?
        // Lire chaque ligne du CSV
        while (csvReader.readNext().also { line = it } != null) {
            val nom = line?.get(0) ?: ""
            val annee = line?.get(1)?.toIntOrNull() ?: 0
            val pays = line?.get(2) ?: ""
            val ingredients = line?.get(3)?.split(",") ?: emptyList()

            plats.add(Plat(nom, annee, pays, ingredients))
        }
        return plats
    }
}
