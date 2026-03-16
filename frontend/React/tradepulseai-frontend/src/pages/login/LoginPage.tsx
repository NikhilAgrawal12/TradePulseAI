import { useEffect, type FormEvent } from "react";
import { Link } from "react-router";
import { Header } from "../../components/Header.tsx";
import "./LoginPage.css";

export function LoginPage() {
  useEffect(() => {
    document.title = "Login | TradePulseAI";
  }, []);

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
  };

  return (
    <>
      <Header />
      <main className="login-page">
        <section className="login-card" aria-labelledby="login-title">
          <p className="login-eyebrow">Welcome back</p>
          <h1 id="login-title">Sign in to TradePulseAI</h1>
          <p className="login-subtitle">Track your portfolio, watch markets, and manage orders in one place.</p>

          <form className="login-form" onSubmit={handleSubmit}>
            <label htmlFor="email">Email</label>
            <input id="email" type="email" name="email" placeholder="you@example.com" autoComplete="username" required />

            <label htmlFor="password">Password</label>
            <input id="password" type="password" name="password" placeholder="Enter your password" autoComplete="current-password" required />

            <div className="login-actions-row">
              <label className="remember-me" htmlFor="remember-me">
                <input id="remember-me" type="checkbox" name="remember-me" />
                Remember me
              </label>
              <a className="forgot-link" href="#">Forgot password?</a>
            </div>

            <button type="submit" className="login-submit-btn">Sign in</button>
          </form>

          <p className="create-account-text">
            New to TradePulseAI? <Link to="/registration">Create an account</Link>
          </p>
        </section>
      </main>
    </>
  );
}