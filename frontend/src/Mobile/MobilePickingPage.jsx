import { useEffect, useState } from "react";
import axios from "axios";
import { useNavigate, useParams } from "react-router-dom";
import ScanBox from "../Qr/ScanBox";
import { isLocationCode, isLotCode } from "../Qr/codes";

// 진동 (지원하는 휴대폰만)
const buzz = () => navigator.vibrate?.(200);

// 남은 수량 = 할당 수량 - 지금까지 집은 수량
const remainOf = (item) => item.qty - item.pickedQty;

// 피킹 작업: 현재 품목 1개 → ① 칸 QR ② LOT QR ③ 수량 → [적용]
// 칸 비교는 화면 안내용, LOT·수량 최종 판단은 서버 (PUT /wms/pickings/{id}/scan)
export default function MobilePickingPage() {
  const { pickingListId } = useParams();
  const navigate = useNavigate();
  const [detail, setDetail] = useState(null); // { picking, items }
  const [step, setStep] = useState("WAIT_LOC"); // WAIT_LOC → WAIT_LOT → READY
  const [scannedLot, setScannedLot] = useState("");
  const [qty, setQty] = useState("");
  const [message, setMessage] = useState(null); // { type: ok|error|info, text }
  const [saving, setSaving] = useState(false);

  const load = async () => {
    try {
      const res = await axios.get(`/wms/pickings/${pickingListId}`);
      setDetail(res.data);
      return res.data;
    } catch (error) {
      setMessage({
        type: "error",
        text: error.response?.data ?? "피킹 리스트를 불러오지 못했습니다.",
      });
      return null;
    }
  };

  useEffect(() => {
    load();
  }, [pickingListId]);

  // 서버가 집는 순서(seq)대로 보내 줌 → 아직 덜 집은 첫 줄이 지금 품목
  const item = detail?.items.find((i) => !i.done);
  const doneCount = detail ? detail.items.filter((i) => i.done).length : 0;
  const total = detail ? detail.items.length : 0;

  const resetScan = () => {
    setStep("WAIT_LOC");
    setScannedLot("");
    setQty("");
  };

  const fail = (text) => {
    setMessage({ type: "error", text });
    buzz();
  };

  // ① 칸 QR
  const scanLocation = (code) => {
    if (isLotCode(code)) return fail("LOT가 아니라 칸 QR을 먼저 찍으세요.");
    if (!isLocationCode(code)) return fail(`칸 QR이 아닙니다: ${code}`);
    if (code !== item.locationCode)
      return fail(`다른 칸입니다. ${item.locationCode} 로 가세요.`);
    setStep("WAIT_LOT");
    setMessage({ type: "ok", text: `칸 확인 ${code}. LOT QR을 찍으세요.` });
  };

  // ② LOT QR (이 단계에서 다른 칸을 찍으면 ①부터 다시)
  const scanLot = (code) => {
    if (isLocationCode(code)) {
      if (code === item.locationCode)
        return setMessage({
          type: "info",
          text: "이미 확인된 칸입니다. LOT QR을 찍으세요.",
        });
      resetScan();
      return fail(`다른 칸입니다. ${item.locationCode} 로 가세요.`);
    }
    if (!isLotCode(code)) return fail(`LOT QR이 아닙니다: ${code}`);
    if (code !== item.lotCode)
      return fail(`다른 LOT입니다. ${item.lotCode} 를 집으세요.`);
    setScannedLot(code);
    setQty(String(remainOf(item))); // 남은 수량을 기본값으로
    setStep("READY");
    setMessage({
      type: "ok",
      text: "LOT 확인. 수량을 확인하고 적용을 누르세요.",
    });
  };

  // ③ 적용 → 서버가 LOT 재검사 + 수량 조건부 증가, 바뀐 줄 1개를 돌려줌
  const apply = async () => {
    const n = Number(qty);
    const remain = remainOf(item);
    if (!(n >= 1 && n <= remain))
      return fail(`수량은 1 ~ ${remain} 사이로 입력하세요.`);
    setSaving(true);
    try {
      const res = await axios.put(`/wms/pickings/${pickingListId}/scan`, {
        pickingListItemId: item.pickingListItemId,
        lotCode: scannedLot,
        qty: n,
      });
      const fresh = await load();
      const allDone = fresh && fresh.items.every((i) => i.done);
      if (allDone) {
        setMessage({
          type: "ok",
          text: "모든 품목을 집었습니다. 관리자에게 출고 확정을 요청하세요.",
        });
      } else if (res.data.done) {
        setMessage({
          type: "ok",
          text: "이 품목 완료. 다음 품목으로 이동하세요.",
        });
        resetScan();
      } else {
        // 같은 칸에서 이어서 집음 → 칸·LOT 확인 상태 유지
        const left = remainOf(res.data);
        setQty(String(left));
        setMessage({ type: "ok", text: `${n}개 적용. 남은 수량 ${left}개.` });
      }
    } catch (error) {
      fail(error.response?.data ?? "적용하지 못했습니다.");
      resetScan();
      await load(); // 다른 기기가 먼저 집었을 수 있어서 다시 불러옴
    } finally {
      setSaving(false);
    }
  };

  if (!detail) {
    return message ? (
      <p className={"m-msg " + message.type}>{message.text}</p>
    ) : (
      <p className="m-muted">불러오는 중...</p>
    );
  }

  const percent = total === 0 ? 0 : Math.round((doneCount / total) * 100);

  return (
    <>
      <div className="m-row">
        <button className="m-btn small" onClick={() => navigate("/m/pickings")}>
          ← 목록
        </button>
        <span className="m-muted">
          {detail.picking.pickingNo} · {detail.picking.documentNo}
        </span>
      </div>
      <div className="m-bar">
        <div style={{ width: percent + "%" }} />
      </div>
      <div className="m-muted">
        {doneCount} / {total} 품목 완료
      </div>

      {message && <p className={"m-msg " + message.type}>{message.text}</p>}

      {!item ? (
        <div className="m-card">
          <b>피킹 완료</b>
          <p className="m-muted">출고 확정은 관리자 화면에서 진행합니다.</p>
          <button className="m-btn" onClick={() => navigate("/m/pickings")}>
            목록으로
          </button>
        </div>
      ) : (
        <div className="m-card">
          <div className="m-muted">
            {item.seq}번 품목 · {item.productCode}
          </div>
          <div className="m-big">{item.locationCode}</div>
          <div>
            <b>{item.productName}</b>
          </div>
          <div className="m-muted">LOT {item.lotCode}</div>
          <div className="m-row">
            <span>남은 수량</span>
            <b>
              {remainOf(item)} / {item.qty}
            </b>
          </div>

          <div className="m-step">
            <span className={step === "WAIT_LOC" ? "now" : "done"}>① 칸</span>
            <span
              className={
                step === "WAIT_LOT" ? "now" : step === "READY" ? "done" : ""
              }
            >
              ② LOT
            </span>
            <span className={step === "READY" ? "now" : ""}>③ 수량</span>
          </div>

          <ScanBox
            label="① 칸 QR"
            placeholder="A-01-01"
            onScan={scanLocation}
            disabled={step !== "WAIT_LOC"}
          />
          <ScanBox
            label="② LOT QR"
            placeholder="LOT-…"
            onScan={scanLot}
            disabled={step !== "WAIT_LOT" && step !== "READY"}
          />

          <p className="m-muted">③ 수량</p>
          <input
            className="m-input"
            type="number"
            inputMode="numeric"
            min="1"
            max={remainOf(item)}
            value={qty}
            onChange={(e) => setQty(e.target.value)}
            disabled={step !== "READY"}
          />
          <p />
          <button
            className="m-btn"
            onClick={apply}
            disabled={step !== "READY" || saving}
          >
            {saving ? "적용 중..." : "적용"}
          </button>
          {step !== "WAIT_LOC" && (
            <p>
              <button className="m-btn small" onClick={resetScan}>
                처음부터 다시 찍기
              </button>
            </p>
          )}
        </div>
      )}
    </>
  );
}
