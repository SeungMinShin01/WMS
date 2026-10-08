import { useEffect, useState } from "react";
import axios from "axios";
import PageTitle from "../../Layout/PageTitle";
import GridTitle from "../../Layout/GridTitle";

const EMPTY = {
  productId: null,
  tenantId: "", // [추가] 신규일 때 화주를 직접 고르게 비워 둔다
  productCode: "SKU-",
  productName: "",
  spec: "",
  unit: "",
  minShipDays: 0, // [추가] 출고허용 잔여일
};

const fmtDate = (v) => (v ? v.slice(0, 10) : "");

// 01 품목 — 담당: 기준정보 담당
export default function ProductPage(props) {
  const [products, setProducts] = useState([]);
  const [filtered, setFiltered] = useState([]);
  const [form, setForm] = useState(null);
  const [tenants, setTenants] = useState([]); // [추가] 화주 select 옵션

  const fetchProducts = async () => {
    try {
      const res = await axios.get("/wms/products");
      setProducts(res.data);
      setFiltered(res.data);
    } catch (err) {
      console.error("품목 목록 조회 실패", err);
    }
  };

  // [추가] 화주 목록 조회 (GET /wms/tenants)
  const fetchTenants = async () => {
    try {
      const res = await axios.get("/wms/tenants");
      setTenants(res.data);
    } catch (err) {
      console.error("화주 목록 조회 실패", err);
    }
  };

  const fetchDetail = async (productId) => {
    try {
      const res = await axios.get("/wms/product/detail", {
        params: { productid: productId },
      });
      setForm(res.data);
    } catch (err) {
      // [변경] 없는 품목(404)이면 서버 문구를 보여준다
      const msg = typeof err.response?.data === "string" ? err.response.data : null;
      alert(msg || "품목 정보를 불러오지 못했습니다.");
    }
  };

  useEffect(() => {
    fetchProducts();
    fetchTenants(); // [추가]
  }, []);

  const 조회 = (event) => {
    event.preventDefault();
    const data = new FormData(event.target);
    const tenantId = data.get("tenantId"); // [추가]
    const code = data.get("productCode").trim();
    const name = data.get("productName").trim();
    const unit = data.get("unit");

    setFiltered(
      products.filter(
        (p) =>
          (tenantId === "" || String(p.tenantId) === tenantId) && // [추가]
          (code === "" || code === "SKU-" || p.productCode?.includes(code)) &&
          (name === "" || p.productName?.includes(name)) &&
          (unit === "" || p.unit === unit)
      )
    );
  };

  const 신규 = () => setForm({ ...EMPTY });

  const 입력 = (e) => setForm({ ...form, [e.target.name]: e.target.value });

  const 저장 = async () => {
    if (!form.tenantId) {
      alert("화주를 선택하세요.");
      return;
    }
    if (!form.productCode.trim() || !form.productName.trim() || !form.spec.trim()) {
      alert("품목코드, 품목명, 규격은 필수입니다.");
      return;
    }
    const isEdit = form.productId != null;
    // [추가] 화주, 출고허용 잔여일은 문자열로 들어오므로 숫자로 바꿔 보낸다
    const body = {
      ...form,
      tenantId: Number(form.tenantId),
      minShipDays: form.minShipDays === "" ? 0 : Number(form.minShipDays),
    };

    try {
      const res = isEdit
        ? await axios.put("/wms/product", body)
        : await axios.post("/wms/product", body);
      if (res.data) {
        // 등록은 새 번호, 수정은 true
        alert(isEdit ? "수정되었습니다." : "등록되었습니다.");
        setForm(null);
        fetchProducts();
      } else {
        alert("저장에 실패했습니다.");
      }
    } catch (err) {
      // 서버가 보낸 문구(400, 404, 409)가 있으면 그대로 보여준다
      const msg = typeof err.response?.data === "string" ? err.response.data : null;
      alert(msg || "저장 중 오류가 발생했습니다.");
    }
  };

  return (
    <>
      <PageTitle title="품목 관리" path="홈 > 기준정보 > 품목" />

      <form className="search" onSubmit={조회}>
        {/* [추가] 화주 조회조건 */}
        <label>화주</label>
        <select name="tenantId">
          <option value="">전체</option>
          {tenants.map((t) => (
            <option key={t.tenantId} value={t.tenantId}>
              {t.tenantName}
            </option>
          ))}
        </select>
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

      <GridTitle title="품목 목록" desc={`총 ${filtered.length}건`}>
        <button className="btn" onClick={신규}>신규</button>
      </GridTitle>
      <table className="grid">
        <thead>
          <tr>
            <th>화주</th> {/* [추가] */}
            <th>품목코드</th>
            <th>품목명</th>
            <th>규격 (단위×입수)</th>
            <th>단위</th>
            <th>출고허용 잔여일</th>
            <th>사용여부</th>
            <th>수정일</th>
          </tr>
        </thead>
        <tbody>
          {filtered.map((p) => (
            <tr key={p.productId} onClick={() => fetchDetail(p.productId)}>
              <td>{p.tenantName}</td> {/* [추가] */}
              <td>{p.productCode}</td>
              <td>{p.productName}</td>
              <td>{p.spec}</td>
              <td>{p.unit}</td>
              <td>{p.minShipDays}</td> {/* [변경] "-" → 실제 값 */}
              <td>-</td>
              <td>{fmtDate(p.updatedAt)}</td>
            </tr>
          ))}
        </tbody>
      </table>

      {form == null ? (
        <p className="empty">목록에서 품목을 클릭하면 상세가 나옵니다.</p>
      ) : (
        <>
          <GridTitle title={form.productId == null ? "품목 등록" : "품목 상세"}>
            <button className="btn primary" onClick={저장}>저장</button>
          </GridTitle>
          <div className="panel">
            {/* [추가] 화주: 신규는 선택, 수정은 바꿀 수 없다(표시만) */}
            <label>화주</label>
            <select
              name="tenantId"
              value={String(form.tenantId ?? "")}
              onChange={입력}
              disabled={form.productId != null}
            >
              <option value="">선택</option>
              {tenants.map((t) => (
                <option key={t.tenantId} value={String(t.tenantId)}>
                  {t.tenantName}
                </option>
              ))}
            </select>
            <label>품목코드</label>
            <input name="productCode" value={form.productCode ?? ""} onChange={입력} />
            <label>품목명</label>
            <input name="productName" value={form.productName ?? ""} onChange={입력} />
            <label>규격</label>
            <input name="spec" value={form.spec ?? ""} onChange={입력} />
            <label>단위</label>
            <input name="unit" value={form.unit ?? ""} onChange={입력} />
            {/* [추가] 출고허용 잔여일 */}
            <label>출고허용 잔여일</label>
            <input
              type="number"
              min="0"
              name="minShipDays"
              value={form.minShipDays ?? 0}
              onChange={입력}
            />
          </div>
        </>
      )}
    </>
  );
}