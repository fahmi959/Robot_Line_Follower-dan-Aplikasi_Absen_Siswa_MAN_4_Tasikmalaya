package com.presensisiswainformatikabyfahmi

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.client.http.InputStreamContent
import de.hdodenhof.circleimageview.CircleImageView
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Collections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await

class ProfileActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var ivProfilePhoto: CircleImageView
    private lateinit var btnChangePhoto: Button
    private lateinit var etFullName: EditText
    private lateinit var btnSaveProfile: Button

    private var selectedImageUri: Uri? = null
    // Change this to store the Google Drive File ID instead of the direct URL
    private var currentProfileFileId: String? = null // Will store Google Drive File ID
    private var currentUsername: String? = null
    private var currentFullName: String? = null

    private var mDriveService: Drive? = null
    private val RC_SIGN_IN = 9001
    private val GOOGLE_DRIVE_FOLDER_ID = "16uLOmSfIm3eo7vmTvhU1ruFqTEM-fRxi"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        ivProfilePhoto = findViewById(R.id.ivProfilePhoto)
        btnChangePhoto = findViewById(R.id.btnChangePhoto)
        etFullName = findViewById(R.id.etFullName)
        btnSaveProfile = findViewById(R.id.btnSaveProfile)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .requestEmail()
            .build()

        val googleSignInClient = GoogleSignIn.getClient(this, gso)

        googleSignInClient.silentSignIn()
            .addOnSuccessListener { googleAccount ->
                if (googleAccount != null) {
                    initializeDriveClient(googleAccount)
                    loadUserProfile()
                } else {
                    startActivityForResult(googleSignInClient.signInIntent, RC_SIGN_IN)
                }
            }
            .addOnFailureListener {
                startActivityForResult(googleSignInClient.signInIntent, RC_SIGN_IN)
            }

        btnChangePhoto.setOnClickListener {
            openImageChooser()
        }

        btnSaveProfile.setOnClickListener {
            saveUserProfile()
            // It's generally better to finish the activity or navigate after a successful save,
            // not immediately, as the save operation is asynchronous.
            // If you navigate immediately, the save might not have completed.
            // Consider moving this to the addOnSuccessListener of performFirestoreUpdate
            // startActivity(Intent(this, MainActivity::class.java))
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            RC_SIGN_IN -> {
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                try {
                    val account = task.getResult(ApiException::class.java)
                    if (account.email != null) {
                        initializeDriveClient(account)
                        loadUserProfile()
                    } else {
                        Toast.makeText(this, "Login Google berhasil, namun email tidak tersedia.", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: ApiException) {
                    Toast.makeText(this, "Login Google gagal: ${e.statusCode}", Toast.LENGTH_SHORT).show()
                }
            }
            imageChooserLauncher.contract.hashCode() -> {
                if (resultCode == Activity.RESULT_OK) {
                    val uri: Uri? = data?.data
                    uri?.let {
                        uploadAndPreviewPhoto(it)
                    }
                }
            }
        }
    }

    private fun initializeDriveClient(googleAccount: GoogleSignInAccount) {
        val accountName = googleAccount.email
        if (accountName == null) {
            Toast.makeText(this, "Gagal inisialisasi Email akun Google tidak tersedia.", Toast.LENGTH_LONG).show()
            return
        }

        val credential = GoogleAccountCredential.usingOAuth2(
            this, Collections.singleton(DriveScopes.DRIVE_FILE)
        )
        credential.selectedAccountName = accountName
        mDriveService = Drive.Builder(
            AndroidHttp.newCompatibleTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        )
            .setApplicationName("PresensiSiswaInformatika")
            .build()
        Toast.makeText(this, "Layanan Ubah Profile Siap.", Toast.LENGTH_SHORT).show()
    }

    private fun loadUserProfile() {
        val currentUser = auth.currentUser
        currentUser?.let { user ->
            db.collection("users").document(user.uid)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val userData = document.toObject(User::class.java)
                        userData?.let {
                            etFullName.setText(it.fullName)
                            currentUsername = it.username
                            currentFullName = it.fullName

                            // Assume profileImageUrl in User class now stores the File ID
                            it.profileImageUrl?.let { fileId ->
                                currentProfileFileId = fileId // Store the file ID
                                val directImageUrl = getGoogleDriveDirectLink(fileId) // Construct direct link
                                Glide.with(this)
                                    .load(directImageUrl)
                                    .placeholder(R.drawable.ic_default_profile)
                                    .error(R.drawable.ic_default_profile)
                                    .into(ivProfilePhoto)
                            } ?: run {
                                currentProfileFileId = null
                                ivProfilePhoto.setImageResource(R.drawable.ic_default_profile)
                            }
                        }
                    } else {
                        etFullName.setText("")
                        ivProfilePhoto.setImageResource(R.drawable.ic_default_profile)
                        currentProfileFileId = null // Reset file ID
                        currentUsername = null
                        currentFullName = null
                        Toast.makeText(this, "Data profil tidak ditemukan. Silakan lengkakan.", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Gagal memuat profil: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    // Helper function to construct the direct Google Drive link
    private fun getGoogleDriveDirectLink(fileId: String): String {
        return "https://drive.google.com/uc?export=view&id=$fileId"
    }

    private fun openImageChooser() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "image/*"
        imageChooserLauncher.launch(intent)
    }

    private val imageChooserLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            data?.data?.let { uri ->
                uploadAndPreviewPhoto(uri)
            }
        }
    }

    private fun uploadAndPreviewPhoto(imageUri: Uri) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Pengguna tidak login.", Toast.LENGTH_SHORT).show()
            return
        }

        if (mDriveService == null) {
            Toast.makeText(this, "Layanan Ubah Profile belum siap. Pastikan Anda sudah login Google.", Toast.LENGTH_LONG).show()
            return
        }

        ivProfilePhoto.setImageResource(R.drawable.ic_default_profile) // Placeholder loading
        Toast.makeText(this, "Mengunggah foto...", Toast.LENGTH_SHORT).show()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val userDocRef = db.collection("users").document(currentUser.uid)
                val documentSnapshot = userDocRef.get().await()
                val user = documentSnapshot.toObject(User::class.java)
                val username = user?.username ?: "unknown_user"
                val fullName = user?.fullName ?: "unknown_fullname"

                val contentResolver = applicationContext.contentResolver
                val mimeType = contentResolver.getType(imageUri) ?: "image/jpeg"

                val originalFileNameWithExt = getFileName(imageUri) ?: "image.jpg"
                val fileExtension = getFileExtension(originalFileNameWithExt) ?: "jpg"
                val originalFileNameWithoutExt = originalFileNameWithExt.substringBeforeLast(".")

                val driveFileName = "${username.replace(" ", "_")}_" +
                        "${fullName.replace(" ", "_")}_" +
                        "${currentUser.uid}_" +
                        "${originalFileNameWithoutExt.replace(" ", "_")}.${fileExtension}"

                val tempFile = File(cacheDir, "temp_upload_${currentUser.uid}.${fileExtension}")
                contentResolver.openInputStream(imageUri)?.use { inputStream ->
                    FileOutputStream(tempFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                val mediaContent = InputStreamContent(mimeType, tempFile.inputStream())

                val fileMetadata = com.google.api.services.drive.model.File().apply {
                    name = driveFileName
                    parents = Collections.singletonList(GOOGLE_DRIVE_FOLDER_ID)
                }

                val uploadedFile = mDriveService?.files()?.create(fileMetadata, mediaContent)
                    ?.setFields("id") // REQUEST ONLY THE ID
                    ?.execute()

                tempFile.delete()

                withContext(Dispatchers.Main) {
                    if (uploadedFile != null && uploadedFile.id != null) {
                        val uploadedFileId = uploadedFile.id // Get the File ID
                        currentProfileFileId = uploadedFileId // Store the File ID
                        val directImageUrl = getGoogleDriveDirectLink(uploadedFileId) // Construct direct link for display

                        Glide.with(this@ProfileActivity)
                            .load(directImageUrl)
                            .placeholder(R.drawable.ic_default_profile)
                            .error(R.drawable.ic_default_profile)
                            .into(ivProfilePhoto)
                        Toast.makeText(this@ProfileActivity, "Foto berhasil diunggah!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@ProfileActivity, "Gagal mengunggah foto.", Toast.LENGTH_SHORT).show()
                        ivProfilePhoto.setImageResource(R.drawable.ic_default_profile)
                    }
                }
            } catch (e: com.google.api.client.googleapis.json.GoogleJsonResponseException) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ProfileActivity, "Error Drive API: ${e.details?.message}", Toast.LENGTH_LONG).show()
                    e.printStackTrace()
                    ivProfilePhoto.setImageResource(R.drawable.ic_default_profile)
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ProfileActivity, "Error I/O foto: ${e.message}", Toast.LENGTH_LONG).show()
                    e.printStackTrace()
                    ivProfilePhoto.setImageResource(R.drawable.ic_default_profile)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ProfileActivity, "Error tak terduga foto: ${e.message}", Toast.LENGTH_LONG).show()
                    e.printStackTrace()
                    ivProfilePhoto.setImageResource(R.drawable.ic_default_profile)
                }
            }
        }
    }

    private fun saveUserProfile() {
        val newFullName = etFullName.text.toString().trim()
        val currentUser = auth.currentUser

        if (currentUser == null) {
            Toast.makeText(this, "Pengguna tidak login.", Toast.LENGTH_SHORT).show()
            return
        }

        val userDocRef = db.collection("users").document(currentUser.uid)

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val documentSnapshot = userDocRef.get().await()
                val existingUser = documentSnapshot.toObject(User::class.java)

                val currentFullNameFromFirestore = existingUser?.fullName ?: ""
                // Now comparing file IDs, not direct URLs
                val currentProfileFileIdFromFirestore = existingUser?.profileImageUrl

                val isFullNameChanged = newFullName != currentFullNameFromFirestore
                val isProfileImageChanged = currentProfileFileId != currentProfileFileIdFromFirestore

                if (!isFullNameChanged && !isProfileImageChanged) {
                    Toast.makeText(this@ProfileActivity, "Tidak ada perubahan untuk disimpan.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val updates = mutableMapOf<String, Any>()

                if (isFullNameChanged) {
                    updates["fullName"] = newFullName
                }

                // Store the Google Drive File ID in Firestore
                currentProfileFileId?.let {
                    updates["profileImageUrl"] = it
                } ?: run {
                    updates["profileImageUrl"] = com.google.firebase.firestore.FieldValue.delete()
                }

                performFirestoreUpdate(userDocRef, updates)

            } catch (e: Exception) {
                Toast.makeText(this@ProfileActivity, "Gagal memuat data profil untuk perbandingan: ${e.message}", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
        }
    }

    private fun performFirestoreUpdate(userDocRef: com.google.firebase.firestore.DocumentReference, updates: Map<String, Any>) {
        userDocRef.update(updates)
            .addOnSuccessListener {
                Toast.makeText(this, "Profil berhasil diperbarui!", Toast.LENGTH_SHORT).show()
                // You can navigate back to MainActivity here, after successful save
                startActivity(Intent(this, MainActivity::class.java))
                finish() // Finish this activity so user can't go back to it
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Gagal memperbarui profil: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        result = it.getString(nameIndex)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != -1) {
                result = result?.substring(cut!! + 1)
            }
        }
        return result
    }

    private fun getFileExtension(fileName: String): String? {
        val dotIndex = fileName.lastIndexOf('.')
        return if (dotIndex == -1) null else fileName.substring(dotIndex + 1)
    }
}