package com.hongik.grad.stocks.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hongik.grad.common.time.KrxTradingCalendar;
import com.hongik.grad.stocks.client.KrxClient;
import com.hongik.grad.stocks.dto.KrxQuote;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

@Service
public class KrxQuoteService {

  private static final Logger log = LoggerFactory.getLogger(KrxQuoteService.class);
  private static final String KEY_PREFIX = "krx:quote:";
  private static final Duration TTL_MARKET_OPEN = Duration.ofSeconds(5);
  private static final Duration TTL_MARKET_CLOSED = Duration.ofMinutes(30);

  private final KrxClient krxClient;
  @Nullable private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;
  private final KrxTradingCalendar tradingCalendar;
  @Nullable private final Executor taskExecutor;

  public KrxQuoteService(
      KrxClient krxClient,
      @Nullable StringRedisTemplate redisTemplate,
      ObjectMapper objectMapper,
      KrxTradingCalendar tradingCalendar,
      @Nullable @Qualifier("taskExecutor") Executor taskExecutor) {
    this.krxClient = krxClient;
    this.redisTemplate = redisTemplate;
    this.objectMapper = objectMapper;
    this.tradingCalendar = tradingCalendar;
    this.taskExecutor = taskExecutor;
  }

  public KrxQuote getQuote(String ticker) {
    String key = KEY_PREFIX + ticker;
    if (redisTemplate != null) {
      try {
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
          return objectMapper.readValue(cached, KrxQuote.class);
        }
      } catch (Exception ex) {
        log.warn("Redis read failed for {}: {}", key, ex.getMessage());
      }
    }

    KrxQuote quote = krxClient.getQuote(ticker);
    cacheQuote(key, quote);
    return quote;
  }

  public List<KrxQuote> getQuotes(List<String> tickers) {
    List<KrxQuote> results = new ArrayList<>(tickers.size());
    List<String> missingTickers = new ArrayList<>();
    List<Integer> missingIndices = new ArrayList<>();

    for (int i = 0; i < tickers.size(); i++) {
      String ticker = tickers.get(i);
      String key = KEY_PREFIX + ticker;
      if (redisTemplate != null) {
        try {
          String cached = redisTemplate.opsForValue().get(key);
          if (cached != null) {
            results.add(objectMapper.readValue(cached, KrxQuote.class));
            continue;
          }
        } catch (Exception ex) {
          log.warn("Redis read failed for {}: {}", key, ex.getMessage());
        }
      }
      results.add(null);
      missingTickers.add(ticker);
      missingIndices.add(i);
    }

    if (taskExecutor != null) {
      List<CompletableFuture<Void>> futures = new ArrayList<>();
      for (int j = 0; j < missingTickers.size(); j++) {
        int idx = j;
        String t = missingTickers.get(idx);
        futures.add(
            CompletableFuture.runAsync(
                () -> {
                  KrxQuote quote = krxClient.getQuote(t);
                  cacheQuote(KEY_PREFIX + t, quote);
                  results.set(missingIndices.get(idx), quote);
                },
                taskExecutor));
      }
      CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    } else {
      for (int j = 0; j < missingTickers.size(); j++) {
        String t = missingTickers.get(j);
        KrxQuote quote = krxClient.getQuote(t);
        cacheQuote(KEY_PREFIX + t, quote);
        results.set(missingIndices.get(j), quote);
      }
    }

    return results;
  }

  private void cacheQuote(String key, KrxQuote quote) {
    if (redisTemplate == null) {
      return;
    }
    try {
      String json = objectMapper.writeValueAsString(quote);
      Duration ttl = tradingCalendar.isMarketOpen() ? TTL_MARKET_OPEN : TTL_MARKET_CLOSED;
      redisTemplate.opsForValue().set(key, json, ttl.toSeconds(), TimeUnit.SECONDS);
    } catch (JsonProcessingException ex) {
      log.warn("Failed to serialize quote for {}: {}", key, ex.getMessage());
    } catch (Exception ex) {
      log.warn("Redis write failed for {}: {}", key, ex.getMessage());
    }
  }
}
