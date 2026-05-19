# GramSieve

GramSieve is an LSPosed module that filters Telegram messages locally. It can hide, collapse, or debug-mark messages that match global or per-chat rules without touching Telegram servers, chat history, or block lists.

## Highlights

- Local-only filtering for `org.telegram.messenger`
- Global rules and per-chat overrides
- Match targets for message text, captions, buttons, senders, and chats
- Whitelist rules that always win before filter rules
- Three actions: hide locally, collapse locally, or debug-mark
- Companion UI in English and Simplified Chinese

## Scope and Compatibility

- Recommended LSPosed scope: `org.telegram.messenger`
- Modern Xposed API: `io.github.libxposed:api:101.0.1`
- Companion service API: `io.github.libxposed:service:101.0.0`
- Current source-aligned Telegram baseline: `11.4.2 (release-11.4.2-5469)`

Telegram UI internals change over time, so new Telegram releases may need hook updates before every filter entry point works again.

## How Rules Work

GramSieve normalizes message text, media captions, inline button labels and URLs, sender names and IDs, plus chat names and IDs. Matching rules then decide whether the message should be hidden, collapsed, or marked.

Each rule is written on its own line. Prefixes are optional in the old free-form format and still supported:

```text
t.me/
buy now
sender:promo_bot
chat:airdrops
button:https://
caption:airdrop
```

Regex rules work the same way:

```text
https?://
sender:^(promo|deal)_bot$
button:https?://[^ ]+
```

Supported target prefixes:

- `text:`
- `caption:`
- `button:`
- `sender:`
- `chat:`

No prefix means "match across every normalized field". In the current UI, each input box is already target-specific, so prefixes are usually unnecessary there.

## Entry Points

- Telegram settings menu: `GramSieve filters`
- Telegram chat menu: `GramSieve chat filters`

Rules are stored inside the module app and synced to the LSPosed service bridge so Telegram can read them safely.

## Sample Rules

- [sample-global-rules.txt](examples/sample-global-rules.txt)
- [sample-chat-rules.txt](examples/sample-chat-rules.txt)
- [sample-config.json](examples/sample-config.json)

## Install

1. Download the latest signed APK from GitHub Releases.
2. Install the APK on the device.
3. Enable GramSieve in LSPosed.
4. Keep the scope limited to `org.telegram.messenger`.
5. Force-stop Telegram, then open it again.
6. Open `GramSieve filters` from Telegram settings or `GramSieve chat filters` from a chat menu.

## Limits

- Filtering is visual and local only.
- GramSieve does not delete messages, report users, block users, or modify Telegram storage.
- Telegram updates can break hooks until the module is updated.

## Development

```powershell
./gradlew.bat lintDebug
./gradlew.bat testDebugUnitTest
./gradlew.bat assembleDebug
./gradlew.bat assembleRelease
```

If you want to publish this module to the LSPosed repository, the repo root already includes `SUMMARY` and `SCOPE`. See [docs/lsposed-publish.md](docs/lsposed-publish.md) for the full release flow.
