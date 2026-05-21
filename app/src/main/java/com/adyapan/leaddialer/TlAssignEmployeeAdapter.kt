package com.adyapan.leaddialer

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

/**
 * Adapter for the Bulk TL Assign dialog.
 * Each row: employee name + current TL label + checkbox.
 */
class TlAssignEmployeeAdapter(
    private val tlNameMap   : Map<String, String>,   // tlId → tlName (for subtitle)
    private val onChecked   : (userId: String, checked: Boolean) -> Unit
) : ListAdapter<TlAssignEmployeeAdapter.AssignItem, TlAssignEmployeeAdapter.VH>(DIFF) {

    data class AssignItem(
        val userId      : String,
        val name        : String,
        val currentTlId : String  // "" if unassigned
    )

    // Tracks which employees are checked (userId set)
    private val checkedIds = mutableSetOf<String>()

    fun setChecked(userIds: Set<String>) {
        checkedIds.clear()
        checkedIds.addAll(userIds)
    }

    fun getCheckedIds(): Set<String> = checkedIds.toSet()

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvAvatar   : TextView = view.findViewById(R.id.tvAssignAvatar)
        val tvName     : TextView = view.findViewById(R.id.tvAssignEmpName)
        val tvCurrentTl: TextView = view.findViewById(R.id.tvAssignCurrentTl)
        val cbAssign   : CheckBox = view.findViewById(R.id.cbAssign)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_assign_employee, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)

        holder.tvAvatar.text = item.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        holder.tvName.text   = item.name

        holder.tvCurrentTl.text = when {
            item.currentTlId.isBlank()              -> "⚪ No TL assigned"
            tlNameMap.containsKey(item.currentTlId) -> "📌 ${tlNameMap[item.currentTlId]}"
            else                                    -> "📌 Assigned"
        }

        // Avoid triggering listener during rebind
        holder.cbAssign.setOnCheckedChangeListener(null)
        holder.cbAssign.isChecked = checkedIds.contains(item.userId)
        holder.cbAssign.setOnCheckedChangeListener { _, checked ->
            if (checked) checkedIds.add(item.userId)
            else         checkedIds.remove(item.userId)
            onChecked(item.userId, checked)
        }

        holder.itemView.setOnClickListener {
            holder.cbAssign.isChecked = !holder.cbAssign.isChecked
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<AssignItem>() {
            override fun areItemsTheSame(a: AssignItem, b: AssignItem) = a.userId == b.userId
            override fun areContentsTheSame(a: AssignItem, b: AssignItem) = a == b
        }

        /** Attach a search TextWatcher to filter the list */
        fun attachSearch(
            adapter : TlAssignEmployeeAdapter,
            allItems: List<AssignItem>,
            editText: android.widget.EditText
        ) {
            editText.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    val q = s?.toString()?.trim()?.lowercase() ?: ""
                    val filtered = if (q.isEmpty()) allItems
                    else allItems.filter { it.name.lowercase().contains(q) }
                    adapter.submitList(filtered)
                }
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            })
        }
    }
}
