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
    const [authNotice, setAuthNotice] = useState('');
    const [token, setToken] = useState<string | null>(() => getStoredToken());
    const menuRef = useRef<HTMLDivElement | null>(null);

    const isLoggedIn = Boolean(token);
    const email = getEmailFromToken(token);

    const avatarLabel = useMemo(() => {
        const value = email ?? '';
        return value.length > 0 ? value[0].toUpperCase() : 'U';
    }, [email]);

    useEffect(() => {
        setMenuOpen(false);
        setAuthNotice('');
    }, [location.pathname]);

    useEffect(() => {
        const handleAuthRequired = (event: Event) => {
            const customEvent = event as CustomEvent<{ message?: string; redirectToLogin?: boolean }>;
            const message = customEvent.detail?.message;
            if (typeof message === 'string' && message.trim().length > 0) {
                setAuthNotice(message);
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
        });
    }, []);

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
        navigate('/login');
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
                        <img className="logo" src="images/logo.png" alt="TradePulseAI" />
                    </Link>
                </div>

                <nav className="center-section" aria-label="Main actions">
                    <Link className="watchlist-link nav-link header-link" to="/watchlist" onClick={handleProtectedNavigation}>
                        Watchlist
                        {totalWatchlistItems > 0 && <span className="watchlist-quantity">{totalWatchlistItems}</span>}
                    </Link>
                    <Link className="cart-link header-link" to="/checkout" aria-label="Go to cart" onClick={handleProtectedNavigation}>
                        <img className="cart-icon" src="images/icons/cart-icon.png" alt="Cart" />
                        <span className="cart-text">Cart</span>
                        {totalItems > 0 && <span className="cart-quantity">{totalItems}</span>}
                    </Link>
                </nav>

                <nav className="right-section" aria-label="Secondary navigation">
                    {isLoggedIn && <Link className="nav-link header-link" to="/portfolio" onClick={handleProtectedNavigation}>Portfolio</Link>}
                    <Link className="nav-link header-link" to="/orders" onClick={handleProtectedNavigation}>Orders</Link>
                    {!isLoggedIn && <Link className="nav-link header-link about-link" to="/about">About Us</Link>}
                    {isLoggedIn && (
                        <div className="avatar-menu" ref={menuRef}>
                            <button
                                type="button"
                                className="avatar-btn"
                                onClick={() => setMenuOpen((prev) => !prev)}
                                aria-haspopup="menu"
                                aria-expanded={menuOpen}
                                aria-label="Account menu"
                            >
                                {avatarLabel}
                            </button>

                            {menuOpen && (
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

            {authNotice && (
                <div className="header-notice" role="alert" aria-live="polite">
                    <span>{authNotice}</span>
                    <button type="button" className="header-notice-close" onClick={() => setAuthNotice('')} aria-label="Dismiss message">
                        ×
                    </button>
                </div>
            )}
        </header>
    );
}