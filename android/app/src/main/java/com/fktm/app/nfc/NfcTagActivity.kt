package com.fktm.app.nfc

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fktm.app.FktmApp
import com.fktm.app.ui.theme.FktmTheme
import com.water.widget.AccountStore
import com.water.widget.DeviceAddActivity
import com.water.widget.ConfigActivity
import com.water.widget.WaterService
import java.util.concurrent.Executors

enum class NfcTagMode {
    READ,
    WRITE,
}

data class NfcTagUiState(
    val nfcAvailable: Boolean,
    val nfcEnabled: Boolean,
    val mode: NfcTagMode = NfcTagMode.READ,
    val writeDeviceId: String = "",
    val deviceId: String? = null,
    val payload: String? = null,
    val status: String,
    val busy: Boolean = false,
    val autoStartEnabled: Boolean = false,
)

class NfcTagActivity : ComponentActivity(), NfcAdapter.ReaderCallback {

    companion object {
        const val EXTRA_RETURN_DEVICE_ID = "com.fktm.app.nfc.EXTRA_RETURN_DEVICE_ID"
        const val EXTRA_DEVICE_ID_RESULT = "com.fktm.app.nfc.EXTRA_DEVICE_ID_RESULT"
    }

    private val io = Executors.newSingleThreadExecutor()
    private var adapter: NfcAdapter? = null
    private var pendingWriteId: String? = null
    private val addDevice = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            startActivity(Intent(this, ConfigActivity::class.java))
        }
    }
    private var uiState by mutableStateOf(
        NfcTagUiState(
            nfcAvailable = true,
            nfcEnabled = false,
            status = "正在准备 NFC…",
            autoStartEnabled = (application as FktmApp).preferences.nfcAutoStartWater,
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adapter = NfcAdapter.getDefaultAdapter(this)
        uiState = uiState.copy(
            nfcAvailable = adapter != null,
            nfcEnabled = adapter?.isEnabled == true,
            status = when {
                adapter == null -> "这台设备不支持 NFC"
                adapter?.isEnabled != true -> "NFC 未开启，请在系统设置中打开"
                else -> "请将标签靠近手机背面"
            }
        )
        consumeIntent(intent)

        setContent {
            FktmTheme(themeMode = (application as FktmApp).preferences.themeMode) {
                NfcTagScreen(
                    state = uiState,
                    onClose = ::finish,
                    onReadMode = {
                        pendingWriteId = null
                        uiState = uiState.copy(
                            mode = NfcTagMode.READ,
                            status = "请将标签靠近手机背面",
                        )
                    },
                    onWriteDevice = { raw ->
                        val id = NfcTagCodec.extractDeviceId(raw)
                        if (id == null) {
                            toast("请输入有效的设备编号")
                        } else {
                            pendingWriteId = id
                            uiState = uiState.copy(
                                mode = NfcTagMode.WRITE,
                                writeDeviceId = id,
                                deviceId = id,
                                status = "请把 NfcA/Ndef 标签靠近手机写入",
                            )
                        }
                    },
                    onAddDevice = { id ->
                        addDevice.launch(
                            Intent(this, DeviceAddActivity::class.java)
                                .putExtra(DeviceAddActivity.EXTRA_DEVICE_ID, id)
                        )
                    },
                    onCopyDeviceId = { id ->
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("慧生活设备编号", id))
                        toast("设备编号已复制")
                    },
                    autoStartEnabled = uiState.autoStartEnabled,
                    onAutoStartChange = { enabled ->
                        (application as FktmApp).preferences.nfcAutoStartWater = enabled
                        uiState = uiState.copy(autoStartEnabled = enabled)
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        val nfc = adapter
        if (nfc?.isEnabled != true) {
            uiState = uiState.copy(
                nfcAvailable = nfc != null,
                nfcEnabled = false,
                status = if (nfc == null) "这台设备不支持 NFC" else "NFC 未开启，请在系统设置中打开",
            )
            return
        }
        uiState = uiState.copy(nfcAvailable = true, nfcEnabled = true)
        nfc.enableReaderMode(
            this,
            this,
            NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null,
        )
    }

    override fun onPause() {
        adapter?.disableReaderMode(this)
        super.onPause()
    }

    override fun onDestroy() {
        io.shutdownNow()
        super.onDestroy()
    }

    override fun onTagDiscovered(tag: Tag) {
        val requestedWriteId = pendingWriteId
        if (requestedWriteId != null) {
            writeTag(tag, requestedWriteId)
        } else {
            readTag(tag)
        }
    }

    private fun consumeIntent(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            NfcAdapter.ACTION_NDEF_DISCOVERED -> {
                val message = ndefMessages(intent).firstOrNull()
                showPayload(NfcTagCodec.extractText(message), fromSystemIntent = true)
            }

            NfcAdapter.ACTION_TECH_DISCOVERED,
            NfcAdapter.ACTION_TAG_DISCOVERED,
            -> tagFromIntent(intent)?.let(::readTag)
        }
    }

    private fun readTag(tag: Tag) {
        uiState = uiState.copy(busy = true, status = "正在读取标签…")
        io.execute {
            val ndef = Ndef.get(tag)
            val payload = try {
                if (ndef == null) {
                    null
                } else {
                    ndef.connect()
                    try {
                        NfcTagCodec.extractText(ndef.ndefMessage)
                    } finally {
                        ndef.close()
                    }
                }
            } catch (error: Exception) {
                error.message
            }
            val uid = tag.id.joinToString("") { "%02X".format(it) }
            runOnUiThread {
                if (payload.isNullOrBlank()) {
                    val knownId = uid.takeIf { it.length >= 8 }
                    uiState = uiState.copy(
                        busy = false,
                        deviceId = knownId ?: uiState.deviceId,
                        payload = null,
                        status = if (knownId == null) {
                            "未读取到 NDEF 内容"
                        } else {
                            "标签没有 NDEF，临时显示芯片 UID：$knownId"
                        },
                    )
                } else {
                    showPayload(payload, fromSystemIntent = false)
                }
            }
        }
    }

    private fun writeTag(tag: Tag, id: String) {
        uiState = uiState.copy(busy = true, status = "正在写入标签…")
        io.execute {
            val result = runCatching {
                val message = NfcTagCodec.messageForDevice(id)
                val ndef = Ndef.get(tag)
                if (ndef != null) {
                    ndef.connect()
                    try {
                        require(ndef.isWritable) { "标签已锁定，不能写入" }
                        ndef.writeNdefMessage(message)
                    } finally {
                        ndef.close()
                    }
                } else {
                    val formatable = NdefFormatable.get(tag)
                        ?: error("标签不支持 Ndef，也不是可格式化标签")
                    formatable.connect()
                    try {
                        formatable.format(message)
                    } finally {
                        formatable.close()
                    }
                }
            }
            runOnUiThread {
                result.onSuccess {
                    pendingWriteId = null
                    val uri = NfcTagCodec.tagUri(id)
                    uiState = uiState.copy(
                        busy = false,
                        deviceId = id,
                        payload = uri,
                        mode = NfcTagMode.READ,
                        status = "写入成功，再次碰一碰即可读取",
                    )
                }.onFailure {
                    uiState = uiState.copy(
                        busy = false,
                        status = "写入失败：${it.message ?: "标签不可写"}",
                    )
                }
            }
        }
    }

    private fun showPayload(raw: String?, fromSystemIntent: Boolean) {
        val payload = raw?.trim()?.takeIf(String::isNotEmpty)
        val id = NfcTagCodec.extractDeviceId(payload)
        if (
            id != null &&
            intent.getBooleanExtra(EXTRA_RETURN_DEVICE_ID, false)
        ) {
            setResult(
                RESULT_OK,
                Intent().putExtra(EXTRA_DEVICE_ID_RESULT, id),
            )
            finish()
            return
        }
        uiState = uiState.copy(
            busy = false,
            payload = payload ?: uiState.payload,
            deviceId = id ?: uiState.deviceId,
            status = when {
                id != null -> "已识别慧生活设备"
                payload == null -> if (fromSystemIntent) "未读取到 NDEF 内容" else "标签内容为空"
                else -> "已读取标签，但未识别设备编号"
            },
        )
        if (id != null && uiState.mode == NfcTagMode.READ) {
            tryAutoStartWater(id)
        }
    }

    private fun tryAutoStartWater(deviceId: String) {
        if (!uiState.autoStartEnabled) return
        val account = AccountStore.getCurrent(this) ?: return
        if (deviceId !in account.rememberedDevices()) return
        when (WaterService.start(this, deviceId, android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID)) {
            WaterService.StartResult.STARTED -> {
                uiState = uiState.copy(status = "已识别并启动默认饮水机")
            }
            WaterService.StartResult.ALREADY_RUNNING -> {
                uiState = uiState.copy(status = "饮水机已在运行")
            }
            WaterService.StartResult.FAILED -> Unit
        }
    }

    @Suppress("DEPRECATION")
    private fun ndefMessages(intent: Intent): List<NdefMessage> {
        val messages: Array<out NdefMessage>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, NdefMessage::class.java)
        } else {
            intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
                as? Array<NdefMessage>
        }
        return messages?.toList().orEmpty()
    }

    @Suppress("DEPRECATION")
    private fun tagFromIntent(intent: Intent): Tag? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        } else {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG) as? Tag
        }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
