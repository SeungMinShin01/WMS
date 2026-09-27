import PageTitle from "../../Layout/PageTitle";
import GridTitle from "../../Layout/GridTitle";

// 08 입출고이력 — 담당: 조현우
export default function HistoryPage(props) {
  // 조회 버튼 — 새로고침만 막는다. 실제 조회는 [팀원 작성]
  const 조회 = (event) => {
    event.preventDefault();
  };

  return (
    <>
      <PageTitle title="입출고 이력" path="홈 > 재고관리 > 입출고이력" />

      {/* 조회조건: form 안에 label + input 나열. 조회 버튼은 submit */}
      <form className="search" onSubmit={조회}>
        <label>기간</label>
        <input type="date" name="from" /> ~ <input type="date" name="to" />
        <label>품목</label>
        <input type="text" name="productCode" placeholder="전체" />
        <label>로케이션</label>
        <input type="text" name="locationCode" />
        <label>변동타입</label>
        <select name="txType">
          <option value="">전체</option>
          <option value="INBOUND">입고</option>
          <option value="ALLOCATE">선점</option>
          <option value="DEALLOCATE">선점해제</option>
          <option value="SHORTAGE">결품</option>
          <option value="OUTBOUND">출고</option>
          <option value="ADJUST">조정</option>
        </select>
        <input type="submit" className="btn primary" value="조회" />
      </form>

      {/* 그리드 제목줄 + 표. 줄(tr)은 나중에 useState 배열을 .map 으로 */}
      <GridTitle title="재고 이력" desc="총 0건 · 추가만 되는 기록 (수정·삭제 불가)" />
      <table className="grid">
        <thead>
          <tr>
            <th>No</th>
            <th>발생일시</th>
            <th>변동타입</th>
            <th>품목코드</th>
            <th>품목명</th>
            <th>로케이션</th>
            <th>LOT</th>
            <th>소비기한</th>
            <th>실물 증감</th>
            <th>선점 증감</th>
            <th>변경 후 실물</th>
            <th>변경 후 선점</th>
            <th>근거 문서</th>
            <th>사유</th>
            <th>처리자</th>
          </tr>
        </thead>
        <tbody></tbody>
      </table>
    </>
  );
}
