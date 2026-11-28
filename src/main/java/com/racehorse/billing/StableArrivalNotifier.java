package com.racehorse.billing;

import com.racehorse.billing.BillingRepository.Arrival;
import com.racehorse.horse.activation.HorseActivatedEvent;
import com.racehorse.notification.NotificationApi;
import com.racehorse.notification.NotificationPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import static com.racehorse.billing.BillingDtos.BOARDING;
import static com.racehorse.billing.BillingDtos.TRAINING;
import static com.racehorse.billing.VnpayGateway.VN;

/**
 * Price transparency (DEC-016 update): when a horse is accepted (DRAFT -> ACTIVE), the owner who will be billed is
 * told which box it is in, from which day fees run, and the current prices. Runs in the activation transaction.
 */
@Component
public class StableArrivalNotifier {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final BillingRepository repository;
    private final NotificationPublisher notifications;

    public StableArrivalNotifier(BillingRepository repository, NotificationPublisher notifications) {
        this.repository = repository;
        this.notifications = notifications;
    }

    @EventListener
    public void onHorseActivated(HorseActivatedEvent event) {
        Optional<Arrival> arrival = repository.arrival(event.horseId());
        Optional<Long> owner = BillingService.billedOwner(repository.ownersOn(event.horseId(), LocalDate.now(VN)));
        if (arrival.isEmpty() || owner.isEmpty()) {
            return;
        }
        Arrival a = arrival.get();
        String prices = repository.currentPrice(BOARDING).flatMap(boarding -> repository.currentPrice(TRAINING)
                        .map(training -> " Phí lưu chuồng & chăm sóc " + BillingService.vnd(boarding.unitPrice().longValueExact())
                                + "/ngày, huấn luyện " + BillingService.vnd(training.unitPrice().longValueExact())
                                + "/buổi hoàn thành."))
                .orElse(" Club Manager chưa công bố bảng giá.");
        notifications.persistAndPublish(owner.get(), NotificationApi.Category.ALERTS, "HORSE_STABLED",
                "Ngựa đã vào chuồng",
                a.horseName() + " (" + a.horseCode() + ") đã vào ô " + a.boxCode() + " từ " + DAY.format(a.from())
                        + "." + prices + " Xem phí tạm tính ở Hóa đơn & thanh toán.",
                "HORSE", event.horseId(), false);
    }
}
