package com.nextpage.data.repository

import com.nextpage.data.remote.supabase.SupabaseDeviceDataSource
import com.nextpage.domain.model.Device
import com.nextpage.domain.model.DeviceInfo
import com.nextpage.domain.repository.DeviceRepository
import java.time.Instant

class DeviceRepositoryImpl(
    private val dataSource: SupabaseDeviceDataSource
) : DeviceRepository {

    override suspend fun getDevices(userId: String): Result<List<Device>> = runCatching {
        dataSource.listDevices(userId)
    }

    override suspend fun registerDevice(userId: String, info: DeviceInfo): Result<Device> = runCatching {
        dataSource.upsertDevice(
            Device(
                userId = userId,
                hardwareId = info.hardwareId,
                name = info.name,
                os = info.os,
                type = info.type,
                lastActive = Instant.now().toString()
            )
        )
    }

    override suspend fun updateHeartbeat(deviceId: String): Result<Unit> = runCatching {
        dataSource.updateHeartbeat(deviceId)
    }

    override suspend fun removeDevice(deviceId: String, userId: String): Result<Unit> = runCatching {
        dataSource.removeDevice(deviceId, userId)
    }
}
