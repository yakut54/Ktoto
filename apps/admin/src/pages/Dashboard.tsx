import { useStats } from '@/hooks/useAdmin'
import { StatCard } from '@/components/StatCard'
import { Users, MessageSquare, Activity, TrendingUp } from 'lucide-react'
import { formatNumber } from '@/lib/utils'
import {
  AreaChart,
  Area,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
} from 'recharts'

export function DashboardPage() {
  const { data, isLoading, error } = useStats()

  if (isLoading) {
    return (
      <div className="p-8">
        <div className="h-8 w-48 bg-muted animate-pulse rounded mb-2" />
        <div className="grid grid-cols-4 gap-4 mt-6">
          {[...Array(4)].map((_, i) => (
            <div key={i} className="h-28 bg-muted animate-pulse rounded-lg" />
          ))}
        </div>
      </div>
    )
  }

  if (error || !data) {
    return (
      <div className="p-8">
        <p className="text-destructive">Failed to load stats</p>
      </div>
    )
  }

  return (
    <div className="p-8 space-y-8">
      <div>
        <h1 className="text-2xl font-bold">Dashboard</h1>
        <p className="text-muted-foreground text-sm mt-1">Overview of your messenger</p>
      </div>

      {/* Stat cards */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard
          title="Total Users"
          value={formatNumber(data.totalUsers)}
          icon={Users}
        />
        <StatCard
          title="Conversations"
          value={formatNumber(data.totalConversations)}
          icon={MessageSquare}
        />
        <StatCard
          title="Messages Today"
          value={formatNumber(data.messagesToday)}
          icon={TrendingUp}
        />
        <StatCard
          title="Active (24h)"
          value={formatNumber(data.activeUsers24h)}
          icon={Activity}
        />
      </div>

      {/* Charts */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Messages chart */}
        <div className="rounded-lg border bg-card p-6">
          <h2 className="font-semibold mb-1">Messages per day</h2>
          <p className="text-xs text-muted-foreground mb-4">Last 30 days</p>
          <ResponsiveContainer width="100%" height={220}>
            <AreaChart data={data.messagesChart}>
              <defs>
                <linearGradient id="msgGrad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor="hsl(221.2 83.2% 53.3%)" stopOpacity={0.3} />
                  <stop offset="95%" stopColor="hsl(221.2 83.2% 53.3%)" stopOpacity={0} />
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" stroke="hsl(214.3 31.8% 91.4%)" />
              <XAxis
                dataKey="date"
                tickFormatter={(v) => v.slice(5)}
                tick={{ fontSize: 11 }}
                interval={4}
              />
              <YAxis tick={{ fontSize: 11 }} />
              <Tooltip
                labelFormatter={(v) => `Date: ${v}`}
                formatter={(v) => [v, 'Messages']}
              />
              <Area
                type="monotone"
                dataKey="count"
                stroke="hsl(221.2 83.2% 53.3%)"
                fill="url(#msgGrad)"
                strokeWidth={2}
              />
            </AreaChart>
          </ResponsiveContainer>
        </div>

        {/* New users chart */}
        <div className="rounded-lg border bg-card p-6">
          <h2 className="font-semibold mb-1">New users per week</h2>
          <p className="text-xs text-muted-foreground mb-4">Last 12 weeks</p>
          <ResponsiveContainer width="100%" height={220}>
            <BarChart data={data.usersChart}>
              <CartesianGrid strokeDasharray="3 3" stroke="hsl(214.3 31.8% 91.4%)" />
              <XAxis
                dataKey="week"
                tickFormatter={(v) => v.slice(5)}
                tick={{ fontSize: 11 }}
                interval={1}
              />
              <YAxis tick={{ fontSize: 11 }} />
              <Tooltip
                labelFormatter={(v) => `Week: ${v}`}
                formatter={(v) => [v, 'New users']}
              />
              <Bar dataKey="count" fill="hsl(221.2 83.2% 53.3%)" radius={[3, 3, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      </div>
    </div>
  )
}
