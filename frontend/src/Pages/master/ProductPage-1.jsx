import PageTitle from "../../Layout/PageTitle";
import GridTitle from "../../Layout/GridTitle";

// 01 품목 — 담당: 기준정보 담당
export default function ProductPage(props) {
  // 조회 버튼 — 새로고침만 막는다. 실제 조회는 [팀원 작성]
  const handleSearch = (event) => {
    event.preventDefault();
  };

  return (
    <>
      <PageTitle title="품목 관리" path="홈 > 기준정보 > 품목" />

      {/* 조회조건: form 안에 label + input 나열. 조회 버튼은 submit */}
      <form className="search" onSubmit={handleSearch}>
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

      {/* 그리드 제목줄 + 표. 줄(tr)은 나중에 useState 배열을 .map 으로 */}
      <GridTitle title="품목 목록" desc="총 0건">
        <button className="btn">신규</button>
      </GridTitle>
      <table className="grid">
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
        <tbody>{/* 백에서 받은 데이터 */}</tbody>
      </table>
    </>
  );
}
