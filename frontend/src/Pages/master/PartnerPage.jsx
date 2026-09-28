import PageTitle from "../../Layout/PageTitle";
import GridTitle from "../../Layout/GridTitle";

// 01-2 거래처 — 담당: 기준정보 담당 (공급사 SUPPLIER / 납품처 CUSTOMER)
export default function PartnerPage(props) {
  // 조회 버튼 — 새로고침만 막는다. 실제 조회는 [팀원 작성]
  const 조회 = (event) => {
    event.preventDefault();
  };

  return (
    <>
      <PageTitle title="거래처 관리" path="홈 > 기준정보 > 거래처" />

      {/* 조회조건: form 안에 label + input 나열. 조회 버튼은 submit */}
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

      {/* 그리드 제목줄 + 표. 줄(tr)은 나중에 useState 배열을 .map 으로 */}
      <GridTitle title="거래처 목록" desc="총 0건">
        <button className="btn">신규</button>
      </GridTitle>
      <table className="grid">
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
        <tbody></tbody>
      </table>
    </>
  );
}
