package com.fktm.app.ujing.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class SavedDevice(
    val qrCode: String = "",
    val deviceId: String = "",
    val macAddress: String = "",
    val storeId: String = "",
    val deviceTypeId: Int = 0,
    val deviceNo: String = "",
    val storeName: String = "",
    val moduleType: Int = -1,
    val customName: String = "",
) {
    val valid: Boolean get() = deviceId.isNotBlank() && macAddress.isNotBlank()

    val displayName: String
        get() = customName.ifBlank {
            listOf(storeName, deviceNo.takeIf(String::isNotBlank)?.let { "$it 号" })
                .filterNotNull()
                .filter(String::isNotBlank)
                .joinToString(" · ")
                .ifBlank { "U净设备" }
        }
}

data class UjingDeviceSnapshot(
    val label: String = "待查询",
    val detail: String = "",
    val createOrderEnabled: Boolean? = null,
    val orderId: String = "",
    val remainingSeconds: Int? = null,
    val checkedAt: Long = 0L,
)

/**
 * Encrypted U净 session and favourite-device store.
 *
 * The upstream project used plaintext DataStore/SharedPreferences. STmall keeps
 * the same functional fields but wraps every sensitive value with AES-GCM and a
 * non-exportable Android Keystore key.
 */
class TokenStore(context: Context) {

    companion object {
        private const val TAG = "UjingStore"
        private const val PREFS = "fktm_ujing_secure"
        private const val KEY_ALIAS = "fktm_ujing_master_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val GCM_TAG_BITS = 128

        private const val FIELD_TOKEN = "token_enc"
        private const val FIELD_MOBILE = "mobile_enc"
        private const val FIELD_DEVICE = "device_enc"
        private const val FIELD_DEVICES = "devices_enc"
        private const val FIELD_QR = "qr_enc"
        private const val FIELD_ORDER_ID = "order_id_enc"
        private const val FIELD_LAST_MODEL = "last_model"
    }

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val devicesState = MutableStateFlow(loadDevices())

    val savedDevicesFlow = devicesState.asStateFlow()

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let {
            return it.secretKey
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun putEncrypted(field: String, value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val packed = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        prefs.edit().putString(field, packed).apply()
    }

    private fun getEncrypted(field: String): String? {
        val packed = prefs.getString(field, null) ?: return null
        return runCatching {
            val parts = packed.split(':', limit = 2)
            require(parts.size == 2)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                key(),
                GCMParameterSpec(GCM_TAG_BITS, Base64.decode(parts[0], Base64.NO_WRAP)),
            )
            String(
                cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)),
                Charsets.UTF_8,
            )
        }.onFailure {
            Log.w(TAG, "U净安全存储解密失败: ${it.message}")
        }.getOrNull()
    }

    private fun remove(field: String) {
        prefs.edit().remove(field).apply()
    }

    val tokenFlow: Flow<String?> = devicesFlow { getEncrypted(FIELD_TOKEN) }
    val mobileFlow: Flow<String?> = devicesFlow { getEncrypted(FIELD_MOBILE) }
    val lastWorkModelFlow: Flow<Int> = devicesFlow { prefs.getInt(FIELD_LAST_MODEL, 0) }

    private fun <T> devicesFlow(block: () -> T): Flow<T> {
        val state = MutableStateFlow(block())
        state.value = block()
        return state.map { block() }
    }

    fun currentToken(): String? = getEncrypted(FIELD_TOKEN)

    fun currentMobile(): String? = getEncrypted(FIELD_MOBILE)

    fun currentDevice(): SavedDevice =
        getEncrypted(FIELD_DEVICE)
            ?.let { runCatching { deviceFromJson(JSONObject(it)) }.getOrNull() }
            ?: SavedDevice()

    fun currentOrderId(): String = getEncrypted(FIELD_ORDER_ID).orEmpty()

    fun currentQrCode(): String = getEncrypted(FIELD_QR).orEmpty()

    fun loadDevices(): List<SavedDevice> {
        val raw = getEncrypted(FIELD_DEVICES) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    array.optJSONObject(index)?.let(::deviceFromJson)?.takeIf { it.valid }?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveSession(token: String, mobile: String) {
        putEncrypted(FIELD_TOKEN, token)
        putEncrypted(FIELD_MOBILE, mobile)
    }

    fun saveDevice(device: SavedDevice) {
        val qr = currentQrCode()
        val completeDevice = device.copy(qrCode = device.qrCode.ifBlank { qr })
        putEncrypted(FIELD_DEVICE, deviceToJson(completeDevice).toString())
        val next = loadDevices()
            .filterNot { it.sameDevice(completeDevice) }
            .toMutableList()
            .apply { add(0, completeDevice) }
        saveDeviceList(next)
    }

    fun saveDeviceList(devices: List<SavedDevice>) {
        val array = JSONArray()
        devices.distinctBy { it.deviceId.ifBlank { it.macAddress } }.forEach {
            array.put(deviceToJson(it))
        }
        putEncrypted(FIELD_DEVICES, array.toString())
        devicesState.value = devices
    }

    fun removeDevice(deviceId: String) {
        saveDeviceList(loadDevices().filterNot { it.deviceId == deviceId })
        if (currentDevice().deviceId == deviceId) {
            remove(FIELD_DEVICE)
            remove(FIELD_QR)
        }
    }

    fun renameDevice(deviceId: String, name: String) {
        val current = currentDevice()
        saveDeviceList(
            loadDevices().map {
                if (it.deviceId == deviceId) it.copy(customName = name.trim()) else it
            }
        )
        if (current.deviceId == deviceId) {
            saveDevice(current.copy(customName = name.trim()))
        }
    }

    fun saveLastWorkModel(id: Int) {
        prefs.edit().putInt(FIELD_LAST_MODEL, id).apply()
    }

    fun saveOrderId(id: String) {
        if (id.isBlank()) remove(FIELD_ORDER_ID) else putEncrypted(FIELD_ORDER_ID, id)
    }

    fun saveQrCode(code: String) {
        if (code.isBlank()) remove(FIELD_QR) else putEncrypted(FIELD_QR, code)
    }

    fun clearSession() {
        listOf(FIELD_TOKEN, FIELD_MOBILE, FIELD_DEVICE, FIELD_QR, FIELD_ORDER_ID).forEach(::remove)
    }

    fun clearAll() {
        prefs.edit().clear().apply()
        devicesState.value = emptyList()
    }

    private fun SavedDevice.sameDevice(other: SavedDevice): Boolean =
        (deviceId.isNotBlank() && deviceId == other.deviceId) ||
            (macAddress.isNotBlank() && macAddress.equals(other.macAddress, ignoreCase = true))

    private fun deviceToJson(device: SavedDevice): JSONObject = JSONObject().apply {
        put("qrCode", device.qrCode)
        put("deviceId", device.deviceId)
        put("macAddress", device.macAddress)
        put("storeId", device.storeId)
        put("deviceTypeId", device.deviceTypeId)
        put("deviceNo", device.deviceNo)
        put("storeName", device.storeName)
        put("moduleType", device.moduleType)
        put("customName", device.customName)
    }

    private fun deviceFromJson(value: JSONObject): SavedDevice = SavedDevice(
        qrCode = value.optString("qrCode"),
        deviceId = value.optString("deviceId"),
        macAddress = value.optString("macAddress"),
        storeId = value.optString("storeId"),
        deviceTypeId = value.optInt("deviceTypeId"),
        deviceNo = value.optString("deviceNo"),
        storeName = value.optString("storeName"),
        moduleType = value.optInt("moduleType", -1),
        customName = value.optString("customName"),
    )
}
