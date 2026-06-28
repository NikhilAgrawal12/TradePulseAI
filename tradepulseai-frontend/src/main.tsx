import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { CartProvider } from './context/CartContext'
import { OrdersProvider } from './context/OrdersContext'
import { WatchlistProvider } from './context/WatchlistContext'
import { WalletProvider } from './context/WalletContext'
import { MarketStatusProvider } from './context/MarketStatusContext'
import './index.css'
import App from './App.tsx'


createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <CartProvider>
        <OrdersProvider>
          <WatchlistProvider>
            <WalletProvider>
              <MarketStatusProvider>
                <App />
              </MarketStatusProvider>
            </WalletProvider>
          </WatchlistProvider>
        </OrdersProvider>
      </CartProvider>
    </BrowserRouter>
  </StrictMode>,
)
