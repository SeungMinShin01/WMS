import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import "./index.css";
import App from "./App.jsx";

// BrowserRouter (수업과 같음): 화면 주소는 / , API 주소는 /wms/... 라 겹치지 않는다.
// 배포 때는 스프링에 "화면 주소로 오면 index.html" 컨트롤러 하나가 필요하다.
createRoot(document.getElementById("root")).render(
  <BrowserRouter>
    <App />
  </BrowserRouter>,
);
