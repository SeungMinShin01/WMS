import { useEffect, useState } from "react";
import axios from "axios";
import { useNavigate, useParams } from "react-router-dom";
import PageTitle from "../../Layout/PageTitle";
import GridTitle from "../../Layout/GridTitle";
import DocumentHeader from "../../Layout/DocumentHeader";

// 문서 상태 영어 값 → 화면에 보여줄 한글
const STATUS_NAME = {
  WAITING: "접수",
  ALLOCATED: "할당",
  PICKING: "피킹중",
  SHIPPED: "출고완료",
  CANCELED: "취소",
};

// 07 출고지시 — 담당: 김지환
// 흐름 : 목록 → 주문 클릭(상세) → 품목 체크 → 추천 받기(미리보기) → 추천 수정 → 피킹리스트 생성
export default function AllocationPage(props) {
  // ─────────────── 1. 주소의 documentId (/outbounds/allocation/3) ───────────────
  const { documentId } = useParams(); // 목록 주소면 undefined
  const navigate = useNavigate();

  // ─────────────── 2. 상태변수 ───────────────
  const [outbounds, setOutbounds] = useState([]);
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
  const getList = async () => {
    const response = await axios.get("/wms/outbounds");
    setOutbounds(response.data);
  };

  // 검색 조건으로 거른 목록 (status 는 "WAITING,ALLOCATED" 처럼 여러 개)
  const visibleList = outbounds.filter((outbound) => {
    if (!filter.status.split(",").includes(outbound.status)) return false;
    const day = outbound.expectedAt.substring(0, 10);
    if (filter.from !== "" && day < filter.from) return false;
    if (filter.to !== "" && day > filter.to) return false;
    if (
      filter.partnerName !== "" &&
      !outbound.partnerName.includes(filter.partnerName)
    )
      return false;
    return true;
  });

  const handleSearch = (event) => {
    event.preventDefault();
    setFilter({
      from: event.target.from.value,
      to: event.target.to.value,
      partnerName: event.target.partnerName.value.trim(),
      status: event.target.status.value,
    });
  };

  const handleReset = (event) => {
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
    getList();
    getStocks();
  }, []);

  // ─────────────── 5. 주소의 번호가 바뀔 때마다 : 상세 다시, 체크·추천 초기화 ───────────────
  useEffect(() => {
    getDetail();
    setChecked([]);
    setPreviews([]);
  }, [documentId]);

  // 목록 줄 클릭 / 뒤로가기 / 피킹리스트로 : 주소만 바꾸면 위 useEffect 가 상세를 다시 부름
  const selectDocument = (id) => navigate(`/outbounds/allocation/${id}`);
  const goBack = () => navigate("/outbounds/allocation");
  const goPicking = () => navigate(`/outbounds/picking/${documentId}`);

  // ─────────────── 6. 상태 판단 도우미 ───────────────
  const isAllocatable = () =>
    detail !== null &&
    (detail.status === "WAITING" || detail.status === "ALLOCATED");
  const isShipped = () => detail !== null && detail.status === "SHIPPED";
  const isAllocated = (item) => item.allocatedQty > 0;
  const getCheckableItems = () =>
    detail === null
      ? []
      : detail.items.filter((item) => isAllocatable() && !isAllocated(item));

  // ─────────────── 7. 체크박스 ───────────────
  const toggleCheck = (documentItemId) => {
    if (checked.includes(documentItemId)) {
      setChecked(checked.filter((id) => id !== documentItemId));
    } else {
      setChecked([...checked, documentItemId]);
    }
  };

  const toggleCheckAll = () => {
    const ids = getCheckableItems().map((item) => item.documentItemId);
    setChecked(ids.length > 0 && checked.length === ids.length ? [] : ids);
  };

  // ─────────────── 8. 추천 받기 (미리보기, 저장 안 함) ───────────────
  const getPreview = async () => {
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
  const updatePreview = (index, key, value) => {
    const copy = [...previews];
    copy[index] = { ...copy[index], [key]: parseInt(value) || 0 }; // 빈 칸이면 0
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
    // 가용 초과 막기 : 수량을 바꿨든 재고를 바꿨든 최대값으로 깎음
    const max = getMaxQty(copy, index);
    if (copy[index].qty > max) {
      copy[index] = { ...copy[index], qty: max };
    }
    setPreviews(copy);
  };

  const addPreviewRow = (index) => {
    const copy = [...previews];
    copy.splice(index + 1, 0, { ...previews[index], qty: 0 });
    setPreviews(copy);
  };

  const removePreviewRow = (index) => {
    setPreviews(previews.filter((p, i) => i !== index));
  };

  const sumPreviewQty = (documentItemId) => {
    let sum = 0;
    for (let i = 0; i < previews.length; i++) {
      if (previews[i].documentItemId === documentItemId)
        sum += previews[i].qty || 0;
    }
    return sum;
  };

  // index 번째 줄에 넣을 수 있는 최대 수량
  // = 고른 재고의 가용 - 같은 재고를 쓰는 다른 줄들의 수량
  const getMaxQty = (list, index) => {
    const row = list[index];
    const stock = stocks.find((s) => s.stockId === row.stockId);
    if (!stock) return 0;
    let used = 0;
    for (let i = 0; i < list.length; i++) {
      if (i !== index && list[i].stockId === row.stockId)
        used += list[i].qty || 0;
    }
    return Math.max(stock.availableQty - used, 0);
  };

  // ─────────────── 10. 피킹리스트 생성 (실제 할당) ───────────────
  const createPickingList = async () => {
    if (previews.length === 0) {
      alert("먼저 추천을 받으세요");
      return;
    }
    // 생성 직전 확인 : 0 이하 줄, 가용 초과 줄
    for (let i = 0; i < previews.length; i++) {
      if (previews[i].qty <= 0) {
        alert(`${i + 1}번째 줄 수량이 0입니다. 수량을 넣거나 줄을 삭제하세요`);
        return;
      }
      if (previews[i].qty > getMaxQty(previews, i)) {
        alert(`${i + 1}번째 줄이 가용재고를 넘습니다`);
        return;
      }
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
      getList(); // 목록 상태 새로고침
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
          <form className="search" onSubmit={handleSearch}>
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
            <button type="button" className="btn" onClick={handleReset}>
              초기화
            </button>
          </form>

          <GridTitle
            title="출고 대상 주문"
            desc={`총 ${visibleList.length}건`}
          />
          <table className="grid">
            <thead>
              <tr>
                <th>No</th>
                <th>주문번호</th>
                <th>배송지명</th>
                <th>출고요청일</th>
                <th>상태</th>
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
                  <td>{outbound.expectedAt.replace("T", " ")}</td>
                  <td>{STATUS_NAME[outbound.status]}</td>
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
            statusName={STATUS_NAME[detail.status]}
          />
          <div className="detail-bar">
            <button className="btn" onClick={goBack}>
              목록
            </button>
            {detail.status !== "WAITING" && (
              <button className="btn" onClick={goPicking}>
                피킹리스트 보기
              </button>
            )}
          </div>

          {/* ── 위: 주문 품목 (체크) ── */}
          <GridTitle title="주문 품목" desc={`체크 ${checked.length}건`}>
            {isAllocatable() && (
              <button
                className="btn primary"
                onClick={getPreview}
                disabled={loading}
              >
                {loading ? "처리 중..." : "추천 받기"}
              </button>
            )}
          </GridTitle>
          <table className="grid">
            <thead>
              <tr>
                <th>
                  <input
                    type="checkbox"
                    disabled={getCheckableItems().length === 0}
                    checked={
                      getCheckableItems().length > 0 &&
                      checked.length === getCheckableItems().length
                    }
                    onChange={toggleCheckAll}
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
                      disabled={isAllocated(item) || !isAllocatable()}
                      onChange={() => toggleCheck(item.documentItemId)}
                    />
                  </td>
                  <td>{item.productCode}</td>
                  <td>{item.productName}</td>
                  <td>{item.expectedQty}</td>
                  <td>{item.allocatedQty}</td>
                  <td
                    style={{
                      color:
                        !isShipped() &&
                        !isAllocated(item) &&
                        item.availableQty < item.expectedQty
                          ? "red"
                          : "",
                    }}
                  >
                    {isShipped() ? "-" : item.availableQty}
                  </td>
                  <td>
                    {isShipped()
                      ? "출고완료"
                      : isAllocated(item)
                        ? "할당됨"
                        : item.availableQty < item.expectedQty
                          ? "재고 부족"
                          : "-"}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

          {/* ── 아래: 할당 추천 (수정 가능) ── */}
          <GridTitle
            title="할당 추천 (수정 가능)"
            desc={`총 ${previews.length}건`}
          >
            {previews.length > 0 && (
              <button
                className="btn primary"
                onClick={createPickingList}
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
                <th>로케이션</th>
                <th>LOT</th>
                <th>소비기한</th>
                <th>가용</th>
                <th>수량</th>
                <th>품목 합계 / 주문</th>
                <th>줄</th>
              </tr>
            </thead>
            <tbody>
              {previews.length === 0 ? (
                <tr>
                  <td colSpan="8">
                    위에서 품목을 체크하고 [추천 받기]를 누르세요
                  </td>
                </tr>
              ) : (
                previews.map((p, index) => {
                  const sameProductStocks = stocks.filter(
                    (s) =>
                      s.productCode === p.productCode &&
                      (s.availableQty > 0 || s.stockId === p.stockId),
                  );
                  // 지금 고른 재고 (LOT · 소비기한 · 가용 칸에 표시)
                  const stock = stocks.find((s) => s.stockId === p.stockId);
                  const item = detail.items.find(
                    (it) => it.documentItemId === p.documentItemId,
                  );
                  const total = sumPreviewQty(p.documentItemId);
                  const maxQty = getMaxQty(previews, index);
                  return (
                    <tr key={index}>
                      <td>{p.productName}</td>
                      <td>
                        <select
                          value={p.stockId}
                          onChange={(e) =>
                            updatePreview(index, "stockId", e.target.value)
                          }
                        >
                          {sameProductStocks.map((s) => (
                            <option key={s.stockId} value={s.stockId}>
                              {s.locationCode}
                            </option>
                          ))}
                        </select>
                      </td>
                      <td>{stock ? stock.lotCode : "-"}</td>
                      <td>{stock ? stock.expiryDate : "-"}</td>
                      <td className="num">
                        {stock ? stock.availableQty : "-"}
                      </td>
                      <td>
                        <input
                          type="number"
                          value={p.qty}
                          min="1"
                          max={maxQty}
                          title={`최대 ${maxQty}`}
                          style={{ width: "70px" }}
                          onChange={(e) =>
                            updatePreview(index, "qty", e.target.value)
                          }
                        />
                      </td>
                      <td
                        style={{
                          color:
                            item && total !== item.expectedQty ? "red" : "",
                        }}
                      >
                        {total} / {item ? item.expectedQty : "-"}
                      </td>
                      <td>
                        <button
                          className="btn"
                          onClick={() => addPreviewRow(index)}
                        >
                          +
                        </button>{" "}
                        <button
                          className="btn danger"
                          onClick={() => removePreviewRow(index)}
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
