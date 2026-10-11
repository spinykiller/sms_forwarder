# Codex review trail (gpt-6-sol, high) — `master...HEAD`

| Round | Finding | Verdict | Fix |
|---|---|---|---|
| 1 | P1 Network sends inside the SMS broadcast can exceed its time limit | Valid | Receiver only enqueues; worker sends (expedited on Android 12+) |
| 1 | P1 Queued forward re-reads destination, can go to a changed URL/chat | Valid | Destination snapshotted at receipt |
| 1 | P2 Forward log (SMS text) included in Android backups | Valid | Log DB in no-backup storage |
| 1 | P2 Long SMS can exceed WorkManager's 10 KB `Data` | Valid | Payload file in no-backup storage; `Data` holds its path |
| 1 | P2 Test message reported failed SMS sends as sent | Valid | Uses the real send result |
| 2 | P1 Broadcast could finish before WorkManager persisted the work | Valid | Receiver awaits the enqueue `Operation` |
| 2 | P2 Failed product-detail query never retried | Valid | Re-queried when the Pro screen opens |
| 2 | P2 Pro-gated settings not refreshed when entitlement changes | Valid | Settings refresh on `pro_active` change |
| 2 | P2 Test message ignored the free one-number limit | Valid | Shared `forwardTargets()` |
| 3 | P1 Banner kept after ad consent is withdrawn | Valid | Banner rebinds after the privacy form either way |
| 3 | P2 7-digit suffix match treats strangers as targets | Valid | Suffix match only `+international` vs ≥9-digit local |

3 rounds done (max); round-3 fixes verified by unit tests, lint and release build.

# Codex review trail (gpt-6-sol, high) — new features, `cb62bdc...HEAD`

`gpt-6.1-sol` was requested but Codex rejects it for ChatGPT-account logins; `gpt-6-sol` is the newest Sol available.

| Round | Finding | Verdict | Fix |
|---|---|---|---|
| 1 | P1 App lock only on the settings screen; log readable after relock time | Valid | `LockedActivity` base for every screen, app-wide started-screen count |
| 1 | P1 Import writes wrong-typed values → crashes reads | Valid | Only known settings with their expected type (from the settings XML) |
| 1 | P1 Queued forwards still sent while paused | Valid | Worker re-schedules itself while paused |
| 1 | P2 Multipart SMS status from last part only | Valid | Per-part reports; any failure sticks; delivered when all parts are |
| 1 | P2 Apps sharing a label collapse in the picker | Valid | Keyed by package, sorted by label |
| 2 | P1 Import keeps settings absent from the backup | Valid | Known settings missing from the file are reset |
| 2 | P2 Blocked target can still trigger reverse SMS | Valid | Blocklist checked before reverse commands |
| 2 | P2 Reused notification key with same text never forwarded | Valid | Forget on removal; compare title + text |
| 2 | P2 Export warning omits the SMTP password | Valid | Warning updated in all languages |
| — | (found while verifying) echo guard blocked genuine repeats | Fixed | Only our own forwarded text counts as an echo (unit-tested) |
| 3 | P1 Reverse SMS sent with every method off | Valid | Receiver returns early when all methods are off |
| 3 | P2 Held SMS sent while roaming | Valid | Roaming re-checked at send time |
| 3 | P2 Carrier delivery failures ignored | Valid | Permanent-failure reports mark the row failed |
| 3 | P2 Paused hold could lose the forward | Valid | Replacement job awaited before completing |

3 rounds done (max). Final: unit tests 14/14, lint 0 errors, debug + R8 release build, emulator regression (forward, all-off, pause hold, app lock, notifications).
