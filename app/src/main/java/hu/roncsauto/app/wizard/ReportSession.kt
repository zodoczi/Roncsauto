package hu.roncsauto.app.wizard

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import hu.roncsauto.app.inspection.InspectionExpiry
import hu.roncsauto.app.plate.HungarianPlate
import java.time.LocalDate

/**
 * A varázsló állapota. Forgásnál a SavedStateHandle tartja meg.
 */
class ReportSession(private val state: SavedStateHandle) : ViewModel() {

    var step: WizardStep
        get() = WizardStep.valueOf(state.get<String>(KEY_STEP) ?: WizardStep.PLATE.name)
        set(value) { state[KEY_STEP] = value.name }

    var plate: String?
        get() = state[KEY_PLATE]
        private set(value) { state[KEY_PLATE] = value }

    var expiry: LocalDate?
        get() = state.get<String>(KEY_EXPIRY)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        private set(value) { state[KEY_EXPIRY] = value?.toString() }

    var vehicle: String?
        get() = state[KEY_VEHICLE]
        private set(value) { state[KEY_VEHICLE] = value }

    var photoMode: Boolean
        get() = state.get<Boolean>(KEY_PHOTO_MODE) == true
        set(value) { state[KEY_PHOTO_MODE] = value }

    var photoSaved: Boolean
        get() = state.get<Boolean>(KEY_PHOTO_SAVED) == true
        set(value) { state[KEY_PHOTO_SAVED] = value }

    fun setPlate(newPlate: String?, allowClear: Boolean) {
        val resolved = newPlate?.let { HungarianPlate.findBest(it)?.canonical }
        if (resolved == null && !allowClear) return
        expiry = InspectionExpiry.retainForPlate(plate, resolved, expiry)
        if (expiry == null) vehicle = null
        plate = resolved
        if (resolved == null) photoSaved = false
    }

    fun setInspection(date: LocalDate?, vehicleDescription: String?) {
        expiry = date
        vehicle = vehicleDescription
    }

    fun clearInspection() {
        expiry = null
        vehicle = null
    }

    fun goNext() {
        step = step.next() ?: return
    }

    fun goBack() {
        step = step.previous() ?: return
    }

    fun restart() {
        step = WizardStep.PLATE
        plate = null
        expiry = null
        vehicle = null
        photoMode = false
        photoSaved = false
    }

    fun canAdvanceFromPlate(): Boolean = plate != null

    companion object {
        private const val KEY_STEP = "step"
        private const val KEY_PLATE = "plate"
        private const val KEY_EXPIRY = "expiry"
        private const val KEY_VEHICLE = "vehicle"
        private const val KEY_PHOTO_MODE = "photoMode"
        private const val KEY_PHOTO_SAVED = "photoSaved"
    }
}
