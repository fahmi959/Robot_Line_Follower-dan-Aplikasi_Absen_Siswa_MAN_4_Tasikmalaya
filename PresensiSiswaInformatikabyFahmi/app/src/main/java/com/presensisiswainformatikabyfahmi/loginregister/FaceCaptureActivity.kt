package com.presensisiswainformatikabyfahmi.loginregister

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.presensisiswainformatikabyfahmi.R
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class FaceCaptureActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var tvFaceFeedback: TextView

    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var faceEmbedder: FaceEmbedder // Inisialisasi FaceEmbedder

    private val faceDetectorOptions = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .setMinFaceSize(0.6f) // Deteksi wajah yang cukup besar
        .enableTracking()
        .build()
    private val faceDetector = FaceDetection.getClient(faceDetectorOptions)

    private var isFaceCapturedAndSaved = false // Flag untuk mencegah multiple save

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_face_capture) // Pastikan Anda punya layout ini

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        previewView = findViewById(R.id.previewViewCapture) // ID di layout Anda
        tvFaceFeedback = findViewById(R.id.tvFaceFeedbackCapture) // ID di layout Anda

        cameraExecutor = Executors.newSingleThreadExecutor()

        faceEmbedder = FaceEmbedder() // Inisialisasi FaceEmbedder
        faceEmbedder.initialize(assets) // Memuat model FaceNet

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Izin kamera tidak diberikan oleh pengguna.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setTargetRotation(windowManager.defaultDisplay.rotation)
                .build()

            // ImageAnalyzer untuk deteksi wajah real-time
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, FaceAnalyzer { facesFound ->
                        runOnUiThread {
                            if (isFaceCapturedAndSaved) {
                                tvFaceFeedback.text = "Wajah sudah didaftarkan."
                                return@runOnUiThread
                            }

                            if (facesFound > 0) {
                                tvFaceFeedback.text = "Wajah terdeteksi! Mengambil gambar..."
                                takePhotoForRegistrationAutomatically()
                            } else {
                                tvFaceFeedback.text = "Tidak ada wajah terdeteksi. Posisikan wajah Anda di bingkai."
                            }
                        }
                    })
                }

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA // Gunakan kamera depan

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, imageAnalyzer
                )
            } catch (exc: Exception) {
                Log.e(TAG, "Gagal mengikat use cases kamera", exc)
                Toast.makeText(this, "Gagal membuka kamera: ${exc.message}", Toast.LENGTH_LONG).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhotoForRegistrationAutomatically() {
        if (isFaceCapturedAndSaved) return // Jangan ambil foto jika sudah tersimpan

        val imageCapture = imageCapture ?: run {
            Log.e(TAG, "ImageCapture is null when trying to take photo.")
            Toast.makeText(this, "Kesalahan kamera. Coba lagi.", Toast.LENGTH_SHORT).show()
            return
        }

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap = imageProxyToBitmap(image)
                    image.close() // Penting: tutup ImageProxy

                    if (bitmap != null) {
                        processFaceForRegistration(bitmap)
                    } else {
                        Toast.makeText(this@FaceCaptureActivity, "Gagal mengonversi gambar.", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Pengambilan foto otomatis gagal: ${exc.message}", exc)
                    Toast.makeText(this@FaceCaptureActivity, "Pengambilan foto otomatis gagal: ${exc.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // Fungsi konversi ImageProxy ke Bitmap (dari kode Anda sebelumnya)
    private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
        if (image.format == ImageFormat.JPEG) {
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

            val matrix = Matrix()
            matrix.postRotate(image.imageInfo.rotationDegrees.toFloat())
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else if (image.planes.size >= 3 && image.format == ImageFormat.YUV_420_888) {
            val yBuffer = image.planes[0].buffer
            val uBuffer = image.planes[1].buffer
            val vBuffer = image.planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            val yuvImage = android.graphics.YuvImage(nv21, android.graphics.ImageFormat.NV21, image.width, image.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(android.graphics.Rect(0, 0, yuvImage.width, yuvImage.height), 90, out)
            val imageBytes = out.toByteArray()
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

            val matrix = Matrix()
            matrix.postRotate(image.imageInfo.rotationDegrees.toFloat())
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            Log.e(TAG, "Unsupported image format or insufficient planes: ${image.format}")
            return null
        }
    }


    private fun processFaceForRegistration(bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)

        faceDetector.process(image)
            .addOnSuccessListener { faces ->
                if (faces.isNotEmpty()) {
                    val firstFace = faces[0] // Asumsikan kita hanya peduli dengan wajah pertama
                    val boundingBox = firstFace.boundingBox

                    // Pastikan boundingBox tidak keluar dari batas bitmap
                    val croppedRect = Rect(
                        boundingBox.left.coerceAtLeast(0),
                        boundingBox.top.coerceAtLeast(0),
                        boundingBox.right.coerceAtMost(bitmap.width),
                        boundingBox.bottom.coerceAtMost(bitmap.height)
                    )

                    // Jika area crop valid
                    if (croppedRect.width() > 0 && croppedRect.height() > 0) {
                        val croppedFaceBitmap = Bitmap.createBitmap(bitmap,
                            croppedRect.left,
                            croppedRect.top,
                            croppedRect.width(),
                            croppedRect.height()
                        )

                        val faceEmbedding = faceEmbedder.getEmbedding(croppedFaceBitmap) // Dapatkan embedding asli
                        if (faceEmbedding != null) {
                            val userId = auth.currentUser?.uid
                            userId?.let { uid ->
                                db.collection("users").document(uid)
                                    .update("faceEmbedding", faceEmbedding) // Simpan embedding yang sebenarnya
                                    .addOnSuccessListener {
                                        Toast.makeText(this, "Wajah berhasil didaftarkan!", Toast.LENGTH_LONG).show()
                                        isFaceCapturedAndSaved = true // Set flag agar tidak mengambil lagi
                                        // Arahkan ke LoginActivity setelah wajah berhasil didaftarkan
                                        startActivity(Intent(this, LoginActivity::class.java))
                                        finish()
                                    }
                                    .addOnFailureListener { e ->
                                        Toast.makeText(this, "Gagal menyimpan data wajah: ${e.message}", Toast.LENGTH_SHORT).show()
                                        Log.e(TAG, "Error saving face embedding", e)
                                        // Opsional: izinkan coba lagi jika penyimpanan gagal
                                        isFaceCapturedAndSaved = false
                                    }
                            } ?: run {
                                Toast.makeText(this, "Pengguna tidak terautentikasi.", Toast.LENGTH_SHORT).show()
                                isFaceCapturedAndSaved = false
                                auth.signOut() // Logout jika tidak ada pengguna aktif
                                startActivity(Intent(this, LoginActivity::class.java))
                                finish()
                            }
                        } else {
                            Toast.makeText(this, "Gagal mendapatkan embedding wajah dari model.", Toast.LENGTH_SHORT).show()
                            isFaceCapturedAndSaved = false // Izinkan coba lagi
                        }
                    } else {
                        Toast.makeText(this, "Wajah terdeteksi terlalu kecil atau di luar batas.", Toast.LENGTH_SHORT).show()
                        isFaceCapturedAndSaved = false // Izinkan coba lagi
                    }
                } else {
                    Toast.makeText(this, "Tidak ada wajah terdeteksi pada gambar. Posisikan wajah Anda dengan jelas.", Toast.LENGTH_SHORT).show()
                    isFaceCapturedAndSaved = false // Izinkan coba lagi
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Deteksi wajah gagal: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e(TAG, "ML Kit Face Detection failed", e)
                isFaceCapturedAndSaved = false // Izinkan coba lagi
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        faceDetector.close()
        faceEmbedder.close() // Tutup FaceEmbedder
    }

    companion object {
        private const val TAG = "FaceCaptureActivity"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}