import { Toaster } from "@/components/ui/sonner";
import { TooltipProvider } from "@/components/ui/tooltip";
import NotFound from "@/pages/NotFound";
import { Route, Switch } from "wouter";
import ErrorBoundary from "./components/ErrorBoundary";
import DashboardLayout from "./components/DashboardLayout";
import { ThemeProvider } from "./contexts/ThemeContext";
import Home from "./pages/Home";
import { ActivityPage, FilesPage, GitHubPage, MemoryPage, ProjectsPage, SettingsPage, TasksPage, ToolsPage } from "./pages/WorkspacePanels";

function Router() {
  // make sure to consider if you need authentication for certain routes
  return (
    <DashboardLayout><Switch>
      <Route path={"/"} component={Home} />
      <Route path={"/tasks"} component={TasksPage} />
      <Route path={"/projects"} component={ProjectsPage} />
      <Route path={"/memory"} component={MemoryPage} />
      <Route path={"/tools"} component={ToolsPage} />
      <Route path={"/files"} component={FilesPage} />
      <Route path={"/github"} component={GitHubPage} />
      <Route path={"/activity"} component={ActivityPage} />
      <Route path={"/settings"} component={SettingsPage} />
      <Route path={"/404"} component={NotFound} />
      <Route component={NotFound} />
    </Switch></DashboardLayout>
  );
}

// NOTE: About Theme
// - First choose a default theme according to your design style (dark or light bg), than change color palette in index.css
//   to keep consistent foreground/background color across components
// - If you want to make theme switchable, pass `switchable` ThemeProvider and use `useTheme` hook

function App() {
  return (
    <ErrorBoundary>
      <ThemeProvider
        defaultTheme="dark"
        // switchable
      >
        <TooltipProvider>
          <Toaster />
          <Router />
        </TooltipProvider>
      </ThemeProvider>
    </ErrorBoundary>
  );
}

export default App;
