package studio.inventory.android

import android.content.Context
import android.os.Build
import java.util.UUID

data class SyncConfiguration(
    val webDavUrl: String = "",
    val username: String = "",
    val password: String = "",
    val deviceId: String = "",
    val deviceName: String = "",
    val repositoryKey: String = "",
    val intervalSeconds: Int = 5,
    val allowInsecureHttp: Boolean = false,
) {
    val isConfigured: Boolean
        get() = webDavUrl.isNotBlank() &&
            username.isNotBlank() &&
            password.isNotBlank() &&
            deviceId.isNotBlank() &&
            deviceName.isNotBlank() &&
            repositoryKey.isNotBlank()
}

class SyncSettingsRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
    private val secrets = AndroidSecretStore(context)

    fun load(): SyncConfiguration = SyncConfiguration(
        webDavUrl = preferences.getString(KeyUrl, "").orEmpty(),
        username = preferences.getString(KeyUsername, "").orEmpty(),
        password = secrets.get(SecretPassword).orEmpty(),
        deviceId = preferences.getString(KeyDeviceId, "").orEmpty(),
        deviceName = preferences.getString(KeyDeviceName, defaultDeviceName()).orEmpty(),
        repositoryKey = secrets.get(SecretRepositoryKey).orEmpty(),
        intervalSeconds = preferences.getInt(KeyInterval, 5),
        allowInsecureHttp = preferences.getBoolean(KeyAllowInsecureHttp, false),
    )

    fun save(configuration: SyncConfiguration) {
        require(configuration.deviceName.isNotBlank()) { "设备名不能为空。" }
        require(configuration.intervalSeconds in AllowedIntervals) { "同步间隔不受支持。" }
        preferences.edit()
            .putString(KeyUrl, configuration.webDavUrl.trim().trimEnd('/'))
            .putString(KeyUsername, configuration.username.trim())
            .putString(KeyDeviceId, configuration.deviceId.ifBlank { UUID.randomUUID().toString() })
            .putString(KeyDeviceName, configuration.deviceName.trim())
            .putInt(KeyInterval, configuration.intervalSeconds)
            .putBoolean(KeyAllowInsecureHttp, configuration.allowInsecureHttp)
            .apply()
        secrets.put(SecretPassword, configuration.password)
        secrets.put(SecretRepositoryKey, configuration.repositoryKey)
    }

    fun clear() {
        preferences.edit().clear().apply()
        secrets.remove(SecretPassword)
        secrets.remove(SecretRepositoryKey)
    }

    fun saveBackupPassword(password: String) {
        require(password.length >= 8) { "备份密码至少 8 位。" }
        secrets.put(SecretBackupPassword, password)
    }

    fun backupPassword(): String? = secrets.get(SecretBackupPassword)

    fun newConfiguration(): SyncConfiguration = SyncConfiguration(
        deviceId = UUID.randomUUID().toString(),
        deviceName = defaultDeviceName(),
        repositoryKey = SyncCrypto.encodeKey(SyncCrypto.generateKey()),
    )

    companion object {
        val AllowedIntervals = setOf(0, 3, 5, 10, 30, 60)

        private const val PreferencesName = "sync_settings"
        private const val KeyUrl = "webdav_url"
        private const val KeyUsername = "username"
        private const val KeyDeviceId = "device_id"
        private const val KeyDeviceName = "device_name"
        private const val KeyInterval = "interval_seconds"
        private const val KeyAllowInsecureHttp = "allow_insecure_http"
        private const val SecretPassword = "webdav_password"
        private const val SecretRepositoryKey = "repository_key"
        private const val SecretBackupPassword = "backup_password"

        fun defaultDeviceName(): String = Build.MODEL.trim().ifBlank { "Android 设备" }
    }
}
