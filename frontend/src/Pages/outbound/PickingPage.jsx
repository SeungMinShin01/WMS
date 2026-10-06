import { useState, useEffect } from "react";
import axios from "axios";
import { useParams, useNavigate } from "react-router-dom";
import PageTitle from "../../Layout/PageTitle";
import GridTitle from "../../Layout/GridTitle";

// 피킹리스트 — 담당: 김지환
// 흐름 : 문서 선택 → 줄마다 [집음] → 전 줄 집음 → 출고확정
// 전 품목이 할당된 문서만 집을 수 있다 (ED-52 결정)
export default function PickingPage(props) {
  // 주소의 documentId (/outbounds/picking/66) — 목록 주소(/outbounds/picking)면 undefined
  const { documentId } = useParams();
  const navigate = useNavigate();

  const [orders, setOrders] = useState([]);       // 피킹할 수 있는 문서 목록 (할당 · 피킹중)
  const [tenants, setTenants] = useState([]);     // [ED-61] 화주 선택 목록 [{tenantId, tenantName}]
  const [tenantId, setTenantId] = useState("");   // [ED-61] 고른 화주 ("" = 전체)
  const [detail, setDetail] = useState(null);     // 선택한 출고 문서 정보
  const [pickings, setPickings] = useState([]);   // 피킹 줄 목록
  const [loading, setLoading] = useState(false);  // 요청 중이면 true → 버튼 잠금

  // 문서 상태 한글 (AllocationPage 와 같은 이름)
  const 문서상태 = (status) => {
    if (status === "WAITING") return "출고예정";
    if (status === "ALLOCATED") return "출고할당";
    if (status === "PICKING") return "재고피킹";
    if (status === "SHIPPED") return "출고완료";
    if (status === "CANCELED") return "취소";
    return status;
  };

  // [ED-61] 출처 한글
  const 출처 = (source) => {
    if (source === "WMS") return "WMS";
    if (source === "PORTAL") return "화주요청";
    return source;
  };

  // 줄 상태 한글
  const 줄상태 = (status) => {
    if (status === "ALLOCATED") return "할당됨";
    if (status === "PICKED") return "집음";
    if (status === "SHIPPED") return "출고됨";
    return status;
  };

  // 에러 메시지 보여주기 (서버가 꺼져 있으면 error.response 가 없음)
  const 에러알림 = (error) => {
    if (error.response) {
      alert(error.response.data);
    } else {
      alert("서버에 연결할 수 없습니다");
    }
  };

  // ED-12 출고 목록 중 할당 · 피킹중 문서만
  // [ED-61] 화주를 고르면 서버에서 그 화주 문서만 받아온다
  const getOrders = async (selectedTenantId) => {
    try {
      let url = "/wms/outbounds";
      if (selectedTenantId) {
        url = url + "?tenantId=" + selectedTenantId;
      }
      const response = await axios.get(url);
      let result = [];
      for (let i = 0; i < response.data.length; i++) {
        const status = response.data[i].status;
        if (status === "ALLOCATED" || status === "PICKING") {
          result.push(response.data[i]);
        }
      }
      setOrders(result);
    } catch (error) {
      에러알림(error);
    }
  };

  // [ED-61] 화주 선택 목록 : 전체 출고 문서에 들어 있는 화주를 중복 없이 모은다
  const getTenants = async () => {
    try {
      const response = await axios.get("/wms/outbounds");
      let result = [];
      for (let i = 0; i < response.data.length; i++) {
        const doc = response.data[i];
        let exists = false;
        for (let j = 0; j < result.length; j++) {
          if (result[j].tenantId === doc.tenantId) exists = true;
        }
        if (!exists) {
          result.push({ tenantId: doc.tenantId, tenantName: doc.tenantName });
        }
      }
      setTenants(result);
    } catch (error) {
      에러알림(error);
    }
  };

  // ED-17 문서 정보 (품목마다 주문수량 expectedQty · 할당수량 allocatedQty 포함)
  const getDetail = async () => {
    if (!documentId) return;
    try {
      const response = await axios.get("/wms/outbounds/" + documentId);
      setDetail(response.data);
    } catch (error) {
      에러알림(error);
    }
  };

  // ED-19 피킹리스트
  const getPickings = async () => {
    if (!documentId) return;
    try {
      const response = await axios.get("/wms/allocations/" + documentId);
      setPickings(response.data); // [{detailId, locationCode, productCode, productName, lotCode, qty, status}]
    } catch (error) {
      에러알림(error);
    }
  };

  // 주소의 documentId 가 바뀔 때마다 : 없으면 문서 목록, 있으면 그 문서의 정보 + 피킹리스트
  useEffect(() => {
    if (!documentId) {
      setDetail(null);
      setPickings([]);
      getOrders(tenantId);
      getTenants();
    } else {
      getDetail();
      getPickings();
    }
  }, [documentId]);

  // [ED-61] 화주 선택을 바꾸면 그 화주 문서로 다시 조회
  const 화주변경 = (event) => {
    setTenantId(event.target.value);
    getOrders(event.target.value);
  };

  // 전 품목 할당 여부 : 품목마다 할당수량이 주문수량보다 적으면 아직 덜 할당된 것
  let 덜할당품목 = [];
  if (detail !== null) {
    for (let i = 0; i < detail.items.length; i++) {
      if (detail.items[i].allocatedQty < detail.items[i].expectedQty) {
        덜할당품목.push(detail.items[i].productName);
      }
    }
  }
  const 전품목할당 = detail !== null && 덜할당품목.length === 0;

  // 안 집은 줄 개수 (출고확정 안내에 씀)
  let 남은줄 = 0;
  for (let i = 0; i < pickings.length; i++) {
    if (pickings[i].status !== "PICKED") {
      남은줄++;
    }
  }

  // 집을 수 있는 문서인지 : 출고할당 또는 재고피킹 + 전 품목 할당 완료
  const 집기가능 =
    detail !== null &&
    (detail.status === "ALLOCATED" || detail.status === "PICKING") &&
    전품목할당;

  // 줄 1개 집음 (ED-52 피킹 확인)
  const 집음 = async (detailId) => {
    try {
      setLoading(true);
      await axios.put("/wms/pickings/" + detailId);
      getPickings();  // 줄 상태 새로고침
      getDetail();    // 처음 집으면 문서가 재고피킹으로 바뀌니까 문서도 새로고침
    } catch (error) {
      에러알림(error);
    } finally {
      setLoading(false);
    }
  };

  // 전체 집음 : 할당됨 줄을 하나씩 차례로 집음 (서버에는 줄 단위 API 만 있음)
  const 전체집음 = async () => {
    try {
      setLoading(true);
      for (let i = 0; i < pickings.length; i++) {
        if (pickings[i].status === "ALLOCATED") {
          await axios.put("/wms/pickings/" + pickings[i].detailId);
        }
      }
    } catch (error) {
      에러알림(error);
    } finally {
      setLoading(false);
      getPickings();
      getDetail();
    }
  };

  // 출고확정 (ED-20) : 버튼은 항상 누를 수 있고, 아직 안 되는 상태면 이유를 알려줌
  const 출고확정 = async () => {
    if (detail === null) return;
    if (detail.status === "SHIPPED") {
      alert("이미 출고완료된 문서입니다");
      return;
    }
    if (!전품목할당) {
      alert("할당이 끝나지 않은 품목이 있어 출고확정할 수 없습니다: " + 덜할당품목.join(", "));
      return;
    }
    if (pickings.length === 0 || 남은줄 > 0) {
      alert("모든 줄을 집어야 출고확정할 수 있습니다 (남은 줄 " + 남은줄 + "개)");
      return;
    }
    if (!confirm("출고확정 하시겠습니까?")) return;
    try {
      setLoading(true);
      await axios.put("/wms/outbounds/" + documentId + "/ship");
      alert("출고확정 되었습니다");
      getPickings();
      getDetail();
    } catch (error) {
      에러알림(error);
    } finally {
      setLoading(false);
    }
  };

  // ─────────────── 문서를 아직 안 골랐을 때 : 피킹할 문서 목록 ───────────────
  if (!documentId) {
    return (
      <>
        <PageTitle title="피킹리스트" path="홈 > 출고관리 > 피킹리스트" />

        {/* [ED-61] 화주 선택 */}
        <form className="search" onSubmit={(e) => e.preventDefault()}>
          <label>화주</label>
          <select value={tenantId} onChange={화주변경}>
            <option value="">전체</option>
            {tenants.map((t) => (
              <option key={t.tenantId} value={t.tenantId}>
                {t.tenantName}
              </option>
            ))}
          </select>
        </form>

        <GridTitle title="피킹할 출고 문서" desc={"총 " + orders.length + "건"} />
        <table className="grid">
          <thead>
            <tr>
              <th>주문번호</th>
              <th>화주</th>
              <th>배송지명</th>
              <th>출고요청일</th>
              <th>출처</th>
              <th>상태</th>
              <th>선택</th>
            </tr>
          </thead>
          <tbody>
            {orders.length === 0 && (
              <tr>
                <td colSpan="7">피킹할 문서가 없습니다 (출고지시에서 피킹리스트를 먼저 생성하세요)</td>
              </tr>
            )}
            {orders.map((row) => (
              <tr key={row.documentId}>
                <td>{row.documentNo}</td>
                <td>{row.tenantName}</td>
                <td>{row.partnerName}</td>
                <td>{row.expectedAt ? row.expectedAt.replace("T", " ") : "-"}</td>
                <td>{출처(row.source)}</td>
                <td>{문서상태(row.status)}</td>
                <td>
                  <button className="btn" onClick={() => navigate(`/outbounds/picking/${row.documentId}`)}>선택</button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </>
    );
  }

  // ─────────────── 문서를 골랐을 때 : 출고 정보 + 피킹리스트 ───────────────
  return (
    <>
      <PageTitle title="피킹리스트" path="홈 > 출고관리 > 피킹리스트" />

      {/* 출고 정보 */}
      <GridTitle title="출고 정보" desc={detail ? detail.documentNo : ""} />
      {detail && (
        <table className="grid">
          <tbody>
            <tr>
              <th>주문번호</th>
              <td>{detail.documentNo}</td>
              <th>배송지명</th>
              <td>{detail.partnerName}</td>
              <th>상태</th>
              <td>{문서상태(detail.status)}</td>
            </tr>
            <tr>
              <th>출고요청일</th>
              <td>{detail.expectedAt ? detail.expectedAt.replace("T", " ") : "-"}</td>
              <th>품목수</th>
              <td>{detail.items.length}</td>
              <th>출고확정일시</th>
              <td>{detail.completedAt ? detail.completedAt.replace("T", " ") : "-"}</td>
            </tr>
          </tbody>
        </table>
      )}

      <div style={{ textAlign: "right", margin: "8px 0" }}>
        {/* 번호 없는 주소로 가서 문서 선택 목록으로 돌아감 */}
        <button className="btn" onClick={() => navigate("/outbounds/picking")}>목록</button>
      </div>

      {/* 덜 할당된 문서 안내 */}
      {detail && detail.status !== "SHIPPED" && !전품목할당 && (
        <p style={{ color: "red", margin: "8px 0" }}>
          할당이 끝나지 않은 품목이 있어 피킹을 시작할 수 없습니다 ({덜할당품목.join(", ")}).
          출고지시에서 나머지 품목을 먼저 할당하세요.
        </p>
      )}

      {/* 피킹리스트 */}
      <GridTitle title="피킹리스트 (로케이션순)" desc={"총 " + pickings.length + "건"}>
        <button className="btn" onClick={전체집음} disabled={loading || !집기가능}>전체 집음</button>{" "}
        <button className="btn primary" onClick={출고확정} disabled={loading}>출고확정</button>
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
            <th>상태</th>
            <th>집음</th>
          </tr>
        </thead>
        <tbody>
          {pickings.length === 0 && (
            <tr>
              <td colSpan="8">피킹리스트가 없습니다</td>
            </tr>
          )}
          {pickings.map((row) => (
            <tr key={row.detailId}>
              <td>{row.detailId}</td>
              <td>{row.locationCode}</td>
              <td>{row.productCode}</td>
              <td>{row.productName}</td>
              <td>{row.lotCode}</td>
              <td>{row.qty}</td>
              <td>{줄상태(row.status)}</td>
              <td>
                {row.status === "ALLOCATED" && 집기가능 ? (
                  <button className="btn" onClick={() => 집음(row.detailId)} disabled={loading}>집음</button>
                ) : (
                  "-"
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </>
  );
}