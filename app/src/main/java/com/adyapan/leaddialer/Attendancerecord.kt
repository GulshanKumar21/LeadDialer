package com.adyapan.leaddialer

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "attendance")
data class AttendanceRecord(
    @PrimaryKey(autoGenerate = true)
    val id          : Int    = 0,
    val date        : String,          // "dd/MM/yyyy"
    val punchInTime : String,          // "HH:mm"
    val punchInMs   : Long   = 0L,     // timestamp
    val isLate      : Boolean = false,
    val lateReason  : String  = "",
    val totalCalls  : Int     = 0,
    val employeeName: String  = ""
)