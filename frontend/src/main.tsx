import React from "react";
import ReactDOM from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import App from "./App";
import { AuthSessionProvider } from "@/context/AuthSessionContext";
import "./index.css";

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <BrowserRouter>
      <AuthSessionProvider>
        <App />
      </AuthSessionProvider>
    </BrowserRouter>
  </React.StrictMode>,
);
