package com.ykatchou.ylauncher.data.running

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecentTasksParserTest {

    /** Captured from `dumpsys activity recents` on a BV9300 Pro running Android 15. */
    private val dump = """
        Recent tasks:
          * Recent #0: Task{eb9eba0 #1444 type=home I=com.ykatchou.ylauncher/.MainActivity}
            baseIntent=Intent { act=android.intent.action.MAIN }
          * Recent #1: Task{f85f386 #1456 type=recents I=com.blackview.launcher/com.android.quickstep.RecentsActivity}
          * Recent #2: Task{d10633a #1498 type=standard A=10384:com.facebook.katana}
            baseIntent=Intent { flg=0x14000000 pkg=com.facebook.katana }
          * Recent #3: Task{cbc1083 #1460 type=standard A=10243:com.instagram.android}
          * Recent #4: Task{b28d33d #1482 type=standard A=10155:com.android.chrome}
    """.trimIndent()

    @Test
    fun `lists only real apps, skipping the launcher and the recents handler`() {
        assertEquals(
            listOf("com.facebook.katana", "com.instagram.android", "com.android.chrome"),
            RecentTasksParser.packages(dump),
        )
    }

    /**
     * Regression: `#(\d+)` unanchored matches the "Recent #2" list position before the task id,
     * so closing Facebook asked the system to remove task 2 — some unrelated app's task, while
     * Facebook stayed in the column as a ghost.
     */
    @Test
    fun `reads the task id from the Task block, not the list position`() {
        assertEquals("1498", RecentTasksParser.taskIdOf(dump, "com.facebook.katana"))
        assertEquals("1460", RecentTasksParser.taskIdOf(dump, "com.instagram.android"))
        assertEquals("1482", RecentTasksParser.taskIdOf(dump, "com.android.chrome"))
    }

    @Test
    fun `returns no task id for an app that is not open`() {
        assertNull(RecentTasksParser.taskIdOf(dump, "com.spotify.music"))
    }

    @Test
    fun `survives empty output rather than throwing`() {
        assertEquals(emptyList<String>(), RecentTasksParser.packages(""))
        assertNull(RecentTasksParser.taskIdOf("", "com.whatsapp"))
    }
}
