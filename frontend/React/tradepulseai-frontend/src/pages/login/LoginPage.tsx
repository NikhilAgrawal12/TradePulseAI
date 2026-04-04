import { useEffect, useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router";
import axios from "axios";
import { Header } from "../../components/Header.tsx";
import "./LoginPage.css";

export function LoginPage() {
  const navigate = useNavigate();
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    document.title = "Login | TradePulseAI";
  }, []);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError("");
    setLoading(true);

    const formData = new FormData(event.currentTarget);
    const email = formData.get("email") as string;
    const password = formData.get("password") as string;
    const rememberMe = formData.get("remember-me") === "on";

    try {
      const response = await axios.post("/auth/login", { email, password });
      const token = response.data?.token as string | undefined;

      if (!token) {
        setError("Login failed: token not received.");
        return;
      }

      // Keep only one active token store based on user choice.
      if (rememberMe) {
        localStorage.setItem("authToken", token);
        sessionStorage.removeItem("authToken");
      } else {
        sessionStorage.setItem("authToken", token);
        localStorage.removeItem("authToken");
      }

      navigate("/");
    } catch (err) {
      if (axios.isAxiosError(err)) {
        if (err.response?.status === 401) {
          setError("Invalid email or password.");
        } else if (err.response?.status === 400) {
          setError("Please check your login details and try again.");
        } else {
          setError("Unable to login right now. Please try again.");
        }
      } else {
        setError("Unable to login right now. Please try again.");
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <Header />
      <main className="login-page">
        <section className="login-card" aria-labelledby="login-title">
          <p className="login-eyebrow">Welcome back</p>
          <h1 id="login-title">Sign in to TradePulseAI</h1>
          <p className="login-subtitle">Track your portfolio, watch markets, and manage orders in one place.</p>

          {error && <div className="login-error">{error}</div>}

          <form className="login-form" onSubmit={handleSubmit}>
            <label htmlFor="email">Email</label>
            <input id="email" type="email" name="email" placeholder="you@example.com" autoComplete="username" required disabled={loading} />

            <label htmlFor="password">Password</label>
            <input id="password" type="password" name="password" placeholder="Enter your password" autoComplete="current-password" required disabled={loading} />

            <div className="login-actions-row">
              <label className="remember-me" htmlFor="remember-me">
                <input id="remember-me" type="checkbox" name="remember-me" disabled={loading} />
                Remember me
              </label>
              <a className="forgot-link" href="#">Forgot password?</a>
            </div>

            <button type="submit" className="login-submit-btn" disabled={loading}>
              {loading ? "Signing in..." : "Sign in"}
            </button>
          </form>

          <p className="create-account-text">
            New to TradePulseAI? <Link to="/registration">Create an account</Link>
          </p>
        </section>
      </main>
    </>
  );
}