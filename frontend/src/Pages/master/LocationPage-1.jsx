import PageTitle from "../../Layout/PageTitle";
import GridTitle from "../../Layout/GridTitle";

// 02 창고·로케이션 — 담당: 기준정보 담당
export default function LocationPage(props) {
  // 조회 버튼 — 새로고침만 막는다. 실제 조회는 [팀원 작성]
  const handleSearch = (event) => {
    event.preventDefault();
  };

  return (
    <>
      <PageTitle
        title="창고·로케이션 관리"
        path="홈 > 기준정보 > 창고·로케이션"
      />

      {/* 조회조건: form 안에 label + input 나열. 조회 버튼은 submit */}
      <form className="search" onSubmit={handleSearch}>
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

      {/* 그리드 제목줄 + 표. 줄(tr)은 나중에 useState 배열을 .map 으로 */}
      <GridTitle title="로케이션 목록" desc="총 0건">
        <button className="btn">신규</button>
      </GridTitle>
      <table className="grid">
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
        <tbody>{/* 백에서 받은 데이터 */}</tbody>
      </table>
    </>
  );
}
