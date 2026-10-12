import { useEffect, useState } from "react";
import axios from "axios";
import PageTitle from "../../Layout/PageTitle";
import GridTitle from "../../Layout/GridTitle";

// 오늘 날짜 "2026-10-12" (브라우저 시간 기준)
const today = () => {
  const d = new Date();
  const pad = (n) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
};

// 한 페이지 줄 수 (하루치를 화면에서 나눔 — 파일은 어차피 전체를 읽어서 서버 페이징 이득이 없음)
const PAGE_SIZE = 50;

// 표 칸: 제목 + 너비. 너비가 없는 칸(내용·사유)은 남은 폭을 나눠 가짐
const COLUMNS = [
  { title: "시간", width: "70px" },
  { title: "사용자", width: "130px" },
  { title: "역할", width: "60px" },
  { title: "작업", width: "120px" },
  { title: "대상", width: "130px" },
  { title: "내용" },
  { title: "결과", width: "50px" },
  { title: "사유" },
  { title: "IP", width: "110px" },
  { title: "ms", width: "50px" },
];

const ROLE_NAME = { ADMIN: "관리자", WORKER: "작업자" };

// 관리자: 감사 로그 (누가 언제 무엇을 했고 성공/실패했는지)
export default function AuditLogPage() {
  const [date, setDate] = useState(today());
  const [rows, setRows] = useState([]); // 그날 전체 (서버가 최신순으로 줌)
  const [filter, setFilter] = useState({ action: "", result: "", keyword: "" });
  const [page, setPage] = useState(0); // 0부터

  // 하루치 조회 (서버)
  const load = async (day) => {
    try {
      const res = await axios.get("/wms/audit-logs", { params: { date: day } });
      setRows(res.data);
    } catch (error) {
      alert(error.response?.data ?? "조회 실패");
    }
  };

  // 날짜가 바뀌면 다시 조회
  useEffect(() => {
    load(date);
  }, [date]);

  // 날짜를 바꾸면 1페이지로 (effect 안이 아니라 이벤트에서)
  const changeDate = (e) => {
    setDate(e.target.value);
    setPage(0);
  };

  // CSV 다운로드: 파일을 받아서 브라우저 저장 (페이징과 관계없이 그날 전체)
  const download = async () => {
    try {
      const res = await axios.get("/wms/audit-logs/download", {
        params: { date },
        responseType: "blob",
      });
      const url = URL.createObjectURL(res.data);
      const a = document.createElement("a");
      a.href = url;
      a.download = `audit-${date}.csv`;
      a.click();
      URL.revokeObjectURL(url);
    } catch {
      alert("다운로드 실패");
    }
  };

  // 작업 선택지: 그날 기록에 있는 작업만 [영문, 한글]
  const actions = [
    ...new Map(rows.map((r) => [r.action, r.actionLabel])).entries(),
  ];

  // 1. 거르기 (작업·결과·검색어)
  const keyword = filter.keyword.trim();
  const visible = rows.filter(
    (r) =>
      (filter.action === "" || r.action === filter.action) &&
      (filter.result === "" || r.result === filter.result) &&
      (keyword === "" ||
        [r.loginId, r.userName, r.target, r.detail, r.reason, r.uri].some((v) =>
          v?.includes(keyword),
        )),
  );
  const failCount = visible.filter((r) => r.result === "FAIL").length;

  // 2. 정렬은 서버가 최신순으로 해서 줌 → 3. 자르기
  const totalPages = Math.max(1, Math.ceil(visible.length / PAGE_SIZE));
  const pageRows = visible.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);

  // 필터를 바꾸면 1페이지로
  const changeFilter = (e) => {
    setFilter({ ...filter, [e.target.name]: e.target.value });
    setPage(0);
  };

  return (
    <>
      <PageTitle title="감사 로그" path="홈 > 관리자 > 감사 로그" />

      <div className="search">
        <label>날짜</label>
        <input type="date" value={date} onChange={changeDate} />
        <label>작업</label>
        <select name="action" value={filter.action} onChange={changeFilter}>
          <option value="">전체</option>
          {actions.map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>
        <label>결과</label>
        <select name="result" value={filter.result} onChange={changeFilter}>
          <option value="">전체</option>
          <option value="SUCCESS">성공</option>
          <option value="FAIL">실패</option>
        </select>
        <label>검색</label>
        <input
          name="keyword"
          value={filter.keyword}
          onChange={changeFilter}
          placeholder="아이디·대상·내용·사유"
        />
      </div>

      <GridTitle
        title="기록"
        desc={`총 ${visible.length}건 · 실패 ${failCount}건`}
      >
        <button className="btn" onClick={() => load(date)}>
          새로고침
        </button>
        <button className="btn primary" onClick={download}>
          CSV 다운로드
        </button>
      </GridTitle>

      {/* grid fixed : 칸 너비 고정, td.wrap 은 칸 안에서 줄바꿈 (index.css) */}
      <table className="grid fixed">
        <colgroup>
          {COLUMNS.map((c) => (
            <col
              key={c.title}
              style={c.width ? { width: c.width } : undefined}
            />
          ))}
        </colgroup>
        <thead>
          <tr>
            {COLUMNS.map((c) => (
              <th key={c.title}>{c.title}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {visible.length === 0 && (
            <tr>
              <td colSpan={COLUMNS.length}>기록이 없습니다</td>
            </tr>
          )}
          {pageRows.map((r, i) => {
            const fail = r.result === "FAIL";
            return (
              <tr key={page * PAGE_SIZE + i}>
                <td>{r.time.substring(11, 19)}</td>
                <td className="wrap">
                  {r.loginId ? `${r.userName} (${r.loginId})` : "-"}
                </td>
                <td>{ROLE_NAME[r.role] ?? "-"}</td>
                <td className="wrap">{r.actionLabel}</td>
                <td className="wrap">{r.target}</td>
                <td className="left wrap">{r.detail}</td>
                <td
                  style={fail ? { color: "#d33", fontWeight: 600 } : undefined}
                >
                  {fail ? "실패" : "성공"}
                </td>
                <td className="left wrap">{r.reason}</td>
                <td className="wrap">{r.ip}</td>
                <td>{r.ms}</td>
              </tr>
            );
          })}
        </tbody>
      </table>

      {/* 페이저 */}
      <div style={{ textAlign: "center", margin: "8px 0" }}>
        <button
          className="btn"
          onClick={() => setPage(0)}
          disabled={page === 0}
        >
          처음
        </button>{" "}
        <button
          className="btn"
          onClick={() => setPage(page - 1)}
          disabled={page === 0}
        >
          이전
        </button>
        <span style={{ margin: "0 12px" }}>
          {page + 1} / {totalPages}
        </span>
        <button
          className="btn"
          onClick={() => setPage(page + 1)}
          disabled={page >= totalPages - 1}
        >
          다음
        </button>{" "}
        <button
          className="btn"
          onClick={() => setPage(totalPages - 1)}
          disabled={page >= totalPages - 1}
        >
          끝
        </button>
      </div>
    </>
  );
}
