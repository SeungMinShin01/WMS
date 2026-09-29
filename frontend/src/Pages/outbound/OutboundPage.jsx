import { useState , useEffect } from "react";
import axios from "axios";
import PageTitle from "../../Layout/PageTitle";
import GridTitle from "../../Layout/GridTitle";
import { Link } from "react-router-dom";

// 05 출고예정 — 담당: 김지환
export default function OutboundPage(props) {
  // 조회 버튼 — 새로고침만 막는다. 실제 조회는 [팀원 작성]
  const [outbounds, setOutbounds] = useState([]);
  const [viewList, setViewList] = useState([]);
  useEffect(() => {
      async function getOutbounds() {
    const response = await axios.get("http://localhost:8080/wms/outbounds");
    setOutbounds(response.data);
    setViewList(response.data);
    }
    getOutbounds();
  } , []);
  const 조회 = (event) => {
    event.preventDefault();
    const status = event.target.status.value;        // name="status" select 값 ("" 이면 전체)
    const from = event.target.from.value;            // "2026-10-01" 형식, 비어 있으면 ""
    const to = event.target.to.value;
    const partnerName = event.target.partnerName.value;
    let result = [];                                 // 조건 통과한 줄을 담을 새 배열
    for (let i = 0; i < outbounds.length; i++) {
      const row = outbounds[i];
      const date = row.expectedAt.substring(0, 10);  // "2026-10-07T15:00:00" → "2026-10-07"
      if (status !== "" && row.status !== status) continue;        // 상태가 다르면 건너뜀
      if (from !== "" && date < from) continue;                    // 시작일보다 이전이면 건너뜀
      if (to !== "" && date > to) continue;                        // 종료일보다 이후면 건너뜀
      if (partnerName !== "" && !row.partnerName.includes(partnerName)) continue; // 이름 부분검색
      result.push(row);                              // 모든 조건 통과 → 담기
  }
  setViewList(result); 
  };
  return (
    <>
      <PageTitle title="출고예정 (주문)" path="홈 > 출고관리 > 출고예정" />

      {/* 조회조건: form 안에 label + input 나열. 조회 버튼은 submit */}
      <form className="search" onSubmit={조회}>
        <label>출고요청일</label>
        <input type="date" name="from" /> ~ <input type="date" name="to" />
        <label>배송지명</label>
        <input type="text" name="partnerName" />
        <label>주문번호</label>
        <input type="text" name="documentNo" defaultValue="OUT-2026" />
        <label>상태</label>
        <select name="status">
          <option value="">전체</option>
          <option value="WAITING">접수</option>
          <option value="ALLOCATED">출고지시됨</option>
          <option value="SHIPPED">출고완료</option>
          <option value="CANCELED">취소</option>
        </select>
        <input type="submit" className="btn primary" value="조회" />
      </form>

      {/* 그리드 제목줄 + 표 */}
      <GridTitle title="출고예정 목록" desc={`총 ${viewList.length}건`}>
        {/* outbounds.length: 상자 안 배열의 개수. 백틱과 ${} 는 문자열 안에 값을 끼워 넣는 문법 */}
        <button className="btn">신규</button>
      </GridTitle>
      <table className="grid">
        <thead>
          <tr>
            <th>No</th>
            <th>주문번호</th>
            <th>배송지명</th>
            <th>배송지 주소</th>
            <th>연락처</th>
            <th>출고요청일</th>
            <th>품목수</th>
            <th>총수량</th>
            <th>상태</th>
            <th>출고지시번호</th>
            <th>등록일시</th>
          </tr>
        </thead>
        <tbody>
          {viewList.map((outbound, index) => (
            // map: 배열의 항목을 하나씩 꺼내서 <tr> 하나로 바꿔 줌. 항목이 2개면 tr이 2줄 나옴
            // outbound: 지금 꺼낸 항목 한 줄, index: 0부터 시작하는 순서
            <tr key={outbound.documentId}>
              {/* key: React가 줄을 구분하려고 요구하는 고유값. PK인 documentId를 씀 */}
              <td>{index + 1}</td>
              <td><Link to={"/outbounds/allocation?documentId=" + outbound.documentId}>{outbound.documentNo}</Link></td>
              <td>{outbound.partnerName}</td>
              <td></td>
              <td></td>
              {/* 배송지 주소, 연락처: 응답에 없어서 빈 칸 */}
              <td>{outbound.expectedAt.replace("T", " ")}</td>
              {/* expectedAt "2026-10-07T15:00:00"의 T를 공백으로 바꿔서 보기 좋게 */}
              <td></td>
              <td></td>
              {/* 품목수, 총수량: 응답에 없어서 빈 칸 */}
              <td>{outbound.status}</td>
              <td></td>
              <td></td>
              {/* 출고지시번호, 등록일시: 응답에 없어서 빈 칸 */}
            </tr>
          ))}
        </tbody>
      </table>
    </>
  );
}
