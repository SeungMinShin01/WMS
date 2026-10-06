// [변경] useEffect, useState, axios import 추가 (초안엔 PageTitle, GridTitle만 있었음)
import { useEffect, useState } from "react";
import axios from "axios";
import PageTitle from "../../Layout/PageTitle";
import GridTitle from "../../Layout/GridTitle";

// [추가] 신규 버튼을 눌렀을 때 상세 패널에 들어갈 빈 값
const EMPTY = {
  productId: null,
  productCode: "SKU-",
  productName: "",
  spec: "",
  unit: "",
};

// [추가] "2026-10-05T12:34:56" → "2026-10-05" (수정일 표시용)
const fmtDate = (v) => (v ? v.slice(0, 10) : "");

// 01 품목 — 담당: 기준정보 담당
export default function ProductPage(props) {
  // [추가] 화면 데이터를 담는 상태 3개
  const [products, setProducts] = useState([]); // 서버에서 받은 전체 목록
  const [filtered, setFiltered] = useState([]); // 조회조건으로 거른, 화면에 보이는 목록
  const [form, setForm] = useState(null); // 하단 상세 (null이면 안내 문구)

  // [추가] 목록 조회 (GET /wms/products)
  const fetchProducts = async () => {
    try {
      const res = await axios.get("/wms/products");
      setProducts(res.data);
      setFiltered(res.data);
    } catch (err) {
      console.error("품목 목록 조회 실패", err);
    }
  };

  // [추가] 상세 조회 (GET /wms/product/detail?productid=)
  const fetchDetail = async (productId) => {
    try {
      const res = await axios.get("/wms/product/detail", {
        params: { productid: productId },
      });
      if (!res.data) {
        alert("품목 정보를 찾을 수 없습니다.");
        return;
      }
      setForm(res.data);
    } catch (err) {
      console.error("품목 상세 조회 실패", err);
    }
  };

  // [추가] 화면이 처음 열릴 때 목록을 한 번 불러온다
  useEffect(() => {
    fetchProducts();
  }, []);

  // [변경] 초안은 preventDefault()만 했음 → 서버가 조회조건을 안 받아서 프론트에서 거른다
  // (사용여부 isActive는 DTO에 값이 없어 아직 걸러지지 않음)
  const 조회 = (event) => {
    event.preventDefault();
    const data = new FormData(event.target);
    const code = data.get("productCode").trim();
    const name = data.get("productName").trim();
    const unit = data.get("unit");

    setFiltered(
      products.filter(
        (p) =>
          (code === "" || code === "SKU-" || p.productCode?.includes(code)) &&
          (name === "" || p.productName?.includes(name)) &&
          (unit === "" || p.unit === unit)
      )
    );
  };

  // [추가] 신규 버튼: 빈 폼을 하단 상세에 띄운다
  const 신규 = () => setForm({ ...EMPTY });

  // [추가] 상세 패널 입력값이 바뀔 때 form 상태에 반영
  const 입력 = (e) => setForm({ ...form, [e.target.name]: e.target.value });

  // [추가] 저장: productId가 없으면 등록(POST), 있으면 수정(PUT)
  const 저장 = async () => {
    if (!form.productCode.trim() || !form.productName.trim()) {
      alert("품목코드와 품목명은 필수입니다.");
      return;
    }
    const isEdit = form.productId != null;
    try {
      const res = isEdit
        ? await axios.put("/wms/product", form)
        : await axios.post("/wms/product", form);
      if (res.data === true) {
        alert(isEdit ? "수정되었습니다." : "등록되었습니다.");
        setForm(null);
        fetchProducts();
      } else {
        alert("저장에 실패했습니다.");
      }
    } catch (err) {
      console.error("품목 저장 실패", err);
      alert("저장 중 오류가 발생했습니다.");
    }
  };

  return (
    <>
      {/* [초안 그대로] */}
      <PageTitle title="품목 관리" path="홈 > 기준정보 > 품목" />

      {/* [초안 그대로] 조회조건 form (name, 옵션, 조회 버튼 전부 동일) */}
      <form className="search" onSubmit={조회}>
        <label>품목코드</label>
        <input type="text" name="productCode" defaultValue="SKU-" />
        <label>품목명</label>
        <input type="text" name="productName" placeholder="품목명 입력" />
        <label>단위</label>
        <select name="unit">
          <option value="">전체</option>
          <option value="BOX">BOX</option>
        </select>
        <label>사용여부</label>
        <select name="isActive">
          <option value="">전체</option>
          <option value="Y">사용</option>
          <option value="N">미사용</option>
        </select>
        <input type="submit" className="btn primary" value="조회" />
      </form>

      {/* [변경] desc "총 0건" → 실제 건수 / 신규 버튼에 onClick 추가 */}
      <GridTitle title="품목 목록" desc={`총 ${filtered.length}건`}>
        <button className="btn" onClick={신규}>신규</button>
      </GridTitle>
      <table className="grid">
        {/* [초안 그대로] 표 머리 */}
        <thead>
          <tr>
            <th>품목코드</th>
            <th>품목명</th>
            <th>규격 (단위×입수)</th>
            <th>단위</th>
            <th>출고허용 잔여일</th>
            <th>사용여부</th>
            <th>수정일</th>
          </tr>
        </thead>
        {/* [변경] 초안은 비어 있던 tbody → 서버 데이터를 .map으로 출력 */}
        <tbody>
          {filtered.map((p) => (
            // 행 클릭 시 상세 조회
            <tr key={p.productId} onClick={() => fetchDetail(p.productId)}>
              <td>{p.productCode}</td>
              <td>{p.productName}</td>
              <td>{p.spec}</td>
              <td>{p.unit}</td>
              {/* 출고허용 잔여일, 사용여부는 DTO에 값이 없어 "-" 표시 */}
              <td>-</td>
              <td>-</td>
              <td>{fmtDate(p.updatedAt)}</td>
            </tr>
          ))}
        </tbody>
      </table>

      {/* [추가] 하단 상세 영역 (입고예정 화면처럼 선택 전에는 안내 문구만) */}
      {form == null ? (
        <p className="empty">목록에서 품목을 클릭하면 상세가 나옵니다.</p>
      ) : (
        <>
          <GridTitle title={form.productId == null ? "품목 등록" : "품목 상세"}>
            <button className="btn primary" onClick={저장}>저장</button>
          </GridTitle>
          <div className="panel">
            <label>품목코드</label>
            <input name="productCode" value={form.productCode ?? ""} onChange={입력} />
            <label>품목명</label>
            <input name="productName" value={form.productName ?? ""} onChange={입력} />
            <label>규격</label>
            <input name="spec" value={form.spec ?? ""} onChange={입력} />
            <label>단위</label>
            <input name="unit" value={form.unit ?? ""} onChange={입력} />
          </div>
        </>
      )}
    </>
  );
}