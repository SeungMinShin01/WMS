export default function InboundHeader(props) {
  const doc = props.doc;
  return (
    <table className="form head">
      <tbody>
        <tr>
          <th>입고번호</th>
          <td>{doc.documentNo}</td>
          <th>화주명</th>
          <td>{doc.partnerName}</td>
          <th>상태</th>
          <td>{props.statusName}</td>
        </tr>
        <tr>
          <th>입고예정일</th>
          <td>{doc.expectedAt}</td>
          <th>품목수</th>
          <td>{doc.items.length}</td>
          <th>입고확정일시</th>
          <td>{doc.completedAt}</td>
        </tr>
      </tbody>
    </table>
  );
}
