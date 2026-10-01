import PageTitle from "../../Layout/PageTitle";
import GridTitle from "../../Layout/GridTitle";
import { useEffect, useState } from "react";
import axios from "axios";

// status 코드 -> 화면에 띄울 한글
// 날짜 모양(yyyy-MM-dd / yyyy-MM-dd HH:mm)과 남은일수는 백엔드 DTO에서 만들어서 보낸다
const 상태명= {WAITING: "입고예정", INSPECTED: "검수완료", COMPLETED: "입고완료", CANCELED: "취소"};

// 03 입고예정 — 담당: 조현우
export default function InboundPage(props) {
  const [inbounds, setInbounds] = useState([]); // ED-10 입고 예정 목록
  const [detail, setDetail] = useState(null);   // ED-13 클릭한 문서 상세
  const [조건, set조건] = useState({from: "", to: "", partnerName: "", documentNo: "", status: ""});  // 조회 조건


  // ED-10 조회 버튼 — 새로고침만 막는다. 실제 조회는 [팀원 작성]
  const 목록조회 = async () => {
    const response = await axios.get("/wms/inbounds");
    setInbounds(response.data);
  }

  // ED-13 상세 조회 - 목록 줄 클릭하면 실행
  const 상세조회 = async(documentId)=>{
    const response = await axios.get(`/wms/inbounds/${documentId}`);
    setDetail(response.data)
  }

  // 조회 버튼 - 새로고침 막고 목록 다시 불러오기
  const 조회 = (event) => {
    event.preventDefault();   // 새로고침 막기
    const form = new FormData(event.target);
    set조건({
      from:form.get("from"),
      to:form.get("to"),
      partnerName: form.get("partnerName").trim(),
      documentNo: form.get("documentNo").trim(),
      status: form.get("status"),
    });
    목록조회();
  };
  

  // 화면이 처음 열릴때 목록 한번 불러오기
  useEffect(()=>{
    목록조회();
  },[]);

  // 조회 조건에 맞는 문서만 (빈 조건은 통과)
  const 보여줄목록 = inbounds.filter((d) =>
    (조건.from === "" || d.expectedAt >= 조건.from) &&
    (조건.to === "" || d.expectedAt <= 조건.to) &&
    (조건.partnerName === "" || d.partnerName.includes(조건.partnerName)) &&
    (조건.documentNo === "" || d.documentNo.includes(조건.documentNo)) &&
    (조건.status === "" || d.status === 조건.status)
  );

  // 상세 품목의 예정수량 합계
  const 예정합계 = detail ? detail.items.reduce((sum, item) => sum + item.expectedQty, 0) : 0;

  return (
    <>
      <PageTitle title="입고예정" path="홈 > 입고관리 > 입고예정" />

      <form className="search" onSubmit={조회}>
        <label>입고예정일</label>
        <input type="date" name="from" /> ~ <input type="date" name="to" />
        <label>화주명</label>
        <input type="text" name="partnerName" />
        <label>입고예정번호</label>
        <input type="text" name="documentNo" defaultValue="IN-2026" />
        <label>상태</label>
        <select name="status" defaultValue="">
          <option value="">전체</option>
          <option value="WAITING">입고예정</option>
          <option value="INSPECTED">검수완료</option>
          <option value="COMPLETED">입고완료</option>
          <option value="CANCELED">취소</option>
        </select>
        <input type="submit" className="btn primary" value="조회" />
      </form>

      <GridTitle title="입고예정 목록" desc={`총 ${보여줄목록.length}건`}>
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
        <tbody>
          {보여줄목록.map((inbound, index) => (
            <tr
              key={inbound.documentId}
              onClick={() => 상세조회(inbound.documentId)}
              className={detail && detail.documentId === inbound.documentId ? "on" : ""}
            >
              <td>{index + 1}</td>
              <td>{inbound.documentNo}</td>
              <td className="left">{inbound.partnerName}</td>
              <td>{inbound.expectedAt}</td>
              <td className="num">{inbound.itemCount}</td>
              <td className="num">{inbound.totalExpectedQty.toLocaleString()}</td>
              <td>{상태명[inbound.status]}</td>
              <td className="left"></td>
              <td>{inbound.createdAt}</td>
              <td>{inbound.completedAt}</td>
            </tr>
          ))}
        </tbody>
      </table>

      {detail === null ? (
        <p className="hint">목록에서 입고예정을 클릭하면 상세가 나옵니다.</p>
      ) : (
        <div className="two-col">
          <div className="col-left">
            <GridTitle title="입고 정보" desc={detail.documentNo} />
            <table className="form">
              <tbody>
                <tr><th>입고예정번호</th><td>{detail.documentNo}</td></tr>
                <tr><th>화주명</th><td>{detail.partnerName}</td></tr>
                <tr><th>입고예정일</th><td>{detail.expectedAt}</td></tr>
                <tr><th>상태</th><td>{상태명[detail.status]}</td></tr>
                <tr><th>입고확정일시</th><td>{detail.completedAt}</td></tr>
              </tbody>
            </table>
          </div>

          <div className="col-right">
            <GridTitle title="입고 품목" desc={`${detail.items.length}품목 · 예정수량 ${예정합계.toLocaleString()}`} />
            <table className="grid">
              <thead>
                <tr>
                  <th>No</th>
                  <th>품목코드</th>
                  <th>품목명</th>
                  <th>LOT</th>
                  <th>소비기한</th>
                  <th>남은일수</th>
                  <th>예정수량</th>
                </tr>
              </thead>
              <tbody>
                {detail.items.map((item, index) => (
                  <tr key={item.documentItemId}>
                    <td>{index + 1}</td>
                    <td>{item.productCode}</td>
                    <td className="left">{item.productName}</td>
                    <td>{item.lotCode}</td>
                    <td>{item.expiryDate}</td>
                    <td className="num">{item.remainingDays}일</td>
                    <td className="num">{item.expectedQty.toLocaleString()}</td>
                  </tr>
                ))}
                <tr className="sum">
                  <td colSpan={6}>합계</td>
                  <td className="num">{예정합계.toLocaleString()}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      )}
    </>
  );
}
