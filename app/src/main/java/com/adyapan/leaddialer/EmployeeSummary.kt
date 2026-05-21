package com.adyapan.leaddialer


data class EmployeeSummary(
    val employeeName  : String,
    val userId        : String,
    val totalLeads    : Int,
    val connected     : Int,
    val interested    : Int,
    val pending       : Int,
    val salesDone     : Int,
    val expectedSales : Int = 0,
    val adminTarget   : Int = 0,   // ← Target set by admin for this employee
    val tlName        : String = "",// ← TL name this employee belongs to
    val leads         : List<Lead>
)
