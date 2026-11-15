# DESIGN — sande UI/UX (Material 3, Compose)

## Theme
- **Design system:** Material 3 (M3) via Compose Material3
- **Color:** dynamic color enabled; light and dark themes; primary/secondary/tertiary from brand seed
- **Type:** M3 type scale (display, headline, title, body, label)
- **Touch targets:** minimum 48dp × 48dp on every interactive element
- **Elevation:** M3 elevation tokens; cards 1dp raised, 3dp pressed

## Navigation
- **Library:** Navigation Compose with type-safe routes (Kotlin Serialization)
- **Shell:** BottomNavigation with 4 destinations: Catalogue, Stock, Sales, Settings
- **Sync chip:** persistent pill in top app bar showing sync status (idle / syncing / offline / error)
- **Auth:** full-screen sign-in flow; session cached for offline use

## Screens (MVP)

| Screen | Purpose | Key controls |
|---|---|---|
| Splash | Brand + session check | animated logo, progress |
| Sign in | Email/password auth | email field, password field, sign-in, forgot password |
| Catalogue | Browse products | search bar, category chips, product grid, add product FAB |
| Stock | Stock balance list | product rows with on-hand qty, low-stock badge, receive FAB |
| Receive stock | Inbound adjustment | product picker, qty, notes, save |
| Sales cart | Build a sale | product list, qty stepper, subtotal, pay/checkout |
| Record sale | Atomic commit | cart summary, cash amount, change, confirm, receipt number |
| Sale history | Past sales | list by date, receipt number, status, void (manager+) |
| Settings | Session & info | sign out, role badge, sync status chip, app version |

## Layout principles
- 8dp grid; 16dp screen padding
- Lists: single-line item with leading avatar/icon, trailing qty
- Empty states: illustration + primary action button
- Error states: inline Snackbar / alert dialog, non-blocking where possible

## Brand (seed color)
Primary: #6750A4 (M3 purple) — replaceable once owner confirms brand
