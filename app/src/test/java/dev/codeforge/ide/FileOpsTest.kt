package dev.codeforge.ide

import dev.codeforge.ide.core.FileOps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FileOpsTest {
    @Test fun quotesPlainNames() = assertEquals("'hello.py'", FileOps.shQuote("hello.py"))

    @Test fun quotesSpacesAndMetacharacters() =
        assertEquals("'a b; rm -rf \$HOME'", FileOps.shQuote("a b; rm -rf \$HOME"))

    @Test fun quotesEmbeddedSingleQuotes() = assertEquals("'it'\\''s.c'", FileOps.shQuote("it's.c"))

    @Test fun acceptsNormalNames() = assertNull(FileOps.validateName("main.cpp"))

    @Test fun rejectsSlashesAndDots() {
        assertNotNull(FileOps.validateName("a/b"))
        assertNotNull(FileOps.validateName(".."))
        assertNotNull(FileOps.validateName("   "))
    }
}
