package com.stock.stock_analyser.engine;

import com.stock.dto.StockHistoryDetails;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BreakoutEngineTest {
    private final BreakoutEngine engine=new BreakoutEngine();
    private List<StockHistoryDetails> candles(int breakoutIndex,double breakoutVolume){List<StockHistoryDetails> list=new ArrayList<>(); for(int i=0;i<40;i++) list.add(candle(i,i==breakoutIndex?110:100,i==breakoutIndex?breakoutVolume:50)); return list;}
    private StockHistoryDetails candle(int i,double close,double volume){return StockHistoryDetails.builder().historyDate(java.time.LocalDate.of(2026,1,1).plusDays(i)).open(close).high(close).low(close).close(close).volume(String.valueOf(volume)).build();}
    @Test void freshBreakoutWithoutVolumeIsNotConfirmed(){var r=engine.analyse(candles(39,50)); assertEquals("FRESH_BREAKOUT_UNCONFIRMED_VOLUME",r.getSignal()); assertNotNull(r.getBreakoutVolumeRatio()); assertTrue(r.getBreakoutVolumeRatio()<1.5);}
    @Test void freshBreakoutWithConfirmedVolumeIsFresh(){var r=engine.analyse(candles(39,100)); assertEquals("FRESH_BREAKOUT",r.getSignal()); assertTrue(r.getBreakoutVolumeRatio()>=1.5);}
    @Test void oldBreakoutIsHistoricalNotCurrentActionable(){var r=engine.analyse(candles(20,100)); assertTrue(r.getDaysAgoBreakout()>5); assertEquals("HISTORICAL_BREAKOUT_CONFIRMED",r.getSignal());}
}
