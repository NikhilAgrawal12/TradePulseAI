import { useEffect, useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router";
import axios from "axios";
import { Header } from "../../components/Header.tsx";
import { setStoredToken } from "../../utils/auth";
import "./LoginPage.css";

// Validation patterns
const VALIDATION_PATTERNS = {
  email: /^[^\s@]+@[^\s@]+\.[^\s@]+$/,
  password: /^(?=.*[A-Z])(?=.*[a-z])(?=.*\d)(?=.*[^A-Za-z\d\s]).{8,}$/,
};

// Validation messages
const VALIDATION_MESSAGES = {
  email: "Email must be valid",
  password: "Password must contain at least one uppercase letter, one lowercase letter, one digit, and one special character",
};

const FORGOT_CODE_TTL_SECONDS = 80;

export function LoginPage() {
  const navigate = useNavigate();
  const [error, setError] = useState("");
  const [infoMessage, setInfoMessage] = useState("");
  const [loading, setLoading] = useState(false);
  const [loginValidationErrors, setLoginValidationErrors] = useState<Record<string, string>>({});
  const [showPassword, setShowPassword] = useState(false);
  const [showNewPassword, setShowNewPassword] = useState(false);
  const [showConfirmResetPassword, setShowConfirmResetPassword] = useState(false);
  const [showForgotPassword, setShowForgotPassword] = useState(false);
  const [forgotStep, setForgotStep] = useState<"email" | "code" | "reset">("email");
  const [forgotEmail, setForgotEmail] = useState("");
  const [resetToken, setResetToken] = useState("");
  const [attemptsRemaining, setAttemptsRemaining] = useState(3);
  const [secondsLeft, setSecondsLeft] = useState(0);
  const [forgotPasswordLoading, setForgotPasswordLoading] = useState(false);
  const [forgotPasswordMessage, setForgotPasswordMessage] = useState("");
  const [forgotPasswordError, setForgotPasswordError] = useState("");
  const [resetPasswordValidationErrors, setResetPasswordValidationErrors] = useState<Record<string, string>>({});

  useEffect(() => {
    document.title = "Login | TradePulse";
  }, []);

  const validateLogin = (email: string, password: string): Record<string, string> => {
    const errors: Record<string, string> = {};

    if (!email.trim()) {
      errors.email = "Email is required";
    } else if (!VALIDATION_PATTERNS.email.test(email)) {
      errors.email = VALIDATION_MESSAGES.email;
    }

    if (!password.trim()) {
      errors.password = "Password is required";
    } else if (password.length < 8) {
      errors.password = "Password must be at least 8 characters";
    }

    return errors;
  };

  useEffect(() => {
    if (!showForgotPassword || forgotStep !== "code" || secondsLeft <= 0) {
      return;
    }

    const timerId = window.setInterval(() => {
      setSecondsLeft((current) => {
        if (current <= 1) {
          window.clearInterval(timerId);
          resetForgotFlow("Forgot password session expired. Please sign in and try again.");
          return 0;
        }
        return current - 1;
      });
    }, 1000);

    return () => window.clearInterval(timerId);
  }, [showForgotPassword, forgotStep, secondsLeft]);

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError("");
    setInfoMessage("");
    setLoginValidationErrors({});
    setLoading(true);

    const formData = new FormData(event.currentTarget);
    const email = formData.get("email") as string;
    const password = formData.get("password") as string;

    const validationErrors = validateLogin(email, password);
    if (Object.keys(validationErrors).length > 0) {
      setLoginValidationErrors(validationErrors);
      setLoading(false);
      return;
    }

    try {
      const response = await axios.post("/auth/login", { email, password });
      const token = response.data?.token as string | undefined;

      if (!token) {
        setError("Login failed: token not received.");
        return;
      }

      setStoredToken(token, true);

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

  const resetForgotFlow = (message?: string) => {
    setShowForgotPassword(false);
    setForgotStep("email");
    setForgotEmail("");
    setResetToken("");
    setForgotPasswordError("");
    setForgotPasswordMessage("");
    setAttemptsRemaining(3);
    setSecondsLeft(0);
    setResetPasswordValidationErrors({});
    if (message) {
      setInfoMessage(message);
    }
  };

  const validateResetPassword = (newPassword: string, confirmPassword: string): Record<string, string> => {
    const errors: Record<string, string> = {};

    if (!newPassword.trim()) {
      errors["new-password"] = "New password is required";
    } else if (newPassword.length < 8) {
      errors["new-password"] = "Password must be at least 8 characters";
    } else if (!VALIDATION_PATTERNS.password.test(newPassword)) {
      errors["new-password"] = VALIDATION_MESSAGES.password;
    }

    if (!confirmPassword.trim()) {
      errors["confirm-password"] = "Confirm password is required";
    } else if (confirmPassword.length < 8) {
      errors["confirm-password"] = "Password must be at least 8 characters";
    } else if (!VALIDATION_PATTERNS.password.test(confirmPassword)) {
      errors["confirm-password"] = VALIDATION_MESSAGES.password;
    }

    if (newPassword && confirmPassword && newPassword !== confirmPassword) {
      errors["passwords-mismatch"] = "Passwords do not match";
    }

    return errors;
  };

  const openForgotFlow = () => {
    setError("");
    setShowForgotPassword(true);
    setForgotStep("email");
    setForgotEmail("");
    setResetToken("");
    setAttemptsRemaining(3);
    setSecondsLeft(0);
    setForgotPasswordError("");
    setForgotPasswordMessage("");
    setInfoMessage("");
  };

  const handleRequestCodeSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setForgotPasswordError("");
    setForgotPasswordMessage("");
    setInfoMessage("");
    setForgotPasswordLoading(true);

    const formData = new FormData(event.currentTarget);
    const email = (formData.get("forgot-email") as string | null)?.trim() ?? "";

    try {
      const response = await axios.post("/auth/forgot-password/request-code", { email });
      const message = response.data?.message as string | undefined;
      const maxAttempts = Number(response.data?.maxAttempts ?? 3);

      setForgotEmail(email);
      setForgotStep("code");
      setSecondsLeft(FORGOT_CODE_TTL_SECONDS);
      setAttemptsRemaining(Number.isFinite(maxAttempts) && maxAttempts > 0 ? maxAttempts : 3);
      setForgotPasswordMessage(message ?? "Verification code sent to your email.");
    } catch (err) {
      if (axios.isAxiosError(err)) {
        const message = err.response?.data?.message;
        if (typeof message === "string" && message.trim().length > 0) {
          setForgotPasswordError(message);
        } else if (err.response?.status === 404) {
          setForgotPasswordError("No account found with this email address.");
        } else if (err.response?.status === 400) {
          setForgotPasswordError("Please provide a valid email and a stronger password.");
        } else {
          setForgotPasswordError("Unable to reset password right now. Please try again.");
        }
      } else {
        setForgotPasswordError("Unable to reset password right now. Please try again.");
      }
    } finally {
      setForgotPasswordLoading(false);
    }
  };

  const handleResendCode = async () => {
    if (!forgotEmail || forgotPasswordLoading || attemptsRemaining <= 0) {
      return;
    }

    setForgotPasswordError("");
    setForgotPasswordMessage("");
    setInfoMessage("");
    setForgotPasswordLoading(true);

    setSecondsLeft(FORGOT_CODE_TTL_SECONDS);

    try {
      const response = await axios.post("/auth/forgot-password/request-code", { email: forgotEmail });
      const message = response.data?.message as string | undefined;
      setForgotPasswordMessage(message ?? "A new verification code was sent to your email.");
    } catch (err) {
      if (axios.isAxiosError(err)) {
        const message = err.response?.data?.message;
        if (typeof message === "string" && message.trim().length > 0) {
          setForgotPasswordError(message);
        } else {
          setForgotPasswordError("Unable to resend code right now. Please try again.");
        }
      } else {
        setForgotPasswordError("Unable to resend code right now. Please try again.");
      }
    } finally {
      setForgotPasswordLoading(false);
    }
  };

  const handleVerifyCodeSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setForgotPasswordError("");
    setForgotPasswordMessage("");
    setInfoMessage("");
    setForgotPasswordLoading(true);

    const formData = new FormData(event.currentTarget);
    const code = (formData.get("forgot-code") as string | null)?.trim() ?? "";

    try {
      const response = await axios.post("/auth/forgot-password/verify-code", { email: forgotEmail, code });
      const token = response.data?.resetToken as string | undefined;
      if (!token) {
        setForgotPasswordError("Verification succeeded but reset session is missing. Please try again.");
        return;
      }

      setResetToken(token);
      setForgotStep("reset");
      setForgotPasswordMessage(response.data?.message ?? "Code verified. Please set your new password.");
    } catch (err) {
      if (axios.isAxiosError(err)) {
        const message = err.response?.data?.message;
        if (err.response?.status === 410 || err.response?.status === 429) {
          resetForgotFlow(typeof message === "string" && message ? message : "Forgot password session expired. Please sign in and try again.");
          return;
        }
        if (typeof message === "string" && message.trim().length > 0) {
          setForgotPasswordError(message);
        } else {
          setForgotPasswordError("Code did not match.");
        }

        const remaining = Number(err.response?.data?.attemptsRemaining);
        if (Number.isFinite(remaining)) {
          setAttemptsRemaining(Math.max(0, remaining));
        }
      } else {
        setForgotPasswordError("Unable to verify code right now. Please try again.");
      }
    } finally {
      setForgotPasswordLoading(false);
    }
  };

  const handleResetPasswordSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setForgotPasswordError("");
    setForgotPasswordMessage("");
    setInfoMessage("");
    setResetPasswordValidationErrors({});
    setForgotPasswordLoading(true);

    const formData = new FormData(event.currentTarget);
    const newPassword = (formData.get("new-password") as string | null) ?? "";
    const confirmPassword = (formData.get("confirm-password") as string | null) ?? "";

    // Validate passwords
    const errors = validateResetPassword(newPassword, confirmPassword);
    if (Object.keys(errors).length > 0) {
      setResetPasswordValidationErrors(errors);
      setForgotPasswordLoading(false);
      return;
    }

    try {
      const response = await axios.post("/auth/forgot-password/reset", {
        email: forgotEmail,
        resetToken,
        newPassword,
        confirmPassword,
      });
      resetForgotFlow(response.data?.message ?? "Password updated successfully. Please sign in.");
    } catch (err) {
      if (axios.isAxiosError(err)) {
        const message = err.response?.data?.message;
        if (err.response?.status === 410 || err.response?.status === 404) {
          resetForgotFlow(typeof message === "string" && message ? message : "Forgot password session expired. Please sign in and try again.");
          return;
        }
        if (typeof message === "string" && message.trim().length > 0) {
          setForgotPasswordError(message);
        } else {
          setForgotPasswordError("Unable to reset password right now. Please try again.");
        }
      } else {
        setForgotPasswordError("Unable to reset password right now. Please try again.");
      }
    } finally {
      setForgotPasswordLoading(false);
    }
  };

  return (
    <>
      <Header />
      <main className="login-page">
        <section className="login-card" aria-labelledby="login-title">
          <p className="login-eyebrow">Welcome back</p>
          <h1 id="login-title">Sign in to TradePulse</h1>
          <p className="login-subtitle">Track your portfolio, watch markets, and manage orders in one place.</p>

          {!showForgotPassword && error && <div className="login-error">{error}</div>}
          {infoMessage && <div className="login-info">{infoMessage}</div>}

          {!showForgotPassword && (
            <form className="login-form" onSubmit={handleSubmit} noValidate>
              <label htmlFor="email">Email</label>
              <input
                id="email"
                type="email"
                name="email"
                autoComplete="username"
                required
                disabled={loading}
                onChange={() => {
                  setLoginValidationErrors((prev) => ({ ...prev, email: "" }));
                }}
              />
              {loginValidationErrors.email && <span className="validation-error">{loginValidationErrors.email}</span>}

              <label htmlFor="password">Password</label>
              <div className="password-input-row">
                <input
                  id="password"
                  type={showPassword ? "text" : "password"}
                  name="password"
                  autoComplete="current-password"
                  required
                  disabled={loading}
                  onChange={() => {
                    setLoginValidationErrors((prev) => ({ ...prev, password: "" }));
                  }}
                />
                <button
                  type="button"
                  className="password-visibility-btn"
                  onClick={() => setShowPassword((current) => !current)}
                  disabled={loading}
                  aria-label={showPassword ? "Hide password" : "Show password"}
                >
                  {showPassword ? "Hide" : "Show"}
                </button>
              </div>
              {loginValidationErrors.password && <span className="validation-error">{loginValidationErrors.password}</span>}

              <div className="login-actions-row">
                <button type="button" className="forgot-link" onClick={openForgotFlow} disabled={loading || forgotPasswordLoading}>
                  Forgot password?
                </button>
              </div>

              <button type="submit" className="login-submit-btn" disabled={loading}>
                {loading ? "Signing in..." : "Sign in"}
              </button>
            </form>
          )}

          {showForgotPassword && (
            <div className="forgot-password-card" aria-live="polite">

              {forgotStep === "email" && (
                <>
                  <p>Enter your registered email to receive a 4-digit verification code.</p>
                  <form className="forgot-password-form" onSubmit={handleRequestCodeSubmit}>
                    <label htmlFor="forgot-email">Account email</label>
                    <input
                      id="forgot-email"
                      type="email"
                      name="forgot-email"
                      autoComplete="email"
                      required
                      disabled={forgotPasswordLoading}
                    />

                    <button type="submit" className="forgot-submit-btn" disabled={forgotPasswordLoading}>
                      {forgotPasswordLoading ? "Sending..." : "Send code"}
                    </button>
                  </form>
                </>
              )}

              {forgotStep === "code" && (
                <>
                  <p className="forgot-code-intro">
                    Enter the 4-digit code sent to <strong>{forgotEmail}</strong>.
                  </p>
                  <p className="forgot-code-intro">If you can't see the code, please check spam.</p>
                  <div className="forgot-code-meta" role="status" aria-live="polite">
                    <span>Time left: {secondsLeft}s</span>
                    <span>Attempts left: {attemptsRemaining}</span>
                  </div>
                  <form className="forgot-password-form" onSubmit={handleVerifyCodeSubmit}>
                    <label htmlFor="forgot-code">Verification code</label>
                    <input
                      id="forgot-code"
                      type="text"
                      name="forgot-code"
                      inputMode="numeric"
                      pattern="[0-9]{4}"
                      maxLength={4}
                      required
                      disabled={forgotPasswordLoading}
                    />

                    <button type="submit" className="forgot-submit-btn" disabled={forgotPasswordLoading}>
                      {forgotPasswordLoading ? "Verifying..." : "Verify code"}
                    </button>
                    <button
                      type="button"
                      className="forgot-link"
                      onClick={() => {
                        void handleResendCode();
                      }}
                      disabled={forgotPasswordLoading || attemptsRemaining <= 0}
                    >
                      {attemptsRemaining <= 0 ? "No attempts left" : "Send code again"}
                    </button>
                  </form>
                </>
              )}

               {forgotStep === "reset" && (
                 <>
                   <p>Enter your new password and confirm it. It must be different from your old password.</p>
                   <form className="forgot-password-form" onSubmit={handleResetPasswordSubmit}>
                     <div className="form-group">
                       <label htmlFor="new-password">New password</label>
                        <div className="password-input-row">
                          <input
                            id="new-password"
                            type={showNewPassword ? "text" : "password"}
                            name="new-password"
                            autoComplete="new-password"
                            minLength={8}
                            required
                            disabled={forgotPasswordLoading}
                          />
                          <button
                            type="button"
                            className="password-visibility-btn"
                            onClick={() => setShowNewPassword((current) => !current)}
                            disabled={forgotPasswordLoading}
                            aria-label={showNewPassword ? "Hide new password" : "Show new password"}
                          >
                            {showNewPassword ? "Hide" : "Show"}
                          </button>
                        </div>
                       {resetPasswordValidationErrors["new-password"] && (
                         <span className="validation-error">{resetPasswordValidationErrors["new-password"]}</span>
                       )}
                     </div>

                     <div className="form-group">
                       <label htmlFor="confirm-password">Confirm password</label>
                        <div className="password-input-row">
                          <input
                            id="confirm-password"
                            type={showConfirmResetPassword ? "text" : "password"}
                            name="confirm-password"
                            autoComplete="new-password"
                            minLength={8}
                            required
                            disabled={forgotPasswordLoading}
                          />
                          <button
                            type="button"
                            className="password-visibility-btn"
                            onClick={() => setShowConfirmResetPassword((current) => !current)}
                            disabled={forgotPasswordLoading}
                            aria-label={showConfirmResetPassword ? "Hide confirm password" : "Show confirm password"}
                          >
                            {showConfirmResetPassword ? "Hide" : "Show"}
                          </button>
                        </div>
                       {resetPasswordValidationErrors["confirm-password"] && (
                         <span className="validation-error">{resetPasswordValidationErrors["confirm-password"]}</span>
                       )}
                       {resetPasswordValidationErrors["passwords-mismatch"] && (
                         <span className="validation-error">{resetPasswordValidationErrors["passwords-mismatch"]}</span>
                       )}
                     </div>

                     <button type="submit" className="forgot-submit-btn" disabled={forgotPasswordLoading}>
                       {forgotPasswordLoading ? "Updating..." : "Update password"}
                     </button>
                   </form>
                 </>
               )}

              {forgotPasswordError && <div className="login-error forgot-feedback">{forgotPasswordError}</div>}
              {forgotPasswordMessage && <div className="forgot-success forgot-feedback">{forgotPasswordMessage}</div>}
            </div>
          )}

          <p className="create-account-text">
            New to TradePulse? <Link to="/registration">Create an account</Link>
          </p>
        </section>
      </main>
    </>
  );
}