const fs = require('fs');
const path = require('path');

// Where the Playwright test saves the answer it captured from ask.permission.ai.
const ANSWER_FILE = path.join(__dirname, '..', 'target', 'agent-answer.txt');

/**
 * A promptfoo "provider" normally calls an AI to produce an answer.
 * This one calls nothing. It just reads the answer that the Playwright test
 * already captured from the real ask.permission.ai agent.
 *
 * So promptfoo grades the APP's answer, not an AI's answer.
 */
class AgentAnswerProvider {

  id() {
    return 'ask-permission-agent';
  }

  async callApi() {
    if (!fs.existsSync(ANSWER_FILE)) {
      return {
        error: 'No agent answer found at ' + ANSWER_FILE +
               '. Run the Playwright test first: mvn test',
      };
    }

    const answer = fs.readFileSync(ANSWER_FILE, 'utf8').trim();

    if (answer.length === 0) {
      return { error: 'The saved agent answer is empty: ' + ANSWER_FILE };
    }

    return { output: answer };
  }
}

module.exports = AgentAnswerProvider;
