import { Link } from 'react-router';
import { useCart } from '../context/CartContext';
import './Header.css';

export function Header() {
    const { totalItems } = useCart();

    return (
        <header className="header">
            <div className="header-inner">
                <div className="left-section">
                    <Link to="/" className="header-link logo-link" aria-label="TradePulseAI home">
                        <img className="logo" src="images/logo.png" alt="TradePulseAI" />
                    </Link>
                </div>

                <nav className="center-section" aria-label="Main actions">
                    <Link className="nav-link header-link" to="/watchlist">Watchlist</Link>
                    <Link className="cart-link header-link" to="/checkout" aria-label="Go to cart">
                        <img className="cart-icon" src="images/icons/cart-icon.png" alt="Cart" />
                        <span className="cart-text">Cart</span>
                        {totalItems > 0 && <span className="cart-quantity">{totalItems}</span>}
                    </Link>
                </nav>

                <nav className="right-section" aria-label="Secondary navigation">
                    <Link className="nav-link header-link" to="/orders">Orders</Link>
                    <Link className="nav-link header-link about-link" to="/about">About Us</Link>
                </nav>
            </div>
        </header>
    );
}