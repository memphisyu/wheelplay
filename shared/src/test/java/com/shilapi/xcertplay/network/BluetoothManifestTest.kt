package com.shilapi.xcertplay.network

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class BluetoothManifestTest {
    @Test fun legacyBluetoothPermissionsRemainDeclaredAboveAndroid11AlongsideConnect() {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val permissions = factory.newDocumentBuilder().parse(File("src/main/AndroidManifest.xml"))
            .getElementsByTagName("uses-permission")
        val android = "http://schemas.android.com/apk/res/android"
        val entries = (0 until permissions.length).map { permissions.item(it) as org.w3c.dom.Element }
        for (permission in listOf("BLUETOOTH", "BLUETOOTH_ADMIN", "BLUETOOTH_CONNECT")) {
            val entry = entries.single { it.getAttributeNS(android, "name") == "android.permission.$permission" }
            assertFalse("MIUI 13 checks legacy Bluetooth permissions on Android 12",
                entry.hasAttributeNS(android, "maxSdkVersion"))
        }
    }
}
