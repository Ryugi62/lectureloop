# RevenueCat setup (Test Store, ~10 minutes)

The app only needs a **Test Store API key**; no Google Play account or store listing is involved.
Test Store products cannot be edited after saving (price and duration are fixed at creation), so enter them exactly once.

1. **Project** — create a project named `LectureLoop`. A Test Store is created with it; copy its API key
   (Apps and providers → Test configuration) into `local.properties` as `REVENUECAT_TEST_STORE_KEY`.
2. **Entitlement** — Product catalog → Entitlements → `pro` (display name "LectureLoop Pro").
3. **Products** (Test Store):

   | Identifier | Title | Type | Duration | Price (USD) |
   |---|---|---|---|---|
   | `semester_pass` | Semester Pass | Subscription | 6 months | 19.99 |
   | `monthly` | Monthly | Subscription | 1 month | 4.99 |

   Attach both to the `pro` entitlement.
4. **Offering** — identifier `default`, mark it current. Packages:

   | Package | Product |
   |---|---|
   | `$rc_six_month` (Six month) | `semester_pass` |
   | `$rc_monthly` (Monthly) | `monthly` |

   Metadata:
   ```json
   {"headline": "Keep every lecture in the loop", "free_lectures_per_week": 2}
   ```
5. **Customer Center** — Tools → Customer Center → enable with the default configuration.

Check in the app: Home shows "2 free lectures left this week"; the third lecture of the week opens the paywall with
"Semester Pass $19.99 / 6 months — Save 33% vs monthly — $0.77 a week". A Test Store purchase shows RevenueCat's
test purchase sheet; after "valid purchase" the waiting recording is built and Home shows "Unlimited lectures".

Test Store subscriptions renew quickly (a 6-month product renews every 30 minutes, 5 times) and then expire, which is
useful for checking that access ends when the entitlement does.
