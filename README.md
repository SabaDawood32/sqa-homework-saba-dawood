# ask.permission.ai — pre-login QA suite

8 tests in Java with Playwright, TestNG and AssertJ against the live pre-login
agent, plus one promptfoo rubric on answer quality.
Target: https://ask.permission.ai

## Setup

Needs JDK 17+ and Maven. Playwright installs its browsers on the first run.

```bash
git clone https://github.com/SabaDawood32/sqa-homework-saba-dawood.git
cd sqa-homework-saba-dawood

# all 8 tests, then the HTML report at target/reports/surefire.html
mvn surefire-report:report

# tests only, browser hidden
mvn test -Dheadless=true

# one class, or one test
mvn test -Dtest=ChatTest
mvn test -Dtest=InputTest#llmEvalPasses
```

Test 8 (`llmEvalPasses`) needs your own Anthropic API key for the judge. The
other 7 need no key.

```powershell
$env:ANTHROPIC_API_KEY = "sk-ant-..."     # Windows PowerShell
```
```bash
export ANTHROPIC_API_KEY=sk-ant-...       # macOS / Linux
```

The key can also go directly into `evals/promptfooconfig.yaml`. With a key,
test 8 runs the rubric and passes with the rest; without one it reports that
the judge never ran, not that the answer was bad.

## Test strategy (TL;DR)

Covered: pills render; a pill click and a typed question each return a real
answer; the sign-up entry point exists pre-login; Shift+Enter adds a newline
without sending; an empty composer sends nothing; a second question keeps the
first answer; and the "What is Permission?" answer is graded by an LLM rubric.

Skipped: cross-browser, since one SPA makes engine bugs the wrong risk; mobile
and post-login automation, explored by hand in `artifacts/ux-review.md`, where
mobile login fails on Recaptcha; and latency, since the answer streams against
no published target.

## Key decisions

- **Playwright over Selenium**, Java over the JS binding. Actions auto-wait for
  visible, enabled and stable, leaving only the wait Playwright cannot infer.
- **Waiting is condition-based, never a fixed sleep.** `ChatPage#waitForAnswer`
  waits for a *new* agent bubble, then polls its text every 300 ms and stops
  once three consecutive reads match. Replies ranged from ~2 s to ~20 s, so any
  constant is either flaky or wasteful. Watching the DOM settle rather than the
  clock means a slower model or a transport change still passes. Same approach
  elsewhere: `DOMCONTENTLOADED`, since the page holds a connection open and
  `load` never fires; waiting out the disabled composer while the greeting
  streams; waiting for the cookie banner to be hidden, not merely clicked.
- **Locators live only in `ChatPage`.** Where the app ships a `data-testid` I
  use it: it exists for tests and survives a restyle. Bubbles ship none, so
  they match on structure — agent rows left-aligned, user rows right — not on
  Tailwind classes, which change with any visual edit. A UI change touches one
  file.
- **Assertions check properties, not prose:** length 100–2000 characters, 3 of
  6 topic word stems, 8 failure markers, an echo check. No exact text, because
  the wording changes every run.
- **The LLM eval grades the app's own answer,** saved by the test and read back
  by `evals/agentAnswerProvider.js`. The model is the judge, never the author.
- **A grader that cannot run is reported separately** from a rubric rejection,
  so a setup problem is never shown as a product defect.
- **Single thread** (`testng.xml`): the target is a shared live agent, and
  parallel sessions would turn rate limiting into fake failures.
- **Two test classes, one page object, one assertions class** — the framework.

## AI disclosure

See [artifacts/ai-workflow.md](artifacts/ai-workflow.md).

## Next steps

With 1–2 more days: a golden set of ~30 questions scored nightly, alerting on
drift rather than one pass/fail; capture the chat network response so
assertions can run on the payload too; authenticated coverage behind a seeded
account; and a nightly CI run, since PR-only CI would miss these regressions.

## Submission checklist

- [x] Repo named `sqa-homework-<first-last>`, default branch `main`
- [x] README includes exact Setup + run commands
- [x] Max 8 tests; all required behaviours covered
- [x] `artifacts/assertions.md` included
- [x] At least one assertion wired into an LLM-evaluation framework and running as part of the suite
- [x] `artifacts/ux-review.md` included (desktop + mobile, post-signup, 5 prioritized improvements)
- [x] `artifacts/data-checks.md` included (with SQL)
- [x] `artifacts/ai-workflow.md` included
- [x] Commit history shows how the work evolved
- [ ] `artifacts/report/` included — generated per run at `target/reports/`, not committed
- [ ] `artifacts/demo.mp4` included (60–90 sec, narrated)
