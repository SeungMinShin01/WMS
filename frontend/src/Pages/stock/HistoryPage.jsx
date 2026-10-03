import PageTitle from "../../Layout/PageTitle";
import GridTitle from "../../Layout/GridTitle";
import { useEffect, useState } from "react";
import axios from "axios";

const 변동타입명 = { INBOUND: "입고", ALLOCATE: "선점", OUTBOUND: "출고" };
const 증감 = (n) => (n === 0 ? "—" : n > 0 ? `+${n.toLocaleString()}` : n.toLocaleString());

// 08 입출고이력 — 담당: 조현우
export default function HistoryPage(props) {
  const [histories, setHistories] = useState([]);
  const [조건, set조건] = useState({ from: "", to: "", product: "", locationCode: "", txType: "" });

  const 이력조회 = async () => {
    const response = await axios.get("/wms/stocks/history");
    setHistories(response.data);
  };

  const 조회 = (event) => {
    event.preventDefault();
    const form = new FormData(event.target);
    set조건({
      from: form.get("from"),
      to: form.get("to"),
      product: form.get("product").trim(),
      locationCode: form.get("locationCode").trim(),
      txType: form.get("txType"),
    });
    이력조회();
  };

  useEffect(() => {
    이력조회();
  }, []);

  const 보여줄목록 = histories.filter((h) => {
    const 날짜 = h.occurredAt ? h.occurredAt.substring(0, 10) : "";
    return (
      (조건.from === "" || 날짜 >= 조건.from) &&
      (조건.to === "" || 날짜 <= 조건.to) &&
      (조건.product === "" || h.productCode.includes(조건.product) || h.productName.includes(조건.product)) &&
      (조건.locationCode === "" || h.locationCode.includes(조건.locationCode)) &&
      (조건.txType === "" || h.txType === 조건.txType)
    );
  });

  return (
    <>
      <PageTitle title="입출고 이력" path="홈 > 재고관리 > 입출고이력" />

      <form className="search" onSubmit={조회}>
        <label>기간</label>
        <input type="date" name="from" /> ~ <input type="date" name="to" />
        <label>품목</label>
        <input type="text" name="product" placeholder="코드 또는 이름" />
        <label>로케이션</label>
        <input type="text" name="locationCode" />
        <label>변동타입</label>
        <select name="txType" defaultValue="">
          <option value="">전체</option>
          <option value="INBOUND">입고</option>
          <option value="ALLOCATE">선점</option>
          <option value="OUTBOUND">출고</option>
        </select>
        <input type="submit" className="btn primary" value="조회" />
      </form>

      <GridTitle title="재고 이력" desc={`총 ${보여줄목록.length}건 · 추가만 되는 기록 (수정·삭제 불가)`} />
      <table className="grid">
        <thead>
          <tr>
            <th>No</th>
            <th>발생일시</th>
            <th>변동타입</th>
            <th>품목코드</th>
            <th>품목명</th>
            <th>로케이션</th>
            <th>LOT</th>
            <th>소비기한</th>
            <th>실물 증감</th>
            <th>선점 증감</th>
            <th>변경 후 실물</th>
            <th>변경 후 선점</th>
            <th>근거 문서</th>
            <th>사유</th>
            <th>처리자</th>
          </tr>
        </thead>
        <tbody>
          {보여줄목록.map((h, index) => (
            <tr key={h.historyKey}>
              <td>{index + 1}</td>
              <td>{h.occurredAt ?? "—"}</td>
              <td>{변동타입명[h.txType]}</td>
              <td>{h.productCode}</td>
              <td className="left">{h.productName}</td>
              <td>{h.locationCode}</td>
              <td>{h.lotCode}</td>
              <td>{h.expiryDate ?? "—"}</td>
              <td className={h.qtyChange < 0 ? "num red" : "num"}>{증감(h.qtyChange)}</td>
              <td className={h.allocatedChange < 0 ? "num red" : "num"}>{증감(h.allocatedChange)}</td>
              <td className="num">{h.afterQty.toLocaleString()}</td>
              <td className="num">{h.afterAllocated.toLocaleString()}</td>
              <td>{h.documentNo}</td>
              <td className="left">{h.remark ?? ""}</td>
              <td>{h.handler}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </>
  );
}