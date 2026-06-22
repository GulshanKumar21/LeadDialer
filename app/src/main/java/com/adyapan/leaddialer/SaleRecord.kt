package com.adyapan.leaddialer

data class SaleRecord(
    val firestoreId : String,
    val name        : String,
    val phone       : String,
    val status      : String  = "Sales Done",
    val notes       : String  = "",
    val calledAt    : Long    = 0L,
    val duration    : Int     = 0,
    val collegeName : String  = "",
    val collegeCity : String  = "",
    val calledBy    : String  = "",
    val employeeName: String  = "",
    val salesDone   : Boolean = true
)
