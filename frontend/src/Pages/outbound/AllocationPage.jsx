import { useState, useEffect } from "react";
import axios from "axios";
import { useSearchParams } from "react-router-dom"; // 주소창 ?documentId=3 값을 읽고 바꾸는 훅 (day04)
import PageTitle from "../../Layout/PageTitle";
import GridTitle from "../../Layout/GridTitle";

// 06 출고지시 — 담당: 김지환
export default function AllocationPage(props) {
  // ─────────────── 1. 주소창의 documentId 읽기 ───────────────
  // searchParams : 주소창 ? 뒤의 값들을 담은 객체
  // setSearchParams : 주소창 ? 뒤의 값을 바꾸는 함수 (바뀌면 화면이 다시 그려짐)
  const [searchParams, setSearchParams] = useSearchParams();
  // "/outbounds/allocation?documentId=3" 이면 "3" (문자열), 없으면 null
  const documentId = searchParams.get("documentId");

  // ─────────────── 2. 상태변수 ───────────────
  const [orders, setOrders] = useState([]);     // 접수(WAITING) 상태 주문 목록
  const [detail, setDetail] = useState(null);   // 선택한 주문 상세 (null = 아직 선택 안 함)
  const [stocks, setStocks] = useState([]);     // 전체 재고 목록
  const [pickings, setPickings] = useState([]); // 선택한 주문의 피킹리스트
  const [confirmed, setConfirmed] = useState([]); // 출고확정 누른 detailId 목록 (중복 클릭 방지용)

  // ─────────────── 3. 다시 불러올 일이 있는 조회 함수 ───────────────
  // 할당·확정 후에 "다시" 불러야 해서 useEffect 밖에 함수로 만든다 (수업은 useEffect 안에서만 만들었음)

  // 전체 재고 조회 (조현우 담당 API)
  const getStocks = async () => {
    const response = await axios.get("/wms/stocks");
    setStocks(response.data); // [{stockId, productCode, lotCode, locationCode, expiryDate, availableQty ...}]
  };

  // ED-19 피킹리스트 조회
  const getPickings = async () => {
    if (documentId === null) return; // 선택한 주문이 없으면 부르지 않고 함수 종료
    const response = await axios.get("http://localhost:8080/wms/allocations/" + documentId);
    setPickings(response.data); // [{detailId, locationCode, productCode, productName, lotCode, qty}]
  };

  // ─────────────── 4. 최초 1번: 주문목록 + 재고 ───────────────
  useEffect(() => {
    async function getOrders() {
      // ED-12 출고 목록 조회
      const response = await axios.get("http://localhost:8080/wms/outbounds");
      let result = []; // 접수 상태만 담을 새 배열
      for (let i = 0; i < response.data.length; i++) {
        if (response.data[i].status === "WAITING") { // 상태가 접수인 주문만
          result.push(response.data[i]);
        }
      }
      setOrders(result);
    }
    getOrders();
    getStocks();
  }, []); // [] : 최초 1번만 실행

  // ─────────────── 5. documentId가 바뀔 때마다: 상세 + 피킹리스트 ───────────────
  useEffect(() => {
    async function getDetail() {
      if (documentId === null) return; // 선택 안 했으면 종료
      // ED-17 출고 상세 조회
      const response = await axios.get("http://localhost:8080/wms/outbounds/" + documentId);
      setDetail(response.data); // {documentNo, partnerName, items:[{documentItemId, productCode, productName, expectedQty}]}
    }
    getDetail();
    getPickings();
  }, [documentId]); // 의존성 배열에 documentId → 최초 1번 + documentId 바뀔 때마다 실행 (day05 Lifecycle [3])

  // ─────────────── 6. 할당 (ED-18) ───────────────
  // event : form 제출 이벤트, documentItemId : 어느 품목 줄에서 눌렀는지
  const 할당 = async (event, documentItemId) => {
    event.preventDefault(); // form 새로고침 막기

    // 같은 품목 재고가 하나도 없으면 select가 비어서 값이 ""
    if (event.target.stockId.value === "") {
      alert("할당할 재고가 없습니다");
      return;
    }

    // 서버로 보낼 객체 (AllocationDto 필드명과 똑같이)
    const obj = {
      documentItemId: documentItemId,
      stockId: parseInt(event.target.stockId.value), // select 값은 문자열 → 정수 변환
      qty: parseInt(event.target.qty.value),         // 입력 수량 → 정수 변환
    };

    // 수량 검사 : 백엔드가 가용수량 검사를 안 하므로 프론트에서 막는다
    if (isNaN(obj.qty) || obj.qty <= 0) {
      alert("수량을 1 이상 입력하세요");
      return;
    }
    for (let i = 0; i < stocks.length; i++) {
      if (stocks[i].stockId === obj.stockId && obj.qty > stocks[i].availableQty) {
        alert("가용수량(" + stocks[i].availableQty + ")보다 많이 할당할 수 없습니다");
        return;
      }
    }

    // POST 요청. 응답 = 새로 만들어진 detailId (숫자)
    const response = await axios.post("http://localhost:8080/wms/allocations", obj);
    if (response.data) {   // detailId가 오면 성공
      alert("할당 완료");
      getPickings();        // 피킹리스트에 방금 할당한 줄이 추가됨
      getStocks();          // 선점수량이 늘었으니 가용수량 새로고침
    }
  };

  // ─────────────── 7. 출고확정 (ED-20) ───────────────
  const 출고확정 = async (detailId) => {
    // PUT 요청. 사용법은 post와 같다 (주소, 보낼 객체)
    const response = await axios.put("http://localhost:8080/wms/allocations", { detailId: detailId });
    if (response.data === true) {
      setConfirmed([...confirmed, detailId]); // 기존 배열 복사 + 새 값 추가 (day03 Component3 방식)
      getStocks();                            // 실재고가 줄었으니 새로고침
    } else {
      alert("출고확정 실패");
    }
  };

  // 조회 버튼 — 이번 단계에서는 새로고침만 막는다
  const 조회 = (event) => {
    event.preventDefault();
  };

  return (
    <>
      <PageTitle title="출고지시" path="홈 > 출고관리 > 출고지시" />

      <form className="search" onSubmit={조회}>
        <label>출고요청일</label>
        <input type="date" name="from" /> ~ <input type="date" name="to" />
        <label>배송지명</label>
        <input type="text" name="partnerName" />
        <label>주문상태</label>
        <select name="status">
          <option value="WAITING">접수</option>
        </select>
        <input type="submit" className="btn primary" value="조회" />
      </form>

      {/* ━━━━━━━━━━ 표1. 출고 대상 주문 ━━━━━━━━━━ */}
      <GridTitle title="출고 대상 주문 (접수)" desc={`총 ${orders.length}건`} />
      <table className="grid">
        <thead>
          <tr>
            <th>NO</th>
            <th>주문번호</th>
            <th>배송지명</th>
            <th>출고요청일</th>
            <th>상태</th>
            <th>선택</th>
          </tr>
        </thead>
        <tbody>
          {orders.map((row, index) => (
            <tr key={row.documentId}>
              <td>{index + 1}</td>
              <td>{row.documentNo}</td>
              <td>{row.partnerName}</td>
              <td>{row.expectedAt.replace("T", " ")}</td>
              <td>{row.status}</td>
              <td>
                {/* 누르면 주소창이 ?documentId=번호 로 바뀜 → 5번 useEffect가 다시 실행됨 */}
                <button className="btn" onClick={() => setSearchParams({ documentId: row.documentId })}>
                  선택
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      {/* ━━━━━━━━━━ 표2. 선택한 주문의 품목 + 할당 ━━━━━━━━━━ */}
      {/* 삼항연산자 : detail이 null이면 앞, 아니면 뒤 (day10 방식) */}
      <GridTitle
        title="주문 품목 / 재고 할당"
        desc={detail === null ? "주문을 선택하세요" : detail.documentNo + " · " + detail.partnerName}
      />
      <table className="grid">
        <thead>
          <tr>
            <th>품목코드</th>
            <th>품목명</th>
            <th>주문수량</th>
            <th>재고 선택 (로케이션 / LOT / 소비기한 / 가용)</th>
          </tr>
        </thead>
        <tbody>
          {detail === null ? (
            <tr>
              <td colSpan="4">위에서 주문을 선택하세요</td>
            </tr>
          ) : (
            detail.items.map((item) => {
              // 이 품목과 같은 productCode + 가용수량이 남은 재고만 골라 담기
              let 같은품목재고 = [];
              for (let i = 0; i < stocks.length; i++) {
                if (stocks[i].productCode === item.productCode && stocks[i].availableQty > 0) {
                  같은품목재고.push(stocks[i]);
                }
              }
              return (
                <tr key={item.documentItemId}>
                  <td>{item.productCode}</td>
                  <td>{item.productName}</td>
                  <td>{item.expectedQty}</td>
                  <td>
                    {/* 제출하면 할당 함수에 event와 이 줄의 documentItemId를 같이 넘김 */}
                    <form onSubmit={(event) => 할당(event, item.documentItemId)}>
                      <select name="stockId">
                        {같은품목재고.map((s) => (
                          <option key={s.stockId} value={s.stockId}>
                            {s.locationCode} / {s.lotCode} / {s.expiryDate} / 가용 {s.availableQty}
                          </option>
                        ))}
                      </select>
                      <input type="number" name="qty" defaultValue={item.expectedQty} style={{ width: "70px" }} />
                      <input type="submit" className="btn" value="할당" />
                    </form>
                  </td>
                </tr>
              );
            })
          )}
        </tbody>
      </table>

      {/* ━━━━━━━━━━ 표3. 피킹리스트 + 출고확정 ━━━━━━━━━━ */}
      <GridTitle title="피킹리스트 (로케이션순)" desc={`총 ${pickings.length}건`} />
      <table className="grid">
        <thead>
          <tr>
            <th>피킹번호</th>
            <th>로케이션</th>
            <th>품목코드</th>
            <th>품목명</th>
            <th>LOT</th>
            <th>수량</th>
            <th>출고확정</th>
          </tr>
        </thead>
        <tbody>
          {pickings.map((p) => (
            <tr key={p.detailId}>
              <td>{p.detailId}</td>
              <td>{p.locationCode}</td>
              <td>{p.productCode}</td>
              <td>{p.productName}</td>
              <td>{p.lotCode}</td>
              <td>{p.qty}</td>
              <td>
                {/* includes : 배열 안에 이 detailId가 있으면 true → 이미 확정한 줄 */}
                {confirmed.includes(p.detailId) ? (
                  "완료"
                ) : (
                  <button className="btn primary" onClick={() => 출고확정(p.detailId)}>
                    확정
                  </button>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </>
  );
}