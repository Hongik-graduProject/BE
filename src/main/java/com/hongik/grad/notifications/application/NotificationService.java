package com.hongik.grad.notifications.application;

import com.hongik.grad.analysis.dto.PaginationMeta;
import com.hongik.grad.common.api.ErrorCode;
import com.hongik.grad.common.exception.DomainException;
import com.hongik.grad.folders.domain.FavoriteRepository;
import com.hongik.grad.notifications.domain.FcmToken;
import com.hongik.grad.notifications.domain.FcmTokenRepository;
import com.hongik.grad.notifications.domain.Notification;
import com.hongik.grad.notifications.domain.NotificationRepository;
import com.hongik.grad.notifications.domain.NotificationType;
import com.hongik.grad.notifications.dto.NotificationItemResponse;
import com.hongik.grad.notifications.dto.NotificationListResponse;
import com.hongik.grad.stocks.domain.Stock;
import com.hongik.grad.stocks.domain.StockRepository;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fans a freshly committed report out to every user that favorited its ticker: one notification row
 * per user (independent of device count) plus an FCM push to each registered device. Runs in its
 * own {@code REQUIRES_NEW} transaction so it is decoupled from the report-generation transaction,
 * and FCM dispatch (async, best-effort) never blocks the notification INSERTs.
 */
@Service
public class NotificationService {

  private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

  private static final int MAX_PAGE_SIZE = 100;

  private final FavoriteRepository favoriteRepository;
  private final NotificationRepository notificationRepository;
  private final FcmTokenRepository fcmTokenRepository;
  private final StockRepository stockRepository;
  private final FcmSender fcmSender;

  public NotificationService(
      FavoriteRepository favoriteRepository,
      NotificationRepository notificationRepository,
      FcmTokenRepository fcmTokenRepository,
      StockRepository stockRepository,
      FcmSender fcmSender) {
    this.favoriteRepository = favoriteRepository;
    this.notificationRepository = notificationRepository;
    this.fcmTokenRepository = fcmTokenRepository;
    this.stockRepository = stockRepository;
    this.fcmSender = fcmSender;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void fanOutReportCreated(Long reportId, String ticker, String title) {
    List<UUID> userIds = favoriteRepository.findDistinctUserIdsByTicker(ticker);
    if (userIds.isEmpty()) {
      log.debug("No favorites for ticker={} — skip fan-out for report_id={}", ticker, reportId);
      return;
    }

    String body = buildBody(title);
    Map<String, String> data =
        Map.of(
            "type",
            NotificationType.DISCLOSURE_REPORT.name().toLowerCase(Locale.ROOT),
            "report_id",
            String.valueOf(reportId),
            "ticker",
            ticker);

    List<Notification> notifications =
        userIds.stream()
            .map(
                userId ->
                    new Notification(
                        userId, NotificationType.DISCLOSURE_REPORT, title, body, ticker, reportId))
            .toList();
    notificationRepository.saveAll(notifications);

    List<FcmToken> tokens = fcmTokenRepository.findByUserIdIn(userIds);
    for (FcmToken token : tokens) {
      fcmSender.send(token.getDeviceToken(), title, body, data);
    }
    log.info("Fanned out report_id={} ticker={} to {} user(s).", reportId, ticker, userIds.size());
  }

  private String buildBody(String title) {
    return title + " 분석 보고서가 생성되었습니다";
  }

  /** §6-1 — paginated list (1-based) with filters and the user's total unread count. */
  @Transactional(readOnly = true)
  public NotificationListResponse list(
      UUID userId, Boolean isRead, String type, int page, int size) {
    if (page < 1) {
      throw new DomainException(ErrorCode.INVALID_PARAM, "page must be >= 1");
    }
    if (size < 1 || size > MAX_PAGE_SIZE) {
      throw new DomainException(
          ErrorCode.INVALID_PARAM, "size must be between 1 and " + MAX_PAGE_SIZE);
    }
    NotificationType typeFilter = parseType(type);
    Pageable pageable = PageRequest.of(page - 1, size);

    Page<Notification> result = query(userId, isRead, typeFilter, pageable);
    List<Notification> content = result.getContent();

    Map<String, String> stockNames = resolveStockNames(content);
    List<NotificationItemResponse> items =
        content.stream().map(n -> toItem(n, stockNames.get(n.getTicker()))).toList();

    long unreadCount = notificationRepository.countByUserIdAndIsReadFalse(userId);
    PaginationMeta pagination = PaginationMeta.of(page, size, result.getTotalElements());
    return new NotificationListResponse(items, unreadCount, pagination);
  }

  /** §6-2 — idempotent single read. Returns 404 when the notification is not the caller's. */
  @Transactional
  public void markRead(UUID userId, Long notificationId) {
    Notification notification =
        notificationRepository
            .findByIdAndUserId(notificationId, userId)
            .orElseThrow(() -> new DomainException(ErrorCode.NOT_FOUND, "알림을 찾을 수 없습니다"));
    if (!notification.isRead()) {
      notification.markRead();
    }
  }

  /** §6-3 — bulk read, optionally scoped to a single ticker. Returns the affected count. */
  @Transactional
  public int markAllRead(UUID userId, String ticker) {
    if (ticker != null && !ticker.isBlank()) {
      return notificationRepository.markAllReadByUserIdAndTicker(userId, ticker);
    }
    return notificationRepository.markAllReadByUserId(userId);
  }

  private Page<Notification> query(
      UUID userId, Boolean isRead, NotificationType type, Pageable pageable) {
    if (isRead != null && type != null) {
      return notificationRepository.findByUserIdAndIsReadAndTypeOrderByCreatedAtDesc(
          userId, isRead, type, pageable);
    }
    if (isRead != null) {
      return notificationRepository.findByUserIdAndIsReadOrderByCreatedAtDesc(
          userId, isRead, pageable);
    }
    if (type != null) {
      return notificationRepository.findByUserIdAndTypeOrderByCreatedAtDesc(userId, type, pageable);
    }
    return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
  }

  private NotificationType parseType(String type) {
    if (type == null || type.isBlank()) {
      return null;
    }
    try {
      return NotificationType.valueOf(type.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      throw new DomainException(ErrorCode.INVALID_PARAM, "Unknown notification type: " + type);
    }
  }

  private Map<String, String> resolveStockNames(List<Notification> notifications) {
    Set<String> tickers =
        notifications.stream()
            .map(Notification::getTicker)
            .filter(t -> t != null)
            .collect(Collectors.toSet());
    if (tickers.isEmpty()) {
      return Collections.emptyMap();
    }
    return stockRepository.findAllById(tickers).stream()
        .collect(Collectors.toMap(Stock::getTicker, Stock::getName));
  }

  private NotificationItemResponse toItem(Notification n, String stockName) {
    return new NotificationItemResponse(
        n.getId(),
        n.getType().name().toLowerCase(Locale.ROOT),
        n.getTitle(),
        n.getBody(),
        n.getTicker(),
        stockName,
        n.getReportId(),
        n.isRead(),
        n.getCreatedAt());
  }
}
