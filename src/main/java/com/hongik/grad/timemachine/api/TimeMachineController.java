package com.hongik.grad.timemachine.api;

import com.hongik.grad.common.api.ApiResponse;
import com.hongik.grad.common.api.ErrorCode;
import com.hongik.grad.common.exception.DomainException;
import com.hongik.grad.disclosures.domain.DisclosureType;
import com.hongik.grad.timemachine.application.ReplayService;
import com.hongik.grad.timemachine.application.TrackRecordService;
import com.hongik.grad.timemachine.dto.ReplayResponse;
import com.hongik.grad.timemachine.dto.TrackRecordResponse;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/analysis")
public class TimeMachineController {

  // ticker 는 시세 원천 URL 에 들어간다. 영숫자만 받는다.
  private static final Pattern TICKER = Pattern.compile("^[A-Za-z0-9]{1,10}$");

  private final ReplayService replayService;
  private final TrackRecordService trackRecordService;

  public TimeMachineController(ReplayService replayService, TrackRecordService trackRecordService) {
    this.replayService = replayService;
    this.trackRecordService = trackRecordService;
  }

  @GetMapping("/reports/{report_id}/replay")
  public ApiResponse<ReplayResponse> replay(@PathVariable("report_id") Long reportId) {
    return ApiResponse.success(replayService.replay(reportId));
  }

  @GetMapping("/{ticker}/track-record")
  public ApiResponse<TrackRecordResponse> trackRecord(
      @PathVariable("ticker") String ticker,
      @RequestParam(value = "type", required = false) String type) {
    if (!TICKER.matcher(ticker).matches()) {
      throw new DomainException(ErrorCode.INVALID_PARAM, "ticker must be alphanumeric, <=10");
    }
    return ApiResponse.success(trackRecordService.report(ticker, parseType(type)));
  }

  private static DisclosureType parseType(String type) {
    if (type == null || type.isBlank()) return null;
    try {
      return DisclosureType.fromValue(type.toLowerCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new DomainException(
          ErrorCode.INVALID_PARAM, "type must be report|quarterly|interim|event");
    }
  }
}
