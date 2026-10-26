package com.fktm.app.perfectcampus

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PerfectCampusUiState(
    val busy: String? = null,
    val message: String? = null,
    val rooms: List<PerfectCampusRoom> = emptyList(),
    val selectedRoom: PerfectCampusRoom? = null,
    val detail: PerfectCampusRoomDetail? = null,
    val queried: Boolean = false,
)

class PerfectCampusViewModel(app: Application) : AndroidViewModel(app) {

    private val api = PerfectCampusApi()
    private val _state = MutableStateFlow(PerfectCampusUiState())
    val state = _state.asStateFlow()

    fun queryRooms(customerCode: String, account: String) {
        if (customerCode.isBlank() || account.isBlank()) {
            _state.update { it.copy(message = "请填写学校代码和学号/工号") }
            return
        }
        viewModelScope.launch {
            _state.update {
                it.copy(
                    busy = "正在查询绑定房间",
                    message = null,
                    rooms = emptyList(),
                    selectedRoom = null,
                    detail = null,
                    queried = false,
                )
            }
            runCatching { api.boundRooms(customerCode, account) }
                .onSuccess { rooms ->
                    _state.update {
                        it.copy(
                            busy = null,
                            rooms = rooms,
                            queried = true,
                            message = if (rooms.isEmpty()) "没有查询到绑定房间" else null,
                        )
                    }
                }
                .onFailure(::handleFailure)
        }
    }

    fun loadRoom(customerCode: String, account: String, room: PerfectCampusRoom) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    busy = "正在查询房间水电",
                    message = null,
                    selectedRoom = room,
                    detail = null,
                )
            }
            runCatching { api.roomDetail(customerCode, account, room.verify) }
                .onSuccess { detail ->
                    _state.update { it.copy(busy = null, detail = detail) }
                }
                .onFailure(::handleFailure)
        }
    }

    fun closeRoom() {
        _state.update { it.copy(selectedRoom = null, detail = null) }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun handleFailure(error: Throwable) {
        _state.update {
            it.copy(
                busy = null,
                message = error.message?.takeIf(String::isNotBlank) ?: "完美校园查询失败",
            )
        }
    }
}
