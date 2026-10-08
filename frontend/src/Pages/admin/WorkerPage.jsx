import { useEffect, useState } from "react";
import axios from "axios";

// 관리자: 작업자 계정 목록 + 생성
export default function WorkerPage() {
  const [list, setList] = useState([]);
  const [form, setForm] = useState({ loginId: "", password: "", userName: "" });

  const load = async () => {
    try {
      const res = await axios.get("/wms/workers");
      setList(res.data);
    } catch (error) {
      alert(error.response?.data ?? "조회 실패");
    }
  };

  useEffect(() => {
    load();
  }, []);

  const change = (e) => setForm({ ...form, [e.target.name]: e.target.value });

  const create = async (e) => {
    e.preventDefault();
    try {
      await axios.post("/wms/workers", form);
      alert("작업자 계정을 만들었습니다.");
      setForm({ loginId: "", password: "", userName: "" });
      load();
    } catch (error) {
      alert(error.response?.data ?? "생성 실패");
    }
  };

  return (
    <>
      <h3>작업자 계정</h3>
      <form className="search" onSubmit={create}>
        아이디 <input name="loginId" value={form.loginId} onChange={change} />
        비밀번호 <input name="password" type="password" value={form.password} onChange={change} />
        이름 <input name="userName" value={form.userName} onChange={change} />
        <input type="submit" className="btn primary" value="생성" />
      </form>

      <table className="grid">
        <thead>
          <tr><th>번호</th><th>아이디</th><th>이름</th><th>역할</th></tr>
        </thead>
        <tbody>
          {list.map((w) => (
            <tr key={w.userId}>
              <td>{w.userId}</td>
              <td>{w.loginId}</td>
              <td>{w.userName}</td>
              <td>{w.role === "ADMIN" ? "관리자" : "작업자"}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </>
  );
}