package com.naury.voxerartc.showcase

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naury.voxerartc.core.VoxeraSession
import com.naury.voxerartc.testkit.DemoScenario
import com.naury.voxerartc.testkit.VoxeraDemo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 진행 중인 데모 세션. 화면 회전으로 Activity가 다시 만들어져도 세션과 통화 상태를 유지한다. */
class ShowcaseViewModel : ViewModel() {

    data class ActiveDemo(val scenario: DemoScenario, val session: VoxeraSession)

    private val _active = MutableStateFlow<ActiveDemo?>(null)
    val active: StateFlow<ActiveDemo?> = _active.asStateFlow()

    fun start(scenario: DemoScenario) {
        _active.value?.session?.close()
        val session = VoxeraDemo.createFakeSession(scenario)
        _active.value = ActiveDemo(scenario, session)
        join(session, scenario)
    }

    /** 같은 세션으로 다시 입장한다. 끝난 세션은 새 세대로 연결된다. */
    fun rejoin() {
        val current = _active.value ?: return
        join(current.session, current.scenario)
    }

    fun exit() {
        _active.value?.session?.close()
        _active.value = null
    }

    private fun join(session: VoxeraSession, scenario: DemoScenario) {
        viewModelScope.launch { session.join(VoxeraDemo.ROOM_ID, scenario.mode) }
    }

    override fun onCleared() {
        _active.value?.session?.close()
    }
}
