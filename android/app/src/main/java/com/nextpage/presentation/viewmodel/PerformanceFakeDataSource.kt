package com.nextpage.presentation.viewmodel

interface PerformanceFakeDataSource {
    fun generateTimings(): List<PerformanceTiming>
    suspend fun loadResources(): PerformanceResources
    fun loadSyncStatus(): PerformanceSyncStatus
    fun loadDiagnostics(): PerformanceDiagnostics
}
