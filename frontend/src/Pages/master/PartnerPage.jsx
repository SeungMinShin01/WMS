import { useEffect, useState } from "react";
import axios from "axios";
import PageTitle from "../../Layout/PageTitle";
import GridTitle from "../../Layout/GridTitle";
import { useOutletContext } from "react-router-dom";
import useSort from "../../Layout/useSort";

const TYPE_LABEL = { SUPPLIER: "공급사", CUSTOMER: "납품처" };

const EMPTY = {
  partnerId: null,
  tenantId: "", // 신규일 때 화주를 직접 고르게 비워 둔다
  partnerCode: "PT-",
  partnerName: "",
  partnerType: "SUPPLIER",
  contact: "",
  address: "",
  // [추가] 상태(is_active). 신규 등록 화면을 열면 기본값이 "활성"이 되도록 true 로 시작한다.
  //        (DB 컬럼 is_active 의 DEFAULT TRUE 와 같은 기준)
  isActive: true,
};

const fmtDate = (v) => (v ? v.slice(0, 10) : "");

// ─────────────────────────────────────────────────────────────
// [추가] activeOf: 행 데이터에서 "활성 여부"를 true/false 로 꺼내는 함수
//   - 서버 JSON 의 키 이름이 응답마다 다를 수 있어서 둘 다 받는다.
//       · isActive : DTO 필드명을 그대로 직렬화한 경우
//       · active   : Lombok 이 boolean isActive 의 getter 를 isActive() 로 만들어
//                    Jackson 이 키를 "active" 로 내보내는 경우 (자주 생기는 함정)
//   - 둘 다 없으면(아직 백엔드에 컬럼이 없을 때) 활성(true)으로 본다.
//   - 목록 표시, 조회 필터, 상세 폼 세 곳이 이 함수 하나만 쓰도록 통일했다.
//   - ProductPage 와 같은 코드다. 공통 파일로 빼서 import 하면 중복이 없어진다.
// ─────────────────────────────────────────────────────────────
const activeOf = (row) => Boolean(row?.isActive ?? row?.active ?? true);

// ─────────────────────────────────────────────────────────────
// [추가] 상태 뱃지 (ProductPage 와 동일한 모양·색)
//   - STATUS: true/false 를 화면 문구와 색으로 바꾸는 표.
//     객체 키는 문자열이라 사용할 때 String(active) 로 변환해서 찾는다.
//   - 활성은 초록, 비활성은 회색.
// ─────────────────────────────────────────────────────────────
const STATUS = {
  true: { label: "활성", color: "#2e7d32" },
  false: { label: "비활성", color: "#9e9e9e" },
};

function StatusBadge({ active }) {
  const s = STATUS[String(active)];
  return (
    <span
      style={{
        color: s.color,
        border: `1px solid ${s.color}`,
        borderRadius: 4,
        padding: "2px 8px",
        fontSize: 12,
      }}
    >
      {s.label}
    </span>
  );
}

