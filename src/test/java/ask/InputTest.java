package ask;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests 5-8: the input box rules, two messages in a row, and the AI quality check. */
public class InputTest {

    private Playwright playwright;
    private Browser browser;
    private BrowserContext context;
    private Page page;
    private ChatPage chat;

    /** Runs once: start the browser. Use -Dheadless=true to hide it. */
    @BeforeClass
    public void startBrowser() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(
                new BrowserType.LaunchOptions().setHeadless(Boolean.getBoolean("headless")));
    }

    /** Runs once at the end: close the browser. */
    @AfterClass
    public void stopBrowser() {
        browser.close();
        playwright.close();
    }

    /** Runs before every test: a fresh tab, so each test starts a new chat. */
    @BeforeMethod
    public void openPage() {
        context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1440, 900));
        page = context.newPage();
        chat = new ChatPage(page);
        chat.open();
    }

    /** Runs after every test: close the tab. */
    @AfterMethod
    public void closePage() {
        context.close();
    }

    /** Test 5: Shift+Enter adds a new line, it does not send the message. */
    @Test
    public void shiftEnterCreatesNewline() {
        int messagesBefore = chat.agentMessageCount();

        chat.typeWithShiftEnter("first line", "second line");

        assertThat(chat.inputValue()).as("no new line was added").contains("\n");
        assertThat(chat.agentMessageCount()).as("the message was sent").isEqualTo(messagesBefore);
    }

    /** Test 6: pressing Enter with an empty box sends nothing. */
    @Test
    public void emptyInputDoesNotSubmit() {
        int messagesBefore = chat.agentMessageCount();

        chat.pressEnterOnEmptyInput();

        assertThat(chat.agentMessageCount()).as("an empty message was sent").isEqualTo(messagesBefore);
    }

    /** Test 7: after a second question the first answer is still there. */
    @Test
    public void consecutiveMessagesPreserveHistory() {
        chat.askQuestion("What is Permission?");
        int messagesAfterFirst = chat.agentMessageCount();

        String secondQuestion = "How do I earn from my data?";
        String secondAnswer = chat.askQuestion(secondQuestion);

        assertThat(chat.agentMessageCount())
                .as("the first answer was lost, the chat history is broken")
                .isGreaterThan(messagesAfterFirst);

        ResponseAssertions.assertValidAgentResponse(secondAnswer, secondQuestion);
    }

    /**
     * Test 8: check the quality of the answer, not just its shape.
     *
     * Step 1: ask the real ask.permission.ai agent and save its answer to a file.
     * Step 2: run promptfoo. It reads that file and asks Claude to grade the
     *         answer against the rubric in evals/promptfooconfig.yaml.
     *         Claude is only the judge here. The answer is the app's own.
     */
    @Test
    public void llmEvalPasses() throws IOException, InterruptedException {

        // Step 1: the same question the rubric expects an answer to.
        String answer = chat.askQuestion("What is Permission?");

        Path answerFile = Path.of("target", "agent-answer.txt");
        Files.createDirectories(answerFile.getParent());
        Files.writeString(answerFile, answer);

        // Step 2: grade it. -o writes the result as JSON so we can read the reason.
        Path resultFile = Path.of("target", "eval-result.json");
        String command = "npx promptfoo eval -c evals/promptfooconfig.yaml"
                + " --no-progress-bar -o " + resultFile;

        // On Windows the command must run through cmd, on Mac/Linux through sh.
        boolean isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");
        ProcessBuilder builder = isWindows
                ? new ProcessBuilder("cmd", "/c", command)
                : new ProcessBuilder("sh", "-c", command);

        builder.inheritIO(); // show the tool's output in the console
        Process process = builder.start();

        boolean finished = process.waitFor(4, TimeUnit.MINUTES);

        assertThat(finished).as("promptfoo did not finish in 4 minutes").isTrue();

        String result = Files.exists(resultFile) ? Files.readString(resultFile) : "";
        String reason = findReason(result);

        // Two very different failures. Say which one it was.
        //   graderError -> the judge never ran (grader not configured or unreachable)
        //   otherwise   -> the judge ran and did not like the app's answer
        assertThat(result)
                .as("the judge could not run, so the answer was never graded. Reason: " + reason)
                .doesNotContain("\"graderError\": true");

        assertThat(process.exitValue())
                .as("the app answer did not pass the rubric. The judge said: " + reason)
                .isZero();
    }

    /** Pulls the first "reason" text out of the promptfoo result file. */
    private static String findReason(String json) {
        String key = "\"reason\": \"";
        int start = json.indexOf(key);
        if (start < 0) {
            return "no reason found in target/eval-result.json";
        }
        start = start + key.length();
        int end = json.indexOf('"', start);
        return json.substring(start, end);
    }
}
