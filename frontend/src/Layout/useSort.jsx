import { useState } from "react";

// 목록 칸 제목 클릭 정렬 (여러 화면이 같이 쓰는 도구)
// 사용법: const { sorted, th } = useSort(목록);
//   sorted : 정렬된 목록 → 표의 map 은 이걸로
//   th("documentNo", "입고번호") : 클릭하면 정렬되는 칸 제목
// 처음 = 서버 순서 그대로 → 1번 클릭 오름차순(▲) → 한번 더 내림차순(▼) → 또 누르면 오름차순
export default function useSort(list) {
  const [sortKey, setSortKey] = useState(null); // 정렬 중인 칸 이름 (null = 정렬 안 함)
  const [sortAsc, setSortAsc] = useState(true); // true 오름차순 / false 내림차순

  const changeSort = (key) => {
    if (sortKey === key) {
      setSortAsc(!sortAsc); // 같은 칸을 또 누름 → 방향만 뒤집기
    } else {
      setSortKey(key); // 다른 칸 → 그 칸 오름차순부터
      setSortAsc(true);
    }
  };

  // [...list] : 원본은 그대로 두고 복사본만 정렬
  const sorted = [...list];
  if (sortKey !== null) {
    sorted.sort((a, b) => {
      const x = a[sortKey]; // sortKey 가 "documentNo" 면 a.documentNo
      const y = b[sortKey];
      // 빈 값(null)은 방향과 상관없이 항상 맨 아래 (예: 입고확정일시가 없는 문서)
      if (x == null && y == null) return 0;
      if (x == null) return 1;
      if (y == null) return -1;

      let result;
      if (typeof x === "number" && typeof y === "number") {
        result = x - y; // 숫자는 빼기로 비교 (글자로 비교하면 "10" 이 "9" 보다 앞에 옴)
      } else {
        // 글자 비교: "IN-20260901-001", "2026-09-01 10:00" 은 글자 순 = 번호 순·날짜 순
        result = String(x).localeCompare(String(y));
      }
      return sortAsc ? result : -result; // 내림차순이면 부호 뒤집기
    });
  }

  // 정렬 칸 제목: 정렬 중인 칸은 진하게, 나머지는 흐린 ▲
  const th = (key, label) => {
    const active = sortKey === key;
    return (
      <th style={{ cursor: "pointer" }} onClick={() => changeSort(key)}>
        {label}{" "}
        <span style={{ fontSize: "0.8em", opacity: active ? 1 : 0.3 }}>
          {active && !sortAsc ? "▼" : "▲"}
        </span>
      </th>
    );
  };

  return { sorted, th };
}