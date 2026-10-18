package com.auth.app.listener;

import com.auth.app.config.IdentitySyncProperties;
import com.auth.idm.event.IdentitySyncRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * 主档事务提交后立即通知子系统做全量幂等对账。
 * <p>单个子系统不可用不会回滚人事主档；子系统的分钟级定时对账作为自愈兜底。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdentitySyncPushListener {

    private final IdentitySyncProperties properties;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void push(IdentitySyncRequestedEvent event) {
        if (!properties.isEnabled()) {
            return;
        }
        properties.getTargets().entrySet().stream()
                .filter(entry -> event.clientIds().isEmpty() || event.clientIds().contains(entry.getKey()))
                .forEach(entry -> pushOne(entry, event));
    }

    private void pushOne(Map.Entry<String, String> target, IdentitySyncRequestedEvent event) {
        String source = target.getKey().equals(event.originClientId()) ? "LOCAL" : "GROUP";
        String separator = target.getValue().contains("?") ? "&" : "?";
        URI uri = URI.create(target.getValue() + separator
                + "source=" + encode(source)
                + "&reason=" + encode(event.reason())
                + "&tenantId=" + event.tenantId());
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .header("X-Identity-Sync-Token", properties.getCallbackToken())
                .header("X-Tenant-Id", String.valueOf(event.tenantId()))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        try {
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("[IDENTITY-SYNC] 实时对账已推送: tenantId={}, clientId={}, source={}, reason={}",
                        event.tenantId(), target.getKey(), source, event.reason());
            } else {
                log.warn("[IDENTITY-SYNC] 实时对账推送失败，将由定时任务自愈: clientId={}, status={}, reason={}",
                        target.getKey(), response.statusCode(), event.reason());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("[IDENTITY-SYNC] 实时对账推送被中断，将由定时任务自愈: clientId={}", target.getKey());
        } catch (Exception ex) {
            log.warn("[IDENTITY-SYNC] 实时对账推送异常，将由定时任务自愈: clientId={}, message={}",
                    target.getKey(), ex.getMessage());
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
