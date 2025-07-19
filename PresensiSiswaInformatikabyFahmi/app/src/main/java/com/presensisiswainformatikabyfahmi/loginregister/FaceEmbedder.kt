package com.presensisiswainformatikabyfahmi.loginregister

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.sqrt

class FaceEmbedder {

    private var tflite: Interpreter? = null
    // Pastikan nama file model sesuai dengan yang Anda unduh
    private val MODEL_PATH = "mobile_face_net.tflite"
    // Ukuran input yang diharapkan oleh MobileFaceNet
    private val INPUT_IMAGE_SIZE = 112 // <--- UBAH DARI 160 MENJADI 112
    // Ukuran output embedding dari MobileFaceNet
    private val OUTPUT_EMBEDDING_SIZE = 128

    fun initialize(assets: AssetManager) {
        try {
            val modelFile = loadModelFile(assets, MODEL_PATH)
            val options = Interpreter.Options()
            options.setNumThreads(4)

            // Opsional: Coba gunakan GPU delegate jika tersedia untuk performa lebih baik
            // try {
            //     val gpuDelegate = GpuDelegate()
            //     options.addDelegate(gpuDelegate)
            //     Log.d("FaceEmbedder", "GPU Delegate ditambahkan.")
            // } catch (e: Exception) {
            //     Log.w("FaceEmbedder", "Gagal menambahkan GPU delegate, menggunakan CPU: ${e.message}")
            // }

            tflite = Interpreter(modelFile, options)
            Log.d("FaceEmbedder", "Model TFLite berhasil dimuat: $MODEL_PATH")
        } catch (e: Exception) {
            Log.e("FaceEmbedder", "Gagal memuat model TFLite: ${e.message}", e)
            tflite = null
        }
    }
    fun close() {
        tflite?.close()
        tflite = null
        Log.d("FaceEmbedder", "Interpreter TFLite ditutup.")
    }

    private fun loadModelFile(assets: AssetManager, modelPath: String): MappedByteBuffer {
        val fileDescriptor = assets.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun getEmbedding(croppedFaceBitmap: Bitmap): List<Float>? {
        if (tflite == null) {
            Log.e("FaceEmbedder", "Interpreter TFLite belum diinisialisasi atau gagal dimuat.")
            return null
        }

        // Resize bitmap ke ukuran input model (112x112)
        val resizedBitmap = Bitmap.createScaledBitmap(croppedFaceBitmap, INPUT_IMAGE_SIZE, INPUT_IMAGE_SIZE, true)

        val inputBuffer = ByteBuffer.allocateDirect(1 * INPUT_IMAGE_SIZE * INPUT_IMAGE_SIZE * 3 * 4)
        inputBuffer.order(ByteOrder.nativeOrder())

        val intValues = IntArray(INPUT_IMAGE_SIZE * INPUT_IMAGE_SIZE)
        resizedBitmap.getPixels(intValues, 0, resizedBitmap.width, 0, 0, resizedBitmap.width, resizedBitmap.height)

        // Preprocessing piksel: normalisasi ke rentang [-1, 1]
        for (i in intValues.indices) {
            val pixel = intValues[i]
            inputBuffer.putFloat((((pixel shr 16 and 0xFF) - 127.5f) / 127.5f))
            inputBuffer.putFloat((((pixel shr 8 and 0xFF) - 127.5f) / 127.5f))
            inputBuffer.putFloat((((pixel and 0xFF) - 127.5f) / 127.5f))
        }
        inputBuffer.rewind()

        val outputBuffer = ByteBuffer.allocateDirect(1 * OUTPUT_EMBEDDING_SIZE * 4)
        outputBuffer.order(ByteOrder.nativeOrder())

        tflite?.run(inputBuffer, outputBuffer)

        outputBuffer.rewind()
        val embeddings = FloatArray(OUTPUT_EMBEDDING_SIZE)
        outputBuffer.asFloatBuffer().get(embeddings)

        return embeddings.toList()
    }

    fun calculateEuclideanDistance(embedding1: List<Float>, embedding2: List<Float>): Float {
        if (embedding1.size != embedding2.size) {
            throw IllegalArgumentException("Embeddings must have the same dimension")
        }
        var sumOfSquares = 0.0f
        for (i in embedding1.indices) {
            sumOfSquares += (embedding1[i] - embedding2[i]) * (embedding1[i] - embedding2[i])
        }
        return sqrt(sumOfSquares)
    }
}