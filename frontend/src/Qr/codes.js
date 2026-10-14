// QR 코드 형식 (백엔드 LocationEntity.CODE_PATTERN 과 같은 규칙)
// 칸 QR = 로케이션 코드 그대로 (A-01-01), LOT QR = LOT 코드 그대로 (LOT-20261001-PT-001-01)
export const LOCATION_PATTERN = /^[A-Z]-\d{2}-\d{2}$/;

export const isLocationCode = (code) => LOCATION_PATTERN.test(code);
export const isLotCode = (code) => code.startsWith("LOT-");
