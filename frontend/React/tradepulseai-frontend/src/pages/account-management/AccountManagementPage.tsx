import { useEffect, useState, type FormEvent } from "react";
import { Header } from "../../components/Header.tsx";
import "./AccountManagementPage.css";

type Tab = "profile" | "address" | "security";

export function AccountManagementPage() {
  useEffect(() => {
    document.title = "Account Management | TradePulseAI";
  }, []);

  const [activeTab, setActiveTab] = useState<Tab>("profile");

  const handleSubmit = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
  };

  return (
    <>
      <Header />
      <main className="am-page">
        <div className="am-container">

          {/* ── Sidebar ── */}
          <aside className="am-sidebar">
            <div className="am-avatar">
              <div className="am-avatar-circle">NA</div>
              <p className="am-avatar-name">Nikhil Agrawal</p>
              <p className="am-avatar-email">nikhil@example.com</p>
            </div>
            <nav className="am-nav">
              {(["profile", "address", "security"] as Tab[]).map((tab) => (
                <button
                  key={tab}
                  className={`am-nav-btn${activeTab === tab ? " active" : ""}`}
                  onClick={() => setActiveTab(tab)}
                >
                  {tab === "profile" && "👤 Personal Info"}
                  {tab === "address" && "📍 Address"}
                  {tab === "security" && "🔒 Security"}
                </button>
              ))}
            </nav>
          </aside>

          {/* ── Panel ── */}
          <section className="am-panel">

            {/* Personal Info */}
            {activeTab === "profile" && (
              <form className="am-form" onSubmit={handleSubmit}>
                <div className="am-form-header">
                  <h2>Personal Information</h2>
                  <p>Update your name, phone, gender, and date of birth.</p>
                </div>

                <div className="am-row">
                  <div className="am-group">
                    <label htmlFor="full-name">Full name</label>
                    <input id="full-name" type="text" name="fullName" defaultValue="Nikhil Agrawal" autoComplete="name" />
                  </div>
                  <div className="am-group">
                    <label htmlFor="phone">Phone number</label>
                    <input id="phone" type="tel" name="phone" defaultValue="+91 98765 43210" autoComplete="tel" />
                  </div>
                </div>

                <div className="am-row">
                  <div className="am-group">
                    <label htmlFor="dob">Date of birth</label>
                    <input id="dob" type="date" name="dateOfBirth" autoComplete="bday" />
                  </div>
                  <div className="am-group">
                    <label htmlFor="gender">Gender</label>
                    <select id="gender" name="gender" defaultValue="male">
                      <option value="" disabled>Select gender</option>
                      <option value="female">Female</option>
                      <option value="male">Male</option>
                      <option value="non-binary">Non-binary</option>
                      <option value="prefer-not-to-say">Prefer not to say</option>
                    </select>
                  </div>
                </div>

                <div className="am-row">
                  <div className="am-group am-full">
                    <label htmlFor="email">Email address</label>
                    <input id="email" type="email" name="email" defaultValue="nikhil@example.com" autoComplete="email" />
                  </div>
                </div>

                <div className="am-actions">
                  <button type="submit" className="am-btn-save">Save changes</button>
                  <button type="reset" className="am-btn-cancel">Discard</button>
                </div>
              </form>
            )}

            {/* Address */}
            {activeTab === "address" && (
              <form className="am-form" onSubmit={handleSubmit}>
                <div className="am-form-header">
                  <h2>Address Details</h2>
                  <p>Keep your delivery and billing address up to date.</p>
                </div>

                <div className="am-row">
                  <div className="am-group am-full">
                    <label htmlFor="address-line">Street address</label>
                    <input id="address-line" type="text" name="addressLine" placeholder="Street and area" autoComplete="street-address" />
                  </div>
                </div>

                <div className="am-row">
                  <div className="am-group">
                    <label htmlFor="city">City</label>
                    <input id="city" type="text" name="city" placeholder="City" autoComplete="address-level2" />
                  </div>
                  <div className="am-group">
                    <label htmlFor="state">State</label>
                    <input id="state" type="text" name="state" placeholder="State" autoComplete="address-level1" />
                  </div>
                </div>

                <div className="am-row">
                  <div className="am-group">
                    <label htmlFor="postal-code">Postal code</label>
                    <input id="postal-code" type="text" name="postalCode" placeholder="Postal code" autoComplete="postal-code" />
                  </div>
                  <div className="am-group">
                    <label htmlFor="country">Country</label>
                    <input id="country" type="text" name="country" placeholder="Country" autoComplete="country-name" />
                  </div>
                </div>

                <div className="am-actions">
                  <button type="submit" className="am-btn-save">Save changes</button>
                  <button type="reset" className="am-btn-cancel">Discard</button>
                </div>
              </form>
            )}

            {/* Security */}
            {activeTab === "security" && (
              <div className="am-form">
                <div className="am-form-header">
                  <h2>Security</h2>
                  <p>Manage your password and account access.</p>
                </div>

                <form onSubmit={handleSubmit} className="am-security-block">
                  <h3>Change password</h3>
                  <div className="am-row">
                    <div className="am-group am-full">
                      <label htmlFor="current-password">Current password</label>
                      <input id="current-password" type="password" name="currentPassword" placeholder="Enter current password" autoComplete="current-password" />
                    </div>
                  </div>
                  <div className="am-row">
                    <div className="am-group">
                      <label htmlFor="new-password">New password</label>
                      <input id="new-password" type="password" name="newPassword" placeholder="Create new password" autoComplete="new-password" minLength={8} />
                    </div>
                    <div className="am-group">
                      <label htmlFor="confirm-password">Confirm new password</label>
                      <input id="confirm-password" type="password" name="confirmPassword" placeholder="Re-enter new password" autoComplete="new-password" minLength={8} />
                    </div>
                  </div>
                  <div className="am-actions">
                    <button type="submit" className="am-btn-save">Update password</button>
                  </div>
                </form>

                <div className="am-danger-zone">
                  <h3>Danger zone</h3>
                  <p>Once you delete your account, all your data will be permanently removed. This action cannot be undone.</p>
                  <button type="button" className="am-btn-danger">Delete my account</button>
                </div>
              </div>
            )}

          </section>
        </div>
      </main>
    </>
  );
}
