import PageTitle from "../../Layout/PageTitle";
import GridTitle from "../../Layout/GridTitle";

// 03 입고예정 — 담당: 조현우
export default function InboundPage(props) {
  // 조회 버튼 — 새로고침만 막는다. 실제 조회는 [팀원 작성]
  const 조회 = (event) => {
    event.preventDefault();
  };
  

  return (
    <>
      <PageTitle title="입고예정" path="홈 > 입고관리 > 입고예정" />

      {/* 조회조건: form 안에 label + input 나열. 조회 버튼은 submit */}
      <form className="search" onSubmit={조회}>
        <label>입고예정일</label>
        <input type="date" name="from" /> ~ <input type="date" name="to" />
        <label>화주명</label>
        <input type="text" name="partnerName" />
        <label>입고예정번호</label>
        <input type="text" name="documentNo" defaultValue="IN-2026" />
        <label>상태</label>
        <select name="status">
          <option value="">전체</option>
          <option value="WAITING">입고예정</option>
          <option value="INSPECTED">검수완료</option>
          <option value="COMPLETED">입고완료</option>
          <option value="CANCELED">취소</option>
        </select>
        <input type="submit" className="btn primary" value="조회" />
      </form>

      {/* 그리드 제목줄 + 표. 줄(tr)은 나중에 useState 배열을 .map 으로 */}
      <GridTitle title="입고예정 목록" desc="총 0건">
        <button className="btn">신규</button>
      </GridTitle>
      <table className="grid">
        <thead>
          <tr>
            <th>No</th>
            <th>입고예정번호</th>
            <th>화주명</th>
            <th>입고예정일</th>
            <th>품목수</th>
            <th>예정수량 합계</th>
            <th>상태</th>
            <th>비고</th>
            <th>등록일시</th>
            <th>입고확정일시</th>
          </tr>
        </thead>
        <tbody></tbody>
      </table>
    </>
  );
}
