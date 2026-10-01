// 입고·출고 상세 공통 헤더
// props.type       : "INBOUND" 또는 "OUTBOUND" (칸 이름만 달라짐)
// props.doc        : 상세 응답 (documentNo, partnerName, expectedAt, completedAt, items)
// props.statusName : 화면에 보여줄 한글 상태
const LABELS = {
  INBOUND: {
    no: "입고번호",
    partner: "화주명",
    expected: "입고예정일",
    completed: "입고확정일시",
  },
  OUTBOUND: {
    no: "주문번호",
    partner: "배송지명",
    expected: "출고요청일",
    completed: "출고확정일시",
  },
};

// "2026-10-01T10:00:00" → "2026-10-01 10:00:00", 값이 없으면 "-"
const formatDateTime = (value) => (value ? value.replace("T", " ") : "-");

export default function DocumentHeader(props) {
  const doc = props.doc;
  const label = LABELS[props.type];
  return (
    <table className="form head">
      <tbody>
        <tr>
          <th>{label.no}</th>
          <td>{doc.documentNo}</td>
          <th>{label.partner}</th>
          <td>{doc.partnerName}</td>
          <th>상태</th>
          <td>{props.statusName}</td>
        </tr>
        <tr>
          <th>{label.expected}</th>
          <td>{formatDateTime(doc.expectedAt)}</td>
          <th>품목수</th>
          <td>{doc.items.length}</td>
          <th>{label.completed}</th>
          <td>{formatDateTime(doc.completedAt)}</td>
        </tr>
      </tbody>
    </table>
  );
}
