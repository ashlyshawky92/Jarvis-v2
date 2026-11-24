export type AssistantState =
  | 'STANDBY'
  | 'LISTENING'
  | 'THINKING'
  | 'EXECUTING'
  | 'SPEAKING'
  | 'OFFLINE'
  | 'ERROR';

export interface PlannedAction {
  actionId: string;
  parameters: Record<string, string>;
  requiresConfirmation: boolean;
  implementedInPhase1: boolean;
  scheduledPhase: number;
}

export interface ChatTurn {
  id: string;
  role: 'user' | 'assistant';
  text: string;
  timestamp: string;
  modelUsed?: string;
  latencyMs?: number;
  plannedAction?: PlannedAction | null;
}

export interface AndroidProjectFile {
  path: string;
  content: string;
  size: number;
}

export interface AndroidProjectPayload {
  projectName: string;
  targetDevice: string;
  fileCount: number;
  files: AndroidProjectFile[];
}

export interface HealthStatus {
  status: string;
  geminiConfigured: boolean;
  defaultModel: string;
  phase: string;
}
