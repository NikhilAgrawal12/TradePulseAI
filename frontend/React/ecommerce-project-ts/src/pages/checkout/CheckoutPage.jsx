import axios from 'axios';
import { Link } from 'react-router';
import { useState, useEffect } from 'react';
import './checkout-header.css'
import './CheckoutPage.css'
import {OrderSummary} from "./OrderSummary.jsx";
import {PaymentSummary} from "./PaymentSummary.jsx";

export function CheckoutPage({cart,loadCart}) {

    const [deliveryOptions, setDeliveryOptions] = useState([]);
    const  [paymentSummary, setPaymentSummary] = useState(null);


    useEffect(() => {
        const fetchCheckoutData = async () => {
            let response = await axios.get('/api/delivery-options?expand=estimatedDeliveryTime')
                setDeliveryOptions(response.data);


            response = await axios.get('/api/payment-summary')
                setPaymentSummary(response.data);

        }

        fetchCheckoutData();
    },[cart]);

    return (


        <>
            <title>Checkout</title>

            <div className="checkout-header">
                <div className="header-content">
                    <div className="checkout-header-left-section">
                        <Link to="/" className="header-link">
                            <img className="logo" src="../public/images/logo.png"/>
                            <img className="mobile-logo" src="../public/images/mobile-logo.png"/>
                        </Link>
                    </div>

                    <div className="checkout-header-middle-section">
                        Checkout (<a className="return-to-home-link"
                                     href="/React/ecommerce-project-js/public">3 items</a>)
                    </div>

                    <div className="checkout-header-right-section">
                        <img src="../public/images/icons/checkout-lock-icon.png"/>
                    </div>
                </div>
            </div>

            <div className="checkout-page">
                <div className="page-title">Review your order</div>

                <div className="checkout-grid">
                    <OrderSummary cart={cart} deliveryOptions={deliveryOptions} loadCart={loadCart}/>

                    <PaymentSummary paymentSummary={paymentSummary} loadCart={loadCart}/>
                </div>
            </div>
        </>

    );
}