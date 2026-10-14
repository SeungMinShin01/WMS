import { useEffect, useState } from "react";
import axios from "axios";
import PageTitle from "../../Layout/PageTitle";
import GridTitle from "../../Layout/GridTitle";
import QrLabel from "../../Qr/QrLabel";

// 관리자: QR 라벨 인쇄 (칸 · LOT · 모바일 로그인 주소)
export default function LabelPage() {
  const [tab, setTab] = useState("LOCATION");
  const [locations, setLocations] = useState([]);
  const [lots, setLots] = useState([]);
  const [keyword, setKeyword] = useState("");

  // 칸: 사용 중인 로케이션만
  const loadLocations = async () => {
    const res = await axios.get("/wms/locations");
    setLocations(res.data.filter((l) => l.isActive));
  };

  // LOT: 재고에 있는 LOT 만 (같은 LOT 가 여러 칸에 있으면 하나로)
  const loadLots = async () => {
    const res = await axios.get("/wms/stocks");
    const map = new Map();
    for (const s of res.data) {
      if (!map.has(s.lotCode)) map.set(s.lotCode, s);
    }
    setLots([...map.values()]);
  };

  useEffect(() => {
    loadLocations().catch(() => alert("로케이션 조회 실패"));
    loadLots().catch(() => alert("재고 조회 실패"));
  }, []);

  const word = keyword.trim();
  const visibleLocations = locations.filter((l) =>
    l.locationCode.startsWith(word),
  );
  const visibleLots = lots.filter(
    (s) =>
      word === "" || s.lotCode.includes(word) || s.productName?.includes(word),
  );
  const loginUrl = window.location.origin + "/m/login";

  return (
    <>
      <div className="no-print">
        <PageTitle title="QR 라벨" path="홈 > 관리자 > QR 라벨" />
        <div className="search">
          {/* 탭 버튼은 div 로 감쌈 — .search .btn.primary 의 margin-left:auto(조회 버튼용)가 탭에 적용되지 않게 */}
          <div style={{ marginLeft: "auto" }}>
            <button
              className={"btn" + (tab === "LOCATION" ? " primary" : "")}
              onClick={() => setTab("LOCATION")}
            >
              칸
            </button>{" "}
            <button
              className={"btn" + (tab === "LOT" ? " primary" : "")}
              onClick={() => setTab("LOT")}
            >
              LOT
            </button>{" "}
            <button
              className={"btn" + (tab === "LOGIN" ? " primary" : "")}
              onClick={() => setTab("LOGIN")}
            >
              모바일 로그인 주소
            </button>
          </div>
          {tab !== "LOGIN" && (
            <>
              <label>검색</label>
              <input
                value={keyword}
                onChange={(e) => setKeyword(e.target.value)}
                placeholder={
                  tab === "LOCATION" ? "구역 (예: A, A-01)" : "LOT 코드·품목명"
                }
              />
            </>
          )}
        </div>
        <GridTitle
          title="인쇄 미리보기"
          desc={
            tab === "LOCATION"
              ? `칸 ${visibleLocations.length}장`
              : tab === "LOT"
                ? `LOT ${visibleLots.length}장`
                : "1장"
          }
        >
          <button className="btn primary" onClick={() => window.print()}>
            인쇄
          </button>
        </GridTitle>
      </div>

      <div className="qr-sheet">
        {tab === "LOCATION" &&
          visibleLocations.map((l) => (
            <QrLabel key={l.locationId} code={l.locationCode} />
          ))}
        {tab === "LOT" &&
          visibleLots.map((s) => (
            <QrLabel
              key={s.lotCode}
              code={s.lotCode}
              title={s.productName}
              sub={"소비기한 " + (s.expiryDate ?? "-")}
            />
          ))}
        {tab === "LOGIN" && (
          <QrLabel
            code={loginUrl}
            title="WMS 모바일 피킹"
            sub="휴대폰 카메라로 찍으세요"
          />
        )}
      </div>
    </>
  );
}
