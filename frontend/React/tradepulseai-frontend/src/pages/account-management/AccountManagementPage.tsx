import { useEffect, useMemo, useState, type ChangeEvent, type FormEvent } from "react";
import { useNavigate } from "react-router";
import axios from "axios";
import { Header } from "../../components/Header.tsx";
import { getEmailFromToken, getStoredToken } from "../../utils/auth";
import "./AccountManagementPage.css";

type CustomerProfile = {
  customerId: string;
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

const emptyProfile: CustomerProfile = {
  customerId: "",
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

  const token = getStoredToken();
  const emailFromToken = getEmailFromToken(token);

  const avatarLabel = useMemo(() => {
    const fullName = `${profile.firstName} ${profile.lastName}`.trim();
    if (fullName.length > 0) {
      return fullName
        .split(" ")
        .slice(0, 2)
        .map((part) => part[0]?.toUpperCase() ?? "")
        .join("");
    }
    return (emailFromToken?.[0] ?? "U").toUpperCase();
  }, [profile.firstName, profile.lastName, emailFromToken]);

  useEffect(() => {
    document.title = "Account Management | TradePulseAI";
  }, []);

  useEffect(() => {
    if (!token || !emailFromToken) {
      navigate("/login");
      return;
    }

    const loadProfile = async () => {
      setLoading(true);
      setError("");

      try {
        const response = await axios.get<CustomerProfile>(`/api/customers/email/${encodeURIComponent(emailFromToken)}`, {
          headers: { Authorization: `Bearer ${token}` },
        });
        setProfile(response.data);
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
  }, [emailFromToken, navigate, token]);

  const handleChange = (event: ChangeEvent<HTMLInputElement>) => {
    const { name, value } = event.target;
    setProfile((prev) => ({ ...prev, [name]: value }));
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    if (!token) {
      navigate("/login");
      return;
    }

    if (!profile.customerId) {
      setError("Customer profile id is missing.");
      return;
    }

    setSaving(true);
    setError("");
    setSuccess("");

    try {
      await axios.put(
        `/api/customers/${profile.customerId}`,
        {
          firstName: profile.firstName,
          lastName: profile.lastName,
          email: profile.email,
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
    } catch {
      setError("Unable to save profile details.");
    } finally {
      setSaving(false);
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
                <p>Update your account details and address information.</p>
              </div>

              {loading && <p className="am-message">Loading profile...</p>}
              {error && <p className="am-message am-error">{error}</p>}
              {success && <p className="am-message am-success">{success}</p>}

              <div className="am-row">
                <div className="am-group">
                  <label htmlFor="first-name">First Name</label>
                  <input id="first-name" name="firstName" type="text" value={profile.firstName} onChange={handleChange} required disabled={loading || saving} />
                </div>
                <div className="am-group">
                  <label htmlFor="last-name">Last Name</label>
                  <input id="last-name" name="lastName" type="text" value={profile.lastName} onChange={handleChange} required disabled={loading || saving} />
                </div>
              </div>

              <div className="am-row">
                <div className="am-group">
                  <label htmlFor="email">Email</label>
                  <input id="email" name="email" type="email" value={profile.email} onChange={handleChange} required disabled={loading || saving} />
                </div>
                <div className="am-group">
                  <label htmlFor="phone-number">Phone Number</label>
                  <input id="phone-number" name="phoneNumber" type="tel" value={profile.phoneNumber} onChange={handleChange} required disabled={loading || saving} />
                </div>
              </div>

              <div className="am-row">
                <div className="am-group">
                  <label htmlFor="date-of-birth">Date Of Birth</label>
                  <input id="date-of-birth" name="dateOfBirth" type="date" value={profile.dateOfBirth} onChange={handleChange} required disabled={loading || saving} />
                </div>
                <div className="am-group">
                  <label htmlFor="country">Country</label>
                  <input id="country" name="country" type="text" value={profile.country} onChange={handleChange} required disabled={loading || saving} />
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
              </div>
            </form>
          </section>
        </div>
      </main>
    </>
  );
}
