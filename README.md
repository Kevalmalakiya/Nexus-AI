# NEXUS AI
*More Than AI. Your Digital Companion.*

Android AI companion (Kotlin, Jetpack Compose). Developed from Termux, built by GitHub Actions.

## Status
| Phase | State |
|---|---|
| 1 Foundation (chat, Gemini, personality) | Scaffolded, **not yet verified by CI** |
| 2-8 | Not started |

## Dev workflow
Edit in Termux -> `git commit` -> `git push` -> Actions builds APK -> download artifact -> install.
Bring your own Gemini key; enter it in the app (stored encrypted). Never commit keys.

## Release secrets (GitHub > Settings > Secrets)
KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD
