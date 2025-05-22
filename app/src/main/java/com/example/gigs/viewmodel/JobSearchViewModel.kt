package com.example.gigs.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gigs.data.repository.JobRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import com.example.gigs.data.model.Job
import com.example.gigs.ui.screens.jobs.JobFilters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job as KotlinJob
import kotlinx.coroutines.delay
import android.util.Log
import javax.inject.Inject

@HiltViewModel
class JobSearchViewModel @Inject constructor(
    private val jobRepository: JobRepository
) : ViewModel() {
    private val TAG = "JobSearchViewModel"

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _filters = MutableStateFlow(JobFilters())
    val filters: StateFlow<JobFilters> = _filters

    private val _searchResults = MutableStateFlow<List<Job>>(emptyList())
    val searchResults: StateFlow<List<Job>> = _searchResults

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    // Track current search job for debouncing
    private var searchJob: KotlinJob? = null

    // Configure debounce delay
    private val searchDebounceDelay = 300L // milliseconds

    /**
     * Set search query with auto debounced search
     */
    fun setSearchQuery(query: String) {
        Log.d(TAG, "Setting search query: $query")
        _searchQuery.value = query

        // Trigger debounced search
        debouncedSearch()
    }

    /**
     * Set filters with auto debounced search
     */
    fun setFilters(filters: JobFilters) {
        Log.d(TAG, "Setting filters: $filters")
        _filters.value = filters

        // Trigger debounced search
        debouncedSearch()
    }

    /**
     * Implements debouncing to prevent excessive API calls during rapid input
     */
    private fun debouncedSearch() {
        // Cancel any previous search job
        searchJob?.cancel()

        // Start a new search job with debounce delay
        searchJob = viewModelScope.launch {
            // Wait for the debounce period before executing search
            delay(searchDebounceDelay)

            // Log that we're executing the search after debounce
            Log.d(TAG, "Executing search after debounce delay: query=${_searchQuery.value}, filters=${_filters.value}")

            // Execute the actual search
            searchJobs()
        }
    }

    /**
     * Execute job search with current query and filters
     * This can also be called directly for immediate search
     */
    fun searchJobs() {
        // Cancel any ongoing debounced search
        searchJob?.cancel()

        viewModelScope.launch {
            _isLoading.value = true
            Log.d(TAG, "Searching jobs with query: ${_searchQuery.value} and filters: ${_filters.value}")

            try {
                jobRepository.searchJobs(_searchQuery.value, _filters.value).collect { result ->
                    _isLoading.value = false
                    if (result.isSuccess) {
                        val jobs = result.getOrNull() ?: emptyList()
                        _searchResults.value = jobs
                        Log.d(TAG, "Search returned ${jobs.size} results")
                    } else {
                        // Handle error
                        Log.e(TAG, "Search error: ${result.exceptionOrNull()?.message}")
                        _searchResults.value = emptyList()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during search: ${e.message}", e)
                _isLoading.value = false
                _searchResults.value = emptyList()
            }
        }
    }

    /**
     * Clear search query and results
     */
    fun clearSearch() {
        // Cancel any pending search
        searchJob?.cancel()

        // Reset search state
        _searchQuery.value = ""
        _searchResults.value = emptyList()

        // Don't automatically search when clearing
        Log.d(TAG, "Search cleared")
    }
}