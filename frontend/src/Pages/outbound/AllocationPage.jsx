import PageTitle from "../../Layout/PageTitle";
import GridTitle from "../../Layout/GridTitle";

// 06 출고지시 — 담당: 김지환
export default function AllocationPage(props) {
  // 조회 버튼 — 새로고침만 막는다. 실제 조회는 [팀원 작성]
  const 조회 = (event) => {
    event.preventDefault();
  };

  return (
    <>
      <PageTitle title="출고지시" path="홈 > 출고관리 > 출고지시" />

      {/* 조회조건: form 안에 label + input 나열. 조회 버튼은 submit */}
      <form className="search" onSubmit={조회}>
        <label>출고요청일</label>
        <input type="date" name="from" /> ~ <input type="date" name="to" />
        <label>배송지명</label>
        <input type="text" name="partnerName" />
        <label>주문상태</label>
        <select name="status">
          <option value="WAITING">접수</option>
        </select>
        <input type="submit" className="btn primary" value="조회" />
      </form>

      {/* 그리드 제목줄 + 표. 줄(tr)은 나중에 useState 배열을 .map 으로 */}
      <GridTitle title="출고 대상 주문 (접수)" desc="총 0건">
        <button className="btn">신규</button>
      </GridTitle>
      <table className="grid">
        <thead>
          <tr>
            <th>NO</th>
            <th>주문번호</th>
            <th>배송지명</th>
            <th>출고요청일</th>
            <th>품목수</th>
            <th>총수량</th>
            <th>상태</th>
          </tr>
        </thead>
        <tbody></tbody>
      </table>
    </>
  );
}
