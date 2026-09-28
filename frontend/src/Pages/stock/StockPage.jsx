import PageTitle from "../../Layout/PageTitle";
import GridTitle from "../../Layout/GridTitle";

// 07 재고현황 — 담당: 조현우
export default function StockPage(props) {
  // 조회 버튼 — 새로고침만 막는다. 실제 조회는 [팀원 작성]
  const 조회 = (event) => {
    event.preventDefault();
  };

  return (
    <>
      <PageTitle title="재고 현황" path="홈 > 재고관리 > 재고현황" />

      {/* 조회조건: form 안에 label + input 나열. 조회 버튼은 submit */}
      <form className="search" onSubmit={조회}>
        <label>품목코드</label>
        <input type="text" name="productCode" defaultValue="SKU-" />
        <label>품목명</label>
        <input type="text" name="productName" placeholder="품목명 입력" />
        <label>구역</label>
        <select name="zone">
          <option value="">전체</option>
          <option value="A">A</option>
          <option value="B">B</option>
          <option value="C">C</option>
        </select>
        <label>재고상태</label>
        <select name="status">
          <option value="">전체</option>
          <option value="NORMAL">정상</option>
          <option value="SHORT">부족</option>
          <option value="NEAR">임박(30일 이하)</option>
        </select>
        <input type="submit" className="btn primary" value="조회" />
      </form>

      {/* 그리드 제목줄 + 표. 줄(tr)은 나중에 useState 배열을 .map 으로 */}
      <GridTitle title="품목별 재고" desc="총 0건">
        <button className="btn">신규</button>
      </GridTitle>
      <table className="grid">
        <thead>
          <tr>
            <th>No</th>
            <th>품목코드</th>
            <th>품목명</th>
            <th>규격</th>
            <th>단위</th>
            <th>실물</th>
            <th>선점</th>
            <th>가용</th>
            <th>로케이션</th>
            <th>최단 소비기한</th>
            <th>남은일수</th>
            <th>상태</th>
          </tr>
        </thead>
        <tbody></tbody>
      </table>
    </>
  );
}
