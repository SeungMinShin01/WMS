import { useState } from "react";
import axios from "axios";
import { useNavigate } from "react-router-dom";
import "./mobile.css";

// 모바일 로그인 — PC 와 같은 API·쿠키, 성공하면 바로 내 피킹 리스트로
export default function MobileLoginPage() {
  const navigate = useNavigate();
  const [loginId, setLoginId] = useState("");
  const [password, setPassword] = useState("");
  const [saving, setSaving] = useState(false);

  const login = async (event) => {
    event.preventDefault();
    setSaving(true);
    try {
      const response = await axios.post("/auth/login", { loginId, password });
      localStorage.setItem("user", JSON.stringify(response.data));
      navigate("/m/pickings", { replace: true }); // 뒤로 가기로 로그인 화면에 안 돌아오게
    } catch (error) {
      alert(error.response?.data ?? "로그인 실패");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="m-page">
      <div className="m-top">
        <b>WMS 모바일 피킹</b>
      </div>
      <form className="m-body" onSubmit={login}>
        <div className="m-card">
          <p className="m-muted">아이디</p>
          <input
            className="m-input"
            value={loginId}
            onChange={(e) => setLoginId(e.target.value)}
            autoComplete="username"
          />
          <p className="m-muted">비밀번호</p>
          <input
            className="m-input"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
          />
        </div>
        <button type="submit" className="m-btn" disabled={saving}>
          {saving ? "로그인 중..." : "로그인"}
        </button>
      </form>
    </div>
  );
}
