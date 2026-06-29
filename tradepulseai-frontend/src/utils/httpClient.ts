import axios from "axios";
import { clearStoredToken } from "./auth";

const DEFAULT_REQUEST_TIMEOUT_MS = 15_000;

let configured = false;

export function configureHttpClient(): void {
  if (configured) {
    return;
  }

  configured = true;
  axios.defaults.timeout = DEFAULT_REQUEST_TIMEOUT_MS;
  axios.defaults.headers.common.Accept = "application/json";

  axios.interceptors.response.use(
    (response) => response,
    (error) => {
      if (axios.isAxiosError(error)) {
        const status = error.response?.status;
        if (status === 401) {
          clearStoredToken();
        }
      }

      return Promise.reject(error);
    },
  );
}

