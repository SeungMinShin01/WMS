import PageTitle from "../../Layout/PageTitle";
import GridTitle from "../../Layout/GridTitle";
import InboundHeader from "./InboundHeader";
import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import axios from "axios";

const 상태명 = {
  WAITING: "입고예정",
  INSPECTED: "검수완료",
  COMPLETED: "입고완료",
  CANCELED: "취소",
};

// 05 물품적재 — 담당: 조현우
export default function PutawayPage(props) {
  const { documentId } = useParams(); // 입고검수 상세에서 [적재하기]로 왔으면 문서 번호가 있음
  const navigate = useNavigate();

  const [inbounds, setInbounds] = useState([]); // ED-10 입고 문서 전체
  const [조건, set조건] = useState({
    from: "",
    to: "",
    partnerName: "",
    status: "INSPECTED,COMPLETED",
  }); // 조회 조건
  const [selected, setSelected] = useState(null); // ED-13 선택한 문서 (items 포함)
  const [results, setResults] = useState([]); // ED-15 검수 결과 (= 적치할 줄)
  const [locations, setLocations] = useState({}); // { detailId: 적치할 locationId }
  const [로케이션목록, set로케이션목록] = useState([]); // 선택 가능한 로케이션 (사용 중인 칸)
  const [focusDetailId, setFocusDetailId] = useState(null); // 추천을 적용할 적치 줄
  const [recommend, setRecommend] = useState([]); // 추천 적치칸
  const [mixLot, setMixLot] = useState(false); // 혼용적재 (같은 품목 · 다른 LOT 허용)

  // ED-10 목록
  const 목록조회 = async () => {
    const response = await axios.get("/wms/inbounds");
    setInbounds(response.data);
  };

  // 적치 로케이션 선택 목록
  const 로케이션조회 = async () => {
    const response = await axios.get("/wms/inspections/locations");
    set로케이션목록(response.data);
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
    setLocations({});
    setRecommend([]);
    setFocusDetailId(null);
  };

  // 저장 후 선택 문서 상태 다시 받기
  const 문서새로고침 = async () => {
    const response = await axios.get(`/wms/inbounds/${selected.documentId}`);
    setSelected(response.data);
    결과조회(selected.documentId);
    목록조회();
  };

  // 조회 버튼 - 입력한 조건 저장 + 목록 다시 불러오기
  const 조회 = (event) => {
    event.preventDefault();
    const form = new FormData(event.target);
    set조건({
      from: form.get("from"),
      to: form.get("to"),
      partnerName: form.get("partnerName").trim(),
      status: form.get("status"),
    });
    목록조회();
  };

  useEffect(() => {
    목록조회();
    로케이션조회();
    if (documentId) 문서선택(Number(documentId)); // 주소에 문서 번호가 있으면 바로 상세
  }, []);

  // 상세 → 목록으로 (주소도 목록 주소로)
  const 뒤로가기 = () => {
    setSelected(null);
    navigate("/inbounds/putaway");
  };

  // 조회 조건에 맞는 문서만
  const 보여줄목록 = inbounds.filter(
    (d) =>
      조건.status.split(",").includes(d.status) &&
      (조건.from === "" || d.expectedAt >= 조건.from) &&
      (조건.to === "" || d.expectedAt <= 조건.to) &&
      (조건.partnerName === "" || d.partnerName.includes(조건.partnerName)),
  );

  const 적재수 = results.filter((r) => r.locationCode !== null).length;

  // ED-16 적치 저장 — 로케이션을 고른 줄만 1건씩 PUT. 마지막 줄까지 적재되면 서버가 자동 입고완료 처리
  const 적치저장 = async () => {
    const 대상 = results.filter(
      (r) => r.locationCode === null && locations[r.detailId],
    );
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
    alert(
      `적치 저장 ${대상.length - 실패메시지.length}건` +
        (실패메시지.length > 0 ? `\n실패\n${실패메시지.join("\n")}` : ""),
    );
    setLocations({});
    setRecommend([]);
    setFocusDetailId(null);
    문서새로고침();
  };

  // 적치 추천 조회 — mixLot 값을 같이 보냄
  const 추천조회 = async (detailId, mix) => {
    setFocusDetailId(detailId);
    try {
      const response = await axios.get(
        `/wms/inspections/recommend/${detailId}`,
        { params: { mixLot: mix } },
      );
      setRecommend(response.data);
    } catch (error) {
      setRecommend([]); // 추천 실패해도 드롭다운으로 적치 가능
    }
  };

  const focusResult = results.find((r) => r.detailId === focusDetailId);

  // 추천칸 [적용] → 선택한 적치 줄의 로케이션을 그 칸으로 바꿈 (저장은 [적치 저장])
  const 추천적용 = (locationId) => {
    if (focusResult === undefined) {
      alert("적치 지정에서 줄을 먼저 선택하세요.");
      return;
    }
    if (focusResult.locationCode !== null) {
      alert("이미 적재된 줄입니다.");
      return;
    }
    setLocations({ ...locations, [focusDetailId]: locationId });
  };

  return (
    <>
      <PageTitle title="물품적재" path="홈 > 입고관리 > 물품적재" />

      {selected === null ? (
        <>
          {/* ── 목록: 검수완료·입고완료 ── */}
          <form className="search" onSubmit={조회}>
            <label>입고예정일</label>
            <input type="date" name="from" /> ~ <input type="date" name="to" />
            <label>화주명</label>
            <input type="text" name="partnerName" />
            <label>상태</label>
            <select name="status" defaultValue="INSPECTED,COMPLETED">
              <option value="INSPECTED,COMPLETED">
                전체 (검수완료·입고완료)
              </option>
              <option value="INSPECTED">검수완료 (적재 대기)</option>
              <option value="COMPLETED">입고완료</option>
            </select>
            <input type="submit" className="btn primary" value="조회" />
          </form>

          <GridTitle
            title="적재 대상 입고문서"
            desc={`총 ${보여줄목록.length}건`}
          />
          <table className="grid">
            <thead>
              <tr>
                <th>No</th>
                <th>입고번호</th>
                <th>화주명</th>
                <th>입고예정일</th>
                <th>품목수</th>
                <th>예정수량</th>
                <th>상태</th>
                <th>입고확정일시</th>
              </tr>
            </thead>
            <tbody>
              {보여줄목록.map((inbound, index) => (
                <tr
                  key={inbound.documentId}
                  onClick={() => 문서선택(inbound.documentId)}
                >
                  <td>{index + 1}</td>
                  <td>{inbound.documentNo}</td>
                  <td className="left">{inbound.partnerName}</td>
                  <td>{inbound.expectedAt}</td>
                  <td className="num">{inbound.itemCount}</td>
                  <td className="num">
                    {inbound.totalExpectedQty.toLocaleString()}
                  </td>
                  <td>{상태명[inbound.status]}</td>
                  <td>{inbound.completedAt}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      ) : (
        <>
          {/* ── 상세: 헤더 + 좌 적치지정 / 우 추천 ── */}

          <GridTitle title="입고 정보" desc={selected.documentNo} />
          <InboundHeader doc={selected} statusName={상태명[selected.status]} />
          <br />
          <div className="detail-bar">
            <button className="btn" onClick={뒤로가기}>
              목록
            </button>
          </div>

          <div className="two-col">
            {/* ── 왼쪽: 적치 지정 ── */}
            <div className="col-left">
              <GridTitle
                title="적치 지정"
                desc={`적재 ${적재수} / 검수 ${results.length}건`}
              >
                {selected.status === "INSPECTED" && (
                  <button className="btn primary" onClick={적치저장}>
                    적치 저장
                  </button>
                )}
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
                    <tr>
                      <td colSpan={4}>검수 결과가 없습니다.</td>
                    </tr>
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
                            r.locationCode // 이미 적재됨
                          ) : (
                            <select
                              value={locations[r.detailId] ?? ""}
                              onClick={(e) => e.stopPropagation()}
                              onChange={(e) =>
                                setLocations({
                                  ...locations,
                                  [r.detailId]: e.target.value,
                                })
                              }
                            >
                              <option value="">선택</option>
                              {로케이션목록.map((loc) => (
                                <option
                                  key={loc.locationId}
                                  value={loc.locationId}
                                >
                                  {loc.locationCode}
                                </option>
                              ))}
                            </select>
                          )}
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>

            {/* ── 오른쪽: 추천 적치칸 ── */}
            <div className="col-right">
              <GridTitle
                title="추천 적치칸"
                desc={
                  focusResult
                    ? `${focusResult.productName} · ${focusResult.qty.toLocaleString()} BOX`
                    : "왼쪽에서 적치 줄을 선택하세요"
                }
              >
                <label>
                  <input
                    type="checkbox"
                    checked={mixLot}
                    onChange={(e) => {
                      setMixLot(e.target.checked);
                      if (focusDetailId !== null)
                        추천조회(focusDetailId, e.target.checked);
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
                      <td className="num">
                        {rec.freeQty === null
                          ? "제한 없음"
                          : rec.freeQty.toLocaleString()}
                      </td>
                      <td>{rec.fits ? "가능" : "부족"}</td>
                      <td>
                        <button
                          className="btn"
                          onClick={() => 추천적용(rec.locationId)}
                        >
                          적용
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
              <p className="hint">
                ※ 같은 LOT 칸 → (혼용적재 시) 같은 품목 다른 LOT 칸 → 다른 품목
                잔량 칸 → 빈 칸 순으로 추천합니다. 모든 줄이 적재되면 자동으로
                입고완료 처리됩니다.
              </p>
            </div>
          </div>
        </>
      )}
    </>
  );
}
