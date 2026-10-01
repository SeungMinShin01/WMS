import { useState, useEffect } from "react";
import axios from "axios";
import { useNavigate, useParams } from "react-router-dom";
import PageTitle from "../../Layout/PageTitle";
import GridTitle from "../../Layout/GridTitle";
import DocumentHeader from "../../Layout/DocumentHeader";

const 상태이름 = {
  WAITING: "접수",
  ALLOCATED: "할당",
  PICKING: "피킹중",
  SHIPPED: "출고완료",
  CANCELED: "취소",
};
const 상태표시 = (status) => 상태이름[status] || status;

// 08 피킹리스트 — 담당: 김지환
// 목록(피킹 줄이 있는 문서) → 상세(헤더 + 피킹리스트) → 출고확정
export default function PickingPage(props) {
  const { documentId } = useParams();
  const navigate = useNavigate();

  const [allOrders, setAllOrders] = useState([]);
  const [status, setStatus] = useState("ALLOCATED,PICKING,SHIPPED"); // 목록 상태 조건
  const [detail, setDetail] = useState(null); // null = 목록 화면
  const [pickings, setPickings] = useState([]); // 선택 문서의 피킹 줄
  const [loading, setLoading] = useState(false);

  const getOrders = async () => {
    const response = await axios.get("/wms/outbounds");
    setAllOrders(response.data);
  };

  const getDetail = async () => {
    if (!documentId) {
      setDetail(null);
      setPickings([]);
      return;
    }
    const response = await axios.get("/wms/outbounds/" + documentId);
    setDetail(response.data);
  };

  // ED-19 피킹리스트
  const getPickings = async () => {
    if (!documentId) return;
    const response = await axios.get("/wms/allocations/" + documentId);
    setPickings(response.data); // [{detailId, locationCode, productCode, productName, lotCode, qty}]
  };

  useEffect(() => {
    getOrders();
  }, []);

  useEffect(() => {
    getDetail();
    getPickings();
  }, [documentId]);

  const orders = allOrders.filter((row) =>
    status.split(",").includes(row.status),
  );

  const 조회 = (event) => {
    event.preventDefault();
    setStatus(event.target.status.value);
  };

  const 문서선택 = (id) => navigate(`/outbounds/picking/${id}`);
  const 뒤로가기 = () => navigate("/outbounds/picking");

  // ED-20 출고확정 (문서 단위) : PICKING → SHIPPED
  const 출고확정 = async () => {
    if (!confirm("피킹리스트 전체를 출고확정할까요?")) return;
    try {
      setLoading(true);
      await axios.put("/wms/outbounds/" + documentId + "/ship");
      alert("출고확정되었습니다");
      getDetail();
      getOrders();
    } catch (error) {
      alert(error.response.data);
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <PageTitle title="피킹리스트" path="홈 > 출고관리 > 피킹리스트" />

      {detail === null ? (
        <>
          {/* ── 목록 ── */}
          <form className="search" onSubmit={조회}>
            <label>상태</label>
            <select name="status" defaultValue="ALLOCATED,PICKING,SHIPPED">
              <option value="ALLOCATED,PICKING,SHIPPED">
                전체 (할당·피킹중·출고완료)
              </option>
              <option value="PICKING">피킹중 (출고확정 대기)</option>
              <option value="ALLOCATED">할당</option>
              <option value="SHIPPED">출고완료</option>
            </select>
            <input type="submit" className="btn primary" value="조회" />
          </form>

          <GridTitle title="피킹 대상 주문" desc={`총 ${orders.length}건`} />
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
                  onClick={() => 문서선택(row.documentId)}
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
          {/* ── 상세: 헤더 + 피킹리스트 + 출고확정 ── */}
          <GridTitle title="출고 정보" desc={detail.documentNo} />
          <DocumentHeader
            type="OUTBOUND"
            doc={detail}
            statusName={상태표시(detail.status)}
          />

          <GridTitle
            title="피킹리스트 (로케이션순)"
            desc={`총 ${pickings.length}건`}
          >
            <button className="btn" onClick={뒤로가기}>
              목록
            </button>
            {detail.status === "PICKING" && (
              <button
                className="btn primary"
                onClick={출고확정}
                disabled={loading}
              >
                {loading ? "처리 중..." : "출고확정"}
              </button>
            )}
            {detail.status === "SHIPPED"}
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
      )}
    </>
  );
}
