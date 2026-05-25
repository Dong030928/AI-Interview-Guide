import { useState } from 'react';
import { motion } from 'framer-motion';
import { Activity, AlertTriangle, FileText } from 'lucide-react';
import MonitorOverview from '../components/monitor/MonitorOverview';
import OperationLogPanel from '../components/monitor/OperationLogPanel';
import AlertRulePanel from '../components/monitor/AlertRulePanel';

type TabKey = 'overview' | 'logs' | 'alerts';

const tabs: { key: TabKey; label: string; icon: React.ComponentType<{ className?: string }> }[] = [
  { key: 'overview', label: '概览', icon: Activity },
  { key: 'logs', label: '操作日志', icon: FileText },
  { key: 'alerts', label: '告警管理', icon: AlertTriangle },
];

export default function MonitorPage() {
  const [activeTab, setActiveTab] = useState<TabKey>('overview');

  return (
    <div className="max-w-6xl mx-auto">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-slate-800 dark:text-white">系统监控</h1>
        <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
          查看系统运行状态、操作日志和告警信息
        </p>
      </div>

      {/* Tab 导航 */}
      <div className="flex gap-1 mb-6 bg-slate-100 dark:bg-slate-800 rounded-lg p-1 w-fit">
        {tabs.map((tab) => (
          <button
            key={tab.key}
            onClick={() => setActiveTab(tab.key)}
            className={`flex items-center gap-2 px-4 py-2 rounded-md text-sm font-medium transition-colors ${
              activeTab === tab.key
                ? 'bg-white dark:bg-slate-700 text-slate-800 dark:text-white shadow-sm'
                : 'text-slate-500 hover:text-slate-700 dark:hover:text-slate-300'
            }`}
          >
            <tab.icon className="w-4 h-4" />
            {tab.label}
          </button>
        ))}
      </div>

      {/* Tab 内容 */}
      <motion.div
        key={activeTab}
        initial={{ opacity: 0, y: 10 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.2 }}
      >
        {activeTab === 'overview' && <MonitorOverview />}
        {activeTab === 'logs' && <OperationLogPanel />}
        {activeTab === 'alerts' && <AlertRulePanel />}
      </motion.div>
    </div>
  );
}
