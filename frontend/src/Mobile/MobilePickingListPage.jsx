import { useEffect, useState } from "react";
import axios from "axios";
import { useNavigate } from "react-router-dom";

const STATUS_NAME = { ASSIGNED: "배정됨", PICKING: "피킹 중" };

// 내 피킹 리스트 — 누구인지는 서버가 쿠키로 판단 (GET /wms/pickings/mine)
// 서버가 배정됨·피킹 중인 것만 보내 줌
export default function MobilePickingListPage() {
  const navigate = useNavigate();
  const [lists, setLists] = useState([]);
  const [message, setMessage] = useState("");

  const load = async () => {
    try {
      const res = await axios.get("/wms/pickings/mine");
      setLists(res.data);
      setMessage(res.data.length === 0 ? "배정된 피킹 리스트가 없습니다." : "");
    } catch (error) {
      setMessage(error.response?.data ?? "목록을 불러오지 못했습니다.");
    }
  };

  useEffect(() => {
    load();
  }, []);

  return (
    <>
      <div className="m-row">
        <b>내 피킹 리스트</b>
        <button className="m-btn small" onClick={load}>
          새로고침
        </button>
      </div>
      {message && <p className="m-msg info">{message}</p>}
      {lists.map((p) => (
        <div
          key={p.pickingListId}
          className="m-card click"
          onClick={() => navigate(`/m/pickings/${p.pickingListId}`)}
        >
          <div className="m-row">
            <b>{p.pickingNo}</b>
            <span className="m-muted">{STATUS_NAME[p.status] ?? p.status}</span>
          </div>
          <div className="m-muted">출고 문서 {p.documentNo}</div>
          <div className="m-row">
            <span>{p.itemCount}개 품목</span>
            <span className="m-muted">
              {p.assignedAt
                ? p.assignedAt.replace("T", " ").slice(0, 16) + " 배정"
                : ""}
            </span>
          </div>
        </div>
      ))}
    </>
  );
}
