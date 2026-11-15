# DECISIONS — sande

Blueprint reference: §25 decisions U1–U9. Recorded here; resolved items marked ✅.

| ID | Decision | Options | Default | Status | Resolution |
|---|---|---|---|---|---|
| U1 | Negative stock policy | WARN / BLOCK / ALLOW | **WARN** | Open | |
| U2 | GitHub repo visibility | Public / Private | Your call | Open | |
| U3 | Employee account creation | Firebase console / in-app | Console only | Open | |
| U4 | Fractional quantities | Integers / scaled ints | Integers | Open | |
| U5 | Local encryption | File-based / SQLCipher | File-based | Open | |
| U6 | Price override by salesperson | Yes / No | No | Open | |
| U7 | Backup destination | Share sheet / fixed path | Share sheet | Open | |
| U8 | Receipt number format | `{prefix}-{deviceShort}-{counter}` | Default | Open | |
| U9 | Multi-location stock pool | Yes / No | No | Open | |
| — | Build route | Local Gradle / GitHub Actions CI | **CI (Route B)** | ✅ Resolved | Probe: ARM, no JDK, 73 MB RAM |
| — | Persistence | Room source of truth / Firestore cache | **Room** | ✅ Resolved | Blueprint §1 |
| — | Package name | TBD | com.example.sande | Open | |
