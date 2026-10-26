package com.fktm.app.pangguai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PangguaiUiState(
    val token: String? = null,
    val mobile: String = "",
    val user: PangguaiUser? = null,
    val codeSent: Boolean = false,
    val busy: String? = null,
    val message: String? = null,
    val recentDevices: List<PangguaiRecentDevice> = emptyList(),
    val selectedDevice: PangguaiDevice? = null,
    val skus: List<PangguaiSku> = emptyList(),
    val orders: List<PangguaiOrder> = emptyList(),
    val pendingNqt: String? = null,
) {
    val loggedIn: Boolean get() = !token.isNullOrBlank()
}

class PangguaiViewModel(app: Application) : AndroidViewModel(app) {

    private val store = PangguaiStore(app)
    private val api = PangguaiApi()
    private val _state = MutableStateFlow(
        PangguaiUiState(
            token = store.currentToken(),
            mobile = store.currentMobile().orEmpty(),
        )
    )
    val state = _state.asStateFlow()

    init {
        if (_state.value.loggedIn) refreshAll()
    }

    fun sendCode() {
        val mobile = _state.value.mobile
        if (mobile.length != 11) {
            _state.update { it.copy(message = "请输入 11 位手机号") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = "正在发送验证码", message = null) }
            runCatching { api.sendSmsCode(mobile) }
                .onSuccess {
                    _state.update {
                        it.copy(
                            busy = null,
                            codeSent = true,
                            message = "验证码已发送",
                        )
                    }
                }
                .onFailure(::handleFailure)
        }
    }

    fun login(verify: String) {
        val mobile = _state.value.mobile
        if (mobile.length != 11 || verify.length < 4) {
            _state.update { it.copy(message = "请填写手机号和短信验证码") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = "正在登录胖乖生活", message = null) }
            runCatching { api.login(mobile, verify) }
                .onSuccess { token ->
                    store.saveSession(token, mobile)
                    _state.update {
                        it.copy(
                            token = token,
                            busy = null,
                            codeSent = false,
                            message = "登录成功",
                        )
                    }
                    refreshAll()
                    _state.value.pendingNqt?.let(::scanNow)
                }
                .onFailure(::handleFailure)
        }
    }

    fun onMobileChange(value: String) {
        val mobile = value.filter(Char::isDigit).take(11)
        _state.update { it.copy(mobile = mobile) }
    }

    fun refreshAll() {
        val token = _state.value.token ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = "正在同步胖乖生活", message = null) }
            runCatching {
                val user = api.userInfo(token)
                val recent = api.recentDevices(token)
                val orders = api.orders(token)
                Triple(user, recent, orders)
            }.onSuccess { (user, recent, orders) ->
                _state.update {
                    it.copy(
                        busy = null,
                        user = user,
                        recentDevices = recent,
                        orders = orders,
                    )
                }
            }.onFailure(::handleFailure)
        }
    }

    fun onScanned(raw: String?) {
        val nqt = PangguaiApi.extractNqt(raw)
        if (nqt.isNullOrBlank()) {
            _state.update {
                it.copy(message = "没有识别到胖乖设备码 NQT")
            }
            return
        }
        _state.update {
            it.copy(
                pendingNqt = nqt,
                message = if (it.loggedIn) null else "登录后将自动打开该设备",
            )
        }
        if (_state.value.loggedIn) scanNow(nqt)
    }

    fun openDevice(goodsId: Int) {
        val token = _state.value.token ?: return
        if (goodsId <= 0) return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    busy = "正在读取设备",
                    selectedDevice = null,
                    skus = emptyList(),
                    message = null,
                )
            }
            runCatching {
                api.deviceDetail(goodsId, token) to api.deviceSkus(goodsId, token)
            }.onSuccess { (device, skus) ->
                _state.update {
                    it.copy(
                        busy = null,
                        selectedDevice = device,
                        skus = skus,
                        pendingNqt = null,
                    )
                }
            }.onFailure(::handleFailure)
        }
    }

    fun closeDevice() {
        _state.update { it.copy(selectedDevice = null, skus = emptyList()) }
    }

    fun logout() {
        store.clearSession()
        _state.value = PangguaiUiState(
            mobile = _state.value.mobile,
            message = "已退出胖乖生活账号",
        )
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun scanNow(nqt: String) {
        val token = _state.value.token ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = "正在识别设备", message = null) }
            runCatching { api.scanDevice(nqt, token) }
                .onSuccess { goodsId ->
                    _state.update { it.copy(busy = null, pendingNqt = null) }
                    openDevice(goodsId)
                }
                .onFailure(::handleFailure)
        }
    }

    private fun handleFailure(error: Throwable) {
        val apiError = error as? PangguaiApiException
        if (apiError?.code == 2) {
            store.clearSession()
            _state.update {
                it.copy(
                    token = null,
                    user = null,
                    busy = null,
                    recentDevices = emptyList(),
                    orders = emptyList(),
                    message = "登录已失效，请重新登录",
                )
            }
            return
        }
        _state.update {
            it.copy(
                busy = null,
                message = error.message?.takeIf(String::isNotBlank) ?: "网络操作失败",
            )
        }
    }
}
