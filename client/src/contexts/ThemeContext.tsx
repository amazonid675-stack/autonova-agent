import { createContext, useContext, useEffect, useState, type ReactNode } from "react";

type Theme = "light" | "dark";

interface ThemeContextType {
  theme: Theme;
  toggleTheme?: () => void;
  switchable: boolean;
}

const ThemeContext = createContext<ThemeContextType | undefined>(undefined);

interface ThemeProviderProps {
  children: ReactNode;
  defaultTheme?: Theme;
  switchable?: boolean;
}

export function ThemeProvider({ children, defaultTheme = "light", switchable = false }: ThemeProviderProps) {
  const [theme, setTheme] = useState<Theme>(() => switchable ? (localStorage.getItem("theme") as Theme) || defaultTheme : defaultTheme);
  useEffect(() => {
    document.documentElement.classList.toggle("dark", theme === "dark");
    if (switchable) localStorage.setItem("theme", theme);
  }, [theme, switchable]);
  const toggleTheme = switchable ? () => setTheme(previous => previous === "light" ? "dark" : "light") : undefined;
  return <ThemeContext.Provider value={{ theme, toggleTheme, switchable }}>{children}</ThemeContext.Provider>;
}

export function useTheme() {
  const context = useContext(ThemeContext);
  if (!context) throw new Error("useTheme must be used within ThemeProvider");
  return context;
}
