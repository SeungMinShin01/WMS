import { useState } from "react";
import QrScanner from "./QrScanner";

// 스캔 입력 박스: [카메라] 켜고 끄기 + 입력칸 (손 스캐너·직접 입력, Enter 로 전송)
// done : 확인된 코드 → 비활성일 때 입력칸에 표시 (비활성이면 카메라도 꺼짐)
export default function ScanBox({ label, placeholder, onScan, disabled, done }) {
  const [camera, setCamera] = useState(false);
  const [text, setText] = useState("");

  const submit = (e) => {
    e.preventDefault();
    const value = text.trim();
    if (value === "") return;
    onScan(value);
    setText("");
  };

  return (
    <div className={"m-scan" + (disabled ? " disabled" : "")}>
      <div className="m-scan-head">
        <b>{label}</b>
        <button
          type="button"
          className="m-btn small"
          disabled={disabled}
          onClick={() => setCamera(!camera)}
        >
          {camera ? "카메라 끄기" : "카메라"}
        </button>
      </div>
      {camera && !disabled && <QrScanner onScan={onScan} />}
      <form onSubmit={submit}>
        <input
          className="m-input"
          value={disabled && done ? done : text}
          onChange={(e) => setText(e.target.value)}
          placeholder={placeholder}
          disabled={disabled}
          autoCapitalize="characters"
          autoComplete="off"
        />
      </form>
    </div>
  );
}