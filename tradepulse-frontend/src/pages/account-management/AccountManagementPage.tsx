import { useEffect, useMemo, useState, type ChangeEvent, type FormEvent } from "react";
import { useNavigate } from "react-router";
import axios from "axios";
import { Header } from "../../components/Header.tsx";
import { SearchableDropdown } from "../../components/SearchableDropdown";
import {
  findCityByName,
  findCountryByName,
  findStateByName,
  getCityOptions,
  getCountryOptions,
  getStateOptions,
  type LocationCityOption,
  type LocationCountryOption,
  type LocationStateOption,
} from "../../utils/locationData";
import { getEmailFromToken, getStoredToken, getUserIdFromToken, setStoredToken } from "../../utils/auth";
import "./AccountManagementPage.css";

// Validation patterns
const VALIDATION_PATTERNS = {
  phoneNumber: /^\+?[0-9\- ]{7,15}$/,
  name: /^[A-Za-z\s'-]{1,100}$/,
  postalCode: /^(?=.*\d)[A-Za-z0-9][A-Za-z0-9\- ]{2,19}$/,
  password: /^(?=.*[A-Z])(?=.*[a-z])(?=.*\d)(?=.*[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>?/`~]).{8,}$/,
};

const VALIDATION_MESSAGES = {
  phoneNumber: "Phone number must be 7-15 digits, optionally starting with +, and can contain spaces or hyphens",
  name: "Name can only contain letters, spaces, hyphens, and apostrophes (1-100 characters)",
  postalCode: "Postal code must be 3-20 characters, include at least one digit, and use only letters, numbers, spaces, or hyphens",
  password: "Password must be at least 8 characters and include uppercase, lowercase, number, and special character",
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

type UpdateCredentialsResponse = {
  userId: number;
  email: string;
  token: string;
};

type ChangePasswordResponse = {
  message: string;
};

type CredentialsForm = {
  email: string;
};

type PasswordForm = {
  currentPassword: string;
  newPassword: string;
  confirmPassword: string;
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
  const [hasSubmittedProfile, setHasSubmittedProfile] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [credentials, setCredentials] = useState<CredentialsForm>({ email: "" });
  const [initialCredentialEmail, setInitialCredentialEmail] = useState("");
  const [credentialsLoading, setCredentialsLoading] = useState(true);
  const [credentialsSaving, setCredentialsSaving] = useState(false);
  const [credentialsError, setCredentialsError] = useState("");
  const [credentialsSuccess, setCredentialsSuccess] = useState("");
  const [passwordForm, setPasswordForm] = useState<PasswordForm>({
    currentPassword: "",
    newPassword: "",
    confirmPassword: "",
  });
  const [passwordSaving, setPasswordSaving] = useState(false);
  const [passwordError, setPasswordError] = useState("");
  const [passwordSuccess, setPasswordSuccess] = useState("");
  const [showPasswordEditor, setShowPasswordEditor] = useState(false);
  const [showPasswordValues, setShowPasswordValues] = useState<Record<keyof PasswordForm, boolean>>({
    currentPassword: false,
    newPassword: false,
    confirmPassword: false,
  });
  const [validationErrors, setValidationErrors] = useState<Record<string, string>>({});
  const [countryOptions, setCountryOptions] = useState<LocationCountryOption[]>([]);
  const [stateOptions, setStateOptions] = useState<LocationStateOption[]>([]);
  const [cityOptions, setCityOptions] = useState<LocationCityOption[]>([]);
  const [selectedCountryCode, setSelectedCountryCode] = useState("");
  const [selectedStateCode, setSelectedStateCode] = useState("");
  const [countriesLoading, setCountriesLoading] = useState(true);
  const [statesLoading, setStatesLoading] = useState(false);
  const [citiesLoading, setCitiesLoading] = useState(false);
  const [locationError, setLocationError] = useState("");

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
    document.title = "Account Management | TradePulse";
  }, []);

  useEffect(() => {
    let cancelled = false;

    const loadCountries = async () => {
      setCountriesLoading(true);
      setLocationError("");

      try {
        const countries = await getCountryOptions();
        if (!cancelled) {
          setCountryOptions(countries);
        }
      } catch {
        if (!cancelled) {
          setLocationError("Unable to load countries right now. Please try again.");
        }
      } finally {
        if (!cancelled) {
          setCountriesLoading(false);
        }
      }
    };

    void loadCountries();

    return () => {
      cancelled = true;
    };
  }, []);

  const loadStatesForCountry = async (countryCode: string) => {
    if (!countryCode) {
      setStateOptions([]);
      setCityOptions([]);
      return [] as LocationStateOption[];
    }

    setStatesLoading(true);
    setLocationError("");
    try {
      const nextStates = await getStateOptions(countryCode);
      setStateOptions(nextStates);
      return nextStates;
    } catch {
      setStateOptions([]);
      setLocationError("Unable to load states for the selected country.");
      return [] as LocationStateOption[];
    } finally {
      setStatesLoading(false);
    }
  };

  const loadCitiesForState = async (countryCode: string, stateCode: string) => {
    if (!countryCode || !stateCode) {
      setCityOptions([]);
      return [] as LocationCityOption[];
    }

    setCitiesLoading(true);
    setLocationError("");
    try {
      const nextCities = await getCityOptions(countryCode, stateCode);
      setCityOptions(nextCities);
      return nextCities;
    } catch {
      setCityOptions([]);
      setLocationError("Unable to load cities for the selected state.");
      return [] as LocationCityOption[];
    } finally {
      setCitiesLoading(false);
    }
  };

  const syncLocationSelections = async (nextProfile: CustomerProfile) => {
    const matchedCountry = await findCountryByName(nextProfile.country);
    if (!matchedCountry) {
      setSelectedCountryCode("");
      setSelectedStateCode("");
      setStateOptions([]);
      setCityOptions([]);
      return;
    }

    setSelectedCountryCode(matchedCountry.isoCode);
    const nextStates = await loadStatesForCountry(matchedCountry.isoCode);

    const matchedState = await findStateByName(matchedCountry.isoCode, nextProfile.state);
    if (!matchedState) {
      setSelectedStateCode("");
      setCityOptions([]);
      return;
    }

    if (!nextStates.some((state) => state.isoCode === matchedState.isoCode)) {
      setSelectedStateCode("");
      setCityOptions([]);
      return;
    }

    setSelectedStateCode(matchedState.isoCode);
    await loadCitiesForState(matchedCountry.isoCode, matchedState.isoCode);
  };

  useEffect(() => {
    if (!token || !userIdFromToken) {
      navigate("/login");
      return;
    }

    const loadProfile = async () => {
      setLoading(true);
      setCredentialsLoading(true);
      setError("");
      setCredentialsError("");

      try {
        const response = await axios.get<CustomerProfile>("/api/profile", {
          headers: { Authorization: `Bearer ${token}` },
        });
        const loadedProfile = response.data;
        // Backend returns userId as primary key for customers; keep customerId fallback for compatibility.
        setProfile({
          ...loadedProfile,
          userId: loadedProfile.userId ?? loadedProfile.customerId ?? Number(userIdFromToken),
          customerId: loadedProfile.customerId ?? loadedProfile.userId ?? Number(userIdFromToken),
        });
        void syncLocationSelections({
          ...loadedProfile,
          userId: loadedProfile.userId ?? loadedProfile.customerId ?? Number(userIdFromToken),
          customerId: loadedProfile.customerId ?? loadedProfile.userId ?? Number(userIdFromToken),
        });
        setCredentials({ email: (loadedProfile.email || "").trim().toLowerCase() });
        setInitialCredentialEmail((loadedProfile.email || "").trim().toLowerCase());
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
        setCredentialsLoading(false);
      }
    };

    loadProfile();
  }, [navigate, token, userIdFromToken]);

  const handleChange = (event: ChangeEvent<HTMLInputElement>) => {
    const { name, value } = event.target;
    setProfile((prev) => ({ ...prev, [name]: value }));
    setSuccess("");
    setCredentialsSuccess("");

    setValidationErrors((prev) => ({
      ...prev,
      [name]: "",
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

    if (name === "postalCode") {
      if (value.trim().length > 20) {
        return "Postal code cannot exceed 20 characters";
      }
      if (!VALIDATION_PATTERNS.postalCode.test(value.trim())) {
        return VALIDATION_MESSAGES.postalCode;
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

  const handlePasswordChange = (event: ChangeEvent<HTMLInputElement>) => {
    const { name, value } = event.target;
    setPasswordForm((prev) => ({
      ...prev,
      [name]: value,
    }));
    setPasswordError("");
    setPasswordSuccess("");
  };

  const togglePasswordValueVisibility = (field: keyof PasswordForm) => {
    setShowPasswordValues((prev) => ({
      ...prev,
      [field]: !prev[field],
    }));
  };

  const handleCountryChange = async (event: ChangeEvent<HTMLInputElement>) => {
    const nextCountryName = event.target.value;

    setSelectedCountryCode("");
    setSelectedStateCode("");
    setStateOptions([]);
    setCityOptions([]);
    setProfile((prev) => ({
      ...prev,
      country: nextCountryName,
      state: "",
      city: "",
    }));
    setSuccess("");
    setCredentialsSuccess("");
    setValidationErrors((prev) => ({
      ...prev,
      country: "",
      state: "",
      city: "",
    }));

    const matchedCountry = await findCountryByName(nextCountryName);
    if (!matchedCountry) {
      return;
    }

    setSelectedCountryCode(matchedCountry.isoCode);
    await loadStatesForCountry(matchedCountry.isoCode);
  };

  const handleStateChange = async (event: ChangeEvent<HTMLInputElement>) => {
    const nextStateName = event.target.value;

    setSelectedStateCode("");
    setCityOptions([]);
    setProfile((prev) => ({
      ...prev,
      state: nextStateName,
      city: "",
    }));
    setSuccess("");
    setCredentialsSuccess("");
    setValidationErrors((prev) => ({
      ...prev,
      state: "",
      city: "",
    }));

    if (!selectedCountryCode) {
      return;
    }

    const matchedState = await findStateByName(selectedCountryCode, nextStateName);
    if (!matchedState) {
      return;
    }

    setSelectedStateCode(matchedState.isoCode);
    await loadCitiesForState(selectedCountryCode, matchedState.isoCode);
  };

  const handleCityChange = (event: ChangeEvent<HTMLInputElement>) => {
    const nextCityName = event.target.value;
    setProfile((prev) => ({
      ...prev,
      city: nextCityName,
    }));
    setSuccess("");
    setCredentialsSuccess("");
    setValidationErrors((prev) => ({
      ...prev,
      city: "",
    }));
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setHasSubmittedProfile(true);

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
    const fieldsToValidate = ["firstName", "lastName", "phoneNumber", "dateOfBirth", "addressLine1", "city", "state", "postalCode", "country"];
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

    if (!selectedCountryCode) {
      newErrors.country = "Please choose a valid country from the list.";
    }

    if (profile.state && !selectedStateCode) {
      newErrors.state = "Please choose a valid state from the selected country.";
    }

    if (selectedCountryCode && selectedStateCode && cityOptions.length > 0) {
      const selectedCity = await findCityByName(selectedCountryCode, selectedStateCode, profile.city);
      if (!selectedCity) {
        setValidationErrors((prev) => ({
          ...prev,
          city: "Please choose a city from the selected state.",
        }));
        setError("We found issues in your input. Please correct them to proceed.");
        return;
      }
    }

    const profileId = profile.userId || profile.customerId || Number(userIdFromToken || 0);
    if (!profileId) {
      setError("Customer profile id is missing.");
      return;
    }

    setSaving(true);
    setCredentialsSaving(true);

    let hasEmailChange = false;
    try {
      const trimmedEmail = credentials.email.trim().toLowerCase();
      hasEmailChange = trimmedEmail !== initialCredentialEmail;

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
      if (hasEmailChange) {
        if (axios.isAxiosError(err) && typeof err.response?.data?.message === "string") {
          setCredentialsError(err.response.data.message);
        } else {
          setCredentialsError("Unable to save account credentials.");
        }
      } else {
        setError("Unable to save profile details.");
      }
    } finally {
      setSaving(false);
      setCredentialsSaving(false);
    }
  };

  const handlePasswordSubmit = async () => {
    setPasswordError("");
    setPasswordSuccess("");

    if (!token || !userIdFromToken) {
      navigate("/login");
      return;
    }

    if (!passwordForm.currentPassword || !passwordForm.newPassword || !passwordForm.confirmPassword) {
      setPasswordError("Please fill in all password fields.");
      return;
    }

    if (!VALIDATION_PATTERNS.password.test(passwordForm.newPassword)) {
      setPasswordError(VALIDATION_MESSAGES.password);
      return;
    }

    if (passwordForm.newPassword !== passwordForm.confirmPassword) {
      setPasswordError("New password and confirm password must match.");
      return;
    }

    setPasswordSaving(true);
    try {
      const response = await axios.put<ChangePasswordResponse>(
        `/auth/users/${userIdFromToken}/password`,
        passwordForm,
        { headers: { Authorization: `Bearer ${token}` } },
      );

      setPasswordSuccess(response.data.message || "Password updated successfully.");
      setPasswordForm({
        currentPassword: "",
        newPassword: "",
        confirmPassword: "",
      });
      setShowPasswordEditor(false);
      setShowPasswordValues({
        currentPassword: false,
        newPassword: false,
        confirmPassword: false,
      });
    } catch (err) {
      if (axios.isAxiosError(err) && typeof err.response?.data?.message === "string") {
        setPasswordError(err.response.data.message);
      } else {
        setPasswordError("Unable to change password right now.");
      }
    } finally {
      setPasswordSaving(false);
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

              {error && <p className="am-message am-error">{error}</p>}

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

                  <div className="am-row">
                    <div className="am-group am-full">
                      <label htmlFor="account-password">Password</label>
                      <input
                        id="account-password"
                        type="password"
                        value="••••••••••••"
                        readOnly
                        disabled
                      />
                      <div className="am-password-actions">
                        <button
                          type="button"
                          className="am-btn-text am-btn-text--primary"
                          onClick={() => {
                            setShowPasswordEditor((prev) => !prev);
                            setPasswordError("");
                            setPasswordSuccess("");
                          }}
                          disabled={passwordSaving}
                        >
                          {showPasswordEditor ? "Cancel" : "Change password"}
                        </button>
                      </div>
                    </div>
                  </div>

                  {passwordError && <p className="am-message am-error">{passwordError}</p>}
                  {passwordSuccess && <p className="am-message am-success">{passwordSuccess}</p>}

                  {showPasswordEditor && (
                    <>
                      <div className="am-row">
                        <div className="am-group am-full">
                          <label htmlFor="current-password">Current Password</label>
                          <div className="am-password-input-wrap">
                            <input
                              id="current-password"
                              name="currentPassword"
                              type={showPasswordValues.currentPassword ? "text" : "password"}
                              value={passwordForm.currentPassword}
                              onChange={handlePasswordChange}
                              disabled={passwordSaving}
                            />
                            <button
                              type="button"
                              className="am-btn-inline"
                              onClick={() => togglePasswordValueVisibility("currentPassword")}
                              disabled={passwordSaving}
                            >
                              {showPasswordValues.currentPassword ? "Hide" : "Show"}
                            </button>
                          </div>
                        </div>
                      </div>

                      <div className="am-row">
                        <div className="am-group">
                          <label htmlFor="new-password">New Password</label>
                          <div className="am-password-input-wrap">
                            <input
                              id="new-password"
                              name="newPassword"
                              type={showPasswordValues.newPassword ? "text" : "password"}
                              value={passwordForm.newPassword}
                              onChange={handlePasswordChange}
                              disabled={passwordSaving}
                            />
                            <button
                              type="button"
                              className="am-btn-inline"
                              onClick={() => togglePasswordValueVisibility("newPassword")}
                              disabled={passwordSaving}
                            >
                              {showPasswordValues.newPassword ? "Hide" : "Show"}
                            </button>
                          </div>
                        </div>

                        <div className="am-group">
                          <label htmlFor="confirm-password">Confirm Password</label>
                          <div className="am-password-input-wrap">
                            <input
                              id="confirm-password"
                              name="confirmPassword"
                              type={showPasswordValues.confirmPassword ? "text" : "password"}
                              value={passwordForm.confirmPassword}
                              onChange={handlePasswordChange}
                              disabled={passwordSaving}
                            />
                            <button
                              type="button"
                              className="am-btn-inline"
                              onClick={() => togglePasswordValueVisibility("confirmPassword")}
                              disabled={passwordSaving}
                            >
                              {showPasswordValues.confirmPassword ? "Hide" : "Show"}
                            </button>
                          </div>
                        </div>
                      </div>


                      <div className="am-security-actions">
                        <button
                          type="button"
                          className="am-btn-save"
                          onClick={() => {
                            void handlePasswordSubmit();
                          }}
                          disabled={passwordSaving}
                        >
                          {passwordSaving ? "Updating password..." : "Update password"}
                        </button>
                      </div>
                    </>
                  )}

                </div>
              </div>

              {loading && <p className="am-message">Loading profile...</p>}
              {locationError && <p className="am-message am-error">{locationError}</p>}

               <div className="am-row">
                 <div className="am-group">
                   <label htmlFor="first-name">First Name</label>
                   <input id="first-name" name="firstName" type="text" value={profile.firstName} onChange={handleChange} required disabled={loading || saving} />
                   {hasSubmittedProfile && validationErrors.firstName && <span className="am-validation-error">{validationErrors.firstName}</span>}
                 </div>
                 <div className="am-group">
                   <label htmlFor="last-name">Last Name</label>
                   <input id="last-name" name="lastName" type="text" value={profile.lastName} onChange={handleChange} required disabled={loading || saving} />
                   {hasSubmittedProfile && validationErrors.lastName && <span className="am-validation-error">{validationErrors.lastName}</span>}
                 </div>
               </div>

               <div className="am-row">
                 <div className="am-group">
                   <label htmlFor="phone-number">Phone Number</label>
                   <input id="phone-number" name="phoneNumber" type="tel" value={profile.phoneNumber} onChange={handleChange} required disabled={loading || saving} />
                   {hasSubmittedProfile && validationErrors.phoneNumber && <span className="am-validation-error">{validationErrors.phoneNumber}</span>}
                 </div>
               </div>

               <div className="am-row">
                 <div className="am-group">
                   <label htmlFor="date-of-birth">Date Of Birth</label>
                   <input id="date-of-birth" name="dateOfBirth" type="date" value={profile.dateOfBirth} onChange={handleChange} required disabled={loading || saving} />
                   {hasSubmittedProfile && validationErrors.dateOfBirth && <span className="am-validation-error">{validationErrors.dateOfBirth}</span>}
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
                  <label htmlFor="country">Country</label>
                  <SearchableDropdown
                    id="country"
                    name="country"
                    value={profile.country}
                    options={countryOptions.map((countryOption) => countryOption.name)}
                    disabled={loading || saving || countriesLoading}
                    loading={countriesLoading}
                    placeholder={countriesLoading ? "Loading countries..." : "Type or choose country"}
                    noOptionsText="No countries found"
                    onChange={(nextValue) => {
                      void handleCountryChange({ target: { value: nextValue } } as ChangeEvent<HTMLInputElement>);
                    }}
                  />
                  {hasSubmittedProfile && validationErrors.country && <span className="am-validation-error">{validationErrors.country}</span>}
                </div>
                <div className="am-group">
                  <label htmlFor="state">State</label>
                  <SearchableDropdown
                    id="state"
                    name="state"
                    value={profile.state}
                    options={stateOptions.map((stateOption) => stateOption.name)}
                    disabled={loading || saving || !selectedCountryCode || statesLoading}
                    loading={statesLoading}
                    placeholder={statesLoading ? "Loading states..." : "Type or choose state"}
                    noOptionsText="No states found"
                    onChange={(nextValue) => {
                      void handleStateChange({ target: { value: nextValue } } as ChangeEvent<HTMLInputElement>);
                    }}
                  />
                  {hasSubmittedProfile && validationErrors.state && <span className="am-validation-error">{validationErrors.state}</span>}
                </div>
              </div>

              <div className="am-row">
                <div className="am-group">
                  <label htmlFor="city">City</label>
                  <SearchableDropdown
                    id="city"
                    name="city"
                    value={profile.city}
                    options={cityOptions.map((cityOption) => cityOption.name)}
                    disabled={loading || saving || !selectedStateCode || citiesLoading}
                    loading={citiesLoading}
                    placeholder={citiesLoading ? "Loading cities..." : "Type or choose city"}
                    noOptionsText="No cities found"
                    onChange={(nextValue) => {
                      handleCityChange({ target: { value: nextValue } } as ChangeEvent<HTMLInputElement>);
                    }}
                  />
                  {hasSubmittedProfile && validationErrors.city && <span className="am-validation-error">{validationErrors.city}</span>}
                </div>
                <div className="am-group">
                  <label htmlFor="postal-code">Postal Code</label>
                  <input id="postal-code" name="postalCode" type="text" value={profile.postalCode} onChange={handleChange} required disabled={loading || saving} maxLength={20} />
                  {hasSubmittedProfile && validationErrors.postalCode && <span className="am-validation-error">{validationErrors.postalCode}</span>}
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
