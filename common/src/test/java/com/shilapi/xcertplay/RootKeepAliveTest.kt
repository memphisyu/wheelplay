package com.shilapi.xcertplay

import org.junit.Assert.*
import org.junit.Test

class RootKeepAliveTest {
    @Test fun exemptionMatchesExactPackageOnly() {
        val listing = "system,android,1000\nuser,moe.feng.wheelplay.xpengaudio,10123\n"
        assertTrue(RootKeepAlive.isListed(listing, "moe.feng.wheelplay.xpengaudio"))
        assertFalse(RootKeepAlive.isListed(listing, "moe.feng.wheelplay"))
        assertFalse(RootKeepAlive.isListed(listing, "xpengaudio"))
    }
    @Test fun commandIsScopedAndReversible() {
        assertEquals("cmd deviceidle whitelist +moe.feng.wheelplay.xpengaudio", RootKeepAlive.command("moe.feng.wheelplay.xpengaudio", true))
        assertEquals("cmd deviceidle whitelist -moe.feng.wheelplay.xpengaudio", RootKeepAlive.command("moe.feng.wheelplay.xpengaudio", false))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsShellInjection() {
        RootKeepAlive.command("foo; reboot", true)
    }
}
