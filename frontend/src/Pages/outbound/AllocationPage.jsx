import { useState, useEffect } from "react";
import axios from "axios";
import { useSearchParams } from "react-router-dom"; // 주소창 ?documentId=3 값을 읽고 바꾸는 훅 (day04)
import PageTitle from "../../Layout/PageTitle";
import GridTitle from "../../Layout/GridTitle";

// 문서 상태 영어 값 → 화면에 보여줄 한글 (값에 없는 상태는 영어 그대로)
const 상태이름 = { WAITING: "접수", ALLOCATED: "할당", PICKING: "피킹중", SHIPPED: "출고완료", CANCELED: "취소" };
const 상태표시 = (status) => 상태이름[status] || status;

// 06 출고지시 — 담당: 김지환
// 흐름 : ⓪ 검색 → ① 주문 선택 → ② 품목 체크 → ③ 추천 받기(미리보기) → ④ 추천 수정 → ⑤ 피킹리스트 생성 → ⑥ 출고확정(문서 단위)
// 문서 상태 : WAITING → (일부 할당) ALLOCATED → (전부 할당) PICKING → (출고확정) SHIPPED
export default function AllocationPage(props) {
  // ─────────────── 1. 주소창의 documentId 읽기 ───────────────
  const [searchParams, setSearchParams] = useSearchParams();
  const documentId = searchParams.get("documentId"); // 없으면 null

  // ─────────────── 2. 상태변수 ───────────────
  const [allOrders, setAllOrders] = useState([]); // 서버에서 받은 출고 문서 전체 (검색은 이 목록을 화면에서 걸러서 보여줌)
  const [filter, setFilter] = useState({ from: "", to: "", partnerName: "", status: "ACTIVE" }); // 검색 조건 (ACTIVE = 진행중 전체)
  const [detail, setDetail] = useState(null);     // 선택한 주문 상세 (null = 선택 안 함)
  const [stocks, setStocks] = useState([]);       // 전체 재고 목록 (추천 수정할 때 select 에 씀)
  const [checked, setChecked] = useState([]);     // 체크한 품목 줄 id 목록 [21, 22]
  const [previews, setPreviews] = useState([]);   // 추천 결과 (사용자가 수정하는 값) [{documentItemId, stockId, qty, ...}]
  const [pickings, setPickings] = useState([]);   // 선택한 주문의 피킹리스트
  const [loading, setLoading] = useState(false);  // 요청 중이면 true → 버튼 잠금 (더블클릭 방지)

  // ─────────────── 3. 조회 함수 (할당 후 다시 불러야 해서 useEffect 밖에 만듦) ───────────────

  // ED-12 출고 목록 : 전체를 받아두고, 화면에 보여줄 때 검색 조건으로 거름
  const getOrders = async () => {
    const response = await axios.get("/wms/outbounds");
    setAllOrders(response.data);
  };

  // 검색 조건으로 거른 목록 (화면 표1 에 보여줄 값)
  //   status : ACTIVE(진행중 = 접수·할당·피킹중) / 특정 상태 / ALL(전체)
  //   from·to : 출고요청일 범위 (날짜 문자열 "2026-10-02" 끼리 비교)
  //   partnerName : 배송지명에 이 글자가 포함되면 통과
  const orders = allOrders.filter((row) => {
    const status = row.status;
    if (filter.status === "ACTIVE") {
      if (status !== "WAITING" && status !== "ALLOCATED" && status !== "PICKING") return false;
    } else if (filter.status !== "ALL") {
      if (status !== filter.status) return false;
    }
    const day = row.expectedAt.substring(0, 10); // "2026-10-02T10:00:00" → "2026-10-02"
    if (filter.from !== "" && day < filter.from) return false;
    if (filter.to !== "" && day > filter.to) return false;
    if (filter.partnerName !== "" && !row.partnerName.includes(filter.partnerName)) return false;
    return true;
  });

  // 조회 버튼 : 폼에 입력한 값을 검색 조건으로 저장 → orders 가 다시 계산됨
  const 조회 = (event) => {
    event.preventDefault(); // form 새로고침 막기
    setFilter({
      from: event.target.from.value,
      to: event.target.to.value,
      partnerName: event.target.partnerName.value.trim(),
      status: event.target.status.value,
    });
  };

  // 초기화 버튼 : 폼 입력값과 검색 조건을 처음 상태로
  const 검색초기화 = (event) => {
    event.target.form.reset();
    setFilter({ from: "", to: "", partnerName: "", status: "ACTIVE" });
  };

  // 전체 재고 (조현우 담당 API)
  const getStocks = async () => {
    const response = await axios.get("/wms/stocks");
    setStocks(response.data); // [{stockId, productCode, lotCode, locationCode, expiryDate, availableQty ...}]
  };

  // ED-17 주문 상세
  const getDetail = async () => {
    if (documentId === null) return;
    const response = await axios.get("/wms/outbounds/" + documentId);
    setDetail(response.data); // {documentNo, partnerName, status, items:[{documentItemId, productCode, productName, expectedQty, allocatedQty, availableQty}]}
  };

  // ED-19 피킹리스트
  const getPickings = async () => {
    if (documentId === null) return;
    const response = await axios.get("/wms/allocations/" + documentId);
    setPickings(response.data); // [{detailId, locationCode, productCode, productName, lotCode, qty}]
  };

  // ─────────────── 4. 최초 1번 : 주문목록 + 재고 ───────────────
  useEffect(() => {
    getOrders();
    getStocks();
  }, []);

  // ─────────────── 5. 주문이 바뀔 때마다 : 상세 + 피킹리스트, 체크·추천 초기화 ───────────────
  useEffect(() => {
    getDetail();
    getPickings();
    setChecked([]);
    setPreviews([]);
  }, [documentId]);

  // ─────────────── 6. 상태 판단 도우미 ───────────────
  // 할당 가능한 문서인지 : 대기(WAITING) 또는 일부 할당(ALLOCATED)
  const 할당가능문서 = () => detail !== null && (detail.status === "WAITING" || detail.status === "ALLOCATED");
  // 출고 완료된 문서인지 : 문서 상태가 SHIPPED
  const 출고완료문서 = () => detail !== null && detail.status === "SHIPPED";
  // 이미 할당된 품목 줄인지 : 서버가 보내준 allocatedQty 로 판단
  const 할당됨 = (item) => item.allocatedQty > 0;
  // 체크할 수 있는 품목 줄 : 할당 가능한 문서 + 아직 할당 안 된 줄
  const 체크가능품목 = () => (detail === null ? [] : detail.items.filter((item) => 할당가능문서() && !할당됨(item)));

  // ─────────────── 7. 체크박스 ───────────────
  const 체크 = (documentItemId) => {
    if (checked.includes(documentItemId)) {
      setChecked(checked.filter((id) => id !== documentItemId)); // 있으면 빼기
    } else {
      setChecked([...checked, documentItemId]);                   // 없으면 넣기
    }
  };

  // 품목 전체선택 : 체크 가능한 줄이 전부 체크돼 있으면 전부 해제, 아니면 전부 체크
  const 품목전체선택 = () => {
    const ids = 체크가능품목().map((item) => item.documentItemId);
    setChecked(ids.length > 0 && checked.length === ids.length ? [] : ids);
  };

  // ─────────────── 8. 추천 받기 (미리보기, 저장 안 함) ───────────────
  const 추천받기 = async () => {
    if (checked.length === 0) {
      alert("품목을 하나 이상 체크하세요");
      return;
    }
    try {
      setLoading(true);
      // GET /wms/allocations/10/preview?documentItemIds=21,22
      const response = await axios.get("/wms/allocations/" + documentId + "/preview", {
        params: { documentItemIds: checked.join(",") },
      });
      setPreviews(response.data); // 이 값을 아래 표에서 사용자가 수정함
    } catch (error) {
      alert(error.response.data); // 백엔드 메시지 (예: "신라면 가용 부족 · 필요 50 · 가용 30")
    } finally {
      setLoading(false);
    }
  };

  // ─────────────── 9. 추천 수정 ───────────────
  // index 번째 추천 줄의 key(stockId 또는 qty) 값을 바꿈
  const 추천수정 = (index, key, value) => {
    const copy = [...previews];                           // 배열 복사 (state 는 직접 바꾸면 안 됨)
    copy[index] = { ...copy[index], [key]: parseInt(value) }; // 그 줄만 새 객체로
    // 재고를 바꾸면 로케이션·LOT·소비기한 표시도 그 재고 것으로 바꿈
    if (key === "stockId") {
      const s = stocks.find((x) => x.stockId === parseInt(value));
      if (s) copy[index] = { ...copy[index], locationCode: s.locationCode, lotCode: s.lotCode, expiryDate: s.expiryDate };
    }
    setPreviews(copy);
  };

  // 같은 품목으로 줄 추가 (재고 두 군데에서 나눠 꺼내고 싶을 때)
  const 줄추가 = (index) => {
    const copy = [...previews];
    copy.splice(index + 1, 0, { ...previews[index], qty: 0 }); // 바로 아래에 복사본 끼워넣기
    setPreviews(copy);
  };

  const 줄삭제 = (index) => {
    setPreviews(previews.filter((p, i) => i !== index));
  };

  // 품목 줄별 입력 합계 (주문수량과 같아야 서버가 통과시킴)
  const 입력합계 = (documentItemId) => {
    let sum = 0;
    for (let i = 0; i < previews.length; i++) {
      if (previews[i].documentItemId === documentItemId) sum += previews[i].qty || 0;
    }
    return sum;
  };

  // ─────────────── 10. 피킹리스트 생성 (실제 할당) ───────────────
  const 피킹리스트생성 = async () => {
    if (previews.length === 0) {
      alert("먼저 추천을 받으세요");
      return;
    }
    // 서버로 보낼 값 : AllocationDto 필드만 [{documentItemId, stockId, qty}]
    const rows = previews.map((p) => ({ documentItemId: p.documentItemId, stockId: p.stockId, qty: p.qty }));
    try {
      setLoading(true); // 요청 끝날 때까지 버튼 잠금
      await axios.post("/wms/allocations/" + documentId + "/pickinglist", rows);
      alert("피킹리스트가 생성되었습니다");
      setChecked([]);
      setPreviews([]);
      getPickings();  // 방금 만든 줄 표시
      getStocks();    // 선점수량이 늘었으니 가용수량 새로고침
      getDetail();    // 문서 상태 (전 품목 할당 시 PICKING)
      getOrders();    // 목록의 상태도 새로고침
    } catch (error) {
      alert(error.response.data); // 400·404·409 메시지
    } finally {
      setLoading(false);
    }
  };

  // ─────────────── 11. 출고확정 (ED-20, 문서 단위) ───────────────
  // 피킹리스트 전체를 한 번에 출고 → 문서 PICKING → SHIPPED
  const 출고확정 = async () => {
    if (!confirm("피킹리스트 전체를 출고확정할까요?")) return; // 실수 클릭 방지
    try {
      setLoading(true); // 요청 끝날 때까지 버튼 잠금 (더블클릭 방지)
      await axios.put("/wms/outbounds/" + documentId + "/ship");
      alert("출고확정되었습니다");
      getDetail();  // 문서 상태 SHIPPED 로 새로고침 → 버튼 대신 "출고 완료" 표시
      getOrders();  // 목록 새로고침
      getStocks();  // 실재고 감소 반영
    } catch (error) {
      alert(error.response.data); // 이미 출고된 문서면 "이미 출고된 문서입니다" (409)
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <PageTitle title="출고지시" path="홈 > 출고관리 > 출고지시" />

      {/* ━━━━━━━━━━ 검색 폼 (화면에서 거르기) ━━━━━━━━━━ */}
      <form className="search" onSubmit={조회}>
        <label>출고요청일</label>
        <input type="date" name="from" /> ~ <input type="date" name="to" />
        <label>배송지명</label>
        <input type="text" name="partnerName" />
        <label>주문상태</label>
        <select name="status" defaultValue="ACTIVE">
          <option value="ACTIVE">진행중 전체</option>
          <option value="WAITING">접수</option>
          <option value="ALLOCATED">할당</option>
          <option value="PICKING">피킹중</option>
          <option value="SHIPPED">출고완료</option>
          <option value="ALL">전체</option>
        </select>
        <input type="submit" className="btn primary" value="조회" />
        <button type="button" className="btn" onClick={검색초기화}>초기화</button>
      </form>

      {/* ━━━━━━━━━━ 표1. 주문 목록 ━━━━━━━━━━ */}
      <GridTitle title="출고 대상 주문" desc={`총 ${orders.length}건`} />
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
            <tr key={row.documentId} className={String(row.documentId) === documentId ? "on" : ""}>
              <td>{index + 1}</td>
              <td>{row.documentNo}</td>
              <td>{row.partnerName}</td>
              <td>{row.expectedAt.replace("T", " ")}</td>
              <td>{상태표시(row.status)}</td>
              <td>
                <button className="btn" onClick={() => setSearchParams({ documentId: row.documentId })}>
                  선택
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      {/* ━━━━━━━━━━ 표2. 주문 품목 (체크) ━━━━━━━━━━ */}
      <GridTitle
        title="주문 품목"
        desc={detail === null ? "주문을 선택하세요" : detail.documentNo + " · " + detail.partnerName + " · " + 상태표시(detail.status)}
      >
        {할당가능문서() && (
          <button className="btn primary" onClick={추천받기} disabled={loading}>
            {loading ? "처리 중..." : "추천 받기"}
          </button>
        )}
      </GridTitle>
      <table className="grid">
        <thead>
          <tr>
            <th>
              {/* 전체선택 : 체크 가능한 품목이 있을 때만 */}
              <input
                type="checkbox"
                disabled={체크가능품목().length === 0}
                checked={체크가능품목().length > 0 && checked.length === 체크가능품목().length}
                onChange={품목전체선택}
              />
            </th>
            <th>품목코드</th>
            <th>품목명</th>
            <th>주문수량</th>
            <th>할당수량</th>
            <th>출고가능재고</th>
            <th>상태</th>
          </tr>
        </thead>
        <tbody>
          {detail === null ? (
            <tr>
              <td colSpan="7">위에서 주문을 선택하세요</td>
            </tr>
          ) : (
            detail.items.map((item) => (
              <tr key={item.documentItemId}>
                <td>
                  <input
                    type="checkbox"
                    checked={checked.includes(item.documentItemId)}
                    disabled={할당됨(item) || !할당가능문서()}
                    onChange={() => 체크(item.documentItemId)}
                  />
                </td>
                <td>{item.productCode}</td>
                <td>{item.productName}</td>
                <td>{item.expectedQty}</td>
                <td>{item.allocatedQty}</td>
                {/* 출고 완료 문서는 재고 표시가 의미 없으니 "-" */}
                {/* 할당 안 된 줄인데 출고가능재고가 주문수량보다 적으면 빨간색 → 추천 받으면 "가용 부족" */}
                <td style={{ color: !출고완료문서() && !할당됨(item) && item.availableQty < item.expectedQty ? "red" : "" }}>
                  {출고완료문서() ? "-" : item.availableQty}
                </td>
                {/* 상태 : 출고완료 > 할당됨 > 재고 부족 > - 순서로 판단 */}
                <td>
                  {출고완료문서()
                    ? "출고완료"
                    : 할당됨(item)
                    ? "할당됨"
                    : item.availableQty < item.expectedQty
                    ? "재고 부족"
                    : "-"}
                </td>
              </tr>
            ))
          )}
        </tbody>
      </table>

      {/* ━━━━━━━━━━ 표3. 추천 결과 (수정 가능) ━━━━━━━━━━ */}
      <GridTitle title="할당 추천 (수정 가능)" desc={`총 ${previews.length}건`}>
        {previews.length > 0 && (
          <button className="btn primary" onClick={피킹리스트생성} disabled={loading}>
            {loading ? "처리 중..." : "피킹리스트 생성"}
          </button>
        )}
      </GridTitle>
      <table className="grid">
        <thead>
          <tr>
            <th>품목명</th>
            <th>재고 (로케이션 / LOT / 소비기한 / 가용)</th>
            <th>수량</th>
            <th>품목 합계 / 주문</th>
            <th>줄</th>
          </tr>
        </thead>
        <tbody>
          {previews.length === 0 ? (
            <tr>
              <td colSpan="5">품목을 체크하고 [추천 받기]를 누르세요</td>
            </tr>
          ) : (
            previews.map((p, index) => {
              // 이 품목의 재고만 select 에 보여줌 (현재 선택된 재고는 가용 0이어도 포함)
              const 같은품목재고 = stocks.filter(
                (s) => s.productCode === p.productCode && (s.availableQty > 0 || s.stockId === p.stockId)
              );
              // 주문수량 찾기
              const item = detail.items.find((it) => it.documentItemId === p.documentItemId);
              const 합계 = 입력합계(p.documentItemId);
              return (
                <tr key={index}>
                  <td>{p.productName}</td>
                  <td>
                    <select value={p.stockId} onChange={(e) => 추천수정(index, "stockId", e.target.value)}>
                      {같은품목재고.map((s) => (
                        <option key={s.stockId} value={s.stockId}>
                          {s.locationCode} / {s.lotCode} / {s.expiryDate} / 가용 {s.availableQty}
                        </option>
                      ))}
                    </select>
                  </td>
                  <td>
                    <input
                      type="number"
                      value={p.qty}
                      min="1"
                      style={{ width: "70px" }}
                      onChange={(e) => 추천수정(index, "qty", e.target.value)}
                    />
                  </td>
                  {/* 합계가 주문수량과 다르면 빨간색 */}
                  <td style={{ color: item && 합계 !== item.expectedQty ? "red" : "" }}>
                    {합계} / {item ? item.expectedQty : "-"}
                  </td>
                  <td>
                    <button className="btn" onClick={() => 줄추가(index)}>+</button>{" "}
                    <button className="btn danger" onClick={() => 줄삭제(index)}>-</button>
                  </td>
                </tr>
              );
            })
          )}
        </tbody>
      </table>

      {/* ━━━━━━━━━━ 표4. 피킹리스트 + 출고확정 ━━━━━━━━━━ */}
      <GridTitle title="피킹리스트 (로케이션순)" desc={`총 ${pickings.length}건`}>
        {/* 문서가 PICKING 이면 확정 버튼, SHIPPED 면 완료 표시 */}
        {detail !== null && detail.status === "PICKING" && (
          <button className="btn primary" onClick={출고확정} disabled={loading}>
            {loading ? "처리 중..." : "출고확정"}
          </button>
        )}
        {detail !== null && detail.status === "SHIPPED" && <b>출고 완료</b>}
      </GridTitle>
      <table className="grid">
        <thead>
          <tr>
            <th>피킹번호</th>
            <th>로케이션</th>
            <th>품목코드</th>
            <th>품목명</th>
            <th>LOT</th>
            <th>수량</th>
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
            </tr>
          ))}
        </tbody>
      </table>
    </>
  );
}
