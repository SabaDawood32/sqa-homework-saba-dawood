# ask.permission.ai — pre-login QA suite

8 tests, Java + Playwright + TestNG + AssertJ, against the live pre-login agent,
plus one promptfoo rubric on answer quality. Target: https://ask.permission.ai

## Setup

Needs JDK 17+ and Maven. Browsers install themselves on the first run.

Test 8 (`llmEvalPasses`) grades the agent's answer with an LLM judge, so it needs
your own Anthropic API key. The other 7 tests need no key and pass without one.

Set it as an environment variable:

```powershell
# Windows PowerShell
$env:ANTHROPIC_API_KEY = "sk-ant-..."
```
```bash
# macOS / Linux
export ANTHROPIC_API_KEY=sk-ant-...
```

Or put it straight into the judge config in `evals/promptfooconfig.yaml`:

```yaml
defaultTest:
  options:
    provider:
      id: anthropic:messages:claude-sonnet-5
      config:
        apiKey: sk-ant-...
```

Once a key is in place, test 8 runs the rubric and passes with the other 7.
Without a key it reports that the judge never ran, rather than claiming the
agent's answer was bad.

```bash
git clone <repo-url>
cd sqa-homework-<first-last>

# all 8 tests, then build the HTML report at target/reports/surefire.html
mvn surefire-report:report

# watch it run (this is the default; use -Dheadless=true to hide the browser)
mvn test -Dheadless=false

# one class
mvn test -Dtest=ChatTest

# LLM eval on its own (needs ANTHROPIC_API_KEY).
# Grades the agent answer saved at target/agent-answer.txt, so run mvn test first.
# Claude is used only as the judge; the answer comes from ask.permission.ai.
npx promptfoo@latest eval -c evals/promptfooconfig.yaml
```

## Test strategy (TL;DR)

Covered: landing renders the greeting + a usable composer; free-text → answer;
Shift+Enter newline; blank/whitespace submit rejected; composer unlocks after the
opening greeting; answer-quality validation for "What is Permission"; mobile
layout + a full turn at 390px.

**Suggested-topic pills do not render on a fresh pre-login session.** They exist —
six of them ("What is Permission", "Best way to earn ASK", …) render on a warmed
session where the greeting has already been consumed. But on a cold automated one
they are absent from the DOM entirely: I anchored on the "Suggested topics:"
heading, on the pill's `.group` class, and finally on the literal string
`"Best way to earn ASK"`, each with a 30s wait, and all three found nothing. That
last one rules out a selector bug — the copy simply is not on the page. So the
pill test is written against a real locator and skipped via an assumption, and it
starts running the day pills render pre-login rather than being deleted.

Skipped on purpose: cross-browser matrix (one SPA — engine bugs are not the risk
here), post-login automation (out of scope; covered in the UX review), multi-turn
memory.
8 tests because 8 is the cap. Each guards a distinct failure no other test sees.

## What the suite found

- Navigating with the default `load` event **never resolves** — the page holds a
  connection open for the agent, so every naive `page.navigate` times out at 30s.
  `ChatPage#open` uses `DOMCONTENTLOADED`.
- The ASK composer is **disabled while the opening greeting streams**, so anything
  that types on load races it. `awaitComposerReady` waits for the real state.
- The OneTrust cookie banner **covers the composer** until dismissed — a click
  interception for tests, and a first-impression problem for users.
- The typing indicator is itself a left-aligned bubble, so it is the "last agent
  message" until the answer replaces it. The wait skips it explicitly.

## Key decisions

- **Playwright over Selenium**: auto-waiting locators and `getByRole` out of the
  box. Selenium would have cost an afternoon on explicit waits before the first
  assertion. Java over the JS binding because the JVM stack is what I ship
  fastest in — the waiting logic is the interesting part, not the language.
- **Waiting: text-stability polling** (`ChatPage#awaitAgentReply`), not `sleep()`,
  not the "Permission is typing…" string. Poll the bubble; call it done when the
  text stops growing for 1.5s. Survives a slow model, a fast model, and a
  transport change — none of which a test should know about. The typing indicator
  is skipped rather than waited on, because it is copy and copy gets rewritten.
- **Locators: the app's own `data-testid`s** (`agent-chat-input`,
  `agent-chat-input-send-button`, `log-in-button`, `sign-up-button`), all in
  `ChatPage`. Message bubbles carry no testid, so they are matched on the one
  stable thing about them — agent rows are left-aligned, user rows right-aligned —
  not on the Tailwind utility classes around them, which change on any restyle.
- **Assertions on properties, not prose** (`ResponseAssertions`): length floor,
  finished-sentence check, broken-output markers, refusal shapes, ≥2 domain
  anchors. No exact response string is asserted anywhere.
- **promptfoo over DeepEval and Ragas**: one YAML file, no Python toolchain, and
  it grades a *captured* output — the real streamed answer, not a re-prompted
  model we do not ship. Ragas assumes retrieval context invisible from outside.
- **One mobile test, not a mirrored suite**: at 390px the new risk is layout and
  reachability, not chat logic.
- **Single fork, no parallelism**: the target is a shared production agent.
  Parallel workers would turn rate limiting into fake failures.
- **Two test classes, no page-object cathedral**: one `ChatPage` for locators
  plus the wait, one assertions class. That is the whole framework.

## AI disclosure

See [artifacts/ai-workflow.md](artifacts/ai-workflow.md).

## Next steps

With 1–2 more days: a golden set of ~30 questions scored nightly with drift
alerting on rubric scores rather than pass/fail; capture the chat network
response so assertions can run on the payload as well as the rendered text;
authenticated smoke coverage behind a seeded account; nightly GitHub Actions run
(the service ships without a PR in this repo, so PR-only CI misses the
regressions this suite exists to catch).

## Submission checklist

- [ ] Repo named `sqa-homework-<first-last>` and default branch is `main`
- [ ] README includes exact Setup + run commands (verified from a clean clone)
- [ ] README word count ≤ 500 (excluding commands/checkboxes)
- [ ] Max 8 tests; all 4 required behaviours covered
- [ ] `artifacts/assertions.md` included (≤ 300 words)
- [ ] At least one assertion wired into an LLM-evaluation framework and running as part of the suite
- [ ] `artifacts/ux-review.md` included (≤ 400 words, desktop + mobile, post-signup exploration, 3–5 prioritized improvements)
- [ ] `artifacts/data-checks.md` included (≤ 300 words + SQL)
- [ ] `artifacts/ai-workflow.md` included (≤ 300 words, all 4 questions answered)
- [ ] `artifacts/report/` included
- [ ] `artifacts/demo.mp4` included (60–90 sec, narrated)
- [ ] Commit history shows how the work evolved
