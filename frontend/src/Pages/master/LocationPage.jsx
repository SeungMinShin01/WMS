import { useEffect, useState } from "react"; // [변경] useEffect, useState 추가
import axios from "axios"; // [추가] 서버 호출용
import PageTitle from "../../Layout/PageTitle";
import GridTitle from "../../Layout/GridTitle";
import { useOutletContext } from "react-router-dom";

// [추가] 신규 등록 시 빈 폼. locationId가 null이면 등록(POST), 값이 있으면 수정(PUT)
const EMPTY = {
  locationId: null,
  locationCode: "",
  isActive: true, // 신규는 기본 활성 (DB 컬럼 is_active 의 DEFAULT TRUE 와 같은 기준)
  capacity: "",
};

// [추가] "A-01-03" -> { zone: "A", aisle: "01", bay: "03" } (구역/통로/베이 열 표시용)
const parseCode = (code) => {
  const [zone, aisle, bay] = (code ?? "").split("-");
  return { zone: zone ?? "", aisle: aisle ?? "", bay: bay ?? "" };
};

// ─────────────────────────────────────────────────────────────
// [추가] activeOf: 행 데이터에서 "활성 여부"를 true/false 로 꺼내는 함수
//   - 서버 JSON 의 키 이름이 응답마다 다를 수 있어서 둘 다 받는다.
//       · isActive : DTO 필드명을 그대로 직렬화한 경우
//       · active   : Lombok 이 boolean isActive 의 getter 를 isActive() 로 만들어
//                    Jackson 이 키를 "active" 로 내보내는 경우 (자주 생기는 함정)
//   - 둘 다 없으면 활성(true)으로 본다.
//   - 이전에는 l.isActive 를 바로 읽어서, 키가 "active" 로 오면 전부 미사용으로 보이는 문제가 있었다.
//   - 품목·거래처 화면과 같은 코드다. 공통 파일로 빼서 import 하면 중복이 없어진다.
// ─────────────────────────────────────────────────────────────
const activeOf = (row) => Boolean(row?.isActive ?? row?.active ?? true);

// ─────────────────────────────────────────────────────────────
// [추가] 비활성 뱃지
//   - 위치는 "고장/비활성 정도만 표시" 하기로 해서, 비활성일 때만 이 뱃지를 쓴다.
//     (활성은 아래 목록에서 아예 표시하지 않아서 정상 위치는 빈 칸으로 둔다.)
//   - 고장과 비활성은 DB 에서 구분하지 않고 is_active=false 하나로 처리한다.
//     → 비활성 = 사용 불가(고장 포함). 나중에 구분이 필요하면 status 컬럼을 따로 추가한다.
//   - 모양과 색은 품목·거래처의 비활성 뱃지와 같다(회색).
// ─────────────────────────────────────────────────────────────
function InactiveBadge() {
  return (
    <span
      style={{
        color: "#9e9e9e",
        border: "1px solid #9e9e9e",
        borderRadius: 4,
        padding: "2px 8px",
        fontSize: 12,
      }}
    >
      비활성
    </span>
  );
}

