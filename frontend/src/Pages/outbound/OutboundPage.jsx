import { useState, useEffect } from "react";
import axios from "axios";
import { useNavigate } from "react-router-dom";
import PageTitle from "../../Layout/PageTitle";
import GridTitle from "../../Layout/GridTitle";
import DocumentHeader from "../../Layout/DocumentHeader";

// 문서 상태 영어 값 → 화면에 보여줄 한글 (값에 없는 상태는 영어 그대로)
const 상태이름 = {
  WAITING: "접수",
  ALLOCATED: "할당",
  PICKING: "피킹중",
  SHIPPED: "출고완료",
  CANCELED: "취소",
};
const 상태표시 = (status) => 상태이름[status] || status;

// 06 출고문서 — 담당: 김지환
// 목록 줄 클릭 → 상세(헤더 + 주문 품목) → [출고지시] / [피킹리스트]로 이동
export default function OutboundPage(props) {
  const navigate = useNavigate();
  const [outbounds, setOutbounds] = useState([]);
  const [viewList, setViewList] = useState([]);
  const [detail, setDetail] = useState(null); // 선택한 문서 상세 (null = 목록 화면)

  // 출고 목록 조회 (취소 후 다시 불러야 해서 useEffect 밖에 만듦)
  const getOutbounds = async () => {
    const response = await axios.get("/wms/outbounds");
    setOutbounds(response.data); // 원본 목록 (검색할 때 기준)
    setViewList(response.data); // 화면에 보여줄 목록 (검색 조건은 초기화됨)
  };

  // 최초 1번 목록 조회
  useEffect(() => {
    getOutbounds();
  }, []);

  // 목록 줄 클릭 → ED-17 주문 상세
  const 문서선택 = async (documentId) => {
    const response = await axios.get("/wms/outbounds/" + documentId);
    setDetail(response.data);
  };

  const 뒤로가기 = () => {
    setDetail(null);
  };

  const 출고지시하기 = () => {
    navigate(`/outbounds/allocation/${detail.documentId}`);
  };

  const 피킹리스트보기 = () => {
    navigate(`/outbounds/picking/${detail.documentId}`);
  };

  // 주문 취소 : 접수·할당 상태에서만 버튼이 보임
  const 주문취소 = async (event, outbound) => {
    event.stopPropagation(); // 버튼 클릭이 줄 클릭(상세 열기)으로 번지지 않게
    if (!confirm(outbound.documentNo + " 주문을 취소할까요? ")) return;
    try {
      await axios.put("/wms/outbounds/" + outbound.documentId + "/cancel");
      alert("주문이 취소되었습니다");
      getOutbounds(); // 상태가 취소로 바뀐 목록 다시 불러오기
    } catch (error) {
      alert(error.response ? error.response.data : "서버에 연결할 수 없습니다");
    }
  };

  const 조회 = (event) => {
    event.preventDefault();
    const status = event.target.status.value;
    const from = event.target.from.value;
    const to = event.target.to.value;
    const partnerName = event.target.partnerName.value;
    let result = [];
    for (let i = 0; i < outbounds.length; i++) {
      const row = outbounds[i];
      const date = row.expectedAt.substring(0, 10);
      if (status !== "" && row.status !== status) continue;
      if (from !== "" && date < from) continue;
      if (to !== "" && date > to) continue;
      if (partnerName !== "" && !row.partnerName.includes(partnerName))
        continue;
      result.push(row);
    }
    setViewList(result);
  };

  return (
    <>
      <PageTitle title="출고문서" path="홈 > 출고관리 > 출고문서" />

      {detail === null ? (
        <>
          {/* ── 목록 ── */}
          <form className="search" onSubmit={조회}>
            <label>출고요청일</label>
            <input type="date" name="from" /> ~ <input type="date" name="to" />
            <label>배송지명</label>
            <input type="text" name="partnerName" />
            <label>주문번호</label>
            <input type="text" name="documentNo" defaultValue="OUT-2026" />
            <label>상태</label>
            <select name="status">
              <option value="">전체</option>
              <option value="WAITING">접수</option>
              <option value="ALLOCATED">할당</option>
              <option value="PICKING">피킹중</option>
              <option value="SHIPPED">출고완료</option>
              <option value="CANCELED">취소</option>
            </select>
            <input type="submit" className="btn primary" value="조회" />
          </form>

          <GridTitle title="출고문서 목록" desc={`총 ${viewList.length}건`}>
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
              {viewList.map((outbound, index) => (
                <tr
                  key={outbound.documentId}
                  onClick={() => 문서선택(outbound.documentId)}
                >
                  <td>{index + 1}</td>
                  <td>{outbound.documentNo}</td>
                  <td>{outbound.partnerName}</td>
                  <td></td>
                  <td></td>
                  <td>{outbound.expectedAt.replace("T", " ")}</td>
                  <td></td>
                  <td></td>
                  <td>{상태표시(outbound.status)}</td>
                  <td></td>
                  <td></td>
                  <td>
                    {outbound.status === "WAITING" ||
                    outbound.status === "ALLOCATED" ? (
                      <button
                        className="btn danger"
                        onClick={(e) => 주문취소(e, outbound)}
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
            statusName={상태표시(detail.status)}
          />

          <GridTitle title="주문 품목" desc={`총 ${detail.items.length}건`}>
            <button className="btn" onClick={뒤로가기}>
              목록
            </button>
            {(detail.status === "WAITING" || detail.status === "ALLOCATED") && (
              <button className="btn primary" onClick={출고지시하기}>
                출고지시
              </button>
            )}
            {(detail.status === "ALLOCATED" ||
              detail.status === "PICKING" ||
              detail.status === "SHIPPED") && (
              <button className="btn" onClick={피킹리스트보기}>
                피킹리스트
              </button>
            )}
          </GridTitle>
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
