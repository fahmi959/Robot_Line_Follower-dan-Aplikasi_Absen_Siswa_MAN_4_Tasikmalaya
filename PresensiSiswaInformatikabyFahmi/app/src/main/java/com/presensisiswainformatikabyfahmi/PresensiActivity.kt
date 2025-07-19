// 4. Lokasi: com/presensisiswainformatikabyfahmi/PresensiActivity.kt
package com.presensisiswainformatikabyfahmi

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*

class PresensiActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestoreDb: FirebaseFirestore
    private lateinit var realtimeDb: FirebaseDatabase

    private lateinit var etOtpCode: EditText
    private lateinit var btnAbsen: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_presensi)

        auth = FirebaseAuth.getInstance()
        firestoreDb = FirebaseFirestore.getInstance()
        realtimeDb = FirebaseDatabase.getInstance()

        etOtpCode = findViewById(R.id.etOtpCode)
        btnAbsen = findViewById(R.id.btnAbsen)

        btnAbsen.setOnClickListener {
            verifyOtpAndRecordAttendance()
        }
    }

    private fun verifyOtpAndRecordAttendance() {
        val otpInput = etOtpCode.text.toString().trim().uppercase(Locale.getDefault())
        if (otpInput.isEmpty()) {
            Toast.makeText(this, "Masukkan kode OTP terlebih dahulu.", Toast.LENGTH_SHORT).show()
            return
        }

        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Anda harus login untuk absen.", Toast.LENGTH_SHORT).show()
            return
        }

        realtimeDb.getReference("current_otp")
            .get()
            .addOnSuccessListener { dataSnapshot ->
                val serverOtp = dataSnapshot.child("code").getValue(String::class.java)
                val expiryTime = dataSnapshot.child("expiry").getValue(Long::class.java)

                if (serverOtp != null && expiryTime != null) {
                    val currentTime = System.currentTimeMillis()

                    if (otpInput == serverOtp) {
                        if (currentTime < expiryTime) {
                            findActiveAttendanceSessionAndRecord(currentUser.uid)
                        } else {
                            Toast.makeText(this, "Kode OTP sudah kedaluwarsa. Silakan minta kode baru.", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(this, "Kode OTP salah. Periksa kembali atau minta kode baru.", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this, "Belum ada kode OTP aktif saat ini. Mohon tunggu kode dari pengelola.", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Gagal mengambil OTP dari server: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun findActiveAttendanceSessionAndRecord(userId: String) {
        firestoreDb.collection("attendance_sessions")
            .whereEqualTo("isActive", true)
            .limit(1)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    val activeSession = querySnapshot.documents[0].toObject(AttendanceSession::class.java)?.copy(id = querySnapshot.documents[0].id)
                    if (activeSession != null) {
                        if (System.currentTimeMillis() < activeSession.expiresAt) {
                            recordAttendance(userId, activeSession.id)
                        } else {
                            Toast.makeText(this, "Sesi absensi aktif sudah kedaluwarsa. Mohon tunggu sesi baru.", Toast.LENGTH_LONG).show()
                            firestoreDb.collection("attendance_sessions").document(activeSession.id).update("isActive", false)
                        }
                    } else {
                        Toast.makeText(this, "Data sesi aktif tidak valid.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Tidak ada sesi absensi aktif saat ini.", Toast.LENGTH_LONG).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Gagal mencari sesi absensi aktif: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // MODIFIKASI DIMULAI DI SINI
    private fun recordAttendance(userId: String, sessionId: String) {
        firestoreDb.collection("users").document(userId)
            .get()
            .addOnSuccessListener { userDoc ->
                if (userDoc.exists()) {
                    val user = userDoc.toObject(User::class.java)
                    user?.let { userData ->
                        val currentTimestamp = System.currentTimeMillis()
                        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                        val dateString = dateFormat.format(Date(currentTimestamp))
                        val timeString = timeFormat.format(Date(currentTimestamp))

                        val attendanceData = hashMapOf(
                            "userId" to userId,
                            "fullName" to userData.fullName,
                            "username" to (userData.username ?: "N/A"), // Ambil username, default "N/A" jika null
                            "email" to (userData.email ?: "N/A"),       // Ambil email, default "N/A" jika null
                            "timestamp" to currentTimestamp,
                            "date" to dateString,
                            "time" to timeString,
                            "status" to "Hadir"
                        )

                        firestoreDb.collection("attendance_sessions")
                            .document(sessionId)
                            .collection("absensi")
                            .document(userId)
                            .set(attendanceData)
                            .addOnSuccessListener {
                                Toast.makeText(this, "Absen berhasil dicatat!", Toast.LENGTH_LONG).show()
                                etOtpCode.setText("")
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "Gagal mencatat absen: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                } else {
                    Toast.makeText(this, "Data profil Anda tidak ditemukan.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Gagal mengambil data profil: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}

