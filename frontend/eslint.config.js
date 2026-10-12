import js from "@eslint/js";
import globals from "globals";
import reactHooks from "eslint-plugin-react-hooks";
import reactRefresh from "eslint-plugin-react-refresh";
import { defineConfig, globalIgnores } from "eslint/config";

export default defineConfig([
  globalIgnores(["dist"]),
  {
    files: ["**/*.{js,jsx}"],
    extends: [
      js.configs.recommended,
      reactHooks.configs.flat.recommended,
      reactRefresh.configs.vite,
    ],
    languageOptions: {
      globals: globals.browser,
      parserOptions: { ecmaFeatures: { jsx: true } },
    },
    rules: {
      // react-hooks 7 새 규칙: 화면 열릴 때 useEffect 안에서 목록 조회(→ setState) 하는 패턴을 오류로 봄
      // 전 화면이 이 패턴이라 지금은 경고로 두고, 데이터 조회 방식을 바꿀 때 함께 정리
      "react-hooks/set-state-in-effect": "warn",
    },
  },
]);
