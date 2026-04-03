import {Fragment} from "react";
import { useNavigate } from 'react-router';
import dayjs from "dayjs";

export function OrderDetailsGrid({order}){
    const navigate = useNavigate();

    return(
        <div className="order-details-grid">
            {order.products.map((orderProduct) => {
                return (
                    <Fragment key={orderProduct.id}>
                        <div className="product-image-container">
                            <img src={orderProduct.product.image}/>
                        </div>

                        <div className="product-details">
                            <div className="product-name">
                                {orderProduct.product.name}
                            </div>
                            <div className="product-delivery-date">
                                Arriving on: {dayjs(orderProduct.estimatedDeliveryTimeMs).format('MMMM D')}
                            </div>
                            <div className="product-quantity">
                                Quantity: {orderProduct.quantity}
                            </div>
                            <button className="buy-again-button button-primary">
                                <img className="buy-again-icon" src="../public/images/icons/buy-again.png"/>
                                <span className="buy-again-message">Add to Cart</span>
                            </button>
                        </div>

                        <div className="product-actions">
                            <button
                                type="button"
                                className="track-package-button button-secondary"
                                onClick={() => navigate('/tracking')}
                            >
                                Track package
                            </button>
                        </div>
                    </Fragment>
                );
            })}

        </div>
    );
}