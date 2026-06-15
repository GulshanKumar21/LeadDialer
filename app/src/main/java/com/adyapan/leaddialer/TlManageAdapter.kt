package com.adyapan.leaddialer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class TlManageAdapter(
    private val onEdit   : (TeamLeaderManager.TeamLeader) -> Unit,
    private val onDelete : (TeamLeaderManager.TeamLeader) -> Unit,
    private val onAssign : (TeamLeaderManager.TeamLeader) -> Unit = {}
) : ListAdapter<TeamLeaderManager.TeamLeader, TlManageAdapter.VH>(DIFF) {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvName    : TextView = view.findViewById(R.id.tvTlName)
        val tvUrl     : TextView = view.findViewById(R.id.tvTlUrl)
        val btnAssign : TextView = view.findViewById(R.id.btnTlAssign)
        val btnEdit   : TextView = view.findViewById(R.id.btnTlEdit)
        val btnDelete : TextView = view.findViewById(R.id.btnTlDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_team_leader, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val tl = getItem(position)
        holder.tvName.text = "👤 ${tl.name}"

        holder.tvUrl.visibility = View.GONE

        holder.btnAssign.setOnClickListener  { onAssign(tl) }
        holder.btnEdit.setOnClickListener   { onEdit(tl) }
        holder.btnDelete.setOnClickListener { onDelete(tl) }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<TeamLeaderManager.TeamLeader>() {
            override fun areItemsTheSame(a: TeamLeaderManager.TeamLeader, b: TeamLeaderManager.TeamLeader) = a.id == b.id
            override fun areContentsTheSame(a: TeamLeaderManager.TeamLeader, b: TeamLeaderManager.TeamLeader) = a == b
        }
    }
}
