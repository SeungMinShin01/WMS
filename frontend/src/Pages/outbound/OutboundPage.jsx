import { useEffect, useState } from "react";
import axios from "axios";
import { useNavigate } from "react-router-dom";
import PageTitle from "../../Layout/PageTitle";
import GridTitle from "../../Layout/GridTitle";
import DocumentHeader from "../../Layout/DocumentHeader";

// 문서 상태 영어 값 → 화면에 보여줄 한글 (값에 없는 상태는 영어 그대로)
const STATUS_NAME = {
  WAITING: "출고예정",
  ALLOCATED: "출고할당",
  PICKING: "재고피킹",
  SHIPPED: "출고완료",
  CANCELED: "취소",
};

// 06 출고문서 — 담당: 김지환
// 목록 줄 클릭 → 상세(헤더 + 주문 품목) → [출고지시] / [피킹리스트]로 이동
export default function OutboundPage(props) {
  const navigate = useNavigate();
  const [outbounds, setOutbounds] = useState([]); // ED-12 출고 문서 전체
  const [filter, setFilter] = useState({
    from: "",
    to: "",
    partnerName: "",
    documentNo: "",
    status: "",
  }); // 조회 조건
  const [detail, setDetail] = useState(null); // 선택한 문서 상세 (null = 목록 화면)

  // 출고 목록 조회 (취소 후 다시 불러야 해서 useEffect 밖에 만듦)
  const getList = async () => {
    const response = await axios.get("/wms/outbounds");
    setOutbounds(response.data);
  };

  // 최초 1번 목록 조회
  useEffect(() => {
    getList();
  }, []);

  // 목록 줄 클릭 → ED-17 주문 상세
  const selectDocument = async (documentId) => {
    const response = await axios.get("/wms/outbounds/" + documentId);
    setDetail(response.data);
  };

  const goBack = () => {
    setDetail(null);
  };

  const goAllocation = () => {
    navigate(`/outbounds/allocation/${detail.documentId}`);
  };

  const goPicking = () => {
    navigate(`/outbounds/picking/${detail.documentId}`);
  };

  // 주문 취소 : 접수·할당 상태에서만 버튼이 보임
  const cancelOrder = async (event, outbound) => {
    event.stopPropagation(); // 버튼 클릭이 줄 클릭(상세 열기)으로 번지지 않게
    if (!confirm(outbound.documentNo + " 주문을 취소할까요? ")) return;
    try {
      await axios.put("/wms/outbounds/" + outbound.documentId + "/cancel");
      alert("주문이 취소되었습니다");
      getList(); // 상태가 취소로 바뀐 목록 다시 불러오기
    } catch (error) {
      alert(error.response ? error.response.data : "서버에 연결할 수 없습니다");
    }
  };

  // 조회 버튼 - 입력한 조건 저장 + 목록 다시 불러오기
  const handleSearch = (event) => {
    event.preventDefault();
    setFilter({
      from: event.target.from.value,
      to: event.target.to.value,
      partnerName: event.target.partnerName.value.trim(),
      documentNo: event.target.documentNo.value.trim(),
      status: event.target.status.value,
    });
    getList();
  };

  // 조회 조건에 맞는 문서만 (빈 조건은 통과)
  const visibleList = outbounds.filter((outbound) => {
    const day = outbound.expectedAt.substring(0, 10); // "2026-10-07T15:00:00" → "2026-10-07"
    if (filter.status !== "" && outbound.status !== filter.status) return false;
    if (filter.from !== "" && day < filter.from) return false;
    if (filter.to !== "" && day > filter.to) return false;
    if (
      filter.partnerName !== "" &&
      !outbound.partnerName.includes(filter.partnerName)
    )
      return false;
    if (
      filter.documentNo !== "" &&
      !outbound.documentNo.includes(filter.documentNo)
    )
      return false;
    return true;
  });

  return (
    <>
      <PageTitle title="출고문서" path="홈 > 출고관리 > 출고문서" />

      {detail === null ? (
        <>
          {/* ── 목록 ── */}
          <form className="search" onSubmit={handleSearch}>
            <label>출고요청일</label>
            <input type="date" name="from" /> ~ <input type="date" name="to" />
            <label>배송지명</label>
            <input type="text" name="partnerName" />
            <label>주문번호</label>
            <input type="text" name="documentNo" defaultValue="OUT-2026" />
            <label>상태</label>
            <select name="status">
              <option value="">전체</option>
              <option value="WAITING">출고예정</option>
              <option value="ALLOCATED">출고할당</option>
              <option value="PICKING">재고피킹</option>
              <option value="SHIPPED">출고완료</option>
              <option value="CANCELED">취소</option>
            </select>
            <input type="submit" className="btn primary" value="조회" />
          </form>

          <GridTitle title="출고문서 목록" desc={`총 ${visibleList.length}건`}>
            <button className="btn">신규</button>
          </GridTitle>
          <table className="grid">
            <thead>
              <tr>
                <th>No</th>
                <th>주문번호</th>
                <th>배송지명</th>
                <th>배송지 주소</th>
                <th>연락처</th>
                <th>출고요청일</th>
                <th>품목수</th>
                <th>총수량</th>
                <th>상태</th>
                <th>출고지시번호</th>
                <th>등록일시</th>
                <th>관리</th>
              </tr>
            </thead>
            <tbody>
              {visibleList.map((outbound, index) => (
                <tr
                  key={outbound.documentId}
                  onClick={() => selectDocument(outbound.documentId)}
                >
                  <td>{index + 1}</td>
                  <td>{outbound.documentNo}</td>
                  <td>{outbound.partnerName}</td>
                  <td></td>
                  <td></td>
                  <td>{outbound.expectedAt.replace("T", " ")}</td>
                  <td></td>
                  <td></td>
                  <td>{STATUS_NAME[outbound.status]}</td>
                  <td></td>
                  <td></td>
                  <td>
                    {outbound.status === "WAITING" ||
                    outbound.status === "ALLOCATED" ? (
                      <button
                        className="btn danger plain"
                        onClick={(e) => cancelOrder(e, outbound)}
                      >
                        취소
                      </button>
                    ) : (
                      "-"
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      ) : (
        <>
          {/* ── 상세: 헤더 + 주문 품목 ── */}
          <GridTitle title="출고 정보" desc={detail.documentNo} />
          <DocumentHeader
            type="OUTBOUND"
            doc={detail}
            statusName={STATUS_NAME[detail.status]}
          />

          <div className="detail-bar">
            <button className="btn" onClick={goBack}>
              목록
            </button>
            {(detail.status === "WAITING" || detail.status === "ALLOCATED") && (
              <button className="btn primary" onClick={goAllocation}>
                출고지시
              </button>
            )}
            {(detail.status === "ALLOCATED" ||
              detail.status === "PICKING" ||
              detail.status === "SHIPPED") && (
              <button className="btn" onClick={goPicking}>
                피킹리스트
              </button>
            )}
          </div>

          <GridTitle title="주문 품목" desc={`총 ${detail.items.length}건`} />
          <table className="grid">
            <thead>
              <tr>
                <th>No</th>
                <th>품목코드</th>
                <th>품목명</th>
                <th>주문수량</th>
                <th>할당수량</th>
              </tr>
            </thead>
            <tbody>
              {detail.items.map((item, index) => (
                <tr key={item.documentItemId}>
                  <td>{index + 1}</td>
                  <td>{item.productCode}</td>
                  <td>{item.productName}</td>
                  <td>{item.expectedQty}</td>
                  <td>{item.allocatedQty}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}
    </>
  );
}
