# Featured Stocks Cache Implementation

## Overview
✅ **Implementation Complete**

The system has been redesigned to keep the 800 stocks table completely unchanged while storing only the top 50 ranking in a separate cache table.

**Key Feature:** Ranking runs **every day at 9:00 AM** automatically, and the cache persists so previous day's top 50 is always available.

---

## Exact Flow

### 1. **Backend Startup (Immediate - No Calculations)**
When `stock-service` starts:

**Step 1: Database Setup**
- `data.sql` runs automatically (`spring.sql.init.mode=always`)
- Creates the `featured_stocks_cache` table if it doesn't exist
- Ensures `market_cap` column exists in `stocks` table
- No columns added to or removed from the `stocks` table

**Step 2: Scheduler Setup (Instant)**
- Calculates next 9:00 AM
- If it's before 9 AM on the server: schedules ranking for 9 AM today
- If it's after 9 AM on the server: schedules ranking for 9 AM tomorrow
- Sets up automatic daily scheduling

**Result:**
- ✅ Service ready immediately (0 seconds)
- ✅ Frontend can start right away
- ✅ Cache from **previous day is still available** even if laptop was off
- ✅ No calculations at startup

---

### 2. **Daily Ranking (Every day at 9:00 AM)**
When 9:00 AM arrives:

**Background Ranking Process (~2-3 minutes):**
- Fetches all 800 stocks from DB
- Calls Massive API ~800 times to refresh market caps
- **Updates ONLY the `market_cap` column** in `stocks` table
- **Keeps all 800 stocks unchanged** (no additions/removals)
- Computes top 50 by `marketCap DESC`
- **Clears old cache**
- **Stores new top 50** in `featured_stocks_cache` with `sort_order` 1-50

**Result:**
- ✅ `stocks` table: 800 stocks (same list, just updated market caps)
- ✅ `featured_stocks_cache` table: Fresh top 50 ranking
- ✅ Runs automatically every 24 hours at 9 AM

**Important:** If laptop is off at 9 AM, ranking runs when laptop comes back on (first time after 9 AM)

---

### 2. **Frontend Loads (can start immediately)**

- Frontend calls `GET /stocks/featured/health` to check if cache is ready
  ```json
  {
    "ready": true,
    "cachedCount": 50,
    "message": "Featured stocks cache is ready"
  }
  ```

**Step 2: Cache Fetch**
- Frontend calls `GET /stocks/featured`
- Backend returns top 50 stocks with latest OHLC data
- Returns empty list if cache is still empty

**Step 3: Local Storage Cache**
- Frontend caches the result in `localStorage`
- Key: `FEATURED_STOCKS_CACHE_KEY`
- Reused on subsequent page loads (fast)

**Step 4: Display**
- Frontend renders homepage with top 50 stocks (or shows loading state if cache not ready)
- Live websocket data overlays current prices

---

## Database Schema

### `stocks` table (unchanged - always 800 stocks)
```sql
- stock_id (PK)
- ticker
- name
- exchange_id
- market_cap (updated daily)  ← Only this column is updated
- ... other stock fields
```

### `featured_stocks_cache` table (NEW - top 50 ranking)
```sql
- cache_id (PK)
- stock_id (FK → stocks.stock_id)
- sort_order (1-50)
- cached_at (TIMESTAMP)
- UNIQUE(stock_id)
```
---

## How Frequently-Changing Values Work

### Market Cap Updates
- **When:** Every day at 9:00 AM
- **What updates:** Only `stocks.market_cap` column
- **What doesn't change:** The 800 stocks in the table

### OHLC Data Updates
- **When:** Continuously (as configured elsewhere)
- **Table:** `stock_market_data` (separate table)
- **Effect on featured:** None directly - OHLC is fetched when API is called

### Featured Ranking
- **When:** Every day at 9:00 AM
- **Based on:** Current market caps in `stocks` table
- **Stored in:** `featured_stocks_cache` table

---

## Lifecycle Diagram

