import { QRCodeSVG } from "qrcode.react";
import "./qr.css";

// QR 라벨 한 장: QR 그림 + 아래에 같은 코드 글자 (사람도 읽을 수 있게)
// code : QR 에 들어갈 내용 그대로 (칸 A-01-01, LOT LOT-…, 주소 https://…)
export default function QrLabel({ code, title, sub }) {
  return (
    <div className="qr-label">
      <QRCodeSVG value={code} size={120} level="M" marginSize={2} />
      <div className="qr-code">{code}</div>
      {title && <div className="qr-title">{title}</div>}
      {sub && <div className="qr-sub">{sub}</div>}
    </div>
  );
}
