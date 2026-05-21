package com.adyapan.leaddialer

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(
    tableName = "call_records",
    indices = [Index(value = ["phone", "calledAt"], unique = true)]
)
data class CallRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val phone: String,
    val name: String,
    val duration: Long,
    val calledAt: Long,
    val status: String = "Pending"
) : Parcelable