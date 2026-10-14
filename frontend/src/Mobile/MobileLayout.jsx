import { useEffect, useState } from "react";
import axios from "axios";
import { Outlet, useNavigate } from "react-router-dom";
import "./mobile.css";

// 모바일 공통 틀: 위쪽 바(이름·로그아웃) + 내용. 사이드 메뉴 없음
// 로그인이 풀리면 PC 로그인이 아니라 /m/login 으로
export default function MobileLayout() {
  const navigate = useNavigate();
  const [user, setUser] = useState(null);

  const checkLogin = async () => {
    try {
      await axios.get("/auth/me");
      setUser(JSON.parse(localStorage.getItem("user")));
    } catch {
      navigate("/m/login", { replace: true });
    }
  };

  useEffect(() => {
    checkLogin();
  }, []);

  const logout = async () => {
    try {
      await axios.post("/auth/logout");
    } finally {
      localStorage.removeItem("user");
      navigate("/m/login", { replace: true });
    }
  };

  return (
    <div className="m-page">
      <div className="m-top">
        <b>피킹</b>
        <span>
          {user?.userName}{" "}
          <button className="m-btn small plain" onClick={logout}>
            로그아웃
          </button>
        </span>
      </div>
      <div className="m-body">
        <Outlet context={{ user }} />
      </div>
    </div>
  );
}
