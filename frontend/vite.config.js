import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// command === "serve" → npm run dev,  command === "build" → npm run build
export default defineConfig(({ command }) => ({
  plugins: [react()],
  // 빌드 결과물은 /wms/ 아래에서 열린다 (나중에 /portal/ 이 따로 붙음)
  // 개발 중에는 / 에서 연다. /wms 를 API 프록시가 쓰고 있어서 겹치지 않게 하려는 것
  base: command === "build" ? "/wms/" : "/",
  server: {
    proxy: {
      // 개발 중 브라우저가 /wms/... 로 부르면 8080 스프링으로 넘긴다
      "/wms": "http://localhost:8080",
    },
  },
}));
