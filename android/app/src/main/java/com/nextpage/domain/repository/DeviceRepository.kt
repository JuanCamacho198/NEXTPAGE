package com.nextpage.domain.repository

import com.nextpage.domain.model.Device
import com.nextpage.domain.model.DeviceInfo

interface DeviceRepository {
    suspend fun getDevices(userId: String): Result<List<Device>>
    suspend fun registerDevice(userId: String, info: DeviceInfo): Result<Device>
    suspend fun updateHeartbeat(deviceId: String): Result<Unit>
    suspend fun removeDevice(deviceId: String, userId: String): Result<Unit>
}
