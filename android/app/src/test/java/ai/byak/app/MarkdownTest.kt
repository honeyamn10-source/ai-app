package ai.byak.app

import ai.byak.app.ui.MdBlock
import ai.byak.app.ui.parseMarkdown
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownTest {
    @Test fun parsesCommonBlocks() {
        val blocks = parseMarkdown("# Title\n\nSome **bold** text\nsecond line\n\n- one\n2. two\n\n```kotlin\nval x = 1\n```")
        assertEquals(
            listOf(
                MdBlock.Heading(1, "Title"), MdBlock.Paragraph("Some **bold** text\nsecond line"),
                MdBlock.Bullet("•", "one"), MdBlock.Bullet("2.", "two"), MdBlock.Code("kotlin", "val x = 1")
            ),
            blocks
        )
    }

    @Test fun unterminatedCodeFenceKeepsContent() {
        assertEquals(listOf(MdBlock.Code("", "partial")), parseMarkdown("```\npartial"))
    }
}
