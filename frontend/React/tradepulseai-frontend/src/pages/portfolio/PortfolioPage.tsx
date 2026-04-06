import { useEffect } from "react";
import {Header} from "../../components/Header.tsx";

export function PortfolioPage() {
    useEffect(() => {
        document.title = "Portfolio | TradePulseAI";
    }, []);

    return (
        <>
            <Header />
            <h1>Portfolio</h1>
            <p>This is Portfolio Page</p>
        </>
    );
}
