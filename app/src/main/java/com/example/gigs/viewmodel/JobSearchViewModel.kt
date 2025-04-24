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
import javax.inject.Inject

@HiltViewModel
class JobSearchViewModel @Inject constructor(
    private val jobRepository: JobRepository
) : ViewModel() {
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _filters = MutableStateFlow(JobFilters())
    val filters: StateFlow<JobFilters> = _filters

    private val _searchResults = MutableStateFlow<List<Job>>(emptyList())
    val searchResults: StateFlow<List<Job>> = _searchResults

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilters(filters: JobFilters) {
        _filters.value = filters
    }

    fun searchJobs() {
        viewModelScope.launch {
            _isLoading.value = true
            jobRepository.searchJobs(_searchQuery.value, _filters.value).collect { result ->
                _isLoading.value = false
                if (result.isSuccess) {
                    _searchResults.value = result.getOrNull() ?: emptyList()
                } else {
                    // Handle error
                    _searchResults.value = emptyList()
                }
            }
        }
    }
}