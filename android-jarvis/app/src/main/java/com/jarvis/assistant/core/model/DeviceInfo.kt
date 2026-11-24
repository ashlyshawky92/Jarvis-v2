package com.jarvis.assistant.core.model

import android.annotation.SuppressLint
import android.os.Build

data class DeviceRuntimeInfo(
    val manufacturer: String,
    val model: String,
    val device: String,
    val androidVersion: String,
    val sdkInt: Int,
    val magicOsVersion: String?,
    val isHonorDevice: Boolean
) {
    companion object {
        fun detect(): DeviceRuntimeInfo {
            val manufacturer = Build.MANUFACTURER.orEmpty()
            val brand = Build.BRAND.orEmpty()
            val isHonor = manufacturer.equals("HONOR", ignoreCase = true) ||
                brand.equals("HONOR", ignoreCase = true)

            val magicOs = readSystemProperty("ro.build.version.magic")
                ?: readSystemProperty("ro.build.version.emui")
                ?: readSystemProperty("ro.honor.magic.version")

            return DeviceRuntimeInfo(
                manufacturer = manufacturer,
                model = Build.MODEL.orEmpty(),
                device = Build.DEVICE.orEmpty(),
                androidVersion = Build.VERSION.RELEASE.orEmpty(),
                sdkInt = Build.VERSION.SDK_INT,
                magicOsVersion = magicOs?.takeIf { it.isNotBlank() },
                isHonorDevice = isHonor
            )
        }

        @SuppressLint("PrivateApi")
        private fun readSystemProperty(key: String): String? {
            return try {
                val systemProperties = Class.forName("android.os.SystemProperties")
                val getMethod = systemProperties.getMethod("get", String::class.java, String::class.java)
                val value = getMethod.invoke(null, key, "") as? String
                value?.takeIf { it.isNotBlank() }
            } catch (_: Throwable) {
                null
            }
        }
    }
}
