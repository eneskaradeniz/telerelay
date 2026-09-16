package com.telerelay.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.telerelay.R

/**
 * Runtime permissions TeleRelay needs, grouped the way they are requested.
 * Each group carries its own user-facing rationale shown before the prompt.
 */
enum class PermissionGroup(
    val permissions: List<String>,
    @StringRes val titleRes: Int,
    @StringRes val rationaleRes: Int,
) {
    SMS(
        permissions = listOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS),
        titleRes = R.string.perm_group_sms_title,
        rationaleRes = R.string.perm_group_sms_rationale,
    ),
    PHONE_STATE(
        permissions = listOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_PHONE_NUMBERS,
        ),
        titleRes = R.string.perm_group_phone_title,
        rationaleRes = R.string.perm_group_phone_rationale,
    ),
    CALL_LOG(
        permissions = listOf(Manifest.permission.READ_CALL_LOG),
        titleRes = R.string.perm_group_calllog_title,
        rationaleRes = R.string.perm_group_calllog_rationale,
    ),
    NOTIFICATIONS(
        permissions = listOf(Manifest.permission.POST_NOTIFICATIONS),
        titleRes = R.string.perm_group_notifications_title,
        rationaleRes = R.string.perm_group_notifications_rationale,
    ),
    ;

    fun isGranted(context: Context): Boolean = permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    companion object {

        /** Groups the user still has to grant, in request order. */
        fun missing(context: Context): List<PermissionGroup> = entries
            .filter { it.isNotRequiredOnThisApi() || !it.isGranted(context) }

        private fun PermissionGroup.isNotRequiredOnThisApi() =
            this == NOTIFICATIONS && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
    }
}
