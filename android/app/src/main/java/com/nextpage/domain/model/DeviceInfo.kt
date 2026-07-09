package com.nextpage.domain.model

import android.content.Context
import android.provider.Settings
import android.os.Build
import java.util.UUID

data class DeviceInfo(
    val hardwareId: String,
    val name: String,
    val os: String,
    val type: String = "mobile"
)

fun detectDeviceInfo(context: Context): DeviceInfo {
    val hardwareId = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ANDROID_ID
    ) ?: UUID.randomUUID().toString()

    return DeviceInfo(
        hardwareId = hardwareId,
        name = Build.MODEL,
        os = "Android ${Build.VERSION.RELEASE}",
        type = "mobile"
    )
}
