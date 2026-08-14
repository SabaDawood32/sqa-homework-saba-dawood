# Data Checks — ask.permission.ai

Based only on what I saw in the videos: signup with email and password, the
profile form with name, phone, DOB and gender, email verification, chat
messages with the agent, and the wallet where 100 ASK points appeared after
completing the signup/profile flow.

## What I'd expect to be written

When the user signs up and completes the profile:

- `users` — id, email, password_hash, email_verified_at, created_at
- `user_profiles` — user_id, first_name, last_name, country, phone, dob,
  gender, updated_at
- `wallets` — user_id, balance, updated_at
- `wallet_transactions` — id, user_id, amount, reason (for example,
  "profile_complete" or "referral"), created_at

When the user sends a message to the agent:

- `conversations` — id, user_id (nullable for pre-login/anonymous users),
  session_id, created_at
- `messages` — id, conversation_id, role ('user' or 'agent'), content,
  created_at

## SQL checks I would run

### 1. Every verified user has a profile and a wallet

```sql
SELECT u.id
FROM users u
LEFT JOIN user_profiles p ON p.user_id = u.id
LEFT JOIN wallets w ON w.user_id = u.id
WHERE u.email_verified_at IS NOT NULL
  AND (p.user_id IS NULL OR w.user_id IS NULL);
-- Expected: 0 rows
```

This checks that once a user has verified their email, the profile and wallet
records are there as well.

I added the `email_verified_at` condition because the profile form seems to
come after email verification. So someone who has only registered but has not
verified their email yet might not have a profile record. Without this
condition, those users could show up as failures even though the flow is
working as expected.

### 2. Every message belongs to a valid conversation

```sql
SELECT m.id, m.conversation_id
FROM messages m
LEFT JOIN conversations c ON c.id = m.conversation_id
WHERE c.id IS NULL;
-- Expected: 0 rows
```

This is mainly to find orphaned messages, where a message exists but the
conversation it should belong to does not exist.

### 3. Message timestamps look valid

```sql
SELECT id, created_at
FROM messages
WHERE created_at IS NULL
   OR created_at > CURRENT_TIMESTAMP + INTERVAL '1 minute';
-- Expected: 0 rows
```

This checks that every message has a timestamp and that there aren't messages
recorded noticeably in the future.

I used a one-minute allowance because there could be a small time difference
between the application server and the database. Without that, a few seconds
of clock difference could create a false failure.

### 4. Wallet balance matches its own transactions

```sql
SELECT w.user_id, w.balance, COALESCE(SUM(t.amount), 0) AS calculated_balance
FROM wallets w
LEFT JOIN wallet_transactions t ON t.user_id = w.user_id
GROUP BY w.user_id, w.balance
HAVING w.balance != COALESCE(SUM(t.amount), 0);
-- Expected: 0 rows
```

The first three checks mostly confirm that the records exist and are linked
correctly. This one checks whether the actual value is correct.

I saw +100 ASK appear in the wallet after completing the signup/profile flow,
so I would want to make sure that 100 is also present in the wallet
transactions. The balance should be explainable by the transactions rather
than just being written directly into the balance.

I used `LEFT JOIN` and `COALESCE` intentionally. The `LEFT JOIN` makes sure
wallets with no transactions are still included, because that could actually be
something I want to catch. `COALESCE` makes a wallet with no transactions
compare against 0 instead of NULL.

## Downstream data check

For the analytics pipeline, I would add a check for wallet transactions where
the amount is zero or the reason is missing.

Since the signup flow showed a +100 ASK reward, I would expect that reward to
have an actual amount and a reason such as `profile_complete`. This can help
catch rewards that were triggered but not recorded properly before the data
reaches analytics.

Before making this an alert, I would first confirm that `amount = 0` is never
valid. If zero-value transactions are used for something like corrections or
reversals, I would exclude those cases from the check.
