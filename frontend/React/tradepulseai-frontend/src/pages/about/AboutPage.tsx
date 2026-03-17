import { useEffect } from "react";
import { Link } from "react-router";
import { Header } from "../../components/Header.tsx";
import "./AboutPage.css";

const features = [
	{
		icon: "📈",
		title: "Live Stock Prices",
		desc: "Real-time market data across thousands of equities, ETFs, and indices — all in one place.",
	},
	{
		icon: "🤖",
		title: "AI-Powered Predictions",
		desc: "Our machine learning models analyse market signals to give you clear Buy, Sell, or Hold recommendations.",
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
		desc: "Set price alerts and AI-trigger notifications so you never miss a critical market move.",
	},
];

const roadmap = [
	{
		tag: "Coming soon",
		title: "LLM-Powered Market Assistant",
		desc: "Ask anything — from earnings breakdowns to portfolio advice — and get natural-language answers powered by a large language model trained on financial data.",
	},
	{
		tag: "Planned",
		title: "Sentiment Analysis",
		desc: "Real-time analysis of news headlines and social signals to surface market sentiment alongside technical indicators.",
	},
	{
		tag: "Planned",
		title: "Automated Strategy Builder",
		desc: "Define rule-based or AI-assisted trading strategies and back-test them against historical data before going live.",
	},
];

export function AboutPage() {
	useEffect(() => {
		document.title = "About | TradePulseAI";
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
							Smarter trading, <br />powered by AI.
						</h1>
						<p className="about-hero-sub">
							TradePulseAI is a next-generation investment platform that combines
							real-time market data, AI-driven predictions, and seamless trade
							execution — giving every investor an edge once reserved for
							institutions.
						</p>
						<div className="about-hero-ctas">
							<Link to="/registration" className="about-btn-primary">
								Get started free
					x		</Link>
							<Link to="/analytics" className="about-btn-secondary">
								Explore analytics
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

				{/* ── Stats strip ── */}
				<section className="about-stats">
					{[
						{ value: "10K+", label: "Active traders" },
						{ value: "50K+", label: "Stocks tracked" },
						{ value: "94%", label: "Prediction accuracy" },
						{ value: "24 / 7", label: "Market monitoring" },
					].map((s) => (
						<div className="about-stat" key={s.label}>
							<strong>{s.value}</strong>
							<span>{s.label}</span>
						</div>
					))}
				</section>

				{/* ── Mission ── */}
				<section className="about-mission">
					<div className="about-mission-text">
						<span className="about-eyebrow">Our mission</span>
						<h2>Democratising intelligent investing.</h2>
						<p>
							Wall Street has always had access to sophisticated data science and
							predictive models. We built TradePulseAI so that retail investors get
							the same advantage — actionable AI insights, beautiful dashboards, and
							the ability to act on them instantly without switching apps.
						</p>
						<p>
							We believe transparent, data-driven decision-making leads to better
							outcomes. Every recommendation we surface is explainable, auditable,
							and yours to act on as you see fit.
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
						<p className="mission-card-label">AI signal feed</p>
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

				{/* ── Roadmap ── */}
				<section className="about-roadmap">
					<div className="about-section-header">
						<span className="about-eyebrow">What's next</span>
						<h2>The future of TradePulseAI.</h2>
						<p className="about-section-sub">
							We're constantly pushing the boundaries of what's possible at the
							intersection of finance and artificial intelligence.
						</p>
					</div>
					<div className="about-roadmap-list">
						{roadmap.map((r) => (
							<div className="about-roadmap-item" key={r.title}>
								<span
									className={`roadmap-tag ${
										r.tag === "Coming soon" ? "tag-soon" : "tag-planned"
									}`}
								>
									{r.tag}
								</span>
								<h3>{r.title}</h3>
								<p>{r.desc}</p>
							</div>
						))}
					</div>
				</section>

				{/* ── CTA ── */}
				<section className="about-cta">
					<h2>Ready to trade smarter?</h2>
					<p>
						Join thousands of investors already using TradePulseAI to make better
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