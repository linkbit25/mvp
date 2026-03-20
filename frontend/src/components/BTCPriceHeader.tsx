import { useQuery } from '@tanstack/react-query';
import { ResponsiveContainer, AreaChart, Area, YAxis } from 'recharts';
import api from '@/services/api';
import { TrendingUp, TrendingDown, Loader2, AlertCircle } from 'lucide-react';
import { cn } from '@/lib/utils';

interface BtcPriceData {
  inr: number;
  change24h: number;
}

interface BtcHistoryPoint {
  timestamp: number;
  price: number;
}

export const BTCPriceHeader = () => {
  const { data: priceData, isLoading: isPriceLoading, isError: isPriceError } = useQuery<BtcPriceData>({
    queryKey: ['btc-price'],
    queryFn: async () => {
      const response = await api.get('/btc/price');
      return response.data;
    },
    refetchInterval: 30000,
  });

  const { data: historyData, isLoading: isHistoryLoading } = useQuery<BtcHistoryPoint[]>({
    queryKey: ['btc-history'],
    queryFn: async () => {
      const response = await api.get('/btc/price/history');
      return response.data;
    },
    refetchInterval: 30000,
  });

  if (isPriceLoading || isHistoryLoading) {
    return (
      <div className="w-full h-16 bg-white border-b border-slate-200 flex items-center px-8 animate-pulse">
        <Loader2 className="h-4 w-4 animate-spin text-slate-400 mr-2" />
        <span className="text-slate-400 text-sm">Loading market data...</span>
      </div>
    );
  }

  if (isPriceError || !priceData) {
    return (
      <div className="w-full h-16 bg-red-50 border-b border-red-100 flex items-center px-8">
        <AlertCircle className="h-4 w-4 text-red-500 mr-2" />
        <span className="text-red-600 text-sm">Market data unavailable</span>
      </div>
    );
  }

  const isPositive = priceData.change24h >= 0;

  return (
    <div className="w-full bg-white border-b border-slate-200 h-16 flex items-center px-4 md:px-8 justify-between overflow-hidden">
      <div className="flex items-center gap-6">
        <div className="flex flex-col">
          <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">BTC / INR</span>
          <div className="flex items-baseline gap-2">
            <span className="text-lg font-bold text-slate-900 leading-none">
              ₹{priceData.inr.toLocaleString('en-IN', { maximumFractionDigits: 0 })}
            </span>
            <div className={cn(
              "flex items-center text-xs font-semibold",
              isPositive ? "text-emerald-600" : "text-red-500"
            )}>
              {isPositive ? <TrendingUp className="h-3 w-3 mr-0.5" /> : <TrendingDown className="h-3 w-3 mr-0.5" />}
              {Math.abs(priceData.change24h).toFixed(2)}%
            </div>
          </div>
        </div>

        <div className="hidden sm:block w-32 md:w-48 h-10">
          <ResponsiveContainer width="100%" height="100%">
            <AreaChart data={historyData}>
              <defs>
                <linearGradient id="colorPrice" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor={isPositive ? "#10b981" : "#ef4444"} stopOpacity={0.3}/>
                  <stop offset="95%" stopColor={isPositive ? "#10b981" : "#ef4444"} stopOpacity={0}/>
                </linearGradient>
              </defs>
              <YAxis domain={['auto', 'auto']} hide />
              <Area 
                type="monotone" 
                dataKey="price" 
                stroke={isPositive ? "#10b981" : "#ef4444"} 
                fillOpacity={1} 
                fill="url(#colorPrice)" 
                strokeWidth={2}
                isAnimationActive={false}
              />
            </AreaChart>
          </ResponsiveContainer>
        </div>
      </div>

      <div className="hidden lg:flex items-center gap-2 text-slate-400 text-[10px] font-semibold uppercase tracking-widest">
        <div className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse" />
        Live Market
      </div>
    </div>
  );
};
