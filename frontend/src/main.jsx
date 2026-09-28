import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { HashRouter } from "react-router-dom";
import "./index.css";
import App from "./App.jsx";

// HashRouter: 주소가 /wms/#/inbounds 처럼 # 뒤에 붙는다.
// 백엔드 API 주소가 /wms/... 라서 BrowserRouter를 쓰면 새로고침할 때 API와 겹친다.
createRoot(document.getElementById("root")).render(
  <StrictMode>
    <HashRouter>
      <App />
    </HashRouter>
  </StrictMode>,
);
