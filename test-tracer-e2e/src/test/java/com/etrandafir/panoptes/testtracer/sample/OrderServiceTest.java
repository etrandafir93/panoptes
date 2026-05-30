package com.etrandafir.panoptes.testtracer.sample;

import com.etrandafir.panoptes.testtracer.junit5.TestTracerExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(PanoptesTestConfig.class)
@ExtendWith({PanoptesTestExtension.class, TestTracerExtension.class})
class OrderServiceTest {

    @Autowired OrderService orderService;

    @Test
    void placeOrder_shouldReturnOrderWithId() {
        OrderService.Order order = orderService.placeOrder(42L, List.of("widget"));

        assertThat(order.id()).isGreaterThan(0);
        assertThat(order.customerId()).isEqualTo(42L);
        assertThat(order.items()).containsExactly("widget");
    }

    @Test
    void placeOrder_withMultipleItems_shouldReserveAll() {
        OrderService.Order order = orderService.placeOrder(1L, List.of("widget", "gadget"));

        assertThat(order.items()).containsExactlyInAnyOrder("widget", "gadget");
    }

    @Test
    void cancelOrder_shouldMakeOrderUnavailable() {
        OrderService.Order order = orderService.placeOrder(7L, List.of("gadget"));
        orderService.cancelOrder(order.id());

        assertThatThrownBy(() -> orderService.getOrder(order.id()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void placeOrder_whenItemOutOfStock_shouldFail() {
        // Exhaust doohickey stock (10 units) — other tests don't touch doohickey
        for (int i = 0; i < 10; i++) {
            orderService.placeOrder((long) i, List.of("doohickey"));
        }

        assertThatThrownBy(() -> orderService.placeOrder(99L, List.of("doohickey")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("out of stock");
    }

    @Test
    void placeOrder_shouldSendConfirmation_toWrongCustomer() {
        // Intentionally wrong assertion — demonstrates what a failing test looks like in the report
        OrderService.Order order = orderService.placeOrder(5L, List.of("widget"));

        assertThat(order.customerId())
                .as("expected order to be placed for customer 5 but something went wrong")
                .isEqualTo(999L);
    }
}
