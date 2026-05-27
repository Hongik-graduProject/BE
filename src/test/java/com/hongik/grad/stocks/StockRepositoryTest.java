package com.hongik.grad.stocks;

import static org.assertj.core.api.Assertions.assertThat;

import com.hongik.grad.stocks.domain.FinancialStatement;
import com.hongik.grad.stocks.domain.FinancialStatementRepository;
import com.hongik.grad.stocks.domain.Market;
import com.hongik.grad.stocks.domain.PeriodType;
import com.hongik.grad.stocks.domain.Stock;
import com.hongik.grad.stocks.domain.StockRepository;
import com.hongik.grad.support.TestcontainersConfig;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test-db")
@Import(TestcontainersConfig.class)
class StockRepositoryTest {

  @Autowired private StockRepository stockRepository;
  @Autowired private FinancialStatementRepository financialStatementRepository;
  @Autowired private TestEntityManager em;

  @Test
  @DisplayName("Stock 저장 시 ticker PK와 BaseTimeEntity 타임스탬프가 채워진다")
  void saveAndReadStock() {
    Stock saved = stockRepository.save(new Stock("T00001", "테스트종목A", Market.KOSPI));
    em.flush();
    em.clear();

    Stock found = stockRepository.findById("T00001").orElseThrow();
    assertThat(found.getTicker()).isEqualTo("T00001");
    assertThat(found.getName()).isEqualTo("테스트종목A");
    assertThat(found.getMarket()).isEqualTo(Market.KOSPI);
    assertThat(found.getCreatedAt()).isNotNull();
    assertThat(found.getUpdatedAt()).isNotNull();
    assertThat(saved.getTicker()).isEqualTo("T00001");
  }

  @Test
  @DisplayName("Market 기준으로 종목을 조회할 수 있다")
  void findByMarket() {
    stockRepository.save(new Stock("T00002", "테스트종목B", Market.KOSPI));
    em.flush();
    em.clear();

    List<Stock> kospiStocks = stockRepository.findByMarket(Market.KOSPI);
    assertThat(kospiStocks)
        .isNotEmpty()
        .allMatch(s -> s.getMarket() == Market.KOSPI)
        .anyMatch(s -> s.getTicker().equals("T00002"));
  }

  @Test
  @DisplayName("FinancialStatement 저장 시 Stock FK 연관과 unique 제약이 적용된다")
  void saveFinancialStatement() {
    Stock stock = stockRepository.save(new Stock("T00003", "테스트종목C", Market.KOSPI));
    FinancialStatement fs = new FinancialStatement(stock, PeriodType.ANNUAL, "2024");
    financialStatementRepository.save(fs);
    em.flush();
    em.clear();

    List<FinancialStatement> result =
        financialStatementRepository.findByStockTickerAndPeriodTypeOrderByPeriodDesc(
            "T00003", PeriodType.ANNUAL);
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getStock().getTicker()).isEqualTo("T00003");
    assertThat(result.get(0).getPeriodType()).isEqualTo(PeriodType.ANNUAL);
    assertThat(result.get(0).getPeriod()).isEqualTo("2024");
  }

  @Test
  @DisplayName("특정 종목의 전체 재무제표를 기간 역순으로 조회할 수 있다")
  void findByStockTickerOrderByPeriodDesc() {
    Stock stock = stockRepository.save(new Stock("T00004", "테스트종목D", Market.KOSDAQ));
    financialStatementRepository.save(new FinancialStatement(stock, PeriodType.ANNUAL, "2023"));
    financialStatementRepository.save(new FinancialStatement(stock, PeriodType.ANNUAL, "2024"));
    financialStatementRepository.save(
        new FinancialStatement(stock, PeriodType.QUARTERLY, "2024Q1"));
    em.flush();
    em.clear();

    List<FinancialStatement> result =
        financialStatementRepository.findByStockTickerOrderByPeriodDesc("T00004");
    assertThat(result).hasSize(3);
    assertThat(result.get(0).getPeriod()).isEqualTo("2024Q1");
    assertThat(result.get(1).getPeriod()).isEqualTo("2024");
    assertThat(result.get(2).getPeriod()).isEqualTo("2023");
  }
}
