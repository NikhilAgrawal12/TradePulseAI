const form = document.querySelector("form");
const amountInput = document.getElementById("amount");
const fromSelect = document.getElementById("from");
const toSelect = document.getElementById("to");

// Output container (we will create a <div> to show result)
const container = document.querySelector(".container");
const resultDiv = document.createElement("div");
resultDiv.id = "result";
resultDiv.style.marginTop = "20px";
resultDiv.style.fontWeight = "bold";
container.appendChild(resultDiv);

const API_URL = "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/usd.json";
const FALLBACK_URL = "https://latest.currency-api.pages.dev/v1/currencies/usd.json";

async function getRates() {
    try {
        let response = await fetch(API_URL);
        if (!response.ok) throw new Error("Primary API failed");
        let data = await response.json();
        return data.usd; // contains all rates relative to USD
    } catch (err) {
        console.warn(err.message, "Using fallback API");
        let fallbackResponse = await fetch(FALLBACK_URL);
        let fallbackData = await fallbackResponse.json();
        return fallbackData.usd;
    }
}

form.addEventListener("submit", async (e) => {
    e.preventDefault(); // prevent page reload

    const amount = parseFloat(amountInput.value);
    const from = fromSelect.value;
    const to = toSelect.value;

    if (isNaN(amount)) {
        resultDiv.innerText = "Please enter a valid number!";
        return;
    }

    const rates = await getRates();

    let convertedAmount;

    if (from === "USD") {
        convertedAmount = amount * rates[to.toLowerCase()]; // USD -> target
    } else if (to === "USD") {
        convertedAmount = amount / rates[from.toLowerCase()]; // source -> USD
    } else {
        // source -> USD -> target
        const amountInUSD = amount / rates[from.toLowerCase()];
        convertedAmount = amountInUSD * rates[to.toLowerCase()];
    }

    resultDiv.innerText = `${amount} ${from} = ${convertedAmount.toFixed(2)} ${to}`;
});