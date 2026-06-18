import { useEffect, useMemo, useRef, useState, type MouseEvent as ReactMouseEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router';
import { useCart } from '../context/CartContext';
import { useWatchlist } from '../context/WatchlistContext';
import {
    clearStoredToken,
    getEmailFromToken,
    getStoredToken,
    requireSignIn,
    subscribeToAuthChanges,
} from '../utils/auth';
import './Header.css';

export function Header() {
    const { totalItems } = useCart();
    const { totalWatchlistItems } = useWatchlist();
    const navigate = useNavigate();
    const location = useLocation();

    const [menuOpen, setMenuOpen] = useState(false);
    const [menuRoute, setMenuRoute] = useState(location.pathname);
    const [authNotice, setAuthNotice] = useState<{ message: string; route: string } | null>(null);
    const [token, setToken] = useState<string | null>(() => getStoredToken());
    const menuRef = useRef<HTMLDivElement | null>(null);

    const isLoggedIn = Boolean(token);
    const email = getEmailFromToken(token);
    const visibleMenuOpen = menuOpen && menuRoute === location.pathname;
    const visibleAuthNotice = authNotice?.route === location.pathname ? authNotice.message : '';

    const avatarLabel = useMemo(() => {
        const value = email ?? '';
        return value.length > 0 ? value[0].toUpperCase() : 'U';
    }, [email]);

    useEffect(() => {
        const handleAuthRequired = (event: Event) => {
            const customEvent = event as CustomEvent<{ message?: string; redirectToLogin?: boolean }>;
            const message = customEvent.detail?.message;
            if (typeof message === 'string' && message.trim().length > 0) {
                setAuthNotice({ message, route: location.pathname });
            }

            if (customEvent.detail?.redirectToLogin && location.pathname !== '/login') {
                navigate('/login');
            }
        };

        window.addEventListener('tradepulseai:auth-required', handleAuthRequired as EventListener);
        return () => {
            window.removeEventListener('tradepulseai:auth-required', handleAuthRequired as EventListener);
        };
    }, [location.pathname, navigate]);

    useEffect(() => {
        return subscribeToAuthChanges(() => {
            setToken(getStoredToken());
            setMenuOpen(false);
            setMenuRoute(location.pathname);
        });
    }, [location.pathname]);

    useEffect(() => {
        const handleClickOutside: EventListener = (event) => {
            if (menuRef.current && !menuRef.current.contains(event.target as Node)) {
                setMenuOpen(false);
            }
        };

        document.addEventListener('mousedown', handleClickOutside);
        return () => {
            document.removeEventListener('mousedown', handleClickOutside);
        };
    }, []);

    const handleLogout = () => {
        clearStoredToken();
        setMenuOpen(false);
        setMenuRoute(location.pathname);
        navigate('/');
    };

    const handleProtectedNavigation = (event: ReactMouseEvent<HTMLAnchorElement>) => {
        if (isLoggedIn) {
            return;
        }

        event.preventDefault();
        requireSignIn();
    };

    return (
        <header className="header">
            <div className="header-inner">
                <div className="left-section">
                    <Link to="/" className="header-link logo-link" aria-label="TradePulseAI home">
                        <img className="logo" src="/images/logo.png" alt="TradePulseAI" />
                    </Link>
                    <Link className="nav-link header-link about-link" to="/about">
                        <span className="nav-link-icon">ℹ️</span>
                        <span>About Us</span>
                    </Link>
                </div>

                <nav className="center-section" aria-label="Main actions">
                    <Link className="watchlist-link nav-link header-link" to="/watchlist" onClick={handleProtectedNavigation}>
                        <img className="watchlist-icon" src="images/icons/watchlist-icon.svg" alt="Watchlist" />
                        <span className="watchlist-text">Watchlist</span>
                        {totalWatchlistItems > 0 && <span className="watchlist-quantity">{totalWatchlistItems}</span>}
                    </Link>
                    <Link className="cart-link header-link" to="/checkout" aria-label="Go to cart" onClick={handleProtectedNavigation}>
                        <img className="cart-icon" src="images/icons/cart-icon.png" alt="Cart" />
                        <span className="cart-text">Cart</span>
                        {totalItems > 0 && <span className="cart-quantity">{totalItems}</span>}
                    </Link>
                </nav>

                <nav className="right-section" aria-label="Secondary navigation">
                    <Link className="nav-link header-link wallet-link" to="/wallet" onClick={handleProtectedNavigation}>
                        <span className="wallet-link-icon">💵</span>
                        <span className="wallet-link-text">Wallet</span>
                    </Link>
                    <Link className="nav-link header-link portfolio-link" to="/portfolio" onClick={handleProtectedNavigation}>
                        <span className="nav-link-icon">📊</span>
                        <span>Portfolio</span>
                    </Link>
                    <Link className="nav-link header-link orders-link" to="/orders" onClick={handleProtectedNavigation}>
                        <span className="nav-link-icon">🧾</span>
                        <span>Orders</span>
                    </Link>
                    {isLoggedIn && (
                        <div className="avatar-menu" ref={menuRef}>
                            <button
                                type="button"
                                className="avatar-btn"
                                onClick={() => {
                                    setMenuRoute(location.pathname);
                                    setMenuOpen((prev) => !prev);
                                }}
                                aria-haspopup="menu"
                                aria-expanded={visibleMenuOpen}
                                aria-label="Account menu"
                            >
                                {avatarLabel}
                            </button>

                            {visibleMenuOpen && (
                                <div className="avatar-dropdown" role="menu">
                                    <button
                                        type="button"
                                        className="avatar-dropdown-item"
                                        role="menuitem"
                                        onClick={() => {
                                            setMenuOpen(false);
                                            navigate('/account-management');
                                        }}
                                    >
                                        Profile
                                    </button>
                                    <button
                                        type="button"
                                        className="avatar-dropdown-item"
                                        role="menuitem"
                                        onClick={handleLogout}
                                    >
                                        Logout
                                    </button>
                                </div>
                            )}
                        </div>
                    )}
                </nav>
            </div>

            {visibleAuthNotice && (
                <div className="header-notice" role="alert" aria-live="polite">
                    <span>{visibleAuthNotice}</span>
                    <button type="button" className="header-notice-close" onClick={() => setAuthNotice(null)} aria-label="Dismiss message">
                        ×
                    </button>
                </div>
            )}
        </header>
    );
}