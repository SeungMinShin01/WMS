import PageTitle from "../../Layout/PageTitle";
import GridTitle from "../../Layout/GridTitle";

// 04 입고검수 — 담당: 조현우
export default function InspectionPage(props) {
  // 조회 버튼 — 새로고침만 막는다. 실제 조회는 [팀원 작성]
  const 조회 = (event) => {
    event.preventDefault();
  };

  return (
    <>
      <PageTitle title="입고 검수" path="홈 > 입고관리 > 입고검수" />

      {/* 조회조건: form 안에 label + input 나열. 조회 버튼은 submit */}
      <form className="search" onSubmit={조회}>
        <label>입고예정일</label>
        <input type="date" name="from" /> ~ <input type="date" name="to" />
        <label>화주명</label>
        <input type="text" name="partnerName" />
        <label>상태</label>
        <select name="status">
          <option value="WAITING,INSPECTED">검수 대상 (등록·검수·적치)</option>
          <option value="WAITING">등록</option>
          <option value="INSPECTED">검수완료</option>
        </select>
        <input type="submit" className="btn primary" value="조회" />
      </form>

      {/* 그리드 제목줄 + 표. 줄(tr)은 나중에 useState 배열을 .map 으로 */}
      <GridTitle title="검수 대상 입고예정" desc="총 0건">
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
            <th>예정수량</th>
            <th>상태</th>
            <th>실제 입고수량 합계</th>
            <th>비고</th>
          </tr>
        </thead>
        <tbody></tbody>
      </table>
    </>
  );
}
