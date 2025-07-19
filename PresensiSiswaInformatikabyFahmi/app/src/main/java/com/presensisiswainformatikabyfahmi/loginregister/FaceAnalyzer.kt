package com.presensisiswainformatikabyfahmi.loginregister

import android.util.Log
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

typealias FaceListener = (facesFound: Int) -> Unit

class FaceAnalyzer(private val listener: FaceListener) : ImageAnalysis.Analyzer {

    private val faceDetectorOptions = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .setMinFaceSize(0.6f)
        .enableTracking()
        .build()
    private val faceDetector = FaceDetection.getClient(faceDetectorOptions)

    @ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            faceDetector.process(image)
                .addOnSuccessListener { faces ->
                    listener(faces.size) // Beri tahu listener berapa banyak wajah yang terdeteksi
                }
                .addOnFailureListener { e ->
                    Log.e("FaceAnalyzer", "Deteksi wajah gagal: ${e.message}", e)
                    listener(0) // Beri tahu listener bahwa tidak ada wajah (atau ada error)
                }
                .addOnCompleteListener {
                    imageProxy.close() // Penting: tutup ImageProxy setelah selesai diproses
                }
        }
    }
}