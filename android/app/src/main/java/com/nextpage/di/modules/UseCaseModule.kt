package com.nextpage.di.modules

import com.nextpage.data.sync.ProgressReconciler
import com.nextpage.domain.usecase.GetBookProgressUseCase
import com.nextpage.domain.usecase.GetStatisticsUseCase
import com.nextpage.domain.usecase.UpdateReadingProgressUseCase

class UseCaseModule(
    private val repositoryModule: RepositoryModule,
    private val databaseModule: DatabaseModule,
    private val preferencesModule: PreferencesModule
) {
    val updateReadingProgressUseCase: UpdateReadingProgressUseCase by lazy {
        UpdateReadingProgressUseCase(repositoryModule.readerRepository)
    }

    val getStatisticsUseCase: GetStatisticsUseCase by lazy {
        GetStatisticsUseCase(
            readingStatsRepository = repositoryModule.readingStatsRepository,
            homeRepository = repositoryModule.homeRepository,
            dailyGoalProvider = preferencesModule.dailyGoalProvider
        )
    }

    val getBookProgressUseCase: GetBookProgressUseCase by lazy {
        GetBookProgressUseCase(
            readerRepository = repositoryModule.readerRepository,
            readingProgressDao = databaseModule.readingProgressDao,
            bookDao = databaseModule.bookDao
        )
    }

    val progressReconciler: ProgressReconciler by lazy {
        ProgressReconciler(
            bookDao = databaseModule.bookDao,
            readingProgressDao = databaseModule.readingProgressDao
        )
    }
}
