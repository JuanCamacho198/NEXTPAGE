package com.nextpage.di.modules

import android.content.Context
import com.nextpage.data.session.ReaderPreferences
import com.nextpage.data.session.ReadingGoalPreferences

class PreferencesModule(context: Context) {
    val readerPreferences: ReaderPreferences = ReaderPreferences(context.applicationContext)

    val readingGoalPreferences: ReadingGoalPreferences = ReadingGoalPreferences(context.applicationContext)

    val dailyGoalProvider: () -> Int = { readingGoalPreferences.load() ?: 30 }
}
