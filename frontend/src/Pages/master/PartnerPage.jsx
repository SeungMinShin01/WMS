import { useEffect, useState } from "react";
import axios from "axios";
import PageTitle from "../../Layout/PageTitle";
import GridTitle from "../../Layout/GridTitle";

const TYPE_LABEL = { SUPPLIER: "공급사", CUSTOMER: "납품처" };

const EMPTY = {
  partnerId: null,
  tenantId: "", // [추가] 신규일 때 화주를 직접 고르게 비워 둔다
  partnerCode: "PT-",
  partnerName: "",
  partnerType: "SUPPLIER",
  contact: "",
  address: "",
};

const fmtDate = (v) => (v ? v.slice(0, 10) : "");

// 01-2 거래처 — 담당: 기준정보 담당 (공급사 SUPPLIER / 납품처 CUSTOMER)
export default function PartnerPage(props) {
  const [partners, setPartners] = useState([]);
  const [filtered, setFiltered] = useState([]);
  const [form, setForm] = useState(null);
  const [tenants, setTenants] = useState([]); // [추가] 화주 select 옵션

  const fetchPartners = async () => {
    try {
      const res = await axios.get("/wms/partners");
      setPartners(res.data);
      setFiltered(res.data);
    } catch (err) {
      console.error("거래처 목록 조회 실패", err);
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

  const fetchDetail = async (partnerId) => {
    try {
      const res = await axios.get("/wms/partner/detail", {
        params: { partnerid: partnerId },
      });
      setForm(res.data);
    } catch (err) {
      // [변경] 없는 거래처(404)면 서버 문구를 보여준다
      const msg = typeof err.response?.data === "string" ? err.response.data : null;
      alert(msg || "거래처 정보를 불러오지 못했습니다.");
    }
  };

  useEffect(() => {
    fetchPartners();
    fetchTenants(); // [추가]
  }, []);

  const 조회 = (event) => {
    event.preventDefault();
    const data = new FormData(event.target);
    const tenantId = data.get("tenantId"); // [추가]
    const code = data.get("partnerCode").trim();
    const name = data.get("partnerName").trim();
    const type = data.get("partnerType");

    setFiltered(
      partners.filter(
        (p) =>
          (tenantId === "" || String(p.tenantId) === tenantId) && // [추가]
          (code === "" || code === "PT-" || p.partnerCode?.includes(code)) &&
          (name === "" || p.partnerName?.includes(name)) &&
          (type === "" || p.partnerType === type)
      )
    );
  };

  const 신규 = () => setForm({ ...EMPTY });

  const 입력 = (e) => setForm({ ...form, [e.target.name]: e.target.value });

  // [변경] 품목 코드가 섞여 있던 것을 거래처 기준으로 교체, 화주 필수 추가
  const 저장 = async () => {
    if (!form.tenantId) {
      alert("화주를 선택하세요.");
      return;
    }
    if (!form.partnerCode.trim() || !form.partnerName.trim()) {
      alert("거래처코드와 거래처명은 필수입니다.");
      return;
    }
    const isEdit = form.partnerId != null;
    const body = { ...form, tenantId: Number(form.tenantId) }; // 문자열 → 숫자

    try {
      const res = isEdit
        ? await axios.put("/wms/partner", body)
        : await axios.post("/wms/partner", body);
      if (res.data) {
        // 등록은 새 번호, 수정은 true
        alert(isEdit ? "수정되었습니다." : "등록되었습니다.");
        setForm(null);
        fetchPartners();
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
      <PageTitle title="거래처 관리" path="홈 > 기준정보 > 거래처" />

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
        <label>거래처코드</label>
        <input type="text" name="partnerCode" defaultValue="PT-" />
        <label>거래처명</label>
        <input type="text" name="partnerName" />
        <label>구분</label>
        <select name="partnerType">
          <option value="">전체</option>
          <option value="SUPPLIER">공급사</option>
          <option value="CUSTOMER">납품처</option>
        </select>
        <input type="submit" className="btn primary" value="조회" />
      </form>

      <GridTitle title="거래처 목록" desc={`총 ${filtered.length}건`}>
        <button className="btn" onClick={신규}>신규</button>
      </GridTitle>
      <table className="grid">
        <thead>
          <tr>
            <th>No</th>
            <th>화주</th> {/* [추가] */}
            <th>거래처코드</th>
            <th>거래처명</th>
            <th>구분</th>
            <th>연락처</th>
            <th>등록일</th>
          </tr>
        </thead>
        <tbody>
          {filtered.map((p, i) => (
            <tr key={p.partnerId} onClick={() => fetchDetail(p.partnerId)}>
              <td>{i + 1}</td>
              <td>{p.tenantName}</td> {/* [추가] */}
              <td>{p.partnerCode}</td>
              <td>{p.partnerName}</td>
              <td>{TYPE_LABEL[p.partnerType]}</td>
              <td>{p.contact}</td>
              <td>{fmtDate(p.createdAt)}</td>
            </tr>
          ))}
        </tbody>
      </table>

      {form == null ? (
        <p className="empty">목록에서 거래처를 클릭하면 상세가 나옵니다.</p>
      ) : (
        <>
          <GridTitle title={form.partnerId == null ? "거래처 등록" : "거래처 상세"}>
            <button className="btn primary" onClick={저장}>저장</button>
          </GridTitle>
          <div className="panel">
            {/* [추가] 화주: 신규는 선택, 수정은 바꿀 수 없다(표시만) */}
            <label>화주</label>
            <select
              name="tenantId"
              value={String(form.tenantId ?? "")}
              onChange={입력}
              disabled={form.partnerId != null}
            >
              <option value="">선택</option>
              {tenants.map((t) => (
                <option key={t.tenantId} value={String(t.tenantId)}>
                  {t.tenantName}
                </option>
              ))}
            </select>
            <label>거래처코드</label>
            <input name="partnerCode" value={form.partnerCode ?? ""} onChange={입력} />
            <label>거래처명</label>
            <input name="partnerName" value={form.partnerName ?? ""} onChange={입력} />
            <label>구분</label>
            <select name="partnerType" value={form.partnerType ?? "SUPPLIER"} onChange={입력}>
              <option value="SUPPLIER">공급사</option>
              <option value="CUSTOMER">납품처</option>
            </select>
            <label>연락처</label>
            <input name="contact" value={form.contact ?? ""} onChange={입력} />
            <label>주소</label>
            <input name="address" value={form.address ?? ""} onChange={입력} />
          </div>
        </>
      )}
    </>
  );
}