import { useEffect } from "react";
import { Link } from "react-router";
import { Header } from "../../components/Header.tsx";
import "./AboutPage.css";

const features = [
	{
		icon: "📈",
		title: "Live Stock Prices",
		desc: "Track hundreds of stocks with real-time market data.",
	},
	{
		icon: "🤖",
		title: "ML-Powered Predictions",
		desc: "Our machine learning models analyze market signals and provide Buy, Sell, or Hold predictions.",
	},
	{
		icon: "📊",
		title: "Analytics & Dashboards",
		desc: "Interactive charts, sector heatmaps, and custom watchlists to help you understand market trends at a glance.",
	},
	{
		icon: "💼",
		title: "Trade Directly",
		desc: "Buy and sell stocks without leaving the platform. Fast, secure, and commission-friendly.",
	},
	{
		icon: "🗂️",
		title: "Portfolio Management",
		desc: "Track your holdings, P&L, allocation breakdown, and performance history all in one dashboard.",
	},
	{
		icon: "🔔",
		title: "Smart Alerts",
		desc: "Get timely updates for market moves, portfolio activity, and important account events.",
	},
];

export function AboutPage() {
	useEffect(() => {
		document.title = "About | TradePulse";
	}, []);

	return (
		<>
			<Header />
			<main className="about-page">
				{/* ── Hero ── */}
				<section className="about-hero">
					<div className="about-hero-inner">
						<span className="about-eyebrow">Who we are</span>
						<h1>
										Smarter trading, <br />built for real investors.
						</h1>
						<p className="about-hero-sub">
										TradePulse combines real-time market data, ML-powered predictions,
										deep stock insights, and seamless order execution in one platform.
						</p>
						<div className="about-hero-ctas">
							<Link to="/registration" className="about-btn-primary">
								Get started free
							</Link>
						</div>
					</div>
					<div className="about-hero-graphic" aria-hidden="true">
						<div className="hero-ring hero-ring-1" />
						<div className="hero-ring hero-ring-2" />
						<div className="hero-ring hero-ring-3" />
						<span className="hero-centre-icon">📡</span>
					</div>
				</section>

				{/* ── Mission ── */}
				<section className="about-mission">
					<div className="about-mission-text">
						<span className="about-eyebrow">Our mission</span>
						<h2>Make market decisions clearer and faster.</h2>
						<p>
							We built TradePulse to help users track markets, review insights,
							manage portfolios, and place trades from a single dashboard.
						</p>
						<p>
							Our focus is practical tools you can use now: live prices, stock
							insights, ML predictions, wallet and order flows, and portfolio
							tracking with clear performance visibility.
						</p>
					</div>
					<div className="about-mission-card" aria-hidden="true">
						<div className="mission-pill green">
							BUY &nbsp;↑ &nbsp;AAPL &nbsp;+2.4%
						</div>
						<div className="mission-pill red">
							SELL &nbsp;↓ &nbsp;TSLA &nbsp;−1.8%
						</div>
						<div className="mission-pill yellow">
							HOLD &nbsp;→ &nbsp;MSFT &nbsp;+0.3%
						</div>
						<p className="mission-card-label">ML signal feed</p>
					</div>
				</section>

				{/* ── Features ── */}
				<section className="about-features">
					<div className="about-section-header">
						<span className="about-eyebrow">What we offer</span>
						<h2>Everything you need to trade with confidence.</h2>
					</div>
					<div className="about-features-grid">
						{features.map((f) => (
							<div className="about-feature-card" key={f.title}>
								<span className="feature-icon">{f.icon}</span>
								<h3>{f.title}</h3>
								<p>{f.desc}</p>
							</div>
						))}
					</div>
				</section>

				{/* ── CTA ── */}
				<section className="about-cta">
					<h2>Ready to trade smarter?</h2>
					<p>
						Join thousands of investors already using TradePulse to make better
						decisions every day.
					</p>
					<Link to="/registration" className="about-btn-primary">
						Create your free account
					</Link>
				</section>
			</main>
		</>
	);
}