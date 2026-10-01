import PageTitle from "../../Layout/PageTitle";
import GridTitle from "../../Layout/GridTitle";
import DocumentHeader from "../../Layout/DocumentHeader";
import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import axios from "axios";

const 상태명 = {
  WAITING: "입고예정",
  INSPECTED: "검수완료",
  COMPLETED: "입고완료",
  CANCELED: "취소",
};

// 04 입고검수 — 담당: 조현우
export default function InspectionPage(props) {
  const { documentId } = useParams(); // 입고문서 상세에서 [검수하기]로 왔으면 문서 번호가 있음
  const navigate = useNavigate();

  const [inbounds, setInbounds] = useState([]); // ED-10 입고 문서 전체
  const [조건, set조건] = useState({
    from: "",
    to: "",
    partnerName: "",
    status: "WAITING,INSPECTED",
  }); // 조회 조건
  const [selected, setSelected] = useState(null); // ED-13 선택한 문서 (items 포함)
  const [results, setResults] = useState([]); // ED-15 검수 결과
  const [qtys, setQtys] = useState({}); // { documentItemId: 실제 입고수량 입력 }
  const [memos, setMemos] = useState({}); // { documentItemId: 비고 입력 }

  // ED-10 목록
  const 목록조회 = async () => {
    const response = await axios.get("/wms/inbounds");
    setInbounds(response.data);
  };

  // ED-15 검수 결과
  const 결과조회 = async (documentId) => {
    const response = await axios.get(`/wms/inspections/${documentId}`);
    setResults(response.data);
  };

  // 목록 줄 클릭 → ED-13 상세 + ED-15 결과
  const 문서선택 = async (documentId) => {
    const response = await axios.get(`/wms/inbounds/${documentId}`);
    setSelected(response.data);
    결과조회(documentId);
    setQtys({});
    setMemos({});
  };

  // 저장 후 선택 문서 상태 다시 받기
  const 문서새로고침 = async () => {
    const response = await axios.get(`/wms/inbounds/${selected.documentId}`);
    setSelected(response.data);
    결과조회(selected.documentId);
    목록조회();
  };

  // 조회 버튼 - 입력한 조건 저장 + 목록 다시 불러오기
  const 조회 = (event) => {
    event.preventDefault();
    const form = new FormData(event.target);
    set조건({
      from: form.get("from"),
      to: form.get("to"),
      partnerName: form.get("partnerName").trim(),
      status: form.get("status"),
    });
    목록조회();
  };

  useEffect(() => {
    목록조회();
    if (documentId) 문서선택(Number(documentId)); // 주소에 문서 번호가 있으면 바로 상세
  }, []);

  // 상세 → 목록으로 (주소도 목록 주소로)
  const 뒤로가기 = () => {
    setSelected(null);
    navigate("/inbounds/inspection");
  };

  // 검수 끝난 문서 → 물품적재 화면의 같은 문서로
  const 적재하기 = () => {
    navigate(`/inbounds/putaway/${selected.documentId}`);
  };

  // 조회 조건에 맞는 문서만
  const 보여줄목록 = inbounds.filter(
    (d) =>
      조건.status.split(",").includes(d.status) &&
      (조건.from === "" || d.expectedAt >= 조건.from) &&
      (조건.to === "" || d.expectedAt <= 조건.to) &&
      (조건.partnerName === "" || d.partnerName.includes(조건.partnerName)),
  );

  // 이미 검수된 품목 { documentItemId: 검수 결과 } — 있으면 입력칸 대신 값으로 보여줌
  const 검수된 = {};
  results.forEach((r) => {
    검수된[r.documentItemId] = r;
  });

  const 실제수량 = (item) =>
    검수된[item.documentItemId]?.qty ?? Number(qtys[item.documentItemId] || 0);
  const 예정합계 = selected
    ? selected.items.reduce((sum, item) => sum + item.expectedQty, 0)
    : 0;
  const 실제합계 = selected
    ? selected.items.reduce((sum, item) => sum + 실제수량(item), 0)
    : 0;

  // [예정수량 일괄 적용] — 아직 검수 안 한 품목만
  const 일괄적용 = () => {
    const next = {};
    selected.items.forEach((item) => {
      if (검수된[item.documentItemId] === undefined)
        next[item.documentItemId] = item.expectedQty;
    });
    setQtys(next);
  };

  // ED-14 검수 저장 — 수량을 입력한 품목만 1건씩 POST (비고 포함)
  const 검수저장 = async () => {
    const 대상 = selected.items.filter(
      (item) =>
        검수된[item.documentItemId] === undefined &&
        Number(qtys[item.documentItemId]) > 0,
    );
    if (대상.length === 0) {
      alert("저장할 검수 수량이 없습니다.");
      return;
    }
    const 실패메시지 = [];
    for (const item of 대상) {
      try {
        await axios.post("/wms/inspections", {
          documentItemId: item.documentItemId,
          qty: Number(qtys[item.documentItemId]),
          remark: memos[item.documentItemId] ?? null,
        });
      } catch (error) {
        실패메시지.push(
          `${item.productName}: ${error.response?.data ?? "오류"}`,
        );
      }
    }
    alert(
      `검수 저장 ${대상.length - 실패메시지.length}건` +
        (실패메시지.length > 0 ? `\n실패\n${실패메시지.join("\n")}` : ""),
    );
    setQtys({});
    setMemos({});
    문서새로고침();
  };

  return (
    <>
      <PageTitle title="입고검수" path="홈 > 입고관리 > 입고검수" />

      {selected === null ? (
        <>
          {/* ── 목록: 입고예정·검수완료 ── */}
          <form className="search" onSubmit={조회}>
            <label>입고예정일</label>
            <input type="date" name="from" /> ~ <input type="date" name="to" />
            <label>화주명</label>
            <input type="text" name="partnerName" />
            <label>상태</label>
            <select name="status" defaultValue="WAITING,INSPECTED">
              <option value="WAITING,INSPECTED">
                전체 (입고예정·검수완료)
              </option>
              <option value="WAITING">입고예정</option>
              <option value="INSPECTED">검수완료</option>
            </select>
            <input type="submit" className="btn primary" value="조회" />
          </form>

          <GridTitle
            title="검수 대상 입고문서"
            desc={`총 ${보여줄목록.length}건`}
          />
          <table className="grid">
            <thead>
              <tr>
                <th>No</th>
                <th>입고번호</th>
                <th>화주명</th>
                <th>입고예정일</th>
                <th>품목수</th>
                <th>예정수량</th>
                <th>상태</th>
              </tr>
            </thead>
            <tbody>
              {보여줄목록.map((inbound, index) => (
                <tr
                  key={inbound.documentId}
                  onClick={() => 문서선택(inbound.documentId)}
                >
                  <td>{index + 1}</td>
                  <td>{inbound.documentNo}</td>
                  <td className="left">{inbound.partnerName}</td>
                  <td>{inbound.expectedAt}</td>
                  <td className="num">{inbound.itemCount}</td>
                  <td className="num">
                    {inbound.totalExpectedQty.toLocaleString()}
                  </td>
                  <td>{상태명[inbound.status]}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      ) : (
        <>
          {/* ── 상세: 헤더 + 검수 입력 ── */}
          <div className="detail-bar">
            <button className="btn" onClick={뒤로가기}>
              목록
            </button>
            <span className="right">
              {selected.status === "INSPECTED" && (
                <button className="btn primary" onClick={적재하기}>
                  적재하기
                </button>
              )}
            </span>
          </div>

          <GridTitle title="입고 정보" desc={selected.documentNo} />
          <DocumentHeader
            type="INBOUND"
            doc={selected}
            statusName={상태명[selected.status]}
          />
          <GridTitle
            title="검수 입력"
            desc={`예정 ${예정합계.toLocaleString()} · 실제 ${실제합계.toLocaleString()}`}
          >
            {selected.status === "WAITING" && (
              <>
                <button className="btn" onClick={일괄적용}>
                  예정수량 일괄 적용
                </button>
                <button className="btn primary" onClick={검수저장}>
                  검수 저장
                </button>
              </>
            )}
          </GridTitle>
          <table className="grid">
            <thead>
              <tr>
                <th>품목코드</th>
                <th>품목명</th>
                <th>소비기한</th>
                <th>남은일수</th>
                <th>예정수량</th>
                <th>실제 입고수량</th>
                <th>비고</th>
              </tr>
            </thead>
            <tbody>
              {selected.items.map((item) => {
                const done = 검수된[item.documentItemId]; // undefined면 아직 검수 전
                return (
                  <tr key={item.documentItemId}>
                    <td>{item.productCode}</td>
                    <td className="left">{item.productName}</td>
                    <td>{item.expiryDate}</td>
                    <td className="num">{item.remainingDays}일</td>
                    <td className="num">{item.expectedQty.toLocaleString()}</td>
                    <td className="num">
                      {done !== undefined ? (
                        done.qty.toLocaleString()
                      ) : (
                        <input
                          type="number"
                          min="1"
                          style={{ width: "80px" }}
                          value={qtys[item.documentItemId] ?? ""}
                          onChange={(e) =>
                            setQtys({
                              ...qtys,
                              [item.documentItemId]: e.target.value,
                            })
                          }
                        />
                      )}
                    </td>
                    <td className="left">
                      {done !== undefined ? (
                        (done.remark ?? "")
                      ) : (
                        <input
                          type="text"
                          style={{ width: "160px" }}
                          value={memos[item.documentItemId] ?? ""}
                          onChange={(e) =>
                            setMemos({
                              ...memos,
                              [item.documentItemId]: e.target.value,
                            })
                          }
                        />
                      )}
                    </td>
                  </tr>
                );
              })}
              <tr className="sum">
                <td colSpan={4}>합계</td>
                <td className="num">{예정합계.toLocaleString()}</td>
                <td className="num">{실제합계.toLocaleString()}</td>
                <td></td>
              </tr>
            </tbody>
          </table>
          <p className="hint">
            ※ 전 품목 검수가 끝나면 문서가 검수완료로 바뀌고, [적재하기]로
            물품적재 화면에 넘어갑니다.
          </p>
        </>
      )}
    </>
  );
}
