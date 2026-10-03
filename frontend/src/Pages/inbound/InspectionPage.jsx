import { useEffect, useState } from "react";
import axios from "axios";
import { useNavigate, useParams } from "react-router-dom";
import PageTitle from "../../Layout/PageTitle";
import GridTitle from "../../Layout/GridTitle";
import DocumentHeader from "../../Layout/DocumentHeader";

const STATUS_NAME = {
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
  const [filter, setFilter] = useState({
    from: "",
    to: "",
    partnerName: "",
    status: "WAITING,INSPECTED",
  }); // 조회 조건
  const [detail, setDetail] = useState(null); // ED-13 선택한 문서 (items 포함)
  const [results, setResults] = useState([]); // ED-15 검수 결과
  const [qtys, setQtys] = useState({}); // { documentItemId: 실제 입고수량 입력 }
  const [memos, setMemos] = useState({}); // { documentItemId: 비고 입력 }

  // ED-10 목록
  const getList = async () => {
    const response = await axios.get("/wms/inbounds");
    setInbounds(response.data);
  };

  // ED-15 검수 결과
  const getResults = async (documentId) => {
    const response = await axios.get(`/wms/inspections/${documentId}`);
    setResults(response.data);
  };

  // 목록 줄 클릭 → ED-13 상세 + ED-15 결과
  const selectDocument = async (documentId) => {
    const response = await axios.get(`/wms/inbounds/${documentId}`);
    setDetail(response.data);
    getResults(documentId);
    setQtys({});
    setMemos({});
  };

  // 저장 후 선택 문서 상태 다시 받기
  const refreshDetail = async () => {
    const response = await axios.get(`/wms/inbounds/${detail.documentId}`);
    setDetail(response.data);
    getResults(detail.documentId);
    getList();
  };

  // 조회 버튼 - 입력한 조건 저장 + 목록 다시 불러오기
  const handleSearch = (event) => {
    event.preventDefault();
    const form = new FormData(event.target);
    setFilter({
      from: form.get("from"),
      to: form.get("to"),
      partnerName: form.get("partnerName").trim(),
      status: form.get("status"),
    });
    getList();
  };

  useEffect(() => {
    getList();
    if (documentId) selectDocument(Number(documentId)); // 주소에 문서 번호가 있으면 바로 상세
  }, []);

  // 상세 → 목록으로 (주소도 목록 주소로)
  const goBack = () => {
    setDetail(null);
    navigate("/inbounds/inspection");
  };

  // 검수 끝난 문서 → 물품적재 화면의 같은 문서로
  const goPutaway = () => {
    navigate(`/inbounds/putaway/${detail.documentId}`);
  };

  // 조회 조건에 맞는 문서만
  const visibleList = inbounds.filter(
    (d) =>
      filter.status.split(",").includes(d.status) &&
      (filter.from === "" || d.expectedAt >= filter.from) &&
      (filter.to === "" || d.expectedAt <= filter.to) &&
      (filter.partnerName === "" || d.partnerName.includes(filter.partnerName)),
  );

  // 이미 검수된 품목 { documentItemId: 검수 결과 } — 있으면 입력칸 대신 값으로 보여줌
  const inspectedMap = {};
  results.forEach((r) => {
    inspectedMap[r.documentItemId] = r;
  });

  const getActualQty = (item) =>
    inspectedMap[item.documentItemId]?.qty ??
    Number(qtys[item.documentItemId] || 0);
  const expectedTotal = detail
    ? detail.items.reduce((sum, item) => sum + item.expectedQty, 0)
    : 0;
  const actualTotal = detail
    ? detail.items.reduce((sum, item) => sum + getActualQty(item), 0)
    : 0;

  // [예정수량 일괄 적용] — 아직 검수 안 한 품목만
  const applyExpectedQty = () => {
    const next = {};
    detail.items.forEach((item) => {
      if (inspectedMap[item.documentItemId] === undefined)
        next[item.documentItemId] = item.expectedQty;
    });
    setQtys(next);
  };

  // ED-14 검수 저장 — 수량을 입력한 품목만 1건씩 POST (비고 포함)
  const saveInspection = async () => {
    const targets = detail.items.filter(
      (item) =>
        inspectedMap[item.documentItemId] === undefined &&
        Number(qtys[item.documentItemId]) > 0,
    );
    if (targets.length === 0) {
      alert("저장할 검수 수량이 없습니다.");
      return;
    }
    const failMessages = [];
    for (const item of targets) {
      try {
        await axios.post("/wms/inspections", {
          documentItemId: item.documentItemId,
          qty: Number(qtys[item.documentItemId]),
          remark: memos[item.documentItemId] ?? null,
        });
      } catch (error) {
        failMessages.push(
          `${item.productName}: ${error.response?.data ?? "오류"}`,
        );
      }
    }
    alert(
      `검수 저장 ${targets.length - failMessages.length}건` +
        (failMessages.length > 0 ? `\n실패\n${failMessages.join("\n")}` : ""),
    );
    setQtys({});
    setMemos({});
    refreshDetail();
  };

  return (
    <>
      <PageTitle title="입고검수" path="홈 > 입고관리 > 입고검수" />

      {detail === null ? (
        <>
          {/* ── 목록: 입고예정·검수완료 ── */}
          <form className="search" onSubmit={handleSearch}>
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
            desc={`총 ${visibleList.length}건`}
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
              {visibleList.map((inbound, index) => (
                <tr
                  key={inbound.documentId}
                  onClick={() => selectDocument(inbound.documentId)}
                >
                  <td>{index + 1}</td>
                  <td>{inbound.documentNo}</td>
                  <td className="left">{inbound.partnerName}</td>
                  <td>{inbound.expectedAt}</td>
                  <td className="num">{inbound.itemCount}</td>
                  <td className="num">
                    {inbound.totalExpectedQty.toLocaleString()}
                  </td>
                  <td>{STATUS_NAME[inbound.status]}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      ) : (
        <>
          <GridTitle title="입고 정보" desc={detail.documentNo} />
          <DocumentHeader
            type="INBOUND"
            doc={detail}
            statusName={STATUS_NAME[detail.status]}
          />
          <div className="detail-bar">
            <button className="btn" onClick={goBack}>
              목록
            </button>
            {detail.status === "INSPECTED" && (
              <button className="btn primary" onClick={goPutaway}>
                적재하기
              </button>
            )}
          </div>

          {/* ── 검수 입력 ── */}
          <GridTitle
            title="검수 입력"
            desc={`예정 ${expectedTotal.toLocaleString()} · 실제 ${actualTotal.toLocaleString()}`}
          >
            {detail.status === "WAITING" && (
              <>
                <button className="btn" onClick={applyExpectedQty}>
                  예정수량 일괄 적용
                </button>
                <button className="btn primary" onClick={saveInspection}>
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
              {detail.items.map((item) => {
                const done = inspectedMap[item.documentItemId]; // undefined면 아직 검수 전
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
                <td className="num">{expectedTotal.toLocaleString()}</td>
                <td className="num">{actualTotal.toLocaleString()}</td>
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
