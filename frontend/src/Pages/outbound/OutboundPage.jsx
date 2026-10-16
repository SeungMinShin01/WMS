import { useEffect, useState } from "react";
import axios from "axios";
import { useNavigate } from "react-router-dom";
import PageTitle from "../../Layout/PageTitle";
import GridTitle from "../../Layout/GridTitle";
import DocumentHeader from "../../Layout/DocumentHeader";
import OutboundCreateForm from "./OutboundCreateForm";
import usePaging from "../../Layout/usePaging";

// 문서 상태 영어 값 → 화면에 보여줄 한글 (값에 없는 상태는 영어 그대로)
const STATUS_NAME = {
  WAITING: "출고예정",
  ALLOCATED: "출고할당",
  PICKING: "재고피킹",
  SHIPPED: "출고완료",
  CANCELED: "취소",
};

// [ED-61] 문서 출처 영어 값 → 한글
const SOURCE_NAME = {
  WMS: "WMS",
  PORTAL: "화주요청",
};

// 06 출고문서 — 담당: 김지환
// 목록 줄 클릭 → 상세(헤더 + 주문 품목) → [출고지시] / [피킹리스트]로 이동
export default function OutboundPage() {
  const navigate = useNavigate();
  const [outbounds, setOutbounds] = useState([]); // ED-12 출고 문서 전체
  const [tenants, setTenants] = useState([]); // 화주 선택 목록 [{tenantId, tenantName}]
  const [filter, setFilter] = useState({
    tenantId: "", // "" = 전체
    from: "",
    to: "",
    partnerName: "",
    documentNo: "",
    status: "",
  }); // 조회 조건
  const [detail, setDetail] = useState(null); // 선택한 문서 상세 (null = 목록 화면)
  const [creating, setCreating] = useState(false);
  // 칸 제목 클릭 정렬 : sortKey = 정렬 중인 칸 이름, sortAsc = true 오름차순 / false 내림차순
  // 처음 화면 : 주문번호 오름차순(▲)으로 시작
  const [sortKey, setSortKey] = useState("documentNo");
  const [sortAsc, setSortAsc] = useState(true);

  // 출고 목록 조회 (취소 후 다시 불러야 해서 useEffect 밖에 만듦)
  // 화주 선택 목록도 같은 응답으로 만든다 : 출고 문서에 들어 있는 화주를 중복 없이 모음 (출고지시 화면과 같은 방식)
  const getList = async () => {
    const response = await axios.get("/wms/outbounds");
    setOutbounds(response.data);
    let result = [];
    for (let i = 0; i < response.data.length; i++) {
      const doc = response.data[i];
      let exists = false;
      for (let j = 0; j < result.length; j++) {
        if (result[j].tenantId === doc.tenantId) exists = true;
      }
      if (!exists) {
        result.push({ tenantId: doc.tenantId, tenantName: doc.tenantName });
      }
    }
    setTenants(result);
  };

  // 엑셀 일괄 등록: 파일을 고르면 바로 업로드 (한 줄이라도 틀리면 전체 취소)
  const uploadExcel = async (event) => {
    const file = event.target.files[0];
    event.target.value = ""; // 같은 파일을 다시 고를 수 있게 초기화
    if (!file) return;
    const form = new FormData(); // 파일은 JSON 이 아니라 FormData 로 보냄
    form.append("file", file); // 컨트롤러 @RequestParam("file") 과 이름 같아야 함
    try {
      const response = await axios.post("/wms/outbounds/upload", form);
      alert(response.data); // "출고문서 3건, 품목 5줄 등록"
      getList();
    } catch (error) {
      alert(`엑셀 등록 실패 (전체 취소)\n${error.response?.data ?? "오류"}`);
    }
  };

  // 최초 1번 목록 조회
  useEffect(() => {
    getList();
  }, []);

  // 목록 줄 클릭 → ED-17 주문 상세
  const selectDocument = async (documentId) => {
    const response = await axios.get("/wms/outbounds/" + documentId);
    setDetail(response.data);
  };

  const goBack = () => {
    setDetail(null);
  };

  const goAllocation = () => {
    navigate(`/outbounds/allocation/${detail.documentId}`);
  };

  const goPicking = () => {
    navigate(`/outbounds/picking/${detail.documentId}`);
  };

  // 주문 취소 : 접수·할당 상태에서만 버튼이 보임
  const cancelOrder = async (event, outbound) => {
    event.stopPropagation(); // 버튼 클릭이 줄 클릭(상세 열기)으로 번지지 않게
    if (!confirm(outbound.documentNo + " 주문을 취소할까요? ")) return;
    try {
      await axios.put("/wms/outbounds/" + outbound.documentId + "/cancel");
      alert("주문이 취소되었습니다");
      getList(); // 상태가 취소로 바뀐 목록 다시 불러오기
    } catch (error) {
      alert(error.response ? error.response.data : "서버에 연결할 수 없습니다");
    }
  };

  // 조회 버튼 - 입력한 조건 저장 + 목록 다시 불러오기
  const handleSearch = (event) => {
    event.preventDefault();
    setFilter({
      tenantId: event.target.tenantId.value,
      from: event.target.from.value,
      to: event.target.to.value,
      partnerName: event.target.partnerName.value.trim(),
      documentNo: event.target.documentNo.value.trim(),
      status: event.target.status.value,
    });
    getList();
  };

  // 초기화 버튼 - 입력칸을 처음 값으로 되돌리고 조건 없이 다시 조회
  // event.target.form : 버튼이 들어 있는 form / reset() : 입력칸을 defaultValue 로 되돌림
  const handleReset = (event) => {
    event.target.form.reset();
    setFilter({
      tenantId: "",
      from: "",
      to: "",
      partnerName: "",
      documentNo: "",
      status: "",
    });
    getList();
  };

  // 조회 조건에 맞는 문서만 (빈 조건은 통과)
  const visibleList = outbounds.filter((outbound) => {
    const day = outbound.expectedAt.substring(0, 10); // "2026-10-07T15:00:00" → "2026-10-07"
    // 화주 : select 값은 글자("2"), 응답의 tenantId 는 숫자(2) → String 으로 맞춰서 비교
    if (filter.tenantId !== "" && String(outbound.tenantId) !== filter.tenantId)
      return false;
    if (filter.status !== "" && outbound.status !== filter.status) return false;
    if (filter.from !== "" && day < filter.from) return false;
    if (filter.to !== "" && day > filter.to) return false;
    if (
      filter.partnerName !== "" &&
      !outbound.partnerName.includes(filter.partnerName)
    )
      return false;
    if (
      filter.documentNo !== "" &&
      !outbound.documentNo.includes(filter.documentNo)
    )
      return false;
    return true;
  });

  // ── 칸 제목 클릭 정렬 (주문번호 · 출고요청일 · 등록일시) ──
  // 정렬 중인 칸을 다시 누르면 방향만 뒤집음 (▲ → ▼ → ▲ ...)
  // 다른 칸을 누르면 그 칸 내림차순(▼)부터 : 목록이 원래 빠른 순이라 첫 클릭에 바로 순서가 바뀌어 보이게
  const changeSort = (key) => {
    if (sortKey === key) {
      setSortAsc(!sortAsc);
    } else {
      setSortKey(key);
      setSortAsc(false);
    }
  };

  // 칸 제목 옆 화살표 : 정렬할 수 있는 칸에는 처음부터 항상 화살표 하나가 보임
  // 정렬 중인 칸 : 지금 방향대로 ▲(오름차순) 또는 ▼(내림차순)
  // 나머지 칸 : ▲ (누르면 그 칸으로 정렬할 수 있다는 표시)
  // fontSize 0.8em : 칸 제목 글자보다 조금 작게
  const arrow = (key) => {
    if (sortKey === key && !sortAsc) {
      return <span style={{ fontSize: "0.8em" }}>▼</span>;
    }
    return <span style={{ fontSize: "0.8em" }}>▲</span>;
  };

  // 화면에 보여줄 목록 : 걸러진 목록(visibleList)을 복사해서 정렬
  // [...배열] : 배열을 복사 (원본 visibleList 는 그대로 두고 복사본만 정렬)
  const sortedList = [...visibleList];
  sortedList.sort((a, b) => {
    // a[sortKey] : sortKey 가 "documentNo" 면 a.documentNo 와 같음 (칸 이름을 변수로 받아서 꺼냄)
    // 세 칸 모두 글자("OUT-20260902-001", "2026-09-02T10:00:00")라서 글자 순 비교 = 번호 순 · 날짜 순
    // localeCompare : a 가 앞이면 음수, 같으면 0, 뒤면 양수 (자바 compareTo 와 같음)
    const result = a[sortKey].localeCompare(b[sortKey]);
    // 내림차순이면 부호를 뒤집어서 순서를 반대로
    return sortAsc ? result : -result;
  });

  const { paged, startNo, pager } = usePaging(sortedList);

  return (
    <>
      <PageTitle title="출고문서" path="홈 > 출고관리 > 출고문서" />

       {creating ? (
        <OutboundCreateForm
          onCancel={() => setCreating(false)}
          onDone={() => {
            setCreating(false);
            getList();
          }}
        />
      ) : detail === null ? (
        <>
          {/* ── 목록 ── */}
          <form className="search" onSubmit={handleSearch}>
            <label>화주</label>
            <select name="tenantId" defaultValue="">
              <option value="">전체</option>
              {tenants.map((t) => (
                <option key={t.tenantId} value={t.tenantId}>
                  {t.tenantName}
                </option>
              ))}
            </select>
            <label>출고요청일</label>
            <input type="date" name="from" /> ~ <input type="date" name="to" />
            <label>배송지명</label>
            <input type="text" name="partnerName" />
            <label>주문번호</label>
            <input type="text" name="documentNo" defaultValue="OUT-2026" />
            <label>상태</label>
            <select name="status">
              <option value="">전체</option>
              <option value="WAITING">출고예정</option>
              <option value="ALLOCATED">출고할당</option>
              <option value="PICKING">재고피킹</option>
              <option value="SHIPPED">출고완료</option>
              <option value="CANCELED">취소</option>
            </select>
            <input type="submit" className="btn primary" value="조회" />
            <button type="button" className="btn" onClick={handleReset}>
              초기화
            </button>
          </form>

          <GridTitle title="출고문서 목록" desc={`총 ${visibleList.length}건`}>
             <button className="btn" onClick={() => setCreating(true)}>신규</button>
             <label className="btn">
              엑셀 등록
              <input type="file" accept=".xlsx" hidden onChange={uploadExcel} />
            </label>
          </GridTitle>
          <table className="grid">
            <thead>
              <tr>
                <th>No</th>
                {/* 정렬 가능한 칸 : 클릭하면 changeSort, 옆에 화살표 표시 (cursor pointer : 마우스를 올리면 손가락 모양) */}
                <th style={{ cursor: "pointer" }} onClick={() => changeSort("documentNo")}>
                  주문번호 {arrow("documentNo")}
                </th>
                <th>화주</th>
                <th>배송지명</th>
                <th>배송지 주소</th>
                <th>연락처</th>
                <th style={{ cursor: "pointer" }} onClick={() => changeSort("expectedAt")}>
                  출고요청일 {arrow("expectedAt")}
                </th>
                <th>출처</th>
                <th>품목수</th>
                <th>예정수량 합계</th>
                <th>상태</th>
                <th style={{ cursor: "pointer" }} onClick={() => changeSort("createdAt")}>
                  등록일시 {arrow("createdAt")}
                </th>
                <th>관리</th>
              </tr>
            </thead>
            <tbody>
              {paged.map((outbound, index) => (
                <tr
                  key={outbound.documentId}
                  onClick={() => selectDocument(outbound.documentId)}
                >
                  <td>{startNo + index}</td>
                  <td>{outbound.documentNo}</td>
                  {/* [ED-61] 화주 */}
                  <td>{outbound.tenantName}</td>
                  <td>{outbound.partnerName}</td>
                  <td>{outbound.shipAddress}</td>
                  <td>{outbound.shipContact}</td>
                  <td>{outbound.expectedAt.replace("T", " ")}</td>
                  {/* [ED-61] 출처 (WMS 직접 등록 / 화주요청) */}
                  <td>{SOURCE_NAME[outbound.source]}</td>
                  {/* 품목수 · 예정수량 합계 (입고 목록과 같은 방식) */}
                  <td className="num">{outbound.itemCount}</td>
                  {/* toLocaleString : 1200 → "1,200" 처럼 천 단위 쉼표 */}
                  <td className="num">{outbound.totalExpectedQty.toLocaleString()}</td>
                  <td>{STATUS_NAME[outbound.status]}</td>
                  {/* 등록일시 : "2026-10-12T09:30:15" → T 를 공백으로 바꾸고 앞 16글자만 → "2026-10-12 09:30" */}
                  <td>{outbound.createdAt.replace("T", " ").substring(0, 16)}</td>
                  <td>
                    {outbound.status === "WAITING" ||
                    outbound.status === "ALLOCATED" ? (
                      // height auto : 버튼 기본 높이(24px) 대신 글자 높이만큼만 → "-" 들어간 줄과 줄 높이가 같아짐
                      <button
                        className="btn danger plain"
                        style={{ height: "auto" }}
                        onClick={(e) => cancelOrder(e, outbound)}
                      >
                        취소
                      </button>
                    ) : (
                      "-"
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {pager}
        </>
      ) : (
        <>
          {/* ── 상세: 헤더 + 주문 품목 ── */}
          <GridTitle title="출고 정보" desc={detail.documentNo} />
          <DocumentHeader
            type="OUTBOUND"
            doc={detail}
            statusName={STATUS_NAME[detail.status]}
          />

          <div className="detail-bar">
            <button className="btn" onClick={goBack}>
              목록
            </button>
            {(detail.status === "WAITING" || detail.status === "ALLOCATED") && (
              <button className="btn primary" onClick={goAllocation}>
                출고지시
              </button>
            )}
            {(detail.status === "ALLOCATED" ||
              detail.status === "PICKING" ||
              detail.status === "SHIPPED") && (
              <button className="btn" onClick={goPicking}>
                피킹리스트
              </button>
            )}
          </div>

          <GridTitle title="주문 품목" desc={`총 ${detail.items.length}건`} />
          <table className="grid">
            <thead>
              <tr>
                <th>No</th>
                <th>품목코드</th>
                <th>품목명</th>
                <th>주문수량</th>
                <th>할당수량</th>
              </tr>
            </thead>
            <tbody>
              {detail.items.map((item, index) => (
                <tr key={item.documentItemId}>
                  <td>{index + 1}</td>
                  <td>{item.productCode}</td>
                  <td>{item.productName}</td>
                  <td>{item.expectedQty}</td>
                  <td>{item.allocatedQty}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}
    </>
  );
}