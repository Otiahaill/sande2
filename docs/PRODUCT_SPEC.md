# PRODUCT SPEC — sande (MVP)

## Purpose
Native Kotlin Android app for salespeople to record sales offline, keep stock correct via an append-only movement ledger, and sync to a shared Firebase backend.

## Core rules (non-negotiable)
1. Money stored as integer cents (KES 1 = 100 cents).
2. Timestamps UTC epoch ms; displayed in Africa/Nairobi.
3. IDs are client-generated UUID strings.
4. Room is the single durable source of truth on device. Firestore is transport only; its offline cache is disabled.
5. Stock = sum of immutable ledger movements. Never an overwritable number.
6. Sales are never rejected at point of sale. Negative stock → WARN alert, reconciled after sync.
7. Offline-first: sale survives app kill + device restart exactly once; uploads on reconnect.

## Roles
- Owner: full access, edits manager flags
- Manager: most actions, cannot manage members
- Salesperson: record sales, receive stock; cannot adjust stock, void sales, edit products, read user lists

## MVP screens
- Splash → Sign in → (Shell)
- Shell: bottom nav — Catalogue, Stock, Sales, Sync chip, Settings
- Catalogue: product list, categories, add/edit product
- Stock: stock_balance view, receive stock, adjust stock (manager+)
- Sales: cart, record sale, void, history, receipts
- Settings: sign out, role view, sync status

## Non-MVP (deferred)
- Reports & replenishment dashboard
- Audit viewer, CSV export
- Firebase Security Rules emulator suite (rules written but not unit-tested in CI)
- Signed release APK / keystore backup
- FCM push, multi-location pool
