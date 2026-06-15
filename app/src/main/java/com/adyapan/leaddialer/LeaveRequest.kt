package com.adyapan.leaddialer

data class LeaveRequest(
    val id: String = "",
    val uid: String = "",
    val employeeName: String = "",
    val employeeEmail: String = "",
    val leaveType: String = "",
    val fromDate: String = "",
    val toDate: String = "",
    val reason: String = "",
    val status: String = "Pending",
    val appliedAt: Long = 0L
)
