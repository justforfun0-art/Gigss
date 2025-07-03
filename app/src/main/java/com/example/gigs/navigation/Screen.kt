package com.example.gigs.navigation

import android.net.Uri

sealed class Screen(open val route: String) {
    object Welcome : Screen("welcome")
    object PhoneAuth : Screen("phone_auth")
    object OtpVerification : Screen("otp_verification")
    object SelectUserType : Screen("select_user_type")
    object CreateEmployeeProfile : Screen("create_employee_profile")
    object EmployeeProfileDetails : Screen("employee_profile_details")
    object CreateEmployerProfile : Screen("create_employer_profile")
    object EmployerProfileDetails : Screen("employer_profile_details")
    object EmployeeHome : Screen("employee_home")
    object EmployerHome : Screen("employer_home")

    // Note: You have both UserTypeSelection and SelectUserType - they appear to be duplicates
    // Consider removing one of them
    object UserTypeSelection : Screen("user_type_selection")
    object BasicProfileSetup : Screen("basic_profile_setup")
    object EmployeeProfileSetup : Screen("employee_profile_setup")
    object EmployerProfileSetup : Screen("employer_profile_setup")

    object MyJobsFiltered : Screen("my_jobs/{filter}/{title}") {
        fun createRoute(filter: String, title: String): String =
            "my_jobs/$filter/${Uri.encode(title)}"
    }
    // New screens for added features
    object JobListing : Screen("job_listing/{district}") {
        fun createRoute(district: String): String = "job_listing/$district"
    }

    object EmployerJobDetails : Screen("employer_job_details/{jobId}") {
        fun createRoute(jobId: String): String = "employer_job_details/$jobId"
    }

    object EditEmployerProfile : Screen("edit_employer_profile") {
        override val route = "edit_employer_profile"
    }

    object JobDetails : Screen("job_details/{jobId}") {
        fun createRoute(jobId: String): String = "job_details/$jobId"
    }
    object CreateJob : Screen("create_job")
    object Conversations : Screen("conversations")
    object Chat : Screen("chat/{conversationId}/{otherUserName}/{receiverId}") {
        fun createRoute(conversationId: String, otherUserName: String, receiverId: String): String =
            "chat/$conversationId/$otherUserName/$receiverId"
    }
    object Notifications : Screen("notifications")
    object Reviews : Screen("reviews/{jobId}") {
        fun createRoute(jobId: String? = null): String =
            jobId?.let { "reviews/$it" } ?: "reviews/null"
    }
    object CreateReview : Screen("create_review/{jobId}/{revieweeId}/{revieweeName}") {
        fun createRoute(jobId: String, revieweeId: String, revieweeName: String): String =
            "create_review/$jobId/$revieweeId/$revieweeName"
    }
    object EmployeeDashboard : Screen("employee_dashboard")
    object EmployerDashboard : Screen("employer_dashboard")

    // Add Job History Screen
    object JobHistory : Screen("job_history")

    // Add Job Application Details Screen
    object JobApplicationDetails : Screen("application_details/{applicationId}") {
        fun createRoute(applicationId: String): String = "application_details/$applicationId"
    }

    object JobApplications : Screen("job_applications/{jobId}/{jobTitle}") {
        fun createRoute(jobId: String, jobTitle: String): String =
            "job_applications/$jobId/${jobTitle.replace("/", "-")}"
    }

    object AllApplications : Screen("all_applications") {
        override val route = "all_applications"
    }

    object ApplicantProfile : Screen("applicant_profile/{applicantId}") {
        fun createRoute(applicantId: String): String = "applicant_profile/$applicantId"
    }

    // Admin screens
    object AdminJobApproval : Screen("admin_job_approval")
    object AdminDashboard : Screen("admin_dashboard")

    // Helper functions for parameterized routes
    fun createRoute(vararg params: String): String {
        var route = this.route
        params.forEach {
            route = route.replaceFirst("{}", it)
        }
        return route
    }


}