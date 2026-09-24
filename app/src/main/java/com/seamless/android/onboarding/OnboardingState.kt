package com.seamless.android.onboarding

enum class OnboardingState {
    WELCOME,
    PERMISSION_REQUEST,
    PERMISSION_SUCCESS,
    INITIALIZING,
    COMPLETE,
    ERROR,
    // Legacy states (kept for compatibility)
    CHECKING,
    PERMISSION_EXPLANATION,
    BLUETOOTH_CHECK,
    LOCATION_CHECK,
    BATTERY_OPTIMIZATION_CHECK,
    PERMISSION_REQUESTING
}