import { useEffect, useState } from "react";

// 목록 프론트 페이징 — 서버는 전체를 주고, 화면에서 20개씩 잘라서 보여줌
// 사용법: const { paged, startNo, pager } = usePaging(sorted);
//   paged   : 지금 페이지의 줄들 → 표의 map 은 이걸로
//   startNo : 지금 페이지 첫 줄의 번호 → No 칸 = startNo + index (2페이지면 21, 22, ...)
//   pager   : 표 아래에 넣을 페이지 버튼 → {pager}
export default function usePaging(list, size = 20) {
  const [page, setPage] = useState(1); // 지금 페이지 (1부터)

  // 조회 조건으로 목록 개수가 바뀌면 1페이지로 (정렬은 개수가 안 바뀌어서 그대로 유지)
  useEffect(() => {
    setPage(1);
  }, [list.length]);

  // 전체 페이지 수 : 45개면 ceil(45 / 20) = 3페이지, 0개여도 최소 1페이지
  const totalPages = Math.max(1, Math.ceil(list.length / size));
  // 혹시 지금 페이지가 전체보다 크면 마지막 페이지로
  const current = Math.min(page, totalPages);

  // 자르기 : 2페이지면 20번째부터 40번째 앞까지 (slice 는 원본을 안 바꾸고 복사본을 돌려줌)
  const start = (current - 1) * size;
  const paged = list.slice(start, start + size);

  // 번호 버튼은 10개씩 묶어서 (1~10, 11~20 ...) — 페이지가 많아도 버튼이 끝없이 늘지 않게
  const groupStart = Math.floor((current - 1) / 10) * 10 + 1;
  const groupEnd = Math.min(groupStart + 9, totalPages);
  const numbers = [];
  for (let n = groupStart; n <= groupEnd; n++) {
    numbers.push(n);
  }

  // 1페이지뿐이면 버튼을 안 보여줌
  const pager =
    totalPages <= 1 ? null : (
      <div className="pager">
        <button className="btn" disabled={current === 1} onClick={() => setPage(1)}>«</button>
        <button className="btn" disabled={current === 1} onClick={() => setPage(current - 1)}>‹</button>
        {numbers.map((n) => (
          <button
            key={n}
            className={n === current ? "btn primary" : "btn"}
            onClick={() => setPage(n)}
          >
            {n}
          </button>
        ))}
        <button className="btn" disabled={current === totalPages} onClick={() => setPage(current + 1)}>›</button>
        <button className="btn" disabled={current === totalPages} onClick={() => setPage(totalPages)}>»</button>
      </div>
    );

  return { paged, startNo: start + 1, pager };
}