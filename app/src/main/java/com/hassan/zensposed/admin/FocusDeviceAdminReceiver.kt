package com.hassan.zensposed.admin

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context

class FocusDeviceAdminReceiver : DeviceAdminReceiver() {
    companion object {
        fun component(context: Context) =
            ComponentName(context, FocusDeviceAdminReceiver::class.java)
    }
}
