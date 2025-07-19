package com.presensisiswainformatikabyfahmi

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton // Import ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.presensisiswainformatikabyfahmi.loginregister.LoginActivity

class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestoreDb: FirebaseFirestore

    private lateinit var tvWelcome: TextView
    private lateinit var btnLogout: Button
    // Change to ImageButton
    private lateinit var imgBtnProfile: ImageButton
    private lateinit var imgBtnPresensi: ImageButton
    private lateinit var imgBtnPrintAttendance: ImageButton
    private lateinit var imgBtnGenerateOtp: ImageButton // New ImageButton for OTP

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()
        firestoreDb = FirebaseFirestore.getInstance()

        tvWelcome = findViewById(R.id.tvWelcome)
        btnLogout = findViewById(R.id.btnLogout)
        // Initialize ImageButtons
        imgBtnProfile = findViewById(R.id.imgBtnProfile)
        imgBtnPresensi = findViewById(R.id.imgBtnPresensi)
        imgBtnPrintAttendance = findViewById(R.id.imgBtnPrintAttendance)
        imgBtnGenerateOtp = findViewById(R.id.imgBtnGenerateOtp) // Initialize new ImageButton

        // Ensure user is logged in
        val currentUser = auth.currentUser
        if (currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // Fetch user data from Firestore to display full name
        firestoreDb.collection("users").document(currentUser.uid)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val user = document.toObject(User::class.java)
                    user?.let {
                        tvWelcome.text = "Selamat datang, ${it.fullName}!"
                    }
                } else {
                    tvWelcome.text = "Selamat datang, Pengguna!" // Fallback if document doesn't exist
                }
            }
            .addOnFailureListener { e ->
                tvWelcome.text = "Selamat datang, Pengguna!" // Fallback
                Toast.makeText(this, "Failed to load user profile: ${e.message}", Toast.LENGTH_SHORT).show()
            }

        // --- Set up listeners for ImageButtons ---
        imgBtnProfile.setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            startActivity(intent)
        }

        imgBtnPresensi.setOnClickListener {
            val intent = Intent(this, PresensiActivity::class.java)
            startActivity(intent)
        }

        imgBtnPrintAttendance.setOnClickListener {
            val intent = Intent(this, AttendanceReportActivity::class.java)
            startActivity(intent)
        }

        imgBtnGenerateOtp.setOnClickListener {
            // Launch the new GenerateOTP_Activity
            val intent = Intent(this, GenerateOTP_Activity::class.java)
            startActivity(intent)
        }

        // --- Logout Button ---
        btnLogout.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }
}