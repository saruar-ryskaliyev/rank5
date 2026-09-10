package io.rank5.app.game

/**
 * One-shot message for the snackbar. The producer decides whether it is an
 * error so the UI can pick the right sound without inspecting the wording.
 */
data class UiStatus(val text: String, val isError: Boolean) {
    companion object {
        fun info(text: String) = UiStatus(text, isError = false)
        fun error(text: String) = UiStatus(text, isError = true)
    }
}
