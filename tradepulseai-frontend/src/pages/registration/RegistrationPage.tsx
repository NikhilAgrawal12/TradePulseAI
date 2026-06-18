import { useEffect, useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router";
import axios from "axios";
import { Header } from "../../components/Header.tsx";
import "./RegistrationPage.css";

// Validation patterns
const VALIDATION_PATTERNS = {
  password: /^(?=.*[A-Z])(?=.*[a-z])(?=.*\d)(?=.*[^A-Za-z\d\s]).{8,}$/,
  phoneNumber: /^\+?[0-9\- ]{7,15}$/,
  name: /^[A-Za-z\s'-]{1,100}$/,
};

// Validation messages
const VALIDATION_MESSAGES = {
  password: "Password must contain at least one uppercase letter, one lowercase letter, one digit, and one special character",
  phoneNumber: "Phone number must be 7-15 digits, optionally starting with +, and can contain spaces or hyphens",
  name: "Name can only contain letters, spaces, hyphens, and apostrophes (1-100 characters)",
};

export function RegistrationPage() {
  const navigate = useNavigate();
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [validationErrors, setValidationErrors] = useState<Record<string, string>>({});

  useEffect(() => {
    document.title = "Register | TradePulseAI";
  }, []);

  const validateField = (name: string, value: string): string => {
    if (!value.trim()) {
      switch (name) {
        case "firstName":
          return "First name is required";
        case "lastName":
          return "Last name is required";
        case "email":
          return "Email is required";
        case "phoneNumber":
          return "Phone number is required";
        case "dateOfBirth":
          return "Date of birth is required";
        case "addressLine1":
          return "Address line 1 is required";
        case "city":
          return "City is required";
        case "state":
          return "State is required";
        case "postalCode":
          return "Postal code is required";
        case "country":
          return "Country is required";
        case "password":
          return "Password is required";
        case "confirmPassword":
          return "Confirm password is required";
        default:
          return "";
      }
    }

    if (name === "firstName" || name === "lastName") {
      if (!VALIDATION_PATTERNS.name.test(value)) {
        return VALIDATION_MESSAGES.name;
      }
      if (value.length > 100) {
        return "Name cannot exceed 100 characters";
      }
    }

    if (name === "email") {
      const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
      if (!emailRegex.test(value)) {
        return "Email must be valid";
      }
    }

    if (name === "phoneNumber") {
      if (!VALIDATION_PATTERNS.phoneNumber.test(value)) {
        return VALIDATION_MESSAGES.phoneNumber;
      }
    }

    if (name === "dateOfBirth") {
      const dob = new Date(value);
      const today = new Date();
      const age = today.getFullYear() - dob.getFullYear();
      const monthDiff = today.getMonth() - dob.getMonth();
      if (monthDiff < 0 || (monthDiff === 0 && today.getDate() < dob.getDate())) {
        // User is not yet 18
      }
      if (age < 18 || (age === 18 && (monthDiff < 0 || (monthDiff === 0 && today.getDate() < dob.getDate())))) {
        return "You must be at least 18 years old";
      }
    }

    if (name === "password") {
      if (value.length < 8) {
        return "Password must be at least 8 characters";
      }
      if (!VALIDATION_PATTERNS.password.test(value)) {
        return VALIDATION_MESSAGES.password;
      }
    }

    return "";
  };

  const handleFieldChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target;
    const error = validateField(name, value);
    setValidationErrors((prev) => ({
      ...prev,
      [name]: error,
    }));
  };

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

    // Validate all fields
    const newErrors: Record<string, string> = {};
    const fields = ["firstName", "lastName", "email", "phoneNumber", "dateOfBirth",
                    "addressLine1", "city", "state", "postalCode", "country",
                    "password", "confirmPassword"];

    for (const field of fields) {
      const value = formData.get(field) as string;
      const error = validateField(field, value === undefined ? "" : value);
      if (error) {
        newErrors[field] = error;
      }
    }

    if (Object.keys(newErrors).length > 0) {
      setValidationErrors(newErrors);
      setLoading(false);
      return;
    }

    // Validate passwords match
    if (password !== confirmPassword) {
      setError("Passwords do not match");
      setLoading(false);
      return;
    }

    try {
      // Step 1: Register user in auth-service
      const registerResponse = await axios.post<{ userId: number }>("/auth/register", {
        email,
        password,
      });
      const userId = registerResponse.data?.userId;
      if (!userId) {
        setError("Registration failed: user id missing from auth-service response");
        setLoading(false);
        return;
      }

      // Step 2: Create customer in cust-service
      const registrationDate = new Date().toISOString();

      await axios.post("/api/customers", {
        userId,
        firstName,
        lastName,
        email,
        phoneNumber,
        dateOfBirth,
        // Keep temporary backward compatibility with older cust-service expecting `address`.
        address: addressLine1,
        addressLine1,
        addressLine2,
        city,
        state,
        postalCode,
        country,
        registrationDate,
        registeredDate: registrationDate,
      });

      // Success - redirect to login
      navigate("/login");
    } catch (err) {
      if (axios.isAxiosError(err)) {
        const data = err.response?.data;
        if (typeof data?.message === "string") {
          setError(data.message);
        } else if (data && typeof data === "object") {
          const firstError = Object.values(data).find((value) => typeof value === "string");
          setError(typeof firstError === "string" ? firstError : "Registration failed");
        } else {
          setError("Registration failed");
        }
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
            <div className="form-group">
              <label htmlFor="first-name">First Name</label>
              <input
                id="first-name"
                type="text"
                name="firstName"
                autoComplete="given-name"
                required
                disabled={loading}
                onChange={handleFieldChange}
                pattern="^[A-Za-z\s'-]{1,100}$"
              />
              {validationErrors.firstName && <span className="validation-error">{validationErrors.firstName}</span>}
            </div>

            <div className="form-group">
              <label htmlFor="last-name">Last Name</label>
              <input
                id="last-name"
                type="text"
                name="lastName"
                autoComplete="family-name"
                required
                disabled={loading}
                onChange={handleFieldChange}
                pattern="^[A-Za-z\s'-]{1,100}$"
              />
              {validationErrors.lastName && <span className="validation-error">{validationErrors.lastName}</span>}
            </div>

            <div className="form-group">
              <label htmlFor="email">Email</label>
              <input
                id="email"
                type="email"
                name="email"
                autoComplete="email"
                required
                disabled={loading}
                onChange={handleFieldChange}
              />
              {validationErrors.email && <span className="validation-error">{validationErrors.email}</span>}
            </div>

            <div className="form-group">
              <label htmlFor="phone-number">Phone Number</label>
              <input
                id="phone-number"
                type="tel"
                name="phoneNumber"
                autoComplete="tel"
                pattern="^\+?[0-9\- ]{7,15}$"
                required
                disabled={loading}
                onChange={handleFieldChange}
              />
              {validationErrors.phoneNumber && <span className="validation-error">{validationErrors.phoneNumber}</span>}
            </div>

            <div className="form-group">
              <label htmlFor="date-of-birth">Date of Birth</label>
              <input
                id="date-of-birth"
                type="date"
                name="dateOfBirth"
                autoComplete="bday"
                required
                disabled={loading}
                onChange={handleFieldChange}
              />
              {validationErrors.dateOfBirth && <span className="validation-error">{validationErrors.dateOfBirth}</span>}
            </div>

            <div className="form-group">
              <label htmlFor="address-line-1">Address Line 1</label>
              <input
                id="address-line-1"
                type="text"
                name="addressLine1"
                autoComplete="street-address"
                required
                disabled={loading}
                onChange={handleFieldChange}
              />
              {validationErrors.addressLine1 && <span className="validation-error">{validationErrors.addressLine1}</span>}
            </div>

            <div className="form-group">
              <label htmlFor="address-line-2">Address Line 2 (Optional)</label>
              <input
                id="address-line-2"
                type="text"
                name="addressLine2"
                autoComplete="street-address"
                disabled={loading}
              />
            </div>

            <div className="form-group">
              <label htmlFor="city">City</label>
              <input
                id="city"
                type="text"
                name="city"
                autoComplete="address-level2"
                required
                disabled={loading}
                onChange={handleFieldChange}
              />
              {validationErrors.city && <span className="validation-error">{validationErrors.city}</span>}
            </div>

            <div className="form-group">
              <label htmlFor="state">State</label>
              <input
                id="state"
                type="text"
                name="state"
                autoComplete="address-level1"
                required
                disabled={loading}
                onChange={handleFieldChange}
              />
              {validationErrors.state && <span className="validation-error">{validationErrors.state}</span>}
            </div>

            <div className="form-group">
              <label htmlFor="postal-code">Postal Code</label>
              <input
                id="postal-code"
                type="text"
                name="postalCode"
                autoComplete="postal-code"
                required
                disabled={loading}
                onChange={handleFieldChange}
              />
              {validationErrors.postalCode && <span className="validation-error">{validationErrors.postalCode}</span>}
            </div>

            <div className="form-group">
              <label htmlFor="country">Country</label>
              <input
                id="country"
                type="text"
                name="country"
                autoComplete="country-name"
                required
                disabled={loading}
                onChange={handleFieldChange}
              />
              {validationErrors.country && <span className="validation-error">{validationErrors.country}</span>}
            </div>

            <div className="form-group">
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
                  onChange={handleFieldChange}
                />
                <button
                  type="button"
                  className="password-toggle"
                  onClick={() => setShowPassword(!showPassword)}
                  disabled={loading}
                  aria-label={showPassword ? "Hide password" : "Show password"}
                >
                  {showPassword ? "Hide" : "Show"}
                </button>
              </div>
              {validationErrors.password && <span className="validation-error">{validationErrors.password}</span>}
            </div>

            <div className="form-group">
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
                  onChange={handleFieldChange}
                />
                <button
                  type="button"
                  className="password-toggle"
                  onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                  disabled={loading}
                  aria-label={showConfirmPassword ? "Hide password" : "Show password"}
                >
                  {showConfirmPassword ? "Hide" : "Show"}
                </button>
              </div>
              {validationErrors.confirmPassword && <span className="validation-error">{validationErrors.confirmPassword}</span>}
            </div>

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