package ask;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks that an agent answer looks like a real answer.
 * The wording changes every run, so we never compare exact text.
 */
public class ResponseAssertions {

    // A real answer is longer than 100 characters and shorter than 2000.
    private static final int MIN_LENGTH = 100;
    private static final int MAX_LENGTH = 2000;

    // Word starts, not full words, so "shar" also matches "sharing".
    private static final List<String> KEYWORDS =
            List.of("data", "earn", "shar", "agent", "wallet", "opportunit");

    // At least 3 of the 6 keywords must appear, or the answer is off topic.
    private static final int MIN_KEYWORDS = 3;

    // Text that means something broke instead of an answer.
    private static final List<String> ERROR_TEXTS = List.of(
            "undefined",            // a JavaScript variable was empty
            "[object object]",      // an object was printed instead of its text
            "{{",                   // a template placeholder was never filled in
            "error:",               // an error message leaked into the chat
            "traceback",            // a server stack trace leaked into the chat
            "as an ai language model",
            "i don't have access",
            "something went wrong");

    public static void assertValidAgentResponse(String text, String question) {

        // 1. There is some text at all.
        assertThat(text).as("the answer was empty").isNotBlank();

        // 2. The text is not too short and not too long.
        assertThat(text.length()).as("answer too short: " + text).isGreaterThan(MIN_LENGTH);
        assertThat(text.length()).as("answer too long").isLessThan(MAX_LENGTH);

        String lower = text.toLowerCase();

        // 3. The answer is about the right topic.
        int found = 0;
        for (String keyword : KEYWORDS) {
            if (lower.contains(keyword)) {
                found++;
            }
        }
        assertThat(found)
                .as("answer looks off topic, only " + found + " keywords found: " + text)
                .isGreaterThanOrEqualTo(MIN_KEYWORDS);

        // 4. The answer is not an error message.
        for (String error : ERROR_TEXTS) {
            assertThat(lower).as("answer contains an error: " + error).doesNotContain(error);
        }

        // 5. The agent answered instead of repeating the question.
        assertThat(text.trim())
                .as("the agent just repeated the question")
                .isNotEqualToIgnoringCase(question.trim());
    }
}
