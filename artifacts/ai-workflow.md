# AI workflow

## Tools I used

I used Claude Code inside VS Code for most of this work. The main reason is
that it edits the actual files in the repo instead of giving me snippets to
copy over. That let me run the tests straight after a change and see whether
it really worked, which matters a lot for a site where the answer is different
every run.

## What I generated and kept

The setup was mostly generated and I kept it as it was: `pom.xml`,
`testng.xml`, the TestNG before/after methods in both test classes, and
`.gitignore`. This is standard boilerplate and there was no reason to type it
out by hand.

## What I generated and then changed

The promptfoo config was the biggest change. The generated version had
`providers: anthropic:messages:claude-sonnet-5`, which means the AI was
writing the answer and then grading its own answer. The chat application was
never involved at all, so the test was not actually testing the product.

I changed it so the Playwright test asks the real agent, saves the answer to
`target/agent-answer.txt`, and promptfoo reads that file through a small
provider in `evals/agentAnswerProvider.js`. Claude is still used, but only as
the judge, because the rubric asks whether the answer implies data is sold
without consent and a keyword check cannot decide that.

## One thing the AI got wrong that I caught

The failure message on the promptfoo test said "promptfoo said the answers
were not good enough". When I ran it, the real reason was that the grader
never ran at all. So the test was blaming the product for what was actually a
setup problem, which would have sent me looking in the wrong place.

I split it into two checks. One reports that the judge could not run and
prints the actual reason from `target/eval-result.json`. The other reports
that the answer was rejected by the rubric.

I found a similar problem in the documentation. `assertions.md` described
checks that were not in the code at all, such as requiring the word "ASK" and
needing only one keyword instead of three. I went through the doc against
`ResponseAssertions.java` line by line and corrected it.

## What I did not trust to AI

Choosing which 8 tests to write, and the keyword list in `ResponseAssertions`.
I used "shar" instead of "share" after seeing the agent write "sharing", and I
decided on 3 of 6 keywords rather than all 6, because asking for all 6 would
fail a shorter answer that is still correct. Those are judgment calls and I
can explain each one.
