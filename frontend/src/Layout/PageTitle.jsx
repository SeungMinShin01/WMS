// 사용: <PageTitle title="입고예정" path="홈 > 입고관리 > 입고예정" />
export default function PageTitle(props) {
  return (
    <div className="page-title">
      <h2>{props.title}</h2>
      <span className="path">{props.path}</span>
    </div>
  );
}
