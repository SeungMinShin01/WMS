import PageTitle from "../../Layout/PageTitle";
import GridTitle from "../../Layout/GridTitle";
import { useEffect, useState } from "react";
import axios from "axios";

const 상태명= {WAITING: "입고예정", INSPECTED: "검수완료", COMPLETED: "입고완료", CANCELED: "취소"};

const 임시로케이션 = [
  { locationId: 1, locationCode: "A-01-01" },
  { locationId: 2, locationCode: "A-01-02" },
  { locationId: 3, locationCode: "A-02-01" },
  { locationId: 4, locationCode: "B-01-01" },
  { locationId: 5, locationCode: "B-01-02" },
];


// 04 입고검수 — 담당: 조현우
export default function InspectionPage(props) {
  const [inbounds, setInbounds] = useState([]);                // ED-10 검수 대상 목록
  const [status, setStatus] = useState("WAITING,INSPECTED");   // 상태 필터
  const [selected, setSelected] = useState(null);              // ED-13 선택한 문서 (items 포함)
  const [results, setResults] = useState([]);                  // ED-15 검수 결과
  const [qtys, setQtys] = useState({});                        // { documentItemId: 입력 수량 }
  const [memos, setMemos] = useState({});                      // { documentItemId: 비고 } — DB 컬럼 없음, 화면만
  const [locations, setLocations] = useState({});              // { detailId: locationId }
  const [focusDetailId, setFocusDetailId] = useState(null);    // 추천칸을 적용할 적치 줄
  const [recommend, setRecommend] = useState([]); 
  const [mixLot, setMixLot] = useState(false);                 // 혼용적재 (같은 품목-다른LOT허용)
  
  // ED-10 목록 → 선택한 상태만 남기기
  const 목록조회 = async () => {
    const response = await axios.get("/wms/inbounds");
    const 허용상태 = status.split(",");
    setInbounds(response.data.filter((inbound) => 허용상태.includes(inbound.status)));
  };

  // ED-15 검수 결과
  const 결과조회 = async (documentId) => {
    const response = await axios.get(`/wms/inspections/${documentId}`);
    setResults(response.data);
  };

  // 목록 줄 클릭 → ED-13 상세 + ED-15 결과
  const 문서선택 = async (documentId) => {
    const response = await axios.get(`/wms/inbounds/${documentId}`);
    setSelected(response.data);
    결과조회(documentId);
    setQtys({});
    setMemos({});
    setLocations({});
    setRecommend([]);
    setFocusDetailId(null);
  };

  // 저장 후 선택 문서 상태 다시 받기 (입력값은 유지)
  const 문서새로고침 = async () => {
    const response = await axios.get(`/wms/inbounds/${selected.documentId}`);
    setSelected(response.data);
    결과조회(selected.documentId);
    목록조회();
  };

  const 조회 = (event) => {
    event.preventDefault();
    목록조회();
  };

  useEffect(() => {
    목록조회();
  }, []);

  // 이미 검수된 품목의 수량 { documentItemId: 수량 } — 있으면 입력칸 대신 숫자로 보여줌 (중복 검수 방지)
  const 검수된수량 = {};
  results.forEach((r) => {
    검수된수량[r.documentItemId] = (검수된수량[r.documentItemId] ?? 0) + r.qty;
  });

  const 실제수량 = (item) => 검수된수량[item.documentItemId] ?? Number(qtys[item.documentItemId] || 0);
  const 예정합계 = selected ? selected.items.reduce((sum, item) => sum + item.expectedQty, 0) : 0;
  const 실제합계 = selected ? selected.items.reduce((sum, item) => sum + 실제수량(item), 0) : 0;

  // [예정수량 일괄 적용] — 아직 검수 안 한 품목만
  const 일괄적용 = () => {
    const next = {};
    selected.items.forEach((item) => {
      if (검수된수량[item.documentItemId] === undefined) next[item.documentItemId] = item.expectedQty;
    });
    setQtys(next);
  };

  // ED-14 검수 저장 — 품목마다 1건씩 POST
  const 검수저장 = async () => {
    const 대상 = selected.items.filter(
      (item) => 검수된수량[item.documentItemId] === undefined && Number(qtys[item.documentItemId]) > 0
    );
    if (대상.length === 0) {
      alert("저장할 검수 수량이 없습니다.");
      return;
    }
    const 실패메시지 = [];
    for (const item of 대상) {
      try {
        await axios.post("/wms/inspections", {
          documentItemId: item.documentItemId,
          qty: Number(qtys[item.documentItemId]),
        });
      } catch (error) {
        실패메시지.push(`${item.productName}: ${error.response?.data ?? "오류"}`);
      }
    }
    alert(`검수 저장 ${대상.length - 실패메시지.length}건` + (실패메시지.length > 0 ? `\n실패\n${실패메시지.join("\n")}` : ""));
    setQtys({});
    문서새로고침();
  };

  // ED-16 적치 저장 — 로케이션을 고른 줄만 1건씩 PUT
  const 적치저장 = async () => {
    const 대상 = results.filter((r) => r.locationCode === null && locations[r.detailId]);
    if (대상.length === 0) {
      alert("적치 로케이션을 선택한 줄이 없습니다.");
      return;
    }
    const 실패메시지 = [];
    for (const r of 대상) {
      try {
        await axios.put("/wms/inspections", {
          detailId: r.detailId,
          locationId: Number(locations[r.detailId]),
        });
      } catch (error) {
        실패메시지.push(`${r.productName}: ${error.response?.data ?? "오류"}`);
      }
    }
    alert(`적치 저장 ${대상.length - 실패메시지.length}건` + (실패메시지.length > 0 ? `\n실패\n${실패메시지.join("\n")}` : ""));
    setLocations({});
    문서새로고침();
  };

  // 추천칸 [적용] → 선택한 적치 줄의 로케이션 채우기 (화면만)
  const 추천적용 = (locationId) => {
    if (focusDetailId === null) {
      alert("적치 지정에서 줄을 먼저 선택하세요.");
      return;
    }
    setLocations({ ...locations, [focusDetailId]: locationId });
  };

  // 적치 추천 조회 — mixLot 값을 같이 보냄
  const 추천조회 = async (detailId, mix) => {
    setFocusDetailId(detailId);
    try {
      const response = await axios.get(`/wms/inspections/recommend/${detailId}`, { params: { mixLot: mix } });
      setRecommend(response.data);
    } catch (error) {
      setRecommend([]);   // 추천 실패해도 드롭다운으로 적치 가능
    }
  };

  const focusResult = results.find((r) => r.detailId === focusDetailId);

  return (
    <>
      <PageTitle title="입고 검수" path="홈 > 입고관리 > 입고검수" />

      <form className="search" onSubmit={조회}>
        <label>입고예정일</label>
        <input type="date" name="from" /> ~ <input type="date" name="to" />
        <label>화주명</label>
        <input type="text" name="partnerName" />
        <label>상태</label>
        <select name="status" value={status} onChange={(e) => setStatus(e.target.value)}>
          <option value="WAITING,INSPECTED">검수 대상 (등록·검수·적치)</option>
          <option value="WAITING">등록</option>
          <option value="INSPECTED">검수완료</option>
        </select>
        <input type="submit" className="btn primary" value="조회" />
      </form>

      {/* ── 검수 대상 목록 (ED-10) ── */}
      <GridTitle title="검수 대상 입고예정" desc={`총 ${inbounds.length}건`} />
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
        <tbody>
          {inbounds.map((inbound, index) => (
            <tr
              key={inbound.documentId}
              onClick={() => 문서선택(inbound.documentId)}
              className={selected && selected.documentId === inbound.documentId ? "on" : ""}
            >
              <td>{index + 1}</td>
              <td>{inbound.documentNo}</td>
              <td className="left">{inbound.partnerName}</td>
              <td>{inbound.expectedAt}</td>
              <td className="num">{inbound.itemCount}</td>
              <td className="num">{inbound.totalExpectedQty.toLocaleString()}</td>
              <td>{상태명[inbound.status]}</td>
              {/* 백엔드에 inspectedQty 없으면 — 로 표시 */}
              <td className="num">{inbound.inspectedQty?.toLocaleString() ?? "—"}</td>
              <td className="left"></td>
            </tr>
          ))}
        </tbody>
      </table>

      {selected === null ? (
        <p className="hint">목록에서 입고예정을 클릭하면 검수 입력이 나옵니다.</p>
      ) : (
        <div className="two-col">
          {/* ── 왼쪽: 검수 입력 (ED-13 + ED-14) ── */}
          <div className="col-left">
            <GridTitle title="검수 입력" desc={`${selected.documentNo} · ${selected.partnerName}`}>
              <button className="btn" onClick={일괄적용}>예정수량 일괄 적용</button>
              <button className="btn primary" onClick={검수저장}>검수 저장</button>
            </GridTitle>
            <table className="grid">
              <thead>
                <tr>
                  <th>품목코드</th>
                  <th>품목명</th>
                  <th>소비기한</th>
                  <th>남은일수</th>
                  <th>예정수량</th>
                  <th>실제 입고수량</th>
                  <th>차이</th>
                  <th>비고</th>
                </tr>
              </thead>
              <tbody>
                {selected.items.map((item) => {
                  const done = 검수된수량[item.documentItemId];   // undefined면 아직 검수 전
                  const 입력 = qtys[item.documentItemId] ?? "";
                  const 보여줄수량 = done ?? 입력;
                  const 차이 = 보여줄수량 === "" ? null : Number(보여줄수량) - item.expectedQty;
                  return (
                    <tr key={item.documentItemId}>
                      <td>{item.productCode}</td>
                      <td className="left">{item.productName}</td>
                      <td>{item.expiryDate}</td>
                      <td className="num">{item.remainingDays}일</td>
                      <td className="num">{item.expectedQty.toLocaleString()}</td>
                      <td className="num">
                        {done !== undefined ? (
                          done.toLocaleString()
                        ) : (
                          <input
                            type="number"
                            min="1"
                            style={{ width: "80px" }}
                            value={입력}
                            onChange={(e) => setQtys({ ...qtys, [item.documentItemId]: e.target.value })}
                          />
                        )}
                      </td>
                      <td className={차이 < 0 ? "num red" : "num"}>
                        {차이 === null || 차이 === 0 ? "—" : 차이.toLocaleString()}
                      </td>
                      <td>
                        <input
                          type="text"
                          value={memos[item.documentItemId] ?? ""}
                          onChange={(e) => setMemos({ ...memos, [item.documentItemId]: e.target.value })}
                        />
                      </td>
                    </tr>
                  );
                })}
                <tr className="sum">
                  <td colSpan={4}>합계</td>
                  <td className="num">{예정합계.toLocaleString()}</td>
                  <td className="num">{실제합계.toLocaleString()}</td>
                  <td className={실제합계 - 예정합계 < 0 ? "num red" : "num"}>
                    {(실제합계 - 예정합계).toLocaleString()}
                  </td>
                  <td></td>
                </tr>
              </tbody>
            </table>
          </div>

          {/* ── 오른쪽: 적치 지정 (ED-15 + ED-16) + 추천 적치칸 (화면만) ── */}
          <div className="col-right">
            <GridTitle title="적치 지정" desc={`검수 ${results.length}건`}>
              <button className="btn primary" onClick={적치저장}>적치 저장</button>
              <button className="btn" disabled title="입고확정 API 준비 전">입고확정</button>
            </GridTitle>
            <table className="grid">
              <thead>
                <tr>
                  <th>품목명</th>
                  <th>LOT</th>
                  <th>수량</th>
                  <th>적치 로케이션</th>
                </tr>
              </thead>
              <tbody>
                {results.length === 0 ? (
                  <tr><td colSpan={4}>검수 결과가 없습니다.</td></tr>
                ) : (
                  results.map((r) => (
                    <tr
                      key={r.detailId}
                      onClick={() => 추천조회(r.detailId, mixLot)}
                      className={focusDetailId === r.detailId ? "on" : ""}
                    >
                      <td className="left">{r.productName}</td>
                      <td>{r.lotCode}</td>
                      <td className="num">{r.qty.toLocaleString()}</td>
                      <td>
                        {r.locationCode !== null ? (
                          r.locationCode   // 이미 적재됨
                        ) : (
                          <select
                            value={locations[r.detailId] ?? ""}
                            onChange={(e) => setLocations({ ...locations, [r.detailId]: e.target.value })}
                          >
                            <option value="">선택</option>
                            {임시로케이션.map((loc) => (
                              <option key={loc.locationId} value={loc.locationId}>{loc.locationCode}</option>
                            ))}
                          </select>
                        )}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>

            <GridTitle
              title="추천 적치칸"
              desc={focusResult ? `${focusResult.productName} · ${focusResult.qty.toLocaleString()} BOX` : "적치 줄을 선택하세요"}
            >
              <label>
                <input
                  type="checkbox"
                  checked={mixLot}
                  onChange={(e) => {
                    setMixLot(e.target.checked);
                    if (focusDetailId !== null) 추천조회(focusDetailId, e.target.checked);
                  }}
                />
                혼용적재 (같은 품목 · 다른 LOT 허용)
              </label>
            </GridTitle>
            <table className="grid">
              <thead>
                <tr>
                  <th>순위</th>
                  <th>로케이션</th>
                  <th>추천 사유</th>
                  <th>현재 수량</th>
                  <th>여유</th>
                  <th>전량 적재</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {recommend.map((rec, index) => (
                  <tr key={rec.locationId}>
                    <td>{index + 1}</td>
                    <td>{rec.locationCode}</td>
                    <td className="left">{rec.reason}</td>
                    <td className="num">{rec.currentQty.toLocaleString()}</td>
                    <td className="num">{rec.freeQty === null ? "제한 없음" : rec.freeQty.toLocaleString()}</td>
                    <td>{rec.fits ? "가능" : "부족"}</td>
                    <td>
                      <button className="btn" onClick={() => 추천적용(rec.locationId)}>적용</button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            <p className="hint">
              ※ 같은 LOT 칸 → (혼용적재 시) 같은 품목 다른 LOT 칸 → 다른 품목 잔량 칸 → 빈 칸 순으로 추천합니다.
              혼용적재를 끄면 같은 품목의 다른 LOT가 있는 칸은 제외됩니다. 적치는 전 품목 검수 후 가능합니다.
            </p>
          </div>
        </div>
      )}
    </>
  );
}
