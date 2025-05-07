package com.example.gigs.data.model

/**
 * Combined data class that holds both EmployeeProfile and relevant User information
 */
data class EmployeeProfileWithUserInfo(
    val employeeProfile: EmployeeProfile,
    val phone: String = "",
    val userType: UserType = UserType.UNDEFINED,
    val isAdmin: Boolean = false
)