package ai.byak.app

import android.graphics.Bitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Drives the real app on an emulator the way a person would (UiAutomator, no test hooks):
 * opens every screen, tries the Connect dialog with a wrong key, and chats with Pollinations Free.
 * Saves a screenshot per step plus report.txt to the app's external files ("walkthrough"), which CI uploads.
 * Problems are collected and reported together at the end, so one broken screen doesn't hide the others.
 */
@RunWith(AndroidJUnit4::class)
class AppWalkthroughTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val out = File(instrumentation.targetContext.getExternalFilesDir(null), "walkthrough").apply { deleteRecursively(); mkdirs() }
    private val problems = mutableListOf<String>()
    private val log = StringBuilder()
    private var step = 0

    private fun note(line: String) { log.append(line).append('\n'); println("WALKTHROUGH: $line") }
    private fun shot(name: String) {
        Thread.sleep(700)
        val file = File(out, "%02d-%s.png".format(++step, name))
        instrumentation.uiAutomation.takeScreenshot()?.let { bmp -> file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) } }
        note("screenshot ${file.name}")
    }
    private fun find(selector: BySelector, timeout: Long = 8_000): UiObject2? = device.wait(Until.findObject(selector), timeout)
    private fun text(t: String) = By.textContains(t)
    private fun problem(what: String) { problems += what; note("PROBLEM: $what") }
    /** Every text and label currently on screen, so a failure can be understood from the log alone. */
    private fun screenText(): String = device.findObjects(By.pkg(instrumentation.targetContext.packageName))
        .mapNotNull { o -> listOfNotNull(o.text, o.contentDescription).joinToString(" / ").takeIf { it.isNotBlank() } }.distinct().joinToString(" | ").take(3_000)
    private fun crashed(): Boolean = device.currentPackageName != instrumentation.targetContext.packageName
    private fun relaunchIfCrashed(where: String) {
        if (!crashed()) return
        problem("app left the foreground (crash?) after: $where")
        ActivityScenario.launch(MainActivity::class.java); device.wait(Until.hasObject(By.pkg(instrumentation.targetContext.packageName)), 15_000)
    }
    private fun openFromDrawer(title: String) {
        val menu = find(By.desc("Menu"), 4_000) ?: return problem("no Menu button when opening $title")
        menu.click()
        var item = find(By.text(title), 3_000)
        // Lower entries need a scroll on small screens, as a person would do.
        var swipes = 0
        while (item == null && swipes++ < 4) { device.swipe(device.displayWidth / 4, device.displayHeight * 3 / 4, device.displayWidth / 4, device.displayHeight / 4, 20); item = find(By.text(title), 1_500) }
        if (item == null) { problem("drawer has no '$title' (even after scrolling)"); device.pressBack(); return }
        item.click()
    }

    @Test fun walkThroughTheApp() {
        ActivityScenario.launch(MainActivity::class.java)
        if (find(text("Good to see you"), 30_000) == null) problem("Home did not appear within 30 s (no greeting)")
        shot("home")

        for (tab in listOf("Chats", "Research", "Files", "Settings", "Home")) {
            val item = find(By.text(tab), 5_000)
            if (item == null) { problem("bottom tab '$tab' not found"); continue }
            item.click(); shot("tab-${tab.lowercase()}"); relaunchIfCrashed("tab $tab")
        }

        for (title in listOf("Projects", "Prompts", "Compare", "Models", "BYAK Pro", "Memory", "Usage")) {
            openFromDrawer(title); shot("screen-${title.lowercase().replace(' ', '-')}"); relaunchIfCrashed("screen $title")
        }

        // Connect dialog: a wrong OpenRouter key must be rejected with a clear message.
        openFromDrawer("Models")
        val connect = find(text("Connect"), 5_000)
        if (connect == null) problem("Models has no Connect button") else {
            connect.click(); shot("connect-dialog")
            val keyField = find(By.clazz("android.widget.EditText"), 5_000)
            if (keyField == null) problem("Connect dialog has no key field") else {
                // A made-up key in OpenRouter's format, built at runtime so it never looks like a committed secret.
                keyField.text = listOf("sk", "or", "v1", "0".repeat(64)).joinToString("-")
                if (find(text("rejected"), 20_000) == null && find(text("Key works"), 1_000) == null) problem("Connect dialog gave no verdict for a fake OpenRouter key within 20 s")
                shot("connect-wrong-key")
            }
            find(By.text("Cancel"), 3_000)?.click() ?: device.pressBack()
        }

        // Free chat with no key: Home → Chat now → send a message → an answer (or a visible reason) must appear.
        find(By.text("Home"), 5_000)?.click()
        val chatNow = find(text("Chat now"), 8_000)
        if (chatNow == null) problem("Home has no 'Chat now (online, free)' button") else {
            chatNow.click()
            val composer = find(By.clazz("android.widget.EditText"), 15_000)
            if (composer == null) problem("no chat composer after 'Chat now'") else {
                composer.text = "Say hello in five words."
                shot("chat-typed")
                val send = find(By.desc("Send"), 5_000)
                if (send == null) problem("no Send button") else {
                    send.click()
                    // Done when the Send button is back (Stop disappears) or an error bubble shows.
                    val start = System.currentTimeMillis()
                    while (System.currentTimeMillis() - start < 120_000 && find(By.desc("Send"), 2_000) == null && !device.hasObject(text("No answer"))) Thread.sleep(1_000)
                    Thread.sleep(1_500)
                    shot("chat-answer")
                    note("chat screen text: ${screenText()}")
                    if (device.hasObject(text("No answer"))) {
                        val reason = device.findObjects(By.textContains(":")).joinToString(" | ") { it.text ?: "" }.take(600)
                        problem("free chat failed: $reason")
                    } else if (!device.hasObject(text("BYAK AI · "))) problem("no answer bubble after sending")
                }
            }
        }
        relaunchIfCrashed("chat")

        File(out, "report.txt").writeText(log.toString() + "\n" + if (problems.isEmpty()) "NO PROBLEMS FOUND\n" else "PROBLEMS:\n" + problems.joinToString("\n") { "- $it" } + "\n")
        if (problems.isNotEmpty()) fail("Walkthrough found ${problems.size} problem(s):\n" + problems.joinToString("\n"))
    }
}
