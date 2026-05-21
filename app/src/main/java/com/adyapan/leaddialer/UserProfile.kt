package com.adyapan.leaddialer

data class UserProfile(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val employeeId: String = "",
    val designation: String = "",
    val department: String = "",
    val reportingManager: String = "",
    val workLocation: String = "",
    val dateOfJoining: String = ""
)
