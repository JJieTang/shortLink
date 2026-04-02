import type { Config } from "tailwindcss";

export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        ink: "#12202b",
        mist: "#f5f2eb",
        sand: "#e3d5bf",
        pine: "#37514a",
        ember: "#c5664a",
        gold: "#d5a741",
      },
      boxShadow: {
        shell: "0 24px 80px rgba(18, 32, 43, 0.16)",
      },
      fontFamily: {
        sans: ["Avenir Next", "Helvetica Neue", "Segoe UI", "sans-serif"],
        mono: ["Azeret Mono", "SFMono-Regular", "monospace"],
      },
    },
  },
  plugins: [],
} satisfies Config;
