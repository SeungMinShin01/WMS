import { useState, useEffect } from "react";
import axios from "axios";
import { useParams, useNavigate } from "react-router-dom";
import PageTitle from "../../Layout/PageTitle";
import GridTitle from "../../Layout/GridTitle";

// 피킹리스트 — 담당: 김지환
// 흐름 : 문서 선택 → 줄마다 [집음] → 전 줄 집음 → 출고확정
// 전 품목이 할당된 문서만 집을 수 있다 (ED-52 결정)
// QR 피킹 : 문서를 고른 뒤 작업자를 [배정] → 작업자가 모바일에서 스캔 → 줄이 집음으로 바뀜
export default function PickingPage() {
  // 주소의 documentId (/outbounds/picking/66) — 목록 주소(/outbounds/picking)면 undefined
  const { documentId } = useParams();
  const navigate = useNavigate();

  const [orders, setOrders] = useState([]); // 피킹할 수 있는 문서 목록 (할당 · 피킹중)
  const [tenants, setTenants] = useState([]); // [ED-61] 화주 선택 목록 [{tenantId, tenantName}]
  // 조회 조건 : 화주 · 출고요청일(from ~ to) · 상태 (출고문서 · 출고지시 화면과 같은 순서)
  // status 의 "ALLOCATED,PICKING" = 전체 (피킹 대상 상태인 출고할당·재고피킹 둘 다)
  const [filter, setFilter] = useState({
    tenantId: "", // [ED-61] "" = 전체
    from: "",
    to: "",
    status: "ALLOCATED,PICKING",
  });
  const [detail, setDetail] = useState(null); // 선택한 출고 문서 정보
  const [pickings, setPickings] = useState([]); // 피킹 줄 목록
  const [loading, setLoading] = useState(false); // 요청 중이면 true → 버튼 잠금
  const [picking, setPicking] = useState(null); // 이 문서의 QR 피킹 리스트 (없으면 null)
  const [workers, setWorkers] = useState([]); // 배정할 작업자 목록
  const [workerId, setWorkerId] = useState(""); // 고른 작업자 ("" = 안 고름)
  // 칸 제목 클릭 정렬 : sortKey = 정렬 중인 칸 이름, sortAsc = true 오름차순 / false 내림차순
  // 처음 화면 : 주문번호 오름차순(▲)으로 시작
  const [sortKey, setSortKey] = useState("documentNo");
  const [sortAsc, setSortAsc] = useState(true);

  // 문서 상태 한글 (AllocationPage 와 같은 이름)
  const 문서상태 = (status) => {
    if (status === "WAITING") return "출고예정";
    if (status === "ALLOCATED") return "출고할당";
    if (status === "PICKING") return "재고피킹";
    if (status === "SHIPPED") return "출고완료";
    if (status === "CANCELED") return "취소";
    return status;
  };

  // 줄 상태 한글
  const 줄상태 = (status) => {
    if (status === "ALLOCATED") return "할당됨";
    if (status === "PICKED") return "집음";
    if (status === "SHIPPED") return "출고됨";
    return status;
  };

  // QR 피킹 리스트 상태 한글
  const QR상태 = (status) => {
    if (status === "CREATED") return "생성";
    if (status === "ASSIGNED") return "배정됨";
    if (status === "PICKING") return "피킹중";
    if (status === "DONE") return "완료";
    if (status === "CANCELED") return "취소";
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

  // ED-76 이 문서의 QR 피킹 리스트 : 전체 목록에서 문서 번호가 같은 것을 찾음 (없으면 null)
  // 주소의 documentId 는 글자("66"), 응답의 documentId 는 숫자(66) → String 으로 맞춰서 비교
  const getPicking = async () => {
    if (!documentId) return;
    try {
      const response = await axios.get("/wms/pickings");
      let found = null;
      for (let i = 0; i < response.data.length; i++) {
        if (String(response.data[i].documentId) === documentId) {
          found = response.data[i];
        }
      }
      setPicking(found);
    } catch (error) {
      에러알림(error);
    }
  };

  // 배정할 작업자 목록 : 작업자(WORKER) 이면서 사용 중인 계정만
  // /wms/workers 는 관리자 전용이라 작업자로 로그인했으면 403 → 알림 없이 빈 목록
  const getWorkers = async () => {
    try {
      const response = await axios.get("/wms/workers");
      let result = [];
      for (let i = 0; i < response.data.length; i++) {
        const w = response.data[i];
        if (w.role === "WORKER" && w.active !== false) {
          result.push(w);
        }
      }
      setWorkers(result);
    } catch {
      setWorkers([]);
    }
  };

  // 주소의 documentId 가 바뀔 때마다 : 없으면 문서 목록, 있으면 그 문서의 정보 + 피킹리스트 + QR 피킹 리스트
  useEffect(() => {
    if (!documentId) {
      setDetail(null);
      setPickings([]);
      setPicking(null);
      getOrders(filter.tenantId);
      getTenants();
    } else {
      getDetail();
      getPickings();
      getPicking();
      getWorkers();
    }
  }, [documentId]);

  // 조회 버튼 - 입력한 조건 저장 + 화주 조건으로 서버에서 다시 받아옴 (출고지시 화면과 같은 방식)
  const handleSearch = (event) => {
    event.preventDefault();
    const tenantId = event.target.tenantId.value; // [ED-61]
    setFilter({
      tenantId: tenantId,
      from: event.target.from.value,
      to: event.target.to.value,
      status: event.target.status.value,
    });
    getOrders(tenantId);
  };

  // 초기화 버튼 - 입력칸을 처음 값으로 되돌리고 전체로 다시 조회
  // event.target.form : 버튼이 들어 있는 form / reset() : 입력칸을 defaultValue 로 되돌림
  const handleReset = (event) => {
    event.target.form.reset();
    setFilter({
      tenantId: "",
      from: "",
      to: "",
      status: "ALLOCATED,PICKING",
    });
    getOrders("");
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

  // 배정할 수 있는 문서인지 : 출고할당 + 전 품목 할당 + QR 피킹 리스트가 없거나 아직 배정 전(생성)
  // 한 번 배정하면 담당자 이름만 보이고 다시 배정할 수 없음 (서버도 409)
  const 배정가능 =
    detail !== null &&
    detail.status === "ALLOCATED" &&
    전품목할당 &&
    (picking === null || picking.status === "CREATED");

  // ED-75 작업자 배정
  // 1. QR 피킹 리스트가 아직 없으면 먼저 만듦 (POST /wms/pickings)
  // 2. 만든(또는 있던) 리스트에 작업자 배정 (PUT /wms/pickings/{id}/assign)
  const 배정 = async () => {
    if (workerId === "") {
      alert("배정할 작업자를 선택하세요");
      return;
    }
    try {
      setLoading(true);
      let pickingListId;
      if (picking === null) {
        const created = await axios.post("/wms/pickings", {
          documentId: Number(documentId),
        });
        pickingListId = created.data.pickingListId;
      } else {
        pickingListId = picking.pickingListId;
      }
      await axios.put("/wms/pickings/" + pickingListId + "/assign", {
        workerId: Number(workerId),
      });
      alert("배정되었습니다");
      setWorkerId("");
      getPicking(); // 담당자 이름 새로고침
    } catch (error) {
      에러알림(error);
      getPicking(); // 생성은 됐는데 배정만 실패했을 수도 있으니 다시 조회
    } finally {
      setLoading(false);
    }
  };

  // 줄 1개 집음 (ED-52 피킹 확인)
  const 집음 = async (detailId) => {
    try {
      setLoading(true);
      await axios.put("/wms/pickings/" + detailId);
      getPickings(); // 줄 상태 새로고침
      getDetail(); // 처음 집으면 문서가 재고피킹으로 바뀌니까 문서도 새로고침
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
      alert(
        "할당이 끝나지 않은 품목이 있어 출고확정할 수 없습니다: " +
          덜할당품목.join(", "),
      );
      return;
    }
    if (pickings.length === 0 || 남은줄 > 0) {
      alert(
        "모든 줄을 집어야 출고확정할 수 있습니다 (남은 줄 " + 남은줄 + "개)",
      );
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
  // ── 칸 제목 클릭 정렬 (주문번호 · 출고요청일) ── 출고문서 화면과 같은 방식
  // 정렬 중인 칸을 다시 누르면 방향만 뒤집음 (▲ → ▼ → ▲ ...)
  // 다른 칸을 누르면 그 칸 내림차순(▼)부터 : 목록이 원래 빠른 순이라 첫 클릭에 바로 순서가 바뀌어 보이게
  const changeSort = (key) => {
    if (sortKey === key) {
      setSortAsc(!sortAsc);
    } else {
      setSortKey(key);
      setSortAsc(false);
    }
  };

  // 칸 제목 옆 화살표 : 정렬할 수 있는 칸에는 처음부터 항상 화살표 하나가 보임
  // 정렬 중인 칸 : 지금 방향대로 ▲(오름차순) 또는 ▼(내림차순)
  // 나머지 칸 : ▲ (누르면 그 칸으로 정렬할 수 있다는 표시)
  // fontSize 0.8em : 칸 제목 글자보다 조금 작게
  const arrow = (key) => {
    if (sortKey === key && !sortAsc) {
      return <span style={{ fontSize: "0.8em" }}>▼</span>;
    }
    return <span style={{ fontSize: "0.8em" }}>▲</span>;
  };

  // 조회 조건(상태 · 출고요청일)으로 거른 목록 (화주는 서버에서 이미 걸러서 받아온다)
  // status 는 "ALLOCATED,PICKING" 처럼 여러 개 → split(",") 로 나눠서 그 안에 있는지 확인
  // 출고요청일이 비어 있는 문서는 날짜 조건을 고르면 빠짐
  const visibleList = orders.filter((order) => {
    if (!filter.status.split(",").includes(order.status)) return false;
    const day = order.expectedAt ? order.expectedAt.substring(0, 10) : "";
    if (filter.from !== "" && (day === "" || day < filter.from)) return false;
    if (filter.to !== "" && (day === "" || day > filter.to)) return false;
    return true;
  });

  // 문서 목록 : 걸러진 목록(visibleList)을 복사해서 정렬
  // [...배열] : 배열을 복사 (원본은 그대로 두고 복사본만 정렬)
  const sortedList = [...visibleList];
  sortedList.sort((a, b) => {
    // a[sortKey] : sortKey 가 "documentNo" 면 a.documentNo 와 같음 (칸 이름을 변수로 받아서 꺼냄)
    // 두 칸 모두 글자("OUT-20260902-001", "2026-09-02T10:00:00")라서 글자 순 비교 = 번호 순 · 날짜 순
    // localeCompare : a 가 앞이면 음수, 같으면 0, 뒤면 양수 (자바 compareTo 와 같음)
    // 출고요청일이 비어 있는 문서가 있을 수 있어서(아래 표에서도 "-" 처리) 비어 있으면 "" 로 비교
    const aValue = a[sortKey] ? a[sortKey] : "";
    const bValue = b[sortKey] ? b[sortKey] : "";
    const result = aValue.localeCompare(bValue);
    // 내림차순이면 부호를 뒤집어서 순서를 반대로
    return sortAsc ? result : -result;
  });

  if (!documentId) {
    return (
      <>
        <PageTitle title="피킹리스트" path="홈 > 출고관리 > 피킹리스트" />

        {/* 조회 조건 : 화주 · 출고요청일 · 상태 + 조회 · 초기화 (출고문서 · 출고지시 화면과 같은 모양) */}
        <form className="search" onSubmit={handleSearch}>
          {/* [ED-61] 화주 선택 */}
          <label>화주</label>
          <select name="tenantId" defaultValue="">
            <option value="">전체</option>
            {tenants.map((t) => (
              <option key={t.tenantId} value={t.tenantId}>
                {t.tenantName}
              </option>
            ))}
          </select>
          <label>출고요청일</label>
          <input type="date" name="from" /> ~ <input type="date" name="to" />
          <label>상태</label>
          {/* 전체 = 피킹 대상 상태(출고할당·재고피킹)만 */}
          <select name="status" defaultValue="ALLOCATED,PICKING">
            <option value="ALLOCATED,PICKING">전체</option>
            <option value="ALLOCATED">출고할당</option>
            <option value="PICKING">재고피킹</option>
          </select>
          <input type="submit" className="btn primary" value="조회" />
          <button type="button" className="btn" onClick={handleReset}>
            초기화
          </button>
        </form>

        <GridTitle
          title="피킹할 출고 문서"
          desc={"총 " + visibleList.length + "건"}
        />
        <table className="grid">
          <thead>
            <tr>
              {/* 정렬 가능한 칸 : 클릭하면 changeSort, 옆에 화살표 표시 (cursor pointer : 마우스를 올리면 손가락 모양) */}
              <th
                style={{ cursor: "pointer" }}
                onClick={() => changeSort("documentNo")}
              >
                주문번호 {arrow("documentNo")}
              </th>
              <th>화주</th>
              <th>배송지명</th>
              <th
                style={{ cursor: "pointer" }}
                onClick={() => changeSort("expectedAt")}
              >
                출고요청일 {arrow("expectedAt")}
              </th>
              <th>상태</th>
            </tr>
          </thead>
          <tbody>
            {visibleList.length === 0 && (
              <tr>
                <td colSpan="5">
                  피킹할 문서가 없습니다 (출고지시에서 피킹리스트를 먼저
                  생성하세요)
                </td>
              </tr>
            )}
            {sortedList.map((row) => (
              <tr
                key={row.documentId}
                onClick={() => navigate(`/outbounds/picking/${row.documentId}`)}
              >
                <td>{row.documentNo}</td>
                <td>{row.tenantName}</td>
                <td>{row.partnerName}</td>
                <td>
                  {row.expectedAt ? row.expectedAt.replace("T", " ") : "-"}
                </td>
                <td>{문서상태(row.status)}</td>
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
              <td>
                {detail.expectedAt ? detail.expectedAt.replace("T", " ") : "-"}
              </td>
              <th>품목수</th>
              <td>{detail.items.length}</td>
              <th>출고확정일시</th>
              <td>
                {detail.completedAt
                  ? detail.completedAt.replace("T", " ")
                  : "-"}
              </td>
            </tr>
            <tr>
              <th>담당자</th>
              <td>
                {picking && picking.workerName ? picking.workerName : "-"}
              </td>
              <th>QR 피킹</th>
              <td>
                {picking
                  ? picking.pickingNo + " (" + QR상태(picking.status) + ")"
                  : "-"}
              </td>
              <th></th>
              <td></td>
            </tr>
          </tbody>
        </table>
      )}

      <div style={{ textAlign: "right", margin: "8px 0" }}>
        {/* 작업자 배정 : 출고할당 + 전 품목 할당 + 아직 스캔 시작 전일 때만 보임 */}
        {배정가능 && (
          <>
            <select
              value={workerId}
              onChange={(e) => setWorkerId(e.target.value)}
            >
              <option value="">작업자 선택</option>
              {workers.map((w) => (
                <option key={w.userId} value={w.userId}>
                  {w.userName} ({w.loginId})
                </option>
              ))}
            </select>{" "}
            <button className="btn primary" onClick={배정} disabled={loading}>
              배정
            </button>{" "}
          </>
        )}
        {/* 번호 없는 주소로 가서 문서 선택 목록으로 돌아감 */}
        <button className="btn" onClick={() => navigate("/outbounds/picking")}>
          목록
        </button>
      </div>

      {/* 덜 할당된 문서 안내 */}
      {detail && detail.status !== "SHIPPED" && !전품목할당 && (
        <p style={{ color: "red", margin: "8px 0" }}>
          할당이 끝나지 않은 품목이 있어 피킹을 시작할 수 없습니다 (
          {덜할당품목.join(", ")}). 출고지시에서 나머지 품목을 먼저 할당하세요.
        </p>
      )}

      {/* 피킹리스트 */}
      <GridTitle
        title="피킹리스트 (로케이션순)"
        desc={"총 " + pickings.length + "건"}
      >
        <button
          className="btn"
          onClick={전체집음}
          disabled={loading || !집기가능}
        >
          전체 집음
        </button>{" "}
        <button className="btn primary" onClick={출고확정} disabled={loading}>
          출고확정
        </button>
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
                  <button
                    className="btn"
                    onClick={() => 집음(row.detailId)}
                    disabled={loading}
                  >
                    집음
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
  );
}
