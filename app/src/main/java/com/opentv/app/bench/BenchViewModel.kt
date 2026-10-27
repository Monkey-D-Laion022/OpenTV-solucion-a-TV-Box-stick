package com.opentv.app.bench

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.StateFlow

class BenchViewModel(application: Application) : AndroidViewModel(application) {
    private val lookup = AndroidEncoderLookup()

    val ui: StateFlow<BenchUi> get() = BenchHub.ui

    /** Configuraciones que el codificador informa como compatibles. */
    val options: List<BenchPlan.Option> by lazy { BenchPlan.compatible(lookup) }

    /** Las que NO admite (no se prueban). */
    val unsupported: List<BenchConfig> by lazy { BenchPlan.unsupported(lookup) }

    fun start(resultCode: Int, data: Intent, selected: List<BenchPlan.Option>) {
        BenchHub.start(getApplication(), resultCode, data, selected)
    }

    fun stop() {
        BenchHub.stop()
    }

    /** Al salir de la pantalla se corta todo y se libera. */
    override fun onCleared() {
        BenchHub.stop()
        super.onCleared()
    }
}
