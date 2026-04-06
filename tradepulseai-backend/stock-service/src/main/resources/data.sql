INSERT INTO stocks (id, symbol, name, sector, exchange, price, change_percent, rating_score, analyst_count, market_cap_billion, volume, recommendation) VALUES
('stk-001-aapl', 'AAPL', 'Apple Inc.', 'Technology', 'NASDAQ', 211.84, 1.12, 4.7, 44, 3290, 58234000, 'BUY'),
('stk-002-msft', 'MSFT', 'Microsoft Corporation', 'Technology', 'NASDAQ', 427.63, 0.64, 4.8, 51, 3198, 28671000, 'BUY'),
('stk-003-googl', 'GOOGL', 'Alphabet Inc. Class A', 'Communication Services', 'NASDAQ', 176.29, -0.42, 4.6, 39, 2212, 31482000, 'HOLD'),
('stk-004-amzn', 'AMZN', 'Amazon.com, Inc.', 'Consumer Discretionary', 'NASDAQ', 197.35, 0.95, 4.7, 47, 2086, 43229000, 'BUY'),
('stk-005-nvda', 'NVDA', 'NVIDIA Corporation', 'Technology', 'NASDAQ', 942.18, 2.38, 4.9, 55, 2329, 50192000, 'BUY'),
('stk-006-meta', 'META', 'Meta Platforms, Inc.', 'Communication Services', 'NASDAQ', 514.27, -0.17, 4.5, 41, 1310, 21140000, 'HOLD'),
('stk-007-tsla', 'TSLA', 'Tesla, Inc.', 'Consumer Discretionary', 'NASDAQ', 228.91, -1.94, 3.9, 36, 729, 92653000, 'SELL'),
('stk-008-jpm', 'JPM', 'JPMorgan Chase & Co.', 'Financials', 'NYSE', 194.22, 0.28, 4.3, 29, 562, 11873000, 'BUY'),
('stk-009-v', 'V', 'Visa Inc. Class A', 'Financials', 'NYSE', 286.51, 0.74, 4.6, 34, 576, 7031000, 'BUY'),
('stk-010-wmt', 'WMT', 'Walmart Inc.', 'Consumer Staples', 'NYSE', 71.88, 0.19, 4.2, 26, 578, 17654000, 'HOLD'),
('stk-011-pg', 'PG', 'The Procter & Gamble Company', 'Consumer Staples', 'NYSE', 167.42, -0.08, 4.1, 21, 395, 6240000, 'HOLD'),
('stk-012-xom', 'XOM', 'Exxon Mobil Corporation', 'Energy', 'NYSE', 114.67, 1.43, 4.0, 23, 476, 15281000, 'BUY'),
('stk-013-unh', 'UNH', 'UnitedHealth Group Incorporated', 'Healthcare', 'NYSE', 524.95, -0.31, 4.4, 20, 484, 2940000, 'BUY'),
('stk-014-jnj', 'JNJ', 'Johnson & Johnson', 'Healthcare', 'NYSE', 158.76, 0.11, 4.0, 18, 381, 7520000, 'HOLD'),
('stk-015-bac', 'BAC', 'Bank of America Corporation', 'Financials', 'NYSE', 40.83, -0.56, 3.8, 27, 318, 42966000, 'HOLD')
ON CONFLICT (id) DO UPDATE SET
symbol = EXCLUDED.symbol,
name = EXCLUDED.name,
sector = EXCLUDED.sector,
exchange = EXCLUDED.exchange,
price = EXCLUDED.price,
change_percent = EXCLUDED.change_percent,
rating_score = EXCLUDED.rating_score,
analyst_count = EXCLUDED.analyst_count,
market_cap_billion = EXCLUDED.market_cap_billion,
volume = EXCLUDED.volume,
recommendation = EXCLUDED.recommendation;

DELETE FROM stock_keywords sk
USING stock_keywords dup
WHERE sk.ctid < dup.ctid
  AND sk.stock_id = dup.stock_id
  AND sk.keyword = dup.keyword;

CREATE UNIQUE INDEX IF NOT EXISTS uq_stock_keywords_stock_id_keyword ON stock_keywords (stock_id, keyword);

INSERT INTO stock_keywords (stock_id, keyword) VALUES
('stk-001-aapl', 'iphone'),
('stk-001-aapl', 'consumer-tech'),
('stk-001-aapl', 'large-cap'),
('stk-002-msft', 'cloud'),
('stk-002-msft', 'ai'),
('stk-002-msft', 'enterprise'),
('stk-003-googl', 'search'),
('stk-003-googl', 'ads'),
('stk-003-googl', 'ai'),
('stk-004-amzn', 'ecommerce'),
('stk-004-amzn', 'aws'),
('stk-004-amzn', 'retail'),
('stk-005-nvda', 'semiconductors'),
('stk-005-nvda', 'gpu'),
('stk-005-nvda', 'ai'),
('stk-006-meta', 'social-media'),
('stk-006-meta', 'ads'),
('stk-006-meta', 'vr'),
('stk-007-tsla', 'ev'),
('stk-007-tsla', 'automotive'),
('stk-007-tsla', 'energy'),
('stk-008-jpm', 'banking'),
('stk-008-jpm', 'finance'),
('stk-008-jpm', 'dividend'),
('stk-009-v', 'payments'),
('stk-009-v', 'fintech'),
('stk-009-v', 'global'),
('stk-010-wmt', 'retail'),
('stk-010-wmt', 'defensive'),
('stk-010-wmt', 'consumer'),
('stk-011-pg', 'household'),
('stk-011-pg', 'dividend'),
('stk-011-pg', 'defensive'),
('stk-012-xom', 'oil'),
('stk-012-xom', 'gas'),
('stk-012-xom', 'energy'),
('stk-013-unh', 'healthcare'),
('stk-013-unh', 'insurance'),
('stk-013-unh', 'defensive'),
('stk-014-jnj', 'pharma'),
('stk-014-jnj', 'medical'),
('stk-014-jnj', 'dividend'),
('stk-015-bac', 'banking'),
('stk-015-bac', 'value'),
('stk-015-bac', 'interest-rates')
ON CONFLICT (stock_id, keyword) DO NOTHING;
