package ask;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.WaitUntilState;

import java.util.Arrays;

/**
 * All the selectors and page actions live here.
 * The tests only call these methods, they never use a selector directly.
 */
public class ChatPage {

    private static final String URL = "https://ask.permission.ai/";

    // Selectors, taken from the real page.
    private static final String INPUT = "[data-testid='agent-chat-input']";
    private static final String SIGN_UP = "[data-testid='sign-up-button']";
    private static final String PILL = "button.group:not([data-testid])";
    private static final String AGENT_MSG = "div[class*='justify-start']"; // agent rows are left aligned
    private static final String COOKIE_BUTTON = "#onetrust-accept-btn-handler";
    private static final String COOKIE_BANNER = "#onetrust-consent-sdk";

    // Timeouts in milliseconds.
    private static final int LOAD_TIMEOUT = 45000;     // page must show the input box
    private static final int ANSWER_TIMEOUT = 30000;   // agent must finish answering
    private static final int POLL = 300;               // how often we re-read the answer
    private static final int SAME_READS_NEEDED = 3;    // 3 identical reads = answer finished

    private final Page page;

    public ChatPage(Page page) {
        this.page = page;
    }

    /** Opens the site and closes the cookie banner. */
    public void open() {
        goToPage();
        closeCookieBanner();
    }

    /** Opens the site again. The suggestion pills only show on the second visit. */
    public void reopen() {
        goToPage();
    }

    private void goToPage() {
        // DOMCONTENTLOADED because the page keeps a connection open, so it never
        // fully "loads" and the default wait would time out.
        page.navigate(URL, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));

        // Wait for the input box before clicking anything.
        page.locator(INPUT).waitFor(new Locator.WaitForOptions().setTimeout(LOAD_TIMEOUT));
    }

    /** The banner covers the input box, so we close it. It is fine if it is not there. */
    private void closeCookieBanner() {
        try {
            page.locator(COOKIE_BUTTON).click(new Locator.ClickOptions().setTimeout(5000));

            // Wait until the banner is really gone. Clicking is not enough: it fades
            // out, and while it fades it still swallows our clicks on the input box.
            page.locator(COOKIE_BANNER).waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.HIDDEN)
                    .setTimeout(10000));
        } catch (RuntimeException e) {
            // no banner this time
        }
    }

    /**
     * Waits until the input box can actually be typed in.
     *
     * While the agent is still writing, the page disables the input box and shows
     * "Agent is responding...". Typing then fails with a timeout, so we wait for
     * the disabled flag to go away first.
     */
    private void waitUntilInputIsReady() {
        page.waitForFunction(
                "selector => { const box = document.querySelector(selector);"
                        + " return box && !box.disabled; }",
                INPUT,
                new Page.WaitForFunctionOptions().setTimeout(ANSWER_TIMEOUT));
    }

    public Locator pills() {
        return page.locator(PILL);
    }

    /** Returns true if a pill appears, false if none appears in time. */
    public boolean pillsRendered() {
        try {
            pills().first().waitFor(new Locator.WaitForOptions().setTimeout(LOAD_TIMEOUT));
            return true;
        } catch (TimeoutError e) {
            return false;
        }
    }

    public Locator signUp() {
        return page.locator(SIGN_UP);
    }

    /** How many agent messages are on screen right now. */
    public int agentMessageCount() {
        return page.locator(AGENT_MSG).count();
    }

    /** The text currently typed in the input box. */
    public String inputValue() {
        return page.locator(INPUT).inputValue();
    }

    /** Clicks a pill and returns the agent's answer. */
    public String clickPill(int index) {
        int countBefore = agentMessageCount();
        pills().nth(index).click();
        return waitForAnswer(countBefore);
    }

    /** Types a question, presses Enter, and returns the agent's answer. */
    public String askQuestion(String question) {
        int countBefore = agentMessageCount();
        waitUntilInputIsReady();
        Locator input = page.locator(INPUT);
        input.click();
        input.fill(question);
        input.press("Enter");
        return waitForAnswer(countBefore);
    }

    /** Types two lines with Shift+Enter between them. This must NOT send the message. */
    public void typeWithShiftEnter(String firstLine, String secondLine) {
        waitUntilInputIsReady();
        Locator input = page.locator(INPUT);
        input.click();
        input.fill(firstLine);
        input.press("Shift+Enter");
        input.pressSequentially(secondLine);
    }

    /** Presses Enter with an empty input box. Nothing should be sent. */
    public void pressEnterOnEmptyInput() {
        waitUntilInputIsReady();
        Locator input = page.locator(INPUT);
        input.click();
        input.fill("");
        input.press("Enter");
    }

    /**
     * Waits for the agent to finish answering.
     *
     * The answer arrives word by word and the page has no "finished" flag, so:
     *   Step 1: wait until a NEW agent message appears.
     *   Step 2: read that message every 300 ms. When the text stays the same
     *           3 times in a row, the answer has stopped growing.
     */
    private String waitForAnswer(int countBefore) {
        long endTime = System.currentTimeMillis() + ANSWER_TIMEOUT;

        // Step 1: a new agent message must appear.
        page.waitForFunction(
                "([selector, n]) => document.querySelectorAll(selector).length > n",
                Arrays.asList(AGENT_MSG, countBefore),
                new Page.WaitForFunctionOptions().setTimeout(ANSWER_TIMEOUT));

        // Step 2: read the newest message until the text stops changing.
        Locator newestMessage = page.locator(AGENT_MSG).last();
        String previousText = "";
        int sameReads = 0;

        while (System.currentTimeMillis() < endTime) {
            String currentText = newestMessage.textContent().trim();

            // "Permission is typing..." is also a left aligned row, so skip it.
            // Its text never changes, so otherwise we would treat it as the answer.
            if (currentText.toLowerCase().contains("is typing") || currentText.endsWith("...")) {
                previousText = "";
                sameReads = 0;
                page.waitForTimeout(POLL);
                continue;
            }

            if (!currentText.isEmpty() && currentText.equals(previousText)) {
                sameReads++;
                if (sameReads == SAME_READS_NEEDED) {
                    return currentText;
                }
            } else {
                sameReads = 0;
            }

            previousText = currentText;
            page.waitForTimeout(POLL);
        }

        throw new AssertionError("The agent did not finish answering in "
                + ANSWER_TIMEOUT + " ms. Last text: " + previousText);
    }
}
