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

import static org.assertj.core.api.Assertions.assertThat;

/** Tests 1-4: the suggestion pills, free text questions, and the sign up button. */
public class ChatTest {

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

    /** Test 1: the suggestion pills are shown. */
    @Test
    public void pillsAreVisibleOnLoad() {
        chat.reopen();

        assertThat(chat.pillsRendered()).as("no pill appeared on the page").isTrue();
        assertThat(chat.pills().count()).as("no pill was found").isGreaterThan(0);
    }

    /** Test 2: clicking a pill gives a real answer. */
    @Test
    public void pillClickProducesResponse() {
        chat.reopen();

        // The pill text is the question, so we can check the agent did not repeat it.
        String question = chat.pills().first().innerText().trim();

        String answer = chat.clickPill(0);

        ResponseAssertions.assertValidAgentResponse(answer, question);
    }

    /** Test 3: typing a question gives a real answer. */
    @Test
    public void freeTextProducesResponse() {
        String question = "What is Permission?";

        String answer = chat.askQuestion(question);

        ResponseAssertions.assertValidAgentResponse(answer, question);
    }

    /** Test 4: the sign up button is there before login. */
    @Test
    public void signUpNavigationWorks() {
        assertThat(chat.signUp().isVisible()).as("the sign up button is missing").isTrue();
    }
}
