package com.presensisiswainformatikabyfahmi.loginregister

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.presensisiswainformatikabyfahmi.R
import java.util.concurrent.Executor

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var etUsernameLogin: EditText
    private lateinit var etPasswordLogin: EditText
    private lateinit var btnLogin: Button
    private lateinit var tvGoToRegister: TextView
    private lateinit var btnLoginWithFace: Button

    private lateinit var executor: Executor
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        etUsernameLogin = findViewById(R.id.etUsernameLogin)
        etPasswordLogin = findViewById(R.id.etPasswordLogin)
        btnLogin = findViewById(R.id.btnLogin)
        tvGoToRegister = findViewById(R.id.tvGoToRegister)
        btnLoginWithFace = findViewById(R.id.btnLoginWithFace)

        executor = ContextCompat.getMainExecutor(this)

        setupBiometricPrompt()

        btnLogin.setOnClickListener {
            loginUser()
        }

        tvGoToRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
            finish()
        }


        btnLoginWithFace.setOnClickListener {

            Toast.makeText(this, "Login wajah terintegrasi setelah login dengan username & password", Toast.LENGTH_LONG).show()

        }
    }

    private fun loginUser() {
        val username = etUsernameLogin.text.toString().trim()
        val password = etPasswordLogin.text.toString().trim()

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Harap isi username dan password.", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("users")
            .whereEqualTo("username", username)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    val email = querySnapshot.documents[0].getString("email")
                    val storedPassword = querySnapshot.documents[0].getString("password") // Retrieve stored password

                    if (storedPassword == password) { // Compare with plain text password
                        email?.let { userEmail ->
                            // Sign in to Firebase Auth using the email and password
                            // This is redundant if you're only verifying against Firestore stored password.
                            // Firebase Auth's signInWithEmailAndPassword handles its own password hashing.
                            // If you want to use Firebase Auth for authentication, you should rely on its password checks,
                            // not store plain text passwords in Firestore and compare them.
                            // For security, never store passwords in plaintext.
                            // The current setup allows two ways to "authenticate": Firebase Auth and Firestore plaintext comparison.
                            // This needs to be clarified. For this response, I'll assume you want to keep
                            // Firebase Auth's signInWithEmailAndPassword for actual authentication and use the stored
                            // password in Firestore for initial validation before Firebase Auth if needed (though not recommended).
                            // The safest way is to solely rely on Firebase Auth's password management.

                            auth.signInWithEmailAndPassword(userEmail, password)
                                .addOnCompleteListener(this) { task ->
                                    if (task.isSuccessful) {
                                        Toast.makeText(this, "Login berhasil, memverifikasi biometrik sistem...", Toast.LENGTH_SHORT).show()
                                        checkBiometricSupportAndAuthenticate()
                                    } else {
                                        Toast.makeText(this, "Login gagal: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                        } ?: Toast.makeText(this, "Email tidak ditemukan untuk username ini.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Password salah.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Username tidak ditemukan.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Gagal mencari username: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun checkBiometricSupportAndAuthenticate() {
        val biometricManager = BiometricManager.from(this)
        when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                Log.d("LoginActivity", "Perangkat mendukung biometrik sistem.")
                biometricPrompt.authenticate(promptInfo)
            }
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                Toast.makeText(this, "Perangkat tidak memiliki hardware biometrik. Lanjut ke verifikasi wajah kustom.", Toast.LENGTH_LONG).show()
                // Jika tidak ada hardware biometrik sistem, langsung ke verifikasi wajah kustom
                startActivity(Intent(this, FaceRecognitionLoginActivity::class.java))
                finish()
            }
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                Toast.makeText(this, "Hardware biometrik sistem tidak tersedia atau sibuk. Lanjut ke verifikasi wajah kustom.", Toast.LENGTH_LONG).show()
                startActivity(Intent(this, FaceRecognitionLoginActivity::class.java))
                finish()
            }
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                Toast.makeText(this, "Tidak ada sidik jari atau wajah terdaftar di perangkat. Lanjut ke verifikasi wajah kustom.", Toast.LENGTH_LONG).show()
                startActivity(Intent(this, FaceRecognitionLoginActivity::class.java))
                finish()
            }
            else -> {
                Toast.makeText(this, "Terjadi kesalahan tidak diketahui pada biometrik sistem. Lanjut ke verifikasi wajah kustom.", Toast.LENGTH_LONG).show()
                startActivity(Intent(this, FaceRecognitionLoginActivity::class.java))
                finish()
            }
        }
    }

    private fun setupBiometricPrompt() {
        biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Toast.makeText(applicationContext, "Otentikasi biometrik sistem gagal: $errString", Toast.LENGTH_SHORT).show()
                    auth.signOut() // Logout pengguna jika biometrik sistem gagal
                    // Tetap di LoginActivity
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    Toast.makeText(applicationContext, "Otentikasi biometrik sistem berhasil! Memulai verifikasi wajah kustom...", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(applicationContext, FaceRecognitionLoginActivity::class.java))
                    finish() // Tutup LoginActivity
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Toast.makeText(applicationContext, "Otentikasi biometrik sistem tidak dikenali.", Toast.LENGTH_SHORT).show()
                    auth.signOut() // Logout pengguna
                    // Tetap di LoginActivity
                }
            })

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Verifikasi Identitas Anda")
            .setSubtitle("Gunakan sidik jari atau wajah perangkat untuk melanjutkan")
            .setNegativeButtonText("Batalkan")
            .build()
    }
}