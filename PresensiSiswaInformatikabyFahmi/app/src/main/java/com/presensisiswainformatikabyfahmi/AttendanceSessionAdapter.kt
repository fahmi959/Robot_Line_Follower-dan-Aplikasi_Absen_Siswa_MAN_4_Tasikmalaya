// Lokasi: com/presensisiswainformatikabyfahmi/AttendanceSessionAdapter.kt
package com.presensisiswainformatikabyfahmi

import androidx.recyclerview.widget.RecyclerView
import android.widget.Button
import android.widget.TextView
import android.widget.Switch
import android.widget.ImageView
import android.view.View
import android.widget.CompoundButton

class AttendanceSessionAdapter(
    private val sessions: List<AttendanceSession>,
    private val listener: (AttendanceSession) -> Unit,
    private val toggleListener: (AttendanceSession, Boolean) -> Unit,
    private val editNameListener: (AttendanceSession) -> Unit
) : RecyclerView.Adapter<AttendanceSessionAdapter.SessionViewHolder>() {

    class SessionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvSessionName: TextView = view.findViewById(R.id.tvSessionName)
        val tvSessionStatus: TextView = view.findViewById(R.id.tvSessionStatus)
        val btnGenerateReport: Button = view.findViewById(R.id.btnGenerateReport)
        val switchActiveStatus: Switch = view.findViewById(R.id.switchActiveStatus)
        val ivEditSessionName: ImageView = view.findViewById(R.id.ivEditSessionName)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): SessionViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_attendance_session, parent, false)
        return SessionViewHolder(view)
    }

    override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
        val session = sessions[position]
        holder.tvSessionName.text = "${session.name} (${session.date})"
        holder.tvSessionStatus.text = if (session.isActive) "Status: Aktif" else "Status: Nonaktif"
        holder.btnGenerateReport.setOnClickListener { listener(session) }
        holder.ivEditSessionName.setOnClickListener { editNameListener(session) }

        // --- PENTING: Perbaikan Logika Switch untuk mencegah pemicuan berulang ---
        // Hapus listener yang mungkin sudah terpasang dari daur ulang ViewHolder
        holder.switchActiveStatus.setOnCheckedChangeListener(null)

        // Atur status switch (checked/unchecked) berdasarkan data 'session.isActive'
        holder.switchActiveStatus.isChecked = session.isActive

        // Pasang kembali listener setelah status diatur
        holder.switchActiveStatus.setOnCheckedChangeListener { _, isChecked ->
            // Update teks status di UI secara instan saat switch diubah
            holder.tvSessionStatus.text = if (isChecked) "Status: Aktif" else "Status: Nonaktif"
            // Panggil toggleListener untuk memperbarui status di Firestore
            toggleListener(session, isChecked)
        }
    }

    override fun getItemCount() = sessions.size
}