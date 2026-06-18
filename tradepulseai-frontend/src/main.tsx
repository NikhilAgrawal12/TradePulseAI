import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { CartProvider } from './context/CartContext'
import { OrdersProvider } from './context/OrdersContext'
import { WatchlistProvider } from './context/WatchlistContext'
import { WalletProvider } from './context/WalletContext'
import './index.css'
import App from './App.tsx'


createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <CartProvider>
        <OrdersProvider>
          <WatchlistProvider>
            <WalletProvider>
              <App />
            </WalletProvider>
          </WatchlistProvider>
        </OrdersProvider>
      </CartProvider>
    </BrowserRouter>
  </StrictMode>,
)
