import { useEffect, useState } from "react";
import axios from "axios";
import usePaging from "../../Layout/usePaging";

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

  // 활성/비활성 — 지금 상태의 반대 값을 보냄
  const changeActive = async (w) => {
    const next = !w.active;
    if (!window.confirm(`${w.userName} 계정을 ${next ? "활성화" : "비활성화"}할까요?`)) return;
    try {
      await axios.put(`/wms/workers/${w.userId}/active`, null, { params: { active: next } });
      load();
    } catch (error) {
      alert(error.response?.data ?? "변경 실패");
    }
  };
  const { paged, pager } = usePaging(list);
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
          <tr><th>번호</th><th>아이디</th><th>이름</th><th>역할</th><th>상태</th><th>관리</th></tr>
        </thead>
        <tbody>
          {paged.map((w) => (
            <tr key={w.userId}>
              <td>{w.userId}</td>
              <td>{w.loginId}</td>
              <td>{w.userName}</td>
              <td>{w.role === "ADMIN" ? "관리자" : "작업자"}</td>
              <td>{w.active ? "사용" : "비활성"}</td>
              <td>
                {w.role === "WORKER" && (
                  <button className="btn" onClick={() => changeActive(w)}>
                    {w.active ? "비활성화" : "활성화"}
                  </button>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      {pager}
    </>
  );
}