// 02 창고·로케이션 — 담당: 기준정보 담당
export default function LocationPage() {
  const { user } = useOutletContext();
  const isAdmin = user?.role === "ADMIN";
  // [추가] 상태값 3개
  const [locations, setLocations] = useState([]); // 서버 전체 목록
  const [filtered, setFiltered] = useState([]); // 화면에 보이는 목록
  const [form, setForm] = useState(null); // 하단 상세. null이면 안내 문구

  // [추가] 목록 조회 (GET /wms/locations)
  const fetchLocations = async () => {
    try {
      const res = await axios.get("/wms/locations");
      setLocations(res.data);
      setFiltered(res.data);
    } catch (err) {
      console.error("로케이션 목록 조회 실패", err);
    }
  };

  // [추가] 상세 조회 (GET /wms/location/detail?locationid=)
  const fetchDetail = async (locationId) => {
    try {
      const res = await axios.get("/wms/location/detail", {
        params: { locationid: locationId },
      });
      if (!res.data) {
        alert("로케이션 정보를 찾을 수 없습니다.");
        return;
      }
      // [변경] 이전: setForm(res.data)
      //        이후: 응답을 그대로 form 에 넣되, 상태만 activeOf 로 꺼내 isActive(boolean)로 덮어쓴다.
      //   이유: 서버 키가 "active" 로 와도 form 안에서는 항상 isActive 하나로만 다루기 위해서.
      setForm({ ...res.data, isActive: activeOf(res.data) });
    } catch (err) {
      console.error("로케이션 상세 조회 실패", err);
    }
  };

  // [추가] 화면이 처음 열릴 때 전체 목록 조회
  useEffect(() => {
    fetchLocations();
  }, []);

  // 조회 버튼 — 새로고침만 막는다. 실제 조회는 [팀원 작성]
  // [변경] 초안은 event.preventDefault()만 있었음.
  // 서버가 조회조건을 받지 않으므로 프론트에서 거른다.
  // (적치상태 occupied는 DTO에 값이 없어 아직 걸러지지 않음)
  const 조회 = (event) => {
    event.preventDefault();
    // 여기부터 추가
    const data = new FormData(event.target);
    const zone = data.get("zone");
    const code = data.get("locationCode").trim();
    // [변경] 이전: "", "Y", "N"  →  이후: "", "true", "false" (품목·거래처 화면과 같은 값)
    const active = data.get("isActive");

    setFiltered(
      locations.filter(
        (l) =>
          (zone === "" || parseCode(l.locationCode).zone === zone) &&
          (code === "" || code === "A-01-" || l.locationCode?.includes(code)) &&
          // [변경] 이전: (active === "Y") === l.isActive  (서버 키가 active 면 전부 걸러짐)
          //        이후: 행의 활성 여부를 activeOf 로 꺼내 문자열로 바꿔 select 값과 비교한다.
          (active === "" || String(activeOf(l)) === active),
      ),
    );
  };

  // [추가] 신규 버튼: 빈 폼을 열어 하단 상세에 표시
  const 신규 = () => setForm({ ...EMPTY });

  // [추가] 상세 입력값 변경 처리
  //   isActive 는 select 값이 "true"/"false" 문자열이라 여기서 boolean 으로 바꿔 저장한다.
  const 입력 = (e) => {
    const { name, value } = e.target;
    setForm({
      ...form,
      [name]: name === "isActive" ? value === "true" : value,
    });
  };

  // [추가] 저장: locationId 유무로 POST/PUT 분기 (서버는 true/false 반환)
  const 저장 = async () => {
    if (!form.locationCode.trim()) {
      alert("로케이션코드는 필수입니다.");
      return;
    }
    const body = {
      ...form,
      capacity: form.capacity === "" ? null : Number(form.capacity),
      // [추가] 서버로 보낼 상태값. Boolean() 으로 감싸서 항상 true/false 만 가도록 한다.
      //   주의: 키 이름이 isActive 로 고정이다. 서버 DTO 가 "active" 로 받는다면 여기 키를 맞춰야 한다.
      isActive: Boolean(form.isActive),
    };
    const isEdit = form.locationId != null;
    try {
      const res = isEdit
        ? await axios.put("/wms/location", body)
        : await axios.post("/wms/location", body);
      if (res.data) {
        // [변경] === true 였던 것 (등록은 새 번호가 와요)
        alert(isEdit ? "수정되었습니다." : "등록되었습니다.");
        setForm(null);
        fetchLocations();
      } else {
        alert("저장에 실패했습니다.");
      }
    } catch (err) {
      // [변경] 서버가 보낸 문구(400, 404, 409)가 있으면 그대로 보여준다
      const msg =
        typeof err.response?.data === "string" ? err.response.data : null;
      alert(msg || "저장 중 오류가 발생했습니다.");
    }
  };

  return (
    <>
      <PageTitle
        title="창고·로케이션 관리"
        path="홈 > 기준정보 > 창고·로케이션"
      />

      {/* 조회조건: form 안에 label + input 나열. 조회 버튼은 submit */}
      <form className="search" onSubmit={조회}>
        <label>구역</label>
        {/* [추가] "전체" 옵션. 이전에는 전체가 없어서 처음부터 A 구역만 걸러졌고,
            조회를 누르면 B·C 구역 위치는 항상 안 보였다. */}
        <select name="zone">
          <option value="">전체</option>
          <option value="A">A 상온 A구역</option>
          <option value="B">B 상온 B구역</option>
          <option value="C">C 상온 C구역</option>
        </select>
        <label>로케이션코드</label>
        <input type="text" name="locationCode" defaultValue="A-01-" />
        {/* [변경] 이전: 라벨 "사용여부", 옵션 사용/미사용, 값 Y / N
                  이후: 라벨 "상태", 옵션 활성/비활성, 값 true / false
            품목·거래처 화면과 문구와 값을 통일했다. 조회() 의 상태 필터와 연결된다. */}
        <label>상태</label>
        <select name="isActive">
          <option value="">전체</option>
          <option value="true">활성</option>
          <option value="false">비활성</option>
        </select>
        <label>적치상태</label>
        <select name="occupied">
          <option value="">전체</option>
          <option value="N">빈 칸</option>
          <option value="Y">적치중</option>
        </select>
        <input type="submit" className="btn primary" value="조회" />
      </form>

      {/* [변경] desc: "총 0건" -> 실제 건수 / 신규 버튼에 onClick 추가 */}
      {/* 그리드 제목줄 + 표. 줄(tr)은 나중에 useState 배열을 .map 으로 */}
      <GridTitle title="로케이션 목록" desc={`총 ${filtered.length}건`}>
        {isAdmin && (
          <button className="btn" onClick={신규}>
            신규
          </button>
        )}
      </GridTitle>
      <table className="grid">
        {/* thead: 초안 그대로, 단 "사용여부" 열 이름만 "상태" 로 바꿨다 */}
        <thead>
          <tr>
            <th>로케이션코드</th>
            <th>구역</th>
            <th>통로</th>
            <th>베이</th>
            <th>상태</th>
            <th>혼용</th>
            <th>적재 가능 수량</th>
            <th>실제 적재수량</th>
            <th>비고</th>
          </tr>
        </thead>
        {/* [변경] 초안은 빈 tbody. 서버 데이터를 .map으로 표시.
            "-"는 DTO에 아직 없는 값(혼용, 적치 품목/LOT, 소비기한, 실제 적재수량, 비고) */}
        <tbody>
          {filtered.map((l) => {
            const { zone, aisle, bay } = parseCode(l.locationCode);
            return (
              <tr key={l.locationId} onClick={() => fetchDetail(l.locationId)}>
                <td>{l.locationCode}</td>
                <td>{zone}</td>
                <td>{aisle}</td>
                <td>{bay}</td>
                <td>{l.isActive ? "사용" : "미사용"}</td>
                <td>{l.mixed ? "혼용" : "-"}</td>
                <td>{l.capacity}</td>
                <td className="num">
                  {l.storedQty ? l.storedQty.toLocaleString() : "-"}
                </td>
                <td className="left">{l.remark ?? "-"}</td>
              </tr>
            );
          })}
        </tbody>
      </table>

      {/* [추가] 하단 상세: 선택 전에는 안내 문구만 (입고예정 화면과 동일) */}
      {form == null ? (
        <p className="empty">목록에서 로케이션을 클릭하면 상세가 나옵니다.</p>
      ) : (
        <>
          <GridTitle
            title={form.locationId == null ? "로케이션 등록" : "로케이션 상세"}
          >
            {isAdmin && (
              <button className="btn primary" onClick={저장}>
                저장
              </button>
            )}
          </GridTitle>
          <div className="panel">
            <label>로케이션코드</label>
            <input
              name="locationCode"
              value={form.locationCode ?? ""}
              onChange={입력}
            />
            {/* [변경] 이전: 라벨 "사용여부", 옵션 사용/미사용
                      이후: 라벨 "상태", 옵션 활성/비활성 (문구 통일)
                - onChange 는 기존 입력() 을 그대로 쓴다. 입력() 안에서 isActive 만 boolean 으로 바꿔 준다.
                - [추가] disabled: 저장 버튼과 같은 기준으로 관리자(ADMIN)만 바꿀 수 있다.
                - 신규 등록은 EMPTY 의 isActive: true 때문에 "활성"이 미리 선택돼 있다. */}
            <label>상태</label>
            <select
              name="isActive"
              value={String(form.isActive)}
              onChange={입력}
              disabled={!isAdmin}
            >
              <option value="true">활성</option>
              <option value="false">비활성</option>
            </select>
            <label>적재 가능 수량</label>
            <input
              type="number"
              name="capacity"
              value={form.capacity ?? ""}
              onChange={입력}
            />
            <label>비고</label>
            <input
              type="text"
              name="remark"
              maxLength={200}
              value={form.remark ?? ""}
              onChange={입력}
            />
          </div>
        </>
      )}
    </>
  );
}
