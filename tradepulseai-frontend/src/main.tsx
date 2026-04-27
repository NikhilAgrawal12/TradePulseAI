import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import { library } from '@fortawesome/fontawesome-svg-core'
import { fas } from '@fortawesome/free-solid-svg-icons'
import { CartProvider } from './context/CartContext'
import { OrdersProvider } from './context/OrdersContext'
import { WatchlistProvider } from './context/WatchlistContext'
import './index.css'
import App from './App.tsx'

library.add(fas)

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <CartProvider>
        <OrdersProvider>
          <WatchlistProvider>
            <App />
          </WatchlistProvider>
        </OrdersProvider>
      </CartProvider>
    </BrowserRouter>
  </StrictMode>,
)
