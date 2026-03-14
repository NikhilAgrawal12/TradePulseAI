const URL = "https://restcountries.com/v3.1/all?fields=name,capital,currencies";


const fact = document.querySelector(".fact");
const but = document.querySelector(".getFactButton");

const getCountriesFacts = async () => {
    console.log("getting data .....")
    let response = await fetch(URL);
    console.log(response);  //json format
    console.log(response.status);

    let data = await response.json();
    console.log(data);
    console.log(data[0].capital);
    console.log(data[0].name.common);


    fact.innerText = data[0].name.common;
    but.addEventListener("click", () => {fact.innerText = fact.innerText + " : " + data[0].capital})

}

getCountriesFacts();
