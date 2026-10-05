import { useState, useEffect } from "react";
import axios from "axios";
import { useSearchParams, useNavigate } from "react-router-dom";
import PageTitle from "../../Layout/PageTitle";
import GridTitle from "../../Layout/GridTitle";

// 피킹리스트 — 담당: 김지환
// 흐름 : 줄마다 [집음] → 전 줄 집음 → 출고확정
export default function PickingPage(props) {
  // 주소창 ?documentId=66 값 읽기
  const [searchParams] = useSearchParams();
  const documentId = searchParams.get("documentId");
  const navigate = useNavigate();

  const [detail, setDetail] = useState(null);     // 출고 문서 정보
  const [pickings, setPickings] = useState([]);   // 피킹 줄 목록
  const [loading, setLoading] = useState(false);  // 요청 중이면 true → 버튼 잠금

  // 문서 상태 한글
  const 문서상태 = (status) => {
    if (status === "WAITING") return "접수";
    if (status === "ALLOCATED") return "할당";
    if (status === "PICKING") return "피킹중";
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

  // 에러 메시지 보여주기 (서버가 꺼져 있으면 error.response 가 없음)
  const 에러알림 = (error) => {
    if (error.response) {
      alert(error.response.data);
    } else {
      alert("서버에 연결할 수 없습니다");
    }
  };

  // ED-17 문서 정보
  const getDetail = async () => {
    if (documentId === null) return;
    try {
      const response = await axios.get("/wms/outbounds/" + documentId);
      setDetail(response.data);
    } catch (error) {
      에러알림(error);
    }
  };

  // ED-19 피킹리스트
  const getPickings = async () => {
    if (documentId === null) return;
    try {
      const response = await axios.get("/wms/allocations/" + documentId);
      setPickings(response.data); // [{detailId, locationCode, productCode, productName, lotCode, qty, status}]
    } catch (error) {
      에러알림(error);
    }
  };

  // 주소의 documentId 가 바뀔 때마다 다시 조회
  useEffect(() => {
    getDetail();
    getPickings();
  }, [documentId]);

  // 줄 1개 집음 (ED-52 피킹 확인)
  const 집음 = async (detailId) => {
    try {
      setLoading(true);
      await axios.put("/wms/pickings/" + detailId);
      getPickings();  // 줄 상태 새로고침
      getDetail();    // 처음 집으면 문서가 피킹중으로 바뀌니까 문서도 새로고침
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

  // 출고확정 (ED-20)
  const 출고확정 = async () => {
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

  // 출고확정 가능 여부 : 문서가 피킹중 + 줄이 1개 이상 + 모든 줄이 집음
  let 전부집음 = pickings.length > 0;
  for (let i = 0; i < pickings.length; i++) {
    if (pickings[i].status !== "PICKED") {
      전부집음 = false;
    }
  }
  const 출고확정가능 = detail !== null && detail.status === "PICKING" && 전부집음;

  // 집을 수 있는 문서인지 : 할당 또는 피킹중
  const 집기가능 = detail !== null && (detail.status === "ALLOCATED" || detail.status === "PICKING");

  if (documentId === null) {
    return (
      <>
        <PageTitle title="피킹리스트" path="홈 > 출고관리 > 피킹리스트" />
        <p>출고 문서를 먼저 선택하세요.</p>
      </>
    );
  }

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
        <button className="btn" onClick={() => navigate("/outbounds")}>목록</button>
      </div>

      {/* 피킹리스트 */}
      <GridTitle title="피킹리스트 (로케이션순)" desc={"총 " + pickings.length + "건"}>
        <button className="btn" onClick={전체집음} disabled={loading || !집기가능}>전체 집음</button>{" "}
        <button className="btn primary" onClick={출고확정} disabled={loading || !출고확정가능}>출고확정</button>
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