package com.mlingofeed.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

class RssSearchViewModel : ViewModel() {

    var searchQuery by mutableStateOf("")
        private set

    fun onQueryChange(value: String) {
        searchQuery = value
    }

    fun clearQuery() {
        searchQuery = ""
    }
}
