import { useEffect, useState } from "react";
import axios from "axios";
import GridTitle from "../../Layout/GridTitle";

// 품목 줄 1개의 빈 값
const EMPTY_ITEM = { productId: "", manufactureDate: "", expiryDate: "", expectedQty: "" };

// 입고문서 신규 등록 (ED-60) — 문서 헤더 POST → 받은 documentId로 품목을 1줄씩 POST
export default function InboundCreateForm(props) {
  const [tenants, setTenants] = useState([]); // 화주 선택지
  const [suppliers, setSuppliers] = useState([]); // 고른 화주의 공급사
  const [products, setProducts] = useState([]); // 고른 화주의 품목
  const [tenantId, setTenantId] = useState("");
  const [partnerId, setPartnerId] = useState("");
  const [expectedDate, setExpectedDate] = useState("");
  const [items, setItems] = useState([{ ...EMPTY_ITEM }]);
  const [saving, setSaving] = useState(false); // 등록 중 버튼 중복 클릭 막기

  // 화면 열릴 때 화주 목록
  const getTenants = async () => {
    const response = await axios.get("/wms/inbounds/tenants");
    setTenants(response.data);
  };

  useEffect(() => {
    getTenants();
  }, []);

  // 화주를 바꾸면 그 화주의 공급사·품목을 다시 받고, 고른 값은 초기화
  const changeTenant = async (value) => {
    setTenantId(value);
    setPartnerId("");
    setItems([{ ...EMPTY_ITEM }]);
    if (value === "") {
      setSuppliers([]);
      setProducts([]);
      return;
    }
    const supplierRes = await axios.get(`/wms/inbounds/tenants/${value}/suppliers`);
    setSuppliers(supplierRes.data);
    const productRes = await axios.get(`/wms/inbounds/tenants/${value}/products`);
    setProducts(productRes.data);
  };

  // 품목 줄 index번의 field 값만 바꾸기
  const changeItem = (index, field, value) => {
    const next = [...items];
    next[index] = { ...next[index], [field]: value };
    setItems(next);
  };

  const addItem = () => setItems([...items, { ...EMPTY_ITEM }]);
  const removeItem = (index) => setItems(items.filter((item, i) => i !== index));

  // 보내기 전 화면에서 먼저 검사 (서버도 같은 검사를 한 번 더 함)
  const validate = () => {
    if (tenantId === "" || partnerId === "" || expectedDate === "")
      return "화주, 공급사, 입고예정일을 입력하세요.";
    for (const item of items) {
      if (
        item.productId === "" ||
        item.manufactureDate === "" ||
        item.expiryDate === "" ||
        !(Number(item.expectedQty) > 0)
      )
        return "모든 품목 줄의 품목, 제조일자, 소비기한, 수량(1 이상)을 입력하세요.";
      if (item.manufactureDate > item.expiryDate)
        return "제조일자가 소비기한보다 늦은 줄이 있습니다.";
    }
    // 같은 품목 + 같은 제조일자 = 같은 LOT → 두 줄이면 서버에서 409 → 미리 막기
    const keys = new Set();
    for (const item of items) {
      const key = item.productId + "-" + item.manufactureDate;
      if (keys.has(key)) return "같은 품목·제조일자가 두 줄 있습니다. 수량을 합쳐 한 줄로 입력하세요.";
      keys.add(key);
    }
    return null;
  };

  const save = async () => {
    const message = validate();
    if (message !== null) {
      alert(message);
      return;
    }
    setSaving(true);

    // 1) 문서 헤더
    let documentId;
    try {
      const response = await axios.post("/wms/inbounds", {
        tenantId: Number(tenantId),
        partnerId: Number(partnerId),
        expectedDate: expectedDate,
      });
      documentId = response.data;
    } catch (error) {
      alert(`문서 등록 실패: ${error.response?.data ?? "오류"}`);
      setSaving(false);
      return;
    }

    // 2) 품목을 1줄씩
    const failMessages = [];
    for (const item of items) {
      try {
        await axios.post(`/wms/inbounds/${documentId}/items`, {
          productId: Number(item.productId),
          manufactureDate: item.manufactureDate,
          expiryDate: item.expiryDate,
          expectedQty: Number(item.expectedQty),
        });
      } catch (error) {
        const product = products.find((p) => p.id === Number(item.productId));
        failMessages.push(
          `${product ? product.name : "품목"}: ${error.response?.data ?? "오류"}`,
        );
      }
    }
    setSaving(false);
    alert(
      `입고문서 등록 완료 (품목 ${items.length - failMessages.length}건)` +
        (failMessages.length > 0 ? `\n실패\n${failMessages.join("\n")}` : ""),
    );
    props.onDone();
  };

  return (
    <>
      <GridTitle title="입고문서 신규 등록" desc="화주를 먼저 고르면 그 화주의 공급사·품목만 나옵니다">
        <button className="btn" onClick={props.onCancel}>
          취소
        </button>
        <button className="btn primary" onClick={save} disabled={saving}>
          {saving ? "등록 중..." : "등록"}
        </button>
      </GridTitle>

      <table className="form head">
        <tbody>
          <tr>
            <th>화주</th>
            <td>
              <select value={tenantId} onChange={(e) => changeTenant(e.target.value)}>
                <option value="">선택</option>
                {tenants.map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.name}
                  </option>
                ))}
              </select>
            </td>
            <th>공급사</th>
            <td>
              <select
                value={partnerId}
                onChange={(e) => setPartnerId(e.target.value)}
                disabled={tenantId === ""}
              >
                <option value="">선택</option>
                {suppliers.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.name}
                  </option>
                ))}
              </select>
            </td>
            <th>입고예정일</th>
            <td>
              <input
                type="date"
                value={expectedDate}
                onChange={(e) => setExpectedDate(e.target.value)}
              />
            </td>
          </tr>
        </tbody>
      </table>

      <GridTitle title="입고 품목" desc={`${items.length}줄`}>
        <button className="btn" onClick={addItem} disabled={tenantId === ""}>
          줄 추가
        </button>
      </GridTitle>
      <table className="grid">
        <thead>
          <tr>
            <th>No</th>
            <th>품목</th>
            <th>제조일자</th>
            <th>소비기한</th>
            <th>예정수량</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {items.map((item, index) => (
            <tr key={index}>
              <td>{index + 1}</td>
              <td>
                <select
                  value={item.productId}
                  onChange={(e) => changeItem(index, "productId", e.target.value)}
                  disabled={tenantId === ""}
                >
                  <option value="">선택</option>
                  {products.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.code} {p.name}
                    </option>
                  ))}
                </select>
              </td>
              <td>
                <input
                  type="date"
                  value={item.manufactureDate}
                  onChange={(e) => changeItem(index, "manufactureDate", e.target.value)}
                />
              </td>
              <td>
                <input
                  type="date"
                  value={item.expiryDate}
                  onChange={(e) => changeItem(index, "expiryDate", e.target.value)}
                />
              </td>
              <td>
                <input
                  type="number"
                  min="1"
                  style={{ width: "80px" }}
                  value={item.expectedQty}
                  onChange={(e) => changeItem(index, "expectedQty", e.target.value)}
                />
              </td>
              <td>
                {items.length > 1 && (
                  <button className="btn" onClick={() => removeItem(index)}>
                    삭제
                  </button>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <p className="hint">
        ※ LOT 번호는 LOT-제조일자-공급사코드-순번으로 자동 생성됩니다. 같은 품목·제조일자·공급사면
        기존 LOT를 재사용하고, 소비기한이 다르면 등록이 거부됩니다. 문서번호는 화주코드-IN-날짜-순번으로
        자동 생성됩니다.
      </p>
    </>
  );
}