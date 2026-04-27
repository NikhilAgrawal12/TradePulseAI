import {Routes, Route} from 'react-router';
import {RegistrationPage} from "./pages/registration/RegistrationPage";
import {LoginPage} from "./pages/login/LoginPage";
import {AboutPage} from "./pages/about/AboutPage";
import {HomePage} from "./pages/home/HomePage";
import {AnalyticsPage} from "./pages/analytics/AnalyticsPage";
import {PortfolioPage} from "./pages/portfolio/PortfolioPage";
import {WatchlistPage} from "./pages/watchlist/WatchlistPage";
import {CheckoutPage} from "./pages/checkout/CheckoutPage";
import {PaymentPage} from "./pages/payment/PaymentPage";
import {OrdersPage} from "./pages/orders/OrdersPage";
import {AccountManagementPage} from "./pages/account-management/AccountManagementPage";
import './App.css'

function App() {

  return (
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="login" element={<LoginPage />} />
        <Route path="registration" element={<RegistrationPage />} />
        <Route path="about" element={<AboutPage />} />
        <Route path="analytics" element={<AnalyticsPage />} />
        <Route path="portfolio" element={<PortfolioPage />} />
        <Route path="watchlist" element={<WatchlistPage />} />
        <Route path="checkout" element={<CheckoutPage />} />
        <Route path="payment" element={<PaymentPage />} />
        <Route path="orders" element={<OrdersPage />} />
        <Route path="account-management" element={<AccountManagementPage />} />
      </Routes>
  );

}

export default App
