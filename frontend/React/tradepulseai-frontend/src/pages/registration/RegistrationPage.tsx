import { useEffect, useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router";
import axios from "axios";
import { Header } from "../../components/Header.tsx";
import "./RegistrationPage.css";

export function RegistrationPage() {
  const navigate = useNavigate();
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);

  useEffect(() => {
    document.title = "Register | TradePulseAI";
  }, []);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError("");
    setLoading(true);

    const formData = new FormData(event.currentTarget);

    const firstName = formData.get("firstName") as string;
    const lastName = formData.get("lastName") as string;
    const email = formData.get("email") as string;
    const phoneNumber = formData.get("phoneNumber") as string;
    const dateOfBirth = formData.get("dateOfBirth") as string;
    const addressLine1 = formData.get("addressLine1") as string;
    const addressLine2 = (formData.get("addressLine2") as string) || "";
    const city = formData.get("city") as string;
    const state = formData.get("state") as string;
    const postalCode = formData.get("postalCode") as string;
    const country = formData.get("country") as string;
    const password = formData.get("password") as string;
    const confirmPassword = formData.get("confirmPassword") as string;

    // Validate passwords match
    if (password !== confirmPassword) {
      setError("Passwords do not match");
      setLoading(false);
      return;
    }

    try {
      // Step 1: Register user in auth-service
      await axios.post("/auth/register", {
        email,
        password,
      });

      // Step 2: Create customer in cust-service
      const registeredDate = new Date().toISOString().split('T')[0];

      await axios.post("/api/customers", {
        firstName,
        lastName,
        email,
        phoneNumber,
        dateOfBirth,
        addressLine1,
        addressLine2,
        city,
        state,
        postalCode,
        country,
        registeredDate,
      });

      // Success - redirect to login
      navigate("/login");
    } catch (err) {
      if (axios.isAxiosError(err)) {
        const message = err.response?.data?.message;
        setError(typeof message === "string" ? message : "Registration failed");
      } else {
        setError("Registration failed");
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <Header />
      <main className="registration-page">
        <section className="registration-card" aria-labelledby="registration-title">
          <p className="registration-eyebrow">Get started</p>
          <h1 id="registration-title">Create your TradePulseAI account</h1>
          <p className="registration-subtitle">Set up your profile to track insights, portfolios, and live market activity.</p>

          {error && <div className="registration-error">{error}</div>}

          <form className="registration-form" onSubmit={handleSubmit}>
            <label htmlFor="first-name">First Name</label>
            <input id="first-name" type="text" name="firstName" autoComplete="given-name" required disabled={loading} />

            <label htmlFor="last-name">Last Name</label>
            <input id="last-name" type="text" name="lastName" autoComplete="family-name" required disabled={loading} />

            <label htmlFor="email">Email</label>
            <input id="email" type="email" name="email" autoComplete="email" required disabled={loading} />

            <label htmlFor="phone-number">Phone Number</label>
            <input id="phone-number" type="tel" name="phoneNumber" autoComplete="tel" pattern="[0-9+\s-]{7,15}" required disabled={loading} />

            <label htmlFor="date-of-birth">Date of Birth</label>
            <input id="date-of-birth" type="date" name="dateOfBirth" autoComplete="bday" required disabled={loading} />

            <label htmlFor="address-line-1">Address Line 1</label>
            <input id="address-line-1" type="text" name="addressLine1" autoComplete="street-address" required disabled={loading} />

            <label htmlFor="address-line-2">Address Line 2 (Optional)</label>
            <input id="address-line-2" type="text" name="addressLine2" autoComplete="street-address" disabled={loading} />

            <label htmlFor="city">City</label>
            <input id="city" type="text" name="city" autoComplete="address-level2" required disabled={loading} />

            <label htmlFor="state">State</label>
            <input id="state" type="text" name="state" autoComplete="address-level1" required disabled={loading} />

            <label htmlFor="postal-code">Postal Code</label>
            <input id="postal-code" type="text" name="postalCode" autoComplete="postal-code" required disabled={loading} />

            <label htmlFor="country">Country</label>
            <input id="country" type="text" name="country" autoComplete="country-name" required disabled={loading} />

            <label htmlFor="password">Password</label>
            <div className="password-field">
              <input
                id="password"
                type={showPassword ? "text" : "password"}
                name="password"
                autoComplete="new-password"
                minLength={8}
                required
                disabled={loading}
              />
              <button
                type="button"
                className="password-toggle"
                onClick={() => setShowPassword(!showPassword)}
                disabled={loading}
                aria-label={showPassword ? "Hide password" : "Show password"}
              >
                {showPassword ? "🙈" : "👁️"}
              </button>
            </div>

            <label htmlFor="confirm-password">Confirm Password</label>
            <div className="password-field">
              <input
                id="confirm-password"
                type={showConfirmPassword ? "text" : "password"}
                name="confirmPassword"
                autoComplete="new-password"
                minLength={8}
                required
                disabled={loading}
              />
              <button
                type="button"
                className="password-toggle"
                onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                disabled={loading}
                aria-label={showConfirmPassword ? "Hide password" : "Show password"}
              >
                {showConfirmPassword ? "🙈" : "👁️"}
              </button>
            </div>

            <label className="terms-check" htmlFor="accept-terms">
              <input id="accept-terms" type="checkbox" name="acceptTerms" required disabled={loading} />
              I agree to the terms and privacy policy.
            </label>

            <button type="submit" className="registration-submit-btn" disabled={loading}>
              {loading ? "Creating account..." : "Create account"}
            </button>
          </form>

          <p className="already-account-text">
            Already have an account? <Link to="/login">Sign in</Link>
          </p>
        </section>
      </main>
    </>
  );
}