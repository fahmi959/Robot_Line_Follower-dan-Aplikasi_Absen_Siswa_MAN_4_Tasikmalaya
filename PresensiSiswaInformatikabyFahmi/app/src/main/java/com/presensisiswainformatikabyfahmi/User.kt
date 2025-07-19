package com.presensisiswainformatikabyfahmi


// Pastikan ini di package yang sama: com.presensisiswainformatikabyfahmi
data class User(
    val fullName: String = "",
    val dob: String = "",
    val email: String = "",
    val username: String = "",
    val faceEmbedding: List<Float>? = null, // Tambahkan field ini
    val profileImageUrl: String? = null, // Tambahkan field ini untuk URL foto profil
    val password: String? = null // **WARNING: password disini bisa berbahaya jika tidak disimpan secara aman
)