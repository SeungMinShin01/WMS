import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      // 개발 중 /wms/... 요청만 8080 스프링으로 넘긴다
      "/wms": "http://localhost:8080",
    },
  },
});
