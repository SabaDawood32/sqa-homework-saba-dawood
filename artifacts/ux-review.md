# UX Review — ask.permission.ai

Tested on **desktop and mobile using responsive mode**, including the pre-login chat and the post-signup experience.

## UX Findings

The pre-login chat works well overall. Suggested topic pills load correctly, questions return responses, and even random inputs such as "ddd", "..", and "????/" don't break the experience. The system gives a fallback response and points the user back toward relevant topics.

The main pre-login issue is the cookie experience. After dismissing the OneTrust popup, a cookie icon remains at the bottom-left. Clicking it opens the full modal again. When I tested this while a response was being generated, the modal covered the response, so I had to close it before continuing.

Signup is smooth, with live password requirements and a quick verification email. However, immediately after verification, the product asks for legal name, phone, DOB, and gender before I have really used the product. This feels like too much information too early, especially for a product focused on data ownership.

The post-signup experience also changes significantly. A category picker appears inside what looks like a normal agent message, although it is actually a form. The dashboard introduces several new sections (Agent, Data Enrichment Hub, Redeem, Referrals, Wallet) without much explanation, while the chat header only says "AI", which feels unfinished.

On mobile, the biggest problem is login. The same credentials that work on desktop consistently return **"Recaptcha execution failed"**, preventing login.

## Prioritized Improvements

1. **Fix mobile login — Highest priority.**
   **Observation:** Valid credentials fail with a Recaptcha error.
   **Why it matters:** This completely blocks mobile users from accessing the product.
   **Change:** Investigate the mobile Recaptcha/authentication flow and ensure the same login path works across form factors.

2. **Reduce personal information requested immediately after signup.**
   **Observation:** Name, phone, DOB and gender are requested before meaningful product use.
   **Why it matters:** Creates unnecessary friction and can reduce trust/conversion.
   **Change:** Collect information progressively, when it is actually required.

3. **Make the mobile and desktop authentication flows consistent.**
   **Observation:** Mobile can redirect to a different domain and branding.
   **Why it matters:** Users may question whether they are still in the same product.
   **Change:** Keep the same domain, branding and signup/login journey where possible.

4. **Improve clarity around interactive chat elements.**
   **Observation:** The category picker looks like an agent message rather than a form.
   **Why it matters:** Users may not understand what action is expected.
   **Change:** Visually distinguish forms/actions from conversational messages.

5. **Prevent cookie controls from interrupting the conversation.**
   **Observation:** The cookie modal can cover an active response.
   **Why it matters:** It interrupts the primary task.
   **Change:** Avoid blocking active chat content and use a smaller settings panel where possible.
