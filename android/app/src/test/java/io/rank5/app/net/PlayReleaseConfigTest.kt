package io.rank5.app.net

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PlayReleaseConfigTest {
    @Test
    fun releaseNetworkConfigForbidsCleartext() {
        val xml = File("src/release/res/xml/network_security_config.xml").takeIf { it.exists() }
            ?: File("src/main/res/xml/network_security_config.xml")
        val text = xml.readText()
        assertFalse(text.contains("cleartextTrafficPermitted=\"true\""))
        assertTrue(text.contains("cleartextTrafficPermitted=\"false\""))
    }

    @Test
    fun debugNetworkConfigAllowsEmulatorLoopback() {
        val text = File("src/debug/res/xml/network_security_config.xml").readText()
        assertTrue(text.contains("10.0.2.2"))
        assertTrue(text.contains("cleartextTrafficPermitted=\"true\""))
    }

    @Test
    fun backupRulesExcludeEverything() {
        val extraction = File("src/main/res/xml/data_extraction_rules.xml").readText()
        val backup = File("src/main/res/xml/backup_rules.xml").readText()
        assertTrue(extraction.contains("data-extraction-rules"))
        assertTrue(extraction.contains("<exclude"))
        assertTrue(backup.contains("full-backup-content"))
        assertTrue(backup.contains("<exclude"))
    }
}
