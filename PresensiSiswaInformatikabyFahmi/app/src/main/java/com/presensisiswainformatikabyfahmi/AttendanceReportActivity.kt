// Lokasi: com/presensisiswainformatikabyfahmi/AttendanceReportActivity.kt
package com.presensisiswainformatikabyfahmi

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.WriteBatch
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import androidx.appcompat.app.AlertDialog

class AttendanceReportActivity : AppCompatActivity() {

    private lateinit var firestoreDb: FirebaseFirestore
    private lateinit var rvAttendanceSessions: RecyclerView
    private lateinit var btnCreateNewSession: Button
    private lateinit var sessionAdapter: AttendanceSessionAdapter
    private val attendanceSessions = mutableListOf<AttendanceSession>()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "Izin penyimpanan diberikan. Anda sekarang bisa menyimpan laporan.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Izin penyimpanan ditolak. Tidak dapat menyimpan laporan CSV.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_attendance_report)

        firestoreDb = FirebaseFirestore.getInstance()
        rvAttendanceSessions = findViewById(R.id.rvAttendanceSessions)
        btnCreateNewSession = findViewById(R.id.btnCreateNewSession)

        rvAttendanceSessions.layoutManager = LinearLayoutManager(this)
        sessionAdapter = AttendanceSessionAdapter(
            attendanceSessions,
            { session -> generateAndSaveCsvReport(session) },
            { session, isActive -> toggleAttendanceSessionStatus(session, isActive) },
            { session -> editAttendanceSessionName(session) }
        )
        rvAttendanceSessions.adapter = sessionAdapter

        loadAttendanceSessions()

        btnCreateNewSession.setOnClickListener {
            createNewAttendanceSession()
        }

        checkStoragePermission()
    }

    private fun checkStoragePermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    private fun loadAttendanceSessions() {
        firestoreDb.collection("attendance_sessions")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Toast.makeText(this, "Gagal memuat sesi absensi: ${e.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    // Cek apakah ada perubahan yang signifikan sebelum memicu notifyDataSetChanged
                    val newSessions = mutableListOf<AttendanceSession>()
                    for (doc in snapshots.documents) {
                        val session = doc.toObject(AttendanceSession::class.java)?.copy(id = doc.id)
                        session?.let { newSessions.add(it) }
                    }

                    // Hanya perbarui adapter jika ada perubahan konten atau ukuran
                    if (newSessions != attendanceSessions) { // Membandingkan isi list
                        attendanceSessions.clear()
                        attendanceSessions.addAll(newSessions)
                        sessionAdapter.notifyDataSetChanged()
                    }
                }
            }
    }

    private fun toggleAttendanceSessionStatus(session: AttendanceSession, newStatus: Boolean) {
        // Jika mencoba mengaktifkan sesi:
        if (newStatus) {
            firestoreDb.runTransaction { transaction ->
                val batch = firestoreDb.batch()

                // Nonaktifkan semua sesi lain yang saat ini aktif
                // Penting: Iterasi di sini menggunakan 'attendanceSessions' yang sudah ada di memori.
                // Jika Firestore listener belum update list ini, mungkin ada perbedaan.
                // Namun, dengan transaksi, operasi update akan lebih reliable di sisi server.
                for (s in attendanceSessions) {
                    // Pastikan kita tidak menonaktifkan sesi yang sedang diaktifkan
                    if (s.isActive && s.id != session.id) {
                        val otherSessionRef = firestoreDb.collection("attendance_sessions").document(s.id)
                        batch.update(otherSessionRef, "isActive", false)
                    }
                }

                // Aktifkan sesi yang dipilih
                val currentSessionRef = firestoreDb.collection("attendance_sessions").document(session.id)
                batch.update(currentSessionRef, "isActive", true)

                batch.commit()
            }
                .addOnSuccessListener {
                    Toast.makeText(this, "Sesi '${session.name}' diaktifkan. Sesi lain dinonaktifkan.", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Gagal mengaktifkan sesi: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            // Jika mencoba menonaktifkan sesi (tanpa memengaruhi yang lain)
            firestoreDb.collection("attendance_sessions").document(session.id)
                .update("isActive", false)
                .addOnSuccessListener {
                    Toast.makeText(this, "Status sesi '${session.name}' berhasil diubah menjadi Nonaktif.", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Gagal mengubah status sesi: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun createNewAttendanceSession() {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val currentTimestamp = System.currentTimeMillis()
        val todayDate = dateFormat.format(Date(currentTimestamp))

        val calendar = Calendar.getInstance()
        calendar.timeInMillis = currentTimestamp
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        val expiryTimestamp = calendar.timeInMillis

        val newSession = AttendanceSession(
            name = "Kelas Absen ${todayDate}",
            date = todayDate,
            isActive = false, // Sesi baru defaultnya Nonaktif
            expiresAt = expiryTimestamp,
            createdAt = null // Akan diisi oleh @ServerTimestamp
        )

        // Menonaktifkan semua sesi yang ada sebelum membuat yang baru jika ada yang aktif
        firestoreDb.collection("attendance_sessions")
            .whereEqualTo("isActive", true)
            .get()
            .addOnSuccessListener { querySnapshot ->
                val batch = firestoreDb.batch()
                for (doc in querySnapshot.documents) {
                    batch.update(doc.reference, "isActive", false)
                }
                // Tambahkan sesi baru ke batch (Firestore akan mengisi ID otomatis)
                batch.set(firestoreDb.collection("attendance_sessions").document(), newSession)

                batch.commit()
                    .addOnSuccessListener {
                        Toast.makeText(this, "Kelas absen baru berhasil dibuat dan sesi lain dinonaktifkan!", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Gagal membuat kelas absen baru atau menonaktifkan yang lain: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Gagal memeriksa sesi aktif sebelumnya: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun generateAndSaveCsvReport(session: AttendanceSession) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Izin penyimpanan belum diberikan. Mohon berikan izin.", Toast.LENGTH_LONG).show()
            requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }

        firestoreDb.collection("attendance_sessions")
            .document(session.id)
            .collection("absensi")
            .get()
            .addOnSuccessListener { querySnapshot ->
                val attendanceRecords = mutableListOf<Map<String, Any>>()
                for (document in querySnapshot.documents) {
                    attendanceRecords.add(document.data as Map<String, Any>)
                }

                if (attendanceRecords.isEmpty()) {
                    Toast.makeText(this, "Tidak ada data kehadiran untuk sesi '${session.name}'.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val folderName = "Absen Siswa MAN 4 Tasikmalaya"
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val customDir = File(downloadsDir, folderName)

                if (!customDir.exists()) {
                    customDir.mkdirs()
                }
                // Nama Laporan Setelah Unduh atau Setelah Download
                val fileName = "${session.name.replace(" ", "_").replace(":", "-")}_${session.date}.csv"
//                val fileName = "Laporan_Absensi_${session.name.replace(" ", "_").replace(":", "-")}_${session.date}.csv"
                val file = File(customDir, fileName)

                try {
                    FileWriter(file).use { writer ->
                        writer.append("Nama Lengkap,Username,Email,Tanggal,Waktu,Status\n")

                        for (record in attendanceRecords) {
                            val fullName = record["fullName"] as? String ?: "N/A"
                            val username = record["username"] as? String ?: "N/A"
                            val email = record["email"] as? String ?: "N/A"
                            val date = record["date"] as? String ?: "N/A"
                            val time = record["time"] as? String ?: "N/A"
                            val status = record["status"] as? String ?: "N/A"
                            writer.append("$fullName,$username,$email,$date,$time,$status\n")
                        }
                    }
                    Toast.makeText(this, "Laporan CSV berhasil disimpan di: ${file.absolutePath}", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Gagal membuat laporan CSV: ${e.message}", Toast.LENGTH_LONG).show()
                    e.printStackTrace()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Gagal mengambil data kehadiran untuk sesi '${session.name}': ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun editAttendanceSessionName(session: AttendanceSession) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Edit Nama Sesi")

        val input = EditText(this)
        input.setText(session.name)
        builder.setView(input)

        builder.setPositiveButton("Simpan") { dialog, _ ->
            val newName = input.text.toString().trim()
            if (newName.isNotEmpty()) {
                firestoreDb.collection("attendance_sessions").document(session.id)
                    .update("name", newName)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Nama sesi berhasil diubah menjadi '$newName'.", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Gagal mengubah nama sesi: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            } else {
                Toast.makeText(this, "Nama sesi tidak boleh kosong.", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }
        builder.setNegativeButton("Batal") { dialog, _ ->
            dialog.cancel()
        }

        builder.show()
    }

    private fun uploadFileToGoogleDrive(file: File) {
        Toast.makeText(this, "Mengunggah ${file.name} ke Google Drive...", Toast.LENGTH_LONG).show()
        // Implementasi integrasi Google Drive API perlu ditambahkan di sini.
    }
}