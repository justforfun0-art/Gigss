package com.example.gigs.data.model

data class Review(
    val id: String = "",
    val jobId: String,
    val reviewerId: String,
    val revieweeId: String,
    val rating: Int,
    val comment: String? = null,
    val createdAt: String? = null,

    // Transient fields for UI
    @Transient val reviewerName: String = "",
    @Transient val revieweeName: String = "",
    @Transient val jobTitle: String = ""
)