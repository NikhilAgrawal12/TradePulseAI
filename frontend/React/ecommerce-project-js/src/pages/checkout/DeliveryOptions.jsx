import dayjs from "dayjs";
import axios from 'axios';
import {formatMoney} from "../../utils/money.js";

export function DeliveryOptions({cartItem, deliveryOptions, loadCart}) {
    return(
        <div className="delivery-options">
            <div className="delivery-options-title">
                Choose a delivery option:
            </div>
            {
                deliveryOptions.map((deliveryOption) => {

                    const updateDeliveryOption = async () => {
                        await axios.put(`/api/cart-items/${cartItem.productId}`, {
                            deliveryOptionId: deliveryOption.id
                        });
                        await loadCart();
                    }

                    return (
                        <div key={deliveryOption.id} className="delivery-option" onClick={updateDeliveryOption}>
                            <input type="radio" checked={deliveryOption.id === cartItem.deliveryOptionId}
                                   onChange={()=>{}}xxxxxxxxx
                                   className="delivery-option-input"
                                   name={`delivery-option-${cartItem.productId}`}/>
                            <div>
                                <div className="delivery-option-date">
                                    {dayjs(deliveryOption.estimatedDeliveryTimeMs).format('dddd, MMMM D')}
                                    {/*Example : Tuesday, June 21*/}
                                </div>
                                <div className="delivery-option-price">
                                    {deliveryOption.priceCents > 0
                                        ? `${formatMoney(deliveryOption.priceCents)} - Shipping`
                                        : 'FREE Shipping'}
                                </div>
                            </div>
                        </div>
                    );
                })
            }


        </div>
    );
}