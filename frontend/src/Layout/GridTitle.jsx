// 사용: <GridTitle title="입고예정 목록" desc="총 6건">
//         <button className="btn">신규</button>
//       </GridTitle>
export default function GridTitle(props) {
  return (
    <div className="grid-title">
      <span className="bar"></span>
      <b>{props.title}</b>
      <span className="desc">{props.desc}</span>
      <span className="right">{props.children}</span>
    </div>
  );
}
