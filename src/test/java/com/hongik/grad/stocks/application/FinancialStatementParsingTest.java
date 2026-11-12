package com.hongik.grad.stocks.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.hongik.grad.disclosures.client.DartFinanceClient.FinanceRow;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FinancialStatementParsingTest {

  @Test
  @DisplayName("자본변동표(SCE) 행은 손익계산서로 합치지 않는다")
  void dropsStatementOfChangesInEquity() {
    assertThat(FinancialStatementFetchService.normalizeDiv("SCE")).isNull();
    assertThat(FinancialStatementFetchService.normalizeDiv("IS")).isEqualTo("CIS");
    assertThat(FinancialStatementFetchService.normalizeDiv("BS")).isEqualTo("BS");
  }

  @Test
  @DisplayName("지배/비지배 귀속 순이익은 포괄손익 행이 아닌 순이익 행에서 고른다")
  void picksNetIncomeShareNotComprehensiveIncome() {
    // 제주반도체 2025 반기 실제 DART 순서
    List<FinanceRow> rows =
        List.of(
            new FinanceRow("포괄손익, 비지배지분", "-5,733,499", "CIS", null),
            new FinanceRow("포괄손익, 지배기업의 소유주에게 귀속되는 지분", "-2,049,252,447", "CIS", null),
            new FinanceRow("당기순이익(손실)", "-2,621,940,932", "CIS", null),
            new FinanceRow("비지배지분에 귀속되는 당기순이익(손실)", "-5,733,499", "CIS", null),
            new FinanceRow("지배기업의 소유주에게 귀속되는 당기순이익(손실)", "-2,616,207,433", "CIS", null));

    assertThat(FinancialStatementFetchService.pickNetIncomeShare(rows, "지배기업"))
        .isEqualTo(-2_616_207_433L);
    assertThat(FinancialStatementFetchService.pickNetIncomeShare(rows, "비지배"))
        .isEqualTo(-5_733_499L);
  }

  @Test
  @DisplayName("부채총계는 앞선 '자본과부채총계' 행이 아니라 정확히 같은 계정에서 고른다")
  void picksExactAccountBeforePartialMatch() {
    List<FinanceRow> rows =
        List.of(
            new FinanceRow("자산총계", "236,800", "BS", null),
            new FinanceRow("자본총계", "191,000", "BS", null),
            new FinanceRow("자본과부채총계", "236,800", "BS", null),
            new FinanceRow("부채총계", "45,800", "BS", null));

    assertThat(FinancialStatementFetchService.pick(rows, "부채총계")).isEqualTo(45_800L);
    assertThat(FinancialStatementFetchService.pick(rows, "자본총계")).isEqualTo(191_000L);
    assertThat(FinancialStatementFetchService.pick(rows, "영업활동")).isNull();
  }

  @Test
  @DisplayName("rcept_no 앞 8 자리를 공시일로 읽고, 형식이 깨졌으면 null")
  void parsesDisclosedAtFromRceptNo() {
    assertThat(FinancialStatementFetchService.parseDisclosedAt("20250514000123"))
        .isEqualTo(LocalDate.of(2025, 5, 14));
    assertThat(FinancialStatementFetchService.parseDisclosedAt("20250231000123")).isNull();
    assertThat(FinancialStatementFetchService.parseDisclosedAt("2025AB14000123")).isNull();
    assertThat(FinancialStatementFetchService.parseDisclosedAt("2025051")).isNull();
    assertThat(FinancialStatementFetchService.parseDisclosedAt("")).isNull();
    assertThat(FinancialStatementFetchService.parseDisclosedAt(null)).isNull();
  }
}
