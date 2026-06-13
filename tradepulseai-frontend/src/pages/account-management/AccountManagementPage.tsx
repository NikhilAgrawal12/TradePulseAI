import { useEffect, useMemo, useState, type ChangeEvent, type FormEvent } from "react";
import { useNavigate } from "react-router";
import axios from "axios";
import { Header } from "../../components/Header.tsx";
import { getEmailFromToken, getStoredToken, getUserIdFromToken, setStoredToken } from "../../utils/auth";
import "./AccountManagementPage.css";

// Validation patterns
const VALIDATION_PATTERNS = {
  phoneNumber: /^\+?[0-9\- ]{7,15}$/,
  name: /^[A-Za-z\s'-]{1,100}$/,
};

const VALIDATION_MESSAGES = {
  phoneNumber: "Phone number must be 7-15 digits, optionally starting with +, and can contain spaces or hyphens",
  name: "Name can only contain letters, spaces, hyphens, and apostrophes (1-100 characters)",
};

type CustomerProfile = {
  userId: number;
  customerId?: number;
  firstName: string;
  lastName: string;
  email: string;
  phoneNumber: string;
  addressLine1: string;
  addressLine2: string;
  city: string;
  state: string;
  postalCode: string;
  country: string;
  dateOfBirth: string;
};

type CredentialsResponse = {
  userId: number;
  email: string;
};

type UpdateCredentialsResponse = {
  userId: number;
  email: string;
  token: string;
};

type CredentialsForm = {
  email: string;
};

const emptyProfile: CustomerProfile = {
  userId: 0,
  customerId: 0,
  firstName: "",
  lastName: "",
  email: "",
  phoneNumber: "",
  addressLine1: "",
  addressLine2: "",
  city: "",
  state: "",
  postalCode: "",
  country: "",
  dateOfBirth: "",
};

export function AccountManagementPage() {
  const navigate = useNavigate();
  const [profile, setProfile] = useState<CustomerProfile>(emptyProfile);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [credentials, setCredentials] = useState<CredentialsForm>({ email: "" });
  const [initialCredentialEmail, setInitialCredentialEmail] = useState("");
  const [credentialsLoading, setCredentialsLoading] = useState(true);
  const [credentialsSaving, setCredentialsSaving] = useState(false);
  const [credentialsError, setCredentialsError] = useState("");
  const [credentialsSuccess, setCredentialsSuccess] = useState("");
  const [validationErrors, setValidationErrors] = useState<Record<string, string>>({});

  const token = getStoredToken();
  const emailFromToken = getEmailFromToken(token);
  const userIdFromToken = getUserIdFromToken(token);

  const avatarLabel = useMemo(() => {
    const fullName = `${profile.firstName} ${profile.lastName}`.trim();
    if (fullName.length > 0) {
      return fullName
        .split(" ")
        .slice(0, 2)
        .map((part) => part[0]?.toUpperCase() ?? "")
        .join("");
    }
    return (credentials.email?.[0] ?? emailFromToken?.[0] ?? "U").toUpperCase();
  }, [profile.firstName, profile.lastName, credentials.email, emailFromToken]);

  useEffect(() => {
    document.title = "Account Management | TradePulseAI";
  }, []);

  useEffect(() => {
    if (!token || !userIdFromToken) {
      navigate("/login");
      return;
    }

    const loadProfile = async () => {
      setLoading(true);
      setError("");

      try {
        const response = await axios.get<CustomerProfile>(`/api/customers/user/${encodeURIComponent(userIdFromToken)}`, {
          headers: { Authorization: `Bearer ${token}` },
        });
        const loadedProfile = response.data;
        // Backend returns userId as primary key for customers; keep customerId fallback for compatibility.
        setProfile({
          ...loadedProfile,
          userId: loadedProfile.userId ?? loadedProfile.customerId ?? Number(userIdFromToken),
          customerId: loadedProfile.customerId ?? loadedProfile.userId ?? Number(userIdFromToken),
        });
      } catch (err) {
        if (axios.isAxiosError(err)) {
          if (err.response?.status === 404) {
            setError("Customer profile not found. Please complete registration details.");
          } else if (typeof err.response?.data?.message === "string") {
            setError(err.response.data.message);
          } else {
            setError("Unable to load profile details.");
          }
        } else {
          setError("Unable to load profile details.");
        }
      } finally {
        setLoading(false);
      }
    };

    loadProfile();
  }, [navigate, token, userIdFromToken]);

  useEffect(() => {
    if (!token || !userIdFromToken) {
      navigate("/login");
      return;
    }

    const loadCredentials = async () => {
      setCredentialsLoading(true);
      setCredentialsError("");

      try {
        const response = await axios.get<CredentialsResponse>(`/auth/users/${userIdFromToken}/credentials`, {
          headers: { Authorization: `Bearer ${token}` },
        });
        setCredentials({ email: response.data.email });
        setInitialCredentialEmail(response.data.email);
      } catch (err) {
        if (axios.isAxiosError(err) && typeof err.response?.data?.message === "string") {
          setCredentialsError(err.response.data.message);
        } else {
          setCredentialsError("Unable to load account credentials.");
        }
      } finally {
        setCredentialsLoading(false);
      }
    };

    loadCredentials();
  }, [navigate, token, userIdFromToken]);

  const handleChange = (event: ChangeEvent<HTMLInputElement>) => {
    const { name, value } = event.target;
    setProfile((prev) => ({ ...prev, [name]: value }));
    setSuccess("");
    setCredentialsSuccess("");

    const fieldError = validateField(name, value);
    setValidationErrors((prev) => ({
      ...prev,
      [name]: fieldError,
    }));
  };

  const validateField = (name: string, value: string): string => {
    if (!value.trim()) {
      switch (name) {
        case "firstName":
          return "First name is required";
        case "lastName":
          return "Last name is required";
        case "phoneNumber":
          return "Phone number is required";
        case "dateOfBirth":
          return "Date of birth is required";
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
      if (age < 18 || (age === 18 && (monthDiff < 0 || (monthDiff === 0 && today.getDate() < dob.getDate())))) {
        return "You must be at least 18 years old";
      }
    }

    return "";
  };

  const handleCredentialsChange = (event: ChangeEvent<HTMLInputElement>) => {
    const { name, value } = event.target;
    setCredentials((prev) => ({ ...prev, [name]: value }));
    setSuccess("");
    setCredentialsSuccess("");
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    // Every save attempt starts with a clean feedback state to avoid stale banners.
    setError("");
    setSuccess("");
    setCredentialsError("");
    setCredentialsSuccess("");

    if (!token) {
      navigate("/login");
      return;
    }

    // Validate critical fields on submit
    const fieldsToValidate = ["firstName", "lastName", "phoneNumber", "dateOfBirth"];
    const newErrors: Record<string, string> = {};
    for (const field of fieldsToValidate) {
      const value = profile[field as keyof CustomerProfile] || "";
      const error = validateField(field, String(value));
      if (error) {
        newErrors[field] = error;
      }
    }

    if (Object.keys(newErrors).length > 0) {
      setValidationErrors(newErrors);
      setError("We found issues in your input. Please correct them to proceed.");
      return;
    }

    const profileId = profile.userId || profile.customerId || Number(userIdFromToken || 0);
    if (!profileId) {
      setError("Customer profile id is missing.");
      return;
    }

    setSaving(true);
    setCredentialsSaving(true);

    try {
      const trimmedEmail = credentials.email.trim().toLowerCase();
      const hasEmailChange = trimmedEmail !== initialCredentialEmail;

      if (hasEmailChange) {
        if (!userIdFromToken) {
          navigate("/login");
          return;
        }

        const credentialsResponse = await axios.put<UpdateCredentialsResponse>(
          `/auth/users/${userIdFromToken}/credentials`,
          {
            email: trimmedEmail,
          },
          { headers: { Authorization: `Bearer ${token}` } },
        );

        // Keep the session type (remember me vs session-only) when replacing JWT.
        const rememberMe = localStorage.getItem("authToken") !== null;
        setStoredToken(credentialsResponse.data.token, rememberMe);

        setCredentials({
          email: credentialsResponse.data.email,
        });
        setInitialCredentialEmail(credentialsResponse.data.email);
        setProfile((prev) => ({ ...prev, email: credentialsResponse.data.email }));
      }

      await axios.put(
        `/api/customers/${profileId}`,
        {
          firstName: profile.firstName,
          lastName: profile.lastName,
          email: (credentials.email || profile.email).trim().toLowerCase(),
          phoneNumber: profile.phoneNumber,
          addressLine1: profile.addressLine1,
          addressLine2: profile.addressLine2,
          city: profile.city,
          state: profile.state,
          postalCode: profile.postalCode,
          country: profile.country,
          dateOfBirth: profile.dateOfBirth,
        },
        { headers: { Authorization: `Bearer ${token}` } },
      );

      setSuccess("Profile updated successfully.");
      if (hasEmailChange) {
        setCredentialsSuccess("Credentials updated successfully.");
      }
    } catch (err) {
      setError("Unable to save profile details.");
      if (axios.isAxiosError(err) && typeof err.response?.data?.message === "string") {
        setCredentialsError(err.response.data.message);
      } else {
        setCredentialsError("Unable to save account credentials.");
      }
    } finally {
      setSaving(false);
      setCredentialsSaving(false);
    }
  };

  return (
    <>
      <Header />
      <main className="am-page">
        <div className="am-container">
          <aside className="am-sidebar">
            <div className="am-avatar">
              <div className="am-avatar-circle">{avatarLabel}</div>
              <p className="am-avatar-name">{`${profile.firstName} ${profile.lastName}`.trim() || "Your Profile"}</p>
              <p className="am-avatar-email">{profile.email || emailFromToken || ""}</p>
            </div>
          </aside>

          <section className="am-panel">
            <form className="am-form" onSubmit={handleSubmit}>
              <div className="am-form-header">
                <h2>Profile</h2>
                <p>Update your account credentials and personal details.</p>
              </div>

              <div className="am-security-block">
                <h3>Account Credentials</h3>
                <div className="am-security-form">
                  {credentialsLoading && <p className="am-message">Loading credentials...</p>}
                  {credentialsError && <p className="am-message am-error">{credentialsError}</p>}
                  {credentialsSuccess && <p className="am-message am-success">{credentialsSuccess}</p>}

                  <div className="am-row">
                    <div className="am-group am-full">
                      <label htmlFor="account-email">Account Email</label>
                      <input
                        id="account-email"
                        name="email"
                        type="email"
                        value={credentials.email}
                        onChange={handleCredentialsChange}
                        required
                        disabled={credentialsLoading || credentialsSaving}
                      />
                    </div>
                  </div>

                </div>
              </div>

              {loading && <p className="am-message">Loading profile...</p>}
              {error && <p className="am-message am-error">{error}</p>}

               <div className="am-row">
                 <div className="am-group">
                   <label htmlFor="first-name">First Name</label>
                   <input id="first-name" name="firstName" type="text" value={profile.firstName} onChange={handleChange} required disabled={loading || saving} />
                   {validationErrors.firstName && <span className="am-validation-error">{validationErrors.firstName}</span>}
                 </div>
                 <div className="am-group">
                   <label htmlFor="last-name">Last Name</label>
                   <input id="last-name" name="lastName" type="text" value={profile.lastName} onChange={handleChange} required disabled={loading || saving} />
                   {validationErrors.lastName && <span className="am-validation-error">{validationErrors.lastName}</span>}
                 </div>
               </div>

               <div className="am-row">
                 <div className="am-group">
                   <label htmlFor="phone-number">Phone Number</label>
                   <input id="phone-number" name="phoneNumber" type="tel" value={profile.phoneNumber} onChange={handleChange} required disabled={loading || saving} />
                   {validationErrors.phoneNumber && <span className="am-validation-error">{validationErrors.phoneNumber}</span>}
                 </div>
                 <div className="am-group">
                   <label htmlFor="country">Country</label>
                   <input id="country" name="country" type="text" value={profile.country} onChange={handleChange} required disabled={loading || saving} />
                 </div>
               </div>

               <div className="am-row">
                 <div className="am-group">
                   <label htmlFor="date-of-birth">Date Of Birth</label>
                   <input id="date-of-birth" name="dateOfBirth" type="date" value={profile.dateOfBirth} onChange={handleChange} required disabled={loading || saving} />
                   {validationErrors.dateOfBirth && <span className="am-validation-error">{validationErrors.dateOfBirth}</span>}
                 </div>
               </div>

              <div className="am-row">
                <div className="am-group am-full">
                  <label htmlFor="address-line-1">Address Line 1</label>
                  <input id="address-line-1" name="addressLine1" type="text" value={profile.addressLine1} onChange={handleChange} required disabled={loading || saving} />
                </div>
              </div>

              <div className="am-row">
                <div className="am-group am-full">
                  <label htmlFor="address-line-2">Address Line 2</label>
                  <input id="address-line-2" name="addressLine2" type="text" value={profile.addressLine2} onChange={handleChange} disabled={loading || saving} />
                </div>
              </div>

              <div className="am-row">
                <div className="am-group">
                  <label htmlFor="city">City</label>
                  <input id="city" name="city" type="text" value={profile.city} onChange={handleChange} required disabled={loading || saving} />
                </div>
                <div className="am-group">
                  <label htmlFor="state">State</label>
                  <input id="state" name="state" type="text" value={profile.state} onChange={handleChange} required disabled={loading || saving} />
                </div>
              </div>

              <div className="am-row">
                <div className="am-group">
                  <label htmlFor="postal-code">Postal Code</label>
                  <input id="postal-code" name="postalCode" type="text" value={profile.postalCode} onChange={handleChange} required disabled={loading || saving} />
                </div>
              </div>

              <div className="am-actions">
                <button type="submit" className="am-btn-save" disabled={loading || saving}>{saving ? "Saving..." : "Save changes"}</button>
                {success && <p className="am-message am-success am-inline-success">{success}</p>}
              </div>
            </form>
          </section>
        </div>
      </main>
    </>
  );
}
