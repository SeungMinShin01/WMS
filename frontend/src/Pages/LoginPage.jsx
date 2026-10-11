import { useState } from "react";
import axios from "axios";
import { useNavigate } from "react-router-dom";
import logo from "../assets/logo-header.png";

export default function LoginPage() {
  const navigate = useNavigate();
  const [loginId, setLoginId] = useState("");
  const [password, setPassword] = useState("");

  const login = async (event) => {
    event.preventDefault();
    try {
      const response = await axios.post("/auth/login", { loginId, password });
      // 토큰은 HttpOnly 쿠키로 자동 저장됨. 여기엔 화면 표시용 사용자 정보만
      localStorage.setItem("user", JSON.stringify(response.data));
      navigate("/");
    } catch (error) {
      alert(error.response?.data ?? "로그인 실패");
    }
  };

  return (
    <div className="auth-page">
      <form className="auth-box" onSubmit={login}>
        <div className="auth-head">
          <img src={logo} alt="first-in" />
          <b>WMS 로그인</b>
        </div>
        <div className="auth-body">
          <table className="form">
            <tbody>
              <tr>
                <th>아이디</th>
                <td>
                  <input value={loginId} onChange={(e) => setLoginId(e.target.value)} autoFocus />
                </td>
              </tr>
              <tr>
                <th>비밀번호</th>
                <td>
                  <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} />
                </td>
              </tr>
            </tbody>
          </table>
          <div className="auth-actions">
            <button type="submit" className="btn primary">로그인</button>
          </div>
          <p className="hint">※ 개발용 계정: admin / 1234 (관리자)</p>
        </div>
      </form>
    </div>
  );
}