import {Header} from "../../components/Header.tsx";

export function RegistrationPage() {


    return (

        <>
            <title>Registration</title>
            <Header />
            <div>
                <h1>Register</h1>
                <form>
                    <input type="text" placeholder="Username" />
                    <input type="email" placeholder="Email" />
                    <input type="password" placeholder="Password" />
                    <button type="submit">Register</button>
                </form>
            </div>
        </>


  );
}