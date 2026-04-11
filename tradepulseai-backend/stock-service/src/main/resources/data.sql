INSERT INTO stocks (symbol, name, sector, exchange, price, change_percent, rating_score, analyst_count, market_cap_billion, volume, recommendation) VALUES
('AAPL', 'Apple Inc.', 'Technology', 'NASDAQ', 211.84, 1.12, 4.7, 44, 3290, 58234000, 'BUY'),
('MSFT', 'Microsoft Corporation', 'Technology', 'NASDAQ', 427.63, 0.64, 4.8, 51, 3198, 28671000, 'BUY'),
('GOOGL', 'Alphabet Inc. Class A', 'Communication Services', 'NASDAQ', 176.29, -0.42, 4.6, 39, 2212, 31482000, 'HOLD'),
('AMZN', 'Amazon.com, Inc.', 'Consumer Discretionary', 'NASDAQ', 197.35, 0.95, 4.7, 47, 2086, 43229000, 'BUY'),
('NVDA', 'NVIDIA Corporation', 'Technology', 'NASDAQ', 942.18, 2.38, 4.9, 55, 2329, 50192000, 'BUY'),
('META', 'Meta Platforms, Inc.', 'Communication Services', 'NASDAQ', 514.27, -0.17, 4.5, 41, 1310, 21140000, 'HOLD'),
('TSLA', 'Tesla, Inc.', 'Consumer Discretionary', 'NASDAQ', 228.91, -1.94, 3.9, 36, 729, 92653000, 'SELL'),
('JPM', 'JPMorgan Chase & Co.', 'Financials', 'NYSE', 194.22, 0.28, 4.3, 29, 562, 11873000, 'BUY'),
('V', 'Visa Inc. Class A', 'Financials', 'NYSE', 286.51, 0.74, 4.6, 34, 576, 7031000, 'BUY'),
('WMT', 'Walmart Inc.', 'Consumer Staples', 'NYSE', 71.88, 0.19, 4.2, 26, 578, 17654000, 'HOLD'),
('PG', 'The Procter & Gamble Company', 'Consumer Staples', 'NYSE', 167.42, -0.08, 4.1, 21, 395, 6240000, 'HOLD'),
('XOM', 'Exxon Mobil Corporation', 'Energy', 'NYSE', 114.67, 1.43, 4.0, 23, 476, 15281000, 'BUY'),
('UNH', 'UnitedHealth Group Incorporated', 'Healthcare', 'NYSE', 524.95, -0.31, 4.4, 20, 484, 2940000, 'BUY'),
('JNJ', 'Johnson & Johnson', 'Healthcare', 'NYSE', 158.76, 0.11, 4.0, 18, 381, 7520000, 'HOLD'),
('BAC', 'Bank of America Corporation', 'Financials', 'NYSE', 40.83, -0.56, 3.8, 27, 318, 42966000, 'HOLD')
ON CONFLICT (symbol) DO UPDATE SET
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

DROP TABLE IF EXISTS stock_keywords;
