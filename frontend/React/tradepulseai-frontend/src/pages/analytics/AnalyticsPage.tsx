import { useEffect } from "react";
import {Header} from "../../components/Header.tsx";

export function AnalyticsPage() {
    useEffect(() => {
        document.title = "Analytics | TradePulseAI";
    }, []);

    return (
        <>
            <Header />
            <h1>Analytics</h1>
            <p>This is Analytics Page</p>
        </>
    );
}
