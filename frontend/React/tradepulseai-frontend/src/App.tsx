import { Routes, Route } from 'react-router';
import {RegistrationPage} from "./pages/registration/RegistrationPage.tsx";
import {LoginPage} from "./pages/login/LoginPage.tsx";
import {AboutPage} from "./pages/about/AboutPage.tsx";
import './App.css'

function App() {

  return (
      <Routes>
        <Route path="login" element={<LoginPage />} />
        <Route path="registration" element={<RegistrationPage />} />
        <Route path="about" element={<AboutPage />} />
      </Routes>
  );

}

export default App
