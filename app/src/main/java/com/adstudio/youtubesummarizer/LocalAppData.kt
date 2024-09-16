package com.adstudio.youtubesummarizer

import android.content.Context
import androidx.appcompat.app.AppCompatActivity

class LocalAppData: AppCompatActivity() {

    private val SHAREDPREFS = "SHARED_PREFS"
    private val ACCEPTED = "ACCEPTED"

    var accepted = false

    fun saveData(context: Context) {
        val preferences = context.getSharedPreferences(SHAREDPREFS, MODE_PRIVATE)
        val editor = preferences.edit()
        editor.putBoolean(ACCEPTED, accepted)
        editor.apply()
    }

    fun loadData(context: Context) {
        val preferences = context.getSharedPreferences(SHAREDPREFS, MODE_PRIVATE)
        accepted = preferences.getBoolean(ACCEPTED, false)
    }
}