import { Link } from 'react-router';
import './Header.css';


export function Header( ) {
    let totalQuantity=0;


    return (
        <div className="header">
            <div className="left-section">
                <Link to="/" className="header-link">
                    <img className="logo"
                         src="images/logo.png"/>
                </Link>
            </div>

            <div className="middle-section">
                <input className="search-bar" type="text" placeholder="Search"/>

                <button className="search-button">
                    <img className="search-icon" src="images/icons/search-icon.png"/>
                </button>
            </div>

            <div className="right-section">
                <Link className="orders-link header-link" to="/orders">
                    <span className="orders-text">Orders</span>
                </Link>

                <Link className="cart-link header-link" to="/checkout">
                    <img className="cart-icon" src="images/icons/cart-icon.png"/>
                    <div className="cart-quantity">{totalQuantity}</div>
                    <div className="cart-text">Cart</div>
                </Link>
            </div>
        </div>

    );
}