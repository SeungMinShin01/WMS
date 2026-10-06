import { useEffect, useState } from "react"; // [변경] useEffect, useState 추가
import axios from "axios"; // [추가] 서버 호출용
import PageTitle from "../../Layout/PageTitle";
import GridTitle from "../../Layout/GridTitle";

// [추가] 신규 등록 시 빈 폼. locationId가 null이면 등록(POST), 값이 있으면 수정(PUT)
const EMPTY = {
  locationId: null,
  locationCode: "",
  isActive: true,
  capacity: "",
};

// [추가] "A-01-03" -> { zone: "A", aisle: "01", bay: "03" } (구역/통로/베이 열 표시용)
const parseCode = (code) => {
  const [zone, aisle, bay] = (code ?? "").split("-");
  return { zone: zone ?? "", aisle: aisle ?? "", bay: bay ?? "" };
};

// 02 창고·로케이션 — 담당: 기준정보 담당
export default function LocationPage(props) {
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
      setForm(res.data);
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
  const active = data.get("isActive"); // "", "Y", "N"

  setFiltered(
    locations.filter(
      (l) =>
        (zone === "" || parseCode(l.locationCode).zone === zone) &&
        (code === "" || code === "A-01-" || l.locationCode?.includes(code)) &&
        (active === "" || (active === "Y") === l.isActive)
      )
    );
  };

  // [추가] 신규 버튼: 빈 폼을 열어 하단 상세에 표시
  const 신규 = () => setForm({ ...EMPTY });

  // [추가] 상세 입력값 변경 처리
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
    };
    const isEdit = form.locationId != null;
    try {
      const res = isEdit
        ? await axios.put("/wms/location", body)
        : await axios.post("/wms/location", body);
      if (res.data === true) {
        alert(isEdit ? "수정되었습니다." : "등록되었습니다.");
        setForm(null);
        fetchLocations();
      } else {
        alert("저장에 실패했습니다.");
      }
    } catch (err) {
      console.error("로케이션 저장 실패", err);
      alert("저장 중 오류가 발생했습니다.");
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
        <select name="zone">
          <option value="A">A 상온 A구역</option>
          <option value="B">B 상온 B구역</option>
          <option value="C">C 상온 C구역</option>
        </select>
        <label>로케이션코드</label>
        <input type="text" name="locationCode" defaultValue="A-01-" />
        <label>사용여부</label>
        <select name="isActive">
          <option value="">전체</option>
          <option value="Y">사용</option>
          <option value="N">미사용</option>
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
        <button className="btn">신규</button>
      </GridTitle>
      <table className="grid">
        {/* thead: 초안 그대로 */}
        <thead>
          <tr>
            <th>로케이션코드</th>
            <th>구역</th>
            <th>통로</th>
            <th>베이</th>
            <th>사용여부</th>
            <th>혼용</th>
            <th>적치 품목 / LOT</th>
            <th>소비기한</th>
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
                <td>-</td>
                <td>-</td>
                <td>-</td>
                <td>{l.capacity}</td>
                <td>-</td>
                <td>-</td>
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
          <GridTitle title={form.locationId == null ? "로케이션 등록" : "로케이션 상세"}>
            <button className="btn primary" onClick={저장}>저장</button>
          </GridTitle>
          <div className="panel">
            <label>로케이션코드</label>
            <input name="locationCode" value={form.locationCode ?? ""} onChange={입력} />
            <label>사용여부</label>
            <select name="isActive" value={String(form.isActive)} onChange={입력}>
              <option value="true">사용</option>
              <option value="false">미사용</option>
            </select>
            <label>적재 가능 수량</label>
            <input type="number" name="capacity" value={form.capacity ?? ""} onChange={입력} />
          </div>
        </>
      )}
    </>
  );
}