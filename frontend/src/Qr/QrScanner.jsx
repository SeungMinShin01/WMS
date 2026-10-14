import { useEffect, useId, useRef, useState } from "react";
import { Html5Qrcode } from "html5-qrcode";

// 휴대폰 카메라 QR 스캐너 (뒷면 카메라)
// - HTTPS 에서만 카메라가 열림 (배포 사이트 OK, 로컬은 localhost 만)
// - 같은 QR 이 카메라에 계속 비쳐도 1.5초 안 같은 값은 한 번만 보냄
export default function QrScanner({ onScan }) {
  const elementId = "qr" + useId().replace(/[^a-zA-Z0-9]/g, "");
  const onScanRef = useRef(onScan);
  const lastRef = useRef({ text: "", time: 0 });
  const [error, setError] = useState("");

  // 부모가 새 함수를 넘겨도 카메라를 다시 켜지 않게 ref 로 보관
  useEffect(() => {
    onScanRef.current = onScan;
  });

  useEffect(() => {
    const scanner = new Html5Qrcode(elementId);
    const started = scanner
      .start(
        { facingMode: "environment" },
        { fps: 10, qrbox: 220 },
        (text) => {
          const now = Date.now();
          const value = text.trim();
          if (
            value === lastRef.current.text &&
            now - lastRef.current.time < 1500
          )
            return;
          lastRef.current = { text: value, time: now };
          onScanRef.current(value);
        },
        () => {}, // 프레임마다 "못 찾음" — 무시
      )
      .catch(() =>
        setError(
          "카메라를 열 수 없습니다. 권한을 허용했는지 확인하거나 직접 입력하세요.",
        ),
      );

    // 화면을 나가면 카메라 끄기 (켜지는 중이면 켜진 뒤 끔)
    return () => {
      started
        .then(() => scanner.stop())
        .then(() => scanner.clear())
        .catch(() => {});
    };
  }, [elementId]);

  return (
    <div>
      <div id={elementId} className="m-camera" />
      {error && <p className="m-msg error">{error}</p>}
    </div>
  );
}
