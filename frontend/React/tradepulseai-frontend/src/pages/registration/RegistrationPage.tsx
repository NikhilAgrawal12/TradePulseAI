import { useEffect, type FormEvent } from "react";
import { Link } from "react-router";
import { Header } from "../../components/Header.tsx";
import "./RegistrationPage.css";

export function RegistrationPage() {
  useEffect(() => {
    document.title = "Register | TradePulseAI";
  }, []);

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
  };

  return (
    <>
      <Header />
      <main className="registration-page">
        <section className="registration-card" aria-labelledby="registration-title">
          <p className="registration-eyebrow">Get started</p>
          <h1 id="registration-title">Create your TradePulseAI account</h1>
          <p className="registration-subtitle">Set up your profile to track insights, portfolios, and live market activity.</p>

          <form className="registration-form" onSubmit={handleSubmit}>
            <label htmlFor="full-name">Full name</label>
            <input id="full-name" type="text" name="fullName" placeholder="Nikhil Agrawal" autoComplete="name" required />

            <label htmlFor="phone">Phone number</label>
            <input id="phone" type="tel" name="phone" placeholder="+91 98765 43210" autoComplete="tel" pattern="[0-9+\s-]{7,15}" required />

            <label htmlFor="date-of-birth">Date of birth</label>
            <input id="date-of-birth" type="date" name="dateOfBirth" autoComplete="bday" required />

            <label htmlFor="gender">Gender</label>
            <select id="gender" name="gender" defaultValue="" required>
              <option value="" disabled>Select gender</option>
              <option value="female">Female</option>
              <option value="male">Male</option>
              <option value="non-binary">Non-binary</option>
              <option value="prefer-not-to-say">Prefer not to say</option>
            </select>

            <label htmlFor="email">Email</label>
            <input id="email" type="email" name="email" placeholder="you@example.com" autoComplete="email" required />

            <label htmlFor="address-line">Address</label>
            <input id="address-line" type="text" name="addressLine" placeholder="Street and area" autoComplete="street-address" required />

            <label htmlFor="city">City</label>
            <input id="city" type="text" name="city" placeholder="City" autoComplete="address-level2" required />

            <label htmlFor="state">State</label>
            <input id="state" type="text" name="state" placeholder="State" autoComplete="address-level1" required />

            <label htmlFor="postal-code">Postal code</label>
            <input id="postal-code" type="text" name="postalCode" placeholder="Postal code" autoComplete="postal-code" required />

            <label htmlFor="password">Password</label>
            <input id="password" type="password" name="password" placeholder="Create a password" autoComplete="new-password" minLength={8} required />

            <label htmlFor="confirm-password">Confirm password</label>
            <input id="confirm-password" type="password" name="confirmPassword" placeholder="Re-enter your password" autoComplete="new-password" minLength={8} required />

            <label className="terms-check" htmlFor="accept-terms">
              <input id="accept-terms" type="checkbox" name="acceptTerms" required />
              I agree to the terms and privacy policy.
            </label>

            <button type="submit" className="registration-submit-btn">Create account</button>
          </form>

          <p className="already-account-text">
            Already have an account? <Link to="/login">Sign in</Link>
          </p>
        </section>
      </main>
    </>
  );
}