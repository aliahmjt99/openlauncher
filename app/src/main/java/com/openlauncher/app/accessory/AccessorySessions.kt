package com.openlauncher.app.accessory

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

internal class AccessorySessions {
    private class Session<T>(val updates: Flow<T>, val label: String)

    private var obdSession by mutableStateOf<Session<ObdSnapshot>?>(null)
    private var tireSession by mutableStateOf<Session<List<TimedTireReading>>?>(null)
    val obdKey: Any? get() = obdSession
    val tireKey: Any? get() = tireSession

    var obd by mutableStateOf<ObdSnapshot?>(null)
        private set
    var tires by mutableStateOf<List<TimedTireReading>>(emptyList())
        private set
    var obdStatus by mutableStateOf("No OBD-II adapter connected")
        private set
    var tireStatus by mutableStateOf("No TPMS receiver connected")
        private set
    val obdRunning: Boolean get() = obdSession != null
    val tiresRunning: Boolean get() = tireSession != null
    val obdUsesLocalAdapter: Boolean get() = obdSession?.label?.startsWith("Wi-Fi") == false

    fun startObd(updates: Flow<ObdSnapshot>, label: String) {
        obd = null
        obdStatus = "Connecting · $label"
        obdSession = Session(updates, label)
    }

    fun startTires(updates: Flow<List<TimedTireReading>>, label: String) {
        tires = emptyList()
        tireStatus = "Connecting · $label"
        tireSession = Session(updates, label)
    }

    fun stopObd() {
        obdSession = null
        obd = null
        obdStatus = "OBD-II stopped"
    }

    fun stopTires() {
        tireSession = null
        tires = emptyList()
        tireStatus = "TPMS stopped"
    }

    suspend fun observeObd() {
        val session = obdSession ?: return
        obd = null
        obdStatus = "Connecting · ${session.label}"
        try {
            session.updates.collect {
                if (obdSession === session) {
                    obd = it
                    obdStatus = session.label
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            if (obdSession === session) {
                obdSession = null
                obd = null
                obdStatus = "OBD-II disconnected · ${session.label}"
            }
        } finally {
            if (obdSession === session) {
                obd = null
                obdStatus = "OBD-II disconnected · ${session.label}"
            }
        }
    }

    suspend fun observeTires() {
        val session = tireSession ?: return
        tires = emptyList()
        tireStatus = "Waiting for sensors · ${session.label}"
        try {
            session.updates.collect {
                if (tireSession === session) {
                    tires = it
                    tireStatus = if (it.isEmpty()) "Waiting for sensors · ${session.label}" else session.label
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            if (tireSession === session) {
                tireSession = null
                tires = emptyList()
                tireStatus = "TPMS disconnected · ${session.label}"
            }
        } finally {
            if (tireSession === session) {
                tires = emptyList()
                tireStatus = "TPMS disconnected · ${session.label}"
            }
        }
    }
}
