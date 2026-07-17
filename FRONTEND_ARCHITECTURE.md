# Frontend Architecture

This document explains how the React frontend is organized and how it interacts with the backend.

## 1. Frontend stack

- React
- TypeScript
- Vite
- React Router
- Axios
- localStorage / sessionStorage caching where it improves perceived responsiveness

## 2. Route map

Defined in `tradepulse-frontend/src/App.tsx`.

| Route | Page |
|---|---|
| `/` | HomePage |
| `/login` | LoginPage |
| `/registration` | RegistrationPage |
| `/about` | AboutPage |
| `/analytics` | AnalyticsPage |
| `/portfolio` | PortfolioPage |
| `/watchlist` | WatchlistPage |
| `/checkout` | CheckoutPage |
| `/payment` | PaymentPage |
| `/orders` | OrdersPage |
| `/account-management` | AccountManagementPage |
| `/wallet` | WalletPage |
| `/stocks/:stockId/insights` | StockInsightsPage |

## 3. Shared app state

Mounted in `tradepulse-frontend/src/main.tsx`:

- `CartProvider`
- `OrdersProvider`
- `WatchlistProvider`
- `WalletProvider`
- `MarketStatusProvider`

These reduce duplicated loading logic across pages.

## 4. Authentication model

Files involved:

- `src/utils/auth.ts`
- `src/utils/httpClient.ts`

Current behavior:

- token is stored in localStorage when the user chooses a persistent session
- token is stored in sessionStorage for session-only login
- helper methods decode JWT locally for `userId` and `email`
- protected requests use `Authorization` plus `X-User-Id`
- auth changes are broadcast through a custom browser event and storage events
- shared axios config clears stored auth state automatically on `401`

Note:
- the backend gateway now overrides any client-sent `X-User-Id` with the validated one, so the frontend header is only a convenience for existing code paths, not a trust boundary

## 5. API communication patterns

### Axios

Used for most REST calls.

Current shared behavior:

- 15-second default request timeout
- shared `Accept: application/json`
- global `401` handling to clear invalid sessions

### SSE

Used for:

- featured stocks stream
- market status stream

## 6. Stock streaming behavior

Files involved:

- `src/utils/useStreamedStocks.ts`
- stock pages such as `HomePage.tsx`, `WatchlistPage.tsx`, and `PortfolioPage.tsx`

Behavior:

- boot from local cached featured stocks when possible
- connect to `/api/stocks/stream/featured`
- prevent transient empty payloads from wiping a valid cache during reconnects
- search terms switch the SSE source to filtered featured/search behavior

## 7. Market status behavior

Files involved:

- `src/context/MarketStatusContext.tsx`
- `src/utils/marketSession.ts`

Behavior:

- one app-level provider manages market status for all pages
- cached status is accepted only if it is fresh within 60 seconds
- provider performs REST bootstrap and SSE subscription
- focus and visibility changes trigger revalidation
- fresh market status is preserved against temporary fallback responses during startup races

This avoids route-level flicker and duplicate SSE connections.

## 8. Cart, orders, watchlist, and wallet contexts

### CartContext

- loads cart when auth changes
- provides add, remove, update, clear actions
- forces sign-in for protected mutations

### WatchlistContext

- loads watchlist when auth changes
- provides add, remove, clear actions
- forces sign-in for protected mutations

### OrdersContext

- exposes cached order history and refresh function
- clears state on sign-out

### WalletContext

- tracks current balance for shared header/page usage
- refreshes on auth change

## 9. Form UX patterns

### Registration and account management

Current behavior includes:

- dependent country -> state -> city selection
- searchable custom dropdown UI
- postal code kept as manual input
- backend payload still uses plain strings

### Login and password recovery

Current behavior includes:

- standard login
- forgot-password request code
- code verification
- password reset flow

## 10. Frontend strengths

- gateway-relative URLs keep deployment topology simple
- state providers reduce repeated page logic
- market status and featured stocks both use cache-first + live refresh patterns
- session invalidation is handled centrally now
- route structure is already clear for major user journeys

## 11. Frontend improvements to consider later

Recommended future work:

- add a dedicated query/cache library if server state grows further
- add route guards for protected pages instead of page-level checks only
- add component-level error boundaries for critical routes
- introduce end-to-end tests for auth, checkout, and portfolio flows
- standardize loading and toast messaging across all pages

