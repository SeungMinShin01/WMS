import { useState, useEffect } from "react";
import axios from "axios";
import { useNavigate, useParams } from "react-router-dom";
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

// 07 출고지시 — 담당: 김지환
// 흐름 : 목록 → 주문 클릭(상세) → 품목 체크 → 추천 받기(미리보기) → 추천 수정 → 피킹리스트 생성
export default function AllocationPage(props) {
  // ─────────────── 1. 주소의 documentId (/outbounds/allocation/3) ───────────────
  const { documentId } = useParams(); // 목록 주소면 undefined
  const navigate = useNavigate();

  // ─────────────── 2. 상태변수 ───────────────
  const [allOrders, setAllOrders] = useState([]);
  const [filter, setFilter] = useState({
    from: "",
    to: "",
    partnerName: "",
    status: "WAITING,ALLOCATED",
  });
  const [detail, setDetail] = useState(null); // 선택한 주문 상세 (null = 목록 화면)
  const [stocks, setStocks] = useState([]); // 전체 재고 (추천 수정 select 에 씀)
  const [checked, setChecked] = useState([]); // 체크한 품목 줄 id 목록
  const [previews, setPreviews] = useState([]); // 추천 결과 (사용자가 수정하는 값)
  const [loading, setLoading] = useState(false); // 요청 중이면 버튼 잠금

  // ─────────────── 3. 조회 함수 ───────────────
  const getOrders = async () => {
    const response = await axios.get("/wms/outbounds");
    setAllOrders(response.data);
  };

  // 검색 조건으로 거른 목록 (status 는 "WAITING,ALLOCATED" 처럼 여러 개)
  const orders = allOrders.filter((row) => {
    if (!filter.status.split(",").includes(row.status)) return false;
    const day = row.expectedAt.substring(0, 10);
    if (filter.from !== "" && day < filter.from) return false;
    if (filter.to !== "" && day > filter.to) return false;
    if (
      filter.partnerName !== "" &&
      !row.partnerName.includes(filter.partnerName)
    )
      return false;
    return true;
  });

  const 조회 = (event) => {
    event.preventDefault();
    setFilter({
      from: event.target.from.value,
      to: event.target.to.value,
      partnerName: event.target.partnerName.value.trim(),
      status: event.target.status.value,
    });
  };

  const 검색초기화 = (event) => {
    event.target.form.reset();
    setFilter({
      from: "",
      to: "",
      partnerName: "",
      status: "WAITING,ALLOCATED",
    });
  };

  const getStocks = async () => {
    const response = await axios.get("/wms/stocks");
    setStocks(response.data);
  };

  // ED-17 주문 상세 : 주소에 번호가 없으면 목록 화면
  const getDetail = async () => {
    if (!documentId) {
      setDetail(null);
      return;
    }
    const response = await axios.get("/wms/outbounds/" + documentId);
    setDetail(response.data);
  };

  // ─────────────── 4. 최초 1번 : 주문목록 + 재고 ───────────────
  useEffect(() => {
    getOrders();
    getStocks();
  }, []);

  // ─────────────── 5. 주소의 번호가 바뀔 때마다 : 상세 다시, 체크·추천 초기화 ───────────────
  useEffect(() => {
    getDetail();
    setChecked([]);
    setPreviews([]);
  }, [documentId]);

  // 목록 줄 클릭 / 뒤로가기 / 피킹리스트로 : 주소만 바꾸면 위 useEffect 가 상세를 다시 부름
  const 주문선택 = (id) => navigate(`/outbounds/allocation/${id}`);
  const 뒤로가기 = () => navigate("/outbounds/allocation");
  const 피킹리스트보기 = () => navigate(`/outbounds/picking/${documentId}`);

  // ─────────────── 6. 상태 판단 도우미 ───────────────
  const 할당가능문서 = () =>
    detail !== null &&
    (detail.status === "WAITING" || detail.status === "ALLOCATED");
  const 출고완료문서 = () => detail !== null && detail.status === "SHIPPED";
  const 할당됨 = (item) => item.allocatedQty > 0;
  const 체크가능품목 = () =>
    detail === null
      ? []
      : detail.items.filter((item) => 할당가능문서() && !할당됨(item));

  // ─────────────── 7. 체크박스 ───────────────
  const 체크 = (documentItemId) => {
    if (checked.includes(documentItemId)) {
      setChecked(checked.filter((id) => id !== documentItemId));
    } else {
      setChecked([...checked, documentItemId]);
    }
  };

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
      const response = await axios.get(
        "/wms/allocations/" + documentId + "/preview",
        {
          params: { documentItemIds: checked.join(",") },
        },
      );
      setPreviews(response.data);
    } catch (error) {
      alert(error.response.data);
    } finally {
      setLoading(false);
    }
  };

  // ─────────────── 9. 추천 수정 ───────────────
  const 추천수정 = (index, key, value) => {
    const copy = [...previews];
    copy[index] = { ...copy[index], [key]: parseInt(value) };
    if (key === "stockId") {
      const s = stocks.find((x) => x.stockId === parseInt(value));
      if (s)
        copy[index] = {
          ...copy[index],
          locationCode: s.locationCode,
          lotCode: s.lotCode,
          expiryDate: s.expiryDate,
        };
    }
    setPreviews(copy);
  };

  const 줄추가 = (index) => {
    const copy = [...previews];
    copy.splice(index + 1, 0, { ...previews[index], qty: 0 });
    setPreviews(copy);
  };

  const 줄삭제 = (index) => {
    setPreviews(previews.filter((p, i) => i !== index));
  };

  const 입력합계 = (documentItemId) => {
    let sum = 0;
    for (let i = 0; i < previews.length; i++) {
      if (previews[i].documentItemId === documentItemId)
        sum += previews[i].qty || 0;
    }
    return sum;
  };

  // ─────────────── 10. 피킹리스트 생성 (실제 할당) ───────────────
  const 피킹리스트생성 = async () => {
    if (previews.length === 0) {
      alert("먼저 추천을 받으세요");
      return;
    }
    const rows = previews.map((p) => ({
      documentItemId: p.documentItemId,
      stockId: p.stockId,
      qty: p.qty,
    }));
    try {
      setLoading(true);
      await axios.post("/wms/allocations/" + documentId + "/pickinglist", rows);
      alert("피킹리스트가 생성되었습니다");
      setChecked([]);
      setPreviews([]);
      getStocks(); // 선점수량 반영
      getDetail(); // 문서 상태·할당수량 새로고침
      getOrders(); // 목록 상태 새로고침
    } catch (error) {
      alert(error.response.data);
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <PageTitle title="출고지시" path="홈 > 출고관리 > 출고지시" />

      {detail === null ? (
        <>
          {/* ━━━━━━━━━━ 목록 ━━━━━━━━━━ */}
          <form className="search" onSubmit={조회}>
            <label>출고요청일</label>
            <input type="date" name="from" /> ~ <input type="date" name="to" />
            <label>배송지명</label>
            <input type="text" name="partnerName" />
            <label>주문상태</label>
            <select name="status" defaultValue="WAITING,ALLOCATED">
              <option value="WAITING,ALLOCATED">
                지시 대상 전체 (접수·할당)
              </option>
              <option value="WAITING">접수</option>
              <option value="ALLOCATED">할당</option>
            </select>
            <input type="submit" className="btn primary" value="조회" />
            <button type="button" className="btn" onClick={검색초기화}>
              초기화
            </button>
          </form>

          <GridTitle title="출고 대상 주문" desc={`총 ${orders.length}건`} />
          <table className="grid">
            <thead>
              <tr>
                <th>NO</th>
                <th>주문번호</th>
                <th>배송지명</th>
                <th>출고요청일</th>
                <th>상태</th>
              </tr>
            </thead>
            <tbody>
              {orders.map((row, index) => (
                <tr
                  key={row.documentId}
                  onClick={() => 주문선택(row.documentId)}
                >
                  <td>{index + 1}</td>
                  <td>{row.documentNo}</td>
                  <td>{row.partnerName}</td>
                  <td>{row.expectedAt.replace("T", " ")}</td>
                  <td>{상태표시(row.status)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      ) : (
        <>
          {/* ━━━━━━━━━━ 상세: 헤더 + 위 주문품목 / 아래 할당추천 ━━━━━━━━━━ */}
          <GridTitle title="출고 정보" desc={detail.documentNo} />
          <DocumentHeader
            type="OUTBOUND"
            doc={detail}
            statusName={상태표시(detail.status)}
          />
          <div className="detail-bar">
            <button className="btn" onClick={뒤로가기}>
              목록
            </button>
            {detail.status !== "WAITING" && (
              <button className="btn" onClick={피킹리스트보기}>
                피킹리스트 보기
              </button>
            )}
          </div>

          {/* ── 위: 주문 품목 (체크) ── */}

          <table className="grid">
            <thead>
              <tr>
                <th>
                  <input
                    type="checkbox"
                    disabled={체크가능품목().length === 0}
                    checked={
                      체크가능품목().length > 0 &&
                      checked.length === 체크가능품목().length
                    }
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
              {detail.items.map((item) => (
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
                  <td
                    style={{
                      color:
                        !출고완료문서() &&
                        !할당됨(item) &&
                        item.availableQty < item.expectedQty
                          ? "red"
                          : "",
                    }}
                  >
                    {출고완료문서() ? "-" : item.availableQty}
                  </td>
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
              ))}
            </tbody>
          </table>
          <GridTitle title="주문 품목" desc={`체크 ${checked.length}건`}>
            {할당가능문서() && (
              <button
                className="btn primary"
                onClick={추천받기}
                disabled={loading}
              >
                {loading ? "처리 중..." : "추천 받기"}
              </button>
            )}
          </GridTitle>
          {/* ── 아래: 할당 추천 (수정 가능) ── */}
          <GridTitle
            title="할당 추천 (수정 가능)"
            desc={`총 ${previews.length}건`}
          >
            {previews.length > 0 && (
              <button
                className="btn primary"
                onClick={피킹리스트생성}
                disabled={loading}
              >
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
                  <td colSpan="5">
                    위에서 품목을 체크하고 [추천 받기]를 누르세요
                  </td>
                </tr>
              ) : (
                previews.map((p, index) => {
                  const 같은품목재고 = stocks.filter(
                    (s) =>
                      s.productCode === p.productCode &&
                      (s.availableQty > 0 || s.stockId === p.stockId),
                  );
                  const item = detail.items.find(
                    (it) => it.documentItemId === p.documentItemId,
                  );
                  const 합계 = 입력합계(p.documentItemId);
                  return (
                    <tr key={index}>
                      <td>{p.productName}</td>
                      <td>
                        <select
                          value={p.stockId}
                          onChange={(e) =>
                            추천수정(index, "stockId", e.target.value)
                          }
                        >
                          {같은품목재고.map((s) => (
                            <option key={s.stockId} value={s.stockId}>
                              {s.locationCode} / {s.lotCode} / {s.expiryDate} /
                              가용 {s.availableQty}
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
                          onChange={(e) =>
                            추천수정(index, "qty", e.target.value)
                          }
                        />
                      </td>
                      <td
                        style={{
                          color: item && 합계 !== item.expectedQty ? "red" : "",
                        }}
                      >
                        {합계} / {item ? item.expectedQty : "-"}
                      </td>
                      <td>
                        <button className="btn" onClick={() => 줄추가(index)}>
                          +
                        </button>{" "}
                        <button
                          className="btn danger"
                          onClick={() => 줄삭제(index)}
                        >
                          -
                        </button>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </>
      )}
    </>
  );
}
