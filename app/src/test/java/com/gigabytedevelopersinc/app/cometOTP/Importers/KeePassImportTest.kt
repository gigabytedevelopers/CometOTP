@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Importers

import com.gigabytedevelopersinc.app.cometOTP.Database.Entry
import com.gigabytedevelopersinc.app.cometOTP.Utilities.TokenCalculator.HashAlgorithm
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class KeePassImportTest {

    private fun assertFails(kind: ImportException.Kind, text: String) {
        try {
            KeePassImport.read(text.toByteArray(), null)
            fail("Expected $kind")
        } catch (e: ImportException) {
            assertEquals(kind, e.kind)
        }
    }

    @Test
    fun readsAnXmlExport() {
        val tokens = KeePassImport.read(EXPORT.toByteArray(), null)
        // Entries with no one-time password, and earlier versions kept in History, are passed over.
        assertEquals(4, tokens.size)

        val t = tokens[0]
        assertEquals(Entry.OTPType.TOTP, t.type)
        assertArrayEquals("Hello!Þ­¾ï".toByteArray(Charsets.ISO_8859_1), t.secret)
        assertEquals("GitHub", t.issuer)
        assertEquals("bob", t.label)
        assertEquals(HashAlgorithm.SHA1, t.algorithm)

        // KeeOTP's seed and settings, in a nested group.
        assertEquals("Old Plugin", tokens[1].issuer)
        assertEquals("carol", tokens[1].label)
        assertArrayEquals("Hello!".toByteArray(), tokens[1].secret)
        assertEquals(60, tokens[1].period)
        assertEquals(8, tokens[1].digits)

        assertEquals(Entry.OTPType.STEAM, tokens[2].type)
        assertEquals(5, tokens[2].digits)

        // A link with no issuer takes the entry's title.
        assertEquals("Fallback", tokens[3].issuer)
        assertEquals("dan", tokens[3].label)

        assertEquals(4, TokenImport.convert(tokens).entries.size)
    }

    @Test
    fun refusesWhatIsNotAKeePassExport() {
        assertFails(ImportException.Kind.NOT_THIS_FORMAT, "otpauth://totp/a?secret=JBSWY3DPEE")
        assertFails(ImportException.Kind.NOT_THIS_FORMAT, """{"services":[]}""")
        assertFails(ImportException.Kind.NOT_THIS_FORMAT, "<html><body>KeePass</body></html>")
        assertFails(ImportException.Kind.DAMAGED, "<KeePassFile><Root><Group><Entry></Group></Root>")
        assertFails(ImportException.Kind.EMPTY, "<KeePassFile><Root><Group><Name>Root</Name><Entry><String><Key>Title</Key><Value>x</Value></String></Entry></Group></Root></KeePassFile>")
    }

    @Test
    fun doesNotFollowExternalEntities() {
        val xml = """<?xml version="1.0"?><!DOCTYPE KeePassFile [<!ENTITY x SYSTEM "file:///etc/hostname">]>
            <KeePassFile><Root><Group><Entry><String><Key>Title</Key><Value>&x;</Value></String>
            <String><Key>otp</Key><Value>otpauth://totp/?secret=JBSWY3DPEE</Value></String></Entry></Group></Root></KeePassFile>"""
        val title = try {
            KeePassImport.read(xml.toByteArray(), null).single().issuer
        } catch (e: ImportException) {
            // Refusing the doctype outright is fine too.
            assertEquals(ImportException.Kind.DAMAGED, e.kind)
            ""
        }
        assertEquals("", title)
    }

    companion object {
        private const val EXPORT = """<?xml version="1.0" encoding="utf-8" standalone="yes"?>
<KeePassFile>
  <Meta><Generator>KeePassXC</Generator></Meta>
  <Root>
    <Group>
      <UUID>AAAA</UUID><Name>Root</Name>
      <Entry>
        <UUID>BBBB</UUID>
        <String><Key>Title</Key><Value>GitHub</Value></String>
        <String><Key>UserName</Key><Value>bob</Value></String>
        <String><Key>Password</Key><Value ProtectInMemory="True">hunter2</Value></String>
        <String><Key>otp</Key><Value ProtectInMemory="True">otpauth://totp/GitHub:bob?secret=JBSWY3DPEHPK3PXP&amp;issuer=GitHub&amp;period=30&amp;digits=6</Value></String>
        <History>
          <Entry>
            <String><Key>Title</Key><Value>GitHub old</Value></String>
            <String><Key>otp</Key><Value>otpauth://totp/GitHub:old?secret=JBSWY3DPEE&amp;issuer=GitHub</Value></String>
          </Entry>
        </History>
      </Entry>
      <Entry>
        <UUID>CCCC</UUID>
        <String><Key>Title</Key><Value>No code</Value></String>
        <String><Key>UserName</Key><Value>nobody</Value></String>
      </Entry>
      <Group>
        <UUID>DDDD</UUID><Name>Plugins</Name>
        <Entry>
          <String><Key>Title</Key><Value>Old Plugin</Value></String>
          <String><Key>UserName</Key><Value>carol</Value></String>
          <String><Key>TOTP Seed</Key><Value>JBSWY3DPEE</Value></String>
          <String><Key>TOTP Settings</Key><Value>60;8</Value></String>
        </Entry>
        <Entry>
          <String><Key>Title</Key><Value>Steam</Value></String>
          <String><Key>UserName</Key><Value>gamer</Value></String>
          <String><Key>TOTP Seed</Key><Value>JBSWY3DPEE</Value></String>
          <String><Key>TOTP Settings</Key><Value>30;S</Value></String>
        </Entry>
        <Group>
          <Name>Deeper</Name>
          <Entry>
            <String><Key>Title</Key><Value>Fallback</Value></String>
            <String><Key>UserName</Key><Value>dan</Value></String>
            <String><Key>otp</Key><Value>otpauth://totp/?secret=JBSWY3DPEE</Value></String>
          </Entry>
        </Group>
      </Group>
    </Group>
  </Root>
</KeePassFile>"""
    }
}