```
Backend Startup (0 seconds)
    ↓
Setup database
    ↓
✅ Service READY (port 8040 available)
    ├─→ No calculations at startup
    ├─→ Load previous day's cache (if exists)
    └─→ Schedule ranking for 9:00 AM
    
    ↓ (Frontend can start immediately)
    ↓
Frontend Startup
    ↓
GET /stocks/featured (or check /stocks/featured/health first)
    ↓
If cache has data: Show top 50
If cache empty: Show loading/empty state
    ↓
    ↓ (Every day at 9:00 AM)
    ↓
Ranking Calculation Runs (background, 2-3 min)
    ├─→ Update market caps
    ├─→ Rank top 50
    └─→ Refresh cache
    ↓
Tomorrow 9:00 AM: Repeat
```

---

## Files Modified / Created

### New Files
1. **`FeaturedStockCache.java`**
   - JPA entity for the cache table
   - Maps to `featured_stocks_cache` table
   - Fields: `cacheId`, `stock`, `sortOrder`, `cachedAt`

2. **`FeaturedStockCacheRepository.java`**
   - Repository to query and manage the cache
   - Methods: `findAllByOrderBySortOrderAsc()`, `deleteAll()`, etc.

### Modified Files
1. **`FeaturedStockRefreshService.java`**
   - ✅ No startup calculations
   - ✅ Schedules ranking for 9:00 AM daily
   - ✅ Runs only at 9:00 AM (or when laptop resumes after 9 AM)

2. **`StockController.java`**
   - ✅ `GET /stocks/featured` - fetch top 50 from cache
   - ✅ `GET /stocks/featured/health` - check if cache is ready

3. **`StockService.java`**
   - ✅ `getFeaturedStocks()` - reads from cache
   - ✅ `getFeaturedCacheStatus()` - cache readiness status

4. **`data.sql`**
   - Creates `featured_stocks_cache` table
   - Ensures `market_cap` column exists
   - Sets up indexes for fast lookups

---

## Key Benefits

✅ **Service starts instantly** - No blocking calculations at startup

✅ **Stocks table unchanged** - Always 800 stocks, only market caps update

✅ **Cache persists** - Previous day's top 50 available even if laptop was off

✅ **Automatic scheduling** - Ranks daily at 9:00 AM automatically

✅ **Frontend ready anytime** - Can display cache or show loading state

✅ **Graceful degradation** - If cache empty, returns empty list

✅ **Health check endpoint** - `/stocks/featured/health` shows cache status

✅ **Efficient** - Only market caps update, no stock list modifications

---

## Timing

**Service startup:**
- Time: 0-2 seconds
- No calculations
- Immediately ready

**Ranking calculation:**
- Time: ~2-3 minutes (800 API calls to Massive)
- Happens every day at 9:00 AM
- Can wait if laptop is off

**Frontend readiness:**
- Can start immediately
- Shows previous day's cache if available
- Or shows loading state while waiting

---

## Configuration

In `application.properties`:
```properties
# Enable daily refresh at 9:00 AM
massive.featured.daily-refresh-enabled=true

# API key for Massive (required)
massive.api.key=YOUR_API_KEY

# How many top stocks to cache (default: 50)
massive.featured.target-count=50
```

---

## Testing the Implementation

1. **Start backend**
   ```bash
   docker-compose up stock-service
   ```
   - Service should start in seconds (not minutes) ✅

2. **Check cache status immediately**
   ```bash
   curl http://localhost:8040/stocks/featured/health
   ```
   - If cache exists from yesterday: `ready: true`
   - If first time starting: `ready: false`

3. **Fetch featured stocks**
   ```bash
   curl http://localhost:8040/stocks/featured
   ```
   - Returns top 50 if cache has data
   - Returns empty array `[]` if cache is empty

4. **Wait for 9:00 AM** or simulate time

5. **Check database after 9 AM**
   ```sql
   SELECT COUNT(*) FROM featured_stocks_cache;  -- Should be 50
   SELECT COUNT(*) FROM stocks;                 -- Should be 800 (unchanged)
   ```

6. **Verify market caps updated**
   ```sql
   SELECT DISTINCT market_cap FROM stocks WHERE market_cap IS NOT NULL LIMIT 10;
   ```

---

## Summary

🎯 **Your requirements - DONE:**
- ✅ Service starts immediately (0 seconds)
- ✅ 800 stocks table stays unchanged
- ✅ Only market cap updates daily
- ✅ Top 50 ranking in separate cache
- ✅ Ranking runs every day at 9:00 AM
- ✅ Cache persists (survives restarts/laptop shutdown)
- ✅ Frontend always has something to use
- ✅ Frontend can start immediately
- ✅ Previous day's cache available even if laptop was off