// 01-2 거래처 — 담당: 기준정보 담당 (공급사 SUPPLIER / 납품처 CUSTOMER)
export default function PartnerPage() {
  const { user } = useOutletContext();
  const isAdmin = user?.role === "ADMIN";
  const [partners, setPartners] = useState([]);
  const [filtered, setFiltered] = useState([]);
  const [form, setForm] = useState(null);
  const [tenants, setTenants] = useState([]); // 화주 select 옵션

  const fetchPartners = async () => {
    try {
      const res = await axios.get("/wms/partners");
      setPartners(res.data);
      setFiltered(res.data);
    } catch (err) {
      console.error("거래처 목록 조회 실패", err);
    }
  };

  // 화주 목록 조회 (GET /wms/tenants)
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
      // [변경] 이전: setForm(res.data)
      //        이후: 응답을 그대로 form 에 넣되, 상태만 activeOf 로 꺼내 isActive(boolean)로 덮어쓴다.
      //   이유: 서버 키가 "active" 로 와도 form 안에서는 항상 isActive 하나로만 다루기 위해서.
      //         이렇게 해두면 아래 상태 select 와 저장(body)이 키 이름을 신경 쓰지 않아도 된다.
      setForm({ ...res.data, isActive: activeOf(res.data) });
    } catch (err) {
      // 없는 거래처(404)면 서버 문구를 보여준다
      const msg =
        typeof err.response?.data === "string" ? err.response.data : null;
      alert(msg || "거래처 정보를 불러오지 못했습니다.");
    }
  };

  useEffect(() => {
    fetchPartners();
    fetchTenants();
  }, []);

  const 조회 = (event) => {
    event.preventDefault();
    const data = new FormData(event.target);
    const tenantId = data.get("tenantId");
    const code = data.get("partnerCode").trim();
    const name = data.get("partnerName").trim();
    const type = data.get("partnerType");
    // [추가] 조회조건의 상태 select 값을 읽는다. ""(전체) | "true"(활성) | "false"(비활성)
    const active = data.get("isActive");

    setFiltered(
      partners.filter(
        (p) =>
          (tenantId === "" || String(p.tenantId) === tenantId) &&
          (code === "" || code === "PT-" || p.partnerCode?.includes(code)) &&
          (name === "" || p.partnerName?.includes(name)) &&
          (type === "" || p.partnerType === type) &&
          // [추가] 상태 필터. 전체("")면 통과, 아니면 행의 활성 여부를 문자열로 바꿔 select 값과 비교한다.
          (active === "" || String(activeOf(p)) === active),
      ),
    );
  };

  const 신규 = () => setForm({ ...EMPTY });

  const 입력 = (e) => setForm({ ...form, [e.target.name]: e.target.value });

  // [추가] 상태 select 전용 변경 함수
  //   위의 입력()은 값을 문자열 그대로 저장하는데, select 값은 "true"/"false" 문자열이다.
  //   서버에는 boolean 을 보내야 하므로 여기서 === "true" 비교로 true/false 로 바꿔서 저장한다.
  const 활성변경 = (e) =>
    setForm({ ...form, isActive: e.target.value === "true" });

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
    const body = {
      ...form,
      tenantId: Number(form.tenantId), // 문자열 → 숫자
      // [추가] 서버로 보낼 상태값. Boolean() 으로 감싸서 항상 true/false 만 가도록 한다.
      //   주의: 키 이름이 isActive 로 고정이다. 서버 DTO 가 "active" 로 받는다면 여기 키를 맞춰야 한다.
      isActive: Boolean(form.isActive),
    };

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
      const msg =
        typeof err.response?.data === "string" ? err.response.data : null;
      alert(msg || "저장 중 오류가 발생했습니다.");
    }
  };

  const { sorted, th } = useSort(filtered);

  return (
    <>
      <PageTitle title="거래처 관리" path="홈 > 기준정보 > 거래처" />

      <form className="search" onSubmit={조회}>
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
        {/* [추가] 상태 조회조건. 값은 true/false 문자열이고, 조회() 의 상태 필터와 연결된다. */}
        <label>상태</label>
        <select name="isActive">
          <option value="">전체</option>
          <option value="true">활성</option>
          <option value="false">비활성</option>
        </select>
        <input type="submit" className="btn primary" value="조회" />
      </form>

      <GridTitle title="거래처 목록" desc={`총 ${filtered.length}건`}>
        {isAdmin && (
          <button className="btn" onClick={신규}>
            신규
          </button>
        )}
      </GridTitle>
      <table className="grid">
        <thead>
          <tr>
            <th>No</th>
            <th>화주</th> {/* [추가] */}
            {th("partnerCode", "거래처코드")}
            <th>거래처명</th>
            <th>구분</th>
            <th>연락처</th>
            {th("createdAt", "등록일")}
          </tr>
        </thead>
        <tbody>
          {sorted.map((p, i) => (
            <tr key={p.partnerId} onClick={() => fetchDetail(p.partnerId)}>
              <td>{i + 1}</td>
              <td>{p.tenantName}</td>
              <td>{p.partnerCode}</td>
              <td>{p.partnerName}</td>
              <td>{TYPE_LABEL[p.partnerType]}</td>
              <td>{p.contact}</td>
              {/* [추가] activeOf(p) 결과(true/false)를 뱃지로 표시 (활성=초록, 비활성=회색) */}
              <td>
                <StatusBadge active={activeOf(p)} />
              </td>
              <td>{fmtDate(p.createdAt)}</td>
            </tr>
          ))}
        </tbody>
      </table>

      {form == null ? (
        <p className="empty">목록에서 거래처를 클릭하면 상세가 나옵니다.</p>
      ) : (
        <>
          <GridTitle
            title={form.partnerId == null ? "거래처 등록" : "거래처 상세"}
          >
            {isAdmin && (
              <button className="btn primary" onClick={저장}>
                저장
              </button>
            )}
          </GridTitle>
          <div className="panel">
            {/* 화주: 신규는 선택, 수정은 바꿀 수 없다(표시만) */}
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
            <input
              name="partnerCode"
              value={form.partnerCode ?? ""}
              onChange={입력}
            />
            <label>거래처명</label>
            <input
              name="partnerName"
              value={form.partnerName ?? ""}
              onChange={입력}
            />
            <label>구분</label>
            <select
              name="partnerType"
              value={form.partnerType ?? "SUPPLIER"}
              onChange={입력}
            >
              <option value="SUPPLIER">공급사</option>
              <option value="CUSTOMER">납품처</option>
            </select>
            <label>연락처</label>
            <input name="contact" value={form.contact ?? ""} onChange={입력} />
            <label>주소</label>
            <input name="address" value={form.address ?? ""} onChange={입력} />
            {/* [추가] 상태 입력란
                - value: form.isActive(boolean)를 문자열로 바꿔 select 의 값과 맞춘다.
                - onChange: 문자열 → boolean 변환이 필요해서 입력() 대신 활성변경()을 쓴다.
                - disabled: 저장 버튼과 같은 기준으로 관리자(ADMIN)만 바꿀 수 있다.
                - 신규 등록은 EMPTY 의 isActive: true 때문에 "활성"이 미리 선택돼 있다. */}
            <label>상태</label>
            <select
              name="isActive"
              value={String(form.isActive)}
              onChange={활성변경}
              disabled={!isAdmin}
            >
              <option value="true">활성</option>
              <option value="false">비활성</option>
            </select>
          </div>
        </>
      )}
    </>
  );
}
