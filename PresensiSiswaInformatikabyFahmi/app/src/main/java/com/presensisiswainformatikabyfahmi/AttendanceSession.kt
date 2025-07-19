// 1. Lokasi: com/presensisiswainformatikabyfahmi/AttendanceSession.kt
package com.presensisiswainformatikabyfahmi
import com.google.firebase.firestore.PropertyName // PENTING: Tambahkan ini!
import com.google.firebase.firestore.ServerTimestamp // Pastikan ini diimport!
import java.util.Date

data class AttendanceSession(
    val id: String = "",
    val name: String = "",
    val date: String = "", // Format YYYY-MM-DD
    @get:PropertyName("isActive") @set:PropertyName("isActive")
    var isActive: Boolean = false, // Pastikan ini 'var'
    val expiresAt: Long = 0L, // Timestamp kapan sesi kedaluwarsa (ms sejak epoch)
    @ServerTimestamp // Field ini akan diisi otomatis oleh Firestore saat dokumen dibuat
    val createdAt: Date? = null // Gunakan Date? karena Firestore akan mengisinya
)