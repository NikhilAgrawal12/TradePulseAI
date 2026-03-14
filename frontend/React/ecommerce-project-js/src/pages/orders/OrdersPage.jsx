import './OrdersPage.css';
import { Header } from '../../components/Header.jsx';
import axios from 'axios';
import { useState, useEffect } from "react";
import {OrdersGrid} from './OrdersGrid'

export function OrdersPage({cart}) {

    const[orders, setOrders] = useState([]);


    useEffect(() => {
        const FetchOrdersData = async () => {
            const response = await axios.get('/api/orders?expand=products')
            setOrders(response.data);
        }

        FetchOrdersData();
    },[]);

    return (
        <>
            <title>Orders</title>

            <Header cart={cart}/>

            <div className="orders-page">
                <div className="page-title">Your Orders</div>

                <OrdersGrid orders={orders}/>
            </div>
        </>
    );
}