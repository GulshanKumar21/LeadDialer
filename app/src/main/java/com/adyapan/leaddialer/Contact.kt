package com.adyapan.leaddialer

data class Contact(
    val name: String,
    val phone: String,
    var status: String = "Not Called"
)