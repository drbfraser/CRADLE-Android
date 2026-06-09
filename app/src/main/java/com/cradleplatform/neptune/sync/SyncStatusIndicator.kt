package com.cradleplatform.neptune.sync

import android.view.MenuItem
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.cradleplatform.neptune.R

fun AppCompatActivity.bindSyncStatusIndicator(
    syncStatusManager: SyncStatusManager,
    menuItem: MenuItem
) {
    syncStatusManager.status.observe(this) { status -> applySyncStatus(menuItem, status) }
    lifecycle.addObserver(
        LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) syncStatusManager.refresh()
        }
    )
}

private fun AppCompatActivity.applySyncStatus(menuItem: MenuItem, status: SyncStatus) {
    when (status.state) {
        SyncState.SYNCING -> {
            menuItem.title = getString(R.string.sync_status_syncing)
            showSpinner(menuItem)
        }
        SyncState.OFFLINE -> {
            stopSpinner(menuItem)
            menuItem.setIcon(R.drawable.ic_status_offline)
            menuItem.title = getString(R.string.status_offline)
        }
        SyncState.FAILED -> {
            stopSpinner(menuItem)
            menuItem.setIcon(R.drawable.ic_status_error)
            menuItem.title = getString(R.string.sync_status_failed, status.lastFailedSyncDate)
        }
        SyncState.UNSYNCED_CHANGES -> {
            stopSpinner(menuItem)
            menuItem.setIcon(R.drawable.ic_status_unsynced)
            menuItem.title = getString(R.string.sync_status_unsynced)
        }
        SyncState.UP_TO_DATE -> {
            stopSpinner(menuItem)
            menuItem.setIcon(R.drawable.ic_status_synced)
            menuItem.title = getString(R.string.status_online)
        }
    }
}

private fun AppCompatActivity.showSpinner(menuItem: MenuItem) {
    if (menuItem.actionView != null) return
    val spinner = ImageView(this).apply {
        setImageResource(R.drawable.ic_baseline_sync_24_white)
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT
        )
        val padding = (resources.displayMetrics.density * SPINNER_PADDING_DP).toInt()
        setPadding(padding, padding, padding, padding)
        contentDescription = getString(R.string.sync_status_syncing)
    }
    menuItem.actionView = spinner
    spinner.startAnimation(AnimationUtils.loadAnimation(this, R.anim.rotate_sync))
}

private fun stopSpinner(menuItem: MenuItem) {
    menuItem.actionView?.clearAnimation()
    menuItem.actionView = null
}

private const val SPINNER_PADDING_DP = 12f
