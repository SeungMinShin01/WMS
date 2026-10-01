import { useEffect, useState } from "react";
import axios from "axios";
import PageTitle from "../../Layout/PageTitle";
import GridTitle from "../../Layout/GridTitle";

// 재고 상태: 임박(30일 이하) > 부족(가용 0) > 정상
const STOCK_STATUS_NAME = { NORMAL: "정상", SHORT: "부족", NEAR: "임박" };
const getStockStatus = (s) => {
  if (s.remainingDays !== null && s.remainingDays <= 30) return "NEAR";
  if (s.availableQty <= 0) return "SHORT";
  return "NORMAL";
};

// 07 재고현황 — 담당: 조현우
export default function StockPage(props) {
  const [stocks, setStocks] = useState([]); // ED-21 재고 전체 (FEFO 정렬)
  const [filter, setFilter] = useState({
    productCode: "",
    productName: "",
    zone: "",
    status: "",
  });

  const getList = async () => {
    const response = await axios.get("/wms/stocks");
    setStocks(response.data);
  };

  // 조회 버튼 - 입력한 조건 저장 + 목록 다시 불러오기
  const handleSearch = (event) => {
    event.preventDefault();
    const form = new FormData(event.target);
    setFilter({
      productCode: form.get("productCode").trim(),
      productName: form.get("productName").trim(),
      zone: form.get("zone"),
      status: form.get("status"),
    });
    getList();
  };

  useEffect(() => {
    getList();
  }, []);

  // 조회 조건에 맞는 재고만 (구역 = 로케이션 코드 첫 글자)
  const visibleList = stocks.filter(
    (s) =>
      (filter.productCode === "" ||
        s.productCode.includes(filter.productCode)) &&
      (filter.productName === "" ||
        s.productName.includes(filter.productName)) &&
      (filter.zone === "" || s.locationCode.startsWith(filter.zone)) &&
      (filter.status === "" || getStockStatus(s) === filter.status),
  );

  return (
    <>
      <PageTitle title="재고 현황" path="홈 > 재고관리 > 재고현황" />

      <form className="search" onSubmit={handleSearch}>
        <label>품목코드</label>
        <input type="text" name="productCode" defaultValue="SKU-" />
        <label>품목명</label>
        <input type="text" name="productName" placeholder="품목명 입력" />
        <label>구역</label>
        <select name="zone" defaultValue="">
          <option value="">전체</option>
          <option value="A">A</option>
          <option value="B">B</option>
          <option value="C">C</option>
        </select>
        <label>재고상태</label>
        <select name="status" defaultValue="">
          <option value="">전체</option>
          <option value="NORMAL">정상</option>
          <option value="SHORT">부족</option>
          <option value="NEAR">임박(30일 이하)</option>
        </select>
        <input type="submit" className="btn primary" value="조회" />
      </form>

      <GridTitle
        title="품목별 재고"
        desc={`총 ${visibleList.length}건`}
      ></GridTitle>
      <table className="grid">
        <thead>
          <tr>
            <th>No</th>
            <th>품목코드</th>
            <th>품목명</th>
            <th>규격</th>
            <th>단위</th>
            <th>LOT</th>
            <th>실물</th>
            <th>선점</th>
            <th>가용</th>
            <th>로케이션</th>
            <th>최단 소비기한</th>
            <th>남은일수</th>
            <th>상태</th>
          </tr>
        </thead>
        <tbody>
          {visibleList.map((s, index) => (
            <tr key={s.stockId}>
              <td>{index + 1}</td>
              <td>{s.productCode}</td>
              <td className="left">{s.productName}</td>
              <td>{s.spec ?? "—"}</td>
              <td>{s.unit}</td>
              <td>{s.lotCode}</td>
              <td className="num">{s.qty.toLocaleString()}</td>
              <td className="num">{s.allocatedQty.toLocaleString()}</td>
              <td className="num">{s.availableQty.toLocaleString()}</td>
              <td>{s.locationCode}</td>
              <td>{s.expiryDate ?? "—"}</td>
              <td className="num">
                {s.remainingDays === null ? "—" : `${s.remainingDays}일`}
              </td>
              <td>{STOCK_STATUS_NAME[getStockStatus(s)]}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </>
  );
}
