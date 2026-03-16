import {Header} from "../../components/Header.tsx";

export function LoginPage() {
  return (

      <>
          <title>Login</title>
          <Header />
          <div>
              <h1>Login</h1>
              <form>
                  <input type="email" placeholder="Email" />
                  <input type="password" placeholder="Password" />
                  <button type="submit">Login</button>
              </form>
          </div>
      </>



  );
}