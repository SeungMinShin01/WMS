// [변경] useEffect, useState, axios import 추가 (초안엔 PageTitle, GridTitle만 있었음)
import { useEffect, useState } from "react";
import axios from "axios";
import PageTitle from "../../Layout/PageTitle";
import GridTitle from "../../Layout/GridTitle";

// [추가] 구분 코드를 화면 표시명으로 바꾸는 표
const TYPE_LABEL = { SUPPLIER: "공급사", CUSTOMER: "납품처" };

// [추가] 신규 버튼을 눌렀을 때 상세 패널에 들어갈 빈 값
const EMPTY = {
  partnerId: null,
  partnerCode: "PT-",
  partnerName: "",
  partnerType: "SUPPLIER",
  contact: "",
  address: "",
};

// [추가] "2026-10-05T12:34:56" → "2026-10-05" (등록일 표시용)
const fmtDate = (v) => (v ? v.slice(0, 10) : "");

// 01-2 거래처 — 담당: 기준정보 담당 (공급사 SUPPLIER / 납품처 CUSTOMER)
export default function PartnerPage(props) {
  // [추가] 화면 데이터를 담는 상태 3개
  const [partners, setPartners] = useState([]); // 서버에서 받은 전체 목록
  const [filtered, setFiltered] = useState([]); // 조회조건으로 거른, 화면에 보이는 목록
  const [form, setForm] = useState(null); // 하단 상세 (null이면 안내 문구)

  // [추가] 목록 조회 (GET /wms/partners)
  const fetchPartners = async () => {
    try {
      const res = await axios.get("/wms/partners");
      setPartners(res.data);
      setFiltered(res.data);
    } catch (err) {
      console.error("거래처 목록 조회 실패", err);
    }
  };

  // [추가] 상세 조회 (GET /wms/partner/detail?partnerid=)
  const fetchDetail = async (partnerId) => {
    try {
      const res = await axios.get("/wms/partner/detail", {
        params: { partnerid: partnerId },
      });
      if (!res.data) {
        alert("거래처 정보를 찾을 수 없습니다.");
        return;
      }
      setForm(res.data);
    } catch (err) {
      console.error("거래처 상세 조회 실패", err);
    }
  };

  // [추가] 화면이 처음 열릴 때 목록을 한 번 불러온다
  useEffect(() => {
    fetchPartners();
  }, []);

  // [변경] 초안은 preventDefault()만 했음 → 서버가 조회조건을 안 받아서 프론트에서 거른다
  const 조회 = (event) => {
    event.preventDefault();
    const data = new FormData(event.target);
    const code = data.get("partnerCode").trim();
    const name = data.get("partnerName").trim();
    const type = data.get("partnerType");

    setFiltered(
      partners.filter(
        (p) =>
          (code === "" || code === "PT-" || p.partnerCode?.includes(code)) &&
          (name === "" || p.partnerName?.includes(name)) &&
          (type === "" || p.partnerType === type)
      )
    );
  };

  // [추가] 신규 버튼: 빈 폼을 하단 상세에 띄운다
  const 신규 = () => setForm({ ...EMPTY });

  // [추가] 상세 패널 입력값이 바뀔 때 form 상태에 반영
  const 입력 = (e) => setForm({ ...form, [e.target.name]: e.target.value });

  // [추가] 저장: partnerId가 없으면 등록(POST), 있으면 수정(PUT)
  const 저장 = async () => {
    if (!form.partnerCode.trim() || !form.partnerName.trim()) {
      alert("거래처코드와 거래처명은 필수입니다.");
      return;
    }
    const isEdit = form.partnerId != null;
    try {
      const res = isEdit
        ? await axios.put("/wms/partner", form)
        : await axios.post("/wms/partner", form);
      if (res.data) {          // === true 제외
        alert(isEdit ? "수정되었습니다." : "등록되었습니다.");
        setForm(null);
        fetchPartners();
      } else {
        alert("저장에 실패했습니다.");
      }
    } catch (err) {
      console.error("거래처 저장 실패", err);
      alert("저장 중 오류가 발생했습니다.");
    }
  };

  return (
    <>
      {/* [초안 그대로] */}
      <PageTitle title="거래처 관리" path="홈 > 기준정보 > 거래처" />

      {/* [초안 그대로] 조회조건: form 안에 label + input 나열. 조회 버튼은 submit */}
      <form className="search" onSubmit={조회}>
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

      {/* [변경] desc "총 0건" → 실제 건수 / 신규 버튼에 onClick 추가 */}
      <GridTitle title="거래처 목록" desc={`총 ${filtered.length}건`}>
        <button className="btn" onClick={신규}>신규</button>
      </GridTitle>
      <table className="grid">
        {/* [초안 그대로] 표 머리 */}        
        <thead>
          <tr>
            <th>No</th>
            <th>거래처코드</th>
            <th>거래처명</th>
            <th>구분</th>
            <th>연락처</th>
            <th>등록일</th>
          </tr>
        </thead>
        {/* [변경] 초안은 비어 있던 tbody → 서버 데이터를 .map으로 출력 */}
        <tbody>
          {filtered.map((p, i) => (
            // 행 클릭 시 상세 조회
            <tr key={p.partnerId} onClick={() => fetchDetail(p.partnerId)}>
              <td>{i + 1}</td>
              <td>{p.partnerCode}</td>
              <td>{p.partnerName}</td>
              <td>{TYPE_LABEL[p.partnerType]}</td>
              <td>{p.contact}</td>
              <td>{fmtDate(p.createdAt)}</td>
            </tr>
          ))}
        </tbody>
      </table>

      {/* [추가] 하단 상세 영역 (입고예정 화면처럼 선택 전에는 안내 문구만) */}
      {form == null ? (
        <p className="empty">목록에서 거래처를 클릭하면 상세가 나옵니다.</p>
      ) : (
        <>
          <GridTitle title={form.partnerId == null ? "거래처 등록" : "거래처 상세"}>
            <button className="btn primary" onClick={저장}>저장</button>
          </GridTitle>
          <div className="panel">
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