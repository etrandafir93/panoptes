package com.etrandafir.panoptes.testtracer.sample;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class OrderService {

    private final Tracer tracer;
    private final Map<Long, Order> orders = new ConcurrentHashMap<>();
    private final Map<String, Integer> inventory = new ConcurrentHashMap<>(Map.of(
            "widget", 100,
            "gadget", 50,
            "doohickey", 10
    ));
    private final AtomicLong idSeq = new AtomicLong(1);

    public OrderService(OpenTelemetry openTelemetry) {
        this.tracer = openTelemetry.getTracer("panoptes-sample");
    }

    public Order placeOrder(long customerId, List<String> items) {
        validateInventory(items);
        reserveItems(items);
        Order order = createOrder(customerId, items);
        sendConfirmation(customerId, order.id());
        return order;
    }

    public Order getOrder(long orderId) {
        Span span = tracer.spanBuilder("db:SELECT order").startSpan();
        try (Scope ws = span.makeCurrent()) {
            Order order = orders.get(orderId);
            if (order == null) {
                throw new IllegalArgumentException("Order " + orderId + " not found");
            }
            return order;
        } finally {
            span.end();
        }
    }

    public void cancelOrder(long orderId) {
        Order order = getOrder(orderId);
        restoreInventory(order.items());
        Span span = tracer.spanBuilder("db:DELETE order").startSpan();
        try (Scope ws = span.makeCurrent()) {
            orders.remove(orderId);
        } finally {
            span.end();
        }
    }

    private void validateInventory(List<String> items) {
        Span span = tracer.spanBuilder("service:validateInventory").startSpan();
        try (Scope ws = span.makeCurrent()) {
            for (String item : items) {
                int stock = inventory.getOrDefault(item, 0);
                if (stock <= 0) {
                    throw new IllegalStateException("Item out of stock: " + item);
                }
            }
        } finally {
            span.end();
        }
    }

    private void reserveItems(List<String> items) {
        Span span = tracer.spanBuilder("service:reserveItems").startSpan();
        try (Scope ws = span.makeCurrent()) {
            Span dbSpan = tracer.spanBuilder("db:UPDATE inventory").startSpan();
            try (Scope dbWs = dbSpan.makeCurrent()) {
                items.forEach(item -> inventory.merge(item, -1, Integer::sum));
            } finally {
                dbSpan.end();
            }
        } finally {
            span.end();
        }
    }

    private Order createOrder(long customerId, List<String> items) {
        Span span = tracer.spanBuilder("service:createOrder").startSpan();
        try (Scope ws = span.makeCurrent()) {
            Span dbSpan = tracer.spanBuilder("db:INSERT order").startSpan();
            try (Scope dbWs = dbSpan.makeCurrent()) {
                long id = idSeq.getAndIncrement();
                Order order = new Order(id, customerId, items);
                orders.put(id, order);
                return order;
            } finally {
                dbSpan.end();
            }
        } finally {
            span.end();
        }
    }

    private void sendConfirmation(long customerId, long orderId) {
        Span span = tracer.spanBuilder("service:sendConfirmation").startSpan();
        try (Scope ws = span.makeCurrent()) {
            Span httpSpan = tracer.spanBuilder("http:POST /notifications").startSpan();
            try (Scope httpWs = httpSpan.makeCurrent()) {
                // simulate HTTP call to notification service
            } finally {
                httpSpan.end();
            }
        } finally {
            span.end();
        }
    }

    private void restoreInventory(List<String> items) {
        Span span = tracer.spanBuilder("db:UPDATE inventory").startSpan();
        try (Scope ws = span.makeCurrent()) {
            items.forEach(item -> inventory.merge(item, 1, Integer::sum));
        } finally {
            span.end();
        }
    }

    public record Order(long id, long customerId, List<String> items) {}
}
