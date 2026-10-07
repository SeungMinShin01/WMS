import { useEffect, useState } from "react";
import axios from "axios";
import { useNavigate } from "react-router-dom";
import PageTitle from "../../Layout/PageTitle";
import GridTitle from "../../Layout/GridTitle";
import DocumentHeader from "../../Layout/DocumentHeader";
import InboundCreateForm from "./InboundCreateForm";

// status 코드 -> 화면에 띄울 한글
// 날짜 모양(yyyy-MM-dd / yyyy-MM-dd HH:mm)과 남은일수는 백엔드 DTO에서 만들어서 보낸다
const STATUS_NAME = {
  WAITING: "입고예정",
  INSPECTED: "검수완료",
  COMPLETED: "입고완료",
  CANCELED: "취소",
};

// 03 입고예정 — 담당: 조현우
export default function InboundPage(props) {
  const navigate = useNavigate();

  const [inbounds, setInbounds] = useState([]); // ED-10 입고 예정 목록
  const [detail, setDetail] = useState(null); // ED-13 클릭한 문서 상세
  const [filter, setFilter] = useState({
    from: "",
    to: "",
    partnerName: "",
    documentNo: "",
    status: "",
  }); // 조회 조건

  const [creating, setCreating] = useState(false); 

  // ED-10 조회 버튼 — 새로고침만 막는다. 실제 조회는 [팀원 작성]
  const getList = async () => {
    const response = await axios.get("/wms/inbounds");
    setInbounds(response.data);
  };

  // ED-13 상세 조회 - 목록 줄 클릭하면 실행
  const selectDocument = async (documentId) => {
    const response = await axios.get(`/wms/inbounds/${documentId}`);
    setDetail(response.data);
  };

  // 조회 버튼 - 새로고침 막고 목록 다시 불러오기
  const handleSearch = (event) => {
    event.preventDefault(); // 새로고침 막기
    const form = new FormData(event.target);
    setFilter({
      from: form.get("from"),
      to: form.get("to"),
      partnerName: form.get("partnerName").trim(),
      documentNo: form.get("documentNo").trim(),
      status: form.get("status"),
    });
    getList();
  };

  // 화면이 처음 열릴때 목록 한번 불러오기
  useEffect(() => {
    getList();
  }, []);

  // 조회 조건에 맞는 문서만 (빈 조건은 통과)
  const visibleList = inbounds.filter(
    (d) =>
      (filter.from === "" || d.expectedAt >= filter.from) &&
      (filter.to === "" || d.expectedAt <= filter.to) &&
      (filter.partnerName === "" ||
        d.partnerName.includes(filter.partnerName)) &&
      (filter.documentNo === "" || d.documentNo.includes(filter.documentNo)) &&
      (filter.status === "" || d.status === filter.status),
  );

  // 상세 품목의 예정수량 합계
  const expectedTotal = detail
    ? detail.items.reduce((sum, item) => sum + item.expectedQty, 0)
    : 0;

  const goBack = () => {
    setDetail(null);
  };

  const goInspect = () => {
    navigate(`/inbounds/inspection/${detail.documentId}`);
  };

  return (
    <>
      <PageTitle title="입고문서" path="홈 > 입고관리 > 입고문서" />

      {creating ? (
        <InboundCreateForm
          onCancel={() => setCreating(false)}
          onDone={() => {
            setCreating(false);
            getList();
          }}
        />
      ) : detail === null ? (
        <>
          {/* ── 목록 ── */}
          <form className="search" onSubmit={handleSearch}>
            <label>입고예정일</label>
            <input type="date" name="from" /> ~ <input type="date" name="to" />
            <label>화주명</label>
            <input type="text" name="partnerName" />
            <label>입고번호</label>
            <input type="text" name="documentNo" defaultValue="IN-2026" />
            <label>상태</label>
            <select name="status" defaultValue="">
              <option value="">전체</option>
              <option value="WAITING">입고예정</option>
              <option value="INSPECTED">검수완료</option>
              <option value="COMPLETED">입고완료</option>
              <option value="CANCELED">취소</option>
            </select>
            <input type="submit" className="btn primary" value="조회" />
          </form>

          <GridTitle title="입고문서 목록" desc={`총 ${visibleList.length}건`}>
            <button className="btn" onClick={() => setCreating(true)}>신규</button>
          </GridTitle>
          <table className="grid">
            <thead>
              <tr>
                <th>No</th>
                <th>입고번호</th>
                <th>화주</th>
                <th>공급사명</th>
                <th>입고예정일</th>
                <th>품목수</th>
                <th>예정수량 합계</th>
                <th>상태</th>
                <th>비고</th>
                <th>등록일시</th>
                <th>입고확정일시</th>
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
                  <td>{inbound.tenantName}</td>
                  <td className="left">{inbound.partnerName}</td>
                  <td>{inbound.expectedAt}</td>
                  <td className="num">{inbound.itemCount}</td>
                  <td className="num">
                    {inbound.totalExpectedQty.toLocaleString()}
                  </td>
                  <td>{STATUS_NAME[inbound.status]}</td>
                  <td className="left"></td>
                  <td>{inbound.createdAt}</td>
                  <td>{inbound.completedAt}</td>
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
            {detail.status === "WAITING" && (
              <button className="btn primary" onClick={goInspect}>
                검수하기
              </button>
            )}
          </div>

          <GridTitle
            title="입고 품목"
            desc={`${detail.items.length}품목 · 예정수량 ${expectedTotal.toLocaleString()}`}
          />
          <table className="grid">
            <thead>
              <tr>
                <th>No</th>
                <th>품목코드</th>
                <th>품목명</th>
                <th>LOT</th>
                <th>소비기한</th>
                <th>남은일수</th>
                <th>예정수량</th>
              </tr>
            </thead>
            <tbody>
              {detail.items.map((item, index) => (
                <tr key={item.documentItemId}>
                  <td>{index + 1}</td>
                  <td>{item.productCode}</td>
                  <td className="left">{item.productName}</td>
                  <td>{item.lotCode}</td>
                  <td>{item.expiryDate}</td>
                  <td className="num">{item.remainingDays}일</td>
                  <td className="num">{item.expectedQty.toLocaleString()}</td>
                </tr>
              ))}
              <tr className="sum">
                <td colSpan={6}>합계</td>
                <td className="num">{expectedTotal.toLocaleString()}</td>
              </tr>
            </tbody>
          </table>
        </>
      )}
    </>
  );
}
