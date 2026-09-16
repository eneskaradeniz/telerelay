package com.telerelay.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsViewModelLogicTest {

    @Test
    fun `auto start fires only on the ready transition with toggle on and service down`() {
        // Just became ready: Telegram configured after an early toggle-on.
        assertTrue(
            SettingsViewModel.shouldAutoStartCallMonitoring(
                previousReady = false, ready = true, callToggleOn = true, running = false,
            ),
        )
    }

    @Test
    fun `a deliberate stop is never overridden`() {
        // Ready stays true and the service is down because the user stopped it.
        assertFalse(
            SettingsViewModel.shouldAutoStartCallMonitoring(
                previousReady = true, ready = true, callToggleOn = true, running = false,
            ),
        )
    }

    @Test
    fun `already running service is not restarted`() {
        assertFalse(
            SettingsViewModel.shouldAutoStartCallMonitoring(
                previousReady = false, ready = true, callToggleOn = true, running = true,
            ),
        )
    }

    @Test
    fun `toggle off never starts`() {
        assertFalse(
            SettingsViewModel.shouldAutoStartCallMonitoring(
                previousReady = false, ready = true, callToggleOn = false, running = false,
            ),
        )
    }

    @Test
    fun `not ready never starts`() {
        assertFalse(
            SettingsViewModel.shouldAutoStartCallMonitoring(
                previousReady = false, ready = false, callToggleOn = true, running = false,
            ),
        )
    }
}
