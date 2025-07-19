package com.presensisiswainformatikabyfahmi

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import java.util.Locale
import java.util.concurrent.TimeUnit

class GenerateOTP_Activity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var realtimeDb: FirebaseDatabase

    private lateinit var etGenerateOtpCode: EditText
    private lateinit var etOtpExpiryMinutes: EditText
    private lateinit var btnGenerateOtp: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_generate_otp) // Make sure to create this layout XML

        auth = FirebaseAuth.getInstance()
        realtimeDb = FirebaseDatabase.getInstance()

        etGenerateOtpCode = findViewById(R.id.etGenerateOtpCode)
        etOtpExpiryMinutes = findViewById(R.id.etOtpExpiryMinutes)
        btnGenerateOtp = findViewById(R.id.btnGenerateOtp)

        btnGenerateOtp.setOnClickListener {
            generateAndSaveOtp()
        }

        // Optional: Check and clear expired OTP when this activity is opened/resumed
        checkAndClearExpiredOtpFromDb()
    }

    /**
     * Function to generate and save OTP to Realtime Database.
     */
    private fun generateAndSaveOtp() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "You must be logged in to generate an OTP.", Toast.LENGTH_SHORT).show()
            return
        }

        val otpCode = etGenerateOtpCode.text.toString().trim().uppercase(Locale.getDefault())
        if (otpCode.length != 6) { // OTP length validation
            Toast.makeText(this, "OTP code must be 6 characters.", Toast.LENGTH_SHORT).show()
            return
        }

        val expiryMinutesString = etOtpExpiryMinutes.text.toString().trim()
        if (expiryMinutesString.isEmpty()) {
            Toast.makeText(this, "Please enter OTP expiry duration (minutes).", Toast.LENGTH_SHORT).show()
            return
        }

        val expiryMinutes = expiryMinutesString.toLongOrNull()
        if (expiryMinutes == null || expiryMinutes <= 0) { // Duration validation
            Toast.makeText(this, "Invalid expiry duration.", Toast.LENGTH_SHORT).show()
            return
        }

        // Calculate expiry time in milliseconds from now
        val expiryTimestamp = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(expiryMinutes)

        val otpData = hashMapOf(
            "code" to otpCode,
            "expiry" to expiryTimestamp,
            "generatedBy" to currentUser.uid,
            "generatedAt" to ServerValue.TIMESTAMP // Use Firebase server timestamp
        )

        // Save OTP to Realtime Database under "current_otp" node
        realtimeDb.getReference("current_otp")
            .setValue(otpData)
            .addOnSuccessListener {
                Toast.makeText(
                    this,
                    "OTP '$otpCode' successfully created! Valid for $expiryMinutes minutes.",
                    Toast.LENGTH_LONG
                ).show()
                // Clear OTP input after successful generation
                etGenerateOtpCode.setText("")
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to create OTP: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * Optional: Function to check and clear expired OTP from Realtime Database
     * when the teacher's app is opened. This is not 100% automatic deletion as it relies on
     * the teacher opening the app. Cloud Functions are more recommended for automatic deletion.
     */
    private fun checkAndClearExpiredOtpFromDb() {
        realtimeDb.getReference("current_otp")
            .get()
            .addOnSuccessListener { dataSnapshot ->
                val expiryTime = dataSnapshot.child("expiry").getValue(Long::class.java)

                if (expiryTime != null) {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime >= expiryTime) {
                        // If expired, remove from Realtime Database
                        realtimeDb.getReference("current_otp").removeValue()
                            .addOnSuccessListener {
                                Toast.makeText(this, "Expired OTP automatically removed.", Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "Failed to remove expired OTP: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                }
            }
            .addOnFailureListener { e ->
                // This will happen if the current_otp node does not exist
                // Toast.makeText(this, "Could not check OTP: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}