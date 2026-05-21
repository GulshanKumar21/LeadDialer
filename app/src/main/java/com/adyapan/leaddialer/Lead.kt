package com.adyapan.leaddialer

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "leads", indices = [Index(value = ["phone"], unique = true)])
data class Lead(
    @PrimaryKey(autoGenerate = true)
    val id          : Int     = 0,
    val name        : String,
    val phone       : String,
    val status      : String  = "Pending",
    val notes       : String  = "",
    val calledAt    : Long    = 0L,
    val duration    : Int     = 0,
    val firestoreId : String? = null,
    val collegeName : String  = "",
    val collegeCity : String  = "",
    val isHotLead   : Boolean = false,  // kept for DB schema compat — no longer used in logic
    val calledBy    : String  = "",
    val salesDone   : Boolean = false
)